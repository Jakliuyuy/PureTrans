package com.puretrans.app.ui.image

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puretrans.app.data.model.Language
import com.puretrans.app.ui.main.LanguagePickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageTranslateScreen(
    uiState: ImageTranslateUiState,
    onNavigateBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickImage: () -> Unit,
    onToggleShowOriginal: () -> Unit,
    onSelectSourceLanguage: (Language) -> Unit,
    onSelectTargetLanguage: (Language) -> Unit,
    onSelectBlock: (TextBlockOverlay?) -> Unit,
    onSpeakText: (String, String) -> Unit,
    onSaveImage: () -> Unit,
    onShareImage: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }

    if (showSourcePicker) {
        LanguagePickerDialog(
            title = "选择源语言",
            languages = Language.supportedSourceLanguages,
            selectedLanguage = uiState.sourceLanguage,
            onSelect = {
                onSelectSourceLanguage(it)
                showSourcePicker = false
            },
            onDismiss = { showSourcePicker = false }
        )
    }

    if (showTargetPicker) {
        LanguagePickerDialog(
            title = "选择目标语言",
            languages = Language.supportedTargetLanguages,
            selectedLanguage = uiState.targetLanguage,
            onSelect = {
                onSelectTargetLanguage(it)
                showTargetPicker = false
            },
            onDismiss = { showTargetPicker = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    // 居中语言切换胶囊
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { showSourcePicker = true },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(uiState.sourceLanguageName, fontSize = 12.sp, maxLines = 1)
                        }

                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "翻译到",
                            modifier = Modifier.padding(horizontal = 6.dp).size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FilledTonalButton(
                            onClick = { showTargetPicker = true },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(uiState.targetLanguageName, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.bitmap != null) {
                        IconButton(onClick = onToggleShowOriginal) {
                            Icon(
                                imageVector = if (uiState.showOriginal) Icons.Filled.VisibilityOff else Icons.Outlined.Visibility,
                                tint = if (uiState.showOriginal) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                contentDescription = if (uiState.showOriginal) "查看译文" else "查看原图"
                            )
                        }
                        IconButton(onClick = onSaveImage) {
                            Icon(Icons.Outlined.Download, contentDescription = "保存到相册")
                        }
                        IconButton(onClick = onShareImage) {
                            Icon(Icons.Outlined.Share, contentDescription = "分享图片")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.bitmap != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onTakePhoto) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("重拍")
                        }
                        TextButton(onClick = onPickImage) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("换图")
                        }
                        FilledTonalButton(
                            onClick = onToggleShowOriginal,
                            shape = CircleShape,
                            colors = if (uiState.showOriginal) ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) else ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Icon(
                                imageVector = if (uiState.showOriginal) Icons.Filled.Visibility else Icons.Outlined.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (uiState.showOriginal) "显示译文" else "查看原图")
                        }
                        TextButton(onClick = {
                            val allTrans = uiState.textBlocks.joinToString("\n") { it.translatedText }
                            if (allTrans.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(allTrans))
                                Toast.makeText(context, "已复制全部翻译文字", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("复制")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.bitmap == null) {
                // 空状态：引导拍摄或选图
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(96.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "拍照与图片 1:1 原位覆盖翻译",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "对标谷歌翻译：自动识别文字位置与背景颜色\n译文 1:1 原位覆盖覆盖于原图相应位置",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(36.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onTakePhoto,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("拍摄照片")
                        }
                        FilledTonalButton(
                            onClick = onPickImage,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("相册选图")
                        }
                    }
                }
            } else {
                // 已有图片：绘制 1:1 原位覆盖画布
                ImageOverlayCanvas(
                    bitmap = uiState.bitmap,
                    textBlocks = uiState.textBlocks,
                    showOriginal = uiState.showOriginal,
                    selectedBlock = uiState.selectedBlock,
                    onSelectBlock = onSelectBlock,
                    modifier = Modifier.fillMaxSize()
                )

                // 处理加载进度指示
                if (uiState.isProcessing) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(uiState.processStepText, fontSize = 13.sp)
                        }
                    }
                }

                // 错误提示条
                if (uiState.errorMessage != null && !uiState.isProcessing) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = uiState.errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // 选中文本块详情卡片 (点击文字块后滑出)
                AnimatedVisibility(
                    visible = uiState.selectedBlock != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val block = uiState.selectedBlock
                    if (block != null) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "文字块详情",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = { onSelectBlock(null) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // 原文
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = block.originalText,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        onSpeakText(block.originalText, uiState.sourceLanguage.code)
                                    }) {
                                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "朗读原文", modifier = Modifier.size(20.dp))
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                // 译文
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = block.translatedText,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        onSpeakText(block.translatedText, uiState.targetLanguage.code)
                                    }) {
                                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "朗读译文", modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(block.translatedText))
                                        Toast.makeText(context, "已复制译文", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制译文", modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
