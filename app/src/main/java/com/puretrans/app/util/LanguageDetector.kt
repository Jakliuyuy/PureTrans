package com.puretrans.app.util

import com.puretrans.app.data.model.Language

/**
 * 智能语种检测与双向自动互转工具
 * 核心逻辑：
 * 1. 只要输入包含中文字符，自动识别为中文并设为翻译成英文 (zh -> en)
 * 2. 只要输入为非中文字符（英文、日文、韩文、法文等其他国语言），自动设为翻译成中文 (auto -> zh)
 */
object LanguageDetector {

    /**
     * 检测文本中是否包含中文字符（CJK 统一汉字集）
     */
    fun containsChinese(text: CharSequence?): Boolean {
        if (text.isNullOrBlank()) return false
        var i = 0
        val len = text.length
        while (i < len) {
            val codePoint = Character.codePointAt(text, i)
            val script = Character.UnicodeScript.of(codePoint)
            if (script == Character.UnicodeScript.HAN) {
                return true
            }
            // 兼容性补充检查 CJK 扩展区
            val block = Character.UnicodeBlock.of(codePoint)
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            ) {
                return true
            }
            i += Character.charCount(codePoint)
        }
        return false
    }

    /**
     * 根据文本字符特征检测最可能的语言类型
     */
    fun detectLanguage(text: CharSequence?): Language {
        if (text.isNullOrBlank()) return Language.AUTO

        var hasHan = false
        var hasKana = false
        var hasHangul = false
        var hasCyrillic = false
        var hasArabic = false
        var hasLatin = false

        var i = 0
        val len = text.length
        while (i < len) {
            val codePoint = Character.codePointAt(text, i)
            val script = Character.UnicodeScript.of(codePoint)

            when (script) {
                Character.UnicodeScript.HAN -> hasHan = true
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> hasKana = true
                Character.UnicodeScript.HANGUL -> hasHangul = true
                Character.UnicodeScript.CYRILLIC -> hasCyrillic = true
                Character.UnicodeScript.ARABIC -> hasArabic = true
                Character.UnicodeScript.LATIN -> hasLatin = true
                else -> {
                    val block = Character.UnicodeBlock.of(codePoint)
                    if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                    ) {
                        hasHan = true
                    }
                }
            }
            i += Character.charCount(codePoint)
        }

        // 日文通常会混杂汉字与假名；若有假名则优先判定为日语
        if (hasKana) return Language.JA
        // 包含汉字判定为中文
        if (hasHan) return Language.ZH
        // 谚文判定为韩文
        if (hasHangul) return Language.KO
        // 西里尔字母判定为俄文
        if (hasCyrillic) return Language.RU
        // 阿拉伯字母判定为阿拉伯文
        if (hasArabic) return Language.AR
        // 拉丁字母判定为英文
        if (hasLatin) return Language.EN

        return Language.AUTO
    }

    /**
     * 根据输入文本智能推导源语言和目标语言
     * 核心规则：
     * 1. 只要输入包含中文字符，自动识别为中文并设为翻译成英文 (zh -> en)
     * 2. 只要输入为非中文字符（英文、日文、韩文、法文等其他国语言），自动设为翻译成中文 (auto -> zh)
     * @param text 用户输入的文本
     * @return Pair(SourceLanguage, TargetLanguage)
     */
    fun determineAutoDirection(text: String): Pair<Language, Language> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            // 输入为空时，默认待命状态 自动检测 -> 简体中文
            return Pair(Language.AUTO, Language.ZH)
        }

        return if (containsChinese(trimmed)) {
            // 输入中文 -> 自动识别翻译成英文 (zh -> en)
            Pair(Language.ZH, Language.EN)
        } else {
            // 输入其他国语言 -> 自动识别翻译成中文 (auto -> zh)
            Pair(Language.AUTO, Language.ZH)
        }
    }
}
