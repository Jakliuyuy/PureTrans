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

        val padX = (width * 0.035f).coerceIn(2f * scale, 8f * scale)
        val padY = (height * 0.05f).coerceIn(1f * scale, 6f * scale)
        val availWidth = (width - padX * 2).coerceAtLeast(10f)
        val availHeight = (height - padY * 2).coerceAtLeast(10f)

        // 1. 排版与测量
        val textPaint = TextPaint().apply {
            isAntiAlias = true
            isSubpixelText = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = block.textColor
            textAlign = Paint.Align.LEFT
        }

        val baseFontSize = height * 0.68f
        textPaint.textSize = baseFontSize
        val singleLineWidth = textPaint.measureText(text)

        val layout: StaticLayout
        val actualTextWidth: Float

        if (singleLineWidth <= availWidth) {
            // 单行能够放下：直接构建单行 StaticLayout，保持原位靠左自然排版
            actualTextWidth = singleLineWidth
            layout = StaticLayout.Builder.obtain(
                text, 0, text.length, textPaint, availWidth.toInt().coerceAtLeast(1)
            ).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
        } else {
            // 单行超出可用宽度：
            // 判断是否可以多行折行排版（原词框有足够高度且文本字数充足）
            if (height >= 26f * scale && text.length > 5) {
                // 尝试多行折行自适应字号
                var low = 9f * scale
                var high = baseFontSize
                var bestLayout: StaticLayout = StaticLayout.Builder.obtain(
                    text, 0, text.length, textPaint, availWidth.toInt().coerceAtLeast(1)
                ).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()

                for (iter in 0..5) {
                    val mid = (low + high) / 2f
                    textPaint.textSize = mid
                    val testL = StaticLayout.Builder.obtain(
                        text, 0, text.length, textPaint, availWidth.toInt().coerceAtLeast(1)
                    ).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()

                    if (testL.height <= availHeight) {
                        bestLayout = testL
                        low = mid
                    } else {
                        high = mid
                    }
                }
                layout = bestLayout
                var maxW = 0f
                for (i in 0 until layout.lineCount) {
                    maxW = max(maxW, layout.getLineWidth(i))
                }
                actualTextWidth = maxW
            } else {
                // 高度较窄，必须保持单行，缩放字号以适应
                val shrinkFactor = availWidth / singleLineWidth
                val fittedSize = max(9f * scale, baseFontSize * shrinkFactor)
                textPaint.textSize = fittedSize
                actualTextWidth = textPaint.measureText(text)
                layout = StaticLayout.Builder.obtain(
                    text, 0, text.length, textPaint, availWidth.toInt().coerceAtLeast(1)
                ).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
            }
        }

        // 2. 绘制智能背景遮罩（紧凑贴合，绝不无限拉长）
        val textNeededWidth = actualTextWidth + padX * 2
        val maskWidth = computeMaskWidth(width, textNeededWidth)

        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = block.dominantBgColor
            alpha = 245
            style = Paint.Style.FILL
        }

        val rectF = RectF(left, top, left + maskWidth, top + height)
        val cornerRadius = 4f * scale
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, bgPaint)

        // 3. 绘制文字：靠左对齐 (textStartX = left + padX)，垂直居中
        val layoutHeight = layout.height
        val textStartX = left + padX
        val textStartY = top + max(0f, (height - layoutHeight) / 2f)

        canvas.save()
        canvas.translate(textStartX, textStartY)
        layout.draw(canvas)
        canvas.restore()

        return maskWidth
    }
}
