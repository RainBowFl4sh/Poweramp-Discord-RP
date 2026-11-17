package io.github.rainbowfl4sh.powerampdiscordrp.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.locale.AppLocale
import io.github.rainbowfl4sh.powerampdiscordrp.service.Notifications
import io.github.rainbowfl4sh.powerampdiscordrp.ui.MainActivity

/** Notifications of the in-app update (on the alerts channel). */
object UpdateNotifications {
    const val ID_CONFIRM = 3
    const val ID_UPDATED = 4
    private const val REQUEST_CONFIRM = 10
    private const val REQUEST_UPDATED = 11

    /** In the background Android's confirmation cannot just open: a tap on this notification opens it. */
    fun showConfirm(context: Context, version: String, confirm: Intent) {
        if (!canPost(context)) return
        Notifications.ensureChannels(context)
        val tap = PendingIntent.getActivity(
            context, REQUEST_CONFIRM, confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.text(R.string.upd_notif_confirm_title, version))
            .setContentText(context.text(R.string.upd_notif_confirm_text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        notify(context, ID_CONFIRM, n)
    }

    fun cancelConfirm(context: Context) = NotificationManagerCompat.from(context).cancel(ID_CONFIRM)

    /** After the update: which version is now installed (it may have happened without any question). */
    fun showUpdated(context: Context, version: String) {
        if (!canPost(context)) return
        Notifications.ensureChannels(context)
        val open = PendingIntent.getActivity(
            context, REQUEST_UPDATED,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.text(R.string.upd_notif_updated_title, version))
            .setContentText(context.text(R.string.upd_notif_updated_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        notify(context, ID_UPDATED, n)
    }

    private fun notify(context: Context, id: Int, n: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun Context.text(id: Int, vararg args: Any): String = AppLocale.resources(this).getString(id, *args)
}
