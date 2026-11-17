package io.github.rainbowfl4sh.powerampdiscordrp.update

import android.content.Context
import android.content.Intent
import io.github.rainbowfl4sh.powerampdiscordrp.BuildConfig
import io.github.rainbowfl4sh.powerampdiscordrp.Graph
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.DownloadResult
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.ReleaseInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.UpdateChecker
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.UpdateDownloader
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.UpdateResult
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UpdateCheckState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UpdateSchedule
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where an update installed from the app stands (the About tab shows it). */
sealed interface InstallState {
    data object Idle : InstallState

    /** [percent] 0..100, -1 while the size is unknown. */
    data class Downloading(val version: String, val percent: Int) : InstallState
    data class Installing(val version: String) : InstallState

    /** Android waits for the user to confirm the installation (its dialog, or the notification while in the background). */
    data class Confirm(val version: String) : InstallState
    data class Failed(val version: String, val reason: Reason, val detail: String = "") : InstallState

    enum class Reason { NO_APK, NO_CHECKSUM, OFFLINE, DAMAGED, WRONG_APP, STORAGE, ABORTED, INSTALL }
}

/**
 * The update check and the installation of new versions, for the About tab and the presence service alike (like the
 * AIMP plugin: checked on the schedule, downloaded from GitHub, checked against the SHA-256 GitHub publishes and
 * installed). The APK must also be this app, newer, and signed with the same key ([UpdateInstaller.verify]); Android
 * installs it through its PackageInstaller - the first time, and always on Android 8-11, after the user confirmed it.
 */
object AppUpdater {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> Graph.log.log("Update error: $e") },
    )
    private val downloader by lazy { UpdateDownloader(Graph.http) }

    private val _check = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    private val _install = MutableStateFlow<InstallState>(InstallState.Idle)
    private var checkJob: Job? = null
    private var installJob: Job? = null

    /** Progress / result of the check started in this process (earlier results are in the settings). */
    val check: StateFlow<UpdateCheckState> = _check.asStateFlow()

    /** Progress of an installation started in this process. */
    val install: StateFlow<InstallState> = _install.asStateFlow()

    /** A check ran since the process started ("every app start" = once per start). */
    @Volatile
    var checkedThisSession = false
        private set

    /** The app's screen is in the foreground: Android's confirmation can be shown at once instead of a notification. */
    @Volatile
    var uiVisible = false

    /**
     * Asks GitHub for the newest release; an update is installed right away if "install automatically" is on.
     * [onResult] is called on the main thread (the snackbar of a manual check). Main thread.
     */
    fun checkNow(context: Context, onResult: (UpdateResult) -> Unit = {}) {
        if (checkJob?.isActive == true) return
        checkedThisSession = true
        _check.value = UpdateCheckState.Checking
        val app = context.applicationContext
        checkJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Graph.updates.check()
                } catch (e: RuntimeException) {
                    UpdateResult.Failed(UpdateResult.Reason.BAD_ANSWER, e.toString())
                }
            }
            if (result is UpdateResult.Failed) {
                // "no release published yet" is GitHub's answer: the check is done until the next one is due
                if (result.reason == UpdateResult.Reason.NO_RELEASE) {
                    Graph.settings.update { it.copy(updateLastCheckMs = System.currentTimeMillis()) }
                }
                _check.value = UpdateCheckState.Failed(result.reason)
                Graph.log.log("Update check failed (${result.reason.name.lowercase()}: ${result.detail})")
            } else {
                val release = (result as UpdateResult.Found).release
                Graph.settings.update { it.copy(updateLastCheckMs = System.currentTimeMillis(), updateLatest = release.version) }
                _check.value = UpdateCheckState.Done(release)
                val newer = isNewer(release)
                Graph.log.log("Update check: newest release ${release.version}" + if (newer) " - an update is available" else "")
                if (newer && Graph.settings.config.value.updateAutoInstall) install(app, release)
            }
            onResult(result)
        }
    }

    /** The automatic check, when it is due (app start, start of the presence service). Main thread. */
    fun checkIfDue(context: Context) {
        val due = try {
            UpdateSchedule.isDue(Graph.settings.config.value, System.currentTimeMillis(), checkedThisSession)
        } catch (e: RuntimeException) {
            Graph.log.log("Update schedule: $e")
            false
        }
        if (due) checkNow(context)
    }

    /**
     * "Install" on the About tab: the release found by this process's check, else a new check first (which installs
     * whatever it finds, also with "install automatically" off). Main thread.
     */
    fun installLatest(context: Context) {
        val found = (_check.value as? UpdateCheckState.Done)?.release
        if (found != null && isNewer(found)) {
            install(context.applicationContext, found)
            return
        }
        val app = context.applicationContext
        checkNow(app) { result ->
            val release = (result as? UpdateResult.Found)?.release ?: return@checkNow
            if (isNewer(release) && !Graph.settings.config.value.updateAutoInstall) install(app, release)
        }
    }

    /** Downloads, checks and installs [release] (once at a time). Main thread. */
    fun install(context: Context, release: ReleaseInfo) {
        if (installJob?.isActive == true) return
        if (_install.value.let { it is InstallState.Confirm && it.version == release.version }) {
            UpdateNotifications.cancelConfirm(context)   // asked again: a fresh session asks again
        }
        val app = context.applicationContext
        _install.value = InstallState.Downloading(release.version, -1)
        installJob = scope.launch {
            val dir = File(app.cacheDir, UPDATE_DIR)
            val target = File(dir, "PowerampDiscordRP-${release.version}.apk")
            val result = withContext(Dispatchers.IO) {
                dir.listFiles()?.filter { it != target }?.forEach { it.delete() }   // older downloads
                downloader.download(release, target) { p -> _install.value = InstallState.Downloading(release.version, p) }
            }
            if (result is DownloadResult.Failed) {
                Graph.log.log("Update ${release.version}: download failed (${result.reason.name.lowercase()}: ${result.detail})")
                _install.value = InstallState.Failed(release.version, reasonOf(result.reason), result.detail)
                return@launch
            }
            val apk = (result as DownloadResult.Done).file
            val wrong = withContext(Dispatchers.IO) { UpdateInstaller.verify(app, apk) }
            if (wrong != null) {
                Graph.log.log("Update ${release.version}: rejected - $wrong")
                apk.delete()
                _install.value = InstallState.Failed(release.version, InstallState.Reason.WRONG_APP, wrong)
                return@launch
            }
            Graph.log.log("Update ${release.version}: downloaded and checked (SHA-256, signature) - installing")
            _install.value = InstallState.Installing(release.version)
            val error = withContext(Dispatchers.IO) { UpdateInstaller.commit(app, apk, release.version) }
            if (error != null) {
                Graph.log.log("Update ${release.version}: could not start the installation - $error")
                _install.value = InstallState.Failed(release.version, InstallState.Reason.INSTALL, error)
            }
        }
    }

    /** Android wants the user's confirmation ([confirm] = its dialog). Main thread (the status receiver). */
    internal fun onNeedsConfirmation(context: Context, version: String, confirm: Intent?) {
        _install.value = InstallState.Confirm(version)
        if (confirm == null) {
            _install.value = InstallState.Failed(version, InstallState.Reason.INSTALL, "no confirmation dialog")
            return
        }
        val shown = uiVisible && try {
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: RuntimeException) {
            Graph.log.log("Update $version: could not open Android's confirmation: $e")
            false
        }
        if (!shown) UpdateNotifications.showConfirm(context, version, confirm)
        Graph.log.log("Update $version: waiting for the user to confirm the installation")
    }

    /** The installation ended without the new version (aborted, failed). Main thread (the status receiver). */
    internal fun onInstallFailed(context: Context, version: String, aborted: Boolean, message: String) {
        UpdateNotifications.cancelConfirm(context)
        Graph.log.log("Update $version: " + if (aborted) "installation cancelled" else "installation failed: $message")
        _install.value = InstallState.Failed(
            version, if (aborted) InstallState.Reason.ABORTED else InstallState.Reason.INSTALL, message,
        )
    }

    /** The new version is in place (Android ends this process right after). */
    internal fun onInstalled(version: String) {
        Graph.log.log("Update $version installed")
        _install.value = InstallState.Idle
    }

    /** The app was just replaced by a new version (MY_PACKAGE_REPLACED, in the new version's process). */
    internal fun onUpdated(context: Context) {
        File(context.cacheDir, UPDATE_DIR).deleteRecursively()
        UpdateNotifications.cancelConfirm(context)
        Graph.log.log("Updated to ${BuildConfig.VERSION_NAME}")
        UpdateNotifications.showUpdated(context, BuildConfig.VERSION_NAME)
    }

    private fun isNewer(release: ReleaseInfo) = UpdateChecker.isNewer(release.version, BuildConfig.VERSION_NAME)

    private fun reasonOf(r: DownloadResult.Reason): InstallState.Reason = when (r) {
        DownloadResult.Reason.NO_APK -> InstallState.Reason.NO_APK
        DownloadResult.Reason.NO_CHECKSUM -> InstallState.Reason.NO_CHECKSUM
        DownloadResult.Reason.OFFLINE -> InstallState.Reason.OFFLINE
        DownloadResult.Reason.DAMAGED -> InstallState.Reason.DAMAGED
        DownloadResult.Reason.STORAGE -> InstallState.Reason.STORAGE
    }

    private const val UPDATE_DIR = "updates"
}
