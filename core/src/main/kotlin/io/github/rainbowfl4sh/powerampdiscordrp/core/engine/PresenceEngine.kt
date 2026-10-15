package io.github.rainbowfl4sh.powerampdiscordrp.core.engine

import io.github.rainbowfl4sh.powerampdiscordrp.core.assets.AppInfoSource
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.CoverResult
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.CoverSource
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Probe
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.ActivityBuilder
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.ActivityTexts
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.Hidden
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.PresenceTexts
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.StatusStrings
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.TextRotation
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.DiscordUser
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.RpcMessage
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.RpcProtocol
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.RpcTransport
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.TransportError
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.SelectBuilder
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Connection / presence state shown on the status page. */
data class EngineStatus(
    val kind: Kind = Kind.IDLE,
    val error: TransportError = TransportError.NONE,
    val errorDetail: String = "",          // why the connection failed: Discord's own message (English) or exception text
    val user: DiscordUser? = null,
    val clientId: String = "",             // application ID in use ("" = none configured)
    val lastSentWallMs: Long = 0,          // unix ms of the last SET_ACTIVITY
    val connects: Int = 0,                 // successful connections so far
    val visible: Boolean = false,          // Discord shows our presence right now (false once it rejected the update)
    val testing: Boolean = false,          // the test presence is shown
    val hidden: Hidden = Hidden.STOPPED,   // why nothing is shown (NO = something is shown / would be shown)
    val coverUrl: String = "",             // cover Discord gets for the current track ("" = the logo)
    val coverSource: String = "",
    val coverSearching: Boolean = false,
    val activityError: String = "",        // Discord rejected the last presence update (its message), "" = accepted
) {
    enum class Kind { IDLE, DISABLED, NO_APP_ID, CONNECTING, CONNECTED, NOT_CONNECTED }
}

/**
 * Owns the Discord connection, builds the presence and resolves covers (port of the AIMP plugin's PresenceWorker).
 *
 * Rules:
 * - connects (with config.effectiveClientId(defaultClientId)) as soon as something is to be shown (or a test is requested);
 *   no ID -> status NO_APP_ID, disabled -> DISABLED (presence cleared)
 * - presence updates are coalesced: at most one SET_ACTIVITY every [minSendIntervalMs] (the first one after READY at once);
 *   identical activities are not sent twice
 * - hidden (stopped, paused + PAUSED_HIDE, filters, paused timeout) -> SET_ACTIVITY with activity null (once)
 * - track change: cover from peekCache, otherwise the logo is shown and the cover is resolved on [ioDispatcher];
 *   when it arrives (and the track is still the same) the presence is updated
 * - periodic refresh every config.refreshSeconds while playing if PresenceTexts.needsPeriodicRefresh
 * - text rotation ("||", PresenceTexts.needsRotation): while the presence is shown (playing, or paused and not hidden) the
 *   actor wakes at every rotation step (TextRotation.nextStepAt, counted from the snapshot's trackSinceMs) and sends the
 *   next alternatives through the same coalesced path; nothing wakes for it while hidden or if no template rotates.
 *   [submit] sets trackSinceMs: kept while the track stays the same (pause, resume, seek, rating change), the time the
 *   engine heard of it for another track or when playback starts again after a stop. Every step refreshes %pos% as well;
 *   a periodic refresh that would come less than ROTATION_MIN_SECONDS before the next step is left to that step
 * - paused with clearAfterPausedMin > 0 -> hidden when the time is over (counted from the snapshot's stamp of the pause)
 * - cover repair (like AIMP): ActivitySet answer with large_image "mp:external/<x>" -> 4 s later check
 *   https://media.discordapp.net/external/<x> with covers.probe; BROKEN -> resend URL with "?r=1"/"?r=2",
 *   then covers.forget + resolve again, then show the logo for this track. UNKNOWN (our own network trouble) repairs
 *   nothing (checked once more 30 s later); a picture found OK is not checked again for this track.
 * - Discord rejects an update (evt ERROR for the latest frame) -> [EngineStatus.activityError] until Discord accepts one,
 *   not visible; an earlier presence of ours that Discord still shows is cleared (it is outdated), the rejected one is
 *   sent again with the next change of the snapshot / settings
 * - lost connection -> reconnect with backoff 5 s, 10 s, 20 s, ... max 60 s while something is to be shown;
 *   TransportError.INVALID_CLIENT_ID -> no automatic retries until the application ID changes (also away and back) or
 *   [reconnect] / [sendTest]
 * - [idle] becomes true when nothing has been shown for [idleGraceMs] (and no test runs): the Android service may stop then.
 *   "Nothing to show" includes a missing / rejected application ID: that only changes with the settings or a user action.
 *   A rejected ID is remembered across [stop] / [start], so a restarted service does not knock on Discord's door again,
 *   and is reported as NOT_CONNECTED + INVALID_CLIENT_ID all that time.
 * - [stop] clears the presence, disconnects and cancels all jobs
 *
 * Threading: every public method may be called from any thread. All connection / presence state lives in one actor
 * coroutine per [start] (a [Session]) on [scope]; the public methods only set request flags and wake it, transport
 * callbacks and background jobs post events to it. Timers are delays on the scope's dispatcher (a test dispatcher
 * controls them), all times come from [clock].
 */
class PresenceEngine(
    private val transport: RpcTransport,
    private val covers: CoverSource,
    private val config: () -> Config,
    private val defaultClientId: String,
    private val strings: () -> StatusStrings,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.System,
    private val log: Logger = Logger.NONE,
    private val pid: Int = 1,
    private val version: String = "",
    private val minSendIntervalMs: Long = 2_000,
    private val idleGraceMs: Long = 60_000,
    private val appInfo: AppInfoSource = AppInfoSource.NONE,
) {
    private val _status = MutableStateFlow(EngineStatus())
    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    private val _texts = MutableStateFlow(ActivityTexts(hidden = Hidden.STOPPED))
    private val _idle = MutableStateFlow(false)

    val status: StateFlow<EngineStatus> get() = _status
    val snapshot: StateFlow<PlaybackSnapshot> get() = _snapshot

    /** The texts Discord gets for the current snapshot (also when hidden - [ActivityTexts.hidden] says why). */
    val texts: StateFlow<ActivityTexts> get() = _texts

    val idle: StateFlow<Boolean> get() = _idle

    // Requests from the public API. Flags (like the AIMP worker's atomics) rather than queued events: asking twice
    // before the actor got to it changes nothing, and nothing piles up while the engine is stopped.
    private val changed = AtomicBoolean(false)           // snapshot / settings changed: build the presence again
    private val resetCover = AtomicBoolean(false)        // settings / cache changed: take the cover from the cache again
    private val freshCovers = AtomicBoolean(false)       // cover cache cleared: covers given up on get a new chance
    private val reconnectRequest = AtomicBoolean(false)
    private val testRequest = AtomicBoolean(false)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    // Transport callbacks and results of background jobs: order matters, so these are real events.
    private val events = Channel<Event>(Channel.UNLIMITED)

    // Every connection attempt gets its own listener and number: a late callback of an older connection (its close
    // racing a reconnect) is recognized and ignored instead of tearing down the new one.
    private val connectionNumbers = AtomicInteger()

    private val snapshotLock = Any()
    private var pausedSinceMs = 0L                         // guarded by snapshotLock

    private val lifecycleLock = Any()
    private var stopFlag: AtomicBoolean? = null            // of the running session; guarded by lifecycleLock
    private var sessionJob: Job? = null                    // guarded by lifecycleLock

    // Application ID Discord rejected (close code 4000) with Discord's message. Kept across sessions: the service is
    // started again by every Poweramp broadcast, and a known bad ID is not tried again until the application ID changes
    // or the user asks for it (reconnect() / sendTest()) - the same rule the app's RejectedAppId uses for service starts.
    @Volatile
    private var rejection: Rejection? = null

    private class Rejection(val clientId: String, val reason: String)

    init {
        // the status page shows a settings problem (switched off, no application ID) before the service ever ran
        _status.value = restingStatus(EngineStatus(), config())
    }

    fun start() {
        val job = synchronized(lifecycleLock) {
            if (stopFlag != null) return
            val flag = AtomicBoolean(false)
            stopFlag = flag
            _idle.value = false
            val previous = sessionJob
            scope.launch(start = CoroutineStart.LAZY) {
                previous?.join()   // a session that is still stopping clears and disconnects first
                Session(flag).run()
            }.also { job ->
                sessionJob = job
                // a session that ended without stop() (scope cancelled) must not block the next start()
                job.invokeOnCompletion { synchronized(lifecycleLock) { if (stopFlag === flag) stopFlag = null } }
            }
        }
        job.start()   // outside the lock: an immediate dispatcher may run the session right here
    }

    fun stop() {
        synchronized(lifecycleLock) {
            val flag = stopFlag ?: return
            stopFlag = null
            flag.set(true)
        }
        wake.trySend(Unit)
    }

    /** New state from Poweramp (track / play state / position). Its [PlaybackSnapshot.trackSinceMs] is set here. */
    fun submit(snapshot: PlaybackSnapshot) {
        synchronized(snapshotLock) {
            val now = clock.nowMs()
            val previous = _snapshot.value
            if (snapshot.state == PlayState.PAUSED) {
                // from the paused snapshot's stamp (the pause, or a later seek / track change while paused), exactly like
                // PowerampBridge and the live preview count it: a pause heard of late (sticky broadcast after a cold
                // start) has been running for a while already. No stamp: from when the engine heard of the pause.
                pausedSinceMs = when {
                    snapshot.stampMs != 0L -> minOf(snapshot.stampMs, now)
                    previous.state != PlayState.PAUSED -> now
                    else -> pausedSinceMs
                }
            }
            _snapshot.value = snapshot.copy(trackSinceMs = trackSince(previous, snapshot, now))
            computeTexts(config(), strings(), now)
        }
        changed.set(true)
        wake.trySend(Unit)
    }

    /** Settings changed: rebuild the presence (and reconnect if the application ID changed). */
    fun refresh() {
        val cfg = config()
        synchronized(snapshotLock) { computeTexts(cfg, strings(), clock.nowMs()) }
        resetCover.set(true)
        changed.set(true)
        whileResting {   // the status page shows what the new settings mean even while the service is stopped
            if (rejection?.clientId != cfg.effectiveClientId(defaultClientId)) rejection = null   // the ID was changed
            _status.update { restingStatus(it, cfg) }
        }
        wake.trySend(Unit)
    }

    fun clearCoverCache() {
        scope.launch(ioDispatcher) {
            try {
                covers.clearCache()
                log.log("Cover cache cleared")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.log("Clearing the cover cache failed: $e")
            }
            freshCovers.set(true)
            resetCover.set(true)
            changed.set(true)
            wake.trySend(Unit)
        }
    }

    /**
     * Shows a test presence for [TEST_DURATION_MS], also when nothing plays. Connects at once (also during a retry pause)
     * and tries a rejected application ID again: the user asked for it.
     */
    fun sendTest() {
        testRequest.set(true)
        _idle.value = false   // keeps the service alive until the test ran
        forgetRejectionWhileResting()
        wake.trySend(Unit)
    }

    /** Drop the connection and connect again at once. */
    fun reconnect() {
        reconnectRequest.set(true)
        forgetRejectionWhileResting()
        wake.trySend(Unit)
    }

    /** A user action tries a rejected application ID again; a running session does that itself. */
    private fun forgetRejectionWhileResting() {
        whileResting {
            rejection = null
            val cfg = config()
            _status.update { restingStatus(it, cfg) }
        }
    }

    /** Runs [block] (under the lifecycle lock) only if no session runs or is stopping: nothing else writes the state then. */
    private inline fun whileResting(block: () -> Unit) {
        synchronized(lifecycleLock) {
            if (stopFlag == null && sessionJob?.isCompleted != false) block()
        }
    }

    /** What the status page shows while the engine rests: a settings problem (or a rejected application ID) stays visible. */
    private fun restingStatus(s: EngineStatus, cfg: Config): EngineStatus {
        val clientId = cfg.effectiveClientId(defaultClientId)
        val base = s.copy(clientId = clientId, visible = false, testing = false, coverSearching = false, activityError = "")
        val rejected = rejection?.takeIf { it.clientId == clientId }
        return when {
            !cfg.enabled -> base.copy(kind = EngineStatus.Kind.DISABLED, error = TransportError.NONE, errorDetail = "")
            appIdOf(clientId) == null ->
                base.copy(kind = EngineStatus.Kind.NO_APP_ID, error = TransportError.NONE, errorDetail = "")
            rejected != null -> base.copy(
                kind = EngineStatus.Kind.NOT_CONNECTED, error = TransportError.INVALID_CLIENT_ID, errorDetail = rejected.reason,
            )
            else -> base.copy(kind = EngineStatus.Kind.IDLE, error = TransportError.NONE, errorDetail = "")
        }
    }

    /**
     * Start of the text rotation for [next]: [previous]'s while the track stays the same (pause, resume, seek, a rating
     * change), otherwise [now] - for another track, and when playback starts again after a stop. Not the snapshot's stamp:
     * a track heard of late (sticky broadcast after a cold start) would start in the middle of its rotation, and the first
     * alternative gets its full time as the presence goes out about now.
     */
    private fun trackSince(previous: PlaybackSnapshot, next: PlaybackSnapshot, now: Long): Long {
        val restarted = previous.state == PlayState.STOPPED && next.state != PlayState.STOPPED
        return if (!restarted && previous.track.key() == next.track.key()) previous.trackSinceMs else now
    }

    /** Texts for the current snapshot; also published for the live preview and the status page. Call with snapshotLock held. */
    private fun computeTexts(cfg: Config, words: StatusStrings, now: Long): View {
        val s = _snapshot.value
        val pausedFor = if (s.state == PlayState.PAUSED) now - pausedSinceMs else 0
        val t = PresenceTexts.compute(cfg, s, now, pausedFor, words)
        _texts.value = t
        _status.update { if (it.hidden == t.hidden) it else it.copy(hidden = t.hidden) }
        return View(s, t, pausedSinceMs)
    }

    private class View(val snap: PlaybackSnapshot, val texts: ActivityTexts, val pausedSinceMs: Long)

    /** What identifies "the same track" (cover, repair counters): a rating change is not a new track, a new stream title is. */
    private data class TrackKey(val id: Long, val realId: Long, val path: String, val title: String, val artist: String, val album: String)

    private fun TrackInfo.key() = TrackKey(id, realId, path, title, artist, album)

    private sealed interface Event {
        class Ready(val connection: Int, val frame: String) : Event
        class Frame(val connection: Int, val frame: String) : Event
        class Closed(val connection: Int, val error: TransportError, val code: Int, val reason: String) : Event
        class CoverResolved(val session: Session, val track: TrackKey, val result: CoverResult) : Event
        class ProxyChecked(val session: Session, val check: ProxyCheck, val result: Probe) : Event
        class AppInfoLoaded(val session: Session, val appId: String, val loaded: Boolean) : Event
    }

    /** A SET_ACTIVITY with a cover URL ([image] = its large_image) whose answer is awaited. */
    private class Awaited(val nonce: String, val track: TrackKey, val image: String)

    /** Discord's proxy copy ([url]) of a cover we sent ([image]), to be checked at [at]; [again] = the second attempt. */
    private class ProxyCheck(val track: TrackKey, val image: String, val url: String, val at: Long, val again: Boolean = false)

    /** Transport listener of one connection attempt: tags everything with the attempt's number. */
    private inner class ConnectionListener(private val number: Int) : RpcTransport.Listener {
        override fun onReady(frame: String) {
            events.trySend(Event.Ready(number, frame))
        }

        override fun onFrame(frame: String) {
            events.trySend(Event.Frame(number, frame))
        }

        override fun onClosed(error: TransportError, code: Int, reason: String) {
            events.trySend(Event.Closed(number, error, code, reason))
        }
    }

    private enum class Conn { NONE, CONNECTING, READY }

    /** One run of the engine, from [start] to [stop]: the actor that owns the connection and everything sent. */
    private inner class Session(private val stopRequested: AtomicBoolean) {
        // a remembered rejection of the application ID is shown again by the first evaluate()
        private var st = _status.value.copy(
            kind = EngineStatus.Kind.IDLE, error = TransportError.NONE, errorDetail = "",
            visible = false, testing = false, coverSearching = false, activityError = "",
        )

        // ---- connection
        private var conn = Conn.NONE
        private var connection = 0            // number of the current connection attempt (0 = none)
        private var currentId = ""            // application ID this session connects with ("" = not decided yet)
        private var connectDeadline = 0L
        private var justReady = false         // the first update after READY goes out at once
        private var retryAt = 0L
        private var backoffMs = FIRST_RETRY_MS

        /** Discord rejected the application ID: no automatic retries. */
        private val invalidId: Boolean get() = currentId.isNotEmpty() && currentId == rejection?.clientId

        // ---- presence
        private var dirty = true
        private var shown = false             // Discord may show a presence of ours (sent and not cleared)
        private var lastActivity: JsonObject? = null   // last activity sent (duplicate suppression)
        // Discord's answer to the last update decides activityError. A rejected update leaves what Discord showed before:
        // shown goes back to shownBefore, and the rejected activity is not sent again until the snapshot / settings change.
        private var lastNonce: String? = null
        private var shownBefore = false
        private var rejected: JsonObject? = null
        private var lastSendAt: Long? = null
        private var lastBuildAt = 0L
        private var testUntil = 0L
        private var testShown = false
        private var inactiveSince: Long? = null
        private var loggedTrack: TrackKey? = null

        // ---- cover
        private var coverTrack: TrackKey? = null        // track coverUrl belongs to (follows the snapshot)
        private var coverUrl = ""
        private var coverSource = ""
        private var coverPending = false                // coverTrack had no cached cover: resolve once it is shown
        private var recheckCover = false                // settings / cache changed: take coverTrack's cover from the cache again
        private var coverJob: Job? = null
        // Discord shows a cover through its media proxy and remembers per URL when that failed ("?" instead of the
        // picture). The proxy copy is checked after sending until it loaded once for this track; if the host answers
        // without a picture, the URL is sent again with "?r=N" (a new URL for Discord), then the cover is resolved /
        // uploaded again, at last the logo is used.
        private var repairTrack: TrackKey? = null       // track the repair counters belong to
        private var coverBust = ""                      // appended to the cover URL ("?r=1", ...)
        private var coverRepairs = 0
        private var sentImage = ""                      // large_image of the last activity sent
        private var verifiedImage = ""                  // large_image whose proxy copy loaded (repairTrack's)
        private var awaited: Awaited? = null
        private var proxyCheck: ProxyCheck? = null
        private var proxyJob: Job? = null
        private var noCoverTrack: TrackKey? = null      // track whose cover Discord cannot show (logo instead)

        // ---- the application's art assets (their pictures are sent as URLs, see AppInfoSource)
        private var appInfoFor = ""                     // application ID looked up on this connection
        private var appInfoJob: Job? = null

        private var nextAt: Long? = null                // next timer deadline (clock.nowMs), null = none

        suspend fun run() {
            try {
                // callbacks of connections of an earlier run, unhandled when it stopped
                while (events.tryReceive().isSuccess) Unit
                if (stopRequested.get()) return
                safely { evaluate() }
                while (!stopRequested.get()) {
                    val waitMs = nextAt?.let { (it - clock.nowMs()).coerceAtLeast(1) }
                    select {
                        events.onReceive { safely { handle(it) } }
                        wake.onReceive { }
                        if (waitMs != null) timeout(waitMs)
                    }
                    if (stopRequested.get()) break
                    while (true) {
                        val event = events.tryReceive().getOrNull() ?: break
                        safely { handle(event) }
                    }
                    wake.tryReceive()
                    safely { evaluate() }
                }
            } finally {
                shutdown()
            }
        }

        /** A failing component (cover resolver, settings) must not kill the actor: log and carry on. */
        private inline fun safely(block: () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.log("Presence engine error: $e")
            }
            publish()
        }

        private fun schedule(at: Long) {
            val current = nextAt
            if (current == null || at < current) nextAt = at
        }

        private fun publish() {
            val logo = coverTrack != null && coverTrack == noCoverTrack   // Discord cannot show this cover
            _status.value = st.copy(
                hidden = _texts.value.hidden,
                // Discord keeps an earlier presence when it rejects an update: ours is not what it shows then
                visible = shown && conn == Conn.READY && st.activityError.isEmpty(),
                coverUrl = if (logo) "" else coverUrl,
                coverSource = if (logo) "" else coverSource,
                coverSearching = coverJob != null,
            )
        }

        // ------------------------------------------------------------------------------------------- main step

        private fun evaluate() {
            nextAt = null
            val now = clock.nowMs()
            val cfg = config()
            val words = strings()
            val retry = takeRequests(now)
            val testing = now < testUntil
            val view = synchronized(snapshotLock) { computeTexts(cfg, words, now) }
            val texts = view.texts
            val clientId = cfg.effectiveClientId(defaultClientId)
            val appId = appIdOf(clientId)
            val wanted = testing || texts.hidden == Hidden.NO
            st = st.copy(clientId = clientId, testing = testing)
            if (testing) schedule(testUntil)
            // the paused timeout hides the presence (texts, idle) also while not connected
            if (texts.hidden == Hidden.NO && view.snap.state == PlayState.PAUSED && cfg.clearAfterPausedMin > 0) {
                schedule(view.pausedSinceMs + cfg.clearAfterPausedMin * 60_000L)
            }
            // the cover follows the track also while not connected (status page, live preview)
            updateCover(cfg, view.snap)

            // ---- disabled: no connection at all
            if (!cfg.enabled && !testing) {
                if (conn != Conn.NONE) {
                    dropConnection()
                    log.log("Presence disabled - disconnected from Discord")
                }
                st = st.copy(kind = EngineStatus.Kind.DISABLED, error = TransportError.NONE, errorDetail = "")
                trackIdle(now, active = false)
                return
            }

            // ---- no application ID configured
            if (appId == null) {
                if (conn != Conn.NONE) dropConnection()
                st = st.copy(kind = EngineStatus.Kind.NO_APP_ID, error = TransportError.NONE, errorDetail = "")
                trackIdle(now, active = false)
                return
            }

            // ---- another application ID (settings changed): new connection, new chances
            if (clientId != currentId) {
                if (conn != Conn.NONE) {
                    log.log("Application ID changed - reconnecting")
                    dropConnection()
                }
                currentId = clientId
                retryAt = now
                backoffMs = FIRST_RETRY_MS
                st = st.copy(error = TransportError.NONE, errorDetail = "")   // failures of the previous ID do not count
                // a rejection belongs to its ID: changing away from it (and back later) gives the ID a new chance, like
                // the app's RejectedAppId; at session start currentId is "", so the current ID's rejection is kept
                if (rejection?.clientId != clientId) rejection = null
            }
            // test requested: a rejected application ID may work now (e.g. the application was set up meanwhile)
            if (retry && invalidId) {
                rejection = null
                retryAt = now
                st = st.copy(error = TransportError.NONE, errorDetail = "")
            }
            trackIdle(now, active = wanted && !invalidId)

            if (conn == Conn.CONNECTING && now >= connectDeadline) {
                connectionLost(TransportError.CONNECT_FAILED, -1, "Discord did not answer", now)
            }
            when (conn) {
                Conn.NONE -> connectIfWanted(now, wanted, clientId, appId)
                Conn.CONNECTING -> schedule(connectDeadline)
                Conn.READY -> presence(now, cfg, words, view, testing)
            }
        }

        /** Takes the requests of the public API; returns true if a rejected application ID is to be tried again. */
        private fun takeRequests(now: Long): Boolean {
            // applied after a change of the application ID was noticed, so the new ID is the one tried again
            var retry = false
            if (changed.getAndSet(false)) {
                dirty = true
                rejected = null   // a rejected presence is tried again with the next change
            }
            if (resetCover.getAndSet(false)) {
                recheckCover = true   // a pending check of Discord's copy stays: the picture is likely the same
                dirty = true
            }
            if (freshCovers.getAndSet(false)) {
                noCoverTrack = null
                repairTrack = null
                verifiedImage = ""
            }
            if (reconnectRequest.getAndSet(false)) {
                log.log("Reconnecting (requested)")
                if (conn != Conn.NONE) dropConnection()
                rejection = null
                retryAt = now
                backoffMs = FIRST_RETRY_MS
                st = st.copy(error = TransportError.NONE, errorDetail = "")
            }
            if (testRequest.getAndSet(false)) {
                testUntil = now + TEST_DURATION_MS
                testShown = false
                retryAt = now   // asked for by the user: connect at once, also during a retry pause
                retry = true
                log.log("Test presence requested")
            }
            return retry
        }

        private fun trackIdle(now: Long, active: Boolean) {
            if (active) {
                inactiveSince = null
                _idle.value = false
                return
            }
            val since = inactiveSince ?: now.also { inactiveSince = it }
            if (now - since < idleGraceMs) {
                schedule(since + idleGraceMs)
            } else if (!_idle.value) {
                _idle.value = true
                log.log("Nothing to show for ${idleGraceMs / 1000} s - idle")
            }
        }

        // ------------------------------------------------------------------------------------------- connection

        private fun connectIfWanted(now: Long, wanted: Boolean, clientId: String, appId: Long) {
            when {
                // the user has to act (another ID, Reconnect, Test): reported as such, also by a restarted service
                invalidId -> st = st.copy(
                    kind = EngineStatus.Kind.NOT_CONNECTED, error = TransportError.INVALID_CLIENT_ID,
                    errorDetail = rejection?.reason.orEmpty(),
                )
                // only connect when there is something to show; a past failure stays visible on the status page
                !wanted -> st = st.copy(
                    kind = if (st.error == TransportError.NONE) EngineStatus.Kind.IDLE else EngineStatus.Kind.NOT_CONNECTED,
                )
                now < retryAt -> {
                    st = st.copy(kind = EngineStatus.Kind.NOT_CONNECTED)
                    schedule(retryAt)
                }
                else -> {
                    conn = Conn.CONNECTING
                    connection = connectionNumbers.incrementAndGet()
                    connectDeadline = now + CONNECT_TIMEOUT_MS
                    st = st.copy(kind = EngineStatus.Kind.CONNECTING)
                    log.log("Connecting to Discord (application ID $clientId)")
                    appInfoFor = ""           // every connection looks the pictures up again (cheap while still cached),
                    loadAppInfoIfNeeded()   // meanwhile - so the first update can carry their URLs already
                    try {
                        transport.setListener(ConnectionListener(connection))
                        transport.connect(appId)
                    } catch (e: Exception) {
                        connectionLost(TransportError.CONNECT_FAILED, -1, e.toString(), now)
                    }
                    if (conn == Conn.CONNECTING) schedule(connectDeadline) else if (!invalidId) schedule(retryAt)
                }
            }
        }

        private fun onReady(frame: String) {
            val user = (RpcProtocol.parse(frame) as? RpcMessage.Ready)?.user
            conn = Conn.READY
            justReady = true
            dirty = true
            shown = false            // a new connection shows nothing yet
            lastActivity = null
            lastNonce = null
            rejected = null
            testShown = false
            awaited = null
            proxyCheck = null
            backoffMs = FIRST_RETRY_MS
            st = st.copy(
                kind = EngineStatus.Kind.CONNECTED, error = TransportError.NONE, errorDetail = "", activityError = "",
                user = user, connects = st.connects + 1,
            )
            val name = user?.globalName?.ifEmpty { user.username }.orEmpty()
            log.log(if (name.isEmpty()) "Connected to Discord" else "Connected to Discord as $name")
        }

        /** Connection closed by Discord / failed. Retries with a growing pause unless the application ID was rejected. */
        private fun connectionLost(error: TransportError, code: Int, reason: String, now: Long) {
            val err = when {
                code == RpcProtocol.CLOSE_INVALID_CLIENT_ID || error == TransportError.INVALID_CLIENT_ID -> TransportError.INVALID_CLIENT_ID
                error == TransportError.NONE -> TransportError.CLOSED
                else -> error
            }
            val wasReady = conn == Conn.READY
            try {
                transport.disconnect()   // releases the binding; no callback for a deliberate disconnect
            } catch (e: Exception) {
                log.log("Disconnect failed: $e")
            }
            resetConnection()
            st = st.copy(kind = EngineStatus.Kind.NOT_CONNECTED, error = err, errorDetail = reason)
            val what = if (wasReady) "Disconnected from Discord" else "Could not connect to Discord"
            val detail = buildString {
                append(err.name)
                if (code != -1) append(" ").append(code)
                if (reason.isNotEmpty()) append(", ").append(reason)
            }
            if (err == TransportError.INVALID_CLIENT_ID) {
                rejection = Rejection(currentId, reason)
                log.log("$what ($detail): the application ID $currentId is not valid - no retries until the settings change")
            } else {
                retryAt = now + backoffMs
                schedule(retryAt)   // also when lost while sending: nothing else would wake the actor in time
                log.log("$what ($detail) - retrying in ${backoffMs / 1000} s")
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_RETRY_MS)
            }
        }

        /**
         * Deliberate disconnect: the presence is cleared first so it does not linger. The clear frame is queued before
         * disconnect() is called; the transport delivers what was queued before it disconnects.
         */
        private fun dropConnection() {
            if (conn == Conn.READY) {
                try {
                    transport.send(RpcProtocol.setActivity(null, pid, newNonce()))
                } catch (e: Exception) {
                    log.log("Clearing the presence failed: $e")
                }
            }
            try {
                transport.disconnect()
            } catch (e: Exception) {
                log.log("Disconnect failed: $e")
            }
            resetConnection()
        }

        private fun resetConnection() {
            conn = Conn.NONE
            connection = 0
            justReady = false
            shown = false
            testShown = false
            lastActivity = null
            lastNonce = null
            rejected = null
            awaited = null
            proxyCheck = null
            loggedTrack = null
            st = st.copy(activityError = "")   // a new connection gets everything again
        }

        private fun shutdown() {
            try {
                coverJob?.cancel()
                proxyJob?.cancel()
                appInfoJob?.cancel()
                coverJob = null
                proxyJob = null
                appInfoJob = null
                coverTrack = null
                if (conn != Conn.NONE) {
                    dropConnection()
                    log.log("Disconnected from Discord (stopped)")
                }
                transport.setListener(null)
            } catch (e: Exception) {
                log.log("Presence engine error while stopping: $e")
            }
            coverUrl = ""
            coverSource = ""
            shown = false
            st = try {
                restingStatus(st, config())
            } catch (e: Exception) {
                log.log("Presence engine error while stopping: $e")
                st.copy(kind = EngineStatus.Kind.IDLE, testing = false)
            }
            publish()
        }

        // ------------------------------------------------------------------------------------------- events

        private fun handle(event: Event) {
            val now = clock.nowMs()
            when (event) {
                is Event.Ready -> if (event.connection == connection && conn == Conn.CONNECTING) onReady(event.frame)
                is Event.Frame -> if (event.connection == connection && conn == Conn.READY) onFrame(event.frame, now)
                is Event.Closed -> if (event.connection == connection && conn != Conn.NONE) {
                    connectionLost(event.error, event.code, event.reason, now)
                }
                is Event.CoverResolved -> if (event.session === this) onCoverResolved(event)
                is Event.ProxyChecked -> if (event.session === this) onProxyChecked(event, now)
                is Event.AppInfoLoaded -> if (event.session === this) onAppInfoLoaded(event)
            }
        }

        private fun onFrame(frame: String, now: Long) {
            when (val m = RpcProtocol.parse(frame)) {
                is RpcMessage.ActivitySet -> {
                    // Discord took the latest update (an answer to an older one, or to the clear after a rejection, says
                    // nothing about it)
                    if (m.nonce == null || m.nonce == lastNonce) {
                        rejected = null
                        if (st.activityError.isNotEmpty()) {
                            st = st.copy(activityError = "")
                            log.log("Discord accepts the presence again")
                        }
                    }
                    val a = awaited ?: return
                    if (m.nonce != null && m.nonce != a.nonce) return               // answer to an older update
                    awaited = null
                    val image = m.largeImage.orEmpty()
                    // Discord's copy of our URL: checked in a few seconds (unless it loaded already)
                    if (image.startsWith(MP_EXTERNAL) && a.image != verifiedImage) {
                        val url = DISCORD_MEDIA_PROXY + image.substring(MP_EXTERNAL.length)
                        proxyCheck = ProxyCheck(a.track, a.image, url, now + PROXY_CHECK_DELAY_MS)
                    }
                }
                is RpcMessage.Error -> {
                    log.log("Discord error ${m.code}: ${m.message}")
                    if (m.nonce != null && m.nonce == awaited?.nonce) awaited = null
                    if (m.nonce != null && m.nonce != lastNonce) return   // an older update: the latest may still be taken
                    // reported until Discord accepts an update
                    st = st.copy(activityError = m.message.ifEmpty { "Error ${m.code}" })
                    val activity = lastActivity ?: return   // a rejected clear is only reported (no retry loop)
                    lastActivity = null
                    // Discord keeps what it showed before: an earlier presence of ours is outdated now and is cleared;
                    // the rejected one is not sent again until something changes (it would be rejected again)
                    rejected = activity
                    shown = shownBefore
                    if (shown) dirty = true
                }
                is RpcMessage.Ready, is RpcMessage.Other -> Unit
            }
        }

        private fun onCoverResolved(event: Event.CoverResolved) {
            coverJob = null
            if (event.track != coverTrack) {
                log.log("Cover lookup of a previous track finished - ignored")
                return
            }
            coverPending = false
            val url = event.result.url.trim()
            when {
                url.isEmpty() -> {
                    coverUrl = ""
                    coverSource = ""
                    log.log("No cover found")
                }
                url.length > PresenceTexts.MAX_URL_LENGTH || !PresenceTexts.isHttpUrl(url) -> {
                    coverUrl = ""
                    coverSource = ""
                    log.log("Cover URL ignored (Discord takes http(s) URLs of at most ${PresenceTexts.MAX_URL_LENGTH} characters): $url")
                }
                else -> {
                    coverUrl = url
                    coverSource = event.result.source
                    dirty = true   // resend with the cover
                    log.log("Cover found (${event.result.source}): $url")
                }
            }
        }

        private fun onProxyChecked(event: Event.ProxyChecked, now: Long) {
            proxyJob = null
            val check = event.check
            if (check.track != coverTrack || check.image != sentImage) return   // another track / picture by now
            when (event.result) {
                Probe.OK -> {   // Discord can show it: not checked again for this track
                    verifiedImage = check.image
                    if (proxyCheck?.image == check.image) proxyCheck = null
                    return
                }
                Probe.UNKNOWN -> {   // our own network trouble says nothing about Discord's copy: once more a bit later
                    if (!check.again && proxyCheck == null) {
                        proxyCheck = ProxyCheck(check.track, check.image, check.url, now + PROXY_RECHECK_DELAY_MS, again = true)
                        log.log("Discord's copy of the cover could not be checked - trying again in ${PROXY_RECHECK_DELAY_MS / 1000} s")
                    } else {
                        log.log("Discord's copy of the cover could not be checked - checked again with the next update")
                    }
                    return
                }
                Probe.BROKEN -> Unit
            }
            log.log("Discord cannot load the cover (${check.image}) - repairing")
            ++coverRepairs
            when {
                coverRepairs <= 2 -> {   // the same picture under a new URL: Discord loads it again
                    coverBust = (if ('?' in coverUrl) "&" else "?") + "r=$coverRepairs"
                }
                coverRepairs == 3 -> {   // resolve / upload it again (repairTrack keeps the count)
                    val track = _snapshot.value.track
                    if (track.key() == coverTrack) {
                        try {
                            covers.forget(track)
                        } catch (e: Exception) {
                            log.log("Cover cache failed: $e")
                        }
                    }
                    recheckCover = true   // a cache miss now: the logo, then the lookup / upload
                    coverBust = ""
                }
                else -> {   // give up for this track: the logo instead of a "?"
                    log.log("Discord cannot show this cover - showing the app logo for this track")
                    noCoverTrack = coverTrack
                }
            }
            dirty = true
        }

        // ------------------------------------------------------------------------------------------- presence

        private fun presence(now: Long, cfg: Config, words: StatusStrings, view: View, testing: Boolean) {
            val snap = view.snap
            val texts = view.texts
            st = st.copy(kind = EngineStatus.Kind.CONNECTED)
            startProxyCheck(now)
            loadAppInfoIfNeeded()

            // ---- test presence (Advanced tab)
            if (testing) {
                showTest(now, cfg, words)
                return
            }
            if (testShown) {   // test over: back to the normal presence
                testShown = false
                dirty = true
            }

            // ---- decide whether to (re)send
            // text rotation: the next alternatives at every step while shown (also paused), counted from the track's start
            val rotationMs = if (texts.hidden == Hidden.NO && PresenceTexts.needsRotation(cfg)) TextRotation.intervalMs(cfg) else 0L
            val since = snap.trackSinceMs
            val nextStep = if (rotationMs > 0) TextRotation.nextStepAt(since, now, rotationMs) else Long.MAX_VALUE
            val periodic = snap.state == PlayState.PLAYING && texts.hidden == Hidden.NO && PresenceTexts.needsPeriodicRefresh(cfg)
            // the %pos% refresh due at [at] - left to the next rotation step (which refreshes it as well) when that step
            // would follow it within ROTATION_GAP_MS: both together must keep to Discord's 5 updates per 20 s
            fun refreshes(at: Long) = periodic && nextStep - maxOf(at, now) >= ROTATION_GAP_MS
            if (!dirty) {
                if (now - lastBuildAt >= refreshMs(cfg) && refreshes(lastBuildAt + refreshMs(cfg))) dirty = true
                if (rotationMs > 0 && TextRotation.step(since, now, rotationMs) != TextRotation.step(since, lastBuildAt, rotationMs)) {
                    dirty = true
                }
                if (shown && texts.hidden != Hidden.NO) dirty = true   // e.g. paused longer than clearAfterPausedMin
            }
            if (dirty && mayCoalescedSend(now)) send(now, cfg, snap, texts)
            if (conn != Conn.READY) return   // sending failed and the connection is gone

            resolveCoverIfNeeded(cfg, snap)

            // ---- when to look again
            if (dirty) {
                lastSendAt?.let { schedule(it + minSendIntervalMs) }   // Discord rate limit: coalesced, latest wins
            } else {
                val refreshAt = lastBuildAt + refreshMs(cfg)   // after a send: from now
                if (refreshes(refreshAt)) schedule(refreshAt)
                if (rotationMs > 0) schedule(nextStep)
            }
        }

        private fun mayCoalescedSend(now: Long): Boolean {
            val last = lastSendAt
            return justReady || last == null || now - last >= minSendIntervalMs
        }

        private fun refreshMs(cfg: Config): Long = (cfg.refreshSeconds.coerceAtLeast(1) * 1000L).coerceAtLeast(minSendIntervalMs)

        private fun send(now: Long, cfg: Config, snap: PlaybackSnapshot, texts: ActivityTexts) {
            dirty = false
            justReady = false
            lastBuildAt = now
            val key = snap.track.key()   // its cover was taken by evaluate()
            val cover = coverFor(cfg, key)
            val activity = ActivityBuilder.build(cfg, texts, cover, clock.wallMs(), images())
            val tolerance = if (cfg.timestampsInSeconds) 2L else 2_000L
            if (activity != null && !sameActivity(activity, rejected, tolerance)) {
                // the same presence again (e.g. a position update without a seek) - Discord would only redraw it
                if (sameActivity(activity, lastActivity, tolerance)) return
                val nonce = sendFrame(activity, now) ?: return
                shown = true
                awaitProxyCopy(activity, nonce, key, cover != null)
                if (key != loggedTrack) {
                    loggedTrack = key
                    log.log("Presence: " + listOf(texts.details, texts.state).filter { it.isNotEmpty() }.joinToString(" | "))
                }
            } else if (shown) {
                // hidden - or Discord rejected what is to be shown and still shows an earlier presence of ours. That
                // clear does not end the rejection: Discord taking it says nothing about the rejected presence.
                val hidden = activity == null
                sendFrame(null, now, decisive = hidden) ?: return
                shown = false
                loggedTrack = null
                log.log(if (hidden) "Presence cleared (${texts.hidden.name.lowercase()})" else "Presence cleared (Discord rejected the update)")
            }
        }

        /**
         * For a cover URL not yet seen loading from Discord's proxy for this track, Discord's answer (its "mp:external"
         * key) is awaited to check that copy. A picture checked OK is not fetched again with every update.
         */
        private fun awaitProxyCopy(activity: JsonObject, nonce: String, key: TrackKey, isCover: Boolean) {
            val image = largeImage(activity)
            sentImage = image
            if (proxyCheck?.image != image) proxyCheck = null   // a pending check of another picture is moot now
            // only covers are checked / repaired: the logo's URL (the application's own asset) is Discord's own picture
            awaited = if (isCover && image.startsWith("http", ignoreCase = true) && image != verifiedImage) Awaited(nonce, key, image) else null
        }

        /** The application's art asset pictures (key -> URL) known so far; empty = send the keys. */
        private fun images(): Map<String, String> = appInfo.peek(currentId)?.images.orEmpty()

        /** Once per connection: look up the application's art assets in the background, then send their URLs. */
        private fun loadAppInfoIfNeeded() {
            val id = currentId
            if (appInfo === AppInfoSource.NONE || id.isEmpty() || appInfoFor == id || appInfoJob != null) return
            appInfoFor = id
            val before = appInfo.peek(id)
            appInfoJob = scope.launch(ioDispatcher) {
                val info = try {
                    appInfo.fetch(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.log("Looking up the Discord application failed: $e")
                    null
                }
                events.trySend(Event.AppInfoLoaded(this@Session, id, info != null && info != before))
            }
        }

        private fun onAppInfoLoaded(event: Event.AppInfoLoaded) {
            appInfoJob = null
            if (event.loaded && event.appId == currentId) dirty = true   // send the pictures as URLs now
        }

        private fun showTest(now: Long, cfg: Config, words: StatusStrings) {
            if (testShown) return
            if (!mayCoalescedSend(now)) {
                lastSendAt?.let { schedule(it + minSendIntervalMs) }
                return
            }
            justReady = false
            val nonce = sendFrame(ActivityBuilder.test(cfg, version, words.testLine, clock.wallMs(), images()), now)
            if (nonce == null) {
                if (conn == Conn.READY) schedule(now + minSendIntervalMs)
                return
            }
            testShown = true
            shown = true
            awaited = null
            testUntil = maxOf(testUntil, now + TEST_DURATION_MS)   // shown for the full time even after a slow connect
            schedule(testUntil)
            log.log("Test presence sent")
        }

        /**
         * Sends one SET_ACTIVITY; returns its nonce, or null if it could not be sent. [decisive]: Discord's answer to it
         * decides [EngineStatus.activityError] (it is "the last update").
         */
        private fun sendFrame(activity: JsonObject?, now: Long, decisive: Boolean = true): String? {
            val nonce = newNonce()
            val before = shown
            val ok = try {
                transport.send(RpcProtocol.setActivity(activity, pid, nonce))
            } catch (e: Exception) {
                log.log("Sending to Discord failed: $e")
                false
            }
            lastSendAt = now
            if (!ok) {
                lastActivity = null
                if (transport.isReady) {
                    log.log("Sending the presence to Discord failed - trying again")
                    dirty = true
                } else {
                    connectionLost(TransportError.CLOSED, -1, "Sending to Discord failed", now)
                }
                return null
            }
            lastActivity = activity
            if (decisive) {
                lastNonce = nonce
                shownBefore = before
            }
            st = st.copy(lastSentWallMs = clock.wallMs())
            return nonce
        }

        // ------------------------------------------------------------------------------------------- cover

        /**
         * Cover of the snapshot's track: from the cache at once (never touches the network); a miss is resolved after the
         * presence went out. A pending check of Discord's copy is not dropped here: its result only counts while the
         * track and the picture sent are still the same.
         */
        private fun updateCover(cfg: Config, snap: PlaybackSnapshot) {
            if (!cfg.coverEnabled || snap.state == PlayState.STOPPED || snap.track.isEmpty) {
                coverTrack = null
                coverUrl = ""
                coverSource = ""
                coverPending = false
                recheckCover = false
                return
            }
            val key = snap.track.key()
            if (key == coverTrack && !recheckCover) return
            coverTrack = key
            recheckCover = false
            if (repairTrack != key) {   // another track: its cover starts without repairs and is checked again
                repairTrack = key
                coverBust = ""
                coverRepairs = 0
                verifiedImage = ""
            }
            val cached = try {
                covers.peekCache(snap.track)
            } catch (e: Exception) {
                log.log("Cover cache failed: $e")
                CoverResult()
            }
            val url = cached.url.trim()
            if (url.isNotEmpty() && url.length <= PresenceTexts.MAX_URL_LENGTH && PresenceTexts.isHttpUrl(url)) {
                coverUrl = url
                coverSource = cached.source
                coverPending = false
            } else {
                coverUrl = ""
                coverSource = ""
                coverPending = true
            }
        }

        private fun coverFor(cfg: Config, key: TrackKey): String? {
            if (!cfg.coverEnabled || coverUrl.isEmpty() || coverTrack != key || noCoverTrack == key) return null
            val busted = coverUrl + coverBust
            return if (busted.length <= PresenceTexts.MAX_URL_LENGTH) busted else coverUrl
        }

        /**
         * One lookup at a time; a newer track waits for the running one (whose result is then ignored). Only once the
         * presence of this very track went out (shown and nothing left to send): a hidden track's cover (path filter,
         * paused) is never looked up / uploaded, also not while the previous presence still waits for Discord's rate limit.
         */
        private fun resolveCoverIfNeeded(cfg: Config, snap: PlaybackSnapshot) {
            if (!coverPending || coverJob != null || !shown || dirty || testShown || !cfg.coverEnabled) return
            val track = snap.track
            val key = track.key()
            if (key != coverTrack) return
            coverPending = false
            log.log("Looking for the cover of ${describe(track)}")
            val session = this
            coverJob = scope.launch(ioDispatcher) {
                var result = CoverResult()
                try {
                    result = covers.resolve(track, cfg)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.log("Cover lookup failed: $e")
                } finally {
                    // always answered, so a failing lookup does not block the next one
                    events.trySend(Event.CoverResolved(session, key, result))
                }
            }
        }

        private fun startProxyCheck(now: Long) {
            val check = proxyCheck ?: return
            if (proxyJob != null) return   // the running check reports first (and wakes the actor)
            if (check.track != coverTrack || check.image != sentImage) {   // another track / picture by now
                proxyCheck = null
                return
            }
            if (now < check.at) {
                schedule(check.at)
                return
            }
            proxyCheck = null
            val session = this
            proxyJob = scope.launch(ioDispatcher) {
                var result = Probe.UNKNOWN   // our own trouble says nothing about Discord's copy
                try {
                    result = covers.probe(check.url)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.log("Checking Discord's copy of the cover failed: $e")
                } finally {
                    events.trySend(Event.ProxyChecked(session, check, result))
                }
            }
        }
    }

    companion object {
        const val TEST_DURATION_MS = 15_000L

        private const val FIRST_RETRY_MS = 5_000L
        private const val MAX_RETRY_MS = 60_000L

        /**
         * Only a safety net: the transport reports a Discord that does not get READY in time itself (CONNECT_FAILED after
         * [RpcTransport.READY_TIMEOUT_MS], which includes starting the Discord app), so this deadline is a bit longer
         * and never cuts that budget short.
         */
        internal const val CONNECT_TIMEOUT_MS = RpcTransport.READY_TIMEOUT_MS + 5_000L
        private const val PROXY_CHECK_DELAY_MS = 4_000L
        private const val PROXY_RECHECK_DELAY_MS = 30_000L   // after a check our own network trouble prevented

        /**
         * Shortest time from a %pos% refresh to the next rotation step: the shortest rotation interval, so the two together
         * send no more often than a rotation alone may (Discord: 5 updates per 20 s).
         */
        private const val ROTATION_GAP_MS = Config.ROTATION_MIN_SECONDS * 1000L
        private const val MP_EXTERNAL = "mp:external/"
        private const val DISCORD_MEDIA_PROXY = "https://media.discordapp.net/external/"
        private const val TIMESTAMPS = "timestamps"

        private fun newNonce() = UUID.randomUUID().toString()

        /** The Social SDK takes the snowflake as a (signed) 64-bit value; null = no usable application ID. */
        private fun appIdOf(clientId: String): Long? = clientId.toULongOrNull()?.toLong()

        private fun describe(track: TrackInfo): String = when {
            track.title.isNotEmpty() && track.artist.isNotEmpty() -> "\"${track.artist} - ${track.title}\""
            track.title.isNotEmpty() -> "\"${track.title}\""
            else -> track.path
        }

        private fun largeImage(activity: JsonObject): String =
            ((activity["assets"] as? JsonObject)?.get("large_image") as? JsonPrimitive)?.contentOrNull.orEmpty()

        /**
         * Same presence for Discord: equal apart from the timestamps, and those differ by at most [tolerance]
         * (the position Poweramp reports drifts by a second or so between updates).
         */
        internal fun sameActivity(a: JsonObject, b: JsonObject?, tolerance: Long): Boolean {
            if (b == null) return false
            if (a - TIMESTAMPS != b - TIMESTAMPS) return false
            val ta = a[TIMESTAMPS] as? JsonObject
            val tb = b[TIMESTAMPS] as? JsonObject
            if (ta == null || tb == null) return ta == tb
            if (ta.keys != tb.keys) return false
            return ta.keys.all { k ->
                val x = (ta[k] as? JsonPrimitive)?.longOrNull
                val y = (tb[k] as? JsonPrimitive)?.longOrNull
                x != null && y != null && abs(x - y) <= tolerance
            }
        }
    }
}

/** select's onTimeout is still marked experimental; kept in one place. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun SelectBuilder<Unit>.timeout(timeMillis: Long) {
    onTimeout(timeMillis) { }
}
