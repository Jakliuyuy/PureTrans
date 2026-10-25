package com.puretrans.app.ui.image

import android.graphics.Bitmap
import android.net.Uri
import com.puretrans.app.data.model.Language

data class ImageTranslateUiState(
    val imageUri: Uri? = null,
    val bitmap: Bitmap? = null,
    val isProcessing: Boolean = false,
    val processStepText: String = "",
    val sourceLanguage: Language = Language.AUTO,
    val targetLanguage: Language = Language.ZH,
    val sourceLanguageName: String = Language.AUTO.displayName,
    val targetLanguageName: String = Language.ZH.displayName,
    val textBlocks: List<TextBlockOverlay> = emptyList(),
    val showOriginal: Boolean = false,
    val selectedBlock: TextBlockOverlay? = null,
    val isSpeaking: Boolean = false,
    val errorMessage: String? = null
)
