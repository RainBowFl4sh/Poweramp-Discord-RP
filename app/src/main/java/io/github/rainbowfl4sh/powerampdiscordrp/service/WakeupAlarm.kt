package io.github.rainbowfl4sh.powerampdiscordrp.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.rainbowfl4sh.powerampdiscordrp.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Inexact alarm for the engine's [wakeupAt][io.github.rainbowfl4sh.powerampdiscordrp.core.engine.PresenceEngine.wakeupAt]
 * (paused timeout, idle grace): the engine's timers are delays that stand still while the device sleeps, so with the
 * screen off and Poweramp paused "Paused" would stay on Discord for hours. setAndAllowWhileIdle needs no exact-alarm
 * permission; Doze may move it to its next maintenance window. One alarm at a time, replaced or cancelled as the
 * deadline changes.
 */
internal object WakeupAlarm {
    /** Sets the alarm for [atElapsedMs] (SystemClock.elapsedRealtime, the engine's clock); null cancels it. */
    fun set(context: Context, atElapsedMs: Long?) {
        if (atElapsedMs == null) return cancel(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsedMs, pendingIntent(context))
        } catch (e: RuntimeException) {   // e.g. SecurityException: too many alarms on some devices
            Graph.log.log("Presence service: could not set the wakeup alarm: $e")
        }
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            alarms.cancel(pendingIntent(context))
        } catch (e: RuntimeException) {
            Graph.log.log("Presence service: could not cancel the wakeup alarm: $e")
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WakeupReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * The [WakeupAlarm] fired: wakes the engine and keeps the CPU awake (goAsync holds the alarm's wakelock) until the
 * engine went through its deadlines and the transport had a moment to hand the cleared presence to Discord.
 */
class WakeupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!PresenceService.running.value) return   // a stale alarm of a process that is gone: nothing runs
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(WAKE_TIMEOUT_MS) { Graph.engine.wakeUp() }
                delay(TRANSPORT_GRACE_MS)   // the binder call runs on the transport's own thread
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        // goAsync allows about 10 s in all
        const val WAKE_TIMEOUT_MS = 5_000L
        const val TRANSPORT_GRACE_MS = 1_000L
    }
}
