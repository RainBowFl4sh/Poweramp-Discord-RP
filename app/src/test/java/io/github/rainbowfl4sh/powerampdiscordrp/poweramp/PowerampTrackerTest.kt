package io.github.rainbowfl4sh.powerampdiscordrp.poweramp

import android.content.Intent
import android.os.Bundle
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PowerampTrackerTest {
    private class FakeClock(var now: Long, var wall: Long) : Clock {
        override fun nowMs() = now
        override fun wallMs() = wall
    }

    private val clock = FakeClock(now = 1_000_000, wall = 1_700_000_000_000)
    private val sticky = mutableMapOf<String, Intent>()
    private val stickyReads = mutableListOf<String>()
    private val logLines = mutableListOf<String>()
    private val tracker = PowerampTracker(
        PowerampState(clock),
        readSticky = { action -> stickyReads += action; sticky[action] },
        log = { logLines += it },
    )

    private fun trackBundle(title: String) = Bundle().apply {
        putLong("id", 3)
        putLong("realId", 33)
        putString("title", title)
        putString("artist", "Artist")
        putString("album", "Album")
        putString("path", "/music/$title.flac")
        putInt("durMs", 240_000)
        putInt("dur", 240)
    }

    private fun trackChanged(action: String, title: String, pos: Int, ts: Long) =
        Intent(action).putExtras(Bundle(trackBundle(title)).apply {
            putBundle("track", trackBundle(title))
            putInt("api", 700)
            putInt("pos", pos)
            putLong("ts", ts)
        })

    private fun statusChanged(action: String, state: Int, pos: Int, ts: Long) = Intent(action).putExtras(Bundle().apply {
        putInt("api", 700)
        putInt("pos", pos)
        putLong("ts", ts)
        putInt("state", state)
        putInt("status", if (state == 1) 1 else if (state == 2) 2 else 3)
        putBoolean("paused", state != 1)
    })

    @Test
    fun coldStartByStatusLearnsTheTrackFromTheStickyBroadcast() {
        // Process killed while paused; the user resumes: only STATUS_CHANGED_EXPLICIT arrives, without the track
        sticky[Poweramp.ACTION_TRACK_CHANGED] = trackChanged(Poweramp.ACTION_TRACK_CHANGED, "Song", 0, clock.wall - 600_000)
        sticky[Poweramp.ACTION_STATUS_CHANGED] = statusChanged(Poweramp.ACTION_STATUS_CHANGED, 1, 95, clock.wall - 200)
        val live = statusChanged(Poweramp.ACTION_STATUS_CHANGED_EXPLICIT, 1, 95, clock.wall - 200)

        val update = tracker.onBroadcast(live)
        assertEquals(PlayState.PLAYING, update.reportedState)
        val s = update.snapshot!!
        assertEquals("Song", s.track.title)
        assertEquals(PlayState.PLAYING, s.state)
        assertEquals(95_000L, s.positionMs)
        assertEquals(clock.now - 200, s.stampMs)
        assertTrue(logLines.any { it.contains("\"Song\"") })
        assertTrue(logLines.any { it.endsWith("playing") })
    }

    @Test
    fun stickyBroadcastsAreReadOnlyOnceForBroadcasts() {
        tracker.onBroadcast(statusChanged(Poweramp.ACTION_STATUS_CHANGED_EXPLICIT, 1, 0, clock.wall))
        tracker.onBroadcast(statusChanged(Poweramp.ACTION_STATUS_CHANGED_EXPLICIT, 2, 5, clock.wall + 5_000))
        assertEquals(listOf(Poweramp.ACTION_TRACK_CHANGED, Poweramp.ACTION_STATUS_CHANGED), stickyReads)
        assertEquals(PlayState.PAUSED, tracker.snapshot.state)
    }

    @Test
    fun syncAppliesTheStickyBroadcastsOldestFirst() {
        // The track changed after the last status change: its position (0 at the track start) is the newer one
        sticky[Poweramp.ACTION_STATUS_CHANGED] = statusChanged(Poweramp.ACTION_STATUS_CHANGED, 1, 20, clock.wall - 10_000)
        sticky[Poweramp.ACTION_TRACK_CHANGED] = trackChanged(Poweramp.ACTION_TRACK_CHANGED, "Next", 0, clock.wall - 2_000)

        val s = tracker.sync()
        assertEquals("Next", s.track.title)
        assertEquals(PlayState.PLAYING, s.state)
        assertEquals(0L, s.positionMs)
        assertEquals(clock.now - 2_000, s.stampMs)

        // Every sync reads them again
        tracker.sync()
        assertEquals(4, stickyReads.size)
    }

    @Test
    fun syncWithoutPowerampGivesTheEmptySnapshot() {
        val s = tracker.sync()
        assertEquals(PlayState.STOPPED, s.state)
        assertTrue(s.track.isEmpty)
    }

    @Test
    fun explicitAndStickyVariantOfOneEventCountOnce() {
        val ts = clock.wall
        assertNotNull(tracker.onBroadcast(trackChanged(Poweramp.ACTION_TRACK_CHANGED_EXPLICIT, "A", 0, ts)).snapshot)
        clock.now += 30
        assertNull(tracker.onBroadcast(trackChanged(Poweramp.ACTION_TRACK_CHANGED, "A", 0, ts)).snapshot)
    }

    @Test
    fun olderStickyValueDoesNotOverrideALiveBroadcast() {
        tracker.onBroadcast(statusChanged(Poweramp.ACTION_STATUS_CHANGED_EXPLICIT, 2, 10, clock.wall))
        // Initial sticky delivery when the service registers its receiver, older than what is known
        val old = statusChanged(Poweramp.ACTION_STATUS_CHANGED, 1, 3, clock.wall - 60_000)
        assertEquals(PowerampTracker.Update(null, null), tracker.onBroadcast(old, replay = true))
        assertEquals(PlayState.PAUSED, tracker.snapshot.state)
    }

    @Test
    fun seekUpdatesThePosition() {
        tracker.onBroadcast(trackChanged(Poweramp.ACTION_TRACK_CHANGED_EXPLICIT, "A", 0, clock.wall))
        clock.now += 5_000
        clock.wall += 5_000
        val s = tracker.onBroadcast(Intent(Poweramp.ACTION_TPOS_SYNC).putExtra("api", 700).putExtra("pos", 120)).snapshot!!
        assertEquals(120_000L, s.positionMs)
        assertEquals(clock.now, s.stampMs)
    }

    @Test
    fun foreignBroadcastsChangeNothing() {
        val nothing = PowerampTracker.Update(null, null)
        assertEquals(nothing, tracker.onBroadcast(Intent("com.example.SOMETHING").putExtra("title", "x")))
        assertEquals(nothing, tracker.onBroadcast(Intent(Poweramp.ACTION_TRACK_CHANGED_EXPLICIT)))   // no extras
        val noState = Intent(Poweramp.ACTION_STATUS_CHANGED_EXPLICIT).putExtra("state", "playing")
        assertEquals(nothing, tracker.onBroadcast(noState))
        assertTrue(tracker.snapshot.track.isEmpty)
    }

    @Test
    fun lateExplicitCopiesDoNotTurnBackTheTrack() {
        // Skipping A -> B -> C while the service runs: the sticky copies reach its receiver at once, the explicit
        // copies of the manifest receiver come later through Android's queue
        val ts = clock.wall
        for ((i, title) in listOf("A", "B", "C").withIndex()) {
            assertNotNull(tracker.onBroadcast(trackChanged(Poweramp.ACTION_TRACK_CHANGED, title, 0, ts + i * 300)).snapshot)
        }
        clock.now += 2_000
        clock.wall += 2_000
        for ((i, title) in listOf("A", "B", "C").withIndex()) {
            val late = trackChanged(Poweramp.ACTION_TRACK_CHANGED_EXPLICIT, title, 0, ts + i * 300)
            assertEquals(PowerampTracker.Update(null, null), tracker.onBroadcast(late))
        }
        assertEquals("C", tracker.snapshot.track.title)
    }

    @Test
    fun coldStartWithANewerStickyValueReportsEveryChange() {
        // Process started by "paused" (ts1); meanwhile the user pressed play again (ts2), which the sticky value says
        sticky[Poweramp.ACTION_TRACK_CHANGED] = trackChanged(Poweramp.ACTION_TRACK_CHANGED, "Song", 30, clock.wall - 60_000)
        sticky[Poweramp.ACTION_STATUS_CHANGED] = statusChanged(Poweramp.ACTION_STATUS_CHANGED, 1, 40, clock.wall - 1_000)

        val paused = tracker.onBroadcast(statusChanged(Poweramp.ACTION_STATUS_CHANGED_EXPLICIT, 2, 40, clock.wall - 2_000))
        assertEquals(PlayState.PLAYING, paused.snapshot!!.state)   // the older live event does not override it
        assertEquals(PlayState.PAUSED, paused.reportedState)       // ... but the user's stop sees the pause

        val playing = tracker.onBroadcast(statusChanged(Poweramp.ACTION_STATUS_CHANGED_EXPLICIT, 1, 40, clock.wall - 1_000))
        assertNull(playing.snapshot)
        assertEquals(PlayState.PLAYING, playing.reportedState)

        // The sticky copy delivered when the service registers its receiver tells nothing new
        val replay = statusChanged(Poweramp.ACTION_STATUS_CHANGED, 1, 40, clock.wall - 1_000)
        assertEquals(PowerampTracker.Update(null, null), tracker.onBroadcast(replay, replay = true))
    }

    @Test
    fun trackOfAPowerampThatDiedEnds() {
        tracker.onBroadcast(trackChanged(Poweramp.ACTION_TRACK_CHANGED_EXPLICIT, "A", 0, clock.wall))
        tracker.onBroadcast(statusChanged(Poweramp.ACTION_STATUS_CHANGED_EXPLICIT, 1, 0, clock.wall))
        val deadline = tracker.trackEndDeadline()!!
        assertNull(tracker.checkTrackEnd())
        clock.now = deadline
        assertEquals(PlayState.STOPPED, tracker.checkTrackEnd()!!.state)
        assertTrue(logLines.last(), logLines.last().startsWith("Poweramp: stopped (nothing heard"))
    }
}
