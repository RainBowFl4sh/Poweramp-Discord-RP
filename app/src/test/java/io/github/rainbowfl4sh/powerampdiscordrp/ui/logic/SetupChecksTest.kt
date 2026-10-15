package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.settings.EnvState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChecksTest {
    private val builtIn = "1555109720807702559"
    private val own = "123456789012345678"
    private val good = EnvState(
        powerampVersion = "build-1032-uni",
        powerampBuild = 1032,
        discordInstalled = true,
        discordRpcAvailable = true,
        batteryOptimizationIgnored = true,
        notificationsAllowed = true,
        clientIdConfigured = true,
    )

    private fun item(env: EnvState, c: Config = Config(), default: String = builtIn, title: Int) =
        SetupChecks.items(env, c, default).single { it.title == title }

    @Test
    fun everythingFine() {
        val items = SetupChecks.items(good, Config(), builtIn)
        // the built-in Discord application needs no line of its own while it is fine
        assertEquals(4, items.size)
        assertTrue(items.none { it.title == R.string.check_appid_title })
        items.forEach {
            assertEquals(Severity.OK, it.severity)
            assertNull(it.action)
        }
        assertEquals(UiText(R.string.check_poweramp_ok_nobuild, "build-1032-uni"), items[0].detail)
        // an own application is always shown: it replaces the built-in one
        val withOwn = SetupChecks.items(good, Config(useCustomApp = true, customClientId = own), builtIn)
        assertEquals(UiText(R.string.check_appid_own, own), withOwn.single { it.title == R.string.check_appid_title }.detail)
    }

    @Test
    fun poweramp() {
        val missing = item(good.copy(powerampVersion = null, powerampBuild = 0), title = R.string.check_poweramp_title)
        assertEquals(Severity.ERROR, missing.severity)
        assertEquals(CheckAction.GET_POWERAMP, missing.action)

        val old = item(good.copy(powerampBuild = 947), title = R.string.check_poweramp_title)
        assertEquals(Severity.WARNING, old.severity)
        assertEquals(CheckAction.UPDATE_POWERAMP, old.action)
        assertEquals(UiText(R.string.check_poweramp_old, "build-1032-uni", 947, 948), old.detail)

        val first = item(good.copy(powerampVersion = "3.0", powerampBuild = 948), title = R.string.check_poweramp_title)
        assertEquals(Severity.OK, first.severity)
        assertEquals(UiText(R.string.check_poweramp_ok, "3.0", 948), first.detail)

        // the build number could not be read: assume it works
        val unknown = item(good.copy(powerampVersion = "", powerampBuild = 0), title = R.string.check_poweramp_title)
        assertEquals(Severity.OK, unknown.severity)
        assertEquals(UiText(R.string.check_poweramp_ok_nobuild, "?"), unknown.detail)
    }

    @Test
    fun discord() {
        val missing = item(good.copy(discordInstalled = false, discordRpcAvailable = false), title = R.string.check_discord_title)
        assertEquals(CheckAction.GET_DISCORD, missing.action)
        assertEquals(Severity.ERROR, missing.severity)
        val old = item(good.copy(discordRpcAvailable = false), title = R.string.check_discord_title)
        assertEquals(CheckAction.UPDATE_DISCORD, old.action)
        assertEquals(UiText(R.string.check_discord_no_rpc), old.detail)
    }

    @Test
    fun applicationId() {
        val none = item(good, default = "", title = R.string.check_appid_title)
        assertEquals(Severity.ERROR, none.severity)
        assertEquals(CheckAction.SET_APP_ID, none.action)
        assertEquals(UiText(R.string.check_appid_missing), none.detail)

        val mine = item(good, Config(useCustomApp = true, customClientId = " $own "), default = "", title = R.string.check_appid_title)
        assertEquals(Severity.OK, mine.severity)
        assertEquals(UiText(R.string.check_appid_own, own), mine.detail)

        val invalidOwn = Config(useCustomApp = true, customClientId = "12345")
        val fallback = item(good, invalidOwn, title = R.string.check_appid_title)
        assertEquals(Severity.WARNING, fallback.severity)
        assertEquals(UiText(R.string.check_appid_invalid_fallback, builtIn), fallback.detail)

        val nothing = item(good, invalidOwn, default = "", title = R.string.check_appid_title)
        assertEquals(Severity.ERROR, nothing.severity)
        assertEquals(UiText(R.string.check_appid_invalid), nothing.detail)

        // a valid own ID that is switched off is not used
        assertEquals(
            SetupChecks.AppIdSource.BUILT_IN,
            SetupChecks.appIdSource(Config(useCustomApp = false, customClientId = own), builtIn),
        )
        assertEquals(SetupChecks.AppIdSource.OWN, SetupChecks.appIdSource(Config(useCustomApp = true, customClientId = own), builtIn))
        assertEquals(SetupChecks.AppIdSource.NONE, SetupChecks.appIdSource(Config(), " "))
    }

    @Test
    fun invalidBuiltInApplicationId() {
        // a build with a wrong discordApplicationId: Discord would reject it, only an own application helps
        for (broken in listOf("12345", "abc", "1555109720807702559x", "123456789012345678901")) {
            val item = item(good, default = broken, title = R.string.check_appid_title)
            assertEquals(broken, Severity.ERROR, item.severity)
            assertEquals(CheckAction.SET_APP_ID, item.action)
            assertEquals(UiText(R.string.check_appid_builtin_invalid, broken), item.detail)
            assertTrue(SetupChecks.builtInInvalid(broken))
        }
        // and the own one is switched on but not valid either
        val both = item(good, Config(useCustomApp = true, customClientId = "1"), default = "12345", title = R.string.check_appid_title)
        assertEquals(Severity.ERROR, both.severity)
        assertEquals(UiText(R.string.check_appid_both_invalid, "12345"), both.detail)
        // a valid own ID makes the built-in one irrelevant
        val mine = item(good, Config(useCustomApp = true, customClientId = own), default = "12345", title = R.string.check_appid_title)
        assertEquals(Severity.OK, mine.severity)
        // blank = not built in (a separate message), a valid one with spaces around is fine
        assertFalse(SetupChecks.builtInInvalid(""))
        assertFalse(SetupChecks.builtInInvalid(" $builtIn "))
        assertTrue(SetupChecks.items(good, Config(), " $builtIn ").none { it.title == R.string.check_appid_title })
    }

    @Test
    fun usableApplicationIdIsWhatTheEngineTakes() {
        assertTrue(SetupChecks.hasUsableAppId(Config(), builtIn))
        assertTrue(SetupChecks.hasUsableAppId(Config(useCustomApp = true, customClientId = own), ""))
        assertFalse(SetupChecks.hasUsableAppId(Config(), ""))
        assertFalse(SetupChecks.hasUsableAppId(Config(useCustomApp = true, customClientId = "12345"), ""))
        // the engine tries any number (Discord rejects a wrong one), but nothing else
        assertTrue(SetupChecks.hasUsableAppId(Config(), "12345"))
        assertFalse(SetupChecks.hasUsableAppId(Config(), "abc"))
    }

    @Test
    fun batteryAndNotifications() {
        val battery = item(good.copy(batteryOptimizationIgnored = false), title = R.string.check_battery_title)
        assertEquals(CheckAction.BATTERY, battery.action)
        assertEquals(R.string.check_battery_fix, battery.actionLabel)
        val notifications = item(good.copy(notificationsAllowed = false), title = R.string.check_notif_title)
        assertEquals(CheckAction.NOTIFICATIONS, notifications.action)
        assertEquals(Severity.WARNING, notifications.severity)
    }
}
