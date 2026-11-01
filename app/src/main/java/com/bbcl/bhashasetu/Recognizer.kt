// Speech-to-text with Google's online recognizer on the phone (free, more accurate than the
// offline packs, and needs nothing installed).
package com.bbcl.bhashasetu

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SpeechException(message: String) : Exception(message)

/** Must be used from the main thread, like the platform recognizer it wraps. */
class Recognizer(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var pending: CancellableContinuation<String>? = null
    private var lastText = ""

    val available get() = SpeechRecognizer.isRecognitionAvailable(context)

    private fun engine(): SpeechRecognizer =
        recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }

    private fun finish(text: String) {
        val p = pending ?: return
        pending = null
        if (p.isActive) p.resume(text.trim())
    }

    private fun fail(message: String) {
        val p = pending ?: return
        pending = null
        if (p.isActive) p.resumeWithException(SpeechException(message))
    }

    /**
     * Listens for one utterance. [onPartial] receives live text; returns the final text,
     * or "" if nothing was heard.
     */
    suspend fun listenOnce(lang: Language, onPartial: (String) -> Unit): String = suspendCancellableCoroutine { cont ->
        finish("") // a session still open from before
        pending = cont
        lastText = ""
        Log.d("stt", "listen ${lang.locale}")
        val sr = engine()
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle) {
                val t = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                if (t.isNotBlank()) {
                    lastText = t
                    onPartial(t)
                }
            }

            override fun onResults(results: Bundle) {
                val t = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                finish(if (t.isNullOrBlank()) lastText else t)
            }

            override fun onError(error: Int) {
                Log.d("stt", "error $error")
                when (error) {
                    // Nothing (more) heard, or the session was ended by stop(): keep what we have.
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_CLIENT -> finish(lastText)
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        fail("Voice input is not available for ${lang.label} on this phone. Type instead.")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Microphone permission is needed.")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                        fail("No internet connection. Voice input needs the internet.")
                    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> fail("Voice input is busy. Try again in a moment.")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        // A stuck engine: start afresh next time.
                        recognizer?.destroy()
                        recognizer = null
                        fail("Voice input is busy. Try again in a moment.")
                    }
                    else -> fail("Speech recognition failed (error $error).")
                }
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.locale)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        sr.startListening(intent)
        cont.invokeOnCancellation { recognizer?.cancel() }
    }

    /** Ends listening early; the pending [listenOnce] returns what was heard. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
        finish("")
    }

    fun destroy() {
        cancel()
        recognizer?.destroy()
        recognizer = null
    }
}
