package com.puretrans.app.data.engine

import com.puretrans.app.data.model.Language
import com.puretrans.app.data.model.TranslationResult

interface TranslationEngine {
    val engineName: String
    val isDirect: Boolean

    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): TranslationResult
}
