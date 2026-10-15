package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.rainbowfl4sh.powerampdiscordrp.BuildConfig
import io.github.rainbowfl4sh.powerampdiscordrp.Graph
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.assets.AppInfoSource
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.PresenceEngine
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.ActivityTexts
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.UpdateChecker
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.UpdateResult
import io.github.rainbowfl4sh.powerampdiscordrp.poweramp.PowerampBridge
import io.github.rainbowfl4sh.powerampdiscordrp.service.PresenceService
import io.github.rainbowfl4sh.powerampdiscordrp.settings.EnvState
import io.github.rainbowfl4sh.powerampdiscordrp.settings.EnvironmentCheck
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.RemoteImages
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.ConfigEditor
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.ConfigStore
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UiText
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UpdateCheckState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UpdateSchedule
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UpdateView
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.Appearance
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.AppearanceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/**
 * State and actions of the settings screen. Settings go through a [ConfigEditor] (typing is saved debounced),
 * everything live (connection, track, log) comes straight from [Graph].
 */
class MainViewModel(app: Application) : AndroidViewModel(app), AppModel {
    private val store = object : ConfigStore {
        override val config: StateFlow<Config> get() = Graph.settings.config
        override fun update(change: (Config) -> Config) = Graph.settings.update(change)
    }

    override val editor = ConfigEditor(store, viewModelScope)

    /** The settings including edits that are not saved yet (what the screens and the live preview show). */
    override val config: StateFlow<Config> get() = editor.draft

    private val engine: PresenceEngine get() = Graph.engine
    override val status: StateFlow<EngineStatus> get() = engine.status
    override val snapshot: StateFlow<PlaybackSnapshot> get() = engine.snapshot
    override val texts: StateFlow<ActivityTexts> get() = engine.texts
    override val serviceRunning: StateFlow<Boolean> get() = PresenceService.running
    override val stoppedByUser: StateFlow<Boolean> get() = PresenceService.stoppedByUser
    override val logLines: StateFlow<List<String>> get() = Graph.log.lines
    override val defaultClientId: String get() = Graph.defaultClientId
    override val appInfo: AppInfoSource get() = Graph.appInfoSource

    override fun isAppIdRejected(clientId: String): Boolean = PresenceService.rejectedAppId.matches(clientId)

    private val _env = MutableStateFlow<EnvState?>(null)

    /** Result of the last environment check (null until the first one finished). */
    override val env: StateFlow<EnvState?> = _env.asStateFlow()

    private val _update = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    override val update: StateFlow<UpdateCheckState> = _update.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)

    /** Short confirmations / errors for the snackbar. */
    override val messages: Flow<UiText> = _messages.receiveAsFlow()

    init {
        checkForUpdatesIfDue()
    }

    override fun onCleared() {
        editor.flush()
    }

    // ---- status page

    /** Checks Poweramp / Discord / battery / notifications again (on every resume: the user may have fixed something). */
    override fun refreshEnvironment() {
        viewModelScope.launch {
            val state = withContext(Dispatchers.Default) {
                guarded("environment check") { EnvironmentCheck.check(getApplication()) }
            }
            if (state != null) _env.value = state
        }
    }

    /**
     * Reads Poweramp's current state (sticky broadcasts) - the app may have been started while music plays.
     * In the background, one after the other: reading the broadcasts (and, once per process, Android's record of how
     * the last process ended) are calls into the system that would hold up the screen on every resume.
     */
    fun syncPoweramp() {
        viewModelScope.launch(syncDispatcher) {
            try {
                PowerampBridge.sync(getApplication())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {   // defensive: opening the settings must never fail because of Poweramp
                logSafely("Settings: could not read Poweramp's state: $e")
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        editor.set { it.copy(enabled = enabled) }
        Graph.log.log("Settings: Rich Presence switched ${if (enabled) "on" else "off"}")
        if (enabled) syncPoweramp()
    }

    override fun sendTest() {
        // The engine runs inside the service: start it, it stays while the test is shown. Without the service nothing
        // would be sent now - and the request would wait for the next start of the engine.
        val wasStopped = PresenceService.stoppedByUser.value
        if (!launchService(quiet = true)) return
        engine.sendTest()
        Graph.log.log("Settings: test presence requested")
        // only requested: the status page says when Discord shows it
        _messages.trySend(UiText(R.string.msg_test_sent, (PresenceEngine.TEST_DURATION_MS / 1000).toInt()))
        if (wasStopped) _messages.trySend(UiText(R.string.msg_stop_ended))   // starting the service ends the user's stop
    }

    override fun reconnect() {
        val wasStopped = PresenceService.stoppedByUser.value
        if (!launchService(quiet = true)) return
        // the engine forgets a rejected application ID now - so must the app, or Poweramp's next play would not start
        // the service for it and Discord would never be asked again
        PresenceService.rejectedAppId.clear()
        engine.reconnect()
        Graph.log.log("Settings: reconnect requested")
        _messages.trySend(UiText(R.string.msg_reconnecting))
        if (wasStopped) _messages.trySend(UiText(R.string.msg_stop_ended))
    }

    override fun startService(quiet: Boolean) {
        launchService(quiet)
    }

    /** Starts the presence service; false (and the snackbar says why) if Android did not allow it. */
    private fun launchService(quiet: Boolean): Boolean {
        val started = try {
            PresenceService.start(getApplication())
        } catch (e: RuntimeException) {
            Graph.log.log("Settings: could not start the presence service: $e")
            false
        }
        if (!started) _messages.trySend(UiText(R.string.msg_start_blocked))
        else if (!quiet) _messages.trySend(UiText(R.string.msg_service_started))
        return started
    }

    override fun stopService() {
        PresenceService.stop(getApplication())
        _messages.trySend(UiText(R.string.msg_service_stopped))
    }

    // ---- cover tab

    override fun clearCoverCache() {
        engine.clearCoverCache()
        Graph.log.log("Settings: cover cache cleared")
        _messages.trySend(UiText(R.string.msg_cover_cleared))
    }

    // ---- advanced tab

    override fun clearLog() = Graph.log.clear()

    /** The log with a header for bug reports (app / Android version, device). */
    override fun logReport(): String = buildString {
        append("Poweramp Discord RP ").append(BuildConfig.VERSION_NAME)
        append(" - Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")")
        append(" - ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        _env.value?.powerampVersion?.let { append("Poweramp ").append(it).append('\n') }
        append('\n')
        logLines.value.forEach { append(it).append('\n') }
    }

    override fun exportSettings(uri: Uri) {
        editor.flush()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                guarded("settings export") {
                    val json = Graph.settings.exportJson().toByteArray()
                    openForWriting(uri)?.use { it.write(json) } != null
                } ?: false
            }
            Graph.log.log(if (ok) "Settings exported" else "Settings export failed")
            _messages.trySend(UiText(if (ok) R.string.msg_exported else R.string.msg_export_failed))
        }
    }

    override fun importSettings(uri: Uri) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) {
                guarded("settings import") {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_IMPORT_BYTES) }
                }
            }
            editor.discard()   // the file replaces everything, also what is still being typed
            val ok = json != null && Graph.settings.importJson(json.decodeToString())
            Graph.log.log(if (ok) "Settings imported" else "Settings import failed: not a settings file of this app")
            _messages.trySend(UiText(if (ok) R.string.msg_imported else R.string.msg_import_failed))
            if (ok) syncPoweramp()
        }
    }

    override fun resetToDefaults() {
        editor.discard()
        Graph.settings.resetToDefaults()
        Graph.log.log("Settings reset to defaults")
        _messages.trySend(UiText(R.string.msg_reset))
        syncPoweramp()
    }

    // ---- about tab

    /** Asks GitHub for the newest release; [manual] = "Check now" (the result is also reported in the snackbar). */
    override fun checkForUpdates(manual: Boolean) {
        if (_update.value == UpdateCheckState.Checking) return
        checkedThisSession = true
        _update.value = UpdateCheckState.Checking
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { guarded("update check") { Graph.updates.check() } }
                ?: UpdateResult.Failed(UpdateResult.Reason.BAD_ANSWER, "error")
            if (result is UpdateResult.Failed) {
                // "no release published yet" is GitHub's answer: the check is done until the next one is due
                if (result.reason == UpdateResult.Reason.NO_RELEASE) {
                    editor.set { it.copy(updateLastCheckMs = System.currentTimeMillis()) }
                }
                _update.value = UpdateCheckState.Failed(result.reason)
                Graph.log.log("Update check failed (${result.reason.name.lowercase()}: ${result.detail})")
                if (manual) _messages.trySend(UpdateView.failureLine(result.reason).text)
                return@launch
            }
            val release = (result as UpdateResult.Found).release
            editor.set { it.copy(updateLastCheckMs = System.currentTimeMillis(), updateLatest = release.version) }
            _update.value = UpdateCheckState.Done(release)
            val newer = UpdateChecker.isNewer(release.version, BuildConfig.VERSION_NAME)
            Graph.log.log("Update check: newest release ${release.version}" + if (newer) " - an update is available" else "")
            if (manual && !newer) _messages.trySend(UiText(R.string.msg_up_to_date))
        }
    }

    private fun checkForUpdatesIfDue() {
        val due = guarded("update schedule") {
            UpdateSchedule.isDue(store.config.value, System.currentTimeMillis(), checkedThisSession)
        } ?: false
        if (due) checkForUpdates(manual = false)
    }

    // ---- app settings (top bar menu) and pictures of the screens

    private val appearanceStore = AppearanceStore.get(app)
    override val appearance: StateFlow<Appearance> get() = appearanceStore.appearance

    override fun setAppearance(appearance: Appearance) = appearanceStore.update { appearance }

    override fun cachedPicture(url: String): ImageBitmap? = RemoteImages.peek(url)

    override suspend fun loadPicture(url: String): ImageBitmap? = RemoteImages.load(url)

    // ---- helpers

    /** Runs [block]; a failure is logged and gives null (coroutine cancellation is passed on). */
    private inline fun <T> guarded(what: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logSafely("Settings: $what failed: $e")
        null
    }

    private fun logSafely(message: String) {
        try {
            Graph.log.log(message)
        } catch (_: Throwable) {
        }
    }

    /** "wt" truncates an existing file; some document providers only know "w". */
    private fun openForWriting(uri: Uri): OutputStream? {
        val resolver = getApplication<Application>().contentResolver
        return try {
            resolver.openOutputStream(uri, "wt")
        } catch (_: FileNotFoundException) {
            resolver.openOutputStream(uri, "w")
        } catch (_: IllegalArgumentException) {
            resolver.openOutputStream(uri, "w")
        }
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > max) return null   // far too big for a settings file
            out.write(buffer, 0, n)
        }
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 1024 * 1024

        /** Poweramp syncs run in order (a later one must not overtake an earlier one). */
        private val syncDispatcher = Dispatchers.IO.limitedParallelism(1)

        /** "Check at every start" means once per process (like the AIMP plugin's once per AIMP session). */
        @Volatile
        private var checkedThisSession = false
    }
}
