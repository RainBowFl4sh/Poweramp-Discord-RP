package io.github.rainbowfl4sh.powerampdiscordrp.core.presence

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import kotlin.math.floor

/** Why Discord shows nothing for a snapshot (shown on the status page / in the live preview). */
enum class Hidden { NO, DISABLED, STOPPED, STREAM, PATH_FILTER, PAUSED, PAUSED_TIMEOUT }

/** Localized words used inside the presence texts (%status%). Provided by the app (string resources). */
data class StatusStrings(
    val playing: String = "Playing",
    val paused: String = "Paused",
    /** State line of the test presence (Advanced tab). */
    val testLine: String = "The connection works!",
)

/** The texts of a presence exactly as Discord gets them (also drawn by the live preview). "" = line not shown. */
data class ActivityTexts(
    val hidden: Hidden = Hidden.NO,
    val playing: Boolean = false,
    val posMs: Long = 0,
    val durMs: Long = 0,
    val details: String = "",
    val state: String = "",
    val largeText: String = "",
    val smallText: String = "",
    val detailsUrl: String = "",   // clickable title, "" = none
)

/**
 * Port of the AIMP plugin's ComputeTexts(): placeholders, filters, paused behaviour, Discord's field limits.
 *
 * Placeholders (case-insensitive): %artist% %title% %album% %filename% %ext% %pos% %dur% %percent% %bar% %status%
 * %codec% %bitrate% %samplerate% %bits% %track% (1-based list position) %listsize% %rating%.
 * "||" separates alternatives that take turns ([TextRotation]).
 */
object PresenceTexts {
    /** Discord rejects URLs (images, details_url) longer than this. */
    const val MAX_URL_LENGTH = 256

    /** More would not fit into a text field anyway (each bar character takes 3 UTF-8 bytes). */
    private const val MAX_BAR_LENGTH = Templates.MAX_FIELD_BYTES / 3

    /**
     * @param nowMs        current [io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock.nowMs] (to advance the position while
     *                     playing, and the text rotation's step counted from [PlaybackSnapshot.trackSinceMs])
     * @param pausedForMs  how long playback has been paused (for clearAfterPausedMin), 0 if playing
     */
    fun compute(c: Config, s: PlaybackSnapshot, nowMs: Long, pausedForMs: Long, strings: StatusStrings): ActivityTexts {
        val track = s.track
        val playing = s.state == PlayState.PLAYING
        val hidden = when {
            !c.enabled -> Hidden.DISABLED
            s.state == PlayState.STOPPED || track.isEmpty -> Hidden.STOPPED
            c.hideStreams && track.isStream -> Hidden.STREAM
            matchesAny(track.path, c.excludePaths) -> Hidden.PATH_FILTER
            !playing && c.pausedBehavior == Config.PAUSED_HIDE -> Hidden.PAUSED
            !playing && c.clearAfterPausedMin > 0 && pausedForMs >= c.clearAfterPausedMin * 60_000L -> Hidden.PAUSED_TIMEOUT
            else -> Hidden.NO
        }

        // the snapshot's position is from stampMs; while playing it has moved on since then
        val durMs = track.durationMs.coerceAtLeast(0)
        var posMs = s.positionMs
        if (playing) posMs += nowMs - s.stampMs
        if (durMs > 0 && posMs > durMs) posMs = durMs
        if (posMs < 0) posMs = 0

        val vars = variables(c, track, playing, posMs, durMs, strings)
        // one step counter for all fields: alternatives with the same count change together
        val step = TextRotation.step(s.trackSinceMs, nowMs, TextRotation.intervalMs(c))
        var details = text(c.details, vars, step)
        val state = text(c.state, vars, step)
        val largeText = text(c.largeText, vars, step)
        val smallText = if (c.showSmallIcon) text(c.smallText, vars, step) else ""
        // Discord needs at least one line: fall back to the file name
        if (details.isEmpty() && state.isEmpty()) details = Templates.field(vars.getValue("filename"))

        return ActivityTexts(
            hidden = hidden,
            playing = playing,
            posMs = posMs,
            durMs = durMs,
            details = details,
            state = state,
            largeText = largeText,
            smallText = smallText,
            detailsUrl = if (details.isEmpty()) "" else titleLink(c.effectiveTitleLink(), vars),
        )
    }

    /**
     * True if a shown text template rotates ("||" between two or more alternatives that are not blank): the presence
     * changes every [Config.effectiveRotationSeconds].
     */
    fun needsRotation(c: Config): Boolean =
        TextRotation.rotates(c.details) || TextRotation.rotates(c.state) || TextRotation.rotates(c.largeText) ||
            (c.showSmallIcon && TextRotation.rotates(c.smallText))

    /**
     * One text field: every alternative expanded and made a Discord field on its own, the one of [step] among those that
     * are not empty. Without "||" exactly the expanded template as a field.
     */
    private fun text(template: String, vars: Map<String, String>, step: Long): String {
        if (TextRotation.SEPARATOR !in template) return Templates.field(Templates.expand(template, vars))
        return TextRotation.pick(TextRotation.alternatives(template).map { Templates.field(Templates.expand(it, vars)) }, step)
    }

    /** True if a template uses %pos%, %bar% or %percent% (presence must be refreshed every refreshSeconds). */
    fun needsPeriodicRefresh(c: Config): Boolean =
        listOf(c.details, c.state, c.largeText, c.smallText).any { template ->
            val l = template.lowercase(Locale.ROOT)
            "%pos%" in l || "%bar%" in l || "%percent%" in l
        }

    /** Values of all placeholders (keys in lower case, without the % signs). */
    internal fun variables(
        c: Config,
        track: TrackInfo,
        playing: Boolean,
        posMs: Long,
        durMs: Long,
        strings: StatusStrings,
    ): Map<String, String> {
        val rating = track.rating.coerceIn(0, 5)
        return mapOf(
            "artist" to track.artist,
            "title" to track.title,
            "album" to track.album,
            "filename" to Templates.fileName(track.path),
            "ext" to Templates.fileExtension(track.path),
            "pos" to Templates.formatTime(posMs),
            "dur" to if (durMs > 0) Templates.formatTime(durMs) else "--:--",
            "percent" to if (durMs > 0) (posMs * 100 / durMs).toString() else "0",
            "bar" to Templates.bar(if (durMs > 0) posMs.toDouble() / durMs else 0.0, c.barLength.coerceIn(0, MAX_BAR_LENGTH)),
            "status" to if (playing) strings.playing else strings.paused,
            "codec" to track.codec,
            "bitrate" to if (track.bitRate > 0) "${track.bitRate} kbps" else "",
            "samplerate" to if (track.sampleRate > 0) "${kiloHertz(track.sampleRate)} kHz" else "",
            "bits" to if (track.bitsPerSample > 0) "${track.bitsPerSample}-bit" else "",
            "track" to if (track.listSize > 0) (track.posInList + 1).toString() else "",
            "listsize" to if (track.listSize > 0) track.listSize.toString() else "",
            "rating" to if (rating > 0) "★".repeat(rating) + "☆".repeat(5 - rating) else "",
        )
    }

    /**
     * Clickable title: the link template with URL-encoded values, made a valid URI; Discord takes at most 256 characters.
     * "" if it cannot be one: Discord would reject the whole presence over an invalid details_url.
     */
    private fun titleLink(template: String, vars: Map<String, String>): String {
        if (template.isEmpty()) return ""
        var url = normalizeUrl(Templates.expand(template, vars, urlEncode = true))
        if (url.length > MAX_URL_LENGTH) {   // very long names: fall back to searching the title only
            url = normalizeUrl(Templates.expand(template, vars + ("artist" to ""), urlEncode = true))
        }
        return if (url.length <= MAX_URL_LENGTH) url else ""
    }

    /**
     * An http(s) link as a valid URI (RFC 3986), or "" if it cannot be one (other scheme, no host). What the user typed
     * literally into a link template is percent-encoded where a URI does not allow it: spaces, quotes, <>{}|\^`,
     * non-ASCII, a '%' that does not start an escape (e.g. an unknown placeholder), '[' ']' outside the host and every
     * '#' after the first.
     */
    internal fun normalizeUrl(link: String): String {
        val url = link.trim()
        if (!isHttpUrl(url)) return ""
        val authorityStart = url.indexOf("://") + 3
        val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).let { if (it < 0) url.length else it }
        val out = StringBuilder(url.length + 16)
        var fragment = false
        var i = 0
        while (i < url.length) {
            val c = url[i]
            val keep = when {
                c == '%' -> i + 2 < url.length && isHex(url[i + 1]) && isHex(url[i + 2])
                c == '[' || c == ']' -> i in authorityStart until authorityEnd   // IPv6 host literal
                c == '#' -> !fragment   // only one fragment
                else -> c.code < 0x80 && (c.isLetterOrDigit() || c in URI_CHARS)
            }
            if (c == '#') fragment = true
            if (keep) {
                out.append(c)
                i++
            } else {
                val end = if (Character.isHighSurrogate(c) && i + 1 < url.length && Character.isLowSurrogate(url[i + 1])) i + 2 else i + 1
                out.append(Templates.urlEncode(url.substring(i, end)))
                i = end
            }
        }
        val normalized = out.toString()
        return try {
            val uri = URI(normalized)
            if (uri.rawAuthority.isNullOrEmpty()) "" else normalized
        } catch (_: URISyntaxException) {
            ""
        }
    }

    /** Characters (besides ASCII letters and digits) a URI may contain as they are: unreserved, gen- and sub-delimiters. */
    private const val URI_CHARS = "-._~:/?@!$&'()*+,;="

    private fun isHex(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /** 44100 -> "44.1", 48000 -> "48", 11025 -> "11.025" (always a dot: the texts are not localized numbers). */
    private fun kiloHertz(hz: Int): String = BigDecimal(hz).movePointLeft(3).stripTrailingZeros().toPlainString()

    /** ';' separated substrings, trimmed, empty entries ignored, case-insensitive (like util::MatchesAny). */
    internal fun matchesAny(text: String, patterns: String): Boolean {
        if (text.isEmpty() || patterns.isBlank()) return false
        val t = text.lowercase(Locale.ROOT)
        return patterns.split(';').any { raw ->
            val p = raw.trim().lowercase(Locale.ROOT)
            p.isNotEmpty() && t.contains(p)
        }
    }

    internal fun isHttpUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
}

/** Template helpers (public for tests and the live preview). */
object Templates {
    /** Discord's limit for text fields (details, state, large_text, small_text) in UTF-8 bytes. */
    const val MAX_FIELD_BYTES = 128

    private const val ELLIPSIS = "…"
    private const val NO_BREAK_SPACE = '\u00A0'
    private const val HEX = "0123456789ABCDEF"

    /** Replaces %name% placeholders (case-insensitive); unknown placeholders stay untouched. urlEncode = percent-encode values. */
    fun expand(template: String, vars: Map<String, String>, urlEncode: Boolean = false): String {
        val lookup = if (vars.keys.all { it == it.lowercase(Locale.ROOT) }) vars else vars.mapKeys { it.key.lowercase(Locale.ROOT) }
        val out = StringBuilder(template.length + 32)
        var i = 0
        while (i < template.length) {
            if (template[i] == '%') {
                val j = template.indexOf('%', i + 1)
                if (j > 0) {
                    val value = lookup[template.substring(i + 1, j).lowercase(Locale.ROOT)]
                    if (value != null) {
                        out.append(if (urlEncode) urlEncode(value) else value)
                        i = j + 1
                        continue
                    }
                }
            }
            // not a known placeholder: keep the '%' and look for the next one right after it ("100%%title%")
            out.append(template[i++])
        }
        return out.toString()
    }

    /** Text progress bar ▰▰▰▱▱▱ with [length] characters. */
    fun bar(fraction: Double, length: Int): String {
        if (length <= 0) return ""
        val f = if (fraction.isNaN()) 0.0 else fraction.coerceIn(0.0, 1.0)
        val filled = floor(f * length + 0.5).toInt()
        return buildString(length) { repeat(length) { append(if (it < filled) '▰' else '▱') } }
    }

    /** Discord text field: trimmed, max. 128 UTF-8 bytes (cut at a character boundary), padded to 2 characters; "" stays "". */
    fun field(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        val out = truncateUtf8(trimmed, MAX_FIELD_BYTES)
        // Discord wants at least 2 characters: pad a single one with a no-break space
        return if (out.codePointCount(0, out.length) < 2) out + NO_BREAK_SPACE else out
    }

    /** m:ss or h:mm:ss */
    fun formatTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 500) / 1000   // rounded to whole seconds, like the AIMP plugin
        val h = total / 3600
        val m = (total / 60) % 60
        val s = total % 60
        return if (h > 0) "$h:${pad2(m)}:${pad2(s)}" else "$m:${pad2(s)}"
    }

    /** Percent-encodes UTF-8 bytes of everything except A-Za-z0-9 - . _ ~ (space -> %20). */
    fun urlEncode(text: String): String {
        val out = StringBuilder(text.length * 3)
        for (b in text.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            if (c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code ||
                c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code
            ) {
                out.append(c.toChar())
            } else {
                out.append('%').append(HEX[c shr 4]).append(HEX[c and 15])
            }
        }
        return out.toString()
    }

    /**
     * Last path segment without extension: "/a/b.mp3" -> "b"; URIs are URL-decoded
     * ("content://…/document/primary%3AMusic%2FSong.flac" -> "Song"); stream URLs ignore the query
     * ("http://h/x.mp3?y" -> "x"); a URL without a path gives its host ("http://radio.example:8000/" -> "radio.example:8000").
     */
    fun fileName(path: String): String = nameAndExtension(path).first

    /** Extension of [fileName]'s segment in lower case, without the dot ("" if none). */
    fun fileExtension(path: String): String = nameAndExtension(path).second

    /**
     * Cuts [s] to at most [maxBytes] UTF-8 bytes at a code point boundary (surrogate pairs stay together) and marks the
     * cut with an ellipsis, like util::TruncateUtf8.
     */
    internal fun truncateUtf8(s: String, maxBytes: Int): String {
        if (utf8Length(s) <= maxBytes) return s
        val budget = maxBytes - utf8Length(ELLIPSIS)
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = utf8Length(cp)
            if (bytes + n > budget) break
            bytes += n
            i += Character.charCount(cp)
        }
        return s.substring(0, i).trimEnd() + ELLIPSIS
    }

    internal fun utf8Length(s: String): Int {
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            bytes += utf8Length(cp)
            i += Character.charCount(cp)
        }
        return bytes
    }

    /** Bytes of one code point in UTF-8; a lone surrogate counts as U+FFFD (3 bytes) - never less than an encoder makes of it. */
    private fun utf8Length(cp: Int): Int = when {
        cp < 0x80 -> 1
        cp < 0x800 -> 2
        cp < 0x10000 -> 3
        else -> 4
    }

    private fun pad2(v: Long): String = if (v < 10) "0$v" else v.toString()

    /** (name, extension) of the last segment of a path or URI. */
    private fun nameAndExtension(path: String): Pair<String, String> {
        val p = path.trim()
        val schemeEnd = p.indexOf("://")
        val isUri = schemeEnd > 0 && p.substring(0, schemeEnd).all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
        if (!isUri) return splitName(p.substringAfterLast('/'))

        val rest = p.substring(schemeEnd + 3).substringBefore('?').substringBefore('#')
        val segments = rest.split('/')
        val pathSegments = segments.drop(1).filter { it.isNotEmpty() }   // segments[0] = host / authority
        // a stream URL without a path has no file name: the host stands in (a host has no "extension")
        val last = pathSegments.lastOrNull() ?: return segments[0] to ""
        var name = percentDecode(last)
        // Storage Access Framework: the segment after "document" is a document ID like "primary:Music/Artist/Song.flac"
        // or "raw:/storage/emulated/0/Download/Song.flac" - its root ends at the first ':'
        if (p.startsWith("content://", ignoreCase = true) && pathSegments.getOrNull(pathSegments.size - 2) == "document") {
            name = name.substringAfter(':')
        }
        return splitName(name.substringAfterLast('/'))
    }

    /** ("name", "ext") - a leading dot (".hidden") is part of the name. */
    private fun splitName(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) to name.substring(dot + 1).lowercase(Locale.ROOT) else name to ""
    }

    /** Decodes %XX sequences as UTF-8 ('+' stays '+': it is a path, not a form). Malformed escapes are kept as they are. */
    private fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val out = ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hi = Character.digit(s[i + 1], 16)
                val lo = Character.digit(s[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    out.write(hi * 16 + lo)
                    i += 3
                    continue
                }
            }
            val end = if (Character.isHighSurrogate(c) && i + 1 < s.length) i + 2 else i + 1
            out.write(s.substring(i, end).encodeToByteArray())
            i = end
        }
        return out.toByteArray().decodeToString()
    }
}
