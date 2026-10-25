package com.puretrans.app.ui.history

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puretrans.app.PureTransApp
import com.puretrans.app.data.model.HistoryItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val app = context.applicationContext as PureTransApp
    val historyDb = app.historyDatabase

    val allItems by historyDb.itemsFlow.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 历史, 1: 收藏
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems = remember(allItems, selectedTab, searchQuery) {
        allItems.filter { item ->
            if (selectedTab == 1 && !item.isFavorite) return@filter false
            if (searchQuery.isNotBlank()) {
                item.sourceText.contains(searchQuery, ignoreCase = true) ||
                        item.translatedText.contains(searchQuery, ignoreCase = true)
            } else {
                true
            }
        }
    }

    val dateFormatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "翻译历史" else "我的收藏", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (selectedTab == 0 && allItems.isNotEmpty()) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                historyDb.clearHistory(onlyNonFavorites = true)
                                Toast.makeText(context, "已清理非收藏历史", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "清空历史")
                        }
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
        ) {
            // Tab 切换栏
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("全部历史") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("我的收藏 ⭐") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索文本...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清除搜索")
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedTab == 0) "暂无翻译历史记录" else "暂无收藏记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        HistoryCard(
                            item = item,
                            dateStr = dateFormatter.format(Date(item.timestamp)),
                            onCopy = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(item.translatedText))
                                Toast.makeText(context, "已复制译文", Toast.LENGTH_SHORT).show()
                            },
                            onSpeak = { onSpeak(item.translatedText) },
                            onToggleFavorite = {
                                coroutineScope.launch {
                                    historyDb.toggleFavorite(item.sourceText, item.translatedText)
                                }
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    historyDb.deleteItem(item.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    item: HistoryItem,
    dateStr: String,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.sourceLangCode} → ${item.targetLangCode} · ${item.engineLabel}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = dateStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.sourceText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.translatedText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSpeak, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.VolumeUp, contentDescription = "朗读", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "复制", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "收藏",
                        tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
