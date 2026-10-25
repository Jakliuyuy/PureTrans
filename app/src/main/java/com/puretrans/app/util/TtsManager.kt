package com.puretrans.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 原生 Android TTS (TextToSpeech) 引擎完整管理工具
 * 具备特性：
 * 1. 单例持有，避免重复创建引擎开销与内存泄漏
 * 2. 覆盖所有支持语种的精确 Locale 映射
 * 3. 完整的播放状态管理 (isSpeaking, currentUtteranceId)
 * 4. 健壮的语种可用性检测与缺包告警回调
 * 5. 支持点击播放/停止切换 (Toggle)
 */
class TtsManager private constructor(private val appContext: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentUtteranceId = MutableStateFlow<String?>(null)
    val currentUtteranceId: StateFlow<String?> = _currentUtteranceId.asStateFlow()

    private var isInitializing = false
    private var pendingSpeakAction: (() -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        if (isInitializing) return
        isInitializing = true
        tts = TextToSpeech(appContext) { status ->
            isInitializing = false
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post {
                            _isSpeaking.value = true
                            _currentUtteranceId.value = utteranceId
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            if (_currentUtteranceId.value == utteranceId) {
                                _isSpeaking.value = false
                                _currentUtteranceId.value = null
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            if (_currentUtteranceId.value == utteranceId) {
                                _isSpeaking.value = false
                                _currentUtteranceId.value = null
                            }
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        mainHandler.post {
                            if (_currentUtteranceId.value == utteranceId) {
                                _isSpeaking.value = false
                                _currentUtteranceId.value = null
                            }
                        }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        mainHandler.post {
                            if (_currentUtteranceId.value == utteranceId) {
                                _isSpeaking.value = false
                                _currentUtteranceId.value = null
                            }
                        }
                    }
                })

                pendingSpeakAction?.invoke()
                pendingSpeakAction = null
            } else {
                isInitialized = false
            }
        }
    }

    /**
     * 将业务语言代码转换为标准 Java Locale
     */
    fun getLocaleForLanguage(langCode: String, sampleText: String = ""): Locale {
        val code = langCode.trim().lowercase()
        return when (code) {
            "zh", "zh-cn", "zh-hans" -> Locale.SIMPLIFIED_CHINESE
            "zh-tw", "zh-hant" -> Locale.TRADITIONAL_CHINESE
            "en", "en-us" -> Locale.US
            "en-gb" -> Locale.UK
            "ja", "jp" -> Locale.JAPANESE
            "ko", "kor" -> Locale.KOREAN
            "fr", "fra" -> Locale.FRENCH
            "de", "ger" -> Locale.GERMAN
            "ru", "rus" -> Locale("ru")
            "es", "spa" -> Locale("es")
            "pt", "por" -> Locale("pt")
            "it", "ita" -> Locale.ITALIAN
            "ar", "ara" -> Locale("ar")
            "auto", "" -> {
                // 当处于自动语种时，基于待朗读文本字符特征智能推断最适合的 Locale
                val detected = LanguageDetector.detectLanguage(sampleText)
                when (detected) {
                    com.puretrans.app.data.model.Language.ZH -> Locale.SIMPLIFIED_CHINESE
                    com.puretrans.app.data.model.Language.JA -> Locale.JAPANESE
                    com.puretrans.app.data.model.Language.KO -> Locale.KOREAN
                    com.puretrans.app.data.model.Language.RU -> Locale("ru")
                    com.puretrans.app.data.model.Language.AR -> Locale("ar")
                    com.puretrans.app.data.model.Language.EN -> Locale.US
                    else -> Locale.US
                }
            }
            else -> {
                try {
                    Locale.forLanguageTag(langCode)
                } catch (_: Exception) {
                    Locale.getDefault()
                }
            }
        }
    }

    /**
     * 朗读文本
     * @param text 要朗读的内容
     * @param langCode 语种标识
     * @param utteranceId 标识本次朗读的任务 ID
     * @param onError 发生错误或缺少语音包时的回调
     */
    fun speak(
        text: String,
        langCode: String,
        utteranceId: String = "tts_${System.currentTimeMillis()}",
        onError: (String) -> Unit = {}
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        if (!isInitialized || tts == null) {
            pendingSpeakAction = {
                speak(cleanText, langCode, utteranceId, onError)
            }
            initTts()
            return
        }

        val locale = getLocaleForLanguage(langCode, cleanText)
        val engine = tts ?: return

        // 检查该语种支持情况
        val availability = engine.isLanguageAvailable(locale)
        if (availability == TextToSpeech.LANG_NOT_SUPPORTED || availability == TextToSpeech.LANG_MISSING_DATA) {
            onError("系统 TTS 缺少【${locale.displayLanguage}】语音包，可在系统设置中下载")
            // 尝试退回默认语言播报
            engine.language = Locale.getDefault()
        } else {
            engine.language = locale
        }

        // 设为普通语速与标准音调
        engine.setSpeechRate(1.0f)
        engine.setPitch(1.0f)

        engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * 播放/停止切换 (Toggle)
     * 如果正在朗读同一个 utteranceId，则立刻停止；否则开始朗读
     */
    fun toggleSpeak(
        text: String,
        langCode: String,
        utteranceId: String,
        onError: (String) -> Unit = {}
    ) {
        if (_isSpeaking.value && _currentUtteranceId.value == utteranceId) {
            stop()
        } else {
            speak(text, langCode, utteranceId, onError)
        }
    }

    /**
     * 立即停止朗读
     */
    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _isSpeaking.value = false
        _currentUtteranceId.value = null
    }

    /**
     * 释放 TTS 资源
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        isInitialized = false
        _isSpeaking.value = false
        _currentUtteranceId.value = null
    }

    companion object {
        @Volatile
        private var instance: TtsManager? = null

        fun getInstance(context: Context): TtsManager {
            return instance ?: synchronized(this) {
                instance ?: TtsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
