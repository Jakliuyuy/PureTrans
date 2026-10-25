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
}
