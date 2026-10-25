package com.puretrans.app.ui.quickpopup

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puretrans.app.PureTransApp
import com.puretrans.app.data.model.Language
import com.puretrans.app.ui.theme.PureTransTheme
import kotlinx.coroutines.launch
import java.util.Locale

class QuickTranslateActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: ""

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }

        val app = application as PureTransApp
        val router = app.router

        setContent {
            PureTransTheme {
                var translatedText by remember { mutableStateOf("") }
                var isLoading by remember { mutableStateOf(true) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                val clipboardManager = LocalClipboardManager.current

                LaunchedEffect(selectedText) {
                    if (selectedText.isBlank()) {
                        errorMessage = "未获取到选中文本"
                        isLoading = false
                        return@LaunchedEffect
                    }
                    val result = router.translate(
                        text = selectedText,
                        sourceLang = Language.AUTO,
                        targetLang = Language.ZH
                    )
                    isLoading = false
                    if (result.isSuccess) {
                        translatedText = result.translatedText
                    } else {
                        errorMessage = result.errorMessage ?: "翻译失败"
                    }
                }

                // 悬浮窗背景半透明容器，点击空白处关闭
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable { finish() },
                    contentAlignment = Alignment.Center
                ) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clickable(enabled = false) {}, // 阻止向背景传递点击
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 顶栏微标与关闭按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PureTrans 划词翻译",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { finish() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "关闭",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // 上半部分：选中的原文（限 2 行省略）
                            Text(
                                text = selectedText.ifEmpty { "（无文本）" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // 下半部分：实时显示译文或加载条
                            if (isLoading) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(3.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = errorMessage ?: translatedText,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (errorMessage != null) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 26.sp
                                )
                            }

                            // 右下角：朗读 与 复制并关闭 两个小图标按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (translatedText.isNotEmpty()) {
                                            tts?.speak(translatedText, TextToSpeech.QUEUE_FLUSH, null, "quick_tts")
                                        }
                                    },
                                    enabled = translatedText.isNotEmpty()
                                ) {
                                    Icon(Icons.Outlined.VolumeUp, contentDescription = "朗读")
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                FilledTonalButton(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(translatedText))
                                        Toast.makeText(this@QuickTranslateActivity, "已复制并关闭", Toast.LENGTH_SHORT).show()
                                        finish()
                                    },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    enabled = translatedText.isNotEmpty()
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("复制并关闭", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}
