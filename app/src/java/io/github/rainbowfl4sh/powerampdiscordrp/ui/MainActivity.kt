package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.AppTheme
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.AppearanceStore
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.ResolvedTheme
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.applyWindowTheme
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.isNightMode
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.windowThemeStyle

/** Settings / status screen (Compose). */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** The theme the window (background, system bar icons) shows now. */
    private var windowTheme: ResolvedTheme? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // The chosen theme is known before the first frame (a tiny preferences file): window and system bars match it
        // at once - no white flash in dark mode, no black flash in light mode
        val start = ResolvedTheme.of(AppearanceStore.get(this).appearance.value, isNightMode(resources.configuration))
        setTheme(windowThemeStyle(start))
        showWindowTheme(start)
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val theme = ResolvedTheme.of(appearance, isSystemInDarkTheme())
            // a changed app setting: system bar icons and the window behind Compose follow it
            LaunchedEffect(theme) { showWindowTheme(theme) }
            AppTheme(theme) {
                MainScreen(viewModel)
            }
        }
    }

    private fun showWindowTheme(theme: ResolvedTheme) {
        if (theme == windowTheme) return
        applyWindowTheme(theme)
        windowTheme = theme
    }

    override fun onResume() {
        super.onResume()
        // Poweramp may be playing already: its sticky broadcasts give the current track even if this app missed them
        viewModel.syncPoweramp()
    }

    override fun onStop() {
        // typed text is saved debounced - leaving the app must not lose the last keystrokes
        viewModel.editor.flush()
        super.onStop()
    }
}
