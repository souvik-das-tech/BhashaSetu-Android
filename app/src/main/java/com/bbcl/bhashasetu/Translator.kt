// Text translation with free online engines, no API key. Google translates directly between
// any two languages (no pivot through English) and covers all the Indian languages here.
// Its endpoints are unofficial and rate-limited per network, so they are tried in turn,
// then MyMemory (translated.net) as a last resort.
package com.bbcl.bhashasetu

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TranslationException(message: String) : Exception(message)

private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) BhashaSetuOnline/1.0"
private const val TIMEOUT_MS = 8_000

/** GET [host][path]?[query]; the body on 200, else null. Throws [IOException] when offline. */
internal fun httpGet(host: String, path: String, query: Map<String, String>, timeoutMs: Int = TIMEOUT_MS): Pair<String?, ByteArray>? {
    val q = query.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
    val conn = URL("https://$host$path?$q").openConnection() as HttpURLConnection
    conn.connectTimeout = timeoutMs
    conn.readTimeout = timeoutMs
    conn.setRequestProperty("User-Agent", USER_AGENT)
    try {
        if (conn.responseCode != 200) return null
        return conn.contentType to conn.inputStream.use { it.readBytes() }
    } finally {
        conn.disconnect()
    }
}

/** One online engine. Returns null when it has no answer, so the next one is tried. */
private interface Engine {
    val name: String
    fun translate(text: String, from: Language, to: Language): String?
}

/** The endpoint Chrome's translate extension uses: the full Google model. */
private object GoogleDict : Engine {
    override val name = "Google"
    override fun translate(text: String, from: Language, to: Language): String? {
        val body = httpGet(
            "clients5.google.com", "/translate_a/t",
            mapOf("client" to "dict-chrome-ex", "sl" to from.code, "tl" to to.code, "q" to text),
        )?.second ?: return null
        // ["translation"] for one input; [["translation","detected language"]] with sl=auto.
        val data = JSONArray(String(body))
        if (data.length() == 0) return null
        val first = data.get(0)
        return if (first is JSONArray) first.optString(0) else first as? String
    }
}

/** Google's web-widget endpoint; split into sentences. */
private object GoogleGtx : Engine {
    override val name = "Google (gtx)"
    override fun translate(text: String, from: Language, to: Language): String? {
        val body = httpGet(
            "translate.googleapis.com", "/translate_a/single",
            mapOf("client" to "gtx", "sl" to from.code, "tl" to to.code, "dt" to "t", "q" to text),
        )?.second ?: return null
        val sentences = JSONArray(String(body)).optJSONArray(0) ?: return null
        return buildString {
            for (i in 0 until sentences.length()) {
                val s = sentences.optJSONArray(i) ?: continue
                (s.opt(0) as? String)?.let(::append)
            }
        }
    }
}

/** MyMemory: free without a key (about 5000 characters a day per phone), 500 bytes per request. */
private object MyMemory : Engine {
    override val name = "MyMemory"
    private fun code(l: Language) = l.code.substringBefore('-')

    override fun translate(text: String, from: Language, to: Language): String? {
        if (text.toByteArray().size > 500) return null
        val body = httpGet(
            "api.mymemory.translated.net", "/get",
            mapOf("q" to text, "langpair" to "${code(from)}|${code(to)}"),
        )?.second ?: return null
        val data = JSONObject(String(body))
        if (data.optString("responseStatus") != "200") return null
        val out = data.optJSONObject("responseData")?.optString("translatedText")
        if (out.isNullOrEmpty() || out.uppercase().contains("MYMEMORY WARNING")) return null
        return out
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
            .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    }
}

object Translator {
    private val engines = listOf(GoogleDict, GoogleGtx, MyMemory)

    suspend fun translate(text: String, from: Language, to: Language): String = withContext(Dispatchers.IO) {
        if (from == to || text.isBlank()) return@withContext text
        var offline = false
        for (e in engines) {
            try {
                val out = e.translate(text, from, to)?.trim()
                if (!out.isNullOrEmpty()) {
                    Log.d("translate", "${e.name} ${from.code}→${to.code}")
                    return@withContext out
                }
            } catch (_: IOException) {
                offline = true
            } catch (err: Exception) {
                Log.d("translate", "${e.name} failed: $err")
            }
        }
        throw TranslationException(
            if (offline) "No internet connection. Translation needs the internet."
            else "Translation is busy right now. Please try again in a moment.",
        )
    }
}
