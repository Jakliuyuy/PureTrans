package com.puretrans.app.util

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.puretrans.app.data.model.Language
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object PhoneticHelper {

    private val cache = ConcurrentHashMap<String, String>()

    // 匹配英式/美式音标典型标记（如倒e(ə)、重音符号(ˈˌ)、长音符号(ː)、IPA特定元音/辅音(ʊ, ɪ, ʌ, æ, θ, ð, ʃ, ʒ, ŋ)等）
    private val IPA_MARKER_REGEX = Regex("[əʊɪʌæːˈˌθðʃʒŋ]")

    // 匹配常见汉语拼音声调字符
    private val PINYIN_TONE_REGEX = Regex("[āáǎàóǒòēéěèīíǐìūúǔùǖǘǚǜĀÁǍÀÓǑÒĒÉĚÈĪÍǏÌŪÚǓÙǕǗǙǛ]")

    private val defaultClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 判断字符串是否具有明显的英文音标特征（如包含倒e(ə)、重音符号ˈˌ、IPA特定符号）
     */
    fun isLikelyEnglishPhonetic(text: String): Boolean {
        if (IPA_MARKER_REGEX.containsMatchIn(text)) return true
        val trimmed = text.trim()
        return (trimmed.startsWith("[") && trimmed.endsWith("]")) ||
                (trimmed.startsWith("/") && trimmed.endsWith("/"))
    }

    /**
     * 判断字符串中是否包含汉语拼音声调字符（排除英文 IPA 音标）
     */
    fun containsPinyinTone(text: String): Boolean {
        if (IPA_MARKER_REGEX.containsMatchIn(text)) return false
        return PINYIN_TONE_REGEX.containsMatchIn(text)
    }

    /**
     * 将英文音标格式化为标准优雅的 IPA 格式（如 [həˈloʊ] 或 /həˈloʊ/）
     * 若已包含方括号或斜杠，则保持原样；若为拼音则不包裹方括号
     */
    fun formatIpaPhonetic(phonetic: String?, isEnglish: Boolean = true): String? {
        val trimmed = phonetic?.trim() ?: return null
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("[") && trimmed.endsWith("]")) return trimmed
        if (trimmed.startsWith("/") && trimmed.endsWith("/")) return trimmed

        // 若包含中文拼音声调，且非英文音标，不包裹方括号
        if (containsPinyinTone(trimmed) && !isLikelyEnglishPhonetic(trimmed)) {
            return trimmed
        }

        return if (isEnglish) "[$trimmed]" else trimmed
    }

    /**
     * 从 Google gtx 接口返回的 rootArray 中智能健壮解析目标语言音标
     */
    fun extractGtxPhonetic(rootArray: JsonArray, targetLang: Language): String? {
        if (rootArray.size() == 0 || !rootArray.get(0).isJsonArray) return null
        val translationArray = rootArray.get(0).asJsonArray

        var candidate2: String? = null
        var candidate3: String? = null

        for (element in translationArray) {
            if (element.isJsonArray) {
                val subArray = element.asJsonArray
                // 音标通常出现在 subArray[0] 为 null，或子数组 index 2/3 有值的元素中
                if (subArray.size() > 2 && !subArray.get(2).isJsonNull) {
                    val s = subArray.get(2).asString.trim()
                    if (s.isNotEmpty()) candidate2 = s
                }
                if (subArray.size() > 3 && !subArray.get(3).isJsonNull) {
                    val s = subArray.get(3).asString.trim()
                    if (s.isNotEmpty()) candidate3 = s
                }
            }
        }

        if (candidate2 == null && candidate3 == null) return null
        if (candidate2 != null && candidate3 == null) {
            return formatIfTargetEnglish(candidate2, targetLang)
        }
        if (candidate2 == null && candidate3 != null) {
            return formatIfTargetEnglish(candidate3, targetLang)
        }

        val nonNull2 = candidate2!!
        val nonNull3 = candidate3!!

        val isTargetEn = targetLang.code.startsWith("en", ignoreCase = true)
        val isTargetZh = targetLang.code.startsWith("zh", ignoreCase = true)

        val selected = when {
            isTargetEn -> {
                val is2En = isLikelyEnglishPhonetic(nonNull2)
                val is3En = isLikelyEnglishPhonetic(nonNull3)
                if (is2En && !is3En) nonNull2
                else if (!is2En && is3En) nonNull3
                else if (containsPinyinTone(nonNull2) && !containsPinyinTone(nonNull3)) nonNull3
                else nonNull2
            }
            isTargetZh -> {
                val is2Pinyin = containsPinyinTone(nonNull2)
                val is3Pinyin = containsPinyinTone(nonNull3)
                if (is2Pinyin && !is3Pinyin) nonNull2
                else if (!is2Pinyin && is3Pinyin) nonNull3
                else nonNull2
            }
            else -> nonNull2
        }

        return formatIfTargetEnglish(selected, targetLang)
    }

    private fun formatIfTargetEnglish(phonetic: String, targetLang: Language): String {
        return if (targetLang.code.startsWith("en", ignoreCase = true) && !containsPinyinTone(phonetic)) {
            formatIpaPhonetic(phonetic, true) ?: phonetic
        } else {
            phonetic
        }
    }

    /**
     * 解析有道/词典开放 API 返回的 JSON 字符串中的 IPA 音标
     */
    fun parseYoudaoPhoneticJson(jsonString: String): String? {
        return try {
            val root = JsonParser.parseString(jsonString).asJsonObject

            // 1. 优先从 simple.word 中获取
            if (root.has("simple") && root.get("simple").isJsonObject) {
                val simpleObj = root.getAsJsonObject("simple")
                if (simpleObj.has("word") && simpleObj.get("word").isJsonArray) {
                    val wordArray = simpleObj.getAsJsonArray("word")
                    if (wordArray.size() > 0 && wordArray.get(0).isJsonObject) {
                        val wordObj = wordArray.get(0).asJsonObject
                        val usphone = wordObj.get("usphone")?.takeIf { !it.isJsonNull }?.asString?.trim()
                        val ukphone = wordObj.get("ukphone")?.takeIf { !it.isJsonNull }?.asString?.trim()
                        val phone = usphone?.takeIf { it.isNotEmpty() } ?: ukphone?.takeIf { it.isNotEmpty() }
                        if (!phone.isNullOrEmpty()) {
                            return formatIpaPhonetic(phone, true)
                        }
                    }
                }
            }

            // 2. 兜底从 ec.word 中获取
            if (root.has("ec") && root.get("ec").isJsonObject) {
                val ecObj = root.getAsJsonObject("ec")
                if (ecObj.has("word") && ecObj.get("word").isJsonArray) {
                    val wordArray = ecObj.getAsJsonArray("word")
                    if (wordArray.size() > 0 && wordArray.get(0).isJsonObject) {
                        val wordObj = wordArray.get(0).asJsonObject
                        val usphone = wordObj.get("usphone")?.takeIf { !it.isJsonNull }?.asString?.trim()
                        val ukphone = wordObj.get("ukphone")?.takeIf { !it.isJsonNull }?.asString?.trim()
                        val phone = usphone?.takeIf { it.isNotEmpty() } ?: ukphone?.takeIf { it.isNotEmpty() }
                        if (!phone.isNullOrEmpty()) {
                            return formatIpaPhonetic(phone, true)
                        }
                    }
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 针对英文单词/短语，从开放词典源极速补充音标
     */
    fun fetchEnglishPhonetic(text: String, client: OkHttpClient = defaultClient): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // 若包含中文，不补充英文音标
        if (LanguageDetector.containsChinese(trimmed)) return null

        // 单词数量限制（支持单单词与常用短语，如 "hello", "apple", "good morning" 等，避免长句无谓请求）
        val words = trimmed.split(Regex("\\s+"))
        if (words.size > 4) return null

        val cleanQuery = trimmed.trim('\"', '\'', '.', '!', '?', ',', ';')
        if (cleanQuery.isEmpty()) return null

        val cacheKey = cleanQuery.lowercase()
        cache[cacheKey]?.let { return it }

        return try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val url = "https://dict.youdao.com/jsonapi?q=$encoded&dicts=%7B%22count%22%3A1%2C%22dicts%22%3A%5B%5B%22ec%22%5D%5D%7D"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            val result = parseYoudaoPhoneticJson(body)
            if (!result.isNullOrEmpty()) {
                if (cache.size > 200) cache.clear()
                cache[cacheKey] = result
            }
            result
        } catch (e: Exception) {
            null
        }
    }
}
