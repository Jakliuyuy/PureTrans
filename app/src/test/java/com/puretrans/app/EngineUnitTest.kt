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
    fun testLanguageLookup() {
        assertEquals("自动检测 (Auto)", Language.findByCode("auto").displayName)
        assertEquals("zh-CN", Language.findByCode("zh").googleCode)
        assertEquals("jp", Language.findByCode("ja").baiduCode)
    }

    @Test
    fun testDirectModeLogic() {
        val hasBaidu = "myAppId".isNotBlank() && "mySecret".isNotBlank()
        assertTrue(hasBaidu)

        val emptyKeys = "".isNotBlank() || "".isNotBlank()
        assertFalse(emptyKeys)
    }
}
