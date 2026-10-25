package com.puretrans.app.ui.main

import com.puretrans.app.data.model.Language

data class MainUiState(
    val sourceLanguage: Language = Language.AUTO,
    val targetLanguage: Language = Language.ZH,
    val sourceLanguageName: String = Language.AUTO.displayName,
    val targetLanguageName: String = Language.ZH.displayName,
    val inputText: String = "",
    val translatedText: String = "",
    val phonetic: String? = null,
    val clipboardPreview: String = "",
    val isLoading: Boolean = false,
    val isDirectMode: Boolean = false,
    val activeEngineLabel: String = "免密",
    val latencyMs: Long = 0,
    val isFavorite: Boolean = false,
    val isAutoDetecting: Boolean = true,
    val isSpeakingSource: Boolean = false,
    val isSpeakingTarget: Boolean = false,
    val errorMessage: String? = null
)
