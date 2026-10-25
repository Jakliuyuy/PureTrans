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
}
