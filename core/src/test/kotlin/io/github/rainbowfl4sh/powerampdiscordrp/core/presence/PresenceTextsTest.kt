package io.github.rainbowfl4sh.powerampdiscordrp.core.presence

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceTextsTest {
    private val track = TrackInfo(
        id = 7, realId = 70, title = "Song", artist = "Artist", album = "Album",
        path = "/storage/emulated/0/Music/Artist - Song.FLAC", durationMs = 200_000,
        posInList = 4, listSize = 12, sampleRate = 44_100, channels = 2, bitsPerSample = 24, bitRate = 1_000,
        codec = "FLAC", rating = 4,
    )
    private val strings = StatusStrings(playing = "Wiedergabe", paused = "Pausiert")

    private fun snap(state: PlayState = PlayState.PLAYING, t: TrackInfo = track, posMs: Long = 50_000, stampMs: Long = 1_000) =
        PlaybackSnapshot(state, t, posMs, stampMs)

    private fun compute(c: Config = Config(), s: PlaybackSnapshot = snap(), nowMs: Long = 11_000, pausedForMs: Long = 0) =
        PresenceTexts.compute(c, s, nowMs, pausedForMs, strings)

    @Test
    fun `default templates`() {
        val t = compute()
        assertEquals(Hidden.NO, t.hidden)
        assertTrue(t.playing)
        assertEquals("Song", t.details)
        assertEquals("Artist", t.state)
        assertEquals("Album", t.largeText)
        assertEquals("Wiedergabe", t.smallText)
        assertEquals("https://www.youtube.com/results?search_query=Artist+Song", t.detailsUrl)
    }

    @Test
    fun `every placeholder`() {
        val c = Config(
            details = "%artist%|%title%|%album%|%filename%|%ext%",
            state = "%pos%/%dur% %percent%% %status%",
            largeText = "%codec% %bitrate% %samplerate% %bits%",
            smallText = "%track%/%listsize% %rating% %bar%",
        )
        val t = compute(c)   // 50 s at stamp 1 s, now 11 s, playing -> 60 s
        assertEquals("Artist|Song|Album|Artist - Song|flac", t.details)
        assertEquals("1:00/3:20 30% Wiedergabe", t.state)
        assertEquals("FLAC 1000 kbps 44.1 kHz 24-bit", t.largeText)
        assertEquals("5/12 ★★★★☆ ▰▰▰▰▱▱▱▱▱▱▱▱", t.smallText)
        assertEquals(60_000, t.posMs)
        assertEquals(200_000, t.durMs)
    }

    @Test
    fun `missing technical values give empty texts`() {
        val bare = track.copy(sampleRate = 0, bitsPerSample = 0, bitRate = 0, codec = "", listSize = 0, posInList = 0, rating = 0, durationMs = 0)
        val c = Config(
            details = "[%codec%][%bitrate%][%samplerate%][%bits%]",
            state = "[%track%][%listsize%][%rating%]",
            largeText = "%pos% %dur% %percent% %bar%",
            barLength = 3,
        )
        val t = compute(c, snap(t = bare))
        assertEquals("[][][][]", t.details)
        assertEquals("[][][]", t.state)
        assertEquals("1:00 --:-- 0 ▱▱▱", t.largeText)
    }

    @Test
    fun `sample rates in kHz`() {
        fun rate(hz: Int) = compute(Config(details = "%samplerate%"), snap(t = track.copy(sampleRate = hz))).details
        assertEquals("48 kHz", rate(48_000))
        assertEquals("44.1 kHz", rate(44_100))
        assertEquals("22.05 kHz", rate(22_050))
        assertEquals("192 kHz", rate(192_000))
    }

    @Test
    fun `ratings as stars`() {
        fun stars(r: Int) = compute(Config(details = "x%rating%"), snap(t = track.copy(rating = r))).details
        assertEquals("x★☆☆☆☆", stars(1))
        assertEquals("x★★★★★", stars(5))
        assertEquals("x ", stars(0))   // "x" alone is padded
    }

    @Test
    fun `paused position does not move and shows the paused word`() {
        val t = compute(Config(pausedBehavior = Config.PAUSED_SHOW, state = "%pos% %status%"), snap(PlayState.PAUSED), nowMs = 99_000)
        assertEquals(Hidden.NO, t.hidden)
        assertFalse(t.playing)
        assertEquals(50_000, t.posMs)
        assertEquals("0:50 Pausiert", t.state)
    }

    @Test
    fun `position is clamped to the duration and to zero`() {
        assertEquals(200_000, compute(s = snap(posMs = 199_000), nowMs = 60_000).posMs)
        assertEquals(0, compute(s = snap(posMs = -5_000, stampMs = 0), nowMs = 1_000).posMs)
        // unknown duration (radio): no upper bound
        assertEquals(400_000, compute(s = snap(t = track.copy(durationMs = 0), posMs = 390_000), nowMs = 11_000).posMs)
    }

    @Test
    fun `file name stands in when details and state are empty`() {
        val c = Config(details = "%album%", state = "%artist%")
        val t = compute(c, snap(t = track.copy(album = " ", artist = "")))
        assertEquals("Artist - Song", t.details)
        assertEquals("", t.state)
    }

    @Test
    fun `small text only with the small icon`() {
        assertEquals("", compute(Config(showSmallIcon = false)).smallText)
        assertEquals("Wiedergabe", compute(Config(showSmallIcon = true)).smallText)
    }

    @Test
    fun `fields are limited`() {
        val t = compute(s = snap(t = track.copy(title = "ü".repeat(100))))
        assertEquals("ü".repeat(62) + "…", t.details)
    }

    @Test
    fun `hidden precedence`() {
        val stream = track.copy(isStream = true, path = "http://radio/podcasts/live")
        val all = Config(enabled = false, hideStreams = true, excludePaths = "podcasts", pausedBehavior = Config.PAUSED_HIDE)
        assertEquals(Hidden.DISABLED, compute(all, snap(PlayState.STOPPED, stream)).hidden)
        val enabled = all.copy(enabled = true)
        assertEquals(Hidden.STOPPED, compute(enabled, snap(PlayState.STOPPED, stream)).hidden)
        assertEquals(Hidden.STOPPED, compute(enabled, snap(PlayState.PLAYING, TrackInfo())).hidden)
        assertEquals(Hidden.STREAM, compute(enabled, snap(PlayState.PAUSED, stream)).hidden)
        assertEquals(Hidden.PATH_FILTER, compute(enabled.copy(hideStreams = false), snap(PlayState.PAUSED, stream)).hidden)
        val unfiltered = enabled.copy(hideStreams = false, excludePaths = "")
        assertEquals(Hidden.PAUSED, compute(unfiltered, snap(PlayState.PAUSED, stream)).hidden)
        assertEquals(Hidden.NO, compute(unfiltered, snap(PlayState.PLAYING, stream)).hidden)
        // a stream is only hidden on request
        assertEquals(Hidden.NO, compute(Config(), snap(t = stream)).hidden)
    }

    @Test
    fun `texts are computed also when hidden`() {
        val t = compute(Config(enabled = false))
        assertEquals(Hidden.DISABLED, t.hidden)
        assertEquals("Song", t.details)
        assertEquals("Artist", t.state)
    }

    @Test
    fun `path filter - separated, trimmed, case-insensitive, empty entries ignored`() {
        fun hidden(filter: String, path: String) = compute(Config(excludePaths = filter), snap(t = track.copy(path = path))).hidden
        assertEquals(Hidden.PATH_FILTER, hidden(" ; /Podcasts/ ;  audiobooks ", "/sdcard/AudioBooks/x.mp3"))
        assertEquals(Hidden.PATH_FILTER, hidden("/podcasts/", "/sdcard/PODCASTS/ep1.mp3"))
        assertEquals(Hidden.NO, hidden(" ; ;; ", "/sdcard/Music/x.mp3"))
        assertEquals(Hidden.NO, hidden("audiobooks", "/sdcard/Music/x.mp3"))
        assertEquals(Hidden.NO, hidden("", "/sdcard/Music/x.mp3"))
        // a track without a path is never filtered
        assertEquals(Hidden.NO, hidden("x", ""))
    }

    @Test
    fun `paused timeout`() {
        val c = Config(pausedBehavior = Config.PAUSED_SHOW, clearAfterPausedMin = 5)
        assertEquals(Hidden.NO, compute(c, snap(PlayState.PAUSED), pausedForMs = 5 * 60_000L - 1).hidden)
        assertEquals(Hidden.PAUSED_TIMEOUT, compute(c, snap(PlayState.PAUSED), pausedForMs = 5 * 60_000L).hidden)
        // only while paused, and never with 0 minutes
        assertEquals(Hidden.NO, compute(c, snap(PlayState.PLAYING), pausedForMs = 60 * 60_000L).hidden)
        assertEquals(Hidden.NO, compute(c.copy(clearAfterPausedMin = 0), snap(PlayState.PAUSED), pausedForMs = Long.MAX_VALUE / 2).hidden)
        // hiding paused presences at once wins
        assertEquals(Hidden.PAUSED, compute(c.copy(pausedBehavior = Config.PAUSED_HIDE), snap(PlayState.PAUSED), pausedForMs = 10 * 60_000L).hidden)
    }

    @Test
    fun `title link with URL-encoded values`() {
        val t = compute(s = snap(t = track.copy(artist = "Sigur Rós", title = "Hoppípolla & more")))
        assertEquals("https://www.youtube.com/results?search_query=Sigur%20R%C3%B3s+Hopp%C3%ADpolla%20%26%20more", t.detailsUrl)

        val custom = Config(titleLinkCustom = true, titleLinkUrl = "  https://example.com/s?q=%TITLE%  ")
        assertEquals("https://example.com/s?q=Song", compute(custom).detailsUrl)
        assertEquals("", compute(Config(titleLink = false)).detailsUrl)
        assertEquals("", compute(custom.copy(titleLinkUrl = "ftp://example.com/%title%")).detailsUrl)
        assertEquals("", compute(custom.copy(titleLinkUrl = "")).detailsUrl)
    }

    @Test
    fun `custom title link is made a valid URI`() {
        fun link(template: String, t: TrackInfo = track) =
            compute(Config(titleLinkCustom = true, titleLinkUrl = template), snap(t = t)).detailsUrl

        // literal spaces, quotes and other characters a URI must not contain are encoded, values as before
        assertEquals("https://example.com/search/Artist%20Song", link("https://example.com/search/%artist% %title%"))
        assertEquals("https://x.test/?q=%22Song%22%20%3Cb%3E%7Cc%7C", link("https://x.test/?q=\"%title%\" <b>|c|"))
        assertEquals("https://x.test/s%C3%BCche?q=Sigur%20R%C3%B3s", link("https://x.test/süche?q=%artist%", track.copy(artist = "Sigur Rós")))
        // an unknown placeholder or a lone '%' is not a broken escape
        assertEquals("https://example.com/?q=%25foo%25&t=Song", link("https://example.com/?q=%foo%&t=%title%"))
        assertEquals("https://example.com/100%25", link("https://example.com/100%"))
        // valid escapes, delimiters, an IPv6 host and one fragment stay; '[' ']' elsewhere and a second '#' are encoded
        assertEquals("https://example.com/a%2Fb?x=1&y=(2);z=*,+!$'@:~#top", link("https://example.com/a%2Fb?x=1&y=(2);z=*,+!$'@:~#top"))
        assertEquals("http://[::1]:8080/?q=%5BSong%5D#a%23b", link("http://[::1]:8080/?q=[%title%]#a#b"))
        // what cannot be a URI at all: no link (Discord would reject the whole presence)
        assertEquals("", link("https://"))
        assertEquals("", link("https:///path/%title%"))
        assertEquals("", link("https://[bad/%title%"))
        assertEquals("", link("mailto:%title%"))
    }

    @Test
    fun `normalizeUrl keeps valid URLs as they are`() {
        val valid = listOf(
            "https://www.youtube.com/results?search_query=Sigur%20R%C3%B3s+Hopp%C3%ADpolla",
            "http://example.com",
            "HTTPS://Example.com:443/a/b.html?x=y#frag",
            "https://user@example.com/~me/",
        )
        for (url in valid) assertEquals(url, PresenceTexts.normalizeUrl(url))
        assertEquals("https://example.com/a%20b", PresenceTexts.normalizeUrl("  https://example.com/a b  "))
        assertEquals("", PresenceTexts.normalizeUrl(""))
        assertEquals("", PresenceTexts.normalizeUrl("example.com/x"))
    }

    @Test
    fun `title link falls back to the title alone when too long`() {
        val longArtist = track.copy(artist = "a".repeat(250))
        assertEquals("https://www.youtube.com/results?search_query=+Song", compute(s = snap(t = longArtist)).detailsUrl)

        // 45 characters of base URL + 1 + 210 = 256: still fits
        val fits = track.copy(artist = "", title = "t".repeat(210))
        assertEquals(256, compute(s = snap(t = fits)).detailsUrl.length)
        val tooLong = track.copy(artist = "", title = "t".repeat(211))
        assertEquals("", compute(s = snap(t = tooLong)).detailsUrl)
    }

    @Test
    fun `no title link without a details line`() {
        val t = compute(Config(details = "", state = "%artist%"))
        assertEquals("", t.details)
        assertEquals("", t.detailsUrl)
    }
}
