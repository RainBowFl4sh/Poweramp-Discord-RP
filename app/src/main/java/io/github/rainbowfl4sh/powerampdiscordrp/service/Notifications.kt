package io.github.rainbowfl4sh.powerampdiscordrp.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.Hidden
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.TransportError
import io.github.rainbowfl4sh.powerampdiscordrp.ui.MainActivity

/** Title (what plays) and text (what Discord shows / the connection state) of the service notification. */
data class NotificationContent(val title: String, val text: String)

/** Notification channels and notifications of the presence service. */
object Notifications {
    const val CHANNEL_PRESENCE = "presence"
    const val CHANNEL_ALERTS = "alerts"
    const val ID_PRESENCE = 1
    const val ID_BACKGROUND_BLOCKED = 2

    private const val REQUEST_OPEN = 0
    private const val REQUEST_STOP = 1
    private const val REQUEST_OPEN_BLOCKED = 2

    private const val KEY_BLOCKED_ALERT = "bgBlockedAlertMs"   // in the runtime prefs: when the alert was posted last
    const val BLOCKED_ALERT_INTERVAL_MS = 24 * 60 * 60_000L

    /** Creating an existing channel again only updates its name / description (e.g. after a language change). */
    fun ensureChannels(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_PRESENCE, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.svc_channel_name))
                    .setDescription(context.getString(R.string.svc_channel_desc))
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.svc_alert_channel_name))
                    .setDescription(context.getString(R.string.svc_alert_channel_desc))
                    .build(),
            ),
        )
    }

    /** Notification content for the engine's state. */
    fun contentOf(context: Context, status: EngineStatus, snapshot: PlaybackSnapshot): NotificationContent {
        val text = when (status.kind) {
            EngineStatus.Kind.DISABLED -> R.string.svc_disabled
            EngineStatus.Kind.NO_APP_ID -> R.string.svc_no_app_id
            EngineStatus.Kind.CONNECTING -> R.string.svc_connecting
            EngineStatus.Kind.NOT_CONNECTED -> when (status.error) {
                TransportError.DISCORD_NOT_INSTALLED -> R.string.svc_discord_missing
                TransportError.INVALID_CLIENT_ID -> R.string.svc_rejected
                else -> R.string.svc_not_connected
            }
            // IDLE = not connected because nothing is to be shown (yet): say why, like a connected engine would
            EngineStatus.Kind.CONNECTED, EngineStatus.Kind.IDLE -> when {
                // "shown" only while Discord really shows it (visible): not after a rejection, nor before the first send
                status.kind == EngineStatus.Kind.CONNECTED && status.activityError.isNotEmpty() -> R.string.svc_update_rejected
                status.testing -> if (status.visible) R.string.svc_testing else R.string.svc_sending
                status.hidden == Hidden.NO && status.kind == EngineStatus.Kind.IDLE -> R.string.svc_connecting
                status.hidden == Hidden.NO && !status.visible -> R.string.svc_sending
                status.hidden == Hidden.NO && snapshot.state == PlayState.PAUSED -> R.string.svc_showing_paused
                status.hidden == Hidden.NO -> R.string.svc_showing
                status.hidden == Hidden.PAUSED || status.hidden == Hidden.PAUSED_TIMEOUT -> R.string.svc_hidden_paused
                snapshot.track.isEmpty -> R.string.svc_waiting
                else -> R.string.svc_hidden
            }
        }
        return NotificationContent(trackLine(context, snapshot), context.getString(text))
    }

    /** Content before the engine reported anything (the service has to show a notification at once). */
    fun initialContent(context: Context, snapshot: PlaybackSnapshot) =
        NotificationContent(trackLine(context, snapshot), context.getString(R.string.svc_connecting))

    /** The service notification ([ensureChannels] must have run in this process). */
    fun presence(context: Context, content: NotificationContent): Notification {
        val stop = PendingIntent.getService(
            context, REQUEST_STOP,
            Intent(context, PresenceService::class.java).setAction(PresenceService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_PRESENCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(openApp(context, REQUEST_OPEN))
            .addAction(R.drawable.ic_notification_stop, context.getString(R.string.svc_action_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Replaces the content of the (already shown) service notification. */
    fun updatePresence(context: Context, content: NotificationContent) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(ID_PRESENCE, presence(context, content))
        } catch (_: SecurityException) {
            // permission revoked in the meantime
        }
    }

    /**
     * Android refused to start the service in the background: the user can fix that from the app (battery settings).
     * That happens on every song, so the alert (with sound) comes once a day at most: not again while it is shown, nor
     * after the user dismissed it.
     */
    fun showBackgroundBlocked(context: Context, nowWallMs: Long = System.currentTimeMillis()) {
        if (!canPost(context) || isShown(context, ID_BACKGROUND_BLOCKED)) return
        val prefs = context.getSharedPreferences(UserStop.PREFS_NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_BLOCKED_ALERT, 0)
        if (nowWallMs - last in 0 until BLOCKED_ALERT_INTERVAL_MS) return   // a clock set back counts as long ago
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.svc_bg_blocked_title))
            .setContentText(context.getString(R.string.svc_bg_blocked_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.svc_bg_blocked_text)))
            .setContentIntent(openApp(context, REQUEST_OPEN_BLOCKED))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_BACKGROUND_BLOCKED, notification)
            prefs.edit().putLong(KEY_BLOCKED_ALERT, nowWallMs).apply()
        } catch (_: SecurityException) {
        }
    }

    fun cancelBackgroundBlocked(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_BACKGROUND_BLOCKED)
    }

    private fun isShown(context: Context, id: Int): Boolean = try {
        context.getSystemService(NotificationManager::class.java)?.activeNotifications?.any { it.id == id } == true
    } catch (_: RuntimeException) {
        false
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Brings the app's task to the front like the launcher does (no second MainActivity on top). */
    private fun openApp(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** "Title – Artist" (or what there is of it); the app name when nothing is known. */
    private fun trackLine(context: Context, snapshot: PlaybackSnapshot): String {
        val track = snapshot.track
        val title = track.title.trim().ifEmpty { track.path.substringAfterLast('/').trim() }
        val artist = track.artist.trim()
        return when {
            title.isEmpty() -> context.getString(R.string.app_name)
            artist.isEmpty() -> title
            else -> context.getString(R.string.svc_track, title, artist)
        }
    }
}
