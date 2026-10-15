package io.github.rainbowfl4sh.powerampdiscordrp.ui.theme

import android.os.Build
import androidx.compose.runtime.Immutable

/** Light / dark choice of the app settings. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * How the app looks (top bar menu -> App settings). Kept apart from the presence settings: changing it never touches
 * the engine or what Discord shows.
 */
@Immutable
data class Appearance(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Pure black backgrounds while the dark theme is in effect (OLED screens). */
    val amoled: Boolean = false,
    /** Material You colors from the wallpaper on Android 12+; off = the app's own blurple scheme. */
    val dynamicColor: Boolean = true,
)

/** What the theme really draws with, once the system's night mode and the Android version are known. */
@Immutable
data class ResolvedTheme(val dark: Boolean, val amoled: Boolean, val dynamicColor: Boolean) {
    companion object {
        fun of(appearance: Appearance, systemDark: Boolean, sdkInt: Int = Build.VERSION.SDK_INT): ResolvedTheme {
            val dark = when (appearance.theme) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            return ResolvedTheme(
                dark = dark,
                // black backgrounds in a light theme would only be a broken light theme
                amoled = dark && appearance.amoled,
                dynamicColor = appearance.dynamicColor && supportsDynamicColor(sdkInt),
            )
        }
    }
}

/** Dynamic color (Material You) exists from Android 12 on. */
fun supportsDynamicColor(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S
