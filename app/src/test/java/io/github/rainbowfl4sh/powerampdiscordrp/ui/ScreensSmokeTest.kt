package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.ActivityTexts
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.Hidden
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkError
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkState
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkedAccount
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.TransportError
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.UpdateResult
import io.github.rainbowfl4sh.powerampdiscordrp.settings.EnvState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.AppLanguages
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UpdateCheckState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.preview.MemberLine
import io.github.rainbowfl4sh.powerampdiscordrp.ui.preview.PresencePreviewContent
import io.github.rainbowfl4sh.powerampdiscordrp.ui.preview.Preview
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.AboutPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.AdvancedPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.CoverPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.DisplayPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.SourcesPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.StatusPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.AppTheme
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.Appearance
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.ThemeMode
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.annotation.Config as RobolectricConfig
import java.time.Duration

/**
 * Every page composes and lays out with made-up state, in light / dark and in every app language (catches crashes in
 * composition, e.g. a missing string argument). The Display tab's live preview is composed from a ready Preview:
 * computing it needs the core's PresenceTexts.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34], application = Application::class)   // the app's own Application wires the whole runtime
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensSmokeTest {
    private val scope = MainScope()
    private val activities = mutableListOf<ActivityController<ComponentActivity>>()

    @After
    fun tearDown() {
        closeActivities()
        scope.cancel()
        ShadowChoreographer.setPaused(false)
    }

    /**
     * Destroys the activities rendered so far. No endless animation may outlive the test: a frame of it still on its
     * way when Robolectric clears the main looper would leave Compose's dispatcher of the main thread waiting for it in
     * every later test. Also called after each language, so 30 languages do not keep hundreds of activities in memory.
     */
    private fun closeActivities() {
        activities.forEach { it.pause().stop().destroy() }
        activities.clear()
        repeat(FRAMES) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) }
    }

    /** [inEveryAppLanguage], closing each language's activities before the next one. */
    private fun everyLanguage(block: () -> Unit) = inEveryAppLanguage {
        block()
        closeActivities()
    }

    private val env = EnvState("build-1032-uni", 1032, true, true, false, true, true)
    private val track = TrackInfo(title = "Boogie Wonderland", artist = "Earth, Wind & Fire", album = "The Very Best", path = "/m/a.flac", durationMs = 287_000)
    private val playing = PlaybackSnapshot(PlayState.PLAYING, track, positionMs = 13_000)
    private val connected = EngineStatus(
        kind = EngineStatus.Kind.CONNECTED,
        clientId = "1555109720807702559", lastSentWallMs = 1_800_000_000_000, connects = 2, visible = true, hidden = Hidden.NO,
        coverUrl = "", coverSource = "poweramp|x0.at",
    )

    /** Composes [content] in a real activity and returns its root view after a few frames. */
    private fun render(dark: Boolean = false, content: @Composable () -> Unit): View {
        // Frames only when the test moves the clock: by default Robolectric moves it on with every frame requested, so
        // an endless animation (the progress indicator of CONNECTING or of a running check) kept idle() busy forever
        ShadowChoreographer.setPaused(true)
        if (activities.isEmpty()) MainThreadCompose.reset()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activities += controller
        val activity = controller.get()
        activity.setContent { AppTheme(darkTheme = dark) { content() } }
        repeat(FRAMES) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) }
        val root = activity.window.decorView
        assertTrue(root.width > 0 && root.height > 0)
        return root
    }

    @Test
    fun everyPageInEveryLanguage() {
        everyLanguage {
            val model = FakeAppModel(scope, env = env, status = connected, snapshot = playing)
            render { MainScreen(model) }
            render(dark = true) { StatusPage(model) {} }
            render { CoverPage(model) }
            render { SourcesPage(model) {} }
            render { AdvancedPage(model) }
            render { AboutPage(model) }
        }
    }

    @Test
    fun pagesWithProblemsAndOptionalFields() {
        val problems = FakeAppModel(
            scope,
            config = Config(
                enabled = false, useCustomApp = true, customClientId = "123", uploadHost = Config.UPLOAD_IMGUR,
                srcSpotify = true, srcDiscogs = true, coverEnabled = false,
            ),
            env = EnvState(null, 0, true, false, false, false, false),
            status = EngineStatus(kind = EngineStatus.Kind.NOT_CONNECTED, error = TransportError.CLOSED, errorDetail = "Binder died"),
        )
        render { StatusPage(problems) {} }
        render { CoverPage(problems) }
        render { SourcesPage(problems) {} }
        render { AdvancedPage(problems) }
        // the environment check has not finished yet
        render { StatusPage(FakeAppModel(scope, status = EngineStatus(kind = EngineStatus.Kind.CONNECTING))) {} }
    }

    @Test
    fun statusAndCoverWhileDiscordShowsNothing() {
        everyLanguage {
            // stopped by the user while Poweramp plays on
            val stopped = FakeAppModel(scope, env = env, status = EngineStatus(clientId = connected.clientId), snapshot = playing)
            stopped.stoppedByUser.value = true
            render { StatusPage(stopped) {} }
            render { CoverPage(stopped) }
            // Discord rejected the update; a test presence is on its way; connecting (an endless progress animation)
            render { StatusPage(FakeAppModel(scope, env = env, status = connected.copy(visible = false, activityError = "Invalid Form Body"), snapshot = playing)) {} }
            render { StatusPage(FakeAppModel(scope, env = env, status = connected.copy(visible = false, testing = true), snapshot = playing)) {} }
            render { StatusPage(FakeAppModel(scope, env = env, status = EngineStatus(kind = EngineStatus.Kind.CONNECTING), snapshot = playing)) {} }
            // a fresh process with the presence switched off; the update check hit GitHub's rate limit
            val off = FakeAppModel(scope, config = Config(enabled = false), env = env, snapshot = playing)
            off.update.value = UpdateCheckState.Failed(UpdateResult.Reason.RATE_LIMITED)
            render { StatusPage(off) {} }
            render { AboutPage(off) }
        }
        // keys in the settings: the export asks first
        render { AdvancedPage(FakeAppModel(scope, config = Config(spotifyId = "id", spotifySecret = "secret", discogsToken = "t"))) }
    }

    @Test
    fun appSettingsAndThemesInEveryLanguage() {
        val amoled = Appearance(theme = ThemeMode.DARK, amoled = true, dynamicColor = false)
        everyLanguage {
            val model = FakeAppModel(scope, env = env, status = connected, snapshot = playing)
            render { AppSettingsDialog(Appearance(), onChange = {}, onDismiss = {}, language = null, onLanguageChange = {}) }
            render(dark = true) {
                AppSettingsDialog(
                    amoled, onChange = {}, onDismiss = {}, language = AppLanguages.byTag("de"), onLanguageChange = {},
                    systemDark = false, dynamicColorAvailable = false, systemLanguageSetting = false,
                )
            }
            // AMOLED black with the app's own colors; light with them
            render { AppTheme(amoled) { MainScreen(model) } }
            render { AppTheme(amoled) { AboutPage(model) } }
            render { AppTheme(Appearance(theme = ThemeMode.LIGHT, dynamicColor = false)) { StatusPage(model) {} } }
        }
    }

    @Test
    fun livePreviewStates() {
        val texts = ActivityTexts(
            playing = true, posMs = 83_000, durMs = 225_000, details = "Boogie Wonderland", state = "Earth, Wind & Fire",
            largeText = "The Very Best", smallText = "Playing", detailsUrl = "https://www.youtube.com/results?search_query=a",
        )
        val preview = Preview(texts, false, true, true, false, MemberLine.STATE, "Earth, Wind & Fire", true)
        for (qualifiers in listOf("en", "ru", "ar-ldrtl")) {
            RuntimeEnvironment.setQualifiers(qualifiers)
            render {
                PresencePreviewContent(preview, "Poweramp", "", flash)
                PresencePreviewContent(preview.copy(example = true, progress = false, elapsed = true), "Poweramp", "", null, onLinkAccount = {})
                PresencePreviewContent(preview.copy(texts = texts.copy(hidden = Hidden.PAUSED_TIMEOUT)), "Poweramp", "", null)
            }
        }
    }

    @Test
    fun discordAccountInEveryState() {
        // every failure, with Discord's own words where they are shown; the progress indicator of Working
        val states = listOf(LinkState.Idle, LinkState.Waiting, LinkState.Working) +
            LinkError.entries.map { LinkState.Failed(it, "HTTP 429: You are being rate limited.") }
        everyLanguage {
            for (state in states) {
                // not linked yet, or a refresh of a linked account
                val account = if (state == LinkState.Idle || state is LinkState.Failed && state.error.ordinal % 2 == 0) null else flash
                val model = FakeAppModel(scope, env = env, status = connected, snapshot = playing, account = account)
                model.accountLink.value = state
                render { StatusPage(model) {} }
            }
            render { StatusPage(FakeAppModel(scope, env = env, status = connected, snapshot = playing, account = flash)) {} }
            // no application ID to log in with; the live preview with and without an account
            render { StatusPage(FakeAppModel(scope, env = env, defaultClientId = "")) {} }
            render { DisplayPage(FakeAppModel(scope, env = env, status = connected, snapshot = playing, account = flash)) }
            render { DisplayPage(FakeAppModel(scope, env = env, status = connected, snapshot = playing)) }
        }
    }

    /** Right to left (Arabic): every page mirrored, the app settings and the live preview with an account. */
    @Test
    fun everyPageRightToLeft() {
        RuntimeEnvironment.setQualifiers("ar-ldrtl")
        val model = FakeAppModel(scope, env = env, status = connected, snapshot = playing, account = flash)
        val root = render { MainScreen(model) }
        assertEquals(View.LAYOUT_DIRECTION_RTL, root.layoutDirection)
        render { StatusPage(model) {} }
        render { DisplayPage(model) }
        render { CoverPage(model) }
        render { SourcesPage(model) {} }
        render { AdvancedPage(model) }
        render { AboutPage(model) }
        render { AppSettingsDialog(Appearance(), onChange = {}, onDismiss = {}, language = AppLanguages.byTag("ar"), onLanguageChange = {}) }
    }

    private val flash = LinkedAccount("80351110224678912", "flash", "Fl4sh", "8342729096ea3675442027381ff50dfe")

    private companion object {
        /** Frames drawn per screen (each one 16 ms of the paused clock): layout, effects and short animations. */
        const val FRAMES = 8
    }
}
