package io.github.rainbowfl4sh.powerampdiscordrp.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.rainbowfl4sh.powerampdiscordrp.ui.NotificationAskState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppearanceStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(AppearanceStore.PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun clearPrefs() {
        prefs.edit().clear().commit()
    }

    @Test
    fun defaultsWithoutStoredSettings() {
        assertEquals(Appearance(), AppearanceStore(context).appearance.value)
    }

    @Test
    fun changesAreVisibleAtOnceAndPersisted() {
        val store = AppearanceStore(context)
        val chosen = Appearance(theme = ThemeMode.DARK, amoled = true, dynamicColor = false)
        store.update { chosen }
        assertEquals(chosen, store.appearance.value)
        assertEquals("DARK", prefs.getString(AppearanceStore.KEY_THEME, null))
        // the next process start reads the same
        assertEquals(chosen, AppearanceStore(context).appearance.value)

        store.update { it.copy(theme = ThemeMode.LIGHT) }
        assertEquals(Appearance(ThemeMode.LIGHT, amoled = true, dynamicColor = false), AppearanceStore(context).appearance.value)
    }

    @Test
    fun anUnknownThemeFallsBackToTheSystem() {
        prefs.edit().putString(AppearanceStore.KEY_THEME, "SEPIA").putBoolean(AppearanceStore.KEY_AMOLED, true).commit()
        assertEquals(Appearance(theme = ThemeMode.SYSTEM, amoled = true), AppearanceStore(context).appearance.value)
    }

    @Test
    fun sharesTheFileWithTheScreensOtherState() {
        NotificationAskState(context).asked = true
        AppearanceStore(context).update { it.copy(amoled = true) }
        assertTrue(NotificationAskState(context).asked)
        assertTrue(AppearanceStore(context).appearance.value.amoled)
    }

    @Test
    fun oneStorePerProcess() {
        assertSame(AppearanceStore.get(context), AppearanceStore.get(context))
    }
}
