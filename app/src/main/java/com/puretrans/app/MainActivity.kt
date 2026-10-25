package com.puretrans.app

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.puretrans.app.ui.fullscreen.FullScreenActivity
import com.puretrans.app.ui.history.HistoryScreen
import com.puretrans.app.ui.main.MainScreen
import com.puretrans.app.ui.main.MainViewModel
import com.puretrans.app.ui.settings.SettingsScreen
import com.puretrans.app.ui.settings.SettingsViewModel
import com.puretrans.app.ui.theme.PureTransTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private var tts: TextToSpeech? = null

    // 语音输入识别 Launcher
    private val speechRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull().orEmpty()
            if (spokenText.isNotEmpty()) {
                mainViewModel.onInputTextChanged(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 TTS 朗读引擎
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }

        setContent {
            PureTransTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf(Screen.MAIN) }
                    val mainUiState by mainViewModel.uiState.collectAsState()
                    val settingsUiState by settingsViewModel.uiState.collectAsState()

                    // 检查剪贴板内容
                    DisposableEffect(Unit) {
                        checkClipboard()
                        onDispose { }
                    }

                    when (currentScreen) {
                        Screen.MAIN -> {
                            MainScreen(
                                uiState = mainUiState,
                                onInputTextChanged = mainViewModel::onInputTextChanged,
                                onSwapLanguages = mainViewModel::onSwapLanguages,
                                onSelectSourceLanguage = mainViewModel::onSelectSourceLanguage,
                                onSelectTargetLanguage = mainViewModel::onSelectTargetLanguage,
                                onClearInput = mainViewModel::onClearInput,
                                onSpeakSource = {
                                    speakText(mainUiState.inputText, mainUiState.sourceLanguage.code)
                                },
                                onSpeakTarget = {
                                    speakText(mainUiState.translatedText, mainUiState.targetLanguage.code)
                                },
                                onToggleFavorite = mainViewModel::onToggleFavorite,
                                onOpenFullScreen = {
                                    if (mainUiState.translatedText.isNotEmpty()) {
                                        FullScreenActivity.start(
                                            this@MainActivity,
                                            mainUiState.translatedText,
                                            mainUiState.targetLanguage.displayName
                                        )
                                    }
                                },
                                onOpenHistory = { currentScreen = Screen.HISTORY },
                                onOpenSettings = {
                                    settingsViewModel.loadSettings()
                                    currentScreen = Screen.SETTINGS
                                },
                                onOpenCameraOcr = {
                                    Toast.makeText(this@MainActivity, "相机/取词功能：支持划词翻译或调用系统取景", Toast.LENGTH_SHORT).show()
                                },
                                onStartVoiceInput = {
                                    startVoiceRecognition()
                                }
                            )
                        }

                        Screen.SETTINGS -> {
                            SettingsScreen(
                                uiState = settingsUiState,
                                onBaiduAppIdChanged = settingsViewModel::onBaiduAppIdChanged,
                                onBaiduSecretKeyChanged = settingsViewModel::onBaiduSecretKeyChanged,
                                onDeepseekApiKeyChanged = settingsViewModel::onDeepseekApiKeyChanged,
                                onBackendUrlChanged = settingsViewModel::onBackendUrlChanged,
                                onBackendTokenChanged = settingsViewModel::onBackendTokenChanged,
                                onToggleBaiduSecretVisibility = settingsViewModel::toggleBaiduSecretVisibility,
                                onToggleDeepseekSecretVisibility = settingsViewModel::toggleDeepseekSecretVisibility,
                                onToggleBackendTokenVisibility = settingsViewModel::toggleBackendTokenVisibility,
                                onSaveSettings = {
                                    settingsViewModel.saveSettings()
                                    currentScreen = Screen.MAIN
                                },
                                onClearAllKeys = settingsViewModel::clearAllKeys,
                                onSmartParseClipboard = settingsViewModel::smartParseClipboard,
                                onDismissToast = settingsViewModel::dismissToast,
                                onNavigateBack = { currentScreen = Screen.MAIN }
                            )
                        }

                        Screen.HISTORY -> {
                            HistoryScreen(
                                onNavigateBack = { currentScreen = Screen.MAIN },
                                onSpeak = { text -> speakText(text) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkClipboard()
    }

    private fun checkClipboard() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = cm.primaryClip?.getItemAt(0)
            val clipText = item?.text?.toString().orEmpty()
            mainViewModel.updateClipboardPreview(clipText)
        } catch (_: Exception) {
        }
    }

    private fun speakText(text: String, langCode: String = "") {
        if (text.isBlank()) return
        val locale = when (langCode.lowercase()) {
            "zh", "zh-cn" -> Locale.CHINESE
            "en" -> Locale.ENGLISH
            "ja" -> Locale.JAPANESE
            "ko" -> Locale.KOREAN
            "fr" -> Locale.FRENCH
            "de" -> Locale.GERMAN
            else -> Locale.getDefault()
        }
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "main_tts")
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...")
        }
        try {
            speechRecognitionLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "当前设备不支持系统语音识别", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }

    enum class Screen {
        MAIN, SETTINGS, HISTORY
    }
}
