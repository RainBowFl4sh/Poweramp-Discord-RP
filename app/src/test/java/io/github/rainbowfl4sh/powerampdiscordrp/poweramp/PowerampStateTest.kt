package io.github.rainbowfl4sh.powerampdiscordrp.poweramp

import android.os.Bundle
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PowerampStateTest {
    private class FakeClock(var now: Long, var wall: Long) : Clock {
        override fun nowMs() = now
        override fun wallMs() = wall
        fun advance(ms: Long) {
            now += ms
            wall += ms
        }
    }

    private val wall0 = 1_700_000_000_000L
    private val clock = FakeClock(now = 500_000, wall = wall0)
    private val state = PowerampState(clock)

    private fun track(title: String = "Song", durMs: Int = 200_000) = Bundle().apply {
        putLong("realId", 5)
        putString("title", title)
        putString("artist", "Artist")
        putString("path", "/music/$title.mp3")
        putInt("durMs", durMs)
    }

    private fun trackChanged(title: String = "Song", pos: Int? = 0, ts: Long? = clock.wall, durMs: Int = 200_000) =
        Bundle(track(title, durMs)).apply {
            putBundle("track", track(title, durMs))
            putInt("api", 700)
            pos?.let { putInt("pos", it) }
            ts?.let { putLong("ts", it) }
        }

    private fun status(state: Int, pos: Int? = null, ts: Long? = clock.wall) = Bundle().apply {
        putInt("api", 700)
        putInt("state", state)
        putInt("status", if (state == 1) 1 else if (state == 2) 2 else 3)
        putBoolean("paused", state != 1)
        pos?.let { putInt("pos", it) }
        ts?.let { putLong("ts", it) }
    }

    @Test
    fun trackChangedSetsTrackAndPositionInMilliseconds() {
        val update = state.onTrackChanged(trackChanged(pos = 12))!!
        assertEquals("Song", update.track.title)
        assertEquals(12_000L, update.positionMs)
        assertEquals(clock.now, update.stampMs)
        assertEquals(PlayState.STOPPED, update.state)   // TRACK_CHANGED carries no play state
    }

    @Test
    fun stampIsCorrectedByTheBroadcastTimestamp() {
        // Sent 3 s ago (cold start of the process): the position was valid back then
        val update = state.onTrackChanged(trackChanged(pos = 40, ts = clock.wall - 3_000))!!
        assertEquals(clock.now - 3_000, update.stampMs)
        assertEquals(40_000L, update.positionMs)
    }

    @Test
    fun timestampFromTheFutureDoesNotMoveTheStampForward() {
        val update = state.onTrackChanged(trackChanged(ts = clock.wall + 5_000))!!
        assertEquals(clock.now, update.stampMs)
    }

    @Test
    fun missingTimestampMeansNow() {
        val update = state.onTrackChanged(trackChanged(ts = null))!!
        assertEquals(clock.now, update.stampMs)
    }

    @Test
    fun statusKeepsTheTrack() {
        state.onTrackChanged(trackChanged())
        clock.advance(100)
        val update = state.onStatusChanged(status(1, pos = 0))!!
        assertEquals(PlayState.PLAYING, update.state)
        assertEquals("Song", update.track.title)
    }

    @Test
    fun trackChangeKeepsThePlayState() {
        state.onTrackChanged(trackChanged())
        clock.advance(10)
        state.onStatusChanged(status(1, pos = 0))
        clock.advance(10_000)
        val next = state.onTrackChanged(trackChanged(title = "Next", pos = 0))!!
        assertEquals(PlayState.PLAYING, next.state)
        assertEquals("Next", next.track.title)
        assertEquals(0L, next.positionMs)
        assertEquals(clock.now, next.stampMs)
    }

    @Test
    fun sameTrackAgainWithoutChangeIsNoUpdate() {
        state.onTrackChanged(trackChanged(pos = 3, ts = null))
        // Same content, no timestamp, same stamp: nothing new
        assertNull(state.onTrackChanged(trackChanged(pos = 3, ts = null)))
        clock.advance(1_000)
        assertFalse(state.onTrackChanged(trackChanged(pos = 4, ts = null)) == null)
    }

    @Test
    fun statusWithoutPositionAdvancesThePlayingPosition() {
        state.onTrackChanged(trackChanged(pos = 10))
        state.onStatusChanged(status(1, pos = 10))
        clock.advance(7_000)
        val paused = state.onStatusChanged(status(2, pos = null))!!
        assertEquals(PlayState.PAUSED, paused.state)
        assertEquals(17_000L, paused.positionMs)
        assertEquals(clock.now, paused.stampMs)

        // While paused the position stands still
        clock.advance(60_000)
        val stopped = state.onStatusChanged(status(0, pos = null))!!
        assertEquals(17_000L, stopped.positionMs)
    }

    @Test
    fun positionSyncAfterSeek() {
        state.onTrackChanged(trackChanged(pos = 0))
        clock.advance(2_000)
        val update = state.onPositionSync(Bundle().apply {
            putInt("api", 700)
            putInt("pos", 95)
        })!!
        assertEquals(95_000L, update.positionMs)
        assertEquals(clock.now, update.stampMs)
        assertNull(state.onPositionSync(Bundle().apply { putInt("api", 700) }))
    }

    @Test
    fun positionIsClampedToTheDuration() {
        val update = state.onTrackChanged(trackChanged(pos = 500, durMs = 200_000))!!
        assertEquals(200_000L, update.positionMs)
    }

    @Test
    fun olderReplayIsIgnored() {
        state.onStatusChanged(status(1, pos = 5, ts = wall0 + 10_000))
        // The sticky value read later is older than what the explicit broadcast already told
        assertNull(state.onStatusChanged(status(2, pos = 1, ts = wall0 + 5_000), replay = true))
        assertEquals(PlayState.PLAYING, state.snapshot().state)
        // A newer sticky value is applied
        assertNotNull(state.onStatusChanged(status(2, pos = 6, ts = wall0 + 11_000), replay = true))
        assertEquals(PlayState.PAUSED, state.snapshot().state)
    }

    @Test
    fun duplicateOfTheSameEventIsIgnored() {
        // TRACK_CHANGED and TRACK_CHANGED_EXPLICIT carry the same "ts"
        assertNotNull(state.onTrackChanged(trackChanged(ts = wall0)))
        clock.advance(50)
        assertNull(state.onTrackChanged(trackChanged(ts = wall0)))
    }

    @Test
    fun olderLiveEventIsALateCopy() {
        // Skipping A -> B -> C: the sticky copies came at once, the explicit copies of the manifest receiver come later
        assertNotNull(state.onTrackChanged(trackChanged(title = "A", ts = wall0 + 1_000)))
        assertNotNull(state.onTrackChanged(trackChanged(title = "B", ts = wall0 + 1_400)))
        assertNotNull(state.onTrackChanged(trackChanged(title = "C", ts = wall0 + 1_800)))
        clock.advance(3_000)
        assertNull(state.onTrackChanged(trackChanged(title = "A", ts = wall0 + 1_000)))
        assertNull(state.onTrackChanged(trackChanged(title = "B", ts = wall0 + 1_400)))
        assertNull(state.onTrackChanged(trackChanged(title = "C", ts = wall0 + 1_800)))
        assertEquals("C", state.snapshot().track.title)

        // Same for the play state: a quick pause / resume does not come back as a pause
        assertNotNull(state.onStatusChanged(status(2, pos = 1, ts = wall0 + 2_000)))
        assertNotNull(state.onStatusChanged(status(1, pos = 1, ts = wall0 + 2_300)))
        assertNull(state.onStatusChanged(status(2, pos = 1, ts = wall0 + 2_000)))
        assertEquals(PlayState.PLAYING, state.snapshot().state)
    }

    @Test
    fun muchOlderLiveEventMeansTheClockWasSetBack() {
        assertNotNull(state.onStatusChanged(status(1, pos = 0, ts = wall0)))
        // The user set the clock back by an hour: Poweramp's next events are "older", but new
        assertNotNull(state.onStatusChanged(status(2, pos = 5, ts = wall0 - 3_600_000)))
        assertEquals(PlayState.PAUSED, state.snapshot().state)
        assertNotNull(state.onStatusChanged(status(1, pos = 5, ts = wall0 - 3_590_000)))
        assertEquals(PlayState.PLAYING, state.snapshot().state)
        // A sticky value read again is never newer than what is known
        assertNull(state.onStatusChanged(status(2, pos = 5, ts = wall0 - 7_200_000), replay = true))
    }

    @Test
    fun timestampFarAheadDoesNotBlockLaterEvents() {
        // Not from Poweramp (the receiver is exported): applied, but its "ts" is not taken as the newest
        assertNotNull(state.onTrackChanged(trackChanged(title = "Fake", ts = wall0 + 86_400_000)))
        clock.advance(1_000)
        assertNotNull(state.onTrackChanged(trackChanged(title = "Real", ts = clock.wall)))
        assertEquals("Real", state.snapshot().track.title)
    }

    @Test
    fun reportedStatesIncludeWhatANewerStickyValueWentPast() {
        // Process started by "paused" (ts1); the sticky value read first already says "playing" again (ts2)
        state.onStatusChanged(status(1, pos = 3, ts = wall0 + 2_000), replay = true)
        assertNull(state.onStatusChanged(status(2, pos = 3, ts = wall0 + 1_000)))
        assertEquals(PlayState.PLAYING, state.snapshot().state)
        // ... but the pause and the resume are both news for whoever follows the reports (the user's stop)
        assertEquals(PlayState.PAUSED, state.reportedState(status(2, pos = 3, ts = wall0 + 1_000)))
        assertEquals(PlayState.PLAYING, state.reportedState(status(1, pos = 3, ts = wall0 + 2_000)))
        // Copies and late copies are not
        assertNull(state.reportedState(status(1, pos = 3, ts = wall0 + 2_000)))
        assertNull(state.reportedState(status(2, pos = 3, ts = wall0 + 1_000)))
        assertNull(state.reportedState(Bundle().apply { putInt("api", 700) }))
    }

    @Test
    fun playingTrackEndsLongAfterItsLength() {
        state.onTrackChanged(trackChanged(pos = 50, durMs = 200_000))
        state.onStatusChanged(status(1, pos = 50))
        // 150 s left; at half speed 300 s, plus a minute
        val deadline = clock.now + 360_000
        assertEquals(deadline, state.trackEndDeadline())
        clock.advance(359_999)
        assertNull(state.checkTrackEnd())
        assertEquals(PlayState.PLAYING, state.snapshot().state)
        clock.advance(1)
        val ended = state.checkTrackEnd()!!
        assertEquals(PlayState.STOPPED, ended.state)
        assertEquals("Song", ended.track.title)
        assertTrue(state.ended)
        assertNull(state.trackEndDeadline())
        assertNull(state.checkTrackEnd())
    }

    @Test
    fun powerampHeardOfAgainAfterTheEnd() {
        state.onTrackChanged(trackChanged(pos = 0, durMs = 10_000))
        state.onStatusChanged(status(1, pos = 0))
        clock.advance(100_000)
        assertEquals(PlayState.STOPPED, state.checkTrackEnd()!!.state)

        // Still alive after all (e.g. a track repeated without a broadcast): its next track plays
        val next = state.onTrackChanged(trackChanged(title = "Next", pos = 0))!!
        assertEquals(PlayState.PLAYING, next.state)
        assertFalse(state.ended)

        clock.advance(500_000)
        state.checkTrackEnd()
        // A seek or a pause brings it back as well
        assertEquals(PlayState.PLAYING, state.onPositionSync(Bundle().apply { putInt("pos", 3) })!!.state)
        clock.advance(500_000)
        state.checkTrackEnd()
        assertEquals(PlayState.PAUSED, state.onStatusChanged(status(2, pos = 3))!!.state)
        assertNull(state.trackEndDeadline())   // paused: no end
    }

    @Test
    fun stickyValueOfAPowerampThatDiedIsStoppedAtOnce() {
        // Poweramp was killed an hour ago while playing; its sticky broadcasts still say "playing"
        val hourAgo = clock.wall - 3_600_000
        state.onTrackChanged(trackChanged(pos = 10, ts = hourAgo, durMs = 200_000), replay = true)
        state.onStatusChanged(status(1, pos = 10, ts = hourAgo), replay = true)
        assertEquals(PlayState.STOPPED, state.snapshot().state)
        assertEquals("Song", state.snapshot().track.title)
        assertTrue(state.ended)
        assertNull(state.trackEndDeadline())
        // Read again: still the same old value, still stopped
        assertNull(state.onStatusChanged(status(1, pos = 10, ts = hourAgo), replay = true))
        assertEquals(PlayState.STOPPED, state.snapshot().state)
    }

    @Test
    fun streamsAndTracksWithoutLengthNeverEnd() {
        state.onTrackChanged(trackChanged(durMs = 0))
        state.onStatusChanged(status(1, pos = 0))
        assertNull(state.trackEndDeadline())
        clock.advance(86_400_000)
        assertNull(state.checkTrackEnd())

        val stream = Bundle(track("Radio")).apply {
            putString("path", "https://radio.example/stream")
            putInt("durMs", 180_000)
            putLong("ts", clock.wall)
        }
        state.onTrackChanged(stream)
        assertNull(state.trackEndDeadline())
    }

    @Test
    fun broadcastWithoutTrackOrStateChangesNothing() {
        assertNull(state.onTrackChanged(Bundle().apply { putInt("api", 700) }))
        assertNull(state.onStatusChanged(Bundle().apply { putInt("api", 700) }))
        assertEquals(PlayState.STOPPED, state.snapshot().state)
        assertTrue(state.snapshot().track.isEmpty)
    }
}
