package com.puretrans.app.ui.settings

data class SettingsUiState(
    val baiduAppId: String = "",
    val baiduSecretKey: String = "",
    val deepseekApiKey: String = "",
    val backendUrl: String = "",
    val backendToken: String = "",
    val isDirectMode: Boolean = false,
    val showBaiduSecret: Boolean = false,
    val showDeepseekSecret: Boolean = false,
    val showBackendTokenSecret: Boolean = false,
    val toastMessage: String? = null
)
