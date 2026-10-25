package com.puretrans.app.data.engine

import android.content.Context
import com.puretrans.app.data.local.HistoryDatabase
import com.puretrans.app.data.local.SettingsManager
import com.puretrans.app.data.model.HistoryItem
import com.puretrans.app.data.model.Language
import com.puretrans.app.data.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmartTranslationRouter(
    private val context: Context,
    private val settingsManager: SettingsManager = SettingsManager.getInstance(context),
    private val historyDatabase: HistoryDatabase = HistoryDatabase.getInstance(context)
) {

    fun isDirectMode(): Boolean {
        return settingsManager.loadSettingsSnapshot().isDirectMode
    }

    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): TranslationResult = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return@withContext TranslationResult(
                originalText = text,
                translatedText = "",
                sourceLang = sourceLang.displayName,
                targetLang = targetLang.displayName,
                activeEngineLabel = "Ready",
                latencyMs = 0,
                isDirectMode = isDirectMode(),
                isSuccess = true
            )
        }

        val snapshot = settingsManager.loadSettingsSnapshot()

        // 核心双模路由调度策略
        val engine: TranslationEngine = when {
            snapshot.baiduAppId.isNotBlank() && snapshot.baiduSecretKey.isNotBlank() -> {
                BaiduDirectEngine(snapshot.baiduAppId, snapshot.baiduSecretKey)
            }
            snapshot.deepseekApiKey.isNotBlank() -> {
                AiDirectEngine(snapshot.deepseekApiKey)
            }
            else -> {
                GoogleBackendFallbackEngine(
                    backendUrl = snapshot.backendUrl,
                    backendToken = snapshot.backendToken
                )
            }
        }

        val rawResult = engine.translate(trimmed, sourceLang, targetLang)

        // 英文音标智能解析与开放词典极速补充
        val result = if (rawResult.isSuccess && rawResult.translatedText.isNotBlank()) {
            val transText = rawResult.translatedText
            val isTargetEnglish = targetLang.code.startsWith("en", ignoreCase = true) ||
                    (!com.puretrans.app.util.LanguageDetector.containsChinese(transText) &&
                            com.puretrans.app.util.LanguageDetector.detectLanguage(transText) == Language.EN)

            val currentPhonetic = rawResult.phonetic
            val enrichedPhonetic = if (isTargetEnglish) {
                val formattedExisting = com.puretrans.app.util.PhoneticHelper.formatIpaPhonetic(currentPhonetic, true)
                formattedExisting ?: com.puretrans.app.util.PhoneticHelper.fetchEnglishPhonetic(transText)
            } else {
                currentPhonetic?.takeIf { it.isNotBlank() }
            }

            if (enrichedPhonetic != currentPhonetic) {
                rawResult.copy(phonetic = enrichedPhonetic)
            } else {
                rawResult
            }
        } else {
            rawResult
        }

        // 成功时自动异步记录到本地历史数据库
        if (result.isSuccess && result.translatedText.isNotBlank()) {
            historyDatabase.insertOrUpdate(
                HistoryItem(
                    sourceText = trimmed,
                    translatedText = result.translatedText,
                    sourceLangCode = sourceLang.code,
                    targetLangCode = targetLang.code,
                    engineLabel = result.activeEngineLabel,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        result
    }
}
