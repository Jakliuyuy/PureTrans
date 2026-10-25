package com.puretrans.app.data.model

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val activeEngineLabel: String,
    val latencyMs: Long,
    val phonetic: String? = null,
    val isDirectMode: Boolean = false,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)
