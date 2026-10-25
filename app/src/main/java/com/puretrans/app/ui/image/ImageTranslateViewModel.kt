package com.puretrans.app.ui.image

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puretrans.app.PureTransApp
import com.puretrans.app.data.model.Language
import com.puretrans.app.util.LanguageDetector
import com.puretrans.app.util.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max

class ImageTranslateViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PureTransApp
    private val router = app.router
    private val ttsManager = TtsManager.getInstance(application)

    private val _uiState = MutableStateFlow(ImageTranslateUiState())
    val uiState: StateFlow<ImageTranslateUiState> = _uiState.asStateFlow()

    private var cachedRawItems: List<ImageTranslateProcessor.RawOcrItem> = emptyList()

    fun loadFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    imageUri = uri,
                    isProcessing = true,
                    processStepText = "正在载入图片...",
                    errorMessage = null,
                    selectedBlock = null,
                    textBlocks = emptyList()
                )
            }

            val decodedBitmap = decodeSampledBitmapFromUri(uri)
            if (decodedBitmap == null) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "图片加载失败，请重试"
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(bitmap = decodedBitmap) }
            runOcrAndTranslate(decodedBitmap)
        }
    }

    fun loadFromBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    bitmap = bitmap,
                    isProcessing = true,
                    processStepText = "正在扫描图片中的文字...",
                    errorMessage = null,
                    selectedBlock = null,
                    textBlocks = emptyList()
                )
            }
            runOcrAndTranslate(bitmap)
        }
    }

    private suspend fun runOcrAndTranslate(bitmap: Bitmap) {
        try {
            _uiState.update { it.copy(processStepText = "正在识别文字位置...") }

            val rawItems = ImageTranslateProcessor.recognizeText(bitmap)
            cachedRawItems = rawItems

            if (rawItems.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "未在图片中检测到文字，请换一张光线充足、文字清晰的图片"
                    )
                }
                return
            }

            // 智能双向自动语种识别：分析全部文字
            val fullText = rawItems.joinToString("\n") { it.originalText }
            val (autoSource, autoTarget) = LanguageDetector.determineAutoDirection(fullText)

            _uiState.update {
                it.copy(
                    sourceLanguage = autoSource,
                    targetLanguage = autoTarget,
                    sourceLanguageName = autoSource.displayName,
                    targetLanguageName = autoTarget.displayName,
                    processStepText = "正在进行 1:1 原位翻译..."
                )
            }

            val overlays = ImageTranslateProcessor.translateBlocks(
                rawItems = rawItems,
                sourceLang = autoSource,
                targetLang = autoTarget,
                router = router
            )

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    textBlocks = overlays,
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    errorMessage = "识别与翻译出错: ${e.localizedMessage ?: "未知错误"}"
                )
            }
        }
    }

    fun changeTargetLanguage(target: Language) {
        if (_uiState.value.targetLanguage == target) return
        _uiState.update {
            it.copy(
                targetLanguage = target,
                targetLanguageName = target.displayName
            )
        }
        retranslateCachedBlocks()
    }

    fun changeSourceLanguage(source: Language) {
        if (_uiState.value.sourceLanguage == source) return
        _uiState.update {
            it.copy(
                sourceLanguage = source,
                sourceLanguageName = source.displayName
            )
        }
        retranslateCachedBlocks()
    }

    private fun retranslateCachedBlocks() {
        if (cachedRawItems.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processStepText = "正在重新翻译..."
                )
            }
            try {
                val overlays = ImageTranslateProcessor.translateBlocks(
                    rawItems = cachedRawItems,
                    sourceLang = _uiState.value.sourceLanguage,
                    targetLang = _uiState.value.targetLanguage,
                    router = router
                )
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        textBlocks = overlays,
                        selectedBlock = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "重新翻译失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun toggleShowOriginal() {
        _uiState.update { it.copy(showOriginal = !it.showOriginal) }
    }

    fun selectBlock(block: TextBlockOverlay?) {
        _uiState.update { it.copy(selectedBlock = block) }
    }

    fun speakText(text: String, langCode: String) {
        ttsManager.speak(text, langCode)
    }

    private suspend fun decodeSampledBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = app.contentResolver
            var input: InputStream? = contentResolver.openInputStream(uri)

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(input, null, options)
            input?.close()

            val maxDimension = 2048
            var inSampleSize = 1
            val maxOutDim = kotlin.math.max(options.outHeight, options.outWidth)
            while ((maxOutDim / inSampleSize) > maxDimension) {
                inSampleSize *= 2
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            input = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(input, null, options)
            input?.close()
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}
