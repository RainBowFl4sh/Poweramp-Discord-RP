package io.github.rainbowfl4sh.powerampdiscordrp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.rainbowfl4sh.powerampdiscordrp.Graph
import io.github.rainbowfl4sh.powerampdiscordrp.poweramp.Poweramp
import io.github.rainbowfl4sh.powerampdiscordrp.poweramp.PowerampBridge
import io.github.rainbowfl4sh.powerampdiscordrp.poweramp.PowerampReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process (and the binding to the Discord app) alive while Poweramp plays.
 * Started by the Poweramp broadcasts, stops itself when the engine reports idle.
 *
 * Runs the engine between activate() and deactivate(). Stopping uses stopSelfResult(lastStartId): if a new start
 * request is already queued, the service stays and that start activates it again - a plain stopSelf() would drop it,
 * and dropping a pending startForegroundService() crashes the app.
 */
class PresenceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observers: Job? = null
    private var receiver: PowerampReceiver? = null
    private var active = false
    private var lastStartId = 0
    private var content: NotificationContent? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // First thing on every start: startForegroundService() leaves only a few seconds for it
        val foreground = goForeground()
        if (intent?.action == ACTION_STOP) {
            userStop.stop(PowerampBridge.snapshot.state)
            Notifications.cancelBackgroundBlocked(this)
        }
        when {
            !foreground -> shutDown("could not run in the foreground")
            userStop.active.value -> shutDown("stopped by the user until playback starts again")
            else -> activate()
        }
        return START_NOT_STICKY   // after the process was killed, the next Poweramp broadcast starts it again
    }

    override fun onDestroy() {
        deactivate()
        scope.cancel()
        super.onDestroy()
    }

    private fun activate() {
        if (active) return
        active = true
        _running.value = true
        Graph.log.log("Presence service: started")
        Notifications.cancelBackgroundBlocked(this)

        val engine = Graph.engine
        engine.start()
        registerPowerampReceiver()
        PowerampBridge.sync(this)

        observers = scope.launch {
            launch {
                // Debounced: a snapshot submitted just now makes the engine busy again within moments (collectLatest
                // then cancels the wait), so only a lasting idle state ends the service
                engine.idle.collectLatest { idle ->
                    if (idle) {
                        delay(IDLE_CONFIRM_MS)
                        shutDown("nothing to show")
                    }
                }
            }
            launch {
                userStop.active.first { it }
                shutDown("stopped by the user until playback starts again")
            }
            launch {
                combine(engine.status, engine.snapshot) { status, snapshot ->
                    Notifications.contentOf(this@PresenceService, status, snapshot)
                }.distinctUntilChanged().collect { showContent(it) }
            }
            launch {
                // An application ID Discord rejected keeps Poweramp's broadcasts from starting the service again
                engine.status.collect { rejectedAppId.onStatus(it) }
            }
        }
    }

    private fun deactivate() {
        if (!active) return
        active = false
        _running.value = false
        observers?.cancel()
        observers = null
        unregisterPowerampReceiver()
        Graph.engine.stop()   // clears the presence and disconnects
    }

    private fun shutDown(reason: String) {
        if (active) Graph.log.log("Presence service: stopping ($reason)")
        deactivate()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(lastStartId)   // false = a newer start is queued; its onStartCommand activates again
    }

    private fun goForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        Notifications.ensureChannels(this)
        val shown = content ?: Notifications.initialContent(this, PowerampBridge.snapshot)
        ServiceCompat.startForeground(this, Notifications.ID_PRESENCE, Notifications.presence(this, shown), type)
        true
    } catch (e: RuntimeException) {
        // ForegroundServiceStartNotAllowedException & co. (all IllegalStateException / SecurityException)
        Graph.log.log("Presence service: could not go to the foreground: $e")
        false
    }

    private fun showContent(next: NotificationContent) {
        content = next
        if (active) Notifications.updatePresence(this, next)
    }

    /** Sticky TRACK_CHANGED / STATUS_CHANGED (they reach registered receivers only) and TPOS_SYNC (seeks). */
    private fun registerPowerampReceiver() {
        val filter = IntentFilter().apply {
            addAction(Poweramp.ACTION_TRACK_CHANGED)
            addAction(Poweramp.ACTION_STATUS_CHANGED)
            addAction(Poweramp.ACTION_TPOS_SYNC)
        }
        val r = PowerampReceiver()
        try {
            ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_EXPORTED)
            receiver = r
        } catch (e: RuntimeException) {
            Graph.log.log("Presence service: could not register for Poweramp broadcasts: $e")
        }
    }

    private fun unregisterPowerampReceiver() {
        val r = receiver ?: return
        receiver = null
        try {
            unregisterReceiver(r)
        } catch (_: IllegalArgumentException) {
        }
    }

    companion object {
        /** Notification action "Stop": stops until playback starts again. */
        const val ACTION_STOP = "io.github.rainbowfl4sh.powerampdiscordrp.action.STOP"

        private const val IDLE_CONFIRM_MS = 3_000L

        private val _running = MutableStateFlow(false)

        /** The user's "Stop" (notification action / [stop]), persisted across process restarts. */
        internal val userStop: UserStop by lazy { UserStop(runtimePrefs()) }

        /** Application ID Discord rejected: not worth starting the service for. */
        internal val rejectedAppId: RejectedAppId by lazy { RejectedAppId(runtimePrefs()) }

        /** Android's own "Stop" for apps with a foreground service (Android 13+ task manager). */
        internal val taskManagerStop: TaskManagerStop by lazy {
            TaskManagerStop(runtimePrefs()) { TaskManagerStop.lastExitOf(Graph.app) }
        }

        private fun runtimePrefs() = Graph.app.getSharedPreferences(UserStop.PREFS_NAME, Context.MODE_PRIVATE)

        /** Reads the saved user stop and rejected application ID now (Graph.warmUp: off the main thread). */
        internal fun loadSavedState() {
            userStop
            rejectedAppId
        }

        /** Starts (or pokes) the service. false = Android did not allow it (background start restrictions). */
        fun start(context: Context): Boolean {
            userStop.clear()
            return launch(context)
        }

        /** Stops the service until playback starts again (or [start] is called). */
        fun stop(context: Context) {
            // A running service stops itself (it observes the flag), a pending start ends right away
            userStop.stop(PowerampBridge.snapshot.state)
            Notifications.cancelBackgroundBlocked(context)
        }

        /** True while the service runs. */
        val running: StateFlow<Boolean> get() = _running.asStateFlow()

        /** The user stopped the presence ([stop] / notification action); cleared when playback starts again. */
        val stoppedByUser: StateFlow<Boolean> get() = userStop.active

        /** Starts the service without touching the user's stop (Poweramp broadcasts, settings changes). */
        internal fun launch(context: Context): Boolean {
            if (_running.value) return true   // a running service follows the engine already
            return try {
                ContextCompat.startForegroundService(context, Intent(context, PresenceService::class.java))
                true
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (Android 12+) / background start not allowed (8+)
                Graph.log.log("Presence service: start not allowed: $e")
                false
            } catch (e: SecurityException) {
                Graph.log.log("Presence service: start not allowed: $e")
                false
            }
        }
    }
}
