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
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UiText
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UpdateCheckState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.Appearance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the screens show and can do. Implemented by [MainViewModel]; the screens only know this interface, so
 * they can also be drawn with made-up data (screenshots, previews).
 */
interface AppModel {
    /** Settings changes: switches are saved at once, typing debounced. */
    val editor: ConfigEditor

    /** The settings including edits not saved yet. */
    val config: StateFlow<Config>
    val status: StateFlow<EngineStatus>
    val snapshot: StateFlow<PlaybackSnapshot>
    val texts: StateFlow<ActivityTexts>
    val serviceRunning: StateFlow<Boolean>

    /** The user stopped the presence (status page / notification) until Poweramp plays again. */
    val stoppedByUser: StateFlow<Boolean>
    val logLines: StateFlow<List<String>>

    /** Result of the last environment check, null until the first one finished. */
    val env: StateFlow<EnvState?>
    val update: StateFlow<UpdateCheckState>

    /** Short confirmations / errors for the snackbar. */
    val messages: Flow<UiText>

    /** Discord application ID built into this APK (may be blank). */
    val defaultClientId: String

    /** Name and art asset pictures of a Discord application (the live preview shows them like Discord). */
    val appInfo: AppInfoSource

    /**
     * Discord rejected [clientId] before (remembered across processes, unlike the engine): Poweramp does not start the
     * presence service for it until it changes or Discord accepts it (Reconnect / Send test presence).
     */
    fun isAppIdRejected(clientId: String): Boolean

    fun refreshEnvironment()
    fun setEnabled(enabled: Boolean)
    fun sendTest()
    fun reconnect()
    fun startService(quiet: Boolean)
    fun stopService()
    fun clearCoverCache()
    fun clearLog()
    fun logReport(): String
    fun exportSettings(uri: Uri)
    fun importSettings(uri: Uri)
    fun resetToDefaults()
    fun checkForUpdates(manual: Boolean)

    /** How the app looks (top bar menu -> App settings): theme, AMOLED black, dynamic color. Not part of [config]. */
    val appearance: StateFlow<Appearance>

    /** Saved at once; the theme follows while the settings dialog is still open. */
    fun setAppearance(appearance: Appearance)

    /** A picture from the web that is in memory already (e.g. the author's avatar on the About tab), else null. */
    fun cachedPicture(url: String): ImageBitmap?

    /** Downloads the picture behind [url] off the main thread and keeps it in memory; null on failure / offline. */
    suspend fun loadPicture(url: String): ImageBitmap?
}
