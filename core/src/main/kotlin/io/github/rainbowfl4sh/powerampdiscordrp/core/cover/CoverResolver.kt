package io.github.rainbowfl4sh.powerampdiscordrp.core.cover

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Logger
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest

/**
 * Port of the AIMP plugin's CoverResolver (cover.cpp): finds a publicly reachable cover URL without depending on a
 * streaming service.
 *  a) local cover (what Poweramp shows, via [local]) -> JPEG via [encoder] -> upload host (x0.at / catbox.moe / Imgur,
 *     the chosen one first, others stand in; a failed host is skipped for 30 min, after our own network trouble only for
 *     a minute; every upload is checked with [probe]). One picture is uploaded once, however many keys share it
 *     (Poweramp has no album artist: "X" and "X feat. Y" tracks of an album have different keys).
 *  b) online lookups (Spotify, Deezer, iTunes, Bandcamp, Discogs, MusicBrainz + Cover Art Archive), first plausible hit wins
 *  Results are cached in [cacheFile] (one line per cover: key TAB url TAB source TAB storedWallMs); failed lookups are
 *  retried after 10 minutes (after a minute if a request got no answer, at once with other cover settings); uploaded
 *  covers older than 3 days are checked once per session and uploaded again if gone.
 *
 * [endpoints] lets tests point all hosts / APIs at a local MockWebServer.
 */
class CoverResolver(
    private val http: OkHttpClient,
    private val cacheFile: File,
    private val local: LocalCoverSource,
    private val encoder: ImageEncoder,
    private val clock: Clock = Clock.System,
    private val log: Logger = Logger.NONE,
    private val endpoints: CoverEndpoints = CoverEndpoints(),
) : CoverSource {
    private val web = Web(http, log)
    private val hosts = UploadHosts(web, endpoints, log)
    private val lookups = OnlineLookups(web, endpoints, clock, log)

    // guarded by lock
    private val lock = Any()
    // read from the file on first use (a background thread), not when the resolver is created
    private val cache by lazy(LazyThreadSafetyMode.NONE) { CoverCache(cacheFile, log) }
    private val negative = HashMap<String, Failed>()     // failed lookups, see Failed
    private val checked = HashSet<String>()              // URLs of uploaded covers checked again in this process
    private val hostSkipUntil = HashMap<Int, Long>()     // upload host -> clock.nowMs until which it is skipped
    private var hostSettings: Pair<Int, String>? = null  // upload host + Imgur Client-ID of the last upload
    private var networkTroubleAt: Long? = null           // clock.nowMs of the last resolve with a request without answer
    private val keyLocks = HashMap<String, KeyLock>()    // one resolve per cache key (and one upload per picture) at a time

    private class KeyLock {
        var users = 0
    }

    /** A failed lookup: not tried again before [retryAtMs] (clock.nowMs) - unless the cover [settings] changed since. */
    private class Failed(val retryAtMs: Long, val settings: List<Any>)

    /**
     * Fast, never touches the network. An uploaded cover older than 3 days that has not been checked in this process
     * yet is not returned: the caller then calls [resolve], which checks it first (hosts delete files after a while).
     */
    override fun peekCache(track: TrackInfo): CoverResult {
        val key = keyOf(track)
        synchronized(lock) {
            val hit = cache[key] ?: return CoverResult()
            return if (needsCheck(hit)) CoverResult() else hit
        }
    }

    override fun resolve(track: TrackInfo, config: Config): CoverResult {
        if (!config.coverEnabled) return CoverResult()
        val key = keyOf(track)
        return try {
            // two tracks of the same album at once (quick skipping): the second one waits and takes the first one's cover
            withKeyLock(key) { resolveKey(key, track, config) }
        } catch (e: Exception) {
            log.log("Cover lookup failed (${e.javaClass.simpleName}: ${e.message ?: "no details"})")
            CoverResult()
        }
    }

    /** Discord cannot show this track's cover: dropped for every key that shares its URL, the upload included. */
    override fun forget(track: TrackInfo) {
        val key = keyOf(track)
        synchronized(lock) {
            val url = cache[key]?.url
            cache.remove(key, persist = true)
            if (url != null) cache.removeUrl(url, persist = true)
            negative.remove(key)
        }
    }

    override fun clearCache() {
        synchronized(lock) {
            cache.clear()
            negative.clear()
            checked.clear()
            hostSkipUntil.clear()
        }
    }

    /** Is the uploaded picture really there? (catbox.moe once answered every request for a file with 0 bytes.) */
    override fun reachable(url: String): Boolean = probe(url) == Probe.OK

    /** First bytes of [url]: [Probe.UNKNOWN] when we got no answer (offline, DNS, timeout), BROKEN only for a real answer. */
    override fun probe(url: String): Probe = web.probe(url, 64, isPicture = Images::looksLikeImage)

    // caller holds lock
    private fun needsCheck(hit: CoverResult): Boolean =
        hit.isUploaded && clock.wallMs() - hit.storedWallMs > RECHECK_AFTER_MS && hit.url !in checked

    private fun <T> withKeyLock(key: String, block: () -> T): T {
        val keyLock = synchronized(lock) { keyLocks.getOrPut(key) { KeyLock() }.also { it.users++ } }
        try {
            return synchronized(keyLock) { block() }
        } finally {
            synchronized(lock) { if (--keyLock.users == 0) keyLocks.remove(key) }
        }
    }

    /**
     * Checks an old upload again (hosts delete files - x0.at after a while - or break, like catbox.moe once): true = still
     * usable. Gone -> dropped for every key that has it, only in memory: if nothing replaces it, the next start checks it
     * again. Could not be checked (our network) -> used for now and checked again next time.
     */
    private fun stillThere(cover: CoverResult): Boolean = when (probe(cover.url)) {
        Probe.OK -> {
            synchronized(lock) { checked += cover.url }
            true
        }
        Probe.UNKNOWN -> {
            log.log("Cached cover cannot be checked now (${cover.url}) - kept")
            true
        }
        Probe.BROKEN -> {
            log.log("Cached cover is gone (${cover.url}) - looking it up again")
            synchronized(lock) { cache.removeUrl(cover.url, persist = false) }
            false
        }
    }

    private fun resolveKey(key: String, track: TrackInfo, config: Config): CoverResult {
        val settings = lookupSettings(config)
        val stale: CoverResult?
        synchronized(lock) {
            stale = cache[key]
            if (stale != null && !needsCheck(stale)) return stale
            val failed = negative[key]
            if (stale == null && failed != null && failed.settings == settings && clock.nowMs() < failed.retryAtMs) {
                return CoverResult()
            }
        }
        if (stale != null && stillThere(stale)) return stale

        val networkErrorsBefore = web.networkErrors
        val query = Query.of(track)
        var res = CoverResult()
        if (config.preferLocal) res = uploadLocal(track, config)
        if (res.isEmpty) res = lookupOnline(query, config)
        if (res.isEmpty && !config.preferLocal) res = uploadLocal(track, config)
        if (!CoverCache.isUsableUrl(res.url)) res = CoverResult()   // Discord limit for image URLs: 256 characters

        synchronized(lock) {
            val now = clock.nowMs()
            if (web.networkErrors != networkErrorsBefore) networkTroubleAt = now
            if (!res.isEmpty) {
                if (res.storedWallMs == 0L) res = res.copy(storedWallMs = clock.wallMs())   // a reused upload keeps its age
                cache.put(key, res)
                negative.remove(key)
            } else if (!Thread.currentThread().isInterrupted) {   // a cancelled lookup is no reason to wait
                // a request without answer just now (offline for a moment, a host that hangs - and hosts skipped since)
                // may be why nothing was found: tried again soon
                val trouble = networkTroubleAt?.let { now - it < NETWORK_RETRY_MS } ?: false
                negative[key] = Failed(now + if (trouble) NETWORK_RETRY_MS else NEGATIVE_TTL_MS, settings)
            }
        }
        return res
    }

    // ---------------------------------------------------------------- a) local cover -> public upload host

    private fun uploadLocal(track: TrackInfo, config: Config): CoverResult {
        if (!config.localCovers || config.uploadHost == Config.UPLOAD_OFF || track.isStream) return CoverResult()
        val art = try {
            local.read(track)
        } catch (e: Exception) {
            log.log("Local cover cannot be read (${e.javaClass.simpleName}: ${e.message ?: "no details"})")
            null
        }
        if (art == null || art.isEmpty()) return CoverResult()
        // the same picture (all tracks of an album, whatever their artist tags say) is uploaded once
        val pictureKey = pictureKey(art, config.coverMaxPx)
        return withKeyLock(pictureKey) {
            val known = synchronized(lock) { cache[pictureKey]?.let { it to needsCheck(it) } }
            if (known != null && (!known.second || stillThere(known.first))) {
                known.first
            } else {
                val uploaded = upload(art, config)
                if (uploaded != null) synchronized(lock) { cache.put(pictureKey, uploaded) }
                uploaded ?: CoverResult()
            }
        }
    }

    /** Converts [art] and uploads it; null = no host took it. */
    private fun upload(art: ByteArray, config: Config): CoverResult? {
        var upload = try {
            encoder.toJpeg(art, config.coverMaxPx)
        } catch (e: Exception) {
            log.log("Local cover cannot be converted (${e.javaClass.simpleName}: ${e.message ?: "no details"})")
            null
        }
        // the encoder failed: JPEG / PNG are taken by every host as they are
        if ((upload == null || upload.isEmpty()) && Images.isJpegOrPng(art) && art.size <= RAW_UPLOAD_MAX_BYTES) upload = art
        if (upload == null || upload.isEmpty()) {
            log.log("Local cover is no picture that can be uploaded (${art.size} bytes)")
            return null
        }

        // the chosen host first; if it fails (or delivers nothing), x0.at / catbox.moe stand in.
        val clientId = credential(config.imgurClientId)
        val order = when {
            config.uploadHost == Config.UPLOAD_IMGUR && clientId.isEmpty() -> listOf(Config.UPLOAD_X0, Config.UPLOAD_CATBOX)
            config.uploadHost == Config.UPLOAD_X0 -> listOf(Config.UPLOAD_X0, Config.UPLOAD_CATBOX)
            else -> listOf(config.uploadHost, Config.UPLOAD_X0)
        }
        synchronized(lock) {
            // other upload settings (another host, a corrected Client-ID): every host gets a new chance
            val now = config.uploadHost to clientId
            if (hostSettings != now) {
                hostSkipUntil.clear()
                hostSettings = now
            }
        }
        for (host in order) {
            val skip = synchronized(lock) { hostSkipUntil[host]?.let { clock.nowMs() < it } ?: false }
            if (skip) continue
            val name = hosts.name(host)
            val sent = try {
                hosts.upload(host, upload, clientId)
            } catch (e: Exception) {   // only this host failed - the others still stand in
                log.log("$name upload failed (${e.javaClass.simpleName}: ${e.message ?: "no details"})")
                UploadHosts.Upload("")
            }
            var networkTrouble = sent.networkError
            if (sent.url.isNotEmpty()) {
                when (probe(sent.url)) {
                    Probe.OK -> {
                        log.log("Cover uploaded to $name: ${sent.url}")
                        return CoverResult(url = sent.url, source = "$LOCAL_ORIGIN|$name", storedWallMs = clock.wallMs())
                    }
                    Probe.BROKEN -> log.log("$name: the uploaded cover cannot be loaded (${sent.url}) - not used")
                    Probe.UNKNOWN -> {
                        log.log("$name: the uploaded cover cannot be checked (${sent.url}) - not used")
                        networkTrouble = true
                    }
                }
            }
            // a failed host is skipped for 30 minutes; one that did not answer (our network?) only for a minute
            synchronized(lock) { hostSkipUntil[host] = clock.nowMs() + if (networkTrouble) NETWORK_RETRY_MS else HOST_SKIP_MS }
        }
        return null
    }

    // ---------------------------------------------------------------- b) public lookups - first hit wins

    private fun lookupOnline(query: Query, config: Config): CoverResult {
        for (source in lookups.sources) {
            if (!source.enabled(config)) continue
            val url = try {
                source.lookup(query, config)
            } catch (e: Exception) {   // only this source failed - the others are still asked
                log.log("${source.name} lookup failed (${e.javaClass.simpleName}: ${e.message ?: "no details"})")
                ""
            }
            if (url.isNotEmpty() && CoverCache.isUsableUrl(url)) {
                log.log("Cover found on ${source.name}")
                return CoverResult(url = url, source = source.name)
            }
        }
        return CoverResult()
    }

    companion object {
        /** First part of [CoverResult.source] for uploaded covers ("poweramp|x0.at"). */
        const val LOCAL_ORIGIN = "poweramp"

        internal const val NEGATIVE_TTL_MS = 10 * 60 * 1000L
        internal const val NETWORK_RETRY_MS = 60 * 1000L
        internal const val HOST_SKIP_MS = 30 * 60 * 1000L
        internal const val RECHECK_AFTER_MS = 3 * 24 * 60 * 60 * 1000L
        internal const val RAW_UPLOAD_MAX_BYTES = 5 shl 20

        /** Cache key: artist + album, else file path, else artist + title (FNV-1a hex like the AIMP plugin). */
        fun keyOf(track: TrackInfo): String {
            val key = when {
                track.album.isNotBlank() -> "a|" + lower(track.artist) + "|" + lower(track.album)
                track.path.isNotBlank() && !isUrl(track.path) -> "f|" + lower(track.path)
                else -> "t|" + lower(track.artist) + "|" + lower(track.title)
            }
            return fnv1a(key).toString(16).padStart(16, '0')
        }

        /** Cache key of an upload: SHA-1 of the picture Poweramp gave + the size it was converted to ("u<hex>-512"). */
        internal fun pictureKey(art: ByteArray, maxPx: Int): String {
            val hash = MessageDigest.getInstance("SHA-1").digest(art)
            return "u" + hash.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') } + "-" + maxPx
        }

        /** What decides whether a lookup can find something: a failed lookup under other settings is tried again at once. */
        private fun lookupSettings(c: Config): List<Any> = listOf(
            c.localCovers, c.uploadHost, c.preferLocal, credential(c.imgurClientId),
            c.srcSpotify, credential(c.spotifyId), credential(c.spotifySecret), c.srcDeezer, c.srcItunes, c.srcBandcamp,
            c.srcDiscogs, credential(c.discogsToken), c.srcMusicBrainz,
        )

        // per UTF-16 unit like the AIMP plugin's towlower: the key never changes its length
        private fun lower(s: String): String = buildString(s.length) { for (c in s) append(c.lowercaseChar()) }

        private fun isUrl(path: String): Boolean {
            val l = lower(path.take(8))
            return listOf("http://", "https://", "mms://", "rtmp://", "ftp://").any { l.startsWith(it) }
        }

        /** 64-bit FNV-1a over the UTF-16 code units (wchar_t on Windows), so keys match the AIMP plugin's. */
        private fun fnv1a(s: String): ULong {
            var h = 0xcbf29ce484222325uL
            for (c in s) {
                h = h xor c.code.toULong()
                h *= 0x100000001b3uL
            }
            return h
        }
    }
}

/** Base URLs of every service the resolver talks to (defaults = the real services). */
data class CoverEndpoints(
    val x0: String = "https://x0.at/",
    val catbox: String = "https://catbox.moe/user/api.php",
    val imgur: String = "https://api.imgur.com/3/image",
    val deezer: String = "https://api.deezer.com",
    val itunes: String = "https://itunes.apple.com",
    val bandcamp: String = "https://bandcamp.com",
    val musicBrainz: String = "https://musicbrainz.org",
    val coverArtArchive: String = "https://coverartarchive.org",
    val discogs: String = "https://api.discogs.com",
    val spotifyAccounts: String = "https://accounts.spotify.com",
    val spotifyApi: String = "https://api.spotify.com",
)
