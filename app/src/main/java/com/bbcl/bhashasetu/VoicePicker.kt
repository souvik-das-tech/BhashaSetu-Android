// Lets the user hear each of the phone's voices for a language and keep the one they like.
package com.bbcl.bhashasetu

import android.speech.tts.Voice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicePicker(app: AppModel, lang: Language, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val voices = remember(lang) { app.speaker.phoneVoices(lang) }
    var chosen by remember(lang) { mutableStateOf(app.speaker.phoneVoice(lang)?.name) }
    DisposableEffect(Unit) { onDispose { app.speaker.stop() } }

    fun play(v: Voice) = scope.launch { app.speaker.preview(v, app.sampleText(lang)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.bgSecondary,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("${lang.nativeName} voice", color = AppColors.textMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Tap ▶ to hear a voice, then pick the one you like. Your choice is remembered.",
                color = AppColors.textMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                itemsIndexed(voices, key = { _, v -> v.name }) { i, v ->
                    VoiceRow(
                        index = i + 1,
                        voice = v,
                        lang = lang,
                        selected = v.name == chosen,
                        onSelect = {
                            chosen = v.name
                            app.speaker.choose(lang, v)
                            play(v)
                        },
                        onPlay = { play(v) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VoiceRow(index: Int, voice: Voice, lang: Language, selected: Boolean, onSelect: () -> Unit, onPlay: () -> Unit) {
    // Another region's voice of the same language says which region, e.g. "Bangladesh".
    val region = voice.locale.takeIf { it.toLanguageTag() != lang.locale }?.getDisplayCountry(Locale.ENGLISH)?.ifBlank { null }
    val details = listOfNotNull(
        if (voice.isNetworkConnectionRequired) "Needs internet" else "Works offline",
        region,
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = AppColors.emerald, unselectedColor = AppColors.textDim),
        )
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text("Voice $index", color = AppColors.textMain, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            Text(details, color = AppColors.textMuted, fontSize = 12.sp)
        }
        FilledTonalIconButton(onClick = onPlay, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play Voice $index")
        }
    }
}
