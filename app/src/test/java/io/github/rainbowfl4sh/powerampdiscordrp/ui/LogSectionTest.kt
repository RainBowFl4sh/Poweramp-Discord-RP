package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.AdvancedPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.AppTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** The recent log on the Advanced tab: only the lines in view are composed, the newest at the bottom. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogSectionTest {
    private val scope = MainScope()

    @After
    fun tearDown() = scope.cancel()

    private fun frames() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))

    private fun logLines(root: View) = ComposedTexts(root).texts.filter { "  line " in it }

    @Test
    fun theNewestLinesInViewAreComposedAndFollowed() {
        val model = FakeAppModel(scope)
        model.logLines.value = List(300) { "12:00:00  line $it" }
        MainThreadCompose.reset()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { AppTheme { AdvancedPage(model) } }
        frames()

        val shown = logLines(activity.window.decorView)
        assertTrue(shown.isNotEmpty())
        assertEquals("12:00:00  line 299", shown.last())   // scrolled to the newest
        assertTrue("only the lines in view, not all 150: ${shown.size}", shown.size < 40)

        model.logLines.value = model.logLines.value + "12:00:01  line 300"
        frames()
        assertEquals("12:00:01  line 300", logLines(activity.window.decorView).last())
    }
}
