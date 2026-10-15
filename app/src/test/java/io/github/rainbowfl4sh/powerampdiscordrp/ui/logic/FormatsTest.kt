package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class FormatsTest {
    @Test
    fun discordTimes() {
        assertEquals("00:00", Formats.discordTime(0))
        assertEquals("00:00", Formats.discordTime(-500))
        assertEquals("00:13", Formats.discordTime(13_999))
        assertEquals("01:23", Formats.discordTime(83_000))
        assertEquals("59:59", Formats.discordTime(3_599_000))
        assertEquals("1:00:00", Formats.discordTime(3_600_000))
        assertEquals("1:02:03", Formats.discordTime(3_723_000))
    }

    @Test
    fun clockAndDateInTheUsersFormat() {
        val ms = 1_700_000_000_000L   // 2023-11-14 22:13:20 UTC
        assertEquals("22:13:20", Formats.clock(ms, Locale.GERMANY, ZoneOffset.UTC))
        assertTrue(Formats.clock(ms, Locale.US, ZoneOffset.UTC).startsWith("10:13:20"))
        val date = Formats.dateTime(ms, Locale.GERMANY, ZoneOffset.UTC)
        assertTrue(date, date.contains("14.11.2023") && date.contains("22:13"))
    }

    @Test
    fun shortUrls() {
        assertEquals("youtube.com/results?q=a", Formats.shortUrl("https://www.youtube.com/results?q=a"))
        assertEquals("last.fm/x", Formats.shortUrl(" http://last.fm/x "))
        assertEquals("example.org", Formats.shortUrl("example.org"))
        assertEquals(
            "youtube.com/results?search_query=Earth, Wind & Fire+Сон",
            Formats.shortUrl("https://www.youtube.com/results?search_query=Earth%2C%20Wind%20%26%20Fire+%D0%A1%D0%BE%D0%BD"),
        )
    }

    @Test
    fun percentDecoding() {
        assertEquals("a b", Formats.percentDecode("a%20b"))
        assertEquals("100% sure", Formats.percentDecode("100% sure"))   // not an escape
        assertEquals("end%", Formats.percentDecode("end%"))
        assertEquals("x%2", Formats.percentDecode("x%2"))
        assertEquals("%zz", Formats.percentDecode("%zz"))
        assertEquals("ü😀", Formats.percentDecode("%C3%BC😀"))
    }

    @Test
    fun httpUrls() {
        assertTrue(Formats.isHttpUrl("https://www.last.fm/search?q=%artist%"))
        assertTrue(Formats.isHttpUrl("HTTP://a.b"))
        assertFalse(Formats.isHttpUrl("https://"))
        assertFalse(Formats.isHttpUrl("ftp://a.b"))
        assertFalse(Formats.isHttpUrl("www.youtube.com"))
        assertFalse(Formats.isHttpUrl(""))
    }

    @Test
    fun numberInput() {
        assertEquals("120", NumberInput.filter("1 2a0"))
        assertEquals("12345", NumberInput.filter("1234567"))
        assertEquals(15, NumberInput.parse("15", 5..120))
        assertEquals(0, NumberInput.parse(" 0 ", 0..1440))
        assertNull(NumberInput.parse("", 0..10))
        assertNull(NumberInput.parse("3", 5..120))
        assertNull(NumberInput.parse("121", 5..120))
        assertNull(NumberInput.parse("-1", 0..10))
        assertNull(NumberInput.parse("99999999999", 0..Int.MAX_VALUE))
    }

    @Test
    fun imageSubsampling() {
        assertEquals(1, sampleSizeFor(500, 500, 512))
        assertEquals(1, sampleSizeFor(1000, 1000, 512))
        assertEquals(2, sampleSizeFor(1024, 1024, 512))
        assertEquals(4, sampleSizeFor(3000, 2400, 512))
        assertEquals(1, sampleSizeFor(0, 0, 512))
    }
}
