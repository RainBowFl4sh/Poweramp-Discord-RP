package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.app.Application
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.ActivityTexts
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.Hidden
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.UpdateResult
import io.github.rainbowfl4sh.powerampdiscordrp.settings.EnvState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UpdateCheckState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.AboutPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.AdvancedPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.CoverPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.SourcesPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.StatusPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.AppTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * What the screens say for the states the engine and the runtime report: only what Discord really shows is "shown",
 * a resting engine does not hide what the settings say, options without effect are not offered.
 * (No CONNECTING status here: its endless progress animation keeps Robolectric's looper busy.)
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34], application = Application::class)   // the app's own Application wires the whole runtime
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenTextsTest {
    private val scope = MainScope()

    @After
    fun tearDown() = scope.cancel()

    private val env = EnvState("build-1032-uni", 1032, true, true, true, true, true)
    private val playing = PlaybackSnapshot(PlayState.PLAYING, TrackInfo(title = "Song", artist = "Artist", path = "/m/a.flac", durationMs = 200_000))
    private val builtIn = "1555109720807702559"
    private val connected = EngineStatus(kind = EngineStatus.Kind.CONNECTED, clientId = builtIn, visible = true, hidden = Hidden.NO)

    private fun model(
        status: EngineStatus = EngineStatus(clientId = builtIn, hidden = Hidden.NO),
        config: Config = Config(),
        hidden: Hidden = Hidden.NO,
        defaultClientId: String = builtIn,
    ) = FakeAppModel(scope, config, env, status, playing, defaultClientId).apply { texts.value = ActivityTexts(hidden = hidden) }

    private var activity: ComponentActivity? = null

    private fun show(content: @Composable () -> Unit): ComposedTexts {
        val a = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        a.setContent { AppTheme { content() } }
        ShadowLooper.idleMainLooper()
        activity = a
        return ComposedTexts(a.window.decorView)
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

    // ---- "Now playing": only what Discord shows is "shown"

    @Test
    fun aTrackDiscordShowsIsShown() {
        val screen = show { StatusPage(model(connected)) {} }
        assertTrue(string(R.string.hidden_no) in screen)
    }

    @Test
    fun stoppedByTheUserIsNotShown() {
        // the engine still computes the texts for every Poweramp broadcast: hidden stays NO
        val m = model().apply { stoppedByUser.value = true }
        val screen = show { StatusPage(m) {} }
        assertTrue(string(R.string.now_user_stopped) in screen)
        assertFalse(string(R.string.hidden_no) in screen)
    }

    @Test
    fun rejectedOrNotYetSentIsNotShown() {
        val rejected = show { StatusPage(model(connected.copy(visible = false, activityError = "Invalid Form Body"))) {} }
        assertTrue(string(R.string.now_rejected) in rejected)
        assertTrue(string(R.string.conn_activity_error, "Invalid Form Body") in rejected)
        assertFalse(string(R.string.hidden_no) in rejected)

        val notRunning = show { StatusPage(model()) {} }
        assertTrue(string(R.string.now_not_connected) in notRunning)
        assertFalse(string(R.string.hidden_no) in notRunning)
    }

    // ---- test presence

    @Test
    fun theTestPresenceIsShownOnlyOnceDiscordShowsIt() {
        val waiting = show { StatusPage(model(connected.copy(visible = false, testing = true))) {} }
        assertTrue(string(R.string.conn_test_waiting) in waiting)
        assertFalse(string(R.string.conn_testing) in waiting)

        val shown = show { StatusPage(model(connected.copy(testing = true))) {} }
        assertTrue(string(R.string.conn_testing) in shown)

        // requested, but there is no connection to send it with: the headline says why
        val noId = show { StatusPage(model(EngineStatus(kind = EngineStatus.Kind.NO_APP_ID, testing = true), defaultClientId = "")) {} }
        assertFalse(string(R.string.conn_testing) in noId)
        assertFalse(string(R.string.conn_test_waiting) in noId)
    }

    @Test
    fun withoutApplicationIdThereIsNothingToTest() {
        val screen = show { StatusPage(model(defaultClientId = "")) {} }
        assertTrue(string(R.string.conn_no_app_id) in screen)
        assertTrue(string(R.string.check_appid_missing) in screen)
        assertFalse(screen.isEnabled(string(R.string.btn_test)))
        assertFalse(screen.isEnabled(string(R.string.btn_reconnect)))

        val withId = show { StatusPage(model()) {} }
        assertTrue(withId.isEnabled(string(R.string.btn_test)))
        assertTrue(withId.isEnabled(string(R.string.btn_reconnect)))
    }

    @Test
    fun anInvalidBuiltInApplicationIdIsAnError() {
        val screen = show { StatusPage(model(defaultClientId = "12345")) {} }
        assertTrue(string(R.string.check_appid_builtin_invalid, "12345") in screen)
        // the engine tries it all the same (Discord rejects it): the buttons stay
        assertTrue(screen.isEnabled(string(R.string.btn_test)))
    }

    // ---- connection headline of a resting engine (fresh process)

    @Test
    fun aRestingEngineShowsWhatTheSettingsSay() {
        val off = show { StatusPage(model(config = Config(enabled = false), hidden = Hidden.DISABLED)) {} }
        assertTrue(string(R.string.conn_disabled) in off)
        assertFalse(string(R.string.conn_idle) in off)

        val noId = show { StatusPage(model(defaultClientId = "")) {} }
        assertFalse(string(R.string.conn_idle) in noId)
    }

    @Test
    fun aRestingEngineShowsARejectionOfAnEarlierProcess() {
        val m = model().apply { rejectedAppId = builtIn }
        val screen = show { StatusPage(m) {} }
        assertTrue(string(R.string.err_invalid_client_id) in screen)
        assertFalse(string(R.string.conn_idle) in screen)
        // a running service tries the ID itself
        m.serviceRunning.value = true
        ShadowLooper.idleMainLooper()
        assertTrue(string(R.string.conn_idle) in screen)
    }

    // ---- cover tab

    @Test
    fun noCoverFoundOnlyOnceTheTrackWasShown() {
        val stopped = show { CoverPage(model()) }
        assertTrue(string(R.string.cov_not_yet) in stopped)
        assertFalse(string(R.string.cov_none) in stopped)

        // shown with the logo (nothing found, or Discord could not load the cover: the engine then reports no URL)
        val logo = show { CoverPage(model(connected)) }
        assertTrue(string(R.string.cov_none) in logo)
    }

    @Test
    fun preferPowerampsCoverNeedsAnUploadHost() {
        val off = Config(localCovers = true, preferLocal = true, uploadHost = Config.UPLOAD_OFF)
        assertFalse(show { CoverPage(model(config = off)) }.isEnabled(string(R.string.cov_prefer_local)))
        assertFalse(string(R.string.src_prefer_local_note) in show { SourcesPage(model(config = off)) {} })

        val x0 = off.copy(uploadHost = Config.UPLOAD_X0)
        assertTrue(show { CoverPage(model(config = x0)) }.isEnabled(string(R.string.cov_prefer_local)))
        assertTrue(string(R.string.src_prefer_local_note) in show { SourcesPage(model(config = x0)) {} })
    }

    // ---- about tab

    @Test
    fun aRateLimitIsNotNoConnection() {
        val m = model().apply { update.value = UpdateCheckState.Failed(UpdateResult.Reason.RATE_LIMITED) }
        val limited = show { AboutPage(m) }
        assertTrue(string(R.string.upd_rate_limited) in limited)
        assertFalse(string(R.string.upd_failed) in limited)

        m.update.value = UpdateCheckState.Failed(UpdateResult.Reason.NO_RELEASE)
        ShadowLooper.idleMainLooper()
        assertTrue(string(R.string.upd_no_release) in limited)

        m.update.value = UpdateCheckState.Failed(UpdateResult.Reason.OFFLINE)
        ShadowLooper.idleMainLooper()
        assertTrue(string(R.string.upd_failed) in limited)
    }

    // ---- settings export

    private fun nextDocumentRequest(): Intent? = shadowOf(activity!!).nextStartedActivityForResult?.intent

    @Test
    fun anExportWithKeysAsksFirst() {
        ShadowDialog.reset()
        val screen = show { AdvancedPage(model(config = Config(spotifyId = "id", spotifySecret = "secret", discogsToken = "token"))) }
        assertTrue(string(R.string.adv_settings_info) in screen)
        screen.click(string(R.string.adv_export))
        ShadowLooper.idleMainLooper()
        assertNull(nextDocumentRequest())

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        val question = ComposedTexts(dialog.window!!.decorView)
        assertTrue(string(R.string.adv_export_keys_title) in question)
        assertTrue(string(R.string.adv_export_keys_text, "Spotify, Discogs") in question)
        question.click(string(R.string.adv_export_keys_confirm))
        ShadowLooper.idleMainLooper()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, nextDocumentRequest()?.action)
    }

    @Test
    fun anExportWithoutKeysStartsAtOnce() {
        ShadowDialog.reset()
        val screen = show { AdvancedPage(model()) }
        screen.click(string(R.string.adv_export))
        ShadowLooper.idleMainLooper()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, nextDocumentRequest()?.action)
        assertNull(ShadowDialog.getLatestDialog())
    }
}
