package com.puretrans.app.ui.image

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlin.math.min

/**
 * 原图 1:1 原位覆盖绘制核心组件 (复刻谷歌翻译图片覆盖效果)
 * 采用细粒度多列拆分、紧凑背景遮罩与靠左自然排版
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

                    // A & B: 使用 ImageOverlayRenderer 紧凑贴合绘制遮罩与靠左排版文字
                    val maskWidth = ImageOverlayRenderer.drawTextBlock(
                        canvas = native,
                        block = block,
                        left = left,
                        top = top,
                        width = width,
                        height = height,
                        scale = scale
                    )

                    // C. 若当前文字块被选中，绘制紧凑贴合的 Material 优雅高亮边框
                    if (selectedBlock?.id == block.id) {
                        val expand = (2.5f * scale).coerceIn(2f, 5f)
                        drawRoundRect(
                            color = Color(0xFF2196F3),
                            topLeft = Offset(left - expand - 2f, top - expand - 2f),
                            size = Size(maskWidth + 4f, height + expand * 2f + 4f),
                            cornerRadius = CornerRadius(6f * scale, 6f * scale),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f * scale)
                        )
                    }
                }
            }
        }
    }
}
