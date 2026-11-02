package io.github.rainbowfl4sh.powerampdiscordrp.service

import android.content.SharedPreferences
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The "Stop" of the service notification: no presence until playback starts again, i.e. Poweramp switches to playing
 * after it was paused or stopped. A track change while playing does not count.
 *
 * Persisted: without the foreground service Android may kill the process at any time, and the next Poweramp broadcast
 * would otherwise bring the presence back right away.
 */
class UserStop(private val prefs: SharedPreferences) {
    private val _active = MutableStateFlow(prefs.getBoolean(KEY_ACTIVE, false))
    private var sawPause = prefs.getBoolean(KEY_SAW_PAUSE, false)   // guarded by this
    private var lastState = prefs.getString(KEY_LAST_STATE, null)    // guarded by this, see noteState

    /** True while the user's stop is in effect. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** The user stopped the presence while Poweramp was in [current] state. */
    @Synchronized
    fun stop(current: PlayState) = set(active = true, sawPause = current != PlayState.PLAYING)

    /**
     * Android's task manager stopped the previous process: stopped in the play state that process saw last ([noteState]).
     * Stopped while paused, the next "playing" ends the stop; the waking broadcast already says what Poweramp does now.
     */
    @Synchronized
    fun stopAtLastKnownState() =
        stop(PlayState.entries.firstOrNull { it.name == lastState } ?: PlayState.PLAYING)

    /**
     * Every play state the bridge hands the engine, also without a stop: kept for [stopAtLastKnownState] in the next
     * process. Written only when it changes.
     */
    @Synchronized
    fun noteState(state: PlayState) {
        if (state.name == lastState) return
        lastState = state.name
        prefs.edit().putString(KEY_LAST_STATE, state.name).apply()
    }

    /** Explicit start (or the presence was switched on again in the settings). */
    @Synchronized
    fun clear() = set(active = false, sawPause = false)

    /**
     * Every play state Poweramp reports. Playing after a pause / stop ends the user's stop.
     * @return true if this call ended it
     */
    @Synchronized
    fun onPlayState(state: PlayState): Boolean {
        if (!_active.value) return false
        if (state != PlayState.PLAYING) {
            if (!sawPause) set(active = true, sawPause = true)
            return false
        }
        if (!sawPause) return false
        set(active = false, sawPause = false)
        return true
    }

    private fun set(active: Boolean, sawPause: Boolean) {
        if (active == _active.value && sawPause == this.sawPause) return
        this.sawPause = sawPause
        _active.value = active
        prefs.edit().putBoolean(KEY_ACTIVE, active).putBoolean(KEY_SAW_PAUSE, sawPause).apply()
    }

    companion object {
        /** Runtime state of this installation (not part of the settings, not exported). */
        const val PREFS_NAME = "runtime"
        private const val KEY_ACTIVE = "userStop"
        private const val KEY_SAW_PAUSE = "userStopSawPause"
        private const val KEY_LAST_STATE = "lastPlayState"
    }
}
