package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import io.github.rainbowfl4sh.powerampdiscordrp.core.assets.AppInfoSource
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.ActivityTexts
import io.github.rainbowfl4sh.powerampdiscordrp.settings.EnvState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.ConfigEditor
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.ConfigStore
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UiText
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UpdateCheckState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.Appearance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Made-up screen state without the app's runtime (Graph); records the actions the screens trigger. */
class FakeAppModel(
    scope: CoroutineScope,
    config: Config = Config(),
    env: EnvState? = null,
    status: EngineStatus = EngineStatus(),
    snapshot: PlaybackSnapshot = PlaybackSnapshot(),
    override val defaultClientId: String = "1555109720807702559",
    override val appInfo: AppInfoSource = AppInfoSource.NONE,
) : AppModel {
    val stored = MutableStateFlow(config)
    private val store = object : ConfigStore {
        override val config: StateFlow<Config> = stored
        override fun update(change: (Config) -> Config) {
            stored.value = change(stored.value)
        }
    }
    val actions = mutableListOf<String>()

    override val editor = ConfigEditor(store, scope)
    override val config: StateFlow<Config> get() = editor.draft
    override val status = MutableStateFlow(status)
    override val snapshot = MutableStateFlow(snapshot)
    override val texts = MutableStateFlow(ActivityTexts())
    override val serviceRunning = MutableStateFlow(false)
    override val stoppedByUser = MutableStateFlow(false)
    override val logLines = MutableStateFlow(listOf("12:00:01  Presence service: started", "12:00:02  Discord: READY"))
    override val env = MutableStateFlow(env)
    override val update = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    override val messages: Flow<UiText> = emptyFlow()

    /** Application ID Discord rejected in an earlier process ("" = none). */
    var rejectedAppId = ""

    override fun isAppIdRejected(clientId: String): Boolean = clientId.isNotEmpty() && clientId == rejectedAppId

    override fun refreshEnvironment() { actions += "refreshEnvironment" }
    override fun setEnabled(enabled: Boolean) { actions += "setEnabled $enabled" }
    override fun sendTest() { actions += "sendTest" }
    override fun reconnect() { actions += "reconnect" }
    override fun startService(quiet: Boolean) { actions += "startService" }
    override fun stopService() { actions += "stopService" }
    override fun clearCoverCache() { actions += "clearCoverCache" }
    override fun clearLog() { actions += "clearLog" }
    override fun logReport(): String = logLines.value.joinToString("\n")
    override fun exportSettings(uri: Uri) { actions += "export" }
    override fun importSettings(uri: Uri) { actions += "import" }
    override fun resetToDefaults() { actions += "reset" }
    override fun checkForUpdates(manual: Boolean) { actions += "checkForUpdates $manual" }

    override val appearance = MutableStateFlow(Appearance())
    override fun setAppearance(appearance: Appearance) {
        actions += "setAppearance $appearance"
        this.appearance.value = appearance
    }

    /** Pictures "on the web" by URL (none: every picture fails to load, like offline); [loadedPictures] records the requests. */
    val pictures = mutableMapOf<String, ImageBitmap>()
    val loadedPictures = mutableListOf<String>()
    override fun cachedPicture(url: String): ImageBitmap? = pictures[url]
    override suspend fun loadPicture(url: String): ImageBitmap? {
        loadedPictures += url
        return pictures[url]
    }
}
