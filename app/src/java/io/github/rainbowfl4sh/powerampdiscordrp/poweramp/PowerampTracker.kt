package io.github.rainbowfl4sh.powerampdiscordrp.poweramp

import android.content.Intent
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Logger

/**
 * Follows Poweramp through its broadcasts (the Android-free part of [PowerampBridge]).
 *
 * A process started by a broadcast knows nothing yet, and STATUS_CHANGED does not carry the track: resuming playback
 * after Android killed this app would show nothing. So before the first broadcast is applied, the current values of the
 * sticky TRACK_CHANGED / STATUS_CHANGED broadcasts are read once ([readSticky]).
 */
class PowerampTracker(
    private val state: PowerampState,
    private val readSticky: (action: String) -> Intent?,
    private val log: Logger = Logger.NONE,
) {
    /** What one broadcast changed. */
    data class Update(
        /** The new snapshot, null if nothing changed. */
        val snapshot: PlaybackSnapshot?,
        /** Play state Poweramp reported with this broadcast if it is news ([PowerampState.reportedState]), else null. */
        val reportedState: PlayState? = null,
    )

    private var stickyRead = false   // guarded by this

    val snapshot: PlaybackSnapshot get() = state.snapshot()

    /** Reads the sticky broadcasts again (app opened, service started) and returns the resulting snapshot. */
    @Synchronized
    fun sync(): PlaybackSnapshot {
        val before = state.snapshot()
        readStickyBroadcasts()
        return state.snapshot().also { report(before, it) }
    }

    /**
     * One broadcast from Poweramp (TRACK_CHANGED / STATUS_CHANGED / TPOS_SYNC, explicit or not).
     * [replay] = the stored value of a sticky broadcast, delivered when a receiver is registered: it tells the current
     * state, but no change, so it never yields a [Update.reportedState].
     */
    @Synchronized
    fun onBroadcast(intent: Intent, replay: Boolean = false): Update {
        val before = state.snapshot()
        if (!stickyRead) readStickyBroadcasts()
        apply(intent, replay)
        val reported = if (replay) null else reportedState(intent)
        val after = state.snapshot()
        if (after == before) return Update(null, reported)
        report(before, after)
        return Update(after, reported)
    }

    /** STOPPED once a playing track should have ended long ago (Poweramp died); null if nothing changed. */
    @Synchronized
    fun checkTrackEnd(): PlaybackSnapshot? {
        val before = state.snapshot()
        return state.checkTrackEnd()?.also { report(before, it) }
    }

    /** See [PowerampState.trackEndDeadline]. */
    fun trackEndDeadline(): Long? = state.trackEndDeadline()

    private fun readStickyBroadcasts() {
        stickyRead = true
        // Oldest first: the newer event's position wins
        listOfNotNull(readSticky(Poweramp.ACTION_TRACK_CHANGED), readSticky(Poweramp.ACTION_STATUS_CHANGED))
            .sortedBy { it.extras?.let(PowerampIntents::timestamp) ?: 0L }
            .forEach { apply(it, replay = true) }
    }

    private fun apply(intent: Intent, replay: Boolean) {
        val extras = intent.extras ?: return
        when (intent.action) {
            Poweramp.ACTION_TRACK_CHANGED, Poweramp.ACTION_TRACK_CHANGED_EXPLICIT -> state.onTrackChanged(extras, replay)
            Poweramp.ACTION_STATUS_CHANGED, Poweramp.ACTION_STATUS_CHANGED_EXPLICIT -> state.onStatusChanged(extras, replay)
            Poweramp.ACTION_TPOS_SYNC -> state.onPositionSync(extras)
        }
    }

    private fun reportedState(intent: Intent): PlayState? = when (intent.action) {
        Poweramp.ACTION_STATUS_CHANGED, Poweramp.ACTION_STATUS_CHANGED_EXPLICIT -> intent.extras?.let(state::reportedState)
        else -> null
    }

    private fun report(before: PlaybackSnapshot, after: PlaybackSnapshot) {
        val t = after.track
        if (t != before.track && !t.isEmpty) {
            log.log("Poweramp: track \"${t.title}\" by \"${t.artist}\"${if (t.isStream) " (stream)" else ""}")
        }
        if (after.state != before.state) {
            val why = if (state.ended) " (nothing heard since the track should have ended - Poweramp is gone)" else ""
            log.log("Poweramp: ${after.state.name.lowercase()}$why")
        }
    }
}
