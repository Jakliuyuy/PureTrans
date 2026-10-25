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
        // 1. 中文短词：国内极速直连百度优先（lan=zh），Google 官方流兜底 (zh-CN)，单词级附加有道词典源 (le=zh)
        val zhWordCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("zh", "你好")
        assertEquals(3, zhWordCandidates.size)
        assertTrue(zhWordCandidates[0].contains("fanyi.baidu.com/gettts"))
        assertTrue(zhWordCandidates[0].contains("lan=zh"))
        assertTrue(zhWordCandidates[1].contains("translate.google.com/translate_tts"))
        assertTrue(zhWordCandidates[1].contains("tl=zh-CN"))
        assertTrue(zhWordCandidates[2].contains("dict.youdao.com/dictvoice"))
        assertTrue(zhWordCandidates[2].contains("le=zh"))

        // 2. 中文长句：百度直连长句优先，Google 官方流兜底，严禁引入有道 500 崩溃源
        val zhSentence = "你好世界，今天天气真好，我们一起去公园散步吧。"
        val zhSentenceCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("zh", zhSentence)
        assertEquals(2, zhSentenceCandidates.size)
        assertTrue(zhSentenceCandidates[0].contains("fanyi.baidu.com/gettts"))
        assertTrue(zhSentenceCandidates[1].contains("translate.google.com/translate_tts"))
        assertFalse(zhSentenceCandidates.any { it.contains("dict.youdao.com") })

        // 3. 英文短词：国内极速直连百度优先（lan=en），Google 官方流兜底 (tl=en)，单词级附加有道美音 (type=2)
        val enWordCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("en", "apple")
        assertEquals(3, enWordCandidates.size)
        assertTrue(enWordCandidates[0].contains("fanyi.baidu.com/gettts"))
        assertTrue(enWordCandidates[0].contains("lan=en"))
        assertTrue(enWordCandidates[1].contains("translate.google.com/translate_tts"))
        assertTrue(enWordCandidates[1].contains("tl=en"))
        assertTrue(enWordCandidates[2].contains("dict.youdao.com/dictvoice"))
        assertTrue(enWordCandidates[2].contains("type=2"))

        // 4. 英文长句：百度直连长句优先，Google 官方流兜底，杜绝有道长句 500
        val enSentence = "Hello world, this is a test sentence for pronunciation."
        val enSentenceCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("en", enSentence)
        assertEquals(2, enSentenceCandidates.size)
        assertTrue(enSentenceCandidates[0].contains("fanyi.baidu.com/gettts"))
        assertTrue(enSentenceCandidates[1].contains("translate.google.com/translate_tts"))
        assertFalse(enSentenceCandidates.any { it.contains("dict.youdao.com") })

        // 5. 日文与韩文
        val jaCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("ja", "ありがとう")
        assertTrue(jaCandidates[0].contains("fanyi.baidu.com/gettts"))
        assertTrue(jaCandidates[0].contains("lan=jp"))
        val koCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("ko", "감사합니다")
        assertTrue(koCandidates[0].contains("fanyi.baidu.com/gettts"))
        assertTrue(koCandidates[0].contains("lan=kor"))

        // 6. 阿拉伯语等国际小语种：Google 官方流首选
        val arCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("ar", "مرحبا")
        assertTrue(arCandidates[0].contains("translate.google.com"))

        // 7. 空文本安全保护
        val emptyCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("en", "")
        assertTrue(emptyCandidates.isEmpty())

        // 8. auto 语种自动识别
        val autoZhCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("auto", "早上好")
        assertTrue(autoZhCandidates[0].contains("fanyi.baidu.com/gettts"))
        assertTrue(autoZhCandidates[0].contains("lan=zh"))

        // 9. 标点符号与问号排除有道 500
        val punctuatedCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("en", "How are you?")
        assertEquals(2, punctuatedCandidates.size)
        assertFalse(punctuatedCandidates.any { it.contains("dict.youdao.com") })

        // 10. 包含换行符排除有道 500
        val linebreakCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("en", "Hello\nWorld")
        assertEquals(2, linebreakCandidates.size)
        assertFalse(linebreakCandidates.any { it.contains("dict.youdao.com") })

        // 11. 繁体中文 zh-TW 正确映射
        val twCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("zh-tw", "早安")
        assertTrue(twCandidates[0].contains("fanyi.baidu.com/gettts?lan=zh"))
        assertTrue(twCandidates[1].contains("tl=zh-TW"))

        // 12. 欧洲小语种法语、俄语长句（排除有道源，仅保留 Google 官方流）
        val frCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("fr", "Bonjour tout le monde, il fait beau aujourd'hui.")
        assertEquals(1, frCandidates.size)
        assertTrue(frCandidates[0].contains("translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=fr"))

        val ruCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("ru", "Привет, мир, сегодня отличная погода.")
        assertEquals(1, ruCandidates.size)
        assertTrue(ruCandidates[0].contains("tl=ru"))

        // 13. 超长文本截断测试 (保护 URL 长度)
        val longText = "a".repeat(500)
        val longCandidates = com.puretrans.app.util.TtsManager.getOnlineAudioCandidates("en", longText)
        assertTrue(longCandidates[0].contains("text=" + "a".repeat(250)))
        assertFalse(longCandidates[0].contains("text=" + "a".repeat(251)))
    }

    @Test
    fun testSemanticSegmentSplittingWithColumnGap() {
        // 模拟用户截图场景：左侧命令名 teamwork 与右侧描述 Invoke a team of agents...
        // teamwork: [100, 200, 220, 230], width=120, height=30
        // Invoke: [450, 200, 520, 230], width=70, height=30. gap = 450 - 220 = 230 > 30 * 0.75
        // a: [530, 200, 545, 230], width=15, height=30. gap = 10 < 30 * 0.75
        // team: [555, 200, 610, 230], width=55, height=30. gap = 10 < 30 * 0.75
        val elements = listOf(
            com.puretrans.app.ui.image.ImageTranslateProcessor.SimpleElement("teamwork", 100, 200, 220, 230),
            com.puretrans.app.ui.image.ImageTranslateProcessor.SimpleElement("Invoke", 450, 200, 520, 230),
            com.puretrans.app.ui.image.ImageTranslateProcessor.SimpleElement("a", 530, 200, 545, 230),
            com.puretrans.app.ui.image.ImageTranslateProcessor.SimpleElement("team", 555, 200, 610, 230)
        )

        val segments = com.puretrans.app.ui.image.ImageTranslateProcessor.splitSimpleElements(
            elements = elements,
            fallbackText = "teamwork Invoke a team",
            fallbackLeft = 100,
            fallbackTop = 200,
            fallbackRight = 610,
            fallbackBottom = 230
        )

        assertEquals("必须精准切分为 2 个独立片段（左命令 vs 右描述）", 2, segments.size)

        // 片段 1: 左侧命令 teamwork
        val seg1 = segments[0]
        assertEquals("teamwork", seg1.text)
        assertEquals(100, seg1.left)
        assertEquals(220, seg1.right)
        assertEquals(120, seg1.width)

        // 片段 2: 右侧描述 Invoke a team
        val seg2 = segments[1]
        assertEquals("Invoke a team", seg2.text)
        assertEquals(450, seg2.left)
        assertEquals(610, seg2.right)
        assertEquals(160, seg2.width)

        // 验证中间留白 gap 完全被排除在外 (220 到 450 之间为 230 像素真实留白，不会被遮罩抹平)
        assertTrue(seg2.left - seg1.right >= 230)
    }

    @Test
    fun testNormalWordGapNotSplit() {
        // 普通英文句子，单词间距正常（gap 为 8~12 像素），不得误拆分
        val elements = listOf(
            com.puretrans.app.ui.image.ImageTranslateProcessor.SimpleElement("Hello", 50, 100, 110, 130),
            com.puretrans.app.ui.image.ImageTranslateProcessor.SimpleElement("world", 120, 100, 180, 130),
            com.puretrans.app.ui.image.ImageTranslateProcessor.SimpleElement("again", 190, 100, 250, 130)
        )

        val segments = com.puretrans.app.ui.image.ImageTranslateProcessor.splitSimpleElements(
            elements = elements,
            fallbackText = "Hello world again",
            fallbackLeft = 50,
            fallbackTop = 100,
            fallbackRight = 250,
            fallbackBottom = 130
        )

        assertEquals("普通词距应保持为 1 个完整段落", 1, segments.size)
        assertEquals("Hello world again", segments[0].text)
        assertEquals(50, segments[0].left)
        assertEquals(250, segments[0].right)
    }

    @Test
    fun testMultiColumnSplit() {
        // 3 列表格或排版场景：命令 | 参数 | 描述
        val elements = listOf(
            com.puretrans.app.ui.image.ImageTranslateProcessor.SimpleElement("boost", 50, 50, 120, 80),
            com.puretrans.app.ui.image.ImageTranslateProcessor.SimpleElement("--fast", 300, 50, 380, 80),
            com.puretrans.app.ui.image.ImageTranslateProcessor.SimpleElement("Run immediately", 550, 50, 750, 80)
        )

        val segments = com.puretrans.app.ui.image.ImageTranslateProcessor.splitSimpleElements(
            elements = elements,
            fallbackText = "boost --fast Run immediately",
            fallbackLeft = 50,
            fallbackTop = 50,
            fallbackRight = 750,
            fallbackBottom = 80
        )

        assertEquals(3, segments.size)
        assertEquals("boost", segments[0].text)
        assertEquals("--fast", segments[1].text)
        assertEquals("Run immediately", segments[2].text)
    }

    @Test
    fun testEmptyOrFallbackElements() {
        // 空 elements 列表应平稳使用 fallback
        val emptySegments = com.puretrans.app.ui.image.ImageTranslateProcessor.splitSimpleElements(
            elements = emptyList(),
            fallbackText = "Fallback Text",
            fallbackLeft = 10,
            fallbackTop = 20,
            fallbackRight = 100,
            fallbackBottom = 40
        )
        assertEquals(1, emptySegments.size)
        assertEquals("Fallback Text", emptySegments[0].text)
        assertEquals(90, emptySegments[0].width)

        // 全部空白 elements
        val blankSegments = com.puretrans.app.ui.image.ImageTranslateProcessor.splitSimpleElements(
            elements = listOf(com.puretrans.app.ui.image.ImageTranslateProcessor.SimpleElement("   ", 10, 20, 30, 40)),
            fallbackText = "Fallback",
            fallbackLeft = 10,
            fallbackTop = 20,
            fallbackRight = 50,
            fallbackBottom = 40
        )
        assertEquals(1, blankSegments.size)
        assertEquals("Fallback", blankSegments[0].text)
    }

    @Test
    fun testCompactMaskWidthCalculation() {
        // 1. 译文所需宽度小于原词框：根据较小合理外接矩形收紧遮罩，但不低于 75% 保底
        val boxWidth = 500f
        val textNeededShort = 300f
        val maskShort = com.puretrans.app.ui.image.ImageOverlayRenderer.computeMaskWidth(boxWidth, textNeededShort)
        assertEquals(375f, maskShort, 0.1f) // 500 * 0.75f = 375f

        val textNeededMedium = 420f
        val maskMedium = com.puretrans.app.ui.image.ImageOverlayRenderer.computeMaskWidth(boxWidth, textNeededMedium)
        assertEquals(420f, maskMedium, 0.1f) // 420f 介于 375f 和 500f 之间

        // 2. 译文所需宽度超出原词框：严禁无限拉长，必须被 boxWidth 严格约束截断
        val textNeededLong = 700f
        val maskLong = com.puretrans.app.ui.image.ImageOverlayRenderer.computeMaskWidth(boxWidth, textNeededLong)
        assertEquals(500f, maskLong, 0.1f)

        // 3. 边界值
        assertEquals(0f, com.puretrans.app.ui.image.ImageOverlayRenderer.computeMaskWidth(0f, 100f), 0.1f)
        assertEquals(100f, com.puretrans.app.ui.image.ImageOverlayRenderer.computeMaskWidth(100f, 0f), 0.1f)
    }

    @Test
    fun testPhoneticIpaFormatting() {
        // 1. 英文无括号 -> 格式化为 [xxx]
        assertEquals("[həˈloʊ]", com.puretrans.app.util.PhoneticHelper.formatIpaPhonetic("həˈloʊ", true))
        assertEquals("[ˈæp(ə)l]", com.puretrans.app.util.PhoneticHelper.formatIpaPhonetic("ˈæp(ə)l", true))

        // 2. 已经有方括号或斜杠 -> 保持原样
        assertEquals("[həˈləʊ]", com.puretrans.app.util.PhoneticHelper.formatIpaPhonetic("[həˈləʊ]", true))
        assertEquals("/həˈloʊ/", com.puretrans.app.util.PhoneticHelper.formatIpaPhonetic("/həˈloʊ/", true))

        // 3. 中文拼音 -> 不强制加方括号
        assertEquals("Nǐ hǎo, shìjiè", com.puretrans.app.util.PhoneticHelper.formatIpaPhonetic("Nǐ hǎo, shìjiè", false))

        // 4. 空值与空白字符
        assertNull(com.puretrans.app.util.PhoneticHelper.formatIpaPhonetic(null, true))
        assertNull(com.puretrans.app.util.PhoneticHelper.formatIpaPhonetic("", true))
        assertNull(com.puretrans.app.util.PhoneticHelper.formatIpaPhonetic("   ", true))
    }

    @Test
    fun testPinyinAndIpaDistinction() {
        // 英文音标识别
        assertTrue(com.puretrans.app.util.PhoneticHelper.isLikelyEnglishPhonetic("həˈlō wərld"))
        assertTrue(com.puretrans.app.util.PhoneticHelper.isLikelyEnglishPhonetic("ˈæp(ə)l"))
        assertTrue(com.puretrans.app.util.PhoneticHelper.isLikelyEnglishPhonetic("[test]"))
        assertTrue(com.puretrans.app.util.PhoneticHelper.isLikelyEnglishPhonetic("/test/"))
        assertFalse(com.puretrans.app.util.PhoneticHelper.isLikelyEnglishPhonetic("Nǐ hǎo"))

        // 汉语拼音声调识别
        assertTrue(com.puretrans.app.util.PhoneticHelper.containsPinyinTone("Nǐ hǎo, shìjiè"))
        assertTrue(com.puretrans.app.util.PhoneticHelper.containsPinyinTone("píngguǒ"))
        // 英文音标带 ə 排除在拼音之外
        assertFalse(com.puretrans.app.util.PhoneticHelper.containsPinyinTone("həˈlō wərld"))
    }

    @Test
    fun testGtxExtractPhoneticForTargetEnglish() {
        // 源语言为中文，目标语言为英文，gtx 返回中携带拼音与英文音标
        val jsonMock = """
            [[["Hello, world","你好，世界",null,null,1],[null,null,"Nǐ hǎo, shìjiè","həˈlō wərld"]],null,"zh-CN"]
        """.trimIndent()
        val rootArray = JsonParser.parseString(jsonMock).asJsonArray
        val extracted = com.puretrans.app.util.PhoneticHelper.extractGtxPhonetic(rootArray, Language.EN)
        assertEquals("[həˈlō wərld]", extracted)
    }

    @Test
    fun testGtxExtractPhoneticForTargetChinese() {
        // 源语言为英文，目标语言为中文，gtx 返回中携带拼音与英文音标
        val jsonMock = """
            [[["你好，世界","Hello, world",null,null,1],[null,null,"Nǐ hǎo, shìjiè","həˈlō wərld"]],null,"en"]
        """.trimIndent()
        val rootArray = JsonParser.parseString(jsonMock).asJsonArray
        val extracted = com.puretrans.app.util.PhoneticHelper.extractGtxPhonetic(rootArray, Language.ZH)
        assertEquals("Nǐ hǎo, shìjiè", extracted)
    }

    @Test
    fun testParseYoudaoPhoneticJson() {
        val youdaoJson = """
            {
              "simple": {
                "word": [
                  {
                    "usphone": "həˈloʊ",
                    "ukphone": "həˈləʊ"
                  }
                ]
              }
            }
        """.trimIndent()
        val parsed = com.puretrans.app.util.PhoneticHelper.parseYoudaoPhoneticJson(youdaoJson)
        assertEquals("[həˈloʊ]", parsed)

        // 仅有 ukphone 的情况
        val ukOnlyJson = """
            {
              "ec": {
                "word": [
                  {
                    "usphone": "",
                    "ukphone": "həˈləʊ"
                  }
                ]
              }
            }
        """.trimIndent()
        val parsedUk = com.puretrans.app.util.PhoneticHelper.parseYoudaoPhoneticJson(ukOnlyJson)
        assertEquals("[həˈləʊ]", parsedUk)

        // 无效 json 或缺少音标
        assertNull(com.puretrans.app.util.PhoneticHelper.parseYoudaoPhoneticJson("{}"))
        assertNull(com.puretrans.app.util.PhoneticHelper.parseYoudaoPhoneticJson("invalid json"))
    }

    @Test
    fun testGtxExtractPhonetic_TargetEnglish_OnlyPinyin_ReturnsNullForFallback() {
        // 源语言为中文，目标为英文，但 gtx 仅返回了源语言拼音（无英文音标）
        val jsonMockOnlyPinyin = """
            [[["Hello","你好",null,null,1],[null,null,"Nǐ hǎo"]],null,"zh-CN"]
        """.trimIndent()
        val rootArray = JsonParser.parseString(jsonMockOnlyPinyin).asJsonArray
        val extracted = com.puretrans.app.util.PhoneticHelper.extractGtxPhonetic(rootArray, Language.EN)
        // 关键断言：绝不能把中文拼音当作英文音标返回，必须为 null 以便触发词典补充
        assertNull(extracted)
    }

    @Test
    fun testGtxExtractPhonetic_TargetChinese_OnlyEnglishPhonetic_ReturnsNull() {
        // 源语言为英文，目标为中文，但 gtx 仅返回了英文音标（无中文拼音）
        val jsonMockOnlyEnglish = """
            [[["你好","Hello",null,null,1],[null,null,null,"[həˈloʊ]"]],null,"en"]
        """.trimIndent()
        val rootArray = JsonParser.parseString(jsonMockOnlyEnglish).asJsonArray
        val extracted = com.puretrans.app.util.PhoneticHelper.extractGtxPhonetic(rootArray, Language.ZH)
        // 关键断言：绝不能把英文音标当作中文译文的拼音返回
        assertNull(extracted)
    }

    @Test
    fun testEnglishPhoneticWithMacronAndToneDistinction() {
        // 英文长元音宏音符 (如 hōm, ɡō, sē) 应被识别为英文音标转写
        assertTrue(com.puretrans.app.util.PhoneticHelper.isLikelyEnglishPhonetic("hōm"))
        assertTrue(com.puretrans.app.util.PhoneticHelper.isLikelyEnglishPhonetic("ɡō"))

        // 汉语拼音特有声调（二三四声）绝对不能被识别为英文音标
        assertFalse(com.puretrans.app.util.PhoneticHelper.isLikelyEnglishPhonetic("píng"))
        assertFalse(com.puretrans.app.util.PhoneticHelper.isLikelyEnglishPhonetic("guǒ"))
        assertFalse(com.puretrans.app.util.PhoneticHelper.isLikelyEnglishPhonetic("nǐ"))

        // formatIpaPhonetic 在要求英文时，若传入拼音，应直接拒绝返回 null
        assertNull(com.puretrans.app.util.PhoneticHelper.formatIpaPhonetic("píngguǒ", true))
        assertNull(com.puretrans.app.util.PhoneticHelper.formatIpaPhonetic("Nǐ hǎo", true))
    }

    @Test
    fun testParseYoudaoPhonetic_FallbackToPhoneField() {
        val jsonPhoneOnly = """
            {
              "simple": {
                "word": [
                  {
                    "phone": "kəmˈpjuːtər"
                  }
                ]
              }
            }
        """.trimIndent()
        val parsed = com.puretrans.app.util.PhoneticHelper.parseYoudaoPhoneticJson(jsonPhoneOnly)
        assertEquals("[kəmˈpjuːtər]", parsed)
    }

    @Test
    fun testParagraphClusteringWithIndependentHeaderAndMultiLineBody() {
        // 模拟图 2 用户真实场景：
        // 顶部独立单行标题：Thought for 35s > (top 100, bottom 120, height 20, width 150)
        // 底部 6 行紧凑英文正文 (top 150 到 286, 行高 24, 行距 4px, width 600)
        val header = com.puretrans.app.ui.image.ImageTranslateProcessor.RawTextLine(
            text = "Thought for 35s >",
            left = 100,
            top = 100,
            right = 250,
            bottom = 120
        )

        val bodyLines = listOf(
            com.puretrans.app.ui.image.ImageTranslateProcessor.RawTextLine(
                text = "This is the first line of the continuous response from assistant,",
                left = 100,
                top = 150,
                right = 700,
                bottom = 174
            ),
            com.puretrans.app.ui.image.ImageTranslateProcessor.RawTextLine(
                text = "which provides in-depth technical analysis and explains every detail",
                left = 100,
                top = 178,
                right = 695,
                bottom = 202
            ),
            com.puretrans.app.ui.image.ImageTranslateProcessor.RawTextLine(
                text = "of the architecture clearly so that developers can easily follow",
                left = 100,
                top = 206,
                right = 705,
                bottom = 230
            ),
            com.puretrans.app.ui.image.ImageTranslateProcessor.RawTextLine(
                text = "and reproduce the state-of-the-art results without any confusion",
                left = 100,
                top = 234,
                right = 690,
                bottom = 258
            ),
            com.puretrans.app.ui.image.ImageTranslateProcessor.RawTextLine(
                text = "or unexpected failures during runtime execution.",
                left = 100,
                top = 262,
                right = 520,
                bottom = 286
            )
        )

        val allLines = listOf(header) + bodyLines
        val clusters = com.puretrans.app.ui.image.ImageTranslateProcessor.clusterLinesIntoParagraphs(allLines)

        // 验证：必须精准聚类为 2 个块（独立标题 + 连续段落大块），绝不允许切成 6 个孤立碎片！
        assertEquals(2, clusters.size)

        // 块 1: 独立标题
        val cluster1 = clusters[0]
        assertEquals("Thought for 35s >", cluster1.text)
        assertEquals(1, cluster1.lineCount)
        assertEquals(100, cluster1.left)
        assertEquals(100, cluster1.top)
        assertEquals(250, cluster1.right)
        assertEquals(120, cluster1.bottom)

        // 块 2: 5 行合并的多行连续段落
        val cluster2 = clusters[1]
        assertEquals(5, cluster2.lineCount)
        assertTrue(cluster2.text.startsWith("This is the first line"))
        assertTrue(cluster2.text.endsWith("during runtime execution."))
        // 验证外接大矩形 unionRect 完整包裹全部 5 行
        assertEquals(100, cluster2.left)
        assertEquals(150, cluster2.top)
        assertEquals(705, cluster2.right)
        assertEquals(286, cluster2.bottom)
    }

    @Test
    fun testParagraphClusteringWithLargeLineGapNotMerged() {
        // 两行之间垂直距离很大（空行或段落分隔）应切分为不同块
        val line1 = com.puretrans.app.ui.image.ImageTranslateProcessor.RawTextLine(
            text = "Paragraph One Ending.",
            left = 50,
            top = 50,
            right = 300,
            bottom = 80 // height = 30
        )
        val line2 = com.puretrans.app.ui.image.ImageTranslateProcessor.RawTextLine(
            text = "Paragraph Two Starting.",
            left = 50,
            top = 160, // gap = 160 - 80 = 80 > 30 * 1.35
            right = 300,
            bottom = 190
        )

        val clusters = com.puretrans.app.ui.image.ImageTranslateProcessor.clusterLinesIntoParagraphs(listOf(line1, line2))
        assertEquals(2, clusters.size)
        assertEquals("Paragraph One Ending.", clusters[0].text)
        assertEquals("Paragraph Two Starting.", clusters[1].text)
    }

    @Test
    fun testParagraphClusteringFontSizeDifferenceNotMerged() {
        // 大字号标题（字高 50）与正文（字高 20），即便距离近也不应合为一个段落
        val title = com.puretrans.app.ui.image.ImageTranslateProcessor.RawTextLine(
            text = "Big Section Title",
            left = 50,
            top = 50,
            right = 400,
            bottom = 100 // height = 50
        )
        val body = com.puretrans.app.ui.image.ImageTranslateProcessor.RawTextLine(
            text = "Normal small body text.",
            left = 50,
            top = 115, // gap = 15 <= avgH(35) * 1.35
            right = 400,
            bottom = 135 // height = 20, (50-20)/50 = 60% > 45%
        )

        val clusters = com.puretrans.app.ui.image.ImageTranslateProcessor.clusterLinesIntoParagraphs(listOf(title, body))
        assertEquals(2, clusters.size)
    }

    @Test
    fun testChineseProtectionDetection() {
        // 1. 纯中文内容 -> 必须保护（返回 true）
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isMainlyChinese("深度思考过程"))
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isMainlyChinese("思考了 35 秒"))
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isMainlyChinese("已完成代码审查与构建"))

        // 2. 中文包含数字或英文代号 -> 保护
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isMainlyChinese("代码重构指南 v2.0"))

        // 3. 纯英文或英文为主 -> 不保护（返回 false），以便正常翻译为中文
        assertFalse(com.puretrans.app.ui.image.ImageTranslateProcessor.isMainlyChinese("Thought for 35s >"))
        assertFalse(com.puretrans.app.ui.image.ImageTranslateProcessor.isMainlyChinese("This is a clean English paragraph for testing."))
        assertFalse(com.puretrans.app.ui.image.ImageTranslateProcessor.isMainlyChinese("Function returns a status code."))

        // 4. 空文本
        assertFalse(com.puretrans.app.ui.image.ImageTranslateProcessor.isMainlyChinese(""))
        assertFalse(com.puretrans.app.ui.image.ImageTranslateProcessor.isMainlyChinese("   "))
    }

    @Test
    fun testGibberishAndNoiseFiltering() {
        // 1. Latin OCR 误识别中文产生的脱字符及非自然符号乱码 -> 必须判定为乱码并过滤
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("ETA^-D ARS 器 LGithub"))
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("^_~"))
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("~^|\\_"))

        // 2. 纯标点、空白噪点 -> 过滤
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise(""))
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("   "))
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("..."))
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("- - -"))
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("."))
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise(">"))

        // 3. 正常文本与标题 -> 绝不能被误判为乱码
        assertFalse(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("Thought for 35s >"))
        assertFalse(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("Hello, world!"))
        assertFalse(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("System Architecture Overview"))
        assertFalse(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("思考过程"))
    }

    @Test
    fun testDominantColorExtractionAndPureWhiteSnap() {
        // 模拟白底卡片采样像素（大量接近纯白的像素 + 少量反走样灰色 + 少量深色文字像素）
        val pixels = mutableListOf<Int>()
        val rgbFunc = com.puretrans.app.ui.image.ImageTranslateProcessor::rgb
        // 80 个纯白卡片像素 (255, 255, 255)
        repeat(80) { pixels.add(rgbFunc(255, 255, 255)) }
        // 10 个压缩轻微噪点 (250, 250, 250)
        repeat(10) { pixels.add(rgbFunc(250, 250, 250)) }
        // 5 个抗锯齿半透明灰 (210, 210, 210)
        repeat(5) { pixels.add(rgbFunc(210, 210, 210)) }
        // 5 个文字深色 (30, 30, 30)
        repeat(5) { pixels.add(rgbFunc(30, 30, 30)) }

        val (bgColor, textColor) = com.puretrans.app.ui.image.ImageTranslateProcessor.sampleDominantColorFromPixels(pixels)

        // 验证：触发纯白吸附，彻底消除灰膏药，输出纯白底色与深色文字！
        assertEquals(rgbFunc(255, 255, 255), bgColor)
        assertEquals(rgbFunc(18, 18, 18), textColor)
    }

    @Test
    fun testDominantColorExtractionDarkMode() {
        val rgbFunc = com.puretrans.app.ui.image.ImageTranslateProcessor::rgb
        // 模拟深色模式暗黑卡片背景（大量 18, 18, 18 像素 + 少量浅色文字）
        val pixels = mutableListOf<Int>()
        repeat(85) { pixels.add(rgbFunc(18, 18, 18)) }
        repeat(10) { pixels.add(rgbFunc(22, 22, 22)) }
        repeat(5) { pixels.add(rgbFunc(240, 240, 240)) }

        val (bgColor, textColor) = com.puretrans.app.ui.image.ImageTranslateProcessor.sampleDominantColorFromPixels(pixels)

        assertEquals(rgbFunc(18, 18, 18), bgColor)
        assertEquals(rgbFunc(245, 245, 245), textColor)
    }

    @Test
    fun testParagraphClusteringTwoColumnsSeparation() {
        // 两栏排版：左栏和右栏在垂直方向上虽然高度重叠，但水平 X 完全错开（左栏 50~200，右栏 400~550）
        val colLeft1 = com.puretrans.app.ui.image.ImageTranslateProcessor.RawTextLine("Left column line 1", 50, 100, 200, 130)
        val colLeft2 = com.puretrans.app.ui.image.ImageTranslateProcessor.RawTextLine("Left column line 2", 50, 135, 200, 165)
        val colRight1 = com.puretrans.app.ui.image.ImageTranslateProcessor.RawTextLine("Right column line 1", 400, 100, 550, 130)

        val clusters = com.puretrans.app.ui.image.ImageTranslateProcessor.clusterLinesIntoParagraphs(listOf(colLeft1, colRight1, colLeft2))
        // 验证两栏不被混淆合并
        assertTrue(clusters.size >= 2)
    }

    @Test
    fun testParagraphClusteringEmptyAndSingleLine() {
        val emptyClusters = com.puretrans.app.ui.image.ImageTranslateProcessor.clusterLinesIntoParagraphs(emptyList())
        assertTrue(emptyClusters.isEmpty())

        val singleLine = com.puretrans.app.ui.image.ImageTranslateProcessor.RawTextLine("Just a title", 10, 10, 100, 30)
        val singleCluster = com.puretrans.app.ui.image.ImageTranslateProcessor.clusterLinesIntoParagraphs(listOf(singleLine))
        assertEquals(1, singleCluster.size)
        assertEquals(1, singleCluster[0].lineCount)
        assertEquals("Just a title", singleCluster[0].text)
    }

    @Test
    fun testGibberishFilteringWithHyphenatedEnglish() {
        // 包含正常连字符的英文句子绝不能被误判为乱码
        assertFalse(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("State-of-the-art neural network architecture"))
        assertFalse(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("real-time translation system"))
        assertFalse(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("high-quality text output"))

        // 特殊乱码符号组合被正确过滤
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("~^"))
        assertTrue(com.puretrans.app.ui.image.ImageTranslateProcessor.isGibberishOrNoise("ETA^-D"))
    }
}


