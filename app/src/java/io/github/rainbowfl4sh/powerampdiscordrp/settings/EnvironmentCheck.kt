package io.github.rainbowfl4sh.powerampdiscordrp.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import io.github.rainbowfl4sh.powerampdiscordrp.Graph
import io.github.rainbowfl4sh.powerampdiscordrp.discord.DiscordBinderTransport
import io.github.rainbowfl4sh.powerampdiscordrp.poweramp.Poweramp

/** Everything the status page checks before the presence can work. */
data class EnvState(
    val powerampVersion: String? = null,         // versionName, null = Poweramp not installed
    val powerampBuild: Int = 0,                  // build number from versionCode (e.g. 1032), 0 = unknown
    val discordInstalled: Boolean = false,
    val discordRpcAvailable: Boolean = false,    // the Discord app exports the Social SDK RPC service
    val batteryOptimizationIgnored: Boolean = false,
    val notificationsAllowed: Boolean = true,    // Android 13+: POST_NOTIFICATIONS granted
    val clientIdConfigured: Boolean = false,
) {
    /** Poweramp API (TRACK_CHANGED_EXPLICIT / STATUS_CHANGED_EXPLICIT, track bundle) needs build 948+. */
    val powerampSupported: Boolean get() = powerampVersion != null && (powerampBuild == 0 || powerampBuild >= 948)
}

object EnvironmentCheck {
    const val POWERAMP_PACKAGE = Poweramp.PACKAGE
    val DISCORD_PACKAGES = listOf("com.discord")

    fun check(context: Context): EnvState {
        val pm = context.packageManager
        val poweramp = packageInfo(pm, POWERAMP_PACKAGE)
        return EnvState(
            powerampVersion = poweramp?.let { it.versionName ?: "" },
            powerampBuild = poweramp?.let { powerampBuild(PackageInfoCompat.getLongVersionCode(it)) } ?: 0,
            discordInstalled = DISCORD_PACKAGES.any { packageInfo(pm, it) != null },
            discordRpcAvailable = DISCORD_PACKAGES.any { pkg ->
                runCatching { pm.resolveService(Intent(DiscordBinderTransport.SERVICE_ACTION).setPackage(pkg), 0) }
                    .getOrNull() != null
            },
            batteryOptimizationIgnored = context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true,
            notificationsAllowed = notificationsAllowed(context),
            clientIdConfigured = Graph.isInitialized &&
                Graph.settings.config.value.effectiveClientId(Graph.defaultClientId).isNotBlank(),
        )
    }

    /**
     * Poweramp's versionCode is the build number times 1000 plus a variant (1032009 -> build 1032).
     * 0 = unknown (no plausible build number).
     */
    fun powerampBuild(versionCode: Long): Int {
        val build = versionCode / 1000
        return if (build in 1..Int.MAX_VALUE.toLong()) build.toInt() else 0
    }

    /** Opens the system dialog to exclude this app from battery optimization (needed to start in the background). */
    fun batteryOptimizationIntent(context: Context): Intent {
        // The direct dialog needs the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission; some ROMs lack it -> list screen
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        return if (direct.resolveActivity(context.packageManager) != null) direct
        else Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    /** Launch intent of Poweramp / Discord, null if not installed. */
    fun launchPoweramp(context: Context): Intent? = context.packageManager.getLaunchIntentForPackage(POWERAMP_PACKAGE)

    fun launchDiscord(context: Context): Intent? =
        DISCORD_PACKAGES.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }

    /** Android 13+: the runtime permission; always: the user did not switch the app's notifications off. */
    fun notificationsAllowed(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun packageInfo(pm: PackageManager, pkg: String): PackageInfo? = try {
        pm.getPackageInfo(pkg, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
