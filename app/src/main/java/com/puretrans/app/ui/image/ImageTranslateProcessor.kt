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
        val textColor: Int
    )

    /**
     * 抽象的轻量视觉元素模型，解耦 Android Framework 便于全面单元测试
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
     * 细粒度语义片段模型（如左侧命令 vs 右侧描述）
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
     * 将单行中的元素根据水平间距 (gap) 进行细粒度语义分段拆分
     * 避免整行粗暴合并为一个大长条矩形，保留行内留白与独立列排版
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

        // 按水平位置从左至右排序
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
                // 当水平间隙明显大于正常词间距时切分（例如 gap > refHeight * 0.75 且 gap > 10 像素）
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
     * 对传入图片执行 OCR，提取细粒度文字片段与紧凑贴合的原图像素位置矩形
     */
    suspend fun recognizeText(bitmap: Bitmap): List<RawOcrItem> = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = Tasks.await(recognizer.process(image))

        val results = mutableListOf<RawOcrItem>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val lineText = line.text.trim()
                if (lineText.isEmpty()) continue

                // 转换 elements
                val simpleElements = line.elements.mapNotNull { elem ->
                    val eb = elem.boundingBox ?: return@mapNotNull null
                    val t = elem.text.trim()
                    if (t.isEmpty()) return@mapNotNull null
                    SimpleElement(t, eb.left, eb.top, eb.right, eb.bottom)
                }

                // 细粒度视觉文本块拆分（Line -> Semantic Segments）
                val segments = splitSimpleElements(
                    elements = simpleElements,
                    fallbackText = lineText,
                    fallbackLeft = box.left,
                    fallbackTop = box.top,
                    fallbackRight = box.right,
                    fallbackBottom = box.bottom
                )

                for (seg in segments) {
                    // 规范化矩形边界，防止越界，并保持紧凑贴合
                    val safeRect = Rect(
                        seg.left.coerceIn(0, bitmap.width),
                        seg.top.coerceIn(0, bitmap.height),
                        seg.right.coerceIn(0, bitmap.width),
                        seg.bottom.coerceIn(0, bitmap.height)
                    )

                    if (safeRect.width() > 4 && safeRect.height() > 4) {
                        // 针对不同细分块分别提取各自局部的真实背景色与文字色
                        val (bgColor, textColor) = sampleColors(bitmap, safeRect)
                        results.add(
                            RawOcrItem(
                                originalText = seg.text,
                                rect = safeRect,
                                dominantBgColor = bgColor,
                                textColor = textColor
                            )
                        )
                    }
                }
            }
        }
        results
    }

    /**
     * 智能采样文字周围边缘背景色与适配文字色
     */
    fun sampleColors(bitmap: Bitmap, rect: Rect): Pair<Int, Int> {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0

        // 沿矩形四周边缘各采样若干点
        val stepX = (rect.width() / 4).coerceAtLeast(1)
        val stepY = (rect.height() / 4).coerceAtLeast(1)

        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val bottom = (rect.bottom - 1).coerceIn(0, bitmap.height - 1)
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val right = (rect.right - 1).coerceIn(0, bitmap.width - 1)

        for (x in left..right step stepX) {
            val pTop = bitmap.getPixel(x, top)
            rSum += Color.red(pTop)
            gSum += Color.green(pTop)
            bSum += Color.blue(pTop)
            count++

            val pBottom = bitmap.getPixel(x, bottom)
            rSum += Color.red(pBottom)
            gSum += Color.green(pBottom)
            bSum += Color.blue(pBottom)
            count++
        }

        for (y in top..bottom step stepY) {
            val pLeft = bitmap.getPixel(left, y)
            rSum += Color.red(pLeft)
            gSum += Color.green(pLeft)
            bSum += Color.blue(pLeft)
            count++

            val pRight = bitmap.getPixel(right, y)
            rSum += Color.red(pRight)
            gSum += Color.green(pRight)
            bSum += Color.blue(pRight)
            count++
        }

        if (count == 0) {
            return Pair(Color.WHITE, Color.BLACK)
        }

        val avgR = (rSum / count).toInt().coerceIn(0, 255)
        val avgG = (gSum / count).toInt().coerceIn(0, 255)
        val avgB = (bSum / count).toInt().coerceIn(0, 255)

        val bgColor = Color.rgb(avgR, avgG, avgB)
        // 亮度公式
        val luminance = 0.299 * avgR + 0.587 * avgG + 0.114 * avgB
        val textColor = if (luminance > 128) {
            Color.argb(235, 18, 18, 18) // 浅底深字
        } else {
            Color.argb(235, 245, 245, 245) // 深底浅字
        }

        return Pair(bgColor, textColor)
    }

    /**
     * 将识别到的所有文字块结合路由引擎进行并发翻译
     */
    suspend fun translateBlocks(
        rawItems: List<RawOcrItem>,
        sourceLang: Language,
        targetLang: Language,
        router: SmartTranslationRouter
    ): List<TextBlockOverlay> = coroutineScope {
        val deferredList = rawItems.mapIndexed { index, item ->
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
                    textColor = item.textColor
                )
            }
        }
        deferredList.awaitAll()
    }
}
