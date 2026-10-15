package io.github.rainbowfl4sh.powerampdiscordrp.core.cover

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Logger
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import java.io.Closeable
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakeClock(var now: Long = 1_000_000L, var wall: Long = 1_760_000_000_000L) : Clock {
    override fun nowMs() = now
    override fun wallMs() = wall

    fun advance(ms: Long) {
        now += ms
        wall += ms
    }
}

class RecordingLogger : Logger {
    val lines = CopyOnWriteArrayList<String>()
    override fun log(message: String) {
        lines += message
    }

    fun has(part: String) = lines.any { it.contains(part) }
}

/**
 * MockWebServer with routes by path. The client sends every request for a host ending in ".test" (e.g. the URLs the
 * fake upload hosts answer with: "https://files.test/a.jpg") to the server as plain http, so the resolver's
 * "https only" rules stay active in the tests.
 */
class FakeNet : Closeable {
    val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, (RecordedRequest) -> MockResponse>()
    private val prefixes = CopyOnWriteArrayList<Pair<String, (RecordedRequest) -> MockResponse>>()
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.url.encodedPath
                val handler = routes["${request.method} $path"] ?: routes[path]
                    ?: prefixes.firstOrNull { path.startsWith(it.first) }?.second
                return handler?.invoke(request) ?: status(404, "not found")
            }
        }
        server.start()
    }

    /** true = the phone has no connection: every request fails like a DNS lookup without network (nothing reaches the server). */
    @Volatile
    var offline = false

    val client: OkHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .addInterceptor { chain ->
            val r = chain.request()
            if (offline) throw UnknownHostException("Unable to resolve host \"${r.url.host}\": No address associated with hostname")
            if (r.url.host.endsWith(".test")) {
                val url = r.url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
                chain.proceed(r.newBuilder().url(url).build())
            } else {
                chain.proceed(r)
            }
        }
        .build()

    /** [method] null = any method. */
    fun on(path: String, method: String? = null, handler: (RecordedRequest) -> MockResponse) {
        routes[if (method == null) path else "$method $path"] = handler
    }

    fun onPrefix(prefix: String, handler: (RecordedRequest) -> MockResponse) {
        prefixes += prefix to handler
    }

    fun url(path: String) = server.url(path).toString()

    fun count(path: String) = requests.count { it.url.encodedPath == path }

    fun last(path: String): RecordedRequest = requests.last { it.url.encodedPath == path }

    fun endpoints() = CoverEndpoints(
        x0 = url("/x0/"),
        catbox = url("/catbox/user/api.php"),
        imgur = url("/imgur/3/image"),
        deezer = url("/deezer"),
        itunes = url("/itunes"),
        bandcamp = url("/bandcamp"),
        musicBrainz = url("/mb"),
        coverArtArchive = url("/caa"),
        discogs = url("/discogs"),
        spotifyAccounts = url("/spotify-accounts"),
        spotifyApi = url("/spotify-api"),
    )

    override fun close() = server.close()

    companion object {
        fun status(code: Int, body: String = "") = MockResponse.Builder().code(code).body(body).build()

        fun text(body: String) = status(200, body)

        fun json(body: String) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build()

        fun bytes(data: ByteArray, code: Int = 200, type: String = "image/jpeg") =
            MockResponse.Builder().code(code).addHeader("Content-Type", type).body(Buffer().write(data)).build()
    }
}

object Samples {
    private fun image(vararg head: Int): ByteArray = ByteArray(200) { i -> if (i < head.size) head[i].toByte() else (i % 251).toByte() }
    private fun ascii(s: String): IntArray = s.map { it.code }.toIntArray()

    val JPEG = image(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, *ascii("JFIF"), 0x00, 0x01, 0x01)
    val ENCODED_JPEG = image(0xFF, 0xD8, 0xFF, 0xDB, 0x00, 0x43, 0x00, 0x08, 0x06, 0x06, 0x07, 0x06, 0x05)
    val PNG = image(0x89, *ascii("PNG"), 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, *ascii("IHDR"))
    val GIF = image(*ascii("GIF89a"), 0x01, 0x00, 0x01, 0x00, 0x80, 0x00)
    val BMP = image(*ascii("BM"), 0x36, 0x00, 0x0C, 0x00, 0x00, 0x00, 0x00, 0x00, 0x36, 0x00)
    val WEBP = image(*ascii("RIFF"), 0x24, 0x00, 0x00, 0x00, *ascii("WEBPVP8 "))
    val AVIF = image(0x00, 0x00, 0x00, 0x1C, *ascii("ftypavif"), 0x00, 0x00, 0x00, 0x00)
    val HEIC = image(0x00, 0x00, 0x00, 0x18, *ascii("ftypheic"), 0x00, 0x00, 0x00, 0x00)
    val MP4 = image(0x00, 0x00, 0x00, 0x18, *ascii("ftypisom"), 0x00, 0x00, 0x02, 0x00)
    val HTML = "<!DOCTYPE html><html><body>File not found</body></html>".toByteArray()

    val TRACK = TrackInfo(
        realId = 42,
        title = "One More Time",
        artist = "Daft Punk",
        album = "Discovery",
        path = "/storage/emulated/0/Music/Daft Punk/Discovery/01 One More Time.mp3",
        durationMs = 320_000,
    )

    fun album(n: Int) = TRACK.copy(album = "Album $n", path = "/music/$n.mp3")

    /** Every online source off: only the upload path is used. */
    val UPLOAD_ONLY = Config(
        srcSpotify = false, srcDeezer = false, srcItunes = false, srcBandcamp = false, srcDiscogs = false,
        srcMusicBrainz = false, uploadHost = Config.UPLOAD_X0,
    )

    /** No uploads and every online source off - enable the one(s) under test with copy(). */
    val NO_SOURCES = UPLOAD_ONLY.copy(localCovers = false, uploadHost = Config.UPLOAD_OFF)
}
