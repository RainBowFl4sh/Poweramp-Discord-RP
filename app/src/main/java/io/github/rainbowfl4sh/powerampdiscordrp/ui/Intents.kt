package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import io.github.rainbowfl4sh.powerampdiscordrp.R

/** Starts an activity; false (and a short note) if no app can handle it. */
fun Context.startSafely(intent: Intent?): Boolean {
    if (intent == null) {
        Toast.makeText(this, R.string.msg_cannot_open, Toast.LENGTH_SHORT).show()
        return false
    }
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.msg_cannot_open, Toast.LENGTH_SHORT).show()
        false
    } catch (_: SecurityException) {
        Toast.makeText(this, R.string.msg_cannot_open, Toast.LENGTH_SHORT).show()
        false
    }
}

/** Opens a web page in the browser. */
fun Context.openUrl(url: String): Boolean =
    startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))

/** The app's page in the store app (Google Play or whatever handles market:// links), else on the Play website. */
fun Context.openStorePage(packageName: String): Boolean {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    if (market.resolveActivity(packageManager) != null) return startSafely(market)
    return openUrl("https://play.google.com/store/apps/details?id=$packageName")
}

/** This app's notification settings (to switch notifications on again after they were denied). */
fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** Links of the project. */
object Links {
    const val REPO = "https://github.com/RainBowFl4sh/Poweramp-Discord-RP"
    const val RELEASES = "$REPO/releases"
    const val LATEST_RELEASE = "$REPO/releases/latest"
    const val ISSUES = "$REPO/issues"
    const val AUTHOR = "https://github.com/RainBowFl4sh"
    const val DISCORD_PORTAL = "https://discord.com/developers/applications"
    const val IMGUR_ADD_CLIENT = "https://api.imgur.com/oauth2/addclient"
    const val SPOTIFY_DASHBOARD = "https://developer.spotify.com/dashboard"
    const val DISCOGS_DEVELOPERS = "https://www.discogs.com/settings/developers"
    const val ANTISOCIALCORD = "https://github.com/Nightdavisao/antisocialcord-kt"
    const val POWERAMP_API = "https://github.com/maxmpz/powerampapi"
    const val DISCORD_PACKAGE = "com.discord"
}
