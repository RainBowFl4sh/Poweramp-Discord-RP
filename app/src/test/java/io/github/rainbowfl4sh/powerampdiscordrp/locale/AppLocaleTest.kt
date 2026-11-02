package io.github.rainbowfl4sh.powerampdiscordrp.locale

import android.app.Application
import android.content.Context
import android.os.Build
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.AppLanguages
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** The app's own language: kept by the system on Android 13+, by the app (and applied where texts are made) before. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppLocaleTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val defaultLocale = Locale.getDefault()
    private val old = Build.VERSION_CODES.S   // Android 12

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun android12KeepsTheChoiceAndAppliesItToTexts() {
        assertNull(AppLocale.current(context, old))
        assertSame(context, AppLocale.wrap(context, old))
        assertEquals("Playing", AppLocale.resources(context, old).getString(R.string.status_playing))

        assertTrue(AppLocale.set(context, AppLanguages.byTag("de"), old))   // the activity is recreated
        assertEquals("de", AppLocale.current(context, old)?.tag)
        assertEquals("Wiedergabe", AppLocale.resources(context, old).getString(R.string.status_playing))
        val wrapped = AppLocale.wrap(context, old)
        assertEquals("de", wrapped.resources.configuration.locales[0].language)
        assertEquals("Sprache", wrapped.getString(R.string.app_settings_language))
        assertEquals("de", Locale.getDefault().language)

        // right to left
        AppLocale.set(context, AppLanguages.byTag("ar"), old)
        assertEquals(View.LAYOUT_DIRECTION_RTL, AppLocale.wrap(context, old).resources.configuration.layoutDirection)

        // back to the system's language
        AppLocale.set(context, null, old)
        assertNull(AppLocale.current(context, old))
        assertEquals("Playing", AppLocale.resources(context, old).getString(R.string.status_playing))
    }

    @Test
    fun android13LeavesTheTextsToTheSystem() {
        AppLocale.set(context, AppLanguages.byTag("de"), old)
        // a choice saved on Android 12 is not applied by the app on 13: the system does it (after migrate())
        assertSame(context, AppLocale.wrap(context))
        assertSame(context.resources, AppLocale.resources(context))
        AppLocale.migrate(context)
        assertNull(AppLocale.current(context, old))   // handed over: no longer kept by the app
    }
}
