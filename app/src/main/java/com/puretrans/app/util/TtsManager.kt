package com.puretrans.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder
import java.util.Locale

/**
 * 原生 Android TTS 与在线免密音频流（Cloud Audio Fallback）双模朗读管理引擎
 * 针对国内国产 ROM（OPPO ColorOS、小米 HyperOS、vivo OriginOS 等）深度适配：
 * 1. 支持 Android 11+ 软件包可见性与多引擎动态查询；
 * 2. 本地 TTS 与在线音频流双模互补无缝切换：
 *    - 本地 TTS 可用且支持目标语种语音包时优先调用本地离线合成；
 *    - 若本地 TTS 未初始化、返回 LANG_NOT_SUPPORTED / LANG_MISSING_DATA，或本地播放超时静音/异常，
 *      立即自动无缝切换为在线免密音频流兜底（有道极速国内直连 + Google TTS 双通道）；
 * 3. 完整的播放状态追踪与 Toggle 切换；
 * 4. 朗读触发时提供轻量明确的状态 Toast 提示，彻底解决“点击没反应、静音无声”问题。
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

    // 在线音频流播放器与看门狗定时器
    private var mediaPlayer: MediaPlayer? = null
    private var watchdogRunnable: Runnable? = null
    private var currentToast: Toast? = null

    private var lastSpokenText: String? = null
    private var lastSpokenLang: String? = null
    private var lastOnError: (String) -> Unit = {}

    init {
        initTts()
    }

    private fun initTts() {
        if (isInitializing) return
        isInitializing = true
        try {
            tts = TextToSpeech(appContext) { status ->
                isInitializing = false
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    setupTtsListeners()
                    pendingSpeakAction?.invoke()
                    pendingSpeakAction = null
                } else {
                    isInitialized = false
                }
            }
        } catch (_: Exception) {
            isInitializing = false
            isInitialized = false
        }
    }

    private fun setupTtsListeners() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                cancelWatchdog()
                mainHandler.post {
                    _isSpeaking.value = true
                    _currentUtteranceId.value = utteranceId
                }
            }

            override fun onDone(utteranceId: String?) {
                cancelWatchdog()
                mainHandler.post {
                    if (_currentUtteranceId.value == utteranceId) {
                        _isSpeaking.value = false
                        _currentUtteranceId.value = null
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handleLocalError(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handleLocalError(utteranceId)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                cancelWatchdog()
                mainHandler.post {
                    if (_currentUtteranceId.value == utteranceId) {
                        _isSpeaking.value = false
                        _currentUtteranceId.value = null
                    }
                }
            }
        })
    }

    private fun handleLocalError(utteranceId: String?) {
        cancelWatchdog()
        val text = lastSpokenText
        val lang = lastSpokenLang
        if (!text.isNullOrBlank() && !lang.isNullOrBlank()) {
            val candidates = getOnlineAudioCandidates(lang, text)
            playOnlineAudio(candidates, utteranceId ?: "fallback_tts", lastOnError)
        } else {
            mainHandler.post {
                if (_currentUtteranceId.value == utteranceId) {
                    _isSpeaking.value = false
                    _currentUtteranceId.value = null
                }
            }
        }
    }

    /**
     * 将业务语言代码转换为标准 Java Locale
     */
    fun getLocaleForLanguage(langCode: String, sampleText: String = ""): Locale =
        Companion.getLocaleForLanguage(langCode, sampleText)

    /**
     * 获取在线免密音频流候选地址（多源保障）
     */
    fun getOnlineAudioCandidates(langCode: String, text: String): List<String> =
        Companion.getOnlineAudioCandidates(langCode, text)

    /**
     * 双模朗读文本
     * 本地 TTS 优先，缺包/失败/无声自动切在线音频流兜底
     */
    fun speak(
        text: String,
        langCode: String,
        utteranceId: String = "tts_${System.currentTimeMillis()}",
        onError: (String) -> Unit = {}
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        lastSpokenText = cleanText
        lastSpokenLang = langCode
        lastOnError = onError

        stop()
        showToast("正在朗读...")

        val locale = getLocaleForLanguage(langCode, cleanText)
        val onlineCandidates = getOnlineAudioCandidates(langCode, cleanText)

        val engine = tts
        val isLocalEngineUsable = isInitialized && engine != null && try {
            val availability = engine.isLanguageAvailable(locale)
            availability >= TextToSpeech.LANG_AVAILABLE
        } catch (_: Exception) {
            false
        }

        if (isLocalEngineUsable && engine != null) {
            try {
                engine.language = locale
                engine.setSpeechRate(1.0f)
                engine.setPitch(1.0f)

                _currentUtteranceId.value = utteranceId

                // 设置 1500ms 看门狗定时器：若某些国产机本地引擎返回成功却静音卡死未触发 onStart，无缝切换在线流
                val watchdog = Runnable {
                    if (!_isSpeaking.value && _currentUtteranceId.value == utteranceId) {
                        try {
                            engine.stop()
                        } catch (_: Exception) {}
                        playOnlineAudio(onlineCandidates, utteranceId, onError)
                    }
                }
                watchdogRunnable = watchdog
                mainHandler.postDelayed(watchdog, 1500)

                val speakResult = engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (speakResult != TextToSpeech.SUCCESS) {
                    cancelWatchdog()
                    playOnlineAudio(onlineCandidates, utteranceId, onError)
                }
            } catch (_: Exception) {
                cancelWatchdog()
                playOnlineAudio(onlineCandidates, utteranceId, onError)
            }
        } else {
            // 本地 TTS 缺少语言包或未准备好，立即转入免密在线音频流播放
            playOnlineAudio(onlineCandidates, utteranceId, onError)
        }
    }

    /**
     * 播放/停止切换 (Toggle)
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
     * 播放在线免密音频流（支持多源自动轮询兜底）
     */
    private fun playOnlineAudio(
        candidateUrls: List<String>,
        utteranceId: String,
        onError: (String) -> Unit
    ) {
        if (candidateUrls.isEmpty()) {
            mainHandler.post {
                if (_currentUtteranceId.value == utteranceId) {
                    _isSpeaking.value = false
                    _currentUtteranceId.value = null
                }
                onError("语音朗读网络异常，请检查网络连接")
            }
            return
        }

        val currentUrl = candidateUrls.first()
        val remainingUrls = candidateUrls.drop(1)

        mainHandler.post {
            _isSpeaking.value = true
            _currentUtteranceId.value = utteranceId
        }

        Thread {
            try {
                releaseMediaPlayer()
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                player.setDataSource(currentUrl)

                player.setOnPreparedListener { mp ->
                    try {
                        mp.start()
                        mainHandler.post {
                            _isSpeaking.value = true
                            _currentUtteranceId.value = utteranceId
                        }
                    } catch (_: Exception) {
                        releaseMediaPlayer()
                        playOnlineAudio(remainingUrls, utteranceId, onError)
                    }
                }

                player.setOnCompletionListener {
                    mainHandler.post {
                        if (_currentUtteranceId.value == utteranceId) {
                            _isSpeaking.value = false
                            _currentUtteranceId.value = null
                        }
                    }
                    releaseMediaPlayer()
                }

                player.setOnErrorListener { _, _, _ ->
                    releaseMediaPlayer()
                    mainHandler.post {
                        playOnlineAudio(remainingUrls, utteranceId, onError)
                    }
                    true
                }

                player.prepareAsync()
                synchronized(this) {
                    mediaPlayer = player
                }
            } catch (_: Exception) {
                releaseMediaPlayer()
                mainHandler.post {
                    playOnlineAudio(remainingUrls, utteranceId, onError)
                }
            }
        }.start()
    }

    private fun cancelWatchdog() {
        watchdogRunnable?.let {
            mainHandler.removeCallbacks(it)
            watchdogRunnable = null
        }
    }

    private fun showToast(msg: String) {
        mainHandler.post {
            try {
                currentToast?.cancel()
                currentToast = Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).apply { show() }
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    /**
     * 立即停止朗读
     */
    fun stop() {
        cancelWatchdog()
        try {
            tts?.stop()
        } catch (_: Exception) {}
        releaseMediaPlayer()
        _isSpeaking.value = false
        _currentUtteranceId.value = null
    }

    /**
     * 释放所有资源
     */
    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }

    companion object {
        @Volatile
        private var instance: TtsManager? = null

        fun getInstance(context: Context): TtsManager {
            return instance ?: synchronized(this) {
                instance ?: TtsManager(context.applicationContext).also { instance = it }
            }
        }

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

        fun getOnlineAudioCandidates(langCode: String, text: String): List<String> {
            val cleanText = text.trim()
            if (cleanText.isEmpty()) return emptyList()

            val textForUrl = if (cleanText.length > 250) cleanText.take(250) else cleanText
            val encodedText = try {
                URLEncoder.encode(textForUrl, "UTF-8")
            } catch (_: Exception) {
                return emptyList()
            }

            val code = if (langCode.isBlank() || langCode.equals("auto", ignoreCase = true)) {
                val detected = LanguageDetector.detectLanguage(cleanText)
                detected.code
            } else {
                langCode.trim().lowercase()
            }

            val googleCode = when (code) {
                "zh", "zh-cn", "zh-hans" -> "zh-CN"
                "zh-tw", "zh-hant" -> "zh-TW"
                "en", "en-us", "en-gb" -> "en"
                "ja", "jp" -> "ja"
                "ko", "kor" -> "ko"
                "fr", "fra" -> "fr"
                "de", "ger" -> "de"
                "ru", "rus" -> "ru"
                "es", "spa" -> "es"
                "it", "ita" -> "it"
                "pt", "por" -> "pt"
                "ar", "ara" -> "ar"
                else -> code
            }
            val googleUrl = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=$googleCode&q=$encodedText"

            val youdaoUrl = when (code) {
                "en", "en-us", "en-gb" -> "https://dict.youdao.com/dictvoice?audio=$encodedText&type=2"
                "zh", "zh-cn", "zh-hans", "zh-tw", "zh-hant" -> "https://dict.youdao.com/dictvoice?audio=$encodedText&le=zh"
                "ja", "jp" -> "https://dict.youdao.com/dictvoice?audio=$encodedText&le=jap"
                "ko", "kor" -> "https://dict.youdao.com/dictvoice?audio=$encodedText&le=ko"
                "fr", "fra" -> "https://dict.youdao.com/dictvoice?audio=$encodedText&le=fr"
                "es", "spa" -> "https://dict.youdao.com/dictvoice?audio=$encodedText&le=es"
                "de", "ger" -> "https://dict.youdao.com/dictvoice?audio=$encodedText&le=de"
                "ru", "rus" -> "https://dict.youdao.com/dictvoice?audio=$encodedText&le=ru"
                else -> "https://dict.youdao.com/dictvoice?audio=$encodedText"
            }

            return when (code) {
                "zh", "zh-cn", "zh-hans", "zh-tw", "zh-hant",
                "en", "en-us", "en-gb",
                "ja", "jp", "ko", "kor", "fr", "fra", "es", "spa", "de", "ger" -> {
                    listOf(youdaoUrl, googleUrl)
                }
                else -> {
                    listOf(googleUrl, youdaoUrl)
                }
            }
        }
    }
}
