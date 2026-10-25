package com.puretrans.app.ui.fullscreen

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puretrans.app.ui.theme.PureTransTheme
import java.util.Locale

class FullScreenActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }

        setContent {
            PureTransTheme {
                FullScreenContent(
                    text = text,
                    onClose = { finish() },
                    onSpeak = {
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fullscreen_tts")
                    },
                    onToggleOrientation = {
                        requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }

    companion object {
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_LANG = "extra_lang"

        fun start(context: Context, text: String, lang: String = "") {
            val intent = Intent(context, FullScreenActivity::class.java).apply {
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_LANG, lang)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun FullScreenContent(
    text: String,
    onClose: () -> Unit,
    onSpeak: () -> Unit,
    onToggleOrientation: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
        ) {
            // 顶部浮动控制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(32.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = onToggleOrientation, shape = CircleShape) {
                        Icon(Icons.Outlined.ScreenRotation, contentDescription = "旋转屏幕")
                    }
                    FilledTonalIconButton(onClick = onSpeak, shape = CircleShape) {
                        Icon(Icons.Outlined.VolumeUp, contentDescription = "朗读")
                    }
                }
            }

            // 中间大字展示
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 56.dp)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    fontSize = if (text.length < 30) 42.sp else 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = if (text.length < 30) 54.sp else 40.sp
                )
            }
        }
    }
}
