package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import androidx.annotation.StringRes
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.settings.EnvState

/** What the fix button of a checklist item does. */
enum class CheckAction { GET_POWERAMP, UPDATE_POWERAMP, GET_DISCORD, UPDATE_DISCORD, SET_APP_ID, BATTERY, NOTIFICATIONS }

data class CheckItem(
    @StringRes val title: Int,
    val detail: UiText,
    val severity: Severity,
    val action: CheckAction? = null,
    @StringRes val actionLabel: Int = 0,
)

/** The setup checklist of the status page, from the environment and the settings. */
object SetupChecks {
    /** First Poweramp build with the API this app uses (track bundle in TRACK_CHANGED, *_EXPLICIT broadcasts). */
    const val MIN_POWERAMP_BUILD = 948

    /** Which application ID is in use and where it comes from. */
    enum class AppIdSource { NONE, BUILT_IN, OWN }

    fun appIdSource(c: Config, defaultClientId: String): AppIdSource {
        val id = c.effectiveClientId(defaultClientId)
        return when {
            id.isBlank() -> AppIdSource.NONE
            c.useCustomApp && id == c.customClientId.trim() -> AppIdSource.OWN
            else -> AppIdSource.BUILT_IN
        }
    }

    /**
     * True if there is an application ID to connect with. The engine (and the Poweramp bridge) take any number - a
     * wrong one is Discord's to reject, which the status page then reports.
     */
    fun hasUsableAppId(c: Config, defaultClientId: String): Boolean = c.effectiveClientId(defaultClientId).toULongOrNull() != null

    /** The ID built into this APK is set, but is no Discord application ID (17 to 20 digits): a mistake of the build. */
    fun builtInInvalid(defaultClientId: String): Boolean =
        defaultClientId.isNotBlank() && !Config.isValidClientId(defaultClientId.trim())

    /** The checklist; the Discord application only appears when it needs attention or an own one is in use. */
    fun items(env: EnvState, c: Config, defaultClientId: String): List<CheckItem> =
        listOfNotNull(poweramp(env), discord(env), appId(c, defaultClientId), battery(env), notifications(env))

    private fun poweramp(env: EnvState): CheckItem {
        val version = env.powerampVersion
            ?: return CheckItem(
                R.string.check_poweramp_title, UiText(R.string.check_poweramp_missing), Severity.ERROR,
                CheckAction.GET_POWERAMP, R.string.check_poweramp_get,
            )
        val shown = version.ifBlank { "?" }
        if (!env.powerampSupported) {
            return CheckItem(
                R.string.check_poweramp_title,
                UiText(R.string.check_poweramp_old, shown, env.powerampBuild, MIN_POWERAMP_BUILD),
                Severity.WARNING, CheckAction.UPDATE_POWERAMP, R.string.check_poweramp_update,
            )
        }
        // Poweramp's version names usually carry the build already ("build-1032-uni"): no need to repeat it
        val buildInName = env.powerampBuild > 0 && env.powerampBuild.toString() in shown
        val detail = if (env.powerampBuild > 0 && !buildInName) UiText(R.string.check_poweramp_ok, shown, env.powerampBuild)
        else UiText(R.string.check_poweramp_ok_nobuild, shown)
        return CheckItem(R.string.check_poweramp_title, detail, Severity.OK)
    }

    private fun discord(env: EnvState): CheckItem = when {
        !env.discordInstalled -> CheckItem(
            R.string.check_discord_title, UiText(R.string.check_discord_missing), Severity.ERROR,
            CheckAction.GET_DISCORD, R.string.check_discord_get,
        )
        !env.discordRpcAvailable -> CheckItem(
            R.string.check_discord_title, UiText(R.string.check_discord_no_rpc), Severity.ERROR,
            CheckAction.UPDATE_DISCORD, R.string.check_discord_update,
        )
        else -> CheckItem(R.string.check_discord_title, UiText(R.string.check_discord_ok), Severity.OK)
    }

    /** null = the application built into the app is used and fine: nothing to tell. */
    private fun appId(c: Config, defaultClientId: String): CheckItem? {
        val id = c.effectiveClientId(defaultClientId)
        val ownInvalid = c.useCustomApp && !Config.isValidClientId(c.customClientId.trim())
        return when (appIdSource(c, defaultClientId)) {
            AppIdSource.NONE -> CheckItem(
                R.string.check_appid_title,
                UiText(if (ownInvalid) R.string.check_appid_invalid else R.string.check_appid_missing),
                Severity.ERROR, CheckAction.SET_APP_ID, R.string.check_appid_fix,
            )
            AppIdSource.OWN -> CheckItem(R.string.check_appid_title, UiText(R.string.check_appid_own, id), Severity.OK)
            AppIdSource.BUILT_IN -> when {
                // Discord would reject it (or the engine cannot even use it): only an own application helps
                builtInInvalid(defaultClientId) -> CheckItem(
                    R.string.check_appid_title,
                    UiText(if (ownInvalid) R.string.check_appid_both_invalid else R.string.check_appid_builtin_invalid, id),
                    Severity.ERROR, CheckAction.SET_APP_ID, R.string.check_appid_fix,
                )
                // the own ID is switched on but not usable: the built-in one stands in
                ownInvalid -> CheckItem(
                    R.string.check_appid_title, UiText(R.string.check_appid_invalid_fallback, id), Severity.WARNING,
                    CheckAction.SET_APP_ID, R.string.check_appid_fix,
                )
                else -> null
            }
        }
    }

    private fun battery(env: EnvState): CheckItem =
        if (env.batteryOptimizationIgnored) {
            CheckItem(R.string.check_battery_title, UiText(R.string.check_battery_ok), Severity.OK)
        } else {
            CheckItem(
                R.string.check_battery_title, UiText(R.string.check_battery_bad), Severity.WARNING,
                CheckAction.BATTERY, R.string.check_battery_fix,
            )
        }

    private fun notifications(env: EnvState): CheckItem =
        if (env.notificationsAllowed) {
            CheckItem(R.string.check_notif_title, UiText(R.string.check_notif_ok), Severity.OK)
        } else {
            CheckItem(
                R.string.check_notif_title, UiText(R.string.check_notif_bad), Severity.WARNING,
                CheckAction.NOTIFICATIONS, R.string.check_notif_fix,
            )
        }
}
