package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.DisplayPage
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
 * The texts section of the Display tab and the rotation ("||"): the separator chip inserts into the template field used
 * last like the placeholder chips, the help line and the rotation interval slider are shown and change the settings.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34], application = Application::class)   // the app's own Application wires the whole runtime
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DisplayPageTest {
    private val scope = MainScope()
    private val activities = mutableListOf<ActivityController<ComponentActivity>>()

    @Before
    fun setUp() {
        // A focused field's cursor blinks for as long as it has the focus: frames come only when frames() moves the clock
        ShadowChoreographer.setPaused(true)
    }

    @After
    fun tearDown() {
        activities.forEach { it.pause().stop().destroy() }
        frames()
        scope.cancel()
        ShadowChoreographer.setPaused(false)
    }

    /** Lets Compose apply changes and draw a few frames (16 ms each). */
    private fun frames() = repeat(8) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) }

    private fun show(model: FakeAppModel): ComposedTexts {
        if (activities.isEmpty()) MainThreadCompose.reset()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activities += controller
        controller.get().setContent { AppTheme { DisplayPage(model) } }
        frames()
        return ComposedTexts(controller.get().window.decorView)
    }

    /** Without arguments the text is not formatted: "%artist%" in the rotation help is no format argument. */
    private fun string(@StringRes id: Int, vararg args: Any): String =
        RuntimeEnvironment.getApplication().run { if (args.isEmpty()) getString(id) else getString(id, *args) }

    @Test
    fun theSeparatorChipInsertsIntoTheFirstLine() {
        val model = FakeAppModel(scope)
        val screen = show(model)
        assertTrue(string(R.string.disp_insert_into, string(R.string.disp_details)) in screen)
        screen.click("||")
        frames()
        assertEquals("%title% || ", model.config.value.details)
        // the cursor stays behind it: the next placeholder is the second alternative
        screen.click("%artist%")
        frames()
        assertEquals("%title% || %artist%", model.config.value.details)
        assertEquals("%artist%", model.config.value.state)
        // saved like typing (after the debounce)
        model.editor.flush()
        assertEquals("%title% || %artist%", model.stored.value.details)
    }

    @Test
    fun theSeparatorChipInsertsIntoTheFieldUsedLast() {
        val model = FakeAppModel(scope, config = Config(largeText = "%album%"))
        val screen = show(model)
        screen.focusField("%album%")
        frames()
        assertTrue(string(R.string.disp_insert_into, string(R.string.disp_large_text)) in screen)
        screen.click("||")
        frames()
        screen.click("%codec%")
        frames()
        assertEquals("%album% || %codec%", model.config.value.largeText)
        assertEquals(Config().details, model.config.value.details)
    }

    @Test
    fun helpLineAndPlaceholderHelpExplainTheSeparator() {
        val screen = show(FakeAppModel(scope))
        assertTrue(string(R.string.disp_rotation_help) in screen)
        assertFalse(string(R.string.ph_rotation) in screen)
        screen.click(string(R.string.disp_placeholders_help))
        frames()
        assertTrue(string(R.string.ph_rotation) in screen)
        assertTrue(string(R.string.ph_title) in screen)
    }

    @Test
    fun theRotationIntervalSliderEditsTheSettings() {
        val model = FakeAppModel(scope)
        val screen = show(model)
        assertTrue(string(R.string.disp_rotation) in screen)
        assertTrue(string(R.string.disp_rotation_value, 10) in screen)
        assertTrue(string(R.string.disp_rotation_note) in screen)

        screen.slide(Config.ROTATION_MIN_SECONDS.toFloat()..Config.ROTATION_MAX_SECONDS.toFloat(), 17f)
        frames()
        assertEquals(17, model.config.value.rotationSeconds)
        assertTrue(string(R.string.disp_rotation_value, 17) in screen)
        model.editor.flush()
        assertEquals(17, model.stored.value.rotationSeconds)
        // the refresh interval is a slider of its own
        assertEquals(Config().refreshSeconds, model.stored.value.refreshSeconds)
    }

    @Test
    fun anIntervalOutsideTheSliderShowsWhatIsUsed() {
        val screen = show(FakeAppModel(scope, config = Config(rotationSeconds = 600)))
        assertTrue(string(R.string.disp_rotation_value, Config.ROTATION_MAX_SECONDS) in screen)
        assertFalse(string(R.string.disp_rotation_value, 600) in screen)
    }

    @Test
    fun theTextsSectionInEveryLanguage() {
        for (lang in listOf("en", "de", "ru", "uk")) {
            RuntimeEnvironment.setQualifiers(lang)
            val screen = show(FakeAppModel(scope, config = Config(details = "%title% || %artist%", rotationSeconds = 25)))
            assertTrue(lang, string(R.string.disp_rotation_help) in screen)
            assertTrue(lang, string(R.string.disp_rotation_value, 25) in screen)
            assertTrue(lang, string(R.string.disp_rotation_note) in screen)
            assertTrue(lang, "||" in screen)
        }
    }
}
