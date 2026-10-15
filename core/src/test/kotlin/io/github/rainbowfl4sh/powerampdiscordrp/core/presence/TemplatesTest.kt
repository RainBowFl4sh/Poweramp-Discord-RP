package io.github.rainbowfl4sh.powerampdiscordrp.core.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {
    private val vars = mapOf("title" to "Song", "artist" to "A & B", "album" to "")

    @Test
    fun `expand replaces placeholders case-insensitively`() {
        assertEquals("Song by A & B", Templates.expand("%TITLE% by %Artist%", vars))
        assertEquals("[]", Templates.expand("[%album%]", vars))
    }

    @Test
    fun `expand keeps unknown placeholders and stray percent signs`() {
        assertEquals("%genre% Song", Templates.expand("%genre% %title%", vars))
        assertEquals("100%Song", Templates.expand("100%%title%", vars))
        assertEquals("50 % of Song %", Templates.expand("50 % of %title% %", vars))
        assertEquals("%", Templates.expand("%", vars))
        assertEquals("", Templates.expand("", vars))
    }

    @Test
    fun `expand accepts keys in any case`() {
        assertEquals("x", Templates.expand("%Key%", mapOf("KEY" to "x")))
    }

    @Test
    fun `expand percent-encodes values only`() {
        assertEquals(
            "https://x.test/?q=A%20%26%20B+Song",
            Templates.expand("https://x.test/?q=%artist%+%title%", vars, urlEncode = true),
        )
    }

    @Test
    fun `urlEncode keeps unreserved characters and encodes UTF-8 bytes`() {
        assertEquals("AZaz09-._~", Templates.urlEncode("AZaz09-._~"))
        assertEquals("a%20b%2Bc%2F%3F%25", Templates.urlEncode("a b+c/?%"))
        assertEquals("M%C3%BCller", Templates.urlEncode("Müller"))
        assertEquals("%F0%9F%98%80", Templates.urlEncode("😀"))
        assertEquals("%D0%96", Templates.urlEncode("Ж"))
    }

    @Test
    fun `bar fills rounded fraction of the length`() {
        assertEquals("▰▰▱▱", Templates.bar(0.5, 4))
        assertEquals("▱▱▱", Templates.bar(0.0, 3))
        assertEquals("▰▰▰", Templates.bar(1.0, 3))
        assertEquals("▱▱", Templates.bar(0.249, 2))
        assertEquals("▰▱", Templates.bar(0.25, 2))
        assertEquals("▰▰▰▰▱▱▱▱▱▱▱▱", Templates.bar(0.3, 12))
    }

    @Test
    fun `bar clamps odd input`() {
        assertEquals("▰▰", Templates.bar(1.5, 2))
        assertEquals("▱▱", Templates.bar(-1.0, 2))
        assertEquals("▱▱", Templates.bar(Double.NaN, 2))
        assertEquals("", Templates.bar(0.5, 0))
        assertEquals("", Templates.bar(0.5, -3))
    }

    @Test
    fun `field trims and pads a single character with a no-break space`() {
        assertEquals("", Templates.field(""))
        assertEquals("", Templates.field("   "))
        assertEquals("ab", Templates.field("  ab "))
        assertEquals("a ", Templates.field(" a "))
        assertEquals("ä ", Templates.field("ä"))
        assertEquals("😀 ", Templates.field("😀"))   // one character, two UTF-16 units
        assertEquals("ab", Templates.field("ab"))
    }

    @Test
    fun `field keeps texts of exactly 128 bytes`() {
        val ascii = "x".repeat(128)
        assertEquals(ascii, Templates.field(ascii))
        val umlauts = "ü".repeat(64)   // 2 bytes each
        assertEquals(umlauts, Templates.field(umlauts))
        val emoji = "🎵".repeat(32)     // 4 bytes each
        assertEquals(emoji, Templates.field(emoji))
    }

    @Test
    fun `field cuts ASCII at 128 bytes with an ellipsis`() {
        val out = Templates.field("x".repeat(200))
        assertEquals("x".repeat(125) + "…", out)
        assertEquals(128, utf8(out))
    }

    @Test
    fun `field cuts umlauts at a character boundary`() {
        val out = Templates.field("ä".repeat(100))
        assertEquals("ä".repeat(62) + "…", out)   // 124 + 3 bytes, a 63rd 'ä' would need 129
        assertTrue(utf8(out) <= 128)
    }

    @Test
    fun `field never splits a surrogate pair`() {
        val out = Templates.field("😀".repeat(40))
        assertEquals("😀".repeat(31) + "…", out)
        assertTrue(utf8(out) <= 128)
        assertWellFormed(out)

        // 1 + 31 * 4 = 125 bytes fit exactly before the ellipsis
        val mixed = Templates.field("a" + "😀".repeat(40))
        assertEquals("a" + "😀".repeat(31) + "…", mixed)
        assertEquals(128, utf8(mixed))

        // an odd offset: the 32nd emoji would end at byte 126 > 125
        val shifted = Templates.field("ab" + "😀".repeat(40))
        assertEquals("ab" + "😀".repeat(30) + "…", shifted)
        assertWellFormed(shifted)
    }

    @Test
    fun `field drops trailing blanks before the ellipsis`() {
        val out = Templates.field("x".repeat(124) + "     tail")
        assertEquals("x".repeat(124) + "…", out)
    }

    @Test
    fun `formatTime rounds to seconds and adds hours when needed`() {
        assertEquals("0:00", Templates.formatTime(0))
        assertEquals("0:00", Templates.formatTime(-5_000))
        assertEquals("0:59", Templates.formatTime(59_499))
        assertEquals("1:00", Templates.formatTime(59_500))
        assertEquals("1:01", Templates.formatTime(61_000))
        assertEquals("10:00", Templates.formatTime(600_000))
        assertEquals("59:59", Templates.formatTime(3_599_000))
        assertEquals("1:00:00", Templates.formatTime(3_600_000))
        assertEquals("1:02:05", Templates.formatTime(3_725_000))
        assertEquals("12:00:00", Templates.formatTime(43_200_000))
    }

    @Test
    fun `file name and extension of plain paths`() {
        assertEquals("Song", Templates.fileName("/storage/emulated/0/Music/Song.MP3"))
        assertEquals("mp3", Templates.fileExtension("/storage/emulated/0/Music/Song.MP3"))
        assertEquals("Artist - Song.live", Templates.fileName("/m/Artist - Song.live.flac"))
        assertEquals("flac", Templates.fileExtension("/m/Artist - Song.live.flac"))
        assertEquals("Song", Templates.fileName("Song.ogg"))
        assertEquals(".hidden", Templates.fileName("/a/.hidden"))
        assertEquals("", Templates.fileExtension("/a/.hidden"))
        assertEquals("d", Templates.fileName("/a/b.c/d"))
        assertEquals("", Templates.fileExtension("/a/b.c/d"))
        assertEquals("100% Hits", Templates.fileName("/m/100% Hits.mp3"))   // not a URI: nothing decoded
        assertEquals("", Templates.fileName(""))
        assertEquals("", Templates.fileExtension(""))
    }

    @Test
    fun `file name of content URIs is URL-decoded`() {
        val saf = "content://com.android.externalstorage.documents/document/primary%3AMusic%2FArtist%20-%20S%C3%BC%C3%9F.flac"
        assertEquals("Artist - Süß", Templates.fileName(saf))
        assertEquals("flac", Templates.fileExtension(saf))

        val tree = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3ATop.m4a"
        assertEquals("Top", Templates.fileName(tree))
        assertEquals("m4a", Templates.fileExtension(tree))

        val raw = "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2FMix.opus"
        assertEquals("Mix", Templates.fileName(raw))

        assertEquals("42", Templates.fileName("content://media/external/audio/media/42"))
        assertEquals("", Templates.fileExtension("content://media/external/audio/media/42"))
        assertEquals("Song One", Templates.fileName("file:///sdcard/Music/Song%20One.ogg"))
        assertEquals("ogg", Templates.fileExtension("file:///sdcard/Music/Song%20One.ogg"))
        assertEquals("50%zz", Templates.fileName("content://x/50%zz.mp3"))   // malformed escape kept
    }

    @Test
    fun `file name of stream URLs ignores the query`() {
        assertEquals("x", Templates.fileName("http://host/stream/x.mp3?y=1&z=a.b"))
        assertEquals("mp3", Templates.fileExtension("http://host/stream/x.mp3?y=1&z=a.b"))
        assertEquals("live", Templates.fileName("https://radio.example/live#frag"))
        // no path: the host stands in, it has no extension
        assertEquals("radio.example:8000", Templates.fileName("https://radio.example:8000/"))
        assertEquals("", Templates.fileExtension("https://radio.example:8000/?type=.mp3"))
    }

    @Test
    fun `needsPeriodicRefresh detects moving placeholders in any field`() {
        val c = io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config()
        assertFalse(PresenceTexts.needsPeriodicRefresh(c))
        assertFalse(PresenceTexts.needsPeriodicRefresh(c.copy(state = "%dur%")))
        assertTrue(PresenceTexts.needsPeriodicRefresh(c.copy(details = "%POS%")))
        assertTrue(PresenceTexts.needsPeriodicRefresh(c.copy(state = "x %Bar%")))
        assertTrue(PresenceTexts.needsPeriodicRefresh(c.copy(largeText = "%percent%%")))
        assertTrue(PresenceTexts.needsPeriodicRefresh(c.copy(smallText = "%pos%")))
    }

    private fun utf8(s: String) = s.toByteArray(Charsets.UTF_8).size

    private fun assertWellFormed(s: String) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (Character.isHighSurrogate(c)) {
                assertTrue("lone high surrogate at $i", i + 1 < s.length && Character.isLowSurrogate(s[i + 1]))
                i += 2
            } else {
                assertFalse("lone low surrogate at $i", Character.isLowSurrogate(c))
                i++
            }
        }
    }
}
