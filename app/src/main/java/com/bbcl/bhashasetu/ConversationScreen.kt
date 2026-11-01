// Face-to-face view: the top half is rotated 180° for the person across the table. Each
// half has a talk button and a keyboard for typing instead.
package com.bbcl.bhashasetu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConversationScreen(c: Conversation, micReady: Boolean, onBack: () -> Unit) {
    DisposableEffect(c) { onDispose { c.dispose() } }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(AppColors.bgPrimary).safeDrawingPadding()) {
        Box(Modifier.weight(1f).rotate(180f)) { SidePanel(c, Side.THEM, micReady) }
        Divider(c, onBack)
        Box(Modifier.weight(1f)) { SidePanel(c, Side.ME, micReady) }
    }
}

@Composable
private fun Divider(c: Conversation, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).background(AppColors.bgTertiary), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.textMain) }
        Text(
            "${c.myLanguage.nativeName}  ⇄  ${c.theirLanguage.nativeName}",
            color = AppColors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = c::clear, enabled = c.history.isNotEmpty()) {
            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear", tint = if (c.history.isNotEmpty()) AppColors.textMain else AppColors.textDim)
        }
    }
}

private class BubbleData(
    val key: String,
    val text: String,
    val color: Color,
    val mine: Boolean,
    val subtext: String? = null,
    val faded: Boolean = false,
    val rtl: Boolean = false,
    val subRtl: Boolean = false,
    val onTap: (() -> Unit)? = null,
)

@Composable
private fun SidePanel(c: Conversation, side: Side, micReady: Boolean) {
    val color = if (side == Side.ME) AppColors.me else AppColors.them
    val lang = c.languageOf(side)
    val otherLang = c.languageOf(c.other(side))
    val s = lang.strings
    val bubbles = mutableListOf<BubbleData>()

    // Live turn first: the list is reversed, so index 0 sits just above the button.
    val active = c.activeSide
    if (active == side && c.phase != Phase.SPEAKING) {
        bubbles += BubbleData(
            "live",
            c.partial.ifEmpty { if (c.phase == Phase.TRANSLATING) s.translating else s.listening },
            color, mine = true, faded = c.partial.isEmpty(), rtl = lang.rtl,
        )
    } else if (active != null && active != side) {
        when (c.phase) {
            Phase.LISTENING -> s.listening
            Phase.TRANSLATING -> s.translating
            else -> null
        }?.let { bubbles += BubbleData("status", it, AppColors.textDim, mine = false, faded = true) }
    }
    c.notice?.takeIf { it.side == side }?.let {
        bubbles += BubbleData("notice", it.text, if (it.isError) AppColors.rose else AppColors.textDim, mine = true, faded = true)
    }
    c.history.asReversed().forEachIndexed { i, u ->
        val mine = u.side == side
        // Each person reads their own language: what they said, or the translation of the other.
        bubbles += BubbleData(
            "u${c.history.size - i}",
            if (mine) u.original else u.translated,
            if (mine) color else if (side == Side.ME) AppColors.them else AppColors.me,
            mine = mine,
            subtext = if (mine) u.translated else u.original,
            rtl = lang.rtl,
            subRtl = otherLang.rtl,
            onTap = if (mine) null else ({ c.replay(u) }),
        )
    }

    Column(Modifier.fillMaxSize().background(AppColors.bgPrimary).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val maxBubble = maxWidth * 0.85f
            LazyColumn(Modifier.fillMaxSize(), reverseLayout = true) {
                items(bubbles, key = { it.key }) { Bubble(it, maxBubble) }
            }
        }
        Spacer(Modifier.height(8.dp))
        InputBar(c, side, color, voice = micReady && lang.voiceInput)
    }
}

/** The talk button with a keyboard toggle, or a text field (always, for languages without voice input). */
@Composable
private fun InputBar(c: Conversation, side: Side, color: Color, voice: Boolean) {
    var typing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val lang = c.languageOf(side)
    val busy = c.phase == Phase.TRANSLATING || c.phase == Phase.LISTENING

    fun send() {
        if (text.isBlank()) return
        c.send(side, text)
        text = ""
    }

    if (voice && !typing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TalkButton(c, side, color, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = { typing = true }, enabled = !busy, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Keyboard, contentDescription = "Type", modifier = Modifier.size(26.dp))
            }
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            minLines = 1,
            maxLines = 4,
            textStyle = TextStyle(fontSize = 18.sp, color = AppColors.textMain, textDirection = if (lang.rtl) TextDirection.Rtl else TextDirection.Ltr),
            placeholder = { Text("${lang.nativeName} · ${lang.strings.typeHere}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = AppColors.bgSecondary,
                focusedContainerColor = AppColors.bgSecondary,
                unfocusedBorderColor = color.copy(alpha = 0.5f),
                focusedBorderColor = color,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = ::send,
            enabled = !busy,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = AppColors.bgPrimary),
            modifier = Modifier.size(52.dp),
        ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Translate", modifier = Modifier.size(26.dp)) }
        if (voice) {
            IconButton(onClick = { typing = false }) { Icon(Icons.Filled.Mic, contentDescription = "Speak", tint = AppColors.textMain) }
        }
    }
}

@Composable
private fun TalkButton(c: Conversation, side: Side, color: Color, modifier: Modifier) {
    val lang = c.languageOf(side)
    val s = lang.strings
    val listeningHere = c.phase == Phase.LISTENING && c.activeSide == side
    val busy = c.phase == Phase.TRANSLATING || (c.phase == Phase.LISTENING && !listeningHere)
    val label = when {
        listeningHere -> s.listening
        c.phase == Phase.SPEAKING && c.activeSide != side -> s.speaking
        else -> s.tapToSpeak
    }
    Button(
        onClick = { c.talk(side) },
        enabled = !busy,
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (listeningHere) AppColors.rose else color, contentColor = AppColors.bgPrimary),
        modifier = modifier.height(64.dp),
    ) {
        Icon(if (listeningHere) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text("${lang.nativeName} · $label", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Bubble(b: BubbleData, maxWidth: androidx.compose.ui.unit.Dp) {
    fun dir(rtl: Boolean) = if (rtl) TextDirection.Rtl else TextDirection.Ltr
    Box(Modifier.fillMaxWidth(), contentAlignment = if (b.mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .padding(vertical = 4.dp)
                .widthIn(max = maxWidth)
                .clip(RoundedCornerShape(16.dp))
                .background(b.color.copy(alpha = if (b.faded) 0.08f else 0.16f))
                .border(1.dp, b.color.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .then(if (b.onTap != null) Modifier.clickable(onClick = b.onTap) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                b.text,
                style = TextStyle(textDirection = dir(b.rtl)),
                color = if (b.faded) AppColors.textMuted else AppColors.textMain,
                // The other person's words are the ones to read, so they're bigger.
                fontSize = if (b.mine) 17.sp else 22.sp,
                fontWeight = if (b.mine) FontWeight.Normal else FontWeight.SemiBold,
            )
            b.subtext?.let {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                    Text(it, style = TextStyle(textDirection = dir(b.subRtl)), color = AppColors.textDim, fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false))
                    if (b.onTap != null) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play again", tint = AppColors.textDim, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
