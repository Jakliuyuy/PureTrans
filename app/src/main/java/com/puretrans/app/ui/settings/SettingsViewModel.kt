package com.puretrans.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.puretrans.app.PureTransApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PureTransApp
    private val settingsManager = app.settingsManager

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        val snapshot = settingsManager.loadSettingsSnapshot()
        _uiState.update {
            it.copy(
                baiduAppId = snapshot.baiduAppId,
                baiduSecretKey = snapshot.baiduSecretKey,
                deepseekApiKey = snapshot.deepseekApiKey,
                backendUrl = snapshot.backendUrl,
                backendToken = snapshot.backendToken,
                isDirectMode = snapshot.isDirectMode
            )
        }
    }

    fun onBaiduAppIdChanged(value: String) {
        _uiState.update { it.copy(baiduAppId = value) }
        autoCheckDirectMode()
    }

    fun onBaiduSecretKeyChanged(value: String) {
        _uiState.update { it.copy(baiduSecretKey = value) }
        autoCheckDirectMode()
    }

    fun onDeepseekApiKeyChanged(value: String) {
        _uiState.update { it.copy(deepseekApiKey = value) }
        autoCheckDirectMode()
    }

    fun onBackendUrlChanged(value: String) {
        _uiState.update { it.copy(backendUrl = value) }
    }

    fun onBackendTokenChanged(value: String) {
        _uiState.update { it.copy(backendToken = value) }
    }

    fun toggleBaiduSecretVisibility() {
        _uiState.update { it.copy(showBaiduSecret = !it.showBaiduSecret) }
    }

    fun toggleDeepseekSecretVisibility() {
        _uiState.update { it.copy(showDeepseekSecret = !it.showDeepseekSecret) }
    }

    fun toggleBackendTokenVisibility() {
        _uiState.update { it.copy(showBackendTokenSecret = !it.showBackendTokenSecret) }
    }

    private fun autoCheckDirectMode() {
        val current = _uiState.value
        val isDirect = (current.baiduAppId.isNotBlank() && current.baiduSecretKey.isNotBlank()) ||
                current.deepseekApiKey.isNotBlank()
        _uiState.update { it.copy(isDirectMode = isDirect) }
    }

    fun saveSettings() {
        val s = _uiState.value
        settingsManager.saveSettings(
            baiduAppId = s.baiduAppId,
            baiduSecretKey = s.baiduSecretKey,
            deepseekApiKey = s.deepseekApiKey,
            backendUrl = s.backendUrl,
            backendToken = s.backendToken
        )
        loadSettings()
        _uiState.update { it.copy(toastMessage = "设置已保存") }
    }

    fun clearAllKeys() {
        settingsManager.clearAllKeys()
        loadSettings()
        _uiState.update { it.copy(toastMessage = "已清空所有 Key，恢复免密模式") }
    }

    fun smartParseClipboard(clipboardContent: String) {
        val trimmed = clipboardContent.trim()
        if (trimmed.isEmpty()) return

        // 识别常见的 DeepSeek Key 格式 (sk-...)
        if (trimmed.startsWith("sk-")) {
            _uiState.update { it.copy(deepseekApiKey = trimmed) }
            autoCheckDirectMode()
            _uiState.update { it.copy(toastMessage = "已识别 DeepSeek API Key") }
            return
        }

        // 识别百度格式 (如 AppID:Key 或多行)
        if (trimmed.contains(":") || trimmed.contains(",") || trimmed.contains(" ")) {
            val parts = trimmed.split("[: , \n\t]+".toRegex()).filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                _uiState.update {
                    it.copy(
                        baiduAppId = parts[0],
                        baiduSecretKey = parts[1]
                    )
                }
                autoCheckDirectMode()
                _uiState.update { it.copy(toastMessage = "已识别百度翻译 AppID 与 Key") }
                return
            }
        }

        _uiState.update { it.copy(toastMessage = "剪贴板未检测到常见 API Key 格式") }
    }

    fun dismissToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
