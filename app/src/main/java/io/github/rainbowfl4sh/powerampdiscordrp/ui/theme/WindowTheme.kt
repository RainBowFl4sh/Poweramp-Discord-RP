package io.github.rainbowfl4sh.powerampdiscordrp.ui.theme

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StyleRes
import androidx.compose.ui.graphics.toArgb
import io.github.rainbowfl4sh.powerampdiscordrp.R

// The scrims enableEdgeToEdge() uses by default (the platform's): 3-button navigation on Android 8 / 9
private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/** The system's night mode in [configuration] (what "Follow system" follows). */
fun isNightMode(configuration: Configuration): Boolean =
    configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/** The activity theme (themes.xml) with a light or dark window, as [theme] is. */
@StyleRes
fun windowThemeStyle(theme: ResolvedTheme): Int =
    if (theme.dark) R.style.Theme_PowerampDiscordRP_Dark else R.style.Theme_PowerampDiscordRP_Light

/**
 * Edge-to-edge with the system bar icons of [theme] (not of the system's night mode, which the default styles follow)
 * and the theme's background behind the content: no white flash in dark mode or black flash in light mode while
 * Compose draws its first frame. Called again when the app settings change.
 */
fun ComponentActivity.applyWindowTheme(theme: ResolvedTheme) {
    val dark = theme.dark
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
        navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { dark },
    )
    window.setBackgroundDrawable(ColorDrawable(appColorScheme(this, theme).background.toArgb()))
}
