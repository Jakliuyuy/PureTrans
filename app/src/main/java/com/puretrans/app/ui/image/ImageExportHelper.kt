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

/**
 * 导出与分享原位覆盖翻译图片的工具类
 */
object ImageExportHelper {

    /**
     * 将原图与 1:1 原位覆盖译文合成为一张完整的新 Bitmap
     * 复用 ImageOverlayRenderer，保证导出效果与界面预览 100% 毫无偏差
     */
    fun createCompositeBitmap(original: Bitmap, textBlocks: List<TextBlockOverlay>): Bitmap {
        val resultBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        for (block in textBlocks) {
            val box = block.boundingBox
            if (box.width() <= 2 || box.height() <= 2) continue

            ImageOverlayRenderer.drawTextBlock(
                canvas = canvas,
                block = block,
                left = box.left.toFloat(),
                top = box.top.toFloat(),
                width = box.width().toFloat(),
                height = box.height().toFloat(),
                scale = 1.0f
            )
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
