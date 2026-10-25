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

        val result = engine.translate(trimmed, sourceLang, targetLang)

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
