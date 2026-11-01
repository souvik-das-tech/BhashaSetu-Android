// The app's state: the language pair and what each language can do on this phone (setup), and
// one conversation, run one turn at a time: listen (or typed text) → translate → speak. The mic
// is never open while speaking.
package com.bbcl.bhashasetu

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Which side of the phone spoke. */
enum class Side { ME, THEM }

/** One finished turn of the conversation. */
data class Utterance(val side: Side, val from: Language, val to: Language, val original: String, val translated: String)

class AppModel(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("pair", Context.MODE_PRIVATE)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val recognizer = Recognizer(app)
    val speaker = Speaker(app)

    var myLanguage by mutableStateOf(Language.BANGLA)
        private set
    var theirLanguage by mutableStateOf(Language.TAMIL)
        private set

    /** Voice of each language checked so far (null while checking). */
    val voices = mutableStateMapOf<Language, VoiceSource?>()

    var micReady by mutableStateOf(false)
        private set
    var speakerReady by mutableStateOf(false)
        private set
    private var speakerError by mutableStateOf<String?>(null)
    private var micError by mutableStateOf<String?>(null)
    var starting by mutableStateOf(false)
        private set

    val initError get() = listOfNotNull(speakerError, micError).joinToString("\n").ifEmpty { null }

    init {
        val mine = Language.entries.firstOrNull { it.name == prefs.getString("my_language", null) }
        val theirs = Language.entries.firstOrNull { it.name == prefs.getString("their_language", null) }
        if (mine != null && theirs != null && mine != theirs) {
            myLanguage = mine
            theirLanguage = theirs
        }
        // The engines start independently, so a slow TTS cold start never blocks the mic.
        scope.launch {
            try {
                speaker.init()
                speakerReady = true
                speakerError = null
            } catch (e: Exception) {
                speakerError = "Text-to-speech did not start: ${e.message}"
            }
            checkVoices()
        }
    }

    /** Called once the mic permission dialog has been answered. */
    fun onMicPermission() {
        val granted = app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        micReady = granted && recognizer.available
        micError = when {
            micReady -> null
            !recognizer.available -> "Voice input is not available on this phone. You can still type."
            else -> "Mic permission was denied. You can still type."
        }
    }

    private fun checkVoices() {
        if (!speakerReady) return
        for (l in setOf(myLanguage, theirLanguage)) {
            if (voices.containsKey(l)) continue
            voices[l] = null
            scope.launch {
                voices[l] = runCatching { speaker.voiceFor(l) }.getOrDefault(VoiceSource.NONE)
            }
        }
    }

    fun setMine(l: Language) {
        if (l == theirLanguage) theirLanguage = myLanguage
        myLanguage = l
        changed()
    }

    fun setTheirs(l: Language) {
        if (l == myLanguage) myLanguage = theirLanguage
        theirLanguage = l
        changed()
    }

    fun swap() {
        val mine = myLanguage
        myLanguage = theirLanguage
        theirLanguage = mine
        changed()
    }

    private fun changed() {
        prefs.edit().putString("my_language", myLanguage.name).putString("their_language", theirLanguage.name).apply()
        checkVoices()
    }

    private val samples = mutableMapOf<Language, String>()

    /** A short sentence in [lang] for previewing voices; its name when offline. */
    suspend fun sampleText(lang: Language): String {
        samples[lang]?.let { return it }
        val english = "Hello! This is how I will sound."
        val text = if (lang == Language.ENGLISH) english else withTimeoutOrNull(5_000) {
            runCatching { Translator.translate(english, Language.ENGLISH, lang) }.getOrNull()
        }
        return text?.also { samples[lang] = it } ?: lang.nativeName
    }

    /**
     * Translates the UI strings into the chosen languages that have none built in. Gives up
     * quietly (English stays) when offline or slow.
     */
    suspend fun prepareStrings() {
        starting = true
        val english = localStrings.getValue(Language.ENGLISH)
        setOf(myLanguage, theirLanguage)
            .filter { it !in localStrings && it !in translatedStrings }
            .map { l ->
                scope.async {
                    withTimeoutOrNull(6_000) {
                        runCatching { Translator.translate(english.all.joinToString("\n"), Language.ENGLISH, l) }.getOrNull()
                    }?.let { t -> LocalStrings.fromList(t.split("\n").map(String::trim))?.let { translatedStrings[l] = it } }
                }
            }
            .awaitAll()
        starting = false
    }
}

enum class Phase { IDLE, LISTENING, TRANSLATING, SPEAKING }

/** A short message shown on one side's half of the screen. */
data class Notice(val side: Side, val text: String, val isError: Boolean = false)

class Conversation(private val app: AppModel, val myLanguage: Language, val theirLanguage: Language) {
    var phase by mutableStateOf(Phase.IDLE)
        private set

    /** Who is talking in the current turn (null when idle). */
    var activeSide by mutableStateOf<Side?>(null)
        private set

    /** Live recognition text for the current turn. */
    var partial by mutableStateOf("")
        private set

    val history = mutableStateListOf<Utterance>()
    var notice by mutableStateOf<Notice?>(null)
        private set
    private var disposed = false

    /** Bumped per turn, so a turn interrupted by a newer one doesn't reset the newer one's state. */
    private var turn = 0

    fun languageOf(side: Side) = if (side == Side.ME) myLanguage else theirLanguage
    fun other(side: Side) = if (side == Side.ME) Side.THEM else Side.ME

    /** Talk button for [side]. Starts a turn, or ends listening early if that side is already talking. */
    fun talk(side: Side) {
        if (phase == Phase.LISTENING && activeSide == side) {
            app.recognizer.stop()
            return
        }
        if (!interrupt()) return
        app.scope.launch { runTurn(side, null) }
    }

    /** Translates and speaks text that [side] typed. */
    fun send(side: Side, text: String) {
        if (text.isBlank() || !interrupt()) return
        app.scope.launch { runTurn(side, text.trim()) }
    }

    /** Stops the phone talking so a new turn can start. False while another turn is busy. */
    private fun interrupt(): Boolean {
        if (phase == Phase.SPEAKING) app.speaker.stop()
        return phase == Phase.IDLE || phase == Phase.SPEAKING
    }

    private suspend fun runTurn(side: Side, typed: String?) {
        val from = languageOf(side)
        val to = languageOf(other(side))
        val t = ++turn
        activeSide = side
        partial = ""
        notice = null
        try {
            val text = if (typed != null) {
                typed
            } else {
                phase = Phase.LISTENING
                val heard = app.recognizer.listenOnce(from) { partial = it }
                if (disposed || t != turn) return
                if (heard.isEmpty()) {
                    notice = Notice(side, from.strings.noSpeech)
                    return
                }
                heard
            }
            partial = text
            phase = Phase.TRANSLATING
            val translated = Translator.translate(text, from, to)
            if (disposed || t != turn) return
            history += Utterance(side, from, to, text, translated)
            partial = ""
            phase = Phase.SPEAKING
            app.speaker.speak(translated, to)
        } catch (e: Exception) {
            if (t == turn) notice = Notice(side, e.message ?: e.toString(), isError = true)
        } finally {
            if (!disposed && t == turn) {
                activeSide = null
                partial = ""
                phase = Phase.IDLE
            }
        }
    }

    /** Speaks a past translation again. */
    fun replay(u: Utterance) {
        if (phase != Phase.IDLE) return
        val t = ++turn
        activeSide = u.side
        phase = Phase.SPEAKING
        app.scope.launch {
            try {
                app.speaker.speak(u.translated, u.to)
            } catch (e: Exception) {
                if (t == turn) notice = Notice(u.side, e.message ?: e.toString(), isError = true)
            } finally {
                if (!disposed && t == turn) {
                    activeSide = null
                    phase = Phase.IDLE
                }
            }
        }
    }

    fun clear() {
        history.clear()
        notice = null
    }

    fun dispose() {
        disposed = true
        app.recognizer.cancel()
        app.speaker.stop()
    }
}
