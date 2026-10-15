package io.github.rainbowfl4sh.powerampdiscordrp.service

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/** How the previous process of this app ended, as far as [TaskManagerStop] cares. */
data class ProcessExit(
    val timestampMs: Long,
    val stoppedByUser: Boolean,      // ApplicationExitInfo.REASON_USER_REQUESTED
    val wasRunningService: Boolean,  // it ran the foreground service (or was in front) when it ended
)

/**
 * Android 13+ lets the user stop an app that runs a foreground service from the notification shade (task manager of
 * "active apps"). That kills the process but, unlike Settings > Force stop, leaves the app able to receive Poweramp's
 * next broadcast, which would bring the presence back with the next song. Such a stop counts like the notification's
 * "Stop": no presence until playback starts again.
 *
 * Android 14+ only: before, Android recorded an update of the app as a stop by the user as well.
 */
class TaskManagerStop(
    private val prefs: SharedPreferences,
    private val lastExit: () -> ProcessExit?,
) {
    /**
     * Once per process, at its first contact with Poweramp. [startedByPoweramp] = that contact is a broadcast, not the
     * app's own screen: a process started for the screen only marks the previous exit as seen (after Settings > Force
     * stop, Poweramp's broadcasts only arrive again once the app was opened).
     * @return true if the previous process was stopped by the user while it showed the presence
     */
    fun check(startedByPoweramp: Boolean): Boolean {
        val exit = try {
            lastExit()
        } catch (_: RuntimeException) {
            null
        } ?: return false
        if (exit.timestampMs == prefs.getLong(KEY_SEEN, 0)) return false
        prefs.edit().putLong(KEY_SEEN, exit.timestampMs).apply()
        return startedByPoweramp && exit.stoppedByUser && exit.wasRunningService
    }

    companion object {
        private const val KEY_SEEN = "lastExitSeen"

        /** The latest exit record of this app (Android 14+), null if none / older Android. */
        fun lastExitOf(context: Context): ProcessExit? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
            val manager = context.getSystemService(ActivityManager::class.java) ?: return null
            val info = manager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return null
            return ProcessExit(
                timestampMs = info.timestamp,
                stoppedByUser = info.reason == ApplicationExitInfo.REASON_USER_REQUESTED,
                wasRunningService = info.importance in
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND..
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
            )
        }
    }
}
