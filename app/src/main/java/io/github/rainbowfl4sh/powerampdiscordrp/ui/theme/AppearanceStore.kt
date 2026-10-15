package io.github.rainbowfl4sh.powerampdiscordrp.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists [Appearance] in the "ui" SharedPreferences (the screen's own state, not the presence settings: export,
 * import and reset leave it alone). Every change is visible at once through [appearance].
 */
class AppearanceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _appearance = MutableStateFlow(load())

    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    fun update(change: (Appearance) -> Appearance) {
        synchronized(lock) {
            val old = _appearance.value
            val new = change(old)
            if (new == old) return
            prefs.edit()
                .putString(KEY_THEME, new.theme.name)
                .putBoolean(KEY_AMOLED, new.amoled)
                .putBoolean(KEY_DYNAMIC_COLOR, new.dynamicColor)
                .apply()
            _appearance.value = new
        }
    }

    private fun load(): Appearance {
        val defaults = Appearance()
        val theme = prefs.getString(KEY_THEME, null)
        return Appearance(
            // an unknown name (a newer version's choice) falls back to the default
            theme = ThemeMode.entries.firstOrNull { it.name == theme } ?: defaults.theme,
            amoled = prefs.getBoolean(KEY_AMOLED, defaults.amoled),
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, defaults.dynamicColor),
        )
    }

    companion object {
        /** Shared with NotificationAskState (other keys). */
        const val PREFS_NAME = "ui"
        const val KEY_THEME = "theme"
        const val KEY_AMOLED = "amoledBlack"
        const val KEY_DYNAMIC_COLOR = "dynamicColor"

        @Volatile
        private var instance: AppearanceStore? = null

        /** The process-wide store: MainActivity reads it before its first frame, the view model changes it. */
        fun get(context: Context): AppearanceStore =
            instance ?: synchronized(this) { instance ?: AppearanceStore(context).also { instance = it } }
    }
}
