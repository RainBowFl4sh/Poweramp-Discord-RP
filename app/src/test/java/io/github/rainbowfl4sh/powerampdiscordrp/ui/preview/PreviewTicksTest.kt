package io.github.rainbowfl4sh.powerampdiscordrp.ui.preview

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshots.Snapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.assets.AppInfoSource
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.ui.MainThreadCompose
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RobolectricConfig
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * What a tick of the live preview's clock recomposes (counted with [LocalPreviewTrace]): while a track plays only the
 * position of the progress line, unless a template shows the position; nothing at all for the example track. A rotating
 * text ("||") redraws the card only when its next alternative is due.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewTicksTest {
    private val composed = mutableListOf<String>()
    private val clock = mutableLongStateOf(10_000L)
    private val track = TrackInfo(title = "Song", artist = "Band", album = "Record", path = "/m/Band - Song.mp3", durationMs = 200_000)
    private val playing = PlaybackSnapshot(PlayState.PLAYING, track, positionMs = 5_000, stampMs = 10_000)

    private fun show(config: Config, snapshot: PlaybackSnapshot) {
        MainThreadCompose.reset()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            AppTheme {
                CompositionLocalProvider(LocalPreviewTrace provides { composed += it }) {
                    PresencePreview(config, snapshot, EngineStatus(), AppInfoSource.NONE, "1555109720807702559", clock = clock)
                }
            }
        }
        frames()
    }

    /** Lets Compose apply the state change and draw the next frames. */
    private fun frames() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))

    /** [n] seconds of the clock; returns what they recomposed. */
    private fun tick(n: Int): List<String> {
        composed.clear()
        repeat(n) {
            clock.longValue += 1_000
            // what Compose's own test rule does after a change from outside: the change reaches the composition at once,
            // not only when Compose's global snapshot manager gets to it (it may hang on an earlier test's main looper)
            Snapshot.sendApplyNotifications()
            frames()
        }
        return composed.toList()
    }

    @Test
    fun aPlayingTrackRecomposesThePositionOnly() {
        show(Config(), playing)
        assertTrue("card" in composed && "position" in composed)
        assertEquals(List(3) { "position" }, tick(3))
    }

    @Test
    fun theElapsedTimeOfPlayingAlsoRecomposesAlone() {
        show(Config(activityType = Config.ACTIVITY_PLAYING), playing)
        assertTrue("position" in composed)
        assertEquals(List(2) { "position" }, tick(2))
    }

    @Test
    fun aTemplateWithThePositionRecomposesTheCard() {
        show(Config(state = "%pos% / %dur%"), playing)
        val recomposed = tick(2)
        assertEquals(2, recomposed.count { it == "card" })
        assertEquals(2, recomposed.count { it == "position" })
    }

    @Test
    fun theExampleTrackRecomposesNothing() {
        show(Config(), PlaybackSnapshot())
        assertTrue("card" in composed)
        assertEquals(emptyList<String>(), tick(3))
    }

    @Test
    fun aRotatingTextRedrawsTheCardAtEachStepOnly() {
        // the engine saw the track at 10 s (the clock's start): the next alternative at 15 s, 20 s, ...
        show(Config(details = "%title% || %artist%", rotationSeconds = 5), playing.copy(trackSinceMs = 10_000))
        assertEquals(List(4) { "position" }, tick(4))
        val step = tick(1)
        assertEquals(1, step.count { it == "card" })
        assertEquals(1, step.count { it == "position" })
        assertEquals(List(4) { "position" }, tick(4))
        assertEquals(1, tick(1).count { it == "card" })
    }

    @Test
    fun theExampleTrackRotatesToo() {
        // nothing plays: the example's texts take turns on the clock (10 s now, the next step at 15 s)
        show(Config(details = "%title% || %artist%", rotationSeconds = 5), PlaybackSnapshot())
        assertEquals(emptyList<String>(), tick(4))
        assertEquals(listOf("card"), tick(1))
        assertEquals(emptyList<String>(), tick(4))
        assertEquals(listOf("card"), tick(1))
    }

    @Test
    fun aPausedTrackRecomposesNothingUntilItsTimeoutHidesIt() {
        val paused = PlaybackSnapshot(PlayState.PAUSED, track, positionMs = 5_000, stampMs = 10_000)
        show(Config(pausedBehavior = Config.PAUSED_SHOW, clearAfterPausedMin = 1), paused)
        assertEquals(emptyList<String>(), tick(30))
        // a minute after the pause Discord no longer shows it: the card says why
        assertEquals(listOf("card"), tick(30))
    }
}
