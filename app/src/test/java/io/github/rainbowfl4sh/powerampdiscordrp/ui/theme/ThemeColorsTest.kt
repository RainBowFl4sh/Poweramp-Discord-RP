package io.github.rainbowfl4sh.powerampdiscordrp.ui.theme

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import androidx.activity.ComponentActivity
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The colors each app setting gives, and the window (background, system bar icons) following them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ThemeColorsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun scheme(dark: Boolean, amoled: Boolean = false, dynamic: Boolean = false) =
        appColorScheme(context, ResolvedTheme(dark, amoled, dynamic))

    @Test
    fun theAppsOwnBlurpleWithoutDynamicColor() {
        assertEquals(Color(0xFF4A55D6), scheme(dark = false).primary)
        assertEquals(Color(0xFFFBF8FF), scheme(dark = false).background)
        assertEquals(Color(0xFFBEC2FF), scheme(dark = true).primary)
        assertEquals(Color(0xFF131318), scheme(dark = true).background)
    }

    @Test
    fun dynamicColorFromTheWallpaper() {
        assertEquals(dynamicLightColorScheme(context).primary, scheme(dark = false, dynamic = true).primary)
        assertEquals(dynamicDarkColorScheme(context).primary, scheme(dark = true, dynamic = true).primary)
        assertEquals(dynamicDarkColorScheme(context).background, scheme(dark = true, dynamic = true).background)
    }

    @Test
    fun amoledIsPureBlackWithCardsThatStillStandOut() {
        for (dynamic in listOf(false, true)) {
            val normal = scheme(dark = true, dynamic = dynamic)
            val amoled = scheme(dark = true, amoled = true, dynamic = dynamic)
            for (c in listOf(amoled.background, amoled.surface, amoled.surfaceDim, amoled.surfaceContainerLowest)) {
                assertEquals(Color.Black, c)
            }
            // cards, menus and dialogs: darker than usual, still lighter than the background, in their usual order
            val containers = listOf(amoled.surfaceContainerLow, amoled.surfaceContainer, amoled.surfaceContainerHigh, amoled.surfaceContainerHighest)
            assertTrue(containers.first().luminance() > 0f)
            assertEquals(containers.sortedBy { it.luminance() }, containers)
            assertTrue(amoled.surfaceContainerLow.luminance() < normal.surfaceContainerLow.luminance())
            // texts and accents as usual
            assertEquals(normal.primary, amoled.primary)
            assertEquals(normal.onSurface, amoled.onSurface)
            assertEquals(normal.onBackground, amoled.onBackground)
        }
    }

    @Test
    fun amoledNeverDarkensALightTheme() {
        assertEquals(Color(0xFFFBF8FF), scheme(dark = false, amoled = true).background)
    }

    @Test
    fun theWindowFollowsTheTheme() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

        activity.applyWindowTheme(ResolvedTheme(dark = true, amoled = true, dynamicColor = false))
        assertEquals(Color.Black.toArgb(), windowColor(activity))
        assertFalse(bars.isAppearanceLightStatusBars)
        assertFalse(bars.isAppearanceLightNavigationBars)

        activity.applyWindowTheme(ResolvedTheme(dark = false, amoled = false, dynamicColor = false))
        assertEquals(0xFFFBF8FF.toInt(), windowColor(activity))
        assertTrue(bars.isAppearanceLightStatusBars)
        assertTrue(bars.isAppearanceLightNavigationBars)

        activity.applyWindowTheme(ResolvedTheme(dark = true, amoled = false, dynamicColor = false))
        assertEquals(0xFF131318.toInt(), windowColor(activity))
        assertFalse(bars.isAppearanceLightStatusBars)
    }

    @Test
    fun theActivityThemeBeforeTheFirstFrame() {
        fun windowBackground(style: Int): Int {
            val values = context.obtainStyledAttributes(style, intArrayOf(android.R.attr.windowBackground))
            try {
                return values.getColor(0, 0)
            } finally {
                values.recycle()
            }
        }
        assertEquals(0xFF131318.toInt(), windowBackground(windowThemeStyle(ResolvedTheme(dark = true, amoled = true, dynamicColor = true))))
        assertEquals(0xFFFBF8FF.toInt(), windowBackground(windowThemeStyle(ResolvedTheme(dark = false, amoled = false, dynamicColor = true))))
    }

    @Test
    fun theSystemsNightMode() {
        val night = Configuration().apply { uiMode = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES }
        val day = Configuration().apply { uiMode = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO }
        val unknown = Configuration().apply { uiMode = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_UNDEFINED }
        assertTrue(isNightMode(night))
        assertFalse(isNightMode(day))
        assertFalse(isNightMode(unknown))
    }

    private fun windowColor(activity: ComponentActivity): Int {
        val background = activity.window.decorView.background
        val drawable = if (background is InsetDrawable) background.drawable else background
        return (drawable as ColorDrawable).color
    }
}
