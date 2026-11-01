// Pick the language pair and see what each language can do on this phone.
package com.bbcl.bhashasetu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(app: AppModel, onStart: () -> Unit) {
    val scope = rememberCoroutineScope()
    Scaffold(
        containerColor = AppColors.bgPrimary,
        topBar = {
            TopAppBar(
                title = { Text("Bhasha Setu") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.bgPrimary, titleContentColor = AppColors.textMain),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
            app.initError?.let { item { Banner(it, AppColors.rose) } }
            item { PairPicker(app) }
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { scope.launch { app.prepareStrings(); onStart() } },
                    enabled = !app.starting,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (app.starting) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.RecordVoiceOver, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Start conversation", fontSize = 16.sp)
                }
                Spacer(Modifier.height(24.dp))
                Text("On this phone", color = AppColors.textMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            items(listOf(app.myLanguage, app.theirLanguage).distinct()) { LanguageTile(app, it) }
            item {
                Spacer(Modifier.height(16.dp))
                Banner(
                    "Needs the internet. ${Language.entries.size} languages, translated directly between " +
                        "any two (Google Translate, with MyMemory as backup) — all free.\n" +
                        "• Voice input: Google speech recognition. Languages without it are typed.\n" +
                        "• Voice output: the phone's voice, or Google's online voice when the phone has none.",
                    AppColors.indigo,
                )
            }
        }
    }
}

@Composable
private fun PairPicker(app: AppModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.bgSecondary),
        border = BorderStroke(1.dp, AppColors.border),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LanguageButton("I speak", AppColors.me, app.myLanguage, app::setMine, Modifier.weight(1f))
            IconButton(onClick = app::swap) { Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap", tint = AppColors.textMain) }
            LanguageButton("They speak", AppColors.them, app.theirLanguage, app::setTheirs, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageButton(label: String, color: Color, value: Language, onChange: (Language) -> Unit, modifier: Modifier) {
    var picking by remember { mutableStateOf(false) }
    Column(modifier.clip(RoundedCornerShape(8.dp)).clickable { picking = true }.padding(vertical = 4.dp)) {
        Text(label, color = color, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value.nativeName,
                color = AppColors.textMain,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = AppColors.textMuted)
        }
        Text(value.label, color = AppColors.textMuted, fontSize = 12.sp)
    }
    if (picking) {
        ModalBottomSheet(
            onDismissRequest = { picking = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppColors.bgSecondary,
        ) {
            LanguageSheet(value) {
                onChange(it)
                picking = false
            }
        }
    }
}

/** Searchable list of all languages. */
@Composable
private fun LanguageSheet(selected: Language, onPick: (Language) -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val matches = Language.entries.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.nativeName.lowercase().contains(q) }
    Column(Modifier.fillMaxHeight(0.85f).imePadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search ${Language.entries.size} languages") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        )
        LazyColumn {
            items(matches) { l ->
                ListItem(
                    headlineContent = { Text(l.nativeName) },
                    supportingContent = { Text(l.label, color = AppColors.textMuted) },
                    trailingContent = when {
                        l == selected -> ({ Icon(Icons.Filled.Check, contentDescription = "Selected", tint = AppColors.emerald) })
                        !l.voiceInput -> ({ Icon(Icons.Filled.Keyboard, contentDescription = "Typed", tint = AppColors.textDim, modifier = Modifier.size(18.dp)) })
                        else -> null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = AppColors.textMain),
                    modifier = Modifier.clickable { onPick(l) },
                )
            }
        }
    }
}

@Composable
private fun LanguageTile(app: AppModel, language: Language) {
    val voice = language.voiceInput && app.micReady
    val source = app.voices[language]
    val voiceCount = if (source == VoiceSource.PHONE) app.speaker.phoneVoices(language).size else 0
    val voiceText = when {
        !app.voices.containsKey(language) || source == null -> "checking voice…"
        source == VoiceSource.PHONE -> if (voiceCount > 1) "phone voice ($voiceCount to choose from)" else "phone voice"
        source == VoiceSource.ONLINE -> "online voice (only one)"
        else -> "no voice (text only)"
    }
    var picking by remember { mutableStateOf(false) }
    if (picking) VoicePicker(app, language) { picking = false }
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.bgSecondary),
        border = BorderStroke(1.dp, AppColors.border),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        ListItem(
            headlineContent = { Text("${language.nativeName}  ·  ${language.label}") },
            supportingContent = {
                Text(
                    "${if (voice) "Speak or type" else "Type only"} · $voiceText",
                    color = if (source == VoiceSource.NONE) AppColors.rose else AppColors.textMuted,
                    fontSize = 12.sp,
                )
            },
            trailingContent = { Icon(if (voice) Icons.Filled.Mic else Icons.Filled.Keyboard, contentDescription = null, tint = AppColors.textMuted) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = AppColors.textMain),
        )
        if (voiceCount > 0) {
            TextButton(onClick = { picking = true }, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Change voice")
            }
        }
    }
}

@Composable
fun Banner(text: String, color: Color) {
    Text(
        text,
        color = AppColors.textMain,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    )
}
