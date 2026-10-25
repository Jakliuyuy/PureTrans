package com.puretrans.app.ui.image

import android.graphics.Color
import android.graphics.Rect

/**
 * 原图原位覆盖翻译文本块模型 (1:1 In-place Overlay)
 * @param id 文本块唯一 ID
 * @param originalText 识别出的原文
 * @param translatedText 翻译后的译文
 * @param boundingBox 原图上的矩形坐标 (Rect in bitmap pixel coordinates)
 * @param dominantBgColor 该区域提取的背景颜色 (用于 1:1 绘制背景遮罩覆盖原文字)
 * @param textColor 译文字体颜色 (深底白字或浅底黑字)
 */
data class TextBlockOverlay(
    val id: String,
    val originalText: String,
    val translatedText: String,
    val boundingBox: Rect,
    val dominantBgColor: Int = Color.WHITE,
    val textColor: Int = Color.BLACK,
    val lineCount: Int = 1
)

