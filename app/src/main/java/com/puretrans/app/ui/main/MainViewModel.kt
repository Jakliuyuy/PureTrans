package com.puretrans.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puretrans.app.PureTransApp
import com.puretrans.app.data.model.Language
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PureTransApp
    private val router = app.router
    private val historyDb = app.historyDatabase
    private val settingsManager = app.settingsManager

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
    }

    fun onInputTextChanged(newText: String) {
        _uiState.update {
            it.copy(
                inputText = newText,
                isFavorite = false,
                errorMessage = null
            )
        }

        translateJob?.cancel()
        if (newText.trim().isEmpty()) {
            _uiState.update {
                it.copy(
                    translatedText = "",
                    phonetic = null,
                    isLoading = false,
                    errorMessage = null
                )
            }
            return
        }

        translateJob = viewModelScope.launch {
            delay(500) // 防抖 500ms
            performTranslate()
        }
    }

    fun onClearInput() {
        translateJob?.cancel()
        _uiState.update {
            it.copy(
                inputText = "",
                translatedText = "",
                phonetic = null,
                isLoading = false,
                errorMessage = null,
                isFavorite = false
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
        _uiState.update {
            it.copy(
                sourceLanguage = lang,
                sourceLanguageName = lang.displayName
            )
        }
        if (_uiState.value.inputText.isNotEmpty()) {
            triggerImmediateTranslate()
        }
    }

    fun onSelectTargetLanguage(lang: Language) {
        _uiState.update {
            it.copy(
                targetLanguage = lang,
                targetLanguageName = lang.displayName
            )
        }
        if (_uiState.value.inputText.isNotEmpty()) {
            triggerImmediateTranslate()
        }
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
