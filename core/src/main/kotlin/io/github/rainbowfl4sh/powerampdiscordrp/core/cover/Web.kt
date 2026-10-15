package io.github.rainbowfl4sh.powerampdiscordrp.core.cover

import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Answer of [Web.send]. status 0 = no usable answer, like the AIMP plugin's web::Response. [networkError]: the host never
 * answered (offline, DNS, timeout, connection reset) - our own network trouble, which says nothing about the host.
 */
internal class Reply(
    val status: Int,
    val body: String,
    val headers: Headers = Headers.headersOf(),
    val networkError: Boolean = false,
)

/** Small blocking HTTP layer for the resolver (port of web.cpp on top of OkHttp). Never throws. */
internal class Web(private val http: OkHttpClient, private val log: Logger) {
    // a redirect would turn an upload into an empty GET (catbox.moe then answers with nothing) - report it instead
    private val noRedirects: OkHttpClient by lazy { http.newBuilder().followRedirects(false).followSslRedirects(false).build() }

    private val networkErrorCount = AtomicLong()

    /**
     * Requests so far that got no answer at all (see [Reply.networkError], [Probe.UNKNOWN]). Counted over all threads:
     * another lookup's trouble at the same time can only make a failed lookup retry sooner.
     */
    val networkErrors: Long get() = networkErrorCount.get()

    /**
     * Builds a request with [build] and runs it with a whole-call timeout; [what] names the service in log lines.
     * Building is part of the call: a value OkHttp refuses (e.g. a pasted key with an invisible character in a header)
     * only fails this request.
     */
    fun send(
        what: String,
        timeoutSec: Long = LOOKUP_TIMEOUT_SEC,
        maxBytes: Long = MAX_ANSWER_BYTES,
        build: Request.Builder.() -> Unit,
    ): Reply {
        val request = try {
            Request.Builder().apply(build).build()
        } catch (e: IllegalArgumentException) {   // OkHttp leaves the values of secret headers out of the message
            log.log("$what: request cannot be built (${e.message ?: "no details"})")
            return Reply(0, "")
        }
        val client = if (request.method == "GET") http else noRedirects
        return try {
            val call = client.newCall(request)
            call.timeout().timeout(timeoutSec, TimeUnit.SECONDS)
            call.execute().use { response ->
                val body = readLimited(response.body, maxBytes)
                if (body == null) {
                    log.log("$what: answer too big (more than ${maxBytes / 1024} KB) - ignored")
                    Reply(0, "")
                } else {
                    Reply(response.code, body, response.headers)
                }
            }
        } catch (e: IOException) {   // no (complete) answer: offline, DNS, timeout, connection reset, interrupted call
            networkErrorCount.incrementAndGet()
            log.log("$what: request failed (${e.javaClass.simpleName}: ${e.message ?: "no details"})")
            Reply(0, "", networkError = true)
        } catch (e: Exception) {
            log.log("$what: request failed (${e.javaClass.simpleName}: ${e.message ?: "no details"})")
            Reply(0, "")
        }
    }

    /**
     * Checks the first [count] bytes behind [url] with [isPicture]. A plain GET with a Range header: x0.at answers HEAD
     * requests with 404, and only the start of the picture is needed.
     * [Probe.BROKEN]: the host answered, but not with a picture (status other than 200 / 206, other bytes), or the URL is
     * malformed. [Probe.UNKNOWN]: no answer (offline, DNS, timeout, connection reset) - says nothing about the picture.
     */
    fun probe(url: String, count: Int, timeoutSec: Long = LOOKUP_TIMEOUT_SEC, isPicture: (ByteArray) -> Boolean): Probe {
        val request = try {
            Request.Builder().url(url).header("Range", "bytes=0-${count - 1}").get().build()
        } catch (e: IllegalArgumentException) {
            log.log("Image check: not a URL ($url)")
            return Probe.BROKEN
        }
        return try {
            val call = http.newCall(request)
            call.timeout().timeout(timeoutSec, TimeUnit.SECONDS)
            call.execute().use { response ->
                if (response.code != 200 && response.code != 206) {
                    Probe.BROKEN
                } else {
                    // a server that ignores the Range header sends the whole picture: read only what is needed
                    val source = response.body.source()
                    source.request(count.toLong())
                    val head = source.buffer.readByteArray(minOf(count.toLong(), source.buffer.size))
                    if (isPicture(head)) Probe.OK else Probe.BROKEN
                }
            }
        } catch (e: Exception) {   // IOException, interrupted call, ...: we could not check it
            networkErrorCount.incrementAndGet()
            log.log("Image check failed for $url (${e.javaClass.simpleName}: ${e.message ?: "no details"})")
            Probe.UNKNOWN
        }
    }

    private fun readLimited(body: ResponseBody, maxBytes: Long): String? {
        val source = body.source()
        val buffer = Buffer()
        while (source.read(buffer, 8192) != -1L) {
            if (buffer.size > maxBytes) return null
        }
        return buffer.readUtf8()
    }

    companion object {
        const val LOOKUP_TIMEOUT_SEC = 10L
        const val UPLOAD_TIMEOUT_SEC = 20L
        const val MAX_ANSWER_BYTES = 4L shl 20

        /** MusicBrainz asks every client for a meaningful User-Agent (anonymous clients get throttled or blocked). */
        const val USER_AGENT = "PowerampDiscordRP/1.0 ( https://github.com/RainBowFl4sh/Poweramp-Discord-RP )"
    }
}

// ---------------------------------------------------------------- JSON helpers (answers of the online services)

internal val LENIENT_JSON = Json { isLenient = true; ignoreUnknownKeys = true }

/** null if [text] is not JSON. */
internal fun parseJson(text: String): JsonElement? = try {
    LENIENT_JSON.parseToJsonElement(text)
} catch (_: Exception) {
    null
}

/** Walks objects (String keys) and arrays (Int indexes): `json.at("results", 0, "artistName")`; null if a step is missing. */
internal fun JsonElement?.at(vararg path: Any): JsonElement? = path.fold(this) { element, step ->
    when (step) {
        is String -> (element as? JsonObject)?.get(step)
        is Int -> (element as? JsonArray)?.getOrNull(step)
        else -> null
    }
}

/** String (or number) value, "" for anything else. */
internal val JsonElement?.text: String
    get() = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content ?: ""

internal val JsonElement?.array: List<JsonElement>
    get() = (this as? JsonArray) ?: emptyList()
