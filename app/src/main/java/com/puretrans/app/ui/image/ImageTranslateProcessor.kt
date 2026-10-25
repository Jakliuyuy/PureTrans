package com.puretrans.app.ui.image

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.puretrans.app.data.engine.SmartTranslationRouter
import com.puretrans.app.data.model.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 图像 OCR 识别与 1:1 原位覆盖处理核心引擎
 */
object ImageTranslateProcessor {

    data class RawOcrItem(
        val originalText: String,
        val rect: Rect,
        val dominantBgColor: Int,
        val textColor: Int,
        val lineCount: Int = 1
    )

    /**
     * 基础文本行模型
     */
    data class RawTextLine(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val rect: Rect get() = Rect(left, top, right, bottom)
    }

    /**
     * 段落聚类块模型（多个紧凑相连的行合并为一个段落）
     */
    data class ParagraphCluster(
        val lines: List<RawTextLine>,
        val text: String,
        val rect: Rect
    ) {
        val lineCount: Int get() = lines.size
        val left: Int get() = lines.minOfOrNull { it.left } ?: rect.left
        val top: Int get() = lines.minOfOrNull { it.top } ?: rect.top
        val right: Int get() = lines.maxOfOrNull { it.right } ?: rect.right
        val bottom: Int get() = lines.maxOfOrNull { it.bottom } ?: rect.bottom
    }

    /**
     * 轻量轴对齐矩形外框，完全解耦 Android Framework
     */
    data class BoxBounds(
        var left: Int,
        var top: Int,
        var right: Int,
        var bottom: Int
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /**
     * 抽象的轻量视觉元素模型，保持向后兼容便于单元测试
     */
    data class SimpleElement(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /**
     * 细粒度语义片段模型，保持向后兼容
     */
    data class SimpleSegment(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /**
     * 过滤 OCR 产生的非文本乱码或杂散符号
     */
    fun isGibberishOrNoise(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true

        var letterOrDigitOrCjkCount = 0
        var symbolCount = 0
        for (ch in trimmed) {
            if (ch.isLetterOrDigit() || Character.isIdeographic(ch.code)) {
                letterOrDigitOrCjkCount++
            } else if (!ch.isWhitespace()) {
                symbolCount++
            }
        }

        // 纯符号，无任何有效字母/数字/文字
        if (letterOrDigitOrCjkCount == 0) return true

        // 极短且带符号的噪点（如单个符号或标点）
        if (letterOrDigitOrCjkCount <= 1 && trimmed.length <= 2 && symbolCount > 0) return true

        // 异常标点符号比率过高（例如标点占比超过 50%）
        val totalChars = letterOrDigitOrCjkCount + symbolCount
        if (totalChars > 2 && symbolCount.toFloat() / totalChars > 0.5f) return true

        // Latin OCR 识别中文常出现的脱字符乱码特征 (如 ETA^-D, ~^ 等)
        if (trimmed.contains("^-") || trimmed.contains("^_") || trimmed.contains("~^")) return true

        return false
    }

    /**
     * 判断文本是否主要由中文汉字组成（用于保护原图中文）
     */
    fun isMainlyChinese(text: String): Boolean {
        var cjkCount = 0
        var latinCount = 0
        for (cp in text.codePoints()) {
            val ub = Character.UnicodeBlock.of(cp)
            if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A) {
                cjkCount++
            } else if (Character.isLetter(cp)) {
                latinCount++
            }
        }
        if (cjkCount == 0) return false
        // 包含汉字且汉字占主体或无英文字母
        return (cjkCount >= 2 && cjkCount >= latinCount * 0.4) || (cjkCount >= 1 && latinCount == 0)
    }

    /**
     * 判断两行文本是否属于同一个自然段落
     */
    fun canMergeLines(
        prevLine: RawTextLine,
        currentLine: RawTextLine,
        clusterBox: BoxBounds
    ): Boolean {
        val h1 = prevLine.height
        val h2 = currentLine.height
        if (h1 <= 0 || h2 <= 0) return false

        val avgH = (h1 + h2) / 2.0f
        val vGap = currentLine.top - prevLine.bottom

        // 1. 垂直间距检查：紧凑行距合并，段落空行或左右分栏隔离
        if (vGap < -avgH * 0.4f || vGap > avgH * 1.35f) {
            return false
        }

        // 2. 字号一致性检查：同一段落正文字号相近
        val hDiffRatio = kotlin.math.abs(h1 - h2).toFloat() / kotlin.math.max(h1, h2)
        if (hDiffRatio > 0.45f) {
            return false
        }

        // 3. 水平重叠与对齐检查：同一段落各行在水平方向有重合或左对齐相近
        val overlapLeft = kotlin.math.max(clusterBox.left, currentLine.left)
        val overlapRight = kotlin.math.min(clusterBox.right, currentLine.right)
        val overlapWidth = kotlin.math.max(0, overlapRight - overlapLeft)
        val shorterWidth = kotlin.math.min(clusterBox.width, currentLine.width)

        val leftDiff = kotlin.math.abs(clusterBox.left - currentLine.left)
        val isLeftAligned = leftDiff <= avgH * 1.5f
        val hasSufficientOverlap = shorterWidth > 0 && (overlapWidth.toFloat() / shorterWidth) >= 0.35f

        if (!isLeftAligned && !hasSufficientOverlap) {
            return false
        }

        // 4. 独立标题/导航守护规则（如 "Thought for 35s >"）
        val prevTextTrimmed = prevLine.text.trim()
        if (prevTextTrimmed.endsWith(">") || prevTextTrimmed.endsWith("»") || prevTextTrimmed.endsWith("→")) {
            return false
        }

        return true
    }

    fun canMergeLines(
        prevLine: RawTextLine,
        currentLine: RawTextLine,
        clusterBox: Rect
    ): Boolean = canMergeLines(prevLine, currentLine, BoxBounds(clusterBox.left, clusterBox.top, clusterBox.right, clusterBox.bottom))

    /**
     * 将识别到的多行按几何与排版特征聚类为段落（Paragraph / Block Clustering）
     */
    fun clusterLinesIntoParagraphs(lines: List<RawTextLine>): List<ParagraphCluster> {
        if (lines.isEmpty()) return emptyList()

        val sortedLines = lines.sortedWith(compareBy({ it.top }, { it.left }))

        val clusters = mutableListOf<ParagraphCluster>()
        var currentClusterLines = mutableListOf<RawTextLine>()
        var currentBox: BoxBounds? = null

        for (line in sortedLines) {
            val textTrimmed = line.text.trim()
            if (textTrimmed.isEmpty() || line.width <= 0 || line.height <= 0) continue

            if (currentClusterLines.isEmpty()) {
                currentClusterLines.add(line)
                currentBox = BoxBounds(line.left, line.top, line.right, line.bottom)
            } else {
                val prevLine = currentClusterLines.last()
                val box = currentBox!!

                if (canMergeLines(prevLine, line, box)) {
                    currentClusterLines.add(line)
                    box.left = kotlin.math.min(box.left, line.left)
                    box.top = kotlin.math.min(box.top, line.top)
                    box.right = kotlin.math.max(box.right, line.right)
                    box.bottom = kotlin.math.max(box.bottom, line.bottom)
                } else {
                    clusters.add(createClusterFromLines(currentClusterLines, box))
                    currentClusterLines = mutableListOf(line)
                    currentBox = BoxBounds(line.left, line.top, line.right, line.bottom)
                }
            }
        }

        if (currentClusterLines.isNotEmpty() && currentBox != null) {
            clusters.add(createClusterFromLines(currentClusterLines, currentBox))
        }

        return clusters
    }

    private fun createClusterFromLines(lines: List<RawTextLine>, box: BoxBounds): ParagraphCluster {
        val mergedText = lines.joinToString(" ") { it.text.trim() }
            .replace(" - ", "-")
            .replace("\\s+".toRegex(), " ")
            .trim()
        return ParagraphCluster(
            lines = lines,
            text = mergedText,
            rect = Rect(box.left, box.top, box.right, box.bottom)
        )
    }

    /**
     * 将单行中的元素根据水平间距进行拆分，保持向后兼容
     */
    fun splitSimpleElements(
        elements: List<SimpleElement>,
        fallbackText: String,
        fallbackLeft: Int,
        fallbackTop: Int,
        fallbackRight: Int,
        fallbackBottom: Int
    ): List<SimpleSegment> {
        val validElements = elements.filter { it.text.isNotBlank() && it.width > 0 && it.height > 0 }
        if (validElements.isEmpty()) {
            return if (fallbackText.isNotBlank() && fallbackRight > fallbackLeft && fallbackBottom > fallbackTop) {
                listOf(SimpleSegment(fallbackText.trim(), fallbackLeft, fallbackTop, fallbackRight, fallbackBottom))
            } else {
                emptyList()
            }
        }

        val sorted = validElements.sortedBy { it.left }
        val segments = mutableListOf<MutableList<SimpleElement>>()
        var currentSegment = mutableListOf<SimpleElement>()

        for (elem in sorted) {
            if (currentSegment.isEmpty()) {
                currentSegment.add(elem)
            } else {
                val prevElem = currentSegment.last()
                val gap = elem.left - prevElem.right
                val refHeight = max(prevElem.height, elem.height)
                val isSplit = gap > refHeight * 0.75f && gap > 10

                if (isSplit) {
                    segments.add(currentSegment)
                    currentSegment = mutableListOf(elem)
                } else {
                    currentSegment.add(elem)
                }
            }
        }
        if (currentSegment.isNotEmpty()) {
            segments.add(currentSegment)
        }

        return segments.mapNotNull { seg ->
            if (seg.isEmpty()) return@mapNotNull null
            val segText = seg.joinToString(" ") { it.text.trim() }.trim()
            if (segText.isEmpty()) return@mapNotNull null

            var sLeft = Int.MAX_VALUE
            var sTop = Int.MAX_VALUE
            var sRight = Int.MIN_VALUE
            var sBottom = Int.MIN_VALUE

            for (e in seg) {
                if (e.left < sLeft) sLeft = e.left
                if (e.top < sTop) sTop = e.top
                if (e.right > sRight) sRight = e.right
                if (e.bottom > sBottom) sBottom = e.bottom
            }

            if (sRight <= sLeft || sBottom <= sTop) null else SimpleSegment(segText, sLeft, sTop, sRight, sBottom)
        }
    }

    /**
     * 对传入图片执行 OCR，提取并聚类为整段文本与外接矩形
     */
    suspend fun recognizeText(bitmap: Bitmap): List<RawOcrItem> = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = Tasks.await(recognizer.process(image))

        val allLines = mutableListOf<RawTextLine>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val lineText = line.text.trim()
                if (lineText.isEmpty() || isGibberishOrNoise(lineText)) continue

                val safeRect = Rect(
                    box.left.coerceIn(0, bitmap.width),
                    box.top.coerceIn(0, bitmap.height),
                    box.right.coerceIn(0, bitmap.width),
                    box.bottom.coerceIn(0, bitmap.height)
                )

                if (safeRect.width() > 4 && safeRect.height() > 4) {
                    allLines.add(
                        RawTextLine(
                            text = lineText,
                            left = safeRect.left,
                            top = safeRect.top,
                            right = safeRect.right,
                            bottom = safeRect.bottom
                        )
                    )
                }
            }
        }

        // 段落级聚合：将属于同一段落的多行聚合为一个 ParagraphCluster
        val clusters = clusterLinesIntoParagraphs(allLines)
        val results = mutableListOf<RawOcrItem>()

        for (cluster in clusters) {
            val safeRect = Rect(
                cluster.rect.left.coerceIn(0, bitmap.width),
                cluster.rect.top.coerceIn(0, bitmap.height),
                cluster.rect.right.coerceIn(0, bitmap.width),
                cluster.rect.bottom.coerceIn(0, bitmap.height)
            )

            if (safeRect.width() > 4 && safeRect.height() > 4) {
                val (bgColor, textColor) = sampleColors(bitmap, safeRect)
                results.add(
                    RawOcrItem(
                        originalText = cluster.text,
                        rect = safeRect,
                        dominantBgColor = bgColor,
                        textColor = textColor,
                        lineCount = cluster.lineCount
                    )
                )
            }
        }
        results
    }

    /**
     * 智能采样文字周围边缘背景色与适配文字色
     */
    fun sampleColors(bitmap: Bitmap, rect: Rect): Pair<Int, Int> {
        val samplePixels = mutableListOf<Int>()
        val bw = bitmap.width
        val bh = bitmap.height

        // 在 rect 外部环绕区域采样（Outer Ring），避开文字笔画反走样
        val marginOuter = 6
        val marginInner = 2

        val topOuter = (rect.top - marginOuter).coerceIn(0, bh - 1)
        val topInner = (rect.top - marginInner).coerceIn(0, bh - 1)
        val bottomInner = (rect.bottom + marginInner).coerceIn(0, bh - 1)
        val bottomOuter = (rect.bottom + marginOuter).coerceIn(0, bh - 1)
        val leftOuter = (rect.left - marginOuter).coerceIn(0, bw - 1)
        val leftInner = (rect.left - marginInner).coerceIn(0, bw - 1)
        val rightInner = (rect.right + marginInner).coerceIn(0, bw - 1)
        val rightOuter = (rect.right + marginOuter).coerceIn(0, bw - 1)

        val stepX = kotlin.math.max(1, rect.width() / 8)
        val stepY = kotlin.math.max(1, rect.height() / 8)

        if (rect.top >= marginInner) {
            for (x in rect.left.coerceIn(0, bw - 1)..rect.right.coerceIn(0, bw - 1) step stepX) {
                samplePixels.add(bitmap.getPixel(x, topOuter))
                samplePixels.add(bitmap.getPixel(x, topInner))
            }
        }
        if (rect.bottom + marginInner < bh) {
            for (x in rect.left.coerceIn(0, bw - 1)..rect.right.coerceIn(0, bw - 1) step stepX) {
                samplePixels.add(bitmap.getPixel(x, bottomInner))
                samplePixels.add(bitmap.getPixel(x, bottomOuter))
            }
        }
        if (rect.left >= marginInner) {
            for (y in rect.top.coerceIn(0, bh - 1)..rect.bottom.coerceIn(0, bh - 1) step stepY) {
                samplePixels.add(bitmap.getPixel(leftOuter, y))
                samplePixels.add(bitmap.getPixel(leftInner, y))
            }
        }
        if (rect.right + marginInner < bw) {
            for (y in rect.top.coerceIn(0, bh - 1)..rect.bottom.coerceIn(0, bh - 1) step stepY) {
                samplePixels.add(bitmap.getPixel(rightInner, y))
                samplePixels.add(bitmap.getPixel(rightOuter, y))
            }
        }

        if (samplePixels.size < 12) {
            val l = rect.left.coerceIn(0, bw - 1)
            val r = (rect.right - 1).coerceIn(0, bw - 1)
            val t = rect.top.coerceIn(0, bh - 1)
            val b = (rect.bottom - 1).coerceIn(0, bh - 1)
            samplePixels.add(bitmap.getPixel(l, t))
            samplePixels.add(bitmap.getPixel(r, t))
            samplePixels.add(bitmap.getPixel(l, b))
            samplePixels.add(bitmap.getPixel(r, b))
        }

        return sampleDominantColorFromPixels(samplePixels)
    }

    fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun colorRed(color: Int): Int = (color ushr 16) and 0xFF
    fun colorGreen(color: Int): Int = (color ushr 8) and 0xFF
    fun colorBlue(color: Int): Int = color and 0xFF

    /**
     * 纯算法：从采样像素集合中提取直方图众数高精度主导背景色与文字色
     */
    fun sampleDominantColorFromPixels(pixels: List<Int>): Pair<Int, Int> {
        if (pixels.isEmpty()) {
            return Pair(rgb(255, 255, 255), rgb(20, 20, 20))
        }

        val binCounts = mutableMapOf<Int, Int>()
        for (p in pixels) {
            val r = colorRed(p) ushr 3
            val g = colorGreen(p) ushr 3
            val b = colorBlue(p) ushr 3
            val bin = (r shl 10) or (g shl 5) or b
            binCounts[bin] = (binCounts[bin] ?: 0) + 1
        }

        var maxCount = -1
        var dominantBin = 0
        for ((bin, count) in binCounts) {
            if (count > maxCount) {
                maxCount = count
                dominantBin = bin
            }
        }

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var validCount = 0

        val targetR = (dominantBin shr 10) and 0x1F
        val targetG = (dominantBin shr 5) and 0x1F
        val targetB = dominantBin and 0x1F

        for (p in pixels) {
            val r5 = colorRed(p) ushr 3
            val g5 = colorGreen(p) ushr 3
            val b5 = colorBlue(p) ushr 3
            if (kotlin.math.abs(r5 - targetR) <= 1 &&
                kotlin.math.abs(g5 - targetG) <= 1 &&
                kotlin.math.abs(b5 - targetB) <= 1) {
                rSum += colorRed(p)
                gSum += colorGreen(p)
                bSum += colorBlue(p)
                validCount++
            }
        }

        val avgR = if (validCount > 0) (rSum / validCount).toInt() else (targetR shl 3) + 4
        val avgG = if (validCount > 0) (gSum / validCount).toInt() else (targetG shl 3) + 4
        val avgB = if (validCount > 0) (bSum / validCount).toInt() else (targetB shl 3) + 4

        // 纯净色吸附：白底卡片或暗黑底色吸附，彻底消除灰膏药
        val finalBgColor = if (avgR >= 242 && avgG >= 242 && avgB >= 242 &&
            kotlin.math.abs(avgR - avgG) <= 5 && kotlin.math.abs(avgR - avgB) <= 5) {
            rgb(255, 255, 255)
        } else if (avgR <= 25 && avgG <= 25 && avgB <= 25 &&
            kotlin.math.abs(avgR - avgG) <= 4 && kotlin.math.abs(avgR - avgB) <= 4) {
            rgb(18, 18, 18)
        } else {
            rgb(avgR.coerceIn(0, 255), avgG.coerceIn(0, 255), avgB.coerceIn(0, 255))
        }

        val lumR = colorRed(finalBgColor)
        val lumG = colorGreen(finalBgColor)
        val lumB = colorBlue(finalBgColor)
        val luminance = 0.299 * lumR + 0.587 * lumG + 0.114 * lumB

        val textColor = if (luminance > 130) {
            rgb(18, 18, 18)
        } else {
            rgb(245, 245, 245)
        }

        return Pair(finalBgColor, textColor)
    }

    /**
     * 将识别到的段落结合翻译路由并发翻译（具备中文保护与乱码过滤）
     */
    suspend fun translateBlocks(
        rawItems: List<RawOcrItem>,
        sourceLang: Language,
        targetLang: Language,
        router: SmartTranslationRouter
    ): List<TextBlockOverlay> = coroutineScope {
        // 1. 中文保护与乱码过滤
        val filteredItems = rawItems.filter { item ->
            val text = item.originalText.trim()
            if (isGibberishOrNoise(text)) return@filter false

            // 若原识别内容主要为中文字符且目标语是中文，直接跳过覆盖，保留原图原始文字
            if (targetLang.code.startsWith("zh") && isMainlyChinese(text)) {
                return@filter false
            }

            true
        }

        // 2. 段落级整段翻译
        val deferredList = filteredItems.mapIndexed { index, item ->
            async(Dispatchers.IO) {
                val transRes = router.translate(
                    text = item.originalText,
                    sourceLang = sourceLang,
                    targetLang = targetLang
                )
                val finalTranslated = if (transRes.isSuccess && transRes.translatedText.isNotBlank()) {
                    transRes.translatedText
                } else {
                    item.originalText
                }
                TextBlockOverlay(
                    id = "block_$index",
                    originalText = item.originalText,
                    translatedText = finalTranslated,
                    boundingBox = item.rect,
                    dominantBgColor = item.dominantBgColor,
                    textColor = item.textColor,
                    lineCount = item.lineCount
                )
            }
        }
        deferredList.awaitAll()
    }
}

