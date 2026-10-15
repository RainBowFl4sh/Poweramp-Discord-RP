package io.github.rainbowfl4sh.powerampdiscordrp.settings

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.github.rainbowfl4sh.powerampdiscordrp.discord.DiscordBinderTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EnvironmentCheckTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val pm = shadowOf(app.packageManager)

    private fun install(pkg: String, versionName: String, versionCode: Long) {
        pm.installPackage(PackageInfo().apply {
            packageName = pkg
            this.versionName = versionName
            longVersionCode = versionCode   // API 28+, the tests run on 34
        })
    }

    @Test
    fun powerampBuildFromVersionCode() {
        assertEquals(1032, EnvironmentCheck.powerampBuild(1_032_009))
        assertEquals(1025, EnvironmentCheck.powerampBuild(1_025_004))
        assertEquals(948, EnvironmentCheck.powerampBuild(948_000))
        assertEquals(0, EnvironmentCheck.powerampBuild(999))
        assertEquals(0, EnvironmentCheck.powerampBuild(0))
        assertEquals(0, EnvironmentCheck.powerampBuild(-5_000))
    }

    @Test
    fun powerampSupportNeedsBuild948() {
        assertFalse(EnvState().powerampSupported)
        assertTrue(EnvState(powerampVersion = "v3", powerampBuild = 1032).powerampSupported)
        assertTrue(EnvState(powerampVersion = "v3", powerampBuild = 0).powerampSupported)   // unknown: assume yes
        assertFalse(EnvState(powerampVersion = "v2", powerampBuild = 800).powerampSupported)
    }

    @Test
    fun nothingInstalled() {
        val env = EnvironmentCheck.check(app)
        assertNull(env.powerampVersion)
        assertEquals(0, env.powerampBuild)
        assertFalse(env.discordInstalled)
        assertFalse(env.discordRpcAvailable)
        assertNull(EnvironmentCheck.launchPoweramp(app))
        assertNull(EnvironmentCheck.launchDiscord(app))
    }

    @Test
    fun powerampInstalled() {
        install("com.maxmpz.audioplayer", "build-1032-bundle-play", 1_032_009)
        val env = EnvironmentCheck.check(app)
        assertEquals("build-1032-bundle-play", env.powerampVersion)
        assertEquals(1032, env.powerampBuild)
        assertTrue(env.powerampSupported)
    }

    @Test
    fun discordWithoutRpcService() {
        install("com.discord", "300.12", 300_012)
        val env = EnvironmentCheck.check(app)
        assertTrue(env.discordInstalled)
        assertFalse(env.discordRpcAvailable)
    }

    @Test
    fun discordWithRpcService() {
        install("com.discord", "300.12", 300_012)
        val service = ComponentName("com.discord", "com.discord.socialrpc.DiscordRpcService")
        pm.addServiceIfNotPresent(service)
        pm.addIntentFilterForService(service, IntentFilter(DiscordBinderTransport.SERVICE_ACTION))
        val env = EnvironmentCheck.check(app)
        assertTrue(env.discordInstalled)
        assertTrue(env.discordRpcAvailable)
    }

    @Test
    fun clientIdConfiguredFollowsTheSettings() {
        // Graph is initialized by App (the test application); the APK's default ID may be blank
        io.github.rainbowfl4sh.powerampdiscordrp.Graph.settings.update {
            it.copy(useCustomApp = true, customClientId = "123456789012345678")
        }
        assertTrue(EnvironmentCheck.check(app).clientIdConfigured)
    }

    @Test
    fun batteryOptimizationFallsBackToTheSettingsList() {
        val intent = EnvironmentCheck.batteryOptimizationIntent(app)
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, intent.action)
    }

    @Test
    fun batteryOptimizationDialogForThisApp() {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${app.packageName}"))
        pm.addResolveInfoForIntent(direct, ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.settings"
                name = "com.android.settings.fuelgauge.RequestIgnoreBatteryOptimizations"
            }
        })
        val intent = EnvironmentCheck.batteryOptimizationIntent(app)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intent.action)
        assertEquals("package:${app.packageName}", intent.dataString)
    }

    @Test
    fun launchIntents() {
        install("com.maxmpz.audioplayer", "build-1025", 1_025_004)
        val launcher = ComponentName("com.maxmpz.audioplayer", "com.maxmpz.audioplayer.StartupActivity")
        pm.addActivityIfNotPresent(launcher)
        pm.addIntentFilterForActivity(launcher, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        assertNotNull(EnvironmentCheck.launchPoweramp(app))
    }
}
