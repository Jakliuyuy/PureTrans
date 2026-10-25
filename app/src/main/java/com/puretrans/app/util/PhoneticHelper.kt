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

    // 匹配英式/美式音标典型标记（如倒e(ə)、重音符号(ˈˌ)、长音符号(ː)、IPA特定元音/辅音(ʊ, ɪ, ʌ, æ, θ, ð, ʃ, ʒ, ŋ, ɜ, ɝ, ɑ, ɒ, ɔ, ɡ)等）
    private val IPA_MARKER_REGEX = Regex("[əʊɪʌæːˈˌθðʃʒŋɜɝɑɒɔɡ]")

    // 匹配汉语拼音特有声调字符（二声、三声、四声以及带有声调的 ü 等，英文音标绝对不会出现这些符号）
    private val PINYIN_DISTINCTIVE_TONE_REGEX = Regex("[áǎàóǒòéěèíǐìúǔùǖǘǚǜüÜÁǍÀÓǑÒÉĚÈÍǏÌÚǓÙǕǗǙǛ]")

    // 匹配所有汉语拼音声调字符（含平调宏音符 ā ē ī ō ū）
    private val ALL_PINYIN_TONE_REGEX = Regex("[āáǎàōóǒòēéěèīíǐìūúǔùǖǘǚǜĀÁǍÀŌÓǑÒĒÉĚÈĪÍǏÌŪÚǓÙǕǗǙǛ]")

    // 匹配英文长元音宏音符 (macron)
    private val MACRON_REGEX = Regex("[āēīōūĀĒĪŌŪ]")

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
        if (text.isBlank()) return false
        if (LanguageDetector.containsChinese(text)) return false
        // 包含二三四声或 ü 等拼音特征字符，绝非英文音标
        if (PINYIN_DISTINCTIVE_TONE_REGEX.containsMatchIn(text)) return false

        if (IPA_MARKER_REGEX.containsMatchIn(text)) return true
        val trimmed = text.trim()
        if ((trimmed.startsWith("[") && trimmed.endsWith("]")) ||
            (trimmed.startsWith("/") && trimmed.endsWith("/"))) {
            return true
        }

        // 兼容单音节英文音标转写（例如包含长元音宏音符如 hōm, ɡō, sē，且为纯拉丁音标结构）
        if (MACRON_REGEX.containsMatchIn(trimmed) && trimmed.matches(Regex("^[a-zA-Zɡˈˌːāēīōū\\s\\(\\)\\-']+$"))) {
            return true
        }

        return false
    }

    /**
     * 判断字符串中是否包含汉语拼音声调字符（排除英文 IPA 音标）
     */
    fun containsPinyinTone(text: String): Boolean {
        if (text.isBlank()) return false
        // 具备二三四声或 ü 声调的必为汉语拼音
        if (PINYIN_DISTINCTIVE_TONE_REGEX.containsMatchIn(text)) return true
        // 若包含 IPA 专属符号，排除在拼音之外
        if (IPA_MARKER_REGEX.containsMatchIn(text)) return false
        return ALL_PINYIN_TONE_REGEX.containsMatchIn(text)
    }

    /**
     * 将英文音标格式化为标准优雅的 IPA 格式（如 [həˈloʊ] 或 /həˈloʊ/）
     * 若已包含方括号或斜杠，则保持原样；若为拼音或无效内容则不作为英文音标处理（返回 null）
     */
    fun formatIpaPhonetic(phonetic: String?, isEnglish: Boolean = true): String? {
        val trimmed = phonetic?.trim() ?: return null
        if (trimmed.isEmpty()) return null

        // 若要求英文音标，但文本包含拼音声调且无英文音标特征，则拒绝作为英文音标
        if (isEnglish) {
            if (containsPinyinTone(trimmed) && !isLikelyEnglishPhonetic(trimmed)) {
                return null
            }
            if (LanguageDetector.containsChinese(trimmed)) {
                return null
            }
        }

        if (trimmed.startsWith("[") && trimmed.endsWith("]")) return trimmed
        if (trimmed.startsWith("/") && trimmed.endsWith("/")) return trimmed

        // 若不是英文，直接返回原字符串（如拼音不加括号）
        if (!isEnglish) {
            return trimmed
        }

        return "[$trimmed]"
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
                    if (s.isNotEmpty() && !LanguageDetector.containsChinese(s)) candidate2 = s
                }
                if (subArray.size() > 3 && !subArray.get(3).isJsonNull) {
                    val s = subArray.get(3).asString.trim()
                    if (s.isNotEmpty() && !LanguageDetector.containsChinese(s)) candidate3 = s
                }
            }
        }

        if (candidate2 == null && candidate3 == null) return null

        val isTargetEn = targetLang.code.startsWith("en", ignoreCase = true)
        val isTargetZh = targetLang.code.startsWith("zh", ignoreCase = true)

        if (isTargetEn) {
            // 目标语言为英文：必须提取合法的英文音标，严禁将中文拼音错当英文音标
            val is2En = candidate2?.let { isLikelyEnglishPhonetic(it) } ?: false
            val is3En = candidate3?.let { isLikelyEnglishPhonetic(it) } ?: false

            val selectedEnglish = when {
                is2En && !is3En -> candidate2
                !is2En && is3En -> candidate3
                is2En && is3En -> candidate3 ?: candidate2
                candidate2 != null && !containsPinyinTone(candidate2) -> candidate2
                candidate3 != null && !containsPinyinTone(candidate3) -> candidate3
                else -> null // 两者皆为拼音或无效，拒绝作为英文音标返回，交由词典极速补充
            }

            return selectedEnglish?.let { formatIpaPhonetic(it, true) }
        } else if (isTargetZh) {
            // 目标语言为中文：优先提取汉语拼音，绝不在中文译文下展示英文音标
            val is2Pinyin = candidate2?.let { containsPinyinTone(it) } ?: false
            val is3Pinyin = candidate3?.let { containsPinyinTone(it) } ?: false

            val selectedPinyin = when {
                is2Pinyin && !is3Pinyin -> candidate2
                !is2Pinyin && is3Pinyin -> candidate3
                is2Pinyin && is3Pinyin -> candidate2
                candidate2 != null && !isLikelyEnglishPhonetic(candidate2) -> candidate2
                candidate3 != null && !isLikelyEnglishPhonetic(candidate3) -> candidate3
                else -> null
            }
            return selectedPinyin
        } else {
            // 其他语言
            val chosen = candidate2 ?: candidate3 ?: return null
            return formatIfTargetEnglish(chosen, targetLang)
        }
    }

    private fun formatIfTargetEnglish(phonetic: String, targetLang: Language): String? {
        val isTargetEn = targetLang.code.startsWith("en", ignoreCase = true)
        if (isTargetEn) {
            if (containsPinyinTone(phonetic) && !isLikelyEnglishPhonetic(phonetic)) return null
            return formatIpaPhonetic(phonetic, true)
        }
        return phonetic
    }

    /**
     * 解析有道/词典开放 API 返回的 JSON 字符串中的 IPA 音标
     */
    fun parseYoudaoPhoneticJson(jsonString: String): String? {
        return try {
            val root = JsonParser.parseString(jsonString).asJsonObject

            // 1. 优先从 simple.word 中获取 (兼容 usphone, ukphone, phone)
            if (root.has("simple") && root.get("simple").isJsonObject) {
                val simpleObj = root.getAsJsonObject("simple")
                if (simpleObj.has("word") && simpleObj.get("word").isJsonArray) {
                    val wordArray = simpleObj.getAsJsonArray("word")
                    if (wordArray.size() > 0 && wordArray.get(0).isJsonObject) {
                        val wordObj = wordArray.get(0).asJsonObject
                        val usphone = wordObj.get("usphone")?.takeIf { !it.isJsonNull }?.asString?.trim()
                        val ukphone = wordObj.get("ukphone")?.takeIf { !it.isJsonNull }?.asString?.trim()
                        val phoneField = wordObj.get("phone")?.takeIf { !it.isJsonNull }?.asString?.trim()
                        val phone = usphone?.takeIf { it.isNotEmpty() }
                            ?: ukphone?.takeIf { it.isNotEmpty() }
                            ?: phoneField?.takeIf { it.isNotEmpty() }
                        if (!phone.isNullOrEmpty()) {
                            return formatIpaPhonetic(phone, true)
                        }
                    }
                }
            }

            // 2. 兜底从 ec.word 中获取 (兼容 usphone, ukphone, phone)
            if (root.has("ec") && root.get("ec").isJsonObject) {
                val ecObj = root.getAsJsonObject("ec")
                if (ecObj.has("word") && ecObj.get("word").isJsonArray) {
                    val wordArray = ecObj.getAsJsonArray("word")
                    if (wordArray.size() > 0 && wordArray.get(0).isJsonObject) {
                        val wordObj = wordArray.get(0).asJsonObject
                        val usphone = wordObj.get("usphone")?.takeIf { !it.isJsonNull }?.asString?.trim()
                        val ukphone = wordObj.get("ukphone")?.takeIf { !it.isJsonNull }?.asString?.trim()
                        val phoneField = wordObj.get("phone")?.takeIf { !it.isJsonNull }?.asString?.trim()
                        val phone = usphone?.takeIf { it.isNotEmpty() }
                            ?: ukphone?.takeIf { it.isNotEmpty() }
                            ?: phoneField?.takeIf { it.isNotEmpty() }
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

        // 清洗首尾与内部标点符号（如末尾标点、中间逗号/分号等），但保留单词内部撇号与连字符（如 don't, ice-cream）
        val cleanQuery = trimmed
            .replace(Regex("[^a-zA-Z0-9'\\-\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
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
