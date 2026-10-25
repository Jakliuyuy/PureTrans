package com.puretrans.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "puretrans_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("puretrans_fallback_prefs", Context.MODE_PRIVATE)
    }

    private val _settingsFlow = MutableStateFlow(loadSettingsSnapshot())
    val settingsFlow: StateFlow<SettingsSnapshot> = _settingsFlow.asStateFlow()

    data class SettingsSnapshot(
        val baiduAppId: String,
        val baiduSecretKey: String,
        val deepseekApiKey: String,
        val backendUrl: String,
        val backendToken: String,
        val isDirectMode: Boolean
    )

    fun loadSettingsSnapshot(): SettingsSnapshot {
        val baiduAppId = prefs.getString(KEY_BAIDU_APP_ID, "")?.trim() ?: ""
        val baiduSecretKey = prefs.getString(KEY_BAIDU_SECRET_KEY, "")?.trim() ?: ""
        val deepseekApiKey = prefs.getString(KEY_DEEPSEEK_KEY, "")?.trim() ?: ""
        val backendUrl = prefs.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL)?.trim()
            ?: DEFAULT_BACKEND_URL
        val backendToken = prefs.getString(KEY_BACKEND_TOKEN, DEFAULT_BACKEND_TOKEN)?.trim()
            ?: DEFAULT_BACKEND_TOKEN

        val isDirectMode = (baiduAppId.isNotEmpty() && baiduSecretKey.isNotEmpty()) || deepseekApiKey.isNotEmpty()

        return SettingsSnapshot(
            baiduAppId = baiduAppId,
            baiduSecretKey = baiduSecretKey,
            deepseekApiKey = deepseekApiKey,
            backendUrl = if (backendUrl.isEmpty()) DEFAULT_BACKEND_URL else backendUrl,
            backendToken = if (backendToken.isEmpty()) DEFAULT_BACKEND_TOKEN else backendToken,
            isDirectMode = isDirectMode
        )
    }

    fun saveSettings(
        baiduAppId: String,
        baiduSecretKey: String,
        deepseekApiKey: String,
        backendUrl: String,
        backendToken: String
    ) {
        prefs.edit()
            .putString(KEY_BAIDU_APP_ID, baiduAppId.trim())
            .putString(KEY_BAIDU_SECRET_KEY, baiduSecretKey.trim())
            .putString(KEY_DEEPSEEK_KEY, deepseekApiKey.trim())
            .putString(KEY_BACKEND_URL, backendUrl.trim())
            .putString(KEY_BACKEND_TOKEN, backendToken.trim())
            .apply()

        _settingsFlow.value = loadSettingsSnapshot()
    }

    fun clearAllKeys() {
        prefs.edit()
            .remove(KEY_BAIDU_APP_ID)
            .remove(KEY_BAIDU_SECRET_KEY)
            .remove(KEY_DEEPSEEK_KEY)
            .putString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL)
            .putString(KEY_BACKEND_TOKEN, DEFAULT_BACKEND_TOKEN)
            .apply()

        _settingsFlow.value = loadSettingsSnapshot()
    }

    companion object {
        const val KEY_BAIDU_APP_ID = "baidu_app_id"
        const val KEY_BAIDU_SECRET_KEY = "baidu_secret_key"
        const val KEY_DEEPSEEK_KEY = "deepseek_api_key"
        const val KEY_BACKEND_URL = "custom_backend_url"
        const val KEY_BACKEND_TOKEN = "custom_backend_token"

        const val DEFAULT_BACKEND_URL = "https://api.yourdomain.com/google-translate"
        const val DEFAULT_BACKEND_TOKEN = "MySecretPin888"

        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
