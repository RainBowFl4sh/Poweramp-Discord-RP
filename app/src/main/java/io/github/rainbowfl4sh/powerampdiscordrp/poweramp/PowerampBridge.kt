package io.github.rainbowfl4sh.powerampdiscordrp.poweramp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import io.github.rainbowfl4sh.powerampdiscordrp.AndroidClock
import io.github.rainbowfl4sh.powerampdiscordrp.Graph
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.Hidden
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.PresenceTexts
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.StatusStrings
import io.github.rainbowfl4sh.powerampdiscordrp.service.Notifications
import io.github.rainbowfl4sh.powerampdiscordrp.service.PresenceService
import io.github.rainbowfl4sh.powerampdiscordrp.settings.EnvironmentCheck

/** Glue between Poweramp's broadcasts and the presence engine. */
object PowerampBridge {
    private val tracker by lazy { PowerampTracker(PowerampState(AndroidClock), ::readSticky, Graph.log) }

    @Volatile
    private var backgroundStartBlocked = false

    @Volatile
    private var discordMissing = false

    private var processChecked = false   // guarded by this

    private val publishLock = Any()

    // Poweramp says nothing when it dies: a playing track that should have ended long ago counts as stopped
    private val timer by lazy { Handler(Looper.getMainLooper()) }
    private val trackEndCheck = Runnable { checkTrackEnd() }

    /** What Poweramp does right now, as far as this process knows (STOPPED until a broadcast was seen). */
    val snapshot: PlaybackSnapshot get() = tracker.snapshot

    /**
     * Reads Poweramp's sticky TRACK_CHANGED / STATUS_CHANGED broadcasts (current state even if this app was not running),
     * submits the snapshot to the engine and starts the presence service if something is to be shown.
     * Called when the app is opened (on a background thread) and by the service on start. Any thread.
     */
    fun sync(context: Context) {
        checkProcessStart(startedByPoweramp = false)
        tracker.sync()
        publish(context.applicationContext)
    }

    /** A broadcast from Poweramp (manifest receiver, or the service's receiver while it runs). Never throws. */
    fun onBroadcast(context: Context, intent: Intent, replay: Boolean = false) {
        try {
            val app = context.applicationContext
            checkProcessStart(startedByPoweramp = true)
            val update = tracker.onBroadcast(intent, replay)
            // The user's stop follows every play state Poweramp reports, also one the snapshot already went past
            val resumed = update.reportedState?.let(::onPlayState) == true
            when {
                update.snapshot != null -> publish(app)
                resumed -> ensureService(app, tracker.snapshot, Graph.settings.config.value)
            }
        } catch (e: RuntimeException) {
            // The receiver is exported: whatever another app sends must not take the process down
            Graph.log.log("Poweramp: could not handle ${intent.action}: $e")
        }
    }

    /** Settings changed: the change may make something visible (e.g. the presence was switched back on). */
    fun onSettingsChanged(context: Context, old: Config, new: Config) {
        // Switching the presence on again is as explicit as the notification's "Stop"
        if (!old.enabled && new.enabled) PresenceService.userStop.clear()
        // Another application ID gets its chance, also one Discord rejected before (it may have been set up meanwhile)
        if (old.effectiveClientId(Graph.defaultClientId) != new.effectiveClientId(Graph.defaultClientId)) {
            PresenceService.rejectedAppId.clear()
        }
        if (old.enabled != new.enabled) syncReceiverComponent(context.applicationContext)
        ensureService(context.applicationContext, tracker.snapshot, new)
    }

    /**
     * Enables the manifest receiver only while the presence is switched on: switched off, Poweramp's broadcasts would
     * start a killed process for nothing with every song. Follows the current settings (whoever calls last writes the
     * right state); called when the switch changes and once per process start (a process killed in between). The
     * service's receiver, registered at runtime, is not affected. Any thread.
     */
    @Synchronized
    fun syncReceiverComponent(context: Context) {
        try {
            val pm = context.packageManager
            val component = ComponentName(context, PowerampReceiver::class.java)
            // DEFAULT = as the manifest says (enabled); only written when it differs
            val wanted = if (Graph.settings.config.value.enabled) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (pm.getComponentEnabledSetting(component) != wanted) {
                pm.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP)
            }
        } catch (e: RuntimeException) {
            Graph.log.log("Poweramp: could not switch the broadcast receiver: $e")
        }
    }

    /**
     * True if Discord is to show something for this snapshot (playing, or paused with "show paused" and not hidden by a
     * filter or the paused timeout) - the presence service is only started then.
     */
    fun wantsPresence(config: Config, snapshot: PlaybackSnapshot, nowMs: Long): Boolean {
        if (!config.enabled || snapshot.state == PlayState.STOPPED || snapshot.track.isEmpty) return false
        // While paused, the stamp is the time of the pause (good enough for the clearAfterPausedMin check)
        val pausedForMs = if (snapshot.state == PlayState.PAUSED) (nowMs - snapshot.stampMs).coerceAtLeast(0) else 0
        return PresenceTexts.compute(config, snapshot, nowMs, pausedForMs, StatusStrings()).hidden == Hidden.NO
    }

    /**
     * True if the service is worth starting for this application ID: there is one, and Discord did not reject it.
     * Otherwise every song would start it for a minute just to fail again (the status page shows the problem).
     */
    fun canConnect(config: Config, defaultClientId: String = Graph.defaultClientId): Boolean {
        val clientId = config.effectiveClientId(defaultClientId)
        return clientId.toULongOrNull() != null && !PresenceService.rejectedAppId.matches(clientId)
    }

    /**
     * Hands the engine what the tracker knows now. [sync] runs on a background thread while broadcasts arrive on the
     * main thread: reading the snapshot and submitting it under one lock means whoever comes last submits the newest
     * state - a sync that read Poweramp's state just before a broadcast can never overwrite that broadcast's snapshot.
     */
    private fun publish(context: Context) {
        val snapshot = synchronized(publishLock) { tracker.snapshot.also { Graph.engine.submit(it) } }
        PresenceService.userStop.noteState(snapshot.state)   // for a stop from Android's task manager (next process)
        ensureService(context, snapshot, Graph.settings.config.value)
        scheduleTrackEndCheck()
    }

    private fun onPlayState(state: PlayState): Boolean =
        PresenceService.userStop.onPlayState(state).also { resumed ->
            if (resumed) Graph.log.log("Poweramp: playback started again, the presence is back")
        }

    /** Starts the presence service when something is to be shown - unless it runs already or the user stopped it. */
    private fun ensureService(context: Context, snapshot: PlaybackSnapshot, config: Config) {
        if (PresenceService.running.value || PresenceService.userStop.active.value) return
        if (!wantsPresence(config, snapshot, AndroidClock.nowMs()) || !canConnect(config)) return
        // Without Discord's RPC service every song would start the service just to fail and retry (the status page
        // shows the problem); checked on every start, so a Discord installed meanwhile works with the next track
        val available = EnvironmentCheck.discordRpcAvailable(context)
        if (available == discordMissing) {
            discordMissing = !available
            if (!available) Graph.log.log("The Discord app (or its RPC service) is missing - the presence service waits for it")
        }
        if (!available) return
        if (PresenceService.launch(context)) {
            if (backgroundStartBlocked) {
                backgroundStartBlocked = false
                Notifications.cancelBackgroundBlocked(context)
            }
        } else {
            // Android 12+ refuses foreground services started from the background unless the app is excluded from
            // battery optimization: ask the user (logged once per blocked period; the alert comes once a day at most)
            if (!backgroundStartBlocked) Graph.log.log("Android did not allow starting in the background - asking the user")
            backgroundStartBlocked = true
            Notifications.showBackgroundBlocked(context)
        }
    }

    /**
     * Once per process, at its first contact with Poweramp: if Android's task manager stopped the previous process while
     * it showed the presence, that counts like the notification's "Stop".
     */
    @Synchronized
    private fun checkProcessStart(startedByPoweramp: Boolean) {
        if (processChecked) return
        processChecked = true
        if (PresenceService.taskManagerStop.check(startedByPoweramp)) {
            Graph.log.log("Presence service: stopped from Android's active apps - no presence until playback starts again")
            // the state the stopped process saw last: stopped while paused, pressing play brings the presence back
            PresenceService.userStop.stopAtLastKnownState()
        }
    }

    private fun scheduleTrackEndCheck() {
        val deadline = tracker.trackEndDeadline()
        timer.removeCallbacks(trackEndCheck)
        if (deadline != null) timer.postDelayed(trackEndCheck, (deadline - AndroidClock.nowMs()).coerceAtLeast(0))
    }

    private fun checkTrackEnd() {
        try {
            if (tracker.checkTrackEnd() == null) return scheduleTrackEndCheck()
            publish(Graph.app)
        } catch (e: RuntimeException) {
            Graph.log.log("Poweramp: track end check failed: $e")
        }
    }

    /** The current value of a sticky broadcast (registerReceiver with a null receiver only returns it). */
    private fun readSticky(action: String): Intent? = try {
        ContextCompat.registerReceiver(Graph.app, null, IntentFilter(action), ContextCompat.RECEIVER_EXPORTED)
    } catch (e: RuntimeException) {
        Graph.log.log("Poweramp: could not read $action: $e")
        null
    }
}
