package io.github.rainbowfl4sh.powerampdiscordrp.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

// Fallback colors (before Android 12 there is no dynamic color): Discord-like blurple with an orange accent
private val LightColors = lightColorScheme(
    primary = Color(0xFF4A55D6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF00006E),
    secondary = Color(0xFF5C5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E0F9),
    onSecondaryContainer = Color(0xFF191A2C),
    tertiary = Color(0xFF8B5000),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
    surfaceDim = Color(0xFFDBD9E0),
    surfaceBright = Color(0xFFFBF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBEC2FF),
    onPrimary = Color(0xFF16239B),
    primaryContainer = Color(0xFF323DBE),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFFC5C4DD),
    onSecondary = Color(0xFF2E2F42),
    secondaryContainer = Color(0xFF444559),
    onSecondaryContainer = Color(0xFFE1E0F9),
    tertiary = Color(0xFFFFB870),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF91909A),
    outlineVariant = Color(0xFF46464F),
    surfaceDim = Color(0xFF131318),
    surfaceBright = Color(0xFF39383F),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A292F),
    surfaceContainerHighest = Color(0xFF35343A),
)

/** Colors Material 3 has no role for: the check marks and warnings of the status page. */
@Immutable
data class StatusColors(val ok: Color, val warning: Color, val error: Color, val info: Color)

private val LightStatus = StatusColors(ok = Color(0xFF1E7E34), warning = Color(0xFF9A6700), error = Color(0xFFBA1A1A), info = Color(0xFF4A55D6))
private val DarkStatus = StatusColors(ok = Color(0xFF7BD88F), warning = Color(0xFFF2C14E), error = Color(0xFFFFB4AB), info = Color(0xFFBEC2FF))

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** Discord's dark theme, used by the live preview whatever the app's theme is (it shows what Discord draws). */
object DiscordColors {
    val Panel = Color(0xFF121214)
    val Card = Color(0xFF1F1F23)
    val Row = Color(0xFF232328)
    val White = Color(0xFFF2F3F5)
    val Text = Color(0xFFDBDEE1)
    val Muted = Color(0xFF949BA4)
    val Track = Color(0xFF4E5058)
    val Green = Color(0xFF23A55A)
    val Blurple = Color(0xFF5865F2)
    val Orange = Color(0xFFF5A623)
    val LogoBackground = Color(0xFF2B2D31)
}

/**
 * The color scheme of [theme]: dynamic color on Android 12+ if wanted, otherwise the blurple scheme; AMOLED black on
 * top. Not composable: MainActivity also needs its background for the window before the first frame.
 */
fun appColorScheme(context: Context, theme: ResolvedTheme): ColorScheme {
    val base = when {
        theme.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (theme.dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        theme.dark -> DarkColors
        else -> LightColors
    }
    return if (theme.dark && theme.amoled) base.toAmoled() else base
}

/**
 * AMOLED black: pure black background and surfaces (top bar, tabs); the containers (cards, dialogs, menus) only get
 * darker, so they still stand out a little and keep the scheme's tint.
 */
internal fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, surfaceContainerLow, 0.7f),
    surfaceContainer = lerp(Color.Black, surfaceContainer, 0.75f),
    surfaceContainerHigh = lerp(Color.Black, surfaceContainerHigh, 0.8f),
    surfaceContainerHighest = lerp(Color.Black, surfaceContainerHighest, 0.85f),
)

/** App theme as the app settings say: light / dark (or the system's), AMOLED black, dynamic color. */
@Composable
fun AppTheme(appearance: Appearance, content: @Composable () -> Unit) =
    AppTheme(ResolvedTheme.of(appearance, isSystemInDarkTheme()), content)

/** App theme with the default settings: dynamic color on Android 12+, otherwise the blurple scheme. */
@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) =
    AppTheme(ResolvedTheme.of(Appearance(), darkTheme), content)

@Composable
fun AppTheme(theme: ResolvedTheme, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = remember(context, theme) { appColorScheme(context, theme) }
    val status = if (theme.dark) DarkStatus else LightStatus
    CompositionLocalProvider(LocalStatusColors provides status.copy(error = scheme.error, info = scheme.primary)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
