package com.puretrans.app.data.engine

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.puretrans.app.data.model.Language
import com.puretrans.app.data.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GoogleBackendFallbackEngine(
    private val backendUrl: String,
    private val backendToken: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : TranslationEngine {

    override val engineName: String = "免密后端"
    override val isDirect: Boolean = false

    override suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): TranslationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 优先尝试配置的自建反代后端端点
        val result = tryRequest(backendUrl, backendToken, text, sourceLang, targetLang, startTime)
        if (result != null && result.isSuccess) {
            return@withContext result
        }

        // 若自建反代失败且配置的是默认占位域名，无缝降级直连官方 gtx 接口进行兜底
        if (backendUrl.contains("yourdomain.com") || (result != null && !result.isSuccess)) {
            val fallbackUrl = "https://translate.googleapis.com/translate_a/single"
            val directResult = tryRequest(fallbackUrl, null, text, sourceLang, targetLang, startTime)
            if (directResult != null && directResult.isSuccess) {
                return@withContext directResult.copy(activeEngineLabel = "Google免密")
            }
        }

        result ?: TranslationResult(
            originalText = text,
            translatedText = "",
            sourceLang = sourceLang.displayName,
            targetLang = targetLang.displayName,
            activeEngineLabel = engineName,
            latencyMs = System.currentTimeMillis() - startTime,
            isDirectMode = false,
            isSuccess = false,
            errorMessage = "免密后端通道请求失败，请检查网络或后端配置"
        )
    }

    private fun tryRequest(
        baseUrl: String,
        token: String?,
        text: String,
        sourceLang: Language,
        targetLang: Language,
        startTime: Long
    ): TranslationResult? {
        val httpUrl = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("client", "gtx")
            ?.addQueryParameter("sl", sourceLang.googleCode)
            ?.addQueryParameter("tl", targetLang.googleCode)
            ?.addQueryParameter("dt", "t")
            ?.addQueryParameter("dt", "rm") // 请求音标/拼音
            ?.addQueryParameter("q", text)
            ?.build() ?: return null

        val reqBuilder = Request.Builder()
            .url(httpUrl)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")

        if (!token.isNullOrEmpty()) {
            reqBuilder.header("X-App-Token", token)
        }

        return try {
            val response = client.newCall(reqBuilder.build()).execute()
            val latency = System.currentTimeMillis() - startTime
            val bodyString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLang = sourceLang.displayName,
                    targetLang = targetLang.displayName,
                    activeEngineLabel = engineName,
                    latencyMs = latency,
                    isDirectMode = false,
                    isSuccess = false,
                    errorMessage = "HTTP ${response.code}: $bodyString"
                )
            }

            parseGtxResponse(bodyString, text, sourceLang, targetLang, latency)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            TranslationResult(
                originalText = text,
                translatedText = "",
                sourceLang = sourceLang.displayName,
                targetLang = targetLang.displayName,
                activeEngineLabel = engineName,
                latencyMs = latency,
                isDirectMode = false,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "连接失败"
            )
        }
    }

    private fun parseGtxResponse(
        jsonString: String,
        originalText: String,
        sourceLang: Language,
        targetLang: Language,
        latencyMs: Long
    ): TranslationResult {
        return try {
            val rootArray = JsonParser.parseString(jsonString).asJsonArray
            val translationArray = rootArray.get(0).asJsonArray
            val sb = StringBuilder()
            for (element in translationArray) {
                if (element.isJsonArray) {
                    val subArray = element.asJsonArray
                    if (subArray.size() > 0 && !subArray.get(0).isJsonNull) {
                        sb.append(subArray.get(0).asString)
                    }
                }
            }

            val translated = sb.toString()
            var phonetic = com.puretrans.app.util.PhoneticHelper.extractGtxPhonetic(rootArray, targetLang)
            if (phonetic.isNullOrBlank() && targetLang.code.startsWith("en", ignoreCase = true)) {
                phonetic = com.puretrans.app.util.PhoneticHelper.fetchEnglishPhonetic(translated, client)
            }

            TranslationResult(
                originalText = originalText,
                translatedText = translated,
                sourceLang = sourceLang.displayName,
                targetLang = targetLang.displayName,
                activeEngineLabel = engineName,
                latencyMs = latencyMs,
                phonetic = phonetic,
                isDirectMode = false,
                isSuccess = true
            )
        } catch (e: Exception) {
            TranslationResult(
                originalText = originalText,
                translatedText = "",
                sourceLang = sourceLang.displayName,
                targetLang = targetLang.displayName,
                activeEngineLabel = engineName,
                latencyMs = latencyMs,
                isDirectMode = false,
                isSuccess = false,
                errorMessage = "解析翻译结果失败: ${e.message}"
            )
        }
    }
}
