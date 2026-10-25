package com.puretrans.app.data.engine

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.puretrans.app.data.model.Language
import com.puretrans.app.data.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AiDirectEngine(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) : TranslationEngine {

    override val engineName: String = "DeepSeek直连"
    override val isDirect: Boolean = true

    private data class ChatRequest(
        @SerializedName("model") val model: String = "deepseek-chat",
        @SerializedName("messages") val messages: List<ChatMessage>,
        @SerializedName("temperature") val temperature: Double = 0.2
    )

    private data class ChatMessage(
        @SerializedName("role") val role: String,
        @SerializedName("content") val content: String
    )

    private data class ChatResponse(
        @SerializedName("choices") val choices: List<ChatChoice>?,
        @SerializedName("error") val error: ChatError?
    )

    private data class ChatChoice(
        @SerializedName("message") val message: ChatMessage?
    )

    private data class ChatError(
        @SerializedName("message") val message: String?
    )

    override suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): TranslationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        val prompt = if (sourceLang.code == "auto") {
            "Translate the following text accurately into ${targetLang.displayName}. Output ONLY the translated text, do not add explanations, notes, quotes or markdown:"
        } else {
            "Translate the following text from ${sourceLang.displayName} into ${targetLang.displayName}. Output ONLY the translated text, do not add explanations, notes, quotes or markdown:"
        }

        val requestPayload = ChatRequest(
            messages = listOf(
                ChatMessage(
                    role = "system",
                    content = "You are an expert translation engine. Provide direct, natural and accurate translation without any surrounding quotes or extra remarks."
                ),
                ChatMessage(
                    role = "user",
                    content = "$prompt\n\n$text"
                )
            )
        )

        val jsonBody = gson.toJson(requestPayload)
        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
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

            val chatResponse = gson.fromJson(bodyString, ChatResponse::class.java)
            val output = chatResponse.choices?.firstOrNull()?.message?.content?.trim()
            if (output != null) {
                TranslationResult(
                    originalText = text,
                    translatedText = output,
                    sourceLang = sourceLang.displayName,
                    targetLang = targetLang.displayName,
                    activeEngineLabel = engineName,
                    latencyMs = latency,
                    isDirectMode = true,
                    isSuccess = true
                )
            } else {
                TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLang = sourceLang.displayName,
                    targetLang = targetLang.displayName,
                    activeEngineLabel = engineName,
                    latencyMs = latency,
                    isDirectMode = true,
                    isSuccess = false,
                    errorMessage = chatResponse.error?.message ?: "DeepSeek 返回结果为空"
                )
            }
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
                errorMessage = "DeepSeek 连接异常: ${e.localizedMessage ?: e.message}"
            )
        }
    }
}
