package com.puretrans.app.ui.image

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.puretrans.app.data.engine.SmartTranslationRouter
import com.puretrans.app.data.model.Language
import com.puretrans.app.util.LanguageDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

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
     * 对传入图片执行 OCR，提取文字与原图像素位置矩形
     */
    suspend fun recognizeText(bitmap: Bitmap): List<RawOcrItem> = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = Tasks.await(recognizer.process(image))

        val results = mutableListOf<RawOcrItem>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val text = line.text.trim()
                if (text.isEmpty()) continue

                // 规范化矩形边界，防止越界
                val safeRect = Rect(
                    box.left.coerceIn(0, bitmap.width),
                    box.top.coerceIn(0, bitmap.height),
                    box.right.coerceIn(0, bitmap.width),
                    box.bottom.coerceIn(0, bitmap.height)
                )

                if (safeRect.width() > 4 && safeRect.height() > 4) {
                    val (bgColor, textColor) = sampleColors(bitmap, safeRect)
                    results.add(
                        RawOcrItem(
                            originalText = text,
                            rect = safeRect,
                            dominantBgColor = bgColor,
                            textColor = textColor
                        )
                    )
                }
            }
        }
        results
    }

    /**
     * 智能采样文字周围边缘背景色与适配文字色
     */
    private fun sampleColors(bitmap: Bitmap, rect: Rect): Pair<Int, Int> {
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
