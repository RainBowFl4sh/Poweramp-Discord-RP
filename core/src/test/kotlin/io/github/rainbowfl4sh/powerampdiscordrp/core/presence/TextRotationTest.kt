package io.github.rainbowfl4sh.powerampdiscordrp.core.presence

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextRotationTest {
    private val track = TrackInfo(
        id = 7, realId = 70, title = "Song", artist = "Artist", album = "Album",
        path = "/storage/emulated/0/Music/Artist - Song.FLAC", durationMs = 200_000,
        posInList = 4, listSize = 12, sampleRate = 44_100, channels = 2, bitsPerSample = 24, bitRate = 1_000,
        codec = "FLAC", rating = 4,
    )
    private val strings = StatusStrings(playing = "Wiedergabe", paused = "Pausiert")

    /** Playing since [since] (the rotation's start), position 0 at that time. */
    private fun snap(t: TrackInfo = track, since: Long = 0, state: PlayState = PlayState.PLAYING) =
        PlaybackSnapshot(state, t, positionMs = 0, stampMs = since, trackSinceMs = since)

    private fun compute(c: Config, nowMs: Long, s: PlaybackSnapshot = snap()) = PresenceTexts.compute(c, s, nowMs, 0, strings)

    /** Rotation every 5 s. */
    private fun rotating(
        details: String = "%title%",
        state: String = "%artist%",
        largeText: String = "%album%",
        smallText: String = "%status%",
    ) = Config(details = details, state = state, largeText = largeText, smallText = smallText, rotationSeconds = 5)

    // ------------------------------------------------------------------------------------------- helpers

    @Test
    fun `alternatives are split at the separator only`() {
        assertEquals(listOf("%artist% ", " %title%"), TextRotation.alternatives("%artist% || %title%"))
        assertEquals(listOf("a", "b", "c"), TextRotation.alternatives("a||b||c"))
        assertEquals(listOf("%title%"), TextRotation.alternatives("%title%"))
        assertEquals(listOf("a | b"), TextRotation.alternatives("a | b"))
        assertEquals(listOf(""), TextRotation.alternatives(""))
        assertEquals(listOf("a ", "| b"), TextRotation.alternatives("a ||| b"))   // a third '|' is text
        assertEquals(listOf("", ""), TextRotation.alternatives("||"))
    }

    @Test
    fun `a template rotates with two alternatives that are not blank`() {
        assertTrue(TextRotation.rotates("%artist% || %title%"))
        assertTrue(TextRotation.rotates("a || || b"))
        assertTrue(TextRotation.rotates("%album%||%title%"))   // may come out empty for a track, still a rotating template
        assertFalse(TextRotation.rotates("%title%"))
        assertFalse(TextRotation.rotates("%artist% | %title%"))
        assertFalse(TextRotation.rotates("%title% ||"))
        assertFalse(TextRotation.rotates(" || %title% ||  "))
        assertFalse(TextRotation.rotates("||"))
        assertFalse(TextRotation.rotates(""))
    }

    @Test
    fun `steps count from the start of the track`() {
        assertEquals(0, TextRotation.step(1_000, 1_000, 5_000))
        assertEquals(0, TextRotation.step(1_000, 5_999, 5_000))
        assertEquals(1, TextRotation.step(1_000, 6_000, 5_000))
        assertEquals(7, TextRotation.step(1_000, 36_000, 5_000))
        // a clock read before the start (the preview's tick may lag behind the engine): still the first step
        assertEquals(0, TextRotation.step(10_000, 9_000, 5_000))
        assertEquals(6_000, TextRotation.nextStepAt(1_000, 1_000, 5_000))
        assertEquals(11_000, TextRotation.nextStepAt(1_000, 6_000, 5_000))
        assertEquals(15_000, TextRotation.nextStepAt(10_000, 9_000, 5_000))
    }

    @Test
    fun `pick skips empty values`() {
        assertEquals("a", TextRotation.pick(listOf("a", "", "b"), 0))
        assertEquals("b", TextRotation.pick(listOf("a", "", "b"), 1))
        assertEquals("a", TextRotation.pick(listOf("a", "", "b"), 2))
        assertEquals("", TextRotation.pick(listOf("", ""), 3))
        assertEquals("", TextRotation.pick(emptyList(), 0))
    }

    @Test
    fun `interval is clamped to 5 to 60 seconds`() {
        assertEquals(10, Config().rotationSeconds)
        assertEquals(10_000, TextRotation.intervalMs(Config()))
        assertEquals(5_000, TextRotation.intervalMs(Config(rotationSeconds = 1)))
        assertEquals(5_000, TextRotation.intervalMs(Config(rotationSeconds = -3)))
        assertEquals(60_000, TextRotation.intervalMs(Config(rotationSeconds = 1_000)))
        assertEquals(25_000, TextRotation.intervalMs(Config(rotationSeconds = 25)))

        // used that way by compute: 1 s counts as 5 s
        val c = rotating(details = "%title% || %artist%").copy(rotationSeconds = 1)
        assertEquals("Song", compute(c, 4_999).details)
        assertEquals("Artist", compute(c, 5_000).details)
        val slow = c.copy(rotationSeconds = 600)
        assertEquals("Song", compute(slow, 59_999).details)
        assertEquals("Artist", compute(slow, 60_000).details)
    }

    @Test
    fun `needsRotation - a shown template with two alternatives`() {
        assertFalse(PresenceTexts.needsRotation(Config()))
        assertTrue(PresenceTexts.needsRotation(Config(details = "%title% || %album%")))
        assertTrue(PresenceTexts.needsRotation(Config(state = "%artist%||%album%")))
        assertTrue(PresenceTexts.needsRotation(Config(largeText = "%album% || %codec%")))
        assertTrue(PresenceTexts.needsRotation(Config(smallText = "%status% || %bitrate%")))
        // the small text is not shown without the small icon
        assertFalse(PresenceTexts.needsRotation(Config(smallText = "%status% || %bitrate%", showSmallIcon = false)))
        assertFalse(PresenceTexts.needsRotation(Config(details = "%artist% | %title%", state = "%title% ||")))
        // the title link is never rotated
        assertFalse(PresenceTexts.needsRotation(Config(titleLinkCustom = true, titleLinkUrl = "https://x.test/?a=1||b")))
    }

    // ------------------------------------------------------------------------------------------- compute

    @Test
    fun `alternatives take turns every interval`() {
        val c = rotating(details = "%artist% || %title%")
        assertEquals("Artist", compute(c, 0).details)
        assertEquals("Artist", compute(c, 4_999).details)
        assertEquals("Song", compute(c, 5_000).details)
        assertEquals("Song", compute(c, 9_999).details)
        assertEquals("Artist", compute(c, 10_000).details)
        assertEquals("Song", compute(c, 15_000).details)
        // three alternatives
        val three = rotating(state = "%artist%||%album%||%codec%")
        assertEquals(listOf("Artist", "Album", "FLAC", "Artist"), (0..3).map { compute(three, it * 5_000L).state })
    }

    @Test
    fun `every field rotates with the same step - in lock-step`() {
        val c = rotating(
            details = "%title% || %album%",
            state = "%artist% || %codec%",
            largeText = "%album%",
            smallText = "%status% || %bits% || %samplerate%",
        )
        val first = compute(c, 1_000)
        assertEquals(listOf("Song", "Artist", "Album", "Wiedergabe"), listOf(first.details, first.state, first.largeText, first.smallText))
        val second = compute(c, 6_000)
        assertEquals(listOf("Album", "FLAC", "Album", "24-bit"), listOf(second.details, second.state, second.largeText, second.smallText))
        val third = compute(c, 11_000)
        assertEquals(listOf("Song", "Artist", "Album", "44.1 kHz"), listOf(third.details, third.state, third.largeText, third.smallText))

        // only the second line changes
        val one = rotating(details = "%title%", state = "%artist% || %album%")
        assertEquals("Song" to "Artist", compute(one, 0).let { it.details to it.state })
        assertEquals("Song" to "Album", compute(one, 5_000).let { it.details to it.state })
    }

    @Test
    fun `all fields change at the same moments - the same number of alternatives keeps them paired`() {
        fun lines(c: Config) = (0..5).map { k -> compute(c, k * 5_000L).let { it.details to it.state } }

        // 2 and 2: the title always comes with the artist, the album with the codec
        val paired = rotating(details = "%title% || %album%", state = "%artist% || %codec%")
        assertEquals(List(3) { listOf("Song" to "Artist", "Album" to "FLAC") }.flatten(), lines(paired))

        // 2 and 3: still both lines change at every step (not at different moments), only the pairs drift
        val drifting = rotating(details = "%title% || %album%", state = "%artist% || %codec% || %bits%")
        val shown = lines(drifting)
        assertEquals(
            listOf("Song" to "Artist", "Album" to "FLAC", "Song" to "24-bit", "Album" to "Artist", "Song" to "FLAC", "Album" to "24-bit"),
            shown,
        )
        for ((before, after) in shown.zipWithNext()) {
            assertTrue("$before -> $after", before.first != after.first && before.second != after.second)
        }
        // in between the steps nothing changes
        assertEquals(shown[1], compute(drifting, 9_999).let { it.details to it.state })
    }

    @Test
    fun `alternatives that come out empty are skipped`() {
        val noAlbum = track.copy(album = "")
        val c = rotating(details = "%album% || %title%")
        for (now in listOf(0L, 5_000L, 10_000L, 15_000L)) assertEquals("Song", compute(c, now, snap(noAlbum)).details)

        val three = rotating(details = "%album% || %title% || %artist%")
        assertEquals(listOf("Song", "Artist", "Song"), (0..2).map { compute(three, it * 5_000L, snap(noAlbum)).details })
        // with the tag the album takes its turn again
        assertEquals(listOf("Album", "Song", "Artist"), (0..2).map { compute(three, it * 5_000L).details })

        // blank alternatives (an extra separator, spaces) never get a turn
        val extra = rotating(details = "%title% ||  || %artist% ||")
        assertEquals(listOf("Song", "Artist", "Song"), (0..2).map { compute(extra, it * 5_000L).details })
        // all empty: no line, like an empty template
        assertEquals("", compute(rotating(largeText = "%album% || %codec%"), 5_000, snap(noAlbum.copy(codec = ""))).largeText)
    }

    @Test
    fun `each alternative is a Discord field on its own`() {
        val long = track.copy(title = "ü".repeat(100))
        val c = rotating(details = "%title% || x")
        assertEquals("ü".repeat(62) + "…", compute(c, 0, snap(long)).details)
        assertEquals("x ", compute(c, 5_000, snap(long)).details)   // padded to 2 characters
        // trimmed
        assertEquals("Artist", compute(rotating(state = "%title%   ||   %artist%   "), 5_000).state)
    }

    @Test
    fun `the rotation starts with the first alternative at the track's start`() {
        val c = rotating(details = "%title% || %artist%")
        val since = 100_000L
        assertEquals("Song", compute(c, since, snap(since = since)).details)
        assertEquals("Song", compute(c, since + 4_999, snap(since = since)).details)
        assertEquals("Artist", compute(c, since + 5_000, snap(since = since)).details)
        // a clock read just before the start: the first alternative, not the last
        assertEquals("Song", compute(c, since - 700, snap(since = since)).details)
        // the position or a pause does not matter, only the time since the start
        val paused = PlaybackSnapshot(PlayState.PAUSED, track, positionMs = 150_000, stampMs = since + 1_000, trackSinceMs = since)
        val p = Config(details = "%title% || %artist%", rotationSeconds = 5, pausedBehavior = Config.PAUSED_SHOW)
        assertEquals("Artist", PresenceTexts.compute(p, paused, since + 7_000, 6_000, strings).details)
        assertEquals("Song", PresenceTexts.compute(p, paused, since + 12_000, 11_000, strings).details)
    }

    @Test
    fun `texts without the separator are exactly as before`() {
        val templates = listOf(
            "%title%", "%artist% | %title%", "|%album%|", "%pos% / %dur% %bar%", "", "  ", "x", "100%%title%", "%unknown% | %status%",
        )
        val tracks = listOf(track, track.copy(album = "", artist = ""), track.copy(title = "ü".repeat(100)))
        for (t in tracks) {
            for (template in templates) {
                val c = Config(details = template, state = template, largeText = template, smallText = template, rotationSeconds = 5)
                val before = expected(c, t, nowMs = 0)
                for (now in listOf(0L, 5_000L, 10_000L, 123_456L)) {
                    val texts = compute(c, now, snap(t))
                    val want = expected(c, t, nowMs = now)
                    assertEquals("\"$template\" at $now", want, listOf(texts.details, texts.state, texts.largeText, texts.smallText))
                }
                // only the position may change with the time, here it does not move the texts of "|%album%|" etc.
                if ("%pos%" !in template && "%bar%" !in template) {
                    val later = compute(c, 10_000, snap(t))
                    assertEquals(before, listOf(later.details, later.state, later.largeText, later.smallText))
                }
            }
        }
        // a value containing "||" is not split
        val odd = track.copy(artist = "A || B")
        assertEquals("A || B", compute(Config(state = "%artist%"), 5_000, snap(odd)).state)
        assertEquals("A || B", compute(rotating(state = "%artist% || %album%"), 0, snap(odd)).state)
        assertEquals("Album", compute(rotating(state = "%artist% || %album%"), 5_000, snap(odd)).state)
    }

    /** The texts as computed before rotation existed: every template expanded and made a field. */
    private fun expected(c: Config, t: TrackInfo, nowMs: Long): List<String> {
        val durMs = t.durationMs
        val vars = PresenceTexts.variables(c, t, playing = true, posMs = minOf(nowMs, durMs), durMs = durMs, strings = strings)
        var details = Templates.field(Templates.expand(c.details, vars))
        val state = Templates.field(Templates.expand(c.state, vars))
        if (details.isEmpty() && state.isEmpty()) details = Templates.field(vars.getValue("filename"))
        return listOf(details, state, Templates.field(Templates.expand(c.largeText, vars)), Templates.field(Templates.expand(c.smallText, vars)))
    }

    @Test
    fun `the file name stands in when both lines are empty at a step`() {
        val bare = track.copy(album = "", codec = "")
        // no alternative of either line has a text: the file name
        val none = rotating(details = "", state = "%album% || %codec%")
        assertEquals("Artist - Song", compute(none, 0, snap(bare)).details)
        assertEquals("Artist - Song", compute(none, 5_000, snap(bare)).details)
        assertEquals("", compute(none, 5_000, snap(bare)).state)
        // one alternative has a text: it is shown at every step, the file name is not needed
        val one = rotating(details = "", state = "%album% || %codec%")
        val withCodec = track.copy(album = "")
        assertEquals("" to "FLAC", compute(one, 0, snap(withCodec)).let { it.details to it.state })
        assertEquals("" to "FLAC", compute(one, 5_000, snap(withCodec)).let { it.details to it.state })
        // the details line empty for this track, the state line rotates on
        val split = rotating(details = "%album% || %codec%", state = "%artist% || %title%")
        assertEquals("" to "Artist", compute(split, 0, snap(bare)).let { it.details to it.state })
        assertEquals("" to "Song", compute(split, 5_000, snap(bare)).let { it.details to it.state })
    }

    @Test
    fun `the title link is not rotated`() {
        val c = rotating(details = "%artist% || %title%")
        val first = compute(c, 0)
        val second = compute(c, 5_000)
        assertEquals("Artist", first.details)
        assertEquals("Song", second.details)
        assertEquals("https://www.youtube.com/results?search_query=Artist+Song", first.detailsUrl)
        assertEquals(first.detailsUrl, second.detailsUrl)
        // a link template containing "||" is a link, not alternatives
        val custom = c.copy(titleLinkCustom = true, titleLinkUrl = "https://x.test/?q=%title%||%artist%")
        assertEquals(compute(custom, 0).detailsUrl, compute(custom, 5_000).detailsUrl)
        assertEquals("https://x.test/?q=Song%7C%7CArtist", compute(custom, 5_000).detailsUrl)
    }

    @Test
    fun `the small text rotates only with the small icon`() {
        val c = rotating(smallText = "%status% || %codec%")
        assertEquals("FLAC", compute(c, 5_000).smallText)
        assertEquals("", compute(c.copy(showSmallIcon = false), 5_000).smallText)
    }
}
