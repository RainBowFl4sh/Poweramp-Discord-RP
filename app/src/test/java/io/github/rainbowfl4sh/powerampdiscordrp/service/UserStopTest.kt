package io.github.rainbowfl4sh.powerampdiscordrp.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserStopTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("user-stop-test", Context.MODE_PRIVATE)

    @Test
    fun notStoppedByDefault() {
        val stop = UserStop(prefs)
        assertFalse(stop.active.value)
        assertFalse(stop.onPlayState(PlayState.PLAYING))
    }

    @Test
    fun stoppedWhilePlayingEndsOnlyAfterAPause() {
        val stop = UserStop(prefs)
        stop.stop(PlayState.PLAYING)
        assertTrue(stop.active.value)
        // Still playing (e.g. the next track): stays stopped
        assertFalse(stop.onPlayState(PlayState.PLAYING))
        assertTrue(stop.active.value)
        assertFalse(stop.onPlayState(PlayState.PAUSED))
        assertTrue(stop.active.value)
        assertTrue(stop.onPlayState(PlayState.PLAYING))
        assertFalse(stop.active.value)
    }

    @Test
    fun stoppedWhilePausedEndsWithPlayback() {
        val stop = UserStop(prefs)
        stop.stop(PlayState.PAUSED)
        assertFalse(stop.onPlayState(PlayState.STOPPED))
        assertTrue(stop.onPlayState(PlayState.PLAYING))
        assertFalse(stop.active.value)
    }

    @Test
    fun survivesAProcessRestart() {
        UserStop(prefs).apply {
            stop(PlayState.PLAYING)
            onPlayState(PlayState.PAUSED)
        }
        // New process: still stopped, and the pause seen before still counts
        val restarted = UserStop(prefs)
        assertTrue(restarted.active.value)
        assertTrue(restarted.onPlayState(PlayState.PLAYING))
        assertFalse(UserStop(prefs).active.value)
    }

    @Test
    fun clearEndsTheStop() {
        val stop = UserStop(prefs)
        stop.stop(PlayState.PLAYING)
        stop.clear()
        assertFalse(stop.active.value)
        assertFalse(UserStop(prefs).active.value)
    }
}
