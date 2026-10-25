package com.puretrans.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBaiduAppIdChanged: (String) -> Unit,
    onBaiduSecretKeyChanged: (String) -> Unit,
    onDeepseekApiKeyChanged: (String) -> Unit,
    onBackendUrlChanged: (String) -> Unit,
    onBackendTokenChanged: (String) -> Unit,
    onToggleBaiduSecretVisibility: () -> Unit,
    onToggleDeepseekSecretVisibility: () -> Unit,
    onToggleBackendTokenVisibility: () -> Unit,
    onSaveSettings: () -> Unit,
    onClearAllKeys: () -> Unit,
    onSmartParseClipboard: (String) -> Unit,
    onDismissToast: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onDismissToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("引擎设置与双模路由", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onSaveSettings) {
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                }
            )
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
            // 1. 顶部动态横幅卡片 (ElevatedCard)
            val bannerContainerColor = if (uiState.isDirectMode) {
                if (isDark) Color(0xFF163821) else Color(0xFFE8F5E9)
            } else {
                if (isDark) Color(0xFF132B45) else Color(0xFFE3F2FD)
            }

            val bannerContentColor = if (uiState.isDirectMode) {
                if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
            } else {
                if (isDark) Color(0xFF90CAF9) else Color(0xFF0D47A1)
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = bannerContainerColor)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (uiState.isDirectMode) "🟢 当前处于：本机直连极速模式" else "🔵 当前处于：美国后端免密模式",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = bannerContentColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (uiState.isDirectMode)
                            "请求由手机直接发往国内节点，不经过海外服务器，延迟最低至 30ms ~ 80ms。"
                        else
                            "未填写本地 Key，已自动启用 Google Translate 免密反代通道，零配置开箱即用。",
                        fontSize = 13.sp,
                        color = bannerContentColor.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )
                }
            }

            // 2. 本地 BYOK 直连设置区块
            Text(
                text = "国内直连配置 (BYOK)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = uiState.baiduAppId,
                onValueChange = onBaiduAppIdChanged,
                label = { Text("百度翻译 App ID") },
                placeholder = { Text("选填，用于百度直连") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedTextField(
                value = uiState.baiduSecretKey,
                onValueChange = onBaiduSecretKeyChanged,
                label = { Text("百度翻译 Secret Key") },
                placeholder = { Text("选填，MD5签名私钥") },
                singleLine = true,
                visualTransformation = if (uiState.showBaiduSecret) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleBaiduSecretVisibility) {
                        Icon(
                            imageVector = if (uiState.showBaiduSecret) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "切换显示"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedTextField(
                value = uiState.deepseekApiKey,
                onValueChange = onDeepseekApiKeyChanged,
                label = { Text("DeepSeek API Key") },
                placeholder = { Text("选填，sk-开头的官方 Key") },
                singleLine = true,
                visualTransformation = if (uiState.showDeepseekSecret) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleDeepseekSecretVisibility) {
                        Icon(
                            imageVector = if (uiState.showDeepseekSecret) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "切换显示"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            // 3. 美国反代后端配置区块
            Text(
                text = "美国后端反代配置 (兜底模式)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = uiState.backendUrl,
                onValueChange = onBackendUrlChanged,
                label = { Text("后端反代端点 URL") },
                placeholder = { Text("https://api.yourdomain.com/google-translate") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedTextField(
                value = uiState.backendToken,
                onValueChange = onBackendTokenChanged,
                label = { Text("后端 X-App-Token") },
                placeholder = { Text("MySecretPin888") },
                singleLine = true,
                visualTransformation = if (uiState.showBackendTokenSecret) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleBackendTokenVisibility) {
                        Icon(
                            imageVector = if (uiState.showBackendTokenSecret) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "切换显示"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4. 底部两大快捷操作按钮
            FilledTonalButton(
                onClick = {
                    val clip = clipboardManager.getText()?.text.orEmpty()
                    onSmartParseClipboard(clip)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("📋 从剪贴板智能识别填入")
            }

            OutlinedButton(
                onClick = onClearAllKeys,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("🗑️ 清空所有 Key (恢复免密后端模式)")
            }

            Button(
                onClick = onSaveSettings,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存设置并生效")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
