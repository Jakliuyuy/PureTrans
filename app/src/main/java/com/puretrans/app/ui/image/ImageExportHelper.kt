package com.puretrans.app.ui.image

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * 导出与分享原位覆盖翻译图片的工具类
 */
object ImageExportHelper {

    /**
     * 将原图与 1:1 原位覆盖译文合成为一张完整的新 Bitmap
     */
    fun createCompositeBitmap(original: Bitmap, textBlocks: List<TextBlockOverlay>): Bitmap {
        val resultBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val bgPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val textPaint = Paint().apply {
            isAntiAlias = true
            isSubpixelText = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        for (block in textBlocks) {
            val box = block.boundingBox
            if (box.width() <= 2 || box.height() <= 2) continue

            // 绘制遮罩背景
            bgPaint.color = block.dominantBgColor
            bgPaint.alpha = 245
            val rectF = RectF(box)
            canvas.drawRoundRect(rectF, 6f, 6f, bgPaint)

            val text = block.translatedText
            if (text.isNotBlank()) {
                textPaint.color = block.textColor
                var fontSize = box.height() * 0.68f
                textPaint.textSize = fontSize

                val textWidth = textPaint.measureText(text)
                val maxAvailableWidth = box.width() * 0.94f
                if (textWidth > maxAvailableWidth && textWidth > 0) {
                    val shrinkFactor = maxAvailableWidth / textWidth
                    fontSize = max(10f, fontSize * shrinkFactor)
                    textPaint.textSize = fontSize
                }

                val fontMetrics = textPaint.fontMetrics
                val baselineY = box.top + (box.height() - fontMetrics.bottom + fontMetrics.top) / 2f - fontMetrics.top
                val centerX = box.left + box.width() / 2f

                canvas.drawText(text, centerX, baselineY, textPaint)
            }
        }
        return resultBitmap
    }

    /**
     * 保存合成后的图片到相册
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
        val filename = "PureTrans_${System.currentTimeMillis()}.png"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PureTrans")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
                uri
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val pureTransDir = File(imagesDir, "PureTrans").apply { if (!exists()) mkdirs() }
                val imageFile = File(pureTransDir, filename)
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Uri.fromFile(imageFile)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 系统分享合成图片
     */
    fun shareBitmap(context: Context, bitmap: Bitmap) {
        try {
            val cacheFile = File(context.cacheDir, "shared_translated_image.png")
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享原位翻译图片"))
        } catch (_: Exception) {
        }
    }
}
