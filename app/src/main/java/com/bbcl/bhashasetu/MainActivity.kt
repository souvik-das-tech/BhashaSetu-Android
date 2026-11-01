package com.bbcl.bhashasetu

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color as ComposeColor

/** Colours: dark background, one colour per side of the conversation. */
object AppColors {
    val bgPrimary = ComposeColor(0xFF070B14)
    val bgSecondary = ComposeColor(0xFF0D1527)
    val bgTertiary = ComposeColor(0xFF131F38)
    val border = ComposeColor(0x336366F1)
    val textMain = ComposeColor(0xFFF1F5F9)
    val textMuted = ComposeColor(0xFF94A3B8)
    val textDim = ComposeColor(0xFF64748B)
    val me = ComposeColor(0xFF06B6D4)
    val them = ComposeColor(0xFFF59E0B)
    val indigo = ComposeColor(0xFF6366F1)
    val emerald = ComposeColor(0xFF10B981)
    val rose = ComposeColor(0xFFF43F5E)
}

private val Scheme = darkColorScheme(
    primary = ComposeColor(0xFFBDC2FF),
    onPrimary = ComposeColor(0xFF1A237E),
    primaryContainer = AppColors.indigo,
    secondaryContainer = ComposeColor(0xFF2E3354),
    onSecondaryContainer = ComposeColor(0xFFDFE0FF),
    background = AppColors.bgPrimary,
    surface = AppColors.bgSecondary,
    onSurface = AppColors.textMain,
    onSurfaceVariant = AppColors.textMuted,
)

class MainActivity : ComponentActivity() {
    private lateinit var app: AppModel

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        app = AppModel(this)
        setContent {
            MaterialTheme(colorScheme = Scheme) {
                val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { app.onMicPermission() }
                LaunchedEffect(Unit) { mic.launch(Manifest.permission.RECORD_AUDIO) }
                var conversation by remember { mutableStateOf<Conversation?>(null) }
                val c = conversation
                if (c == null) {
                    SetupScreen(app, onStart = { conversation = Conversation(app, app.myLanguage, app.theirLanguage) })
                } else {
                    ConversationScreen(c, app.micReady, onBack = { conversation = null })
                }
            }
        }
    }

    override fun onDestroy() {
        app.recognizer.destroy()
        app.speaker.shutdown()
        super.onDestroy()
    }
}
