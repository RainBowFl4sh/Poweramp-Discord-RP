package io.github.rainbowfl4sh.powerampdiscordrp.ui.preview

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.ActivityTexts
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.Hidden
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.StatusStrings
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.DiscordUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewModelTest {
    private val example = ExampleTexts("Example title", "Example artist", "Example album")
    private val strings = StatusStrings(playing = "Playing", paused = "Paused")

    /** What the preview handed to PresenceTexts.compute (the real one belongs to the core module). */
    private class Call(val c: Config, val s: PlaybackSnapshot, val now: Long, val pausedFor: Long)

    private var call: Call? = null

    /** Fake PresenceTexts.compute: details = title, state = artist, playing / position from the snapshot. */
    private fun fake(hidden: Hidden = Hidden.NO): TextsComputer = { c, s, now, pausedFor, _ ->
        call = Call(c, s, now, pausedFor)
        ActivityTexts(
            hidden = hidden,
            playing = s.state == PlayState.PLAYING,
            posMs = s.positionMs,
            durMs = s.track.durationMs,
            details = s.track.title,
            state = s.track.artist,
            largeText = s.track.album,
            smallText = "Playing",
        )
    }

    private val track = TrackInfo(title = "Song", artist = "Band", album = "Record", path = "/m/Band - Song.mp3", durationMs = 200_000)

    @Test
    fun nothingPlayingShowsTheExampleTrack() {
        val p = PreviewModel.build(Config(), PlaybackSnapshot(), nowMs = 10_000, example, strings, fake())
        assertTrue(p.example)
        val s = call!!.s
        assertEquals(PlayState.PLAYING, s.state)
        assertEquals("Example title", s.track.title)
        assertEquals("Example artist", s.track.artist)
        assertEquals("Example album", s.track.album)
        assertEquals(PreviewModel.EXAMPLE_POS_MS, s.positionMs)
        assertEquals(PreviewModel.EXAMPLE_DUR_MS, s.track.durationMs)
        assertEquals(10_000, s.stampMs)   // frozen at 1:23
        assertEquals(0, call!!.pausedFor)
        assertTrue(p.progress)
    }

    @Test
    fun aTrackWithoutTitleAndPathCountsAsNothing() {
        val p = PreviewModel.build(Config(), PlaybackSnapshot(PlayState.PLAYING, TrackInfo()), 0, example, strings, fake())
        assertTrue(p.example)
    }

    @Test
    fun theLiveTrackIsShownWithTheCurrentEdits() {
        val edits = Config(details = "%artist% - %title%")
        val live = PlaybackSnapshot(PlayState.PLAYING, track, positionMs = 5_000, stampMs = 1_000)
        val p = PreviewModel.build(edits, live, nowMs = 3_000, example, strings, fake())
        assertFalse(p.example)
        assertEquals(edits, call!!.c)
        assertEquals(live, call!!.s)
        assertEquals(3_000, call!!.now)
        assertEquals("Song", p.texts.details)
    }

    @Test
    fun pausedForIsTheTimeSinceThePause() {
        val live = PlaybackSnapshot(PlayState.PAUSED, track, positionMs = 5_000, stampMs = 1_000)
        val p = PreviewModel.build(Config(pausedBehavior = Config.PAUSED_SHOW), live, nowMs = 61_000, example, strings, fake())
        assertEquals(60_000, call!!.pausedFor)
        assertFalse(p.progress)   // Discord shows no times while paused
        assertFalse(p.elapsed)
        // a stamp from the future (clock oddities) is no negative pause
        PreviewModel.build(Config(), live.copy(stampMs = 99_000), nowMs = 61_000, example, strings, fake())
        assertEquals(0, call!!.pausedFor)
    }

    @Test
    fun pausedTimeCountsLikeTheEngine() {
        // the engine counts the paused timeout from the paused snapshot's stamp as well (PresenceEngine.submit)
        val paused = PlaybackSnapshot(PlayState.PAUSED, track, positionMs = 5_000, stampMs = 1_000)
        assertEquals(300_000, PreviewModel.pausedForMs(paused, 301_000))
        // a seek / track change while paused re-stamps the snapshot (PowerampState): the time starts again for both
        assertEquals(0, PreviewModel.pausedForMs(paused.copy(positionMs = 9_000, stampMs = 301_000), 301_000))
        // a pause heard of late (sticky broadcast after a cold start) has been running for a while already
        assertEquals(1_800_000, PreviewModel.pausedForMs(paused.copy(stampMs = 200_000), 2_000_000))
        // without a stamp the engine counts from when it heard of the pause: not "timed out" at once
        assertEquals(0, PreviewModel.pausedForMs(paused.copy(stampMs = 0), 2_000_000))
        assertEquals(0, PreviewModel.pausedForMs(paused.copy(state = PlayState.PLAYING), 301_000))
        PreviewModel.build(Config(pausedBehavior = Config.PAUSED_SHOW), paused.copy(stampMs = 0), 2_000_000, example, strings, fake())
        assertEquals(0, call!!.pausedFor)
    }

    @Test
    fun hiddenKeepsTheReason() {
        val p = PreviewModel.build(Config(), PlaybackSnapshot(PlayState.PAUSED, track), 0, example, strings, fake(Hidden.PAUSED))
        assertTrue(p.hidden)
        assertEquals(Hidden.PAUSED, p.texts.hidden)
    }

    @Test
    fun listeningWithTimestampsShowsTheBar() {
        val live = PlaybackSnapshot(PlayState.PLAYING, track)
        val p = PreviewModel.build(Config(), live, 0, example, strings, fake())
        assertTrue(p.listening)
        assertTrue(p.progress)
        assertFalse(p.elapsed)
    }

    @Test
    fun playingTypeShowsElapsedTime() {
        val live = PlaybackSnapshot(PlayState.PLAYING, track)
        val p = PreviewModel.build(Config(activityType = Config.ACTIVITY_PLAYING), live, 0, example, strings, fake())
        assertFalse(p.listening)
        assertFalse(p.progress)
        assertTrue(p.elapsed)
    }

    @Test
    fun unknownDurationShowsElapsedTime() {
        val live = PlaybackSnapshot(PlayState.PLAYING, track.copy(durationMs = 0, isStream = true))
        val p = PreviewModel.build(Config(), live, 0, example, strings, fake())
        assertFalse(p.progress)
        assertTrue(p.elapsed)
    }

    @Test
    fun noTimestamps() {
        val live = PlaybackSnapshot(PlayState.PLAYING, track)
        val p = PreviewModel.build(Config(showTimestamps = false), live, 0, example, strings, fake())
        assertFalse(p.progress)
        assertFalse(p.elapsed)
    }

    @Test
    fun memberListLine() {
        val live = PlaybackSnapshot(PlayState.PLAYING, track)
        fun line(display: Int) = PreviewModel.build(Config(statusDisplay = display), live, 0, example, strings, fake())
        assertEquals(MemberLine.STATE to "Band", line(Config.STATUS_DISPLAY_STATE).let { it.memberLine to it.memberText })
        assertEquals(MemberLine.DETAILS to "Song", line(Config.STATUS_DISPLAY_DETAILS).let { it.memberLine to it.memberText })
        assertEquals(MemberLine.APP_NAME to "", line(Config.STATUS_DISPLAY_NAME).let { it.memberLine to it.memberText })
        // an empty state line: Discord falls back to the application's name
        val noArtist = PreviewModel.build(Config(), live.copy(track = track.copy(artist = "")), 0, example, strings, fake())
        assertEquals("", noArtist.memberText)
    }

    @Test
    fun aSecondOfPlayingOnlyMovesThePosition() {
        // the real PresenceTexts: what the preview redraws once a second
        val live = PlaybackSnapshot(PlayState.PLAYING, track, positionMs = 5_000, stampMs = 1_000)
        val before = PreviewModel.build(Config(), live, nowMs = 3_000, example, strings)
        val after = PreviewModel.build(Config(), live, nowMs = 4_000, example, strings)
        assertEquals(7_000, before.texts.posMs)
        assertEquals(8_000, after.texts.posMs)
        assertEquals(PreviewModel.withoutPosition(before), PreviewModel.withoutPosition(after))
        assertEquals(0, PreviewModel.withoutPosition(after).texts.posMs)
        assertEquals(after.copy(texts = after.texts.copy(posMs = 0)), PreviewModel.withoutPosition(after))
    }

    @Test
    fun templatesWithThePositionChangeTheTextsEverySecond() {
        val live = PlaybackSnapshot(PlayState.PLAYING, track, positionMs = 5_000, stampMs = 1_000)
        val pos = Config(state = "%pos% / %dur%")
        val before = PreviewModel.withoutPosition(PreviewModel.build(pos, live, nowMs = 3_000, example, strings))
        val after = PreviewModel.withoutPosition(PreviewModel.build(pos, live, nowMs = 4_000, example, strings))
        assertEquals("0:07 / 3:20", before.texts.state)
        assertEquals("0:08 / 3:20", after.texts.state)
        assertFalse(before == after)
    }

    @Test
    fun theExampleTrackStandsStill() {
        val first = PreviewModel.build(Config(), PlaybackSnapshot(), nowMs = 3_000, example, strings)
        val later = PreviewModel.build(Config(), PlaybackSnapshot(), nowMs = 63_000, example, strings)
        assertEquals(first, later)
        assertEquals(PreviewModel.EXAMPLE_POS_MS, later.texts.posMs)
    }

    @Test
    fun rotatingTextsTakeTurnsFromWhenTheEngineSawTheTrack() {
        // the real PresenceTexts with the engine's snapshot: the same alternative as Discord gets
        val c = Config(details = "%title%", state = "%artist% || %album%", rotationSeconds = 5)
        val live = PlaybackSnapshot(PlayState.PLAYING, track, positionMs = 5_000, stampMs = 1_000, trackSinceMs = 2_000)
        fun state(now: Long) = PreviewModel.build(c, live, now, example, strings).texts.state
        assertEquals("Band", state(2_000))
        assertEquals("Band", state(6_999))
        assertEquals("Record", state(7_000))
        assertEquals("Band", state(12_000))
        // the preview's clock may lag the engine's anchor a little: the first alternative, not the last one
        assertEquals("Band", state(1_500))
        // the first line has no "||" and stays
        assertEquals("Song", PreviewModel.build(c, live, 7_000, example, strings).texts.details)
        // another anchor (a new track) starts with the first alternative again
        assertEquals("Band", PreviewModel.build(c, live.copy(trackSinceMs = 7_000), 7_000, example, strings).texts.state)
    }

    @Test
    fun rotationFollowsTheEditedInterval() {
        val live = PlaybackSnapshot(PlayState.PLAYING, track, stampMs = 0, trackSinceMs = 0)
        fun details(seconds: Int, now: Long) =
            PreviewModel.build(Config(details = "%title% || %artist%", rotationSeconds = seconds), live, now, example, strings).texts.details
        assertEquals("Band", details(10, 10_000))
        assertEquals("Song", details(20, 10_000))
        // outside 5..60 (an edited file): clamped like the engine does it
        assertEquals("Band", details(1, 5_000))
        assertEquals("Song", details(1, 4_999))
        assertEquals("Song", details(600, 59_999))
        assertEquals("Band", details(600, 60_000))
    }

    @Test
    fun theExampleTrackRotatesOnTheClock() {
        // built anew every second with the stamp "now": its anchor is fixed, so the alternatives still take turns
        val c = Config(details = "%title% || %artist%", largeText = "%album% || %codec%", rotationSeconds = 5)
        val first = PreviewModel.build(c, PlaybackSnapshot(), nowMs = 3_000, example, strings)
        assertTrue(first.example)
        assertEquals(PreviewModel.EXAMPLE_TRACK_SINCE_MS, exampleSnapshotAt(c, 3_000).trackSinceMs)
        assertEquals("Example title", first.texts.details)
        assertEquals("Example album", first.texts.largeText)
        val second = PreviewModel.build(c, PlaybackSnapshot(), nowMs = 8_000, example, strings)
        assertEquals("Example artist", second.texts.details)
        assertEquals("FLAC", second.texts.largeText)   // lock-step with the first line
        assertEquals(first, PreviewModel.build(c, PlaybackSnapshot(), nowMs = 13_000, example, strings))
        // the example does not move on: within a step nothing changes
        assertEquals(second, PreviewModel.build(c, PlaybackSnapshot(), nowMs = 9_999, example, strings))
    }

    /** The snapshot the preview computes the example with at [now]. */
    private fun exampleSnapshotAt(c: Config, now: Long): PlaybackSnapshot {
        PreviewModel.build(c, PlaybackSnapshot(), now, example, strings, fake())
        return call!!.s
    }

    @Test
    fun theEnginesAnchorReachesTheTexts() {
        val live = PlaybackSnapshot(PlayState.PAUSED, track, positionMs = 5_000, stampMs = 1_000, trackSinceMs = 400)
        PreviewModel.build(Config(), live, nowMs = 3_000, example, strings, fake())
        assertEquals(400, call!!.s.trackSinceMs)
    }

    @Test
    fun progressFraction() {
        assertEquals(0f, PreviewModel.fraction(10, 0))
        assertEquals(0.5f, PreviewModel.fraction(50, 100))
        assertEquals(1f, PreviewModel.fraction(150, 100))
        assertEquals(0f, PreviewModel.fraction(-5, 100))
    }

    @Test
    fun avatarLinks() {
        assertEquals(
            "https://cdn.discordapp.com/avatars/80351110224678912/a_1f2e3d.png?size=64",
            avatarUrl(DiscordUser(id = "80351110224678912", avatar = "a_1f2e3d")),
        )
        assertEquals("", avatarUrl(null))
        assertEquals("", avatarUrl(DiscordUser(id = "80351110224678912", avatar = "")))
        assertEquals("", avatarUrl(DiscordUser(id = "x/../y", avatar = "abc")))
        assertEquals("", avatarUrl(DiscordUser(id = "1", avatar = "../../evil")))
    }
}
