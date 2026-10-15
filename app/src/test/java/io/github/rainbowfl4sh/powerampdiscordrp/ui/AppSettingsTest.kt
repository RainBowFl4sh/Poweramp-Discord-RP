package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.app.Application
import android.app.Dialog
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.runtime.collectAsState
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.settings.EnvState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.AppTheme
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.Appearance
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.ThemeMode
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowDialog
import java.time.Duration
import org.robolectric.annotation.Config as RobolectricConfig

/** The top bar's three-dot menu opens the app settings; every choice goes to the model at once. */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34], application = Application::class)   // the app's own Application wires the whole runtime
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSettingsTest {
    private val scope = MainScope()

    // a finished environment check: an endless progress animation would keep Robolectric's looper busy
    private val env = EnvState("build-1032-uni", 1032, true, true, true, true, true)

    @Before
    fun setUp() {
        ShadowDialog.reset()
        // An open menu (a Compose popup) asks for a frame on every frame to follow its anchor: idling the looper
        // until nothing is left would never end. Frames come only when settle() moves the clock.
        ShadowChoreographer.setPaused(true)
    }

    @After
    fun tearDown() {
        scope.cancel()
        ShadowChoreographer.setPaused(false)
    }

    /** Lets [frames] frames pass, enough for the menu's and the dialog's animations. */
    private fun settle(frames: Int = 30) = repeat(frames) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) }

    private fun string(@StringRes id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    /** The main screen themed like MainActivity does it, from the model's appearance. */
    private fun showMainScreen(model: FakeAppModel): ComposedTexts {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { AppTheme(model.appearance.collectAsState().value) { MainScreen(model) } }
        settle()
        return ComposedTexts(activity.window.decorView)
    }

    /** Opens the menu and picks "App settings"; the dialog that opens. */
    private fun openAppSettings(screen: ComposedTexts): Pair<Dialog, ComposedTexts> {
        screen.click(string(R.string.menu_more))
        settle()
        val menu = ComposedTexts.everyWindow()
        assertTrue(string(R.string.app_settings_title) in menu)
        menu.click(string(R.string.app_settings_title))
        settle()
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        return dialog to ComposedTexts(dialog.window!!.decorView)
    }

    private fun ComposedTexts.clickAndWait(text: String) {
        click(string(textIds.getValue(text)))
        settle()
    }

    private val textIds = mapOf(
        "system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark,
        "amoled" to R.string.app_settings_amoled, "dynamic" to R.string.app_settings_dynamic, "close" to R.string.common_close,
    )

    @Test
    fun theMenuOpensTheAppSettings() {
        val model = FakeAppModel(scope, env = env)
        val (dialog, settings) = openAppSettings(showMainScreen(model))
        for (id in listOf(
            R.string.app_settings_theme, R.string.theme_system, R.string.theme_light, R.string.theme_dark,
            R.string.app_settings_amoled, R.string.app_settings_dynamic,
        )) {
            assertTrue(string(id), string(id) in settings)
        }
        // nothing changed by only looking
        assertEquals(emptyList<String>(), model.actions.filter { it.startsWith("setAppearance") })

        settings.clickAndWait("close")
        assertFalse(dialog.isShowing)
        assertEquals(Appearance(), model.appearance.value)
    }

    @Test
    fun everyChoiceGoesToTheModel() {
        val model = FakeAppModel(scope, env = env)
        val (_, settings) = openAppSettings(showMainScreen(model))
        // Robolectric's system is light: AMOLED black has no effect yet
        assertFalse(settings.isEnabled(string(R.string.app_settings_amoled)))
        assertTrue(string(R.string.app_settings_amoled_needs_dark) in settings)

        settings.clickAndWait("dark")
        assertEquals(Appearance(theme = ThemeMode.DARK), model.appearance.value)
        assertTrue("setAppearance ${Appearance(theme = ThemeMode.DARK)}" in model.actions)
        assertTrue(settings.isEnabled(string(R.string.app_settings_amoled)))
        assertTrue(string(R.string.app_settings_amoled_on) in settings)

        settings.clickAndWait("amoled")
        assertEquals(Appearance(theme = ThemeMode.DARK, amoled = true), model.appearance.value)

        settings.clickAndWait("dynamic")
        assertEquals(Appearance(theme = ThemeMode.DARK, amoled = true, dynamicColor = false), model.appearance.value)

        // back to the (light) system: AMOLED keeps its value but is off again
        settings.clickAndWait("system")
        assertEquals(Appearance(theme = ThemeMode.SYSTEM, amoled = true, dynamicColor = false), model.appearance.value)
        assertFalse(settings.isEnabled(string(R.string.app_settings_amoled)))

        settings.clickAndWait("light")
        assertEquals(ThemeMode.LIGHT, model.appearance.value.theme)
        // the chosen option again changes nothing
        val count = model.actions.size
        settings.clickAndWait("light")
        assertEquals(count, model.actions.size)
    }

    @Test
    fun amoledIsOfferedWhileTheSystemIsDark() {
        RuntimeEnvironment.setQualifiers("+night")
        val model = FakeAppModel(scope, env = env)
        val (_, settings) = openAppSettings(showMainScreen(model))
        assertTrue(settings.isEnabled(string(R.string.app_settings_amoled)))
        settings.clickAndWait("amoled")
        assertEquals(Appearance(amoled = true), model.appearance.value)

        settings.clickAndWait("light")
        assertFalse(settings.isEnabled(string(R.string.app_settings_amoled)))
    }

    @Test
    fun withoutDynamicColorThereIsNoSwitchForIt() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            AppTheme { AppSettingsDialog(Appearance(), onChange = {}, onDismiss = {}, dynamicColorAvailable = false) }
        }
        settle()
        val settings = ComposedTexts(ShadowDialog.getLatestDialog().window!!.decorView)
        assertTrue(string(R.string.app_settings_amoled) in settings)
        assertFalse(string(R.string.app_settings_dynamic) in settings)
    }

    @Test
    fun theMenuIsClosedAtFirst() {
        showMainScreen(FakeAppModel(scope, env = env))
        assertFalse(string(R.string.app_settings_title) in ComposedTexts.everyWindow())
        assertNull(ShadowDialog.getLatestDialog())
    }
}
