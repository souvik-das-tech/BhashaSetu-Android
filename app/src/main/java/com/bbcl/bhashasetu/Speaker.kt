// Text-to-speech. Uses the phone's own voice for the language when it has one (including
// Google's network voices), and otherwise Google Translate's free online voice.
package com.bbcl.bhashasetu

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class SpeakException(message: String) : Exception(message)

/** Where a language's voice comes from. */
enum class VoiceSource { PHONE, ONLINE, NONE }

class Speaker(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var voices: List<Voice> = emptyList()
    private var player: MediaPlayer? = null

    /** The voice picked for each language, by voice name. */
    private val prefs = context.getSharedPreferences("voices", Context.MODE_PRIVATE)

    /** Result of asking Google's online voice for a language, once per language. */
    private val online = ConcurrentHashMap<Language, Boolean>()

    /** Bumped by [stop], so a speech in progress gives up between pieces. */
    private var generation = 0
    @Volatile private var waiting: CancellableContinuation<Unit>? = null

    /** The utterance [waiting] is for; a late callback from an earlier one must not end it. */
    @Volatile private var waitingId: String? = null

    /** Starts the phone's TTS engine. A cold engine (first launch) can take a while to bind. */
    suspend fun init() {
        withTimeout(30_000) {
            suspendCancellableCoroutine { cont ->
                var engine: TextToSpeech? = null
                engine = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts = engine
                        if (cont.isActive) cont.resume(Unit)
                    } else if (cont.isActive) {
                        cont.resumeWith(Result.failure(SpeakException("the phone's speech engine is unavailable")))
                    }
                }
            }
        }
        tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = wake(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = wake(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = wake(utteranceId)
        })
        loadVoices()
    }

    private fun loadVoices() {
        voices = runCatching { tts?.voices.orEmpty().toList() }.getOrDefault(emptyList())
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
    }

    /** Resumes the speech being waited on; [id] null means whatever it is. */
    private fun wake(id: String? = null) {
        if (id != null && id != waitingId) return
        val w = waiting ?: return
        waiting = null
        if (w.isActive) w.resume(Unit)
    }

    /**
     * The phone's voices for [lang]: exact locale first, then other regions of the same
     * language; within each, voices that work offline before network ones. The order is
     * stable, so "Voice 2" stays the same voice between launches.
     */
    fun phoneVoices(lang: Language): List<Voice> {
        val prefix = lang.locale.substringBefore('-')
        val order = compareBy<Voice>({ it.isNetworkConnectionRequired }, { it.name })
        val tag = { v: Voice -> v.locale.toLanguageTag() }
        return voices.filter { tag(it) == lang.locale }.sortedWith(order) +
            voices.filter { tag(it) != lang.locale && tag(it).substringBefore('-') == prefix }.sortedWith(order)
    }

    /** The voice the user picked for [lang], else the best one. */
    fun phoneVoice(lang: Language): Voice? {
        val list = phoneVoices(lang)
        val chosen = prefs.getString(lang.name, null)
        return list.firstOrNull { it.name == chosen } ?: list.firstOrNull()
    }

    fun choose(lang: Language, voice: Voice) = prefs.edit().putString(lang.name, voice.name).apply()

    /** Plays [text] in [voice] straight away, without waiting; used to compare voices. */
    fun preview(voice: Voice, text: String) {
        stop()
        val engine = tts ?: return
        engine.voice = voice
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview")
    }

    suspend fun voiceFor(lang: Language): VoiceSource {
        if (voices.isEmpty()) loadVoices()
        if (phoneVoice(lang) != null) return VoiceSource.PHONE
        return try {
            if (onlineWorks(lang)) VoiceSource.ONLINE else VoiceSource.NONE
        } catch (_: Exception) {
            VoiceSource.NONE // offline: can't tell yet
        }
    }

    private suspend fun onlineWorks(lang: Language) =
        online[lang] ?: (fetchOnline("ok", lang) != null).also { online[lang] = it }

    /** Google Translate's voice for up to ~200 characters, or null if it has none for [lang]. */
    private suspend fun fetchOnline(text: String, lang: Language): ByteArray? = withContext(Dispatchers.IO) {
        val (type, body) = httpGet(
            "translate.google.com", "/translate_tts",
            mapOf("ie" to "UTF-8", "client" to "tw-ob", "tl" to lang.code, "q" to text),
            timeoutMs = 10_000,
        ) ?: return@withContext null
        if (type?.startsWith("audio/") == true) body else null
    }

    /** Speaks [text]; returns when speech has finished (or [stop] was called). */
    suspend fun speak(text: String, lang: Language) {
        val gen = ++generation
        val voice = phoneVoice(lang)
        val engine = tts
        if (voice != null && engine != null) {
            engine.voice = voice
            suspendCancellableCoroutine { cont ->
                waitingId = "u$gen"
                waiting = cont
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u$gen")
                cont.invokeOnCancellation { engine.stop() }
            }
            return
        }
        if (online[lang] == false) throw SpeakException("No voice for ${lang.label}. The text is shown instead.")
        for (piece in pieces(text)) {
            val audio = try {
                fetchOnline(piece, lang)
            } catch (_: Exception) {
                throw SpeakException("No internet for the online voice.")
            }
            if (gen != generation) return
            if (audio == null) {
                online[lang] = false
                throw SpeakException("No voice for ${lang.label}. The text is shown instead.")
            }
            online[lang] = true
            play(audio)
            if (gen != generation) return
        }
    }

    private suspend fun play(audio: ByteArray) {
        val file = File(context.cacheDir, "voice.mp3")
        withContext(Dispatchers.IO) { file.writeBytes(audio) }
        suspendCancellableCoroutine { cont ->
            waitingId = null
            waiting = cont
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            mp.setOnCompletionListener { release(mp); wake() }
            mp.setOnErrorListener { _, _, _ -> release(mp); wake(); true }
            try {
                mp.setDataSource(file.path)
                mp.setOnPreparedListener { it.start() }
                mp.prepareAsync()
            } catch (_: Exception) {
                release(mp)
                wake()
            }
            cont.invokeOnCancellation { release(mp) }
        }
    }

    private fun release(mp: MediaPlayer) {
        if (player === mp) player = null
        runCatching { mp.release() }
    }

    fun stop() {
        generation++
        tts?.stop()
        player?.let(::release)
        wake()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        /**
         * Splits text into pieces the online voice accepts (≤ 200 characters), at sentence ends,
         * then commas, then spaces.
         */
        fun pieces(text: String, max: Int = 190): List<String> {
            val out = mutableListOf<String>()
            var rest = text.trim()
            val marks = listOf(Regex("[.!?।॥。？！]\\s"), Regex("[,;:،、]\\s"), Regex("\\s"))
            while (rest.length > max) {
                var cut = -1
                for (m in marks) {
                    cut = m.findAll(rest.substring(0, max)).lastOrNull()?.range?.last?.plus(1) ?: -1
                    if (cut > max / 3) break
                    cut = -1
                }
                if (cut <= 0) cut = max
                out += rest.substring(0, cut).trim()
                rest = rest.substring(cut).trim()
            }
            if (rest.isNotEmpty()) out += rest
            return out
        }
    }
}
