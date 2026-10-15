package io.github.rainbowfl4sh.powerampdiscordrp.poweramp

import android.os.Bundle
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock

/**
 * Merges Poweramp's separate broadcasts into one [PlaybackSnapshot]: TRACK_CHANGED brings the track (and the position),
 * STATUS_CHANGED only the play state (it does not carry the track), TPOS_SYNC only the position after a seek.
 *
 * Positions are stamped with [Clock.nowMs] at the moment Poweramp sent the event: the broadcast's "ts" (wall clock) tells
 * how long ago that was, so a delayed delivery (cold process start, sticky broadcast read later) still yields the right
 * position.
 *
 * Every event arrives twice while the presence service runs (the sticky copy at once, the explicit copy through Android's
 * slower queue for manifest receivers), and a process started by a broadcast first reads the sticky values, which may
 * already be newer than that broadcast. So an event older than the last one applied is outdated and ignored.
 *
 * Poweramp says nothing when it dies (force stop, crash, update, task killer): a playing snapshot whose track should
 * have ended long ago turns into STOPPED ([checkTrackEnd]), until Poweramp is heard of again.
 */
class PowerampState(private val clock: Clock) {
    private var current = PlaybackSnapshot()
    private var trackTs = 0L
    private var statusTs = 0L
    private var reportTs = 0L

    /** The snapshot is STOPPED because nothing came from Poweramp long after the track should have ended. */
    @get:Synchronized
    var ended = false
        private set

    @Synchronized
    fun snapshot(): PlaybackSnapshot = current

    /**
     * [replay] = a sticky broadcast read again (sync, receiver registration): ignored if it is older than what was
     * already applied, like a live broadcast that is older by less than [CLOCK_SET_BACK_MS].
     * @return the new snapshot, null if nothing changed
     */
    @Synchronized
    fun onTrackChanged(extras: Bundle, replay: Boolean = false): PlaybackSnapshot? {
        val ts = PowerampIntents.timestamp(extras)
        if (isStale(ts, trackTs, replay)) return null
        val track = PowerampIntents.trackOf(extras) ?: return null
        trackTs = remembered(ts, trackTs)
        val position = PowerampIntents.positionMs(extras) ?: 0
        return apply(alive().copy(track = track, positionMs = position, stampMs = stampOf(ts)))
    }

    @Synchronized
    fun onStatusChanged(extras: Bundle, replay: Boolean = false): PlaybackSnapshot? {
        val ts = PowerampIntents.timestamp(extras)
        if (isStale(ts, statusTs, replay)) return null
        val state = PowerampIntents.playStateOf(extras) ?: return null
        statusTs = remembered(ts, statusTs)
        val stamp = stampOf(ts)
        val base = alive()
        // Without "pos" the position is carried over (advanced up to the event if it was playing until then)
        val position = PowerampIntents.positionMs(extras) ?: positionAt(base, stamp)
        return apply(base.copy(state = state, positionMs = position, stampMs = stamp))
    }

    /** TPOS_SYNC (after a seek): only "pos", no timestamp. */
    @Synchronized
    fun onPositionSync(extras: Bundle): PlaybackSnapshot? {
        val position = PowerampIntents.positionMs(extras) ?: return null
        return apply(alive().copy(positionMs = position, stampMs = stampOf(PowerampIntents.timestamp(extras))))
    }

    /**
     * The play state of a live STATUS_CHANGED if it is news: not a copy of (or older than) a report seen before.
     * Unlike the snapshot, this follows every change Poweramp reported, also those a newer sticky value read first
     * already went past (process started by a broadcast).
     */
    @Synchronized
    fun reportedState(extras: Bundle): PlayState? {
        val state = PowerampIntents.playStateOf(extras) ?: return null
        val ts = PowerampIntents.timestamp(extras)
        if (isStale(ts, reportTs, replay = false)) return null
        reportTs = remembered(ts, reportTs)
        return state
    }

    /** [Clock.nowMs] from which a playing snapshot counts as ended (see [checkTrackEnd]), null = never. */
    @Synchronized
    fun trackEndDeadline(): Long? = if (ended) null else endDeadlineOf(current)

    /** Turns a playing snapshot into STOPPED once its [trackEndDeadline] has passed. @return the new snapshot, null if unchanged */
    @Synchronized
    fun checkTrackEnd(): PlaybackSnapshot? {
        if (ended) return null
        val deadline = endDeadlineOf(current) ?: return null
        if (clock.nowMs() < deadline) return null
        ended = true
        current = current.copy(state = PlayState.STOPPED)
        return current
    }

    /**
     * Older than [last] (same kind of event): a sticky value read again, the late copy of an event, or a copy of the
     * same event (same "ts"). A live event that is much older is applied: the wall clock was set back.
     */
    private fun isStale(ts: Long, last: Long, replay: Boolean): Boolean = when {
        ts <= 0 || last <= 0 || ts > last -> false
        ts == last -> true
        else -> replay || last - ts < CLOCK_SET_BACK_MS
    }

    /** A "ts" far ahead of the clock (not from Poweramp) is not remembered: the real events after it would look old. */
    private fun remembered(ts: Long, last: Long): Long =
        if (ts > 0 && ts <= clock.wallMs() + MAX_AHEAD_MS) ts else last

    /** [Clock.nowMs] at the time Poweramp sent an event with this "ts" (now if unknown or in the future). */
    private fun stampOf(ts: Long): Long {
        val now = clock.nowMs()
        if (ts <= 0) return now
        return now - (clock.wallMs() - ts).coerceAtLeast(0)
    }

    /** Poweramp was heard of: an ended snapshot is back to the state Poweramp reported last (only PLAYING ends). */
    private fun alive(): PlaybackSnapshot {
        if (!ended) return current
        ended = false
        return current.copy(state = PlayState.PLAYING)
    }

    private fun positionAt(s: PlaybackSnapshot, stamp: Long): Long =
        if (s.state == PlayState.PLAYING) s.positionMs + (stamp - s.stampMs).coerceAtLeast(0) else s.positionMs

    /**
     * Poweramp plays at down to half speed (tempo 0.5x), so a track may take twice its remaining length; a minute more
     * for gaps and for the next track's broadcast. Streams and tracks without a length never end.
     */
    private fun endDeadlineOf(s: PlaybackSnapshot): Long? {
        val dur = s.track.durationMs
        if (s.state != PlayState.PLAYING || s.track.isStream || dur <= 0) return null
        return s.stampMs + SLOWEST_TEMPO_FACTOR * (dur - s.positionMs).coerceAtLeast(0) + END_GRACE_MS
    }

    private fun apply(next: PlaybackSnapshot): PlaybackSnapshot? {
        val dur = next.track.durationMs
        var s = next.copy(positionMs = if (dur > 0) next.positionMs.coerceIn(0, dur) else next.positionMs.coerceAtLeast(0))
        // Already long over (e.g. the sticky value of a Poweramp that died while playing): stopped right away
        val deadline = endDeadlineOf(s)
        if (deadline != null && clock.nowMs() >= deadline) {
            ended = true
            s = s.copy(state = PlayState.STOPPED)
        }
        if (s == current) return null
        current = s
        return s
    }

    companion object {
        /** A live event older than the last one by up to this much is a late copy; by more, the clock was set back. */
        const val CLOCK_SET_BACK_MS = 2 * 60_000L

        /** How far ahead of the wall clock a "ts" may be and still be remembered. */
        const val MAX_AHEAD_MS = 60_000L

        /** Slowest tempo Poweramp offers is 0.5x: a track takes up to twice its length. */
        const val SLOWEST_TEMPO_FACTOR = 2

        const val END_GRACE_MS = 60_000L
    }
}
