package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Number / time formatting of the screens. */
object Formats {
    /** Times as the Discord app prints them under the progress bar: "00:13", "04:47", "1:02:03". */
    fun discordTime(ms: Long): String {
        val total = ms.coerceAtLeast(0) / 1000
        val h = total / 3600
        val m = (total / 60) % 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%02d:%02d".format(Locale.ROOT, m, s)
    }

    /** Time of day in the user's format ("14:03:12" / "2:03:12 PM"). */
    fun clock(wallMs: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale).format(Instant.ofEpochMilli(wallMs).atZone(zone))

    /** Date and time in the user's format, e.g. for the last update check. */
    fun dateTime(wallMs: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
            .format(Instant.ofEpochMilli(wallMs).atZone(zone))

    /**
     * Link for short notes ("Link: youtube.com/results?search_query=Queen+Bohemian Rhapsody"): without "https://" /
     * "www." and with %XX escapes decoded (only for reading - Discord gets the encoded link).
     */
    fun shortUrl(url: String): String {
        var u = url.trim()
        for (prefix in listOf("https://", "http://", "www.")) {
            if (u.startsWith(prefix, ignoreCase = true)) u = u.substring(prefix.length)
        }
        return percentDecode(u)
    }

    /** %XX sequences as UTF-8; malformed escapes stay as they are. */
    fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val out = java.io.ByteArrayOutputStream(s.length)
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

    /** True for an http(s) link (own title link, Discord only accepts those). */
    fun isHttpUrl(url: String): Boolean {
        val u = url.trim()
        return (u.startsWith("https://", ignoreCase = true) && u.length > 8) ||
            (u.startsWith("http://", ignoreCase = true) && u.length > 7)
    }
}

/** Number fields: what is typed -> a setting, or null while it is not a valid value. */
object NumberInput {
    /** Digits only (other characters are dropped while typing), at most [maxLength] of them. */
    fun filter(text: String, maxLength: Int = 5): String = text.filter { it in '0'..'9' }.take(maxLength)

    /** The value if [text] is a number inside [range], else null (empty, too small / large). */
    fun parse(text: String, range: IntRange): Int? {
        val t = text.trim()
        if (t.isEmpty() || t.length > 9 || !t.all { it in '0'..'9' }) return null
        val v = t.toInt()
        return if (v in range) v else null
    }
}

/** Image decoding: the power of two to subsample a picture with so it is still at least [maxPx] on its shorter side. */
fun sampleSizeFor(width: Int, height: Int, maxPx: Int): Int {
    if (width <= 0 || height <= 0 || maxPx <= 0) return 1
    var sample = 1
    while (minOf(width, height) / (sample * 2) >= maxPx) sample *= 2
    return sample
}
