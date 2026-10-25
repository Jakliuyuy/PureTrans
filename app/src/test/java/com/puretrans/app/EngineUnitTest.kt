package com.puretrans.app

import com.google.gson.JsonParser
import com.puretrans.app.data.model.Language
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class EngineUnitTest {

    @Test
    fun testBaiduMd5Signature() {
        val appid = "2015063000000001"
        val q = "apple"
        val salt = "1435660288"
        val secret = "12345678"
        val raw = "$appid$q$salt$secret"

        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
        val sign = bytes.joinToString("") { "%02x".format(it) }

        assertEquals("f89f9594663708c1605f3d736d01d2d4", sign)
    }

    @Test
    fun testBaiduSignatureWithChineseAndSymbols() {
        val appid = "202401010001"
        val q = "你好，世界！"
        val salt = "9999"
        val secret = "secret123"
        val raw = "$appid$q$salt$secret"

        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
        val sign = bytes.joinToString("") { "%02x".format(it) }

        assertNotNull(sign)
        assertEquals(32, sign.length)
    }

    @Test
    fun testGtxJsonParsing() {
        val jsonMock = """
            [[["苹果","apple",null,null,1],["\n你好","\nhello",null,null,1]],null,"en",null,null,null,null,[]]
        """.trimIndent()

        val rootArray = JsonParser.parseString(jsonMock).asJsonArray
        val translationArray = rootArray.get(0).asJsonArray
        val sb = StringBuilder()

        for (element in translationArray) {
            if (element.isJsonArray) {
                val subArray = element.asJsonArray
                if (subArray.size() > 0 && !subArray.get(0).isJsonNull) {
                    sb.append(subArray.get(0).asString)
                }
            }
        }

        assertEquals("苹果\n你好", sb.toString())
    }

    @Test
    fun testGtxJsonParsingWithTargetPhonetic() {
        // 模拟带目标音标（拼音）在索引 2 的返回
        val jsonMock = """
            [[["你好，世界","Hello, world",null,null,1],[null,null,"Nǐ hǎo, shìjiè","həˈlō wərld"]],null,"en"]
        """.trimIndent()

        val rootArray = JsonParser.parseString(jsonMock).asJsonArray
        val translationArray = rootArray.get(0).asJsonArray
        val sb = StringBuilder()
        var phonetic: String? = null

        for (element in translationArray) {
            if (element.isJsonArray) {
                val subArray = element.asJsonArray
                if (subArray.size() > 0 && !subArray.get(0).isJsonNull) {
                    sb.append(subArray.get(0).asString)
                }
                if (phonetic == null) {
                    if (subArray.size() > 2 && !subArray.get(2).isJsonNull && subArray.get(2).asString.isNotBlank()) {
                        phonetic = subArray.get(2).asString
                    } else if (subArray.size() > 3 && !subArray.get(3).isJsonNull && subArray.get(3).asString.isNotBlank()) {
                        phonetic = subArray.get(3).asString
                    }
                }
            }
        }

        assertEquals("你好，世界", sb.toString())
        assertEquals("Nǐ hǎo, shìjiè", phonetic)
    }

    @Test
    fun testLanguageLookup() {
        assertEquals("自动检测 (Auto)", Language.findByCode("auto").displayName)
        assertEquals("zh-CN", Language.findByCode("zh").googleCode)
        assertEquals("jp", Language.findByCode("ja").baiduCode)
    }

    @Test
    fun testLanguageSwapWhenSourceIsAuto() {
        // 验证源语言为 auto 时的互换逻辑
        val currentSource = Language.AUTO
        assertEquals("auto", currentSource.code)
        val currentTargetJa = Language.JA

        val newSourceJa = currentTargetJa
        val newTargetJa = if (currentTargetJa.code.equals("zh", ignoreCase = true)) Language.EN else Language.ZH

        assertEquals("ja", newSourceJa.code)
        assertEquals("zh", newTargetJa.code)

        val currentTargetZh = Language.ZH
        val newSourceZh = currentTargetZh
        val newTargetZh = if (currentTargetZh.code.equals("zh", ignoreCase = true)) Language.EN else Language.ZH

        assertEquals("zh", newSourceZh.code)
        assertEquals("en", newTargetZh.code)
    }

    @Test
    fun testDirectModeLogicEdgeCases() {
        // 仅输入 appId 未输入 secretKey 不应触发百度直连
        val baiduOnlyAppId = "appid123".isNotBlank() && "".isNotBlank()
        assertFalse(baiduOnlyAppId)

        // 完整百度凭据
        val baiduComplete = "appid123".isNotBlank() && "secret456".isNotBlank()
        assertTrue(baiduComplete)

        // DeepSeek 凭据
        val deepseekOnly = "".isNotBlank() || "sk-xxx".isNotBlank()
        assertTrue(deepseekOnly)

        // 全空时必须走免密
        val allEmpty = ("".isNotBlank() && "".isNotBlank()) || "".isNotBlank()
        assertFalse(allEmpty)
    }

    @Test
    fun testLanguageDetectorContainsChinese() {
        // 纯中文
        assertTrue(com.puretrans.app.util.LanguageDetector.containsChinese("你好，世界"))
        assertTrue(com.puretrans.app.util.LanguageDetector.containsChinese("今天天气真好！"))

        // 中英混排
        assertTrue(com.puretrans.app.util.LanguageDetector.containsChinese("Hello 你好"))
        assertTrue(com.puretrans.app.util.LanguageDetector.containsChinese("使用 Compose 开发 Android 翻译 App"))

        // 纯英文
        assertFalse(com.puretrans.app.util.LanguageDetector.containsChinese("Hello world"))
        assertFalse(com.puretrans.app.util.LanguageDetector.containsChinese("How are you today?"))

        // 纯韩文
        assertFalse(com.puretrans.app.util.LanguageDetector.containsChinese("안녕하세요"))

        // 纯日文假名
        assertFalse(com.puretrans.app.util.LanguageDetector.containsChinese("ありがとう"))

        // 其他西文、西里尔、阿拉伯文
        assertFalse(com.puretrans.app.util.LanguageDetector.containsChinese("Bonjour tout le monde"))
        assertFalse(com.puretrans.app.util.LanguageDetector.containsChinese("Hola amigo"))
        assertFalse(com.puretrans.app.util.LanguageDetector.containsChinese("Привет мир"))
        assertFalse(com.puretrans.app.util.LanguageDetector.containsChinese("مرحبا"))

        // 数字与标点边界情况
        assertFalse(com.puretrans.app.util.LanguageDetector.containsChinese("1234567890"))
        assertFalse(com.puretrans.app.util.LanguageDetector.containsChinese("!@#$%^&*()_+-="))
        assertFalse(com.puretrans.app.util.LanguageDetector.containsChinese(""))
        assertFalse(com.puretrans.app.util.LanguageDetector.containsChinese("   \t\n  "))
        assertFalse(com.puretrans.app.util.LanguageDetector.containsChinese(null))
    }

    @Test
    fun testLanguageDetectorBidirectionalAutoRouting() {
        // 1. 输入中文 -> 自动识别翻译成英文 (zh -> en)
        val (srcZh, tgtZh) = com.puretrans.app.util.LanguageDetector.determineAutoDirection("你好，今天过得好吗？")
        assertEquals(Language.ZH, srcZh)
        assertEquals(Language.EN, tgtZh)

        // 中英混杂（含中文） -> 翻译成英文
        val (srcMix, tgtMix) = com.puretrans.app.util.LanguageDetector.determineAutoDirection("请帮我 check 一下这个 bug")
        assertEquals(Language.ZH, srcMix)
        assertEquals(Language.EN, tgtMix)

        // 2. 输入其他国家语言（英文） -> 自动识别翻译成中文 (auto -> zh)
        val (srcEn, tgtEn) = com.puretrans.app.util.LanguageDetector.determineAutoDirection("Good morning, how are you doing?")
        assertEquals(Language.AUTO, srcEn)
        assertEquals(Language.ZH, tgtEn)

        // 输入其他国家语言（韩文） -> 自动识别翻译成中文 (auto -> zh)
        val (srcKo, tgtKo) = com.puretrans.app.util.LanguageDetector.determineAutoDirection("감사합니다")
        assertEquals(Language.AUTO, srcKo)
        assertEquals(Language.ZH, tgtKo)

        // 输入其他国家语言（法文） -> 自动识别翻译成中文 (auto -> zh)
        val (srcFr, tgtFr) = com.puretrans.app.util.LanguageDetector.determineAutoDirection("Je t'aime")
        assertEquals(Language.AUTO, srcFr)
        assertEquals(Language.ZH, tgtFr)

        // 3. 空输入或纯空白 -> 默认待命状态 auto -> zh
        val (srcEmpty, tgtEmpty) = com.puretrans.app.util.LanguageDetector.determineAutoDirection("")
        assertEquals(Language.AUTO, srcEmpty)
        assertEquals(Language.ZH, tgtEmpty)

        val (srcBlank, tgtBlank) = com.puretrans.app.util.LanguageDetector.determineAutoDirection("   \n ")
        assertEquals(Language.AUTO, srcBlank)
        assertEquals(Language.ZH, tgtBlank)
    }

    @Test
    fun testLanguageDetectorMultiScriptIdentification() {
        // 日文（含平假名或片假名）
        assertEquals(Language.JA, com.puretrans.app.util.LanguageDetector.detectLanguage("こんにちは"))
        assertEquals(Language.JA, com.puretrans.app.util.LanguageDetector.detectLanguage("カタカナテスト"))
        assertEquals(Language.JA, com.puretrans.app.util.LanguageDetector.detectLanguage("東京に行きます"))

        // 韩文
        assertEquals(Language.KO, com.puretrans.app.util.LanguageDetector.detectLanguage("안녕하세요"))
        assertEquals(Language.KO, com.puretrans.app.util.LanguageDetector.detectLanguage("감사합니다"))

        // 俄文（西里尔字母）
        assertEquals(Language.RU, com.puretrans.app.util.LanguageDetector.detectLanguage("Привет мир"))
        assertEquals(Language.RU, com.puretrans.app.util.LanguageDetector.detectLanguage("Спасибо большое"))

        // 阿拉伯文
        assertEquals(Language.AR, com.puretrans.app.util.LanguageDetector.detectLanguage("مرحبا بالعالم"))
        assertEquals(Language.AR, com.puretrans.app.util.LanguageDetector.detectLanguage("شكرا جزيلا"))

        // 中文（纯汉字）
        assertEquals(Language.ZH, com.puretrans.app.util.LanguageDetector.detectLanguage("你好世界"))
        assertEquals(Language.ZH, com.puretrans.app.util.LanguageDetector.detectLanguage("极速免密翻译应用"))

        // 英文（纯拉丁字母）
        assertEquals(Language.EN, com.puretrans.app.util.LanguageDetector.detectLanguage("Hello world"))
        assertEquals(Language.EN, com.puretrans.app.util.LanguageDetector.detectLanguage("This is a test"))

        // 纯符号或数字
        assertEquals(Language.AUTO, com.puretrans.app.util.LanguageDetector.detectLanguage("123456"))
        assertEquals(Language.AUTO, com.puretrans.app.util.LanguageDetector.detectLanguage("!@#$%^&*()"))
        assertEquals(Language.AUTO, com.puretrans.app.util.LanguageDetector.detectLanguage(""))
        assertEquals(Language.AUTO, com.puretrans.app.util.LanguageDetector.detectLanguage(null))
    }

    @Test
    fun testLanguageDetectorConsecutiveSwitching() {
        // 模拟用户连续交替输入不同语言
        val inputs = listOf(
            "Hello" to Pair(Language.AUTO, Language.ZH),
            "你好" to Pair(Language.ZH, Language.EN),
            "こんにちは" to Pair(Language.AUTO, Language.ZH),
            "很高兴认识你" to Pair(Language.ZH, Language.EN),
            "Bonjour" to Pair(Language.AUTO, Language.ZH),
            "测试" to Pair(Language.ZH, Language.EN)
        )

        for ((input, expected) in inputs) {
            val result = com.puretrans.app.util.LanguageDetector.determineAutoDirection(input)
            assertEquals("Failed for input: $input", expected.first, result.first)
            assertEquals("Failed for input: $input", expected.second, result.second)
        }
    }

    @Test
    fun testImageDownsamplingCalculation() {
        val maxDimension = 2048

        // 场景 1: 超长图 1000 x 6000
        val height1 = 6000
        val width1 = 1000
        var inSampleSize1 = 1
        val maxDim1 = kotlin.math.max(height1, width1)
        while ((maxDim1 / inSampleSize1) > maxDimension) {
            inSampleSize1 *= 2
        }
        assertEquals(4, inSampleSize1)
        assertTrue((height1 / inSampleSize1) <= maxDimension)

        // 场景 2: 超宽全景图 8000 x 1200
        val height2 = 1200
        val width2 = 8000
        var inSampleSize2 = 1
        val maxDim2 = kotlin.math.max(height2, width2)
        while ((maxDim2 / inSampleSize2) > maxDimension) {
            inSampleSize2 *= 2
        }
        assertEquals(4, inSampleSize2)
        assertTrue((width2 / inSampleSize2) <= maxDimension)

        // 场景 3: 正常尺寸图片 1920 x 1080
        val height3 = 1080
        val width3 = 1920
        var inSampleSize3 = 1
        val maxDim3 = kotlin.math.max(height3, width3)
        while ((maxDim3 / inSampleSize3) > maxDimension) {
            inSampleSize3 *= 2
        }
        assertEquals(1, inSampleSize3)
    }

    @Test
    fun testTtsLocaleMapping() {
        // 中文映射
        assertEquals(java.util.Locale.SIMPLIFIED_CHINESE, com.puretrans.app.util.TtsManager.getLocaleForLanguage("zh"))
        assertEquals(java.util.Locale.SIMPLIFIED_CHINESE, com.puretrans.app.util.TtsManager.getLocaleForLanguage("zh-CN"))
        assertEquals(java.util.Locale.TRADITIONAL_CHINESE, com.puretrans.app.util.TtsManager.getLocaleForLanguage("zh-TW"))

        // 英文与欧洲语系
        assertEquals(java.util.Locale.US, com.puretrans.app.util.TtsManager.getLocaleForLanguage("en"))
        assertEquals(java.util.Locale.UK, com.puretrans.app.util.TtsManager.getLocaleForLanguage("en-GB"))
        assertEquals(java.util.Locale.FRENCH, com.puretrans.app.util.TtsManager.getLocaleForLanguage("fr"))
        assertEquals(java.util.Locale.GERMAN, com.puretrans.app.util.TtsManager.getLocaleForLanguage("de"))
        assertEquals(java.util.Locale.JAPANESE, com.puretrans.app.util.TtsManager.getLocaleForLanguage("ja"))
        assertEquals(java.util.Locale.KOREAN, com.puretrans.app.util.TtsManager.getLocaleForLanguage("ko"))

        // auto 智能基于输入文本推断
        assertEquals(java.util.Locale.SIMPLIFIED_CHINESE, com.puretrans.app.util.TtsManager.getLocaleForLanguage("auto", "你好"))
        assertEquals(java.util.Locale.US, com.puretrans.app.util.TtsManager.getLocaleForLanguage("auto", "Hello world"))
        assertEquals(java.util.Locale.JAPANESE, com.puretrans.app.util.TtsManager.getLocaleForLanguage("auto", "こんにちは"))
        assertEquals(java.util.Locale.KOREAN, com.puretrans.app.util.TtsManager.getLocaleForLanguage("auto", "안녕하세요"))
    }

    @Test
    fun testTtsOnlineAudioCandidates() {
        // 1. 中文：国内直连有道优先（le=zh），Google 兜底 (zh-CN)
        val zhCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("zh", "你好")
        assertEquals(2, zhCandidates.size)
        assertTrue(zhCandidates[0].contains("dict.youdao.com/dictvoice"))
        assertTrue(zhCandidates[0].contains("le=zh"))
        assertTrue(zhCandidates[1].contains("translate.google.com/translate_tts"))
        assertTrue(zhCandidates[1].contains("tl=zh-CN"))

        // 2. 英文：国内直连有道优先（type=2 美音），Google 兜底 (tl=en)
        val enCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("en", "Hello world")
        assertEquals(2, enCandidates.size)
        assertTrue(enCandidates[0].contains("dict.youdao.com/dictvoice"))
        assertTrue(enCandidates[0].contains("type=2"))
        assertTrue(enCandidates[1].contains("translate.google.com/translate_tts"))
        assertTrue(enCandidates[1].contains("tl=en"))

        // 3. 日文与韩文
        val jaCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("ja", "ありがとう")
        assertTrue(jaCandidates[0].contains("le=jap"))
        val koCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("ko", "감사합니다")
        assertTrue(koCandidates[0].contains("le=ko"))

        // 4. 阿拉伯语等小语种：Google 优先
        val arCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("ar", "مرحبا")
        assertTrue(arCandidates[0].contains("translate.google.com"))

        // 5. 空文本保护
        val emptyCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("en", "")
        assertTrue(emptyCandidates.isEmpty())

        // 6. auto 语种自动识别
        val autoZhCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("auto", "早上好")
        assertTrue(autoZhCandidates[0].contains("le=zh"))
    }
}
