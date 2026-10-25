package com.puretrans.app.data.engine

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.puretrans.app.data.model.Language
import com.puretrans.app.data.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class BaiduDirectEngine(
    private val appId: String,
    private val secretKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) : TranslationEngine {

    override val engineName: String = "百度直连"
    override val isDirect: Boolean = true

    private data class BaiduResponse(
        @SerializedName("from") val from: String?,
        @SerializedName("to") val to: String?,
        @SerializedName("trans_result") val transResult: List<TransItem>?,
        @SerializedName("error_code") val errorCode: String?,
        @SerializedName("error_msg") val errorMsg: String?
    )

    private data class TransItem(
        @SerializedName("src") val src: String?,
        @SerializedName("dst") val dst: String?
    )

    override suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): TranslationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val salt = System.currentTimeMillis().toString()
        val rawSign = "$appId$text$salt$secretKey"
        val sign = md5(rawSign)

        val formBody = FormBody.Builder()
            .add("q", text)
            .add("from", sourceLang.baiduCode)
            .add("to", targetLang.baiduCode)
            .add("appid", appId)
            .add("salt", salt)
            .add("sign", sign)
            .build()

        val request = Request.Builder()
            .url("https://fanyi-api.baidu.com/api/trans/vip/translate")
            .post(formBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val bodyString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLang = sourceLang.displayName,
                    targetLang = targetLang.displayName,
                    activeEngineLabel = engineName,
                    latencyMs = latency,
                    isDirectMode = true,
                    isSuccess = false,
                    errorMessage = "HTTP ${response.code}: $bodyString"
                )
            }

            val resultObj = gson.fromJson(bodyString, BaiduResponse::class.java)
            if (resultObj.errorCode != null && resultObj.errorCode != "52000") {
                val errorDescription = when (resultObj.errorCode) {
                    "54001" -> "签名错误，请检查百度 Secret Key 是否正确"
                    "52003" -> "未授权用户，请检查百度 App ID 是否正确"
                    "54000" -> "必填参数为空"
                    "54004" -> "账户余额不足"
                    "58001" -> "不支持的方向语言"
                    else -> "百度接口错误: [${resultObj.errorCode}] ${resultObj.errorMsg}"
                }
                return@withContext TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLang = sourceLang.displayName,
                    targetLang = targetLang.displayName,
                    activeEngineLabel = engineName,
                    latencyMs = latency,
                    isDirectMode = true,
                    isSuccess = false,
                    errorMessage = errorDescription
                )
            }

            val translated = resultObj.transResult?.joinToString("\n") { it.dst.orEmpty() } ?: ""
            TranslationResult(
                originalText = text,
                translatedText = translated,
                sourceLang = sourceLang.displayName,
                targetLang = targetLang.displayName,
                activeEngineLabel = engineName,
                latencyMs = latency,
                isDirectMode = true,
                isSuccess = true
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            TranslationResult(
                originalText = text,
                translatedText = "",
                sourceLang = sourceLang.displayName,
                targetLang = targetLang.displayName,
                activeEngineLabel = engineName,
                latencyMs = latency,
                isDirectMode = true,
                isSuccess = false,
                errorMessage = "网络连接失败: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
