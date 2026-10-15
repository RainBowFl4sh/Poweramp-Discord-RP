package io.github.rainbowfl4sh.powerampdiscordrp.core.cover

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Logger
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.Normalizer

/** Normalized track data used by all lookups (AIMP: Query / MakeQuery). */
internal data class Query(
    val artist: String,       // album artist if known - Poweramp's track info has none, so the track artist
    val trackArtist: String,
    val album: String,
    val title: String,
) {
    companion object {
        fun of(t: TrackInfo): Query {
            // quotes would break the field searches ("artist:\"...\"")
            fun quoteless(s: String) = s.replace('"', ' ').trim()
            val artist = quoteless(t.artist)
            return Query(artist = artist, trackArtist = artist, album = quoteless(t.album), title = quoteless(t.title))
        }
    }
}

/**
 * Lower-case letters / digits of any script only (AIMP's Norm kept every non-ASCII character). Compatibility forms and
 * accents are folded first (NFKD, combining marks dropped), so typographic punctuation ("Destiny’s Child", "Jay–Z"),
 * fullwidth forms and "Beyoncé" / "Beyonce" compare like their plain spellings.
 */
internal fun norm(s: String): String = buildString {
    Normalizer.normalize(s, Normalizer.Form.NFKD).codePoints().forEach { cp ->
        if (Character.isLetterOrDigit(cp)) appendCodePoint(Character.toLowerCase(cp))
    }
}

/**
 * A pasted key / token / client ID without spaces and invisible characters (zero-width space, BOM, NBSP): they would
 * only make the key wrong - or, in a header, make OkHttp refuse the request. The keys of all services are printable ASCII.
 */
internal fun credential(s: String): String = s.filter { it in '!'..'~' }

/** Guards against wrong covers: the artist of the hit must roughly match ours. */
internal fun plausible(found: String, wanted: String): Boolean {
    if (wanted.isEmpty()) return true
    val a = norm(found)
    val b = norm(wanted)
    if (a.isEmpty() || b.isEmpty()) return false
    return a.contains(b) || b.contains(a)
}

internal fun https(url: String): String = if (url.startsWith("http://")) "https://" + url.substring(7) else url

/** Percent-encodes everything but RFC 3986 "unreserved" characters (AIMP: util::UrlEncode). */
internal fun urlEncode(s: String): String = buildString {
    for (b in s.toByteArray(Charsets.UTF_8)) {
        val c = b.toInt() and 0xFF
        if (c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code || c.toChar() in "-_.~") {
            append(c.toChar())
        } else {
            append('%').append(HEX[c shr 4]).append(HEX[c and 15])
        }
    }
}

private const val HEX = "0123456789ABCDEF"

/**
 * The public cover sources of the AIMP plugin (cover.cpp): Spotify, Deezer, iTunes, Bandcamp, Discogs and
 * MusicBrainz + Cover Art Archive. Each lookup returns a cover URL or "" (nothing plausible found / error).
 */
internal class OnlineLookups(
    private val web: Web,
    private val endpoints: CoverEndpoints,
    private val clock: Clock,
    private val log: Logger,
) {
    /** One entry per source in the order they are asked; [enabled] reads the user's switch. */
    class Source(val name: String, val enabled: (Config) -> Boolean, val lookup: (Query, Config) -> String)

    val sources: List<Source> = listOf(
        Source("Spotify", { it.srcSpotify }, ::spotify),
        Source("Deezer", { it.srcDeezer }) { q, _ -> deezer(q) },
        Source("iTunes", { it.srcItunes }) { q, _ -> itunes(q) },
        Source("Bandcamp", { it.srcBandcamp }) { q, _ -> bandcamp(q) },
        Source("Discogs", { it.srcDiscogs }) { q, c -> discogs(q, credential(c.discogsToken)) },
        Source("MusicBrainz", { it.srcMusicBrainz }) { q, _ -> musicBrainz(q) },
    )

    private fun base(url: String) = url.trimEnd('/')

    private fun get(url: String, what: String, vararg headers: Pair<String, String>): Reply = web.send(what) {
        url(url)
        headers.forEach { (k, v) -> header(k, v) }
        get()
    }

    // ---------------------------------------------------------------- Deezer (public API, no key)

    private fun deezerQuery(type: String, q: String, artist: String): String {
        val r = get("${base(endpoints.deezer)}/search/$type?limit=1&q=${urlEncode(q)}", "Deezer")
        if (r.status != 200) return ""
        val item = parseJson(r.body).at("data", 0) ?: return ""   // "data":[] = nothing found
        // even the field searches answer with other artists ("Various Artists", tribute / cover bands)
        if (!plausible(item.at("artist", "name").text, artist)) return ""   // album: its artist, track: the track's
        val album = if (type == "track") item.at("album") else item
        var cover = album.at("cover_xl").text
        if (cover.isEmpty()) cover = album.at("cover_big").text
        if (cover.contains("/cover//")) return ""   // Deezer's "no cover" placeholder
        return cover
    }

    fun deezer(q: Query): String {
        var url = ""
        if (q.album.isNotEmpty() && q.artist.isNotEmpty()) {
            url = deezerQuery("album", "artist:\"${q.artist}\" album:\"${q.album}\"", q.artist)
        }
        if (url.isEmpty() && q.title.isNotEmpty() && q.trackArtist.isNotEmpty()) {
            url = deezerQuery("track", "artist:\"${q.trackArtist}\" track:\"${q.title}\"", q.trackArtist)
        }
        if (url.isEmpty() && q.album.isNotEmpty() && q.artist.isNotEmpty()) {
            url = deezerQuery("album", "${q.artist} ${q.album}", q.artist)
        }
        return url
    }

    // ---------------------------------------------------------------- iTunes / Apple Music (public API, no key)

    private fun itunesQuery(entity: String, term: String, artist: String): String {
        val r = get("${base(endpoints.itunes)}/search?media=music&limit=1&entity=$entity&term=${urlEncode(term)}", "iTunes")
        if (r.status != 200) return ""
        val item = parseJson(r.body).at("results", 0) ?: return ""
        if (!plausible(item.at("artistName").text, artist)) return ""
        return item.at("artworkUrl100").text.replaceFirst("100x100", "600x600")
    }

    fun itunes(q: Query): String {
        var url = ""
        if (q.album.isNotEmpty() && q.artist.isNotEmpty()) url = itunesQuery("album", "${q.artist} ${q.album}", q.artist)
        if (url.isEmpty() && q.title.isNotEmpty() && q.trackArtist.isNotEmpty()) {
            url = itunesQuery("song", "${q.trackArtist} ${q.title}", q.trackArtist)
        }
        return url
    }

    // ---------------------------------------------------------------- Bandcamp (public search endpoint, no key)

    private fun bandcampQuery(filter: String, text: String, artist: String): String {
        val json = buildJsonObject {
            put("search_text", JsonPrimitive(text))
            put("search_filter", JsonPrimitive(filter))
            put("full_page", JsonPrimitive(false))
            put("fan_id", JsonNull)
        }.toString()
        val r = web.send("Bandcamp") {
            url("${base(endpoints.bandcamp)}/api/bcsearch_public_api/1/autocomplete_elastic")
            post(json.toByteArray(Charsets.UTF_8).toRequestBody("application/json".toMediaType()))
        }
        if (r.status != 200) return ""
        val item = parseJson(r.body).at("auto", "results", 0) ?: return ""
        if (!plausible(item.at("band_name").text, artist)) return ""
        val img = item.at("img").text                      // ..._3.jpg (100 px)
        val us = img.lastIndexOf('_')
        val dot = img.lastIndexOf('.')
        if (img.isEmpty() || us < 0 || dot < 0 || dot < us) return ""
        return https(img.substring(0, us) + "_10" + img.substring(dot))   // _10 = full size
    }

    fun bandcamp(q: Query): String {
        // without an artist any band would pass (and "Greatest Hits" / "Track 01" always find someone's)
        var url = ""
        if (q.album.isNotEmpty() && q.artist.isNotEmpty()) url = bandcampQuery("a", "${q.artist} ${q.album}", q.artist)
        if (url.isEmpty() && q.title.isNotEmpty() && q.trackArtist.isNotEmpty()) {
            url = bandcampQuery("t", "${q.trackArtist} ${q.title}", q.trackArtist)
        }
        return url
    }

    // ---------------------------------------------------------------- MusicBrainz + Cover Art Archive (no key)

    fun musicBrainz(q: Query): String {
        if (q.artist.isEmpty() || (q.album.isEmpty() && q.title.isEmpty())) return ""
        val byAlbum = q.album.isNotEmpty()
        val url = if (byAlbum) {
            val lucene = "releasegroup:\"${q.album}\" AND artist:\"${q.artist}\""
            "${base(endpoints.musicBrainz)}/ws/2/release-group/?fmt=json&limit=1&query=${urlEncode(lucene)}"
        } else {
            val lucene = "recording:\"${q.title}\" AND artist:\"${q.trackArtist}\""
            "${base(endpoints.musicBrainz)}/ws/2/recording/?fmt=json&limit=1&query=${urlEncode(lucene)}"
        }
        val headers = arrayOf("Accept" to "application/json", "User-Agent" to Web.USER_AGENT)
        val r = get(url, "MusicBrainz", *headers)
        if (r.status != 200) return ""
        val json = parseJson(r.body)
        val item = if (byAlbum) json.at("release-groups", 0) else json.at("recordings", 0)
        if ((item.at("score").text.toLongOrNull() ?: -1) < 90) return ""   // a weak match is probably another record

        val id = if (byAlbum) item.at("id").text else item.at("releases", 0, "id").text
        if (id.isEmpty()) return ""
        val caa = "${base(endpoints.coverArtArchive)}/${if (byAlbum) "release-group" else "release"}/$id"
        val c = get(caa, "Cover Art Archive", *headers)
        if (c.status != 200) return ""
        val images = parseJson(c.body).at("images").array
        val image = images.firstOrNull { it.at("front").text == "true" } ?: images.firstOrNull()
        var img = image.at("thumbnails", "500").text
        if (img.isEmpty()) img = image.at("thumbnails", "large").text
        return https(img)
    }

    // ---------------------------------------------------------------- Discogs (personal access token)

    fun discogs(q: Query, token: String): String {
        if (token.isEmpty() || q.artist.isEmpty()) return ""
        var url = "${base(endpoints.discogs)}/database/search?type=release&per_page=1&artist=${urlEncode(q.artist)}"
        url += when {
            q.album.isNotEmpty() -> "&release_title=${urlEncode(q.album)}"
            q.title.isNotEmpty() -> "&track=${urlEncode(q.title)}"
            else -> return ""
        }
        url += "&token=${urlEncode(token)}"
        val r = get(url, "Discogs")
        if (r.status != 200) {
            if (r.status != 0) log.log("Discogs lookup failed (HTTP ${r.status})")
            return ""
        }
        val item = parseJson(r.body).at("results", 0) ?: return ""
        if (!plausible(item.at("title").text, q.artist)) return ""   // "Artist - Album"
        val img = item.at("cover_image").text
        if (img.contains("spacer.gif")) return ""
        return https(img)
    }

    // ---------------------------------------------------------------- Spotify (client credentials)

    private val spotifyLock = Any()
    private var spotifyToken = ""
    private var spotifyFor = ""
    private var spotifyExpiresMs = 0L

    /** Access token of the client-credentials flow, cached until shortly before it expires. */
    private fun spotifyToken(id: String, secret: String, forceNew: Boolean): String {
        synchronized(spotifyLock) { return fetchSpotifyToken(id, secret, forceNew) }
    }

    private fun fetchSpotifyToken(id: String, secret: String, forceNew: Boolean): String {
        val who = "$id:$secret"
        if (!forceNew && spotifyToken.isNotEmpty() && spotifyFor == who && clock.nowMs() < spotifyExpiresMs) return spotifyToken
        spotifyToken = ""
        val r = web.send("Spotify") {
            url("${base(endpoints.spotifyAccounts)}/api/token")
            header("Authorization", Credentials.basic(id, secret, Charsets.UTF_8))
            post(FormBody.Builder().add("grant_type", "client_credentials").build())
        }
        if (r.status != 200) {
            log.log("Spotify token request failed (HTTP ${r.status})")
            return ""
        }
        val json = parseJson(r.body)
        spotifyToken = json.at("access_token").text
        val sec = json.at("expires_in").text.toLongOrNull() ?: -1
        spotifyExpiresMs = clock.nowMs() + (if (sec > 120) sec - 60 else 3000) * 1000
        spotifyFor = who
        return spotifyToken
    }

    private fun spotifySearch(id: String, secret: String, type: String, q: String, artist: String): String {
        val url = "${base(endpoints.spotifyApi)}/v1/search?limit=1&type=$type&q=${urlEncode(q)}"
        repeat(2) { attempt ->
            val token = spotifyToken(id, secret, forceNew = attempt > 0)
            if (token.isEmpty()) return ""
            val r = get(url, "Spotify", "Authorization" to "Bearer $token")
            if (r.status == 401) return@repeat   // expired token -> fetch a new one once
            if (r.status != 200) return ""
            val item = parseJson(r.body).at("${type}s", "items", 0) ?: return ""
            if (!plausible(item.at("artists", 0, "name").text, artist)) return ""
            val images = if (type == "track") item.at("album", "images") else item.at("images")
            return images.at(0, "url").text   // first image = largest (640 px)
        }
        return ""
    }

    fun spotify(q: Query, config: Config): String {
        val id = credential(config.spotifyId)
        val secret = credential(config.spotifySecret)
        if (id.isEmpty() || secret.isEmpty()) return ""
        var url = ""
        if (q.album.isNotEmpty() && q.artist.isNotEmpty()) {
            url = spotifySearch(id, secret, "album", "album:${q.album} artist:${q.artist}", q.artist)
        }
        if (url.isEmpty() && q.title.isNotEmpty() && q.trackArtist.isNotEmpty()) {
            url = spotifySearch(id, secret, "track", "track:${q.title} artist:${q.trackArtist}", q.trackArtist)
        }
        return url
    }
}
