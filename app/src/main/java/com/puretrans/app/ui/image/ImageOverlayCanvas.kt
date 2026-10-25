package com.puretrans.app.ui.image

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

/**
 * 原图 1:1 原位覆盖绘制核心组件 (复刻谷歌翻译图片覆盖效果)
 */
@Composable
fun ImageOverlayCanvas(
    bitmap: Bitmap,
    textBlocks: List<TextBlockOverlay>,
    showOriginal: Boolean,
    selectedBlock: TextBlockOverlay?,
    onSelectBlock: (TextBlockOverlay?) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.8f, 5.0f)
        offset += offsetChange
    }

    val composeImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    // 原生文字绘制 Paint
    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            isSubpixelText = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .transformable(state = transformState)
            .pointerInput(bitmap, textBlocks, scale, offset) {
                detectTapGestures { tapOffset ->
                    val canvasWidth = size.width.toFloat()
                    val canvasHeight = size.height.toFloat()

                    val fitScale = min(
                        canvasWidth / bitmap.width.toFloat(),
                        canvasHeight / bitmap.height.toFloat()
                    )
                    val baseScale = fitScale * scale

                    val imgDisplayWidth = bitmap.width * baseScale
                    val imgDisplayHeight = bitmap.height * baseScale

                    val imgLeft = (canvasWidth - imgDisplayWidth) / 2f + offset.x
                    val imgTop = (canvasHeight - imgDisplayHeight) / 2f + offset.y

                    // 反求点击在 Bitmap 原图上的坐标
                    val clickBitmapX = (tapOffset.x - imgLeft) / baseScale
                    val clickBitmapY = (tapOffset.y - imgTop) / baseScale

                    val clicked = textBlocks.find {
                        it.boundingBox.contains(clickBitmapX.toInt(), clickBitmapY.toInt())
                    }
                    onSelectBlock(clicked)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val fitScale = min(
                canvasWidth / bitmap.width.toFloat(),
                canvasHeight / bitmap.height.toFloat()
            )
            val baseScale = fitScale * scale

            val imgDisplayWidth = bitmap.width * baseScale
            val imgDisplayHeight = bitmap.height * baseScale

            val imgLeft = (canvasWidth - imgDisplayWidth) / 2f + offset.x
            val imgTop = (canvasHeight - imgDisplayHeight) / 2f + offset.y

            // 1. 绘制底层原图
            drawImage(
                image = composeImageBitmap,
                dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
                dstSize = IntSize(imgDisplayWidth.toInt(), imgDisplayHeight.toInt())
            )

            // 2. 如果当前处于“显示原图模式”，则跳过覆盖绘制
            if (showOriginal) {
                return@Canvas
            }

            // 3. 1:1 原位覆盖绘制 (In-place Overlays)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas

                for (block in textBlocks) {
                    val box = block.boundingBox

                    // 计算当前文字块在屏幕 Canvas 上的投影矩形
                    val left = imgLeft + box.left * baseScale
                    val top = imgTop + box.top * baseScale
                    val width = box.width() * baseScale
                    val height = box.height() * baseScale

                    if (width <= 2 || height <= 2) continue

                    // A. 绘制智能背景遮罩，完全覆盖原图文字
                    drawRoundRect(
                        color = Color(block.dominantBgColor).copy(alpha = 0.96f),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(4f * scale, 4f * scale)
                    )

                    // B. 动态自适应字号绘制译文
                    val text = block.translatedText
                    if (text.isNotBlank()) {
                        textPaint.color = block.textColor

                        // 初始以高度 70% 作为基准字号
                        var fontSize = height * 0.68f
                        textPaint.textSize = fontSize

                        // 测量宽度，若超出可用宽度则进行比例缩放适配
                        val textWidth = textPaint.measureText(text)
                        val maxAvailableWidth = width * 0.94f
                        if (textWidth > maxAvailableWidth && textWidth > 0) {
                            val shrinkFactor = maxAvailableWidth / textWidth
                            fontSize = max(10f, fontSize * shrinkFactor)
                            textPaint.textSize = fontSize
                        }

                        // 垂直居中 baseline 计算
                        val fontMetrics = textPaint.fontMetrics
                        val baselineY = top + (height - fontMetrics.bottom + fontMetrics.top) / 2f - fontMetrics.top
                        val centerX = left + width / 2f

                        native.drawText(text, centerX, baselineY, textPaint)
                    }

                    // C. 若当前文字块被选中，绘制 Material 优雅高亮边框
                    if (selectedBlock?.id == block.id) {
                        drawRoundRect(
                            color = Color(0xFF2196F3),
                            topLeft = Offset(left - 2, top - 2),
                            size = Size(width + 4, height + 4),
                            cornerRadius = CornerRadius(6f * scale, 6f * scale),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f * scale)
                        )
                    }
                }
            }
        }
    }
}
