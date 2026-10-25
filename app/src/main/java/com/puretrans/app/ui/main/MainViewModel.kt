package com.puretrans.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puretrans.app.PureTransApp
import com.puretrans.app.data.model.Language
import com.puretrans.app.util.LanguageDetector
import com.puretrans.app.util.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PureTransApp
    private val router = app.router
    private val historyDb = app.historyDatabase
    private val settingsManager = app.settingsManager
    private val ttsManager = TtsManager.getInstance(application)

    private val _uiState = MutableStateFlow(
        MainUiState(
            isDirectMode = router.isDirectMode(),
            activeEngineLabel = if (router.isDirectMode()) "直连" else "免密"
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var translateJob: Job? = null

    init {
        // 监听设置变化同步更新直连/免密微标
        viewModelScope.launch {
            settingsManager.settingsFlow.collect { snapshot ->
                _uiState.update { it.copy(isDirectMode = snapshot.isDirectMode) }
            }
        }

        // 监听 TTS 播放状态
        viewModelScope.launch {
            combine(ttsManager.isSpeaking, ttsManager.currentUtteranceId) { speaking, id ->
                Pair(speaking, id)
            }.collect { (speaking, id) ->
                _uiState.update {
                    it.copy(
                        isSpeakingSource = speaking && id == "main_source_tts",
                        isSpeakingTarget = speaking && id == "main_target_tts"
                    )
                }
            }
        }
    }

    fun onInputTextChanged(newText: String) {
        val trimmed = newText.trim()

        if (trimmed.isEmpty()) {
            translateJob?.cancel()
            _uiState.update {
                it.copy(
                    inputText = newText,
                    translatedText = "",
                    phonetic = null,
                    isLoading = false,
                    errorMessage = null,
                    isFavorite = false,
                    isAutoDetecting = true,
                    sourceLanguage = Language.AUTO,
                    targetLanguage = Language.ZH,
                    sourceLanguageName = Language.AUTO.displayName,
                    targetLanguageName = Language.ZH.displayName
                )
            }
            return
        }

        // 智能双向自动语种识别与目标语言匹配：
        // 处于自动检测模式时进行文本语种判定：
        // 只要输入的是中文（包含中文字符），自动将目标语言设为英文（zh -> en）；
        // 只要输入的是其他国家语言（非中文，如英文、日文、西文等），自动将目标语言设为中文（auto -> zh）
        if (_uiState.value.isAutoDetecting) {
            val (autoSource, autoTarget) = LanguageDetector.determineAutoDirection(trimmed)
            _uiState.update {
                it.copy(
                    inputText = newText,
                    sourceLanguage = autoSource,
                    targetLanguage = autoTarget,
                    sourceLanguageName = autoSource.displayName,
                    targetLanguageName = autoTarget.displayName,
                    isFavorite = false,
                    errorMessage = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    inputText = newText,
                    isFavorite = false,
                    errorMessage = null
                )
            }
        }

        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            delay(500) // 防抖 500ms
            performTranslate()
        }
    }

    fun onClearInput() {
        translateJob?.cancel()
        ttsManager.stop()
        _uiState.update {
            it.copy(
                inputText = "",
                translatedText = "",
                phonetic = null,
                isLoading = false,
                errorMessage = null,
                isFavorite = false,
                isAutoDetecting = true,
                sourceLanguage = Language.AUTO,
                targetLanguage = Language.ZH,
                sourceLanguageName = Language.AUTO.displayName,
                targetLanguageName = Language.ZH.displayName
            )
        }
    }

    fun onSwapLanguages() {
        val currentSource = _uiState.value.sourceLanguage
        val currentTarget = _uiState.value.targetLanguage

        val newSource: Language
        val newTarget: Language

        if (currentSource.code.equals("auto", ignoreCase = true)) {
            newSource = currentTarget
            newTarget = if (currentTarget.code.equals("zh", ignoreCase = true)) Language.EN else Language.ZH
        } else {
            newSource = currentTarget
            newTarget = currentSource
        }

        _uiState.update {
            it.copy(
                isAutoDetecting = false, // 用户手动互换后暂停自动推导
                sourceLanguage = newSource,
                targetLanguage = newTarget,
                sourceLanguageName = newSource.displayName,
                targetLanguageName = newTarget.displayName
            )
        }

        // 互换语言后，如果当前已有译文，可以直接将之前的译文作为新输入触发翻译
        val prevTrans = _uiState.value.translatedText
        if (prevTrans.isNotEmpty()) {
            onInputTextChanged(prevTrans)
        } else if (_uiState.value.inputText.isNotEmpty()) {
            triggerImmediateTranslate()
        }
    }

    fun onSelectSourceLanguage(lang: Language) {
        val isAuto = lang.code.equals("auto", ignoreCase = true)
        _uiState.update {
            it.copy(
                isAutoDetecting = isAuto,
                sourceLanguage = lang,
                sourceLanguageName = lang.displayName
            )
        }
        if (isAuto && _uiState.value.inputText.isNotBlank()) {
            // 重新按当前文本智能推导方向
            val (autoSource, autoTarget) = LanguageDetector.determineAutoDirection(_uiState.value.inputText)
            _uiState.update {
                it.copy(
                    sourceLanguage = autoSource,
                    targetLanguage = autoTarget,
                    sourceLanguageName = autoSource.displayName,
                    targetLanguageName = autoTarget.displayName
                )
            }
        }
        if (_uiState.value.inputText.isNotEmpty()) {
            triggerImmediateTranslate()
        }
    }

    fun onSelectTargetLanguage(lang: Language) {
        _uiState.update {
            it.copy(
                isAutoDetecting = false,
                targetLanguage = lang,
                targetLanguageName = lang.displayName
            )
        }
        if (_uiState.value.inputText.isNotEmpty()) {
            triggerImmediateTranslate()
        }
    }

    fun toggleSpeakSource(onError: (String) -> Unit = {}) {
        val text = _uiState.value.inputText
        val lang = _uiState.value.sourceLanguage.code
        ttsManager.toggleSpeak(text, lang, "main_source_tts", onError)
    }

    fun toggleSpeakTarget(onError: (String) -> Unit = {}) {
        val text = _uiState.value.translatedText
        val lang = _uiState.value.targetLanguage.code
        ttsManager.toggleSpeak(text, lang, "main_target_tts", onError)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun triggerImmediateTranslate() {
        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            performTranslate()
        }
    }

    private suspend fun performTranslate() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        val result = router.translate(
            text = text,
            sourceLang = _uiState.value.sourceLanguage,
            targetLang = _uiState.value.targetLanguage
        )

        _uiState.update {
            it.copy(
                isLoading = false,
                translatedText = result.translatedText,
                phonetic = result.phonetic,
                activeEngineLabel = result.activeEngineLabel,
                latencyMs = result.latencyMs,
                isDirectMode = result.isDirectMode,
                errorMessage = result.errorMessage
            )
        }
    }

    fun onToggleFavorite() {
        val src = _uiState.value.inputText.trim()
        val dst = _uiState.value.translatedText.trim()
        if (src.isEmpty() || dst.isEmpty()) return

        viewModelScope.launch {
            val isFav = historyDb.toggleFavorite(src, dst)
            _uiState.update { it.copy(isFavorite = isFav) }
        }
    }

    fun updateClipboardPreview(clipText: String) {
        val trimmed = clipText.trim()
        if (trimmed.isNotEmpty() && trimmed != _uiState.value.inputText) {
            _uiState.update { it.copy(clipboardPreview = trimmed) }
        } else {
            _uiState.update { it.copy(clipboardPreview = "") }
        }
    }
}
