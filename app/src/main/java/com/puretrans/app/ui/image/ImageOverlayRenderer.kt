package com.puretrans.app.ui.image

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max
import kotlin.math.min

/**
 * 图像翻译原位覆盖渲染核心引擎 (1:1 In-place Overlay Renderer)
 * 专为消除硬居中错位、紧凑贴合文本真实区域、多行自然折行排版而设计
 */
object ImageOverlayRenderer {

    /**
     * 计算紧凑贴合的遮罩宽度（根据原词框与译文所需宽度的较小合理外接矩形）
     * 严禁整行无限拉长，严禁向右过度涂抹遮挡非文本或图标元素
     */
    fun computeMaskWidth(boxWidth: Float, textNeededWidth: Float): Float {
        if (boxWidth <= 0f) return 0f
        if (textNeededWidth <= 0f) return boxWidth
        // 遮罩宽度根据原词框与译文所需宽度的较小合理外接矩形绘制
        // 取 textNeededWidth 与合理保底宽度（boxWidth * 0.75f），且绝不超过原词框本身
        return min(boxWidth, max(textNeededWidth, boxWidth * 0.75f))
    }

    /**
     * 核心绘制逻辑：紧凑贴合遮罩 + 靠左自然排版 + 自适应折行/单行
     *
     * @return 实际绘制的遮罩宽度（用于高亮边框适配）
     */
    fun drawTextBlock(
        canvas: Canvas,
        block: TextBlockOverlay,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float
    ): Float {
        val text = block.translatedText.trim()
        if (text.isEmpty() || width <= 2 || height <= 2) return width

        // 1. 彻底纯色抹除原文字 (100% 无痕抹平覆盖)
        // 外扩 2~3 像素，彻底覆盖字体上下左右溢出的反走样和笔画末端（如 g, j, p, q, y 等）
        val expand = (2.5f * scale).coerceIn(2f, 5f)
        val maskRect = RectF(
            left - expand,
            top - expand,
            left + width + expand,
            top + height + expand
        )

        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = block.dominantBgColor
            alpha = 255 // 必须 100% 不透明纯色抹平，绝不允许旧文字透出
            style = Paint.Style.FILL
        }

        val cornerRadius = 3f * scale
        canvas.drawRoundRect(maskRect, cornerRadius, cornerRadius, bgPaint)

        // 2. 排版空间与内边距 (Padding)
        val padX = (width * 0.025f).coerceIn(4f * scale, 12f * scale)
        val padY = (height * 0.035f).coerceIn(2f * scale, 8f * scale)
        val availWidth = (width - padX * 2).coerceAtLeast(10f)
        val availHeight = (height - padY * 2).coerceAtLeast(10f)

        // 3. 原生 StaticLayout 优雅排版
        val textPaint = TextPaint().apply {
            isAntiAlias = true
            isSubpixelText = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            color = block.textColor
            textAlign = Paint.Align.LEFT
        }

        val layout = computeOptimalLayout(
            text = text,
            textPaint = textPaint,
            availWidth = availWidth,
            availHeight = availHeight,
            originalLineCount = block.lineCount,
            boxHeight = height,
            scale = scale
        )

        // 4. 文字靠左排版 (ALIGN_NORMAL) 且垂直居中
        val layoutHeight = layout.height
        val textStartX = left + padX
        val textStartY = top + padY + max(0f, (availHeight - layoutHeight) / 2f)

        canvas.save()
        canvas.translate(textStartX, textStartY)
        layout.draw(canvas)
        canvas.restore()

        return width + expand * 2
    }

    /**
     * 自适应计算 StaticLayout：根据矩形尺寸与原始行数适配最佳字号，保证在矩形内舒展居中且自动折行
     */
    fun computeOptimalLayout(
        text: String,
        textPaint: TextPaint,
        availWidth: Float,
        availHeight: Float,
        originalLineCount: Int,
        boxHeight: Float,
        scale: Float
    ): StaticLayout {
        val widthInt = availWidth.toInt().coerceAtLeast(1)

        // 单行独立标题场景 (lineCount == 1 且文本较短)
        if (originalLineCount == 1 && text.length <= 25) {
            val baseSize = (boxHeight * 0.68f).coerceIn(12f * scale, 32f * scale)
            textPaint.textSize = baseSize
            val singleLineWidth = textPaint.measureText(text)

            if (singleLineWidth <= availWidth) {
                // 单行即可完全容纳
                return buildStaticLayout(text, textPaint, widthInt)
            } else {
                // 超出一行：微调字号保持单行，若过小则允许折为两行
                val fittedSize = (availWidth / singleLineWidth) * baseSize
                if (fittedSize >= 11f * scale) {
                    textPaint.textSize = fittedSize
                    return buildStaticLayout(text, textPaint, widthInt)
                }
            }
        }

        // 多行段落场景 (Paragraph Block)
        val avgLineH = boxHeight / max(1, originalLineCount).toFloat()
        val maxSize = min(availHeight * 0.85f, max(12f * scale, avgLineH * 0.82f))
        val minSize = 9f * scale

        var low = minSize
        var high = maxSize
        textPaint.textSize = high

        var bestLayout = buildStaticLayout(text, textPaint, widthInt)
        if (bestLayout.height <= availHeight) {
            return bestLayout
        }

        // 二分搜索使得 StaticLayout 高度恰好在 availHeight 内
        for (iter in 0..6) {
            val mid = (low + high) / 2f
            textPaint.textSize = mid
            val testLayout = buildStaticLayout(text, textPaint, widthInt)

            if (testLayout.height <= availHeight) {
                bestLayout = testLayout
                low = mid // 尝试更大字号
            } else {
                high = mid // 超出高度，减小字号
            }
        }

        return bestLayout
    }

    private fun buildStaticLayout(text: String, textPaint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f) // 1.15 倍行间距，舒适阅读质感
            .setIncludePad(false)
            .build()
    }
}
