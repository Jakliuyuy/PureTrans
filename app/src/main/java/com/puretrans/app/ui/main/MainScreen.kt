package com.puretrans.app.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puretrans.app.data.model.Language

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onInputTextChanged: (String) -> Unit,
    onSwapLanguages: () -> Unit,
    onSelectSourceLanguage: (Language) -> Unit,
    onSelectTargetLanguage: (Language) -> Unit,
    onClearInput: () -> Unit,
    onSpeakSource: () -> Unit,
    onSpeakTarget: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenFullScreen: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCameraOcr: () -> Unit,
    onStartVoiceInput: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    var swapRotation by remember { mutableFloatStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = swapRotation,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swap_rotation"
    )

    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }

    if (showSourcePicker) {
        LanguagePickerDialog(
            title = "选择源语言",
            languages = Language.supportedSourceLanguages,
            selectedLanguage = uiState.sourceLanguage,
            onSelect = onSelectSourceLanguage,
            onDismiss = { showSourcePicker = false }
        )
    }

    if (showTargetPicker) {
        LanguagePickerDialog(
            title = "选择目标语言",
            languages = Language.supportedTargetLanguages,
            selectedLanguage = uiState.targetLanguage,
            onSelect = onSelectTargetLanguage,
            onDismiss = { showTargetPicker = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PureTrans", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        // 当前路由模式微标
                        AssistChip(
                            onClick = onOpenSettings,
                            label = {
                                Text(
                                    text = if (uiState.isDirectMode) "🟢 极速直连" else "🔵 免密谷歌",
                                    fontSize = 11.sp
                                )
                            },
                            shape = CircleShape,
                            modifier = Modifier.height(26.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Outlined.History, contentDescription = "历史与收藏")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        bottomBar = {
            // 底部极简快捷双按钮栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilledTonalButton(
                    onClick = onStartVoiceInput,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Outlined.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("语音输入")
                }
                FilledTonalButton(
                    onClick = onOpenCameraOcr,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("拍照翻译")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 语言选择与互换栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalButton(
                    onClick = { showSourcePicker = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = CircleShape
                ) {
                    Text(uiState.sourceLanguageName, maxLines = 1)
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        swapRotation += 180f
                        onSwapLanguages()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp).rotate(animatedRotation)
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "互换语言")
                }

                FilledTonalButton(
                    onClick = { showTargetPicker = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = CircleShape
                ) {
                    Text(uiState.targetLanguageName, maxLines = 1)
                }
            }

            // 2. 原文输入卡片 (极简紧凑自适应：单行时紧贴一行高度约56dp，多行时自适应平滑向下扩展)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val isMultiLine = uiState.inputText.contains('\n') || uiState.inputText.length > 40
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = if (isMultiLine) Alignment.Top else Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = if (uiState.inputText.isNotEmpty()) 8.dp else 0.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (uiState.inputText.isEmpty()) {
                                Text(
                                    text = "输入文本...",
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    lineHeight = 26.sp
                                )
                            }
                            BasicTextField(
                                value = uiState.inputText,
                                onValueChange = onInputTextChanged,
                                textStyle = TextStyle(
                                    fontSize = if (uiState.inputText.length < 50) 20.sp else 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 26.sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 26.dp, max = 200.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }

                        // 紧凑操作区：单行时直接内联于同一行右侧（约32dp高），多行时靠右上角停靠
                        if (uiState.inputText.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = onSpeakSource,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isSpeakingSource) Icons.AutoMirrored.Filled.VolumeUp
                                        else Icons.AutoMirrored.Outlined.VolumeUp,
                                        tint = if (uiState.isSpeakingSource) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        contentDescription = if (uiState.isSpeakingSource) "停止朗读" else "朗读原文",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onClearInput()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "清空",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 剪贴板快捷粘贴胶囊 (仅当输入为空且剪贴板有内容时浮现)
                    AnimatedVisibility(visible = uiState.inputText.isEmpty() && uiState.clipboardPreview.isNotEmpty()) {
                        SuggestionChip(
                            onClick = { onInputTextChanged(uiState.clipboardPreview) },
                            icon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("粘贴: ${uiState.clipboardPreview.take(18)}...", maxLines = 1) },
                            shape = CircleShape,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // 输入卡片流光加载条
                    if (uiState.isLoading) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 多行时展示字数统计，单行紧凑模式下不占垂直高度
                    AnimatedVisibility(visible = isMultiLine) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "${uiState.inputText.length} / 5000",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // 3. 译文结果展示卡片 (带展开动画)
            AnimatedVisibility(
                visible = uiState.translatedText.isNotEmpty() || uiState.errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // 顶部目标语种与耗时小字提示
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uiState.targetLanguageName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${uiState.activeEngineLabel} · ${uiState.latencyMs}ms",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 译文主体内容
                        Text(
                            text = uiState.errorMessage ?: uiState.translatedText,
                            fontSize = if (uiState.translatedText.length < 50) 24.sp else 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (uiState.errorMessage != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onPrimaryContainer,
                            lineHeight = 32.sp
                        )

                        // 次级拼音/音标展示 (次级透明度)
                        if (!uiState.phonetic.isNullOrEmpty() && uiState.errorMessage == null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = uiState.phonetic,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // 底部四大金刚操作栏：朗读、复制、收藏、全屏放大
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            IconButton(onClick = onSpeakTarget) {
                                Icon(
                                    imageVector = if (uiState.isSpeakingTarget) Icons.AutoMirrored.Filled.VolumeUp
                                    else Icons.AutoMirrored.Outlined.VolumeUp,
                                    tint = if (uiState.isSpeakingTarget) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    contentDescription = if (uiState.isSpeakingTarget) "停止朗读" else "朗读译文"
                                )
                            }
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(uiState.translatedText))
                            }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "复制译文")
                            }
                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    imageVector = if (uiState.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    contentDescription = "收藏"
                                )
                            }
                            IconButton(onClick = onOpenFullScreen) {
                                Icon(Icons.Outlined.Fullscreen, contentDescription = "全屏大字展示")
                            }
                        }
                    }
                }
            }
        }
    }
}
