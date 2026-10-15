package io.github.rainbowfl4sh.powerampdiscordrp

import android.app.Application
import android.os.Process
import android.os.SystemClock
import android.util.Log
import io.github.rainbowfl4sh.powerampdiscordrp.core.assets.AppInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.assets.AppInfoSource
import io.github.rainbowfl4sh.powerampdiscordrp.core.assets.DiscordAppInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.CoverResolver
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.CoverResult
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.CoverSource
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Probe
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.PresenceEngine
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.StatusStrings
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.UpdateChecker
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.RingLog
import io.github.rainbowfl4sh.powerampdiscordrp.cover.AndroidLocalCover
import io.github.rainbowfl4sh.powerampdiscordrp.cover.BitmapJpegEncoder
import io.github.rainbowfl4sh.powerampdiscordrp.discord.DiscordBinderTransport
import io.github.rainbowfl4sh.powerampdiscordrp.service.PresenceService
import io.github.rainbowfl4sh.powerampdiscordrp.settings.SettingsStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Process-wide singletons (no DI framework). Initialized in [App.onCreate].
 * The engine lives as long as the process; [io.github.rainbowfl4sh.powerampdiscordrp.service.PresenceService] starts / stops it.
 */
object Graph {
    lateinit var app: Application
        private set

    // Everything is created on first use; init() only remembers the application (it runs before every receiver,
    // service and activity, so it must stay cheap).
    @Volatile
    private var deps: Deps? = null

    val log: RingLog get() = deps().log
    val http: OkHttpClient get() = deps().http
    val settings: SettingsStore get() = deps().settings
    val covers: CoverResolver get() = deps().covers
    val engine: PresenceEngine get() = deps().engine
    val updates: UpdateChecker get() = deps().updates

    /** Name and art asset pictures of the Discord application (presence and live preview). */
    val appInfo: DiscordAppInfo get() = deps().appInfo

    /**
     * [appInfo] for the screens and the engine: [AppInfoSource.peek] does not create it - and with it the HTTP client,
     * which takes a while to set up - on the main thread. Before its first fetch (in the background) it knows nothing.
     */
    val appInfoSource: AppInfoSource get() = deps().appInfoSource

    /** Discord application ID built into this APK (BuildConfig.DISCORD_APPLICATION_ID, may be blank). */
    val defaultClientId: String get() = BuildConfig.DISCORD_APPLICATION_ID

    val isInitialized: Boolean get() = deps != null

    fun init(app: Application) {
        this.app = app
        deps = Deps(app)
    }

    /**
     * Creates what nearly every start needs - the settings, the engine with its state, the service's saved flags - on a
     * background thread, so the screen (or a Poweramp broadcast) finds them ready instead of reading files and parsing
     * JSON on the main thread. Whoever needs one of them before it is done waits for it (or creates it, if first).
     */
    fun warmUp() {
        val d = deps ?: return
        thread(name = "Warm-up", isDaemon = true) { d.warmUp() }
    }

    /** The engine if something created it already, else null - for callers that must not create it (settings). */
    fun engineIfCreated(): PresenceEngine? = deps?.engineIfCreated()

    private fun deps(): Deps = deps ?: error("Graph.init() has not been called")

    private class Deps(private val app: Application) {
        val log by lazy { RingLog(clock = AndroidClock, forward = { Log.i(LOG_TAG, it) }) }

        val http: OkHttpClient by lazy {
            val userAgent = "PowerampDiscordRP/${BuildConfig.VERSION_NAME} (+https://github.com/${BuildConfig.GITHUB_REPO})"
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(40, TimeUnit.SECONDS)
                .addInterceptor(Interceptor { chain ->
                    val request = chain.request()
                    // Services like MusicBrainz ask for an identifying User-Agent; a request may still set its own
                    if (request.header("User-Agent") != null) chain.proceed(request)
                    else chain.proceed(request.newBuilder().header("User-Agent", userAgent).build())
                })
                .build()
        }

        val settings by lazy { SettingsStore(app) }

        val covers by lazy {
            CoverResolver(
                http = http,
                cacheFile = File(app.filesDir, "covers.tsv"),
                local = AndroidLocalCover(app, log),
                encoder = BitmapJpegEncoder,
                clock = AndroidClock,
                log = log,
            )
        }

        // The engine reaches the resolver through this: it is created (HTTP client, cache file) on the engine's thread
        // when a track first needs its cover - not with the engine, which the screen may create on the main thread
        private val coverSource = object : CoverSource {
            override fun peekCache(track: TrackInfo): CoverResult = covers.peekCache(track)
            override fun resolve(track: TrackInfo, config: Config): CoverResult = covers.resolve(track, config)
            override fun forget(track: TrackInfo) = covers.forget(track)
            override fun clearCache() = covers.clearCache()
            override fun reachable(url: String): Boolean = covers.reachable(url)
            override fun probe(url: String): Probe = covers.probe(url)
        }

        private val engineLazy = lazy {
            // One thread for all engine state (no locking inside the engine), network work goes to Dispatchers.IO.
            // An exception escaping an engine job is logged instead of taking the whole process down.
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Default.limitedParallelism(1) +
                    CoroutineExceptionHandler { _, e -> log.log("Presence engine error: $e") },
            )
            PresenceEngine(
                transport = DiscordBinderTransport(app, log),
                covers = coverSource,
                config = { settings.config.value },
                defaultClientId = defaultClientId,
                strings = ::statusStrings,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                clock = AndroidClock,
                log = log,
                pid = Process.myPid(),
                version = BuildConfig.VERSION_NAME,
                appInfo = appInfoSource,
            )
        }
        val engine: PresenceEngine by engineLazy

        val updates by lazy { UpdateChecker(http, BuildConfig.GITHUB_REPO) }

        private val appInfoLazy = lazy { DiscordAppInfo(http, AndroidClock, log) }
        val appInfo: DiscordAppInfo by appInfoLazy

        val appInfoSource: AppInfoSource = object : AppInfoSource {
            override fun peek(appId: String): AppInfo? = if (appInfoLazy.isInitialized()) appInfoLazy.value.peek(appId) else null
            override fun fetch(appId: String): AppInfo? = appInfoLazy.value.fetch(appId)
        }

        fun engineIfCreated(): PresenceEngine? = if (engineLazy.isInitialized()) engineLazy.value else null

        fun warmUp() {
            try {
                settings
                engine
                PresenceService.loadSavedState()
            } catch (e: Exception) {   // whoever needs it next creates it again (and gets the error there)
                Log.w(LOG_TAG, "Warm-up failed", e)
            }
        }

        // Read on every use, so a language change applies without restarting the process
        private fun statusStrings() = StatusStrings(
            playing = app.getString(R.string.status_playing),
            paused = app.getString(R.string.status_paused),
            testLine = app.getString(R.string.status_test_line),
        )
    }

    const val LOG_TAG = "PowerampDiscordRP"
}

/** Android time source: monotonic elapsedRealtime (keeps running in deep sleep) and the wall clock. */
object AndroidClock : Clock {
    override fun nowMs(): Long = SystemClock.elapsedRealtime()
    override fun wallMs(): Long = System.currentTimeMillis()
}
