package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.DiscordOAuth
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkError
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkState
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkedAccount
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.Hidden
import io.github.rainbowfl4sh.powerampdiscordrp.settings.EnvState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.AccountLinkStarter
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.DisplayPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.StatusPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.AppTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * The linked Discord account on the Status tab and in the live preview: its real name and picture once linked, "You"
 * and a way to link it before - never the placeholder user the Discord app reports to the presence connection.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34], application = Application::class)   // the app's own Application wires the whole runtime
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountCardTest {
    private val scope = MainScope()
    private val activities = mutableListOf<ActivityController<ComponentActivity>>()

    @Before
    fun setUp() {
        // frames only when frames() moves the clock: the live preview's clock and progress indicators run endlessly
        ShadowChoreographer.setPaused(true)
    }

    @After
    fun tearDown() {
        activities.forEach { it.pause().stop().destroy() }
        frames()
        scope.cancel()
        ShadowChoreographer.setPaused(false)
    }

    private val env = EnvState("build-1032-uni", 1032, true, true, true, true, true)
    private val playing = PlaybackSnapshot(PlayState.PLAYING, TrackInfo(title = "Song", artist = "Artist", path = "/m/a.flac", durationMs = 200_000))
    private val connected = EngineStatus(kind = EngineStatus.Kind.CONNECTED, clientId = "1555109720807702559", visible = true, hidden = Hidden.NO)
    private val flash = LinkedAccount("80351110224678912", "flash", "Fl4sh", "8342729096ea3675442027381ff50dfe")

    private fun model(account: LinkedAccount? = null, defaultClientId: String = "1555109720807702559") =
        FakeAppModel(scope, env = env, status = connected, snapshot = playing, defaultClientId = defaultClientId, account = account)

    private fun frames() = repeat(8) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) }

    /** Past the moment Discord's page needs to open (that delay runs on the coroutines' own timer, in real time). */
    private fun settle() {
        Thread.sleep(AccountLinkStarter.SETTLE_MS + 300)
        frames()
    }

    private lateinit var activity: ComponentActivity

    private fun show(content: @Composable () -> Unit): ComposedTexts {
        if (activities.isEmpty()) MainThreadCompose.reset()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activities += controller
        activity = controller.get()
        activity.setContent { AppTheme { content() } }
        frames()
        return ComposedTexts(activity.window.decorView)
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

    // ---- Status tab

    @Test
    fun withoutAnAccountTheCardOffersToLinkIt() {
        val m = model()
        val screen = show { StatusPage(m) {} }
        assertTrue(string(R.string.acc_not_linked) in screen)
        assertTrue(string(R.string.acc_hint) in screen)
        assertTrue(string(R.string.acc_privacy) in screen)
        assertFalse(string(R.string.acc_unlink) in screen)
        assertTrue(screen.isEnabled(string(R.string.acc_link)))
        // the connection headline names nobody
        assertTrue(string(R.string.conn_connected) in screen)

        screen.click(string(R.string.acc_link))
        frames()
        assertTrue("startAccountLink" in m.actions)
        val opened = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals(m.authorizeUrl, opened.dataString)

        // waiting for Discord: what to do if Discord does not know the redirect, and a way out
        assertTrue(string(R.string.acc_waiting) in screen)
        assertTrue(string(R.string.acc_redirect_hint, DiscordOAuth.REDIRECT_URI) in screen)
        assertFalse(string(R.string.acc_link) in screen)
        // while Discord's page is just opening, the buttons in Link's place do nothing (a double tap on Link)
        assertFalse(screen.isEnabled(string(R.string.common_cancel)))
        assertFalse(screen.isEnabled(string(R.string.acc_restart)))
        settle()
        assertTrue(screen.isEnabled(string(R.string.common_cancel)))
        screen.click(string(R.string.common_cancel))
        assertTrue("cancelAccountLink" in m.actions)
    }

    @Test
    fun aLoginLeftWithoutAnswerCanBeStartedAgain() {
        // e.g. waiting from an earlier process, or Discord's page was left with Back
        val m = model().apply { accountLink.value = LinkState.Waiting }
        val screen = show { StatusPage(m) {} }
        assertTrue(screen.isEnabled(string(R.string.common_cancel)))
        assertTrue(screen.isEnabled(string(R.string.acc_restart)))
        screen.click(string(R.string.acc_restart))
        frames()
        assertEquals(listOf("startAccountLink"), m.actions.filter { it == "startAccountLink" })
        assertEquals(m.authorizeUrl, shadowOf(activity).nextStartedActivity.dataString)
        assertTrue(string(R.string.acc_waiting) in screen)
    }

    @Test
    fun aDoubleTapOnLinkStartsOneLogin() {
        val m = model()
        val screen = show { StatusPage(m) {} }
        screen.click(string(R.string.acc_link))
        screen.click(string(R.string.acc_link))
        frames()
        assertEquals(1, m.actions.count { it == "startAccountLink" })
        assertTrue(shadowOf(activity).nextStartedActivity != null)
        assertEquals(null, shadowOf(activity).nextStartedActivity)
        assertFalse("cancelAccountLink" in m.actions)
    }

    @Test
    fun withoutAnApplicationIdThereIsNoLogin() {
        val m = model(defaultClientId = "")
        m.authorizeUrl = null
        val screen = show { StatusPage(m) {} }
        assertFalse(screen.isEnabled(string(R.string.acc_link)))
        assertTrue(string(R.string.acc_no_app_id) in screen)
    }

    @Test
    fun theLinkedAccountWithItsNameAndPicture() {
        val m = model(account = flash)
        val screen = show { StatusPage(m) {} }
        assertTrue("Fl4sh" in screen)
        assertTrue("@flash" in screen)
        assertTrue(string(R.string.cd_account_avatar, "Fl4sh") in screen.descriptions)
        assertEquals(listOf("https://cdn.discordapp.com/avatars/80351110224678912/8342729096ea3675442027381ff50dfe.png?size=128"), m.loadedPictures)
        assertTrue("the initial while the picture is missing", "F" in screen)
        assertFalse(string(R.string.acc_not_linked) in screen)
        assertFalse(string(R.string.acc_link) in screen)
        assertTrue(screen.isEnabled(string(R.string.acc_refresh)))

        screen.click(string(R.string.acc_refresh))
        frames()
        assertTrue("startAccountLink" in m.actions)
        // back from Discord; then unlinked
        m.accountLink.value = LinkState.Idle
        frames()
        screen.click(string(R.string.acc_unlink))
        frames()
        assertTrue("unlinkAccount" in m.actions)
        assertTrue(string(R.string.acc_not_linked) in screen)
        assertFalse("Fl4sh" in screen)
    }

    @Test
    fun thePictureReplacesTheInitial() {
        val m = model(account = flash.copy(globalName = "")).apply { pictures[flash.avatarUrl] = ImageBitmap(8, 8) }
        val screen = show { StatusPage(m) {} }
        assertTrue(string(R.string.cd_account_avatar, "flash") in screen.descriptions)
        assertFalse("F" in screen)
        // the username is the display name: not twice
        assertFalse("@flash" in screen)
    }

    @Test
    fun aNewPictureOrAccountDoesNotKeepTheOldPicture() {
        val m = model(account = flash).apply { pictures[flash.avatarUrl] = ImageBitmap(8, 8) }
        val screen = show { StatusPage(m) {} }
        assertFalse("the picture, not the initial", "F" in screen)

        // Refresh after the picture was changed on Discord: the new one is loaded (offline here: the initial)
        val changed = flash.copy(avatar = "a_0123456789abcdef0123456789abcdef")
        m.account.value = changed
        frames()
        assertEquals(changed.avatarUrl, m.loadedPictures.last())
        assertTrue("F" in screen)

        // unlinked: no picture of the account before
        m.account.value = flash
        frames()
        assertFalse("F" in screen)
        m.account.value = null
        frames()
        assertTrue(string(R.string.acc_not_linked) in screen)
        // another account (no picture of its own yet): neither the old picture nor the old initial
        m.account.value = LinkedAccount("175928847299117063", "zed", "Zed", "")
        frames()
        assertEquals("https://cdn.discordapp.com/embed/avatars/2.png", m.loadedPictures.last())
        assertTrue("Z" in screen)
    }

    @Test
    fun withoutAvatarDiscordsDefaultPicture() {
        val m = model(account = flash.copy(avatar = ""))
        show { StatusPage(m) {} }
        assertEquals(listOf("https://cdn.discordapp.com/embed/avatars/5.png"), m.loadedPictures)
    }

    @Test
    fun failuresInPlainWords() {
        val detail = "HTTP 429: You are being rate limited."
        for (error in LinkError.entries) {
            val m = model().apply { accountLink.value = LinkState.Failed(error, detail) }
            val screen = show { StatusPage(m) {} }
            val expected = when (error) {
                LinkError.CANCELLED -> string(R.string.acc_err_cancelled)
                LinkError.REDIRECT_NOT_ADDED -> string(R.string.acc_err_redirect, DiscordOAuth.REDIRECT_URI)
                LinkError.NOT_PUBLIC_CLIENT -> string(R.string.acc_err_public_client)
                LinkError.EXPIRED -> string(R.string.acc_err_expired)
                LinkError.NETWORK -> string(R.string.acc_err_network)
                LinkError.DISCORD_ERROR -> string(R.string.acc_err_discord)
                LinkError.BAD_ANSWER -> string(R.string.acc_err_bad_answer)
            }
            assertTrue("$error", expected in screen)
            val detailShown = string(R.string.conn_detail, detail) in screen
            assertEquals("$error", error in setOf(LinkError.DISCORD_ERROR, LinkError.BAD_ANSWER, LinkError.NETWORK), detailShown)
            // try again
            assertTrue("$error", screen.isEnabled(string(R.string.acc_link)))
        }
        assertTrue(string(R.string.acc_err_public_client).contains("Public Client"))
        assertTrue(string(R.string.acc_err_redirect, DiscordOAuth.REDIRECT_URI).contains("powerampdiscordrp://auth/callback"))
    }

    @Test
    fun whileTheProfileIsReadNothingCanBeStarted() {
        val m = model().apply { accountLink.value = LinkState.Working }
        val screen = show { StatusPage(m) {} }
        assertTrue(string(R.string.acc_working) in screen)
        assertFalse(string(R.string.acc_link) in screen)
        assertFalse(string(R.string.common_cancel) in screen)
    }

    // ---- live preview (Display tab)

    @Test
    fun thePreviewShowsTheLinkedAccount() {
        val screen = show { DisplayPage(model(account = flash)) }
        assertTrue("Fl4sh" in screen)
        assertTrue(string(R.string.cd_account_avatar, "Fl4sh") in screen.descriptions)
        assertFalse(string(R.string.prev_you) in screen)
        assertFalse(string(R.string.prev_link_account) in screen)
    }

    @Test
    fun withoutAnAccountThePreviewShowsYouAndLinksIt() {
        val m = model()
        val screen = show { DisplayPage(m) }
        assertTrue(string(R.string.prev_you) in screen)
        screen.click(string(R.string.prev_link_account))
        frames()
        assertTrue("startAccountLink" in m.actions)
        assertEquals(m.authorizeUrl, shadowOf(activity).nextStartedActivity.dataString)
        assertTrue(string(R.string.prev_you) in screen)
        // a second tap while Discord's page opens is ignored
        screen.click(string(R.string.prev_link_account))
        frames()
        assertEquals(1, m.actions.count { it == "startAccountLink" })
        // still waiting later (the page was left without an answer): the hint starts a fresh login
        settle()
        screen.click(string(R.string.prev_link_account))
        frames()
        assertEquals(2, m.actions.count { it == "startAccountLink" })
        // while the profile is read: no login from here
        m.accountLink.value = LinkState.Working
        frames()
        assertFalse(string(R.string.prev_link_account) in screen)
    }

    @Test
    fun withoutAnApplicationIdThePreviewOffersNoLink() {
        val screen = show { DisplayPage(model(defaultClientId = "")) }
        assertTrue(string(R.string.prev_you) in screen)
        assertFalse(string(R.string.prev_link_account) in screen)
    }
}
