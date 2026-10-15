package io.github.rainbowfl4sh.powerampdiscordrp.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Theme mode + the system's night mode + AMOLED + dynamic color -> what the theme draws with. */
class AppearanceTest {
    private val android14 = 34
    private val android11 = 30

    private fun resolve(appearance: Appearance, systemDark: Boolean, sdk: Int = android14) =
        ResolvedTheme.of(appearance, systemDark, sdk)

    @Test
    fun defaultsFollowTheSystemWithDynamicColor() {
        assertEquals(Appearance(ThemeMode.SYSTEM, amoled = false, dynamicColor = true), Appearance())
        assertEquals(ResolvedTheme(dark = false, amoled = false, dynamicColor = true), resolve(Appearance(), systemDark = false))
        assertEquals(ResolvedTheme(dark = true, amoled = false, dynamicColor = true), resolve(Appearance(), systemDark = true))
    }

    @Test
    fun lightAndDarkIgnoreTheSystem() {
        for (systemDark in listOf(false, true)) {
            assertFalse(resolve(Appearance(theme = ThemeMode.LIGHT), systemDark).dark)
            assertTrue(resolve(Appearance(theme = ThemeMode.DARK), systemDark).dark)
        }
    }

    @Test
    fun amoledOnlyWhileDark() {
        val amoled = Appearance(amoled = true)
        assertTrue(resolve(amoled.copy(theme = ThemeMode.DARK), systemDark = false).amoled)
        assertTrue(resolve(amoled, systemDark = true).amoled)
        assertFalse(resolve(amoled, systemDark = false).amoled)
        assertFalse(resolve(amoled.copy(theme = ThemeMode.LIGHT), systemDark = true).amoled)
        assertFalse(resolve(Appearance(theme = ThemeMode.DARK, amoled = false), systemDark = true).amoled)
    }

    @Test
    fun dynamicColorOnlyFromAndroid12() {
        assertTrue(resolve(Appearance(), false, sdk = 31).dynamicColor)
        assertFalse(resolve(Appearance(), false, sdk = android11).dynamicColor)
        assertFalse(resolve(Appearance(dynamicColor = false), false, sdk = android14).dynamicColor)
        assertTrue(supportsDynamicColor(31))
        assertFalse(supportsDynamicColor(26))
    }
}
