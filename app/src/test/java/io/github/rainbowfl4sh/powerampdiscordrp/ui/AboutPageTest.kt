package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.app.Application
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import io.github.rainbowfl4sh.powerampdiscordrp.BuildConfig
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.GitHubProfile
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.AboutPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.AppTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import kotlin.math.abs
import org.robolectric.annotation.Config as RobolectricConfig

/** About tab header: the author's GitHub picture (a placeholder until it is there) and centered link buttons. */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34], application = Application::class)   // the app's own Application wires the whole runtime
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutPageTest {
    private val scope = MainScope()

    @After
    fun tearDown() = scope.cancel()

    private val user = "RainBowFl4sh"
    private val avatarUrl = "https://avatars.githubusercontent.com/RainBowFl4sh?s=256"

    private fun string(@StringRes id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private lateinit var activity: ComponentActivity

    private fun show(model: FakeAppModel): ComposedTexts {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { AppTheme { AboutPage(model) } }
        ShadowLooper.idleMainLooper()
        return ComposedTexts(activity.window.decorView)
    }

    @Test
    fun theAuthorComesFromTheRepository() {
        assertEquals(user, GitHubProfile.user(BuildConfig.GITHUB_REPO))
        assertEquals(avatarUrl, GitHubProfile.avatarUrl(user))
    }

    @Test
    fun aPlaceholderWhileThePictureIsMissing() {
        val model = FakeAppModel(scope)   // knows no pictures: like offline
        val screen = show(model)
        assertEquals(listOf(avatarUrl), model.loadedPictures)
        assertTrue(string(R.string.cd_author_avatar, "Fl4sh") in screen.descriptions)
        assertTrue("F" in screen)
        assertTrue(string(R.string.about_by, "Fl4sh") in screen)
        assertTrue(string(R.string.about_on_github, user) in screen)
    }

    @Test
    fun thePictureReplacesThePlaceholder() {
        val model = FakeAppModel(scope).apply { pictures[avatarUrl] = ImageBitmap(8, 8) }
        val screen = show(model)
        assertTrue(string(R.string.cd_author_avatar, "Fl4sh") in screen.descriptions)
        assertFalse("F" in screen)
    }

    @Test
    fun theAuthorOpensTheGitHubProfile() {
        val screen = show(FakeAppModel(scope))
        screen.click(string(R.string.about_by, "Fl4sh"))
        val opened = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("https://github.com/$user", opened.dataString)
    }

    /** Checks that every row of link buttons is centered on the screen; the number of rows. */
    private fun assertCenteredLinkRows(what: String): Int {
        val screen = show(FakeAppModel(scope))
        val buttons = listOf(R.string.about_github, R.string.about_releases, R.string.about_issues).map { screen.bounds(string(it)) }
        val middle = screen.rootWidth / 2
        // each row of buttons (they wrap on narrow screens) is centered, "Report a problem" too when alone
        val rows = buttons.groupBy { it.top.toInt() }.values
        rows.forEach { row ->
            val rowMiddle = (row.minOf { it.left } + row.maxOf { it.right }) / 2
            assertTrue("$what: row centered at $rowMiddle, screen middle $middle", abs(rowMiddle - middle) <= 2f)
        }
        return rows.size
    }

    @Test
    fun theLinkButtonsAreCentered() {
        for (lang in listOf("en", "de", "ru", "uk")) {
            RuntimeEnvironment.setQualifiers(lang)
            assertCenteredLinkRows(lang)
        }
        // a phone: "Report a problem" alone in a second row, centered under the other two
        RuntimeEnvironment.setQualifiers("en-w360dp-h740dp")
        assertEquals(2, assertCenteredLinkRows("phone"))
        // a tablet: all three in one row
        RuntimeEnvironment.setQualifiers("en-w800dp-h1280dp")
        assertEquals(1, assertCenteredLinkRows("tablet"))
    }
}
