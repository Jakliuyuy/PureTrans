package com.puretrans.app.data.model

data class HistoryItem(
    val id: Long = System.currentTimeMillis(),
    val sourceText: String,
    val translatedText: String,
    val sourceLangCode: String,
    val targetLangCode: String,
    val engineLabel: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
