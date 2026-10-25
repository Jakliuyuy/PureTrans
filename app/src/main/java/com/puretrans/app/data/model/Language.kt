package com.puretrans.app.data.model

data class Language(
    val code: String,
    val displayName: String,
    val baiduCode: String,
    val googleCode: String
) {
    companion object {
        val AUTO = Language(code = "auto", displayName = "自动检测 (Auto)", baiduCode = "auto", googleCode = "auto")
        val ZH = Language(code = "zh", displayName = "简体中文", baiduCode = "zh", googleCode = "zh-CN")
        val EN = Language(code = "en", displayName = "英语 (English)", baiduCode = "en", googleCode = "en")
        val JA = Language(code = "ja", displayName = "日语 (日本語)", baiduCode = "jp", googleCode = "ja")
        val KO = Language(code = "ko", displayName = "韩语 (한국어)", baiduCode = "kor", googleCode = "ko")
        val FR = Language(code = "fr", displayName = "法语 (Français)", baiduCode = "fra", googleCode = "fr")
        val DE = Language(code = "de", displayName = "德语 (Deutsch)", baiduCode = "de", googleCode = "de")
        val RU = Language(code = "ru", displayName = "俄语 (Русский)", baiduCode = "ru", googleCode = "ru")
        val ES = Language(code = "es", displayName = "西班牙语 (Español)", baiduCode = "spa", googleCode = "es")
        val PT = Language(code = "pt", displayName = "葡萄牙语 (Português)", baiduCode = "pt", googleCode = "pt")
        val IT = Language(code = "it", displayName = "意大利语 (Italiano)", baiduCode = "it", googleCode = "it")
        val AR = Language(code = "ar", displayName = "阿拉伯语 (العربية)", baiduCode = "ara", googleCode = "ar")

        val supportedSourceLanguages = listOf(AUTO, ZH, EN, JA, KO, FR, DE, RU, ES, PT, IT, AR)
        val supportedTargetLanguages = listOf(ZH, EN, JA, KO, FR, DE, RU, ES, PT, IT, AR)

        fun findByCode(code: String): Language {
            return supportedSourceLanguages.find { it.code.equals(code, ignoreCase = true) }
                ?: supportedTargetLanguages.find { it.code.equals(code, ignoreCase = true) }
                ?: EN
        }
    }
}
