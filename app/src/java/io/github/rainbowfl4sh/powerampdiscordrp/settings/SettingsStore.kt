package io.github.rainbowfl4sh.powerampdiscordrp.settings

import android.content.Context
import io.github.rainbowfl4sh.powerampdiscordrp.Graph
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.poweramp.PowerampBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Persists [Config] (JSON in SharedPreferences). Every change is visible at once through [config]. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _config = MutableStateFlow(load())

    /**
     * Called after every change of what Discord shows (outside the lock). Tells the engine to rebuild the presence -
     * only if it exists already: creating it from here would be an init cycle (the engine reads the settings) - and
     * starts the service if the change makes something visible (e.g. the presence was switched back on).
     * Replaced in tests.
     */
    internal var onChanged: (old: Config, new: Config) -> Unit = { old, new ->
        Graph.engineIfCreated()?.refresh()
        if (Graph.isInitialized) PowerampBridge.onSettingsChanged(Graph.app, old, new)
    }

    val config: StateFlow<Config> = _config.asStateFlow()

    /** Atomic read-modify-write; the engine is told to rebuild the presence. */
    fun update(change: (Config) -> Config) {
        val (old, new) = synchronized(lock) {
            val old = _config.value
            val new = change(old)
            if (new == old) return
            prefs.edit().putString(KEY_CONFIG, new.toJson()).apply()
            _config.value = new
            old to new
        }
        // The update check's bookkeeping does not change what Discord shows
        if (new.forExport() != old.forExport()) onChanged(old, new)
    }

    /** Settings file for the Export button: {"app":"poweramp-discord-rp","version":1,"settings":{...}} */
    fun exportJson(): String {
        val root = buildJsonObject {
            put("app", Config.EXPORT_MARKER)
            put("version", EXPORT_VERSION)
            put("settings", Config.JSON.encodeToJsonElement(Config.serializer(), config.value.forExport()))
        }
        return Config.JSON.encodeToString(JsonObject.serializer(), root)
    }

    /** false = not a settings file of this app (nothing changed). */
    fun importJson(json: String): Boolean {
        val root = try {
            Config.JSON.parseToJsonElement(json) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return false
        if ((root["app"] as? JsonPrimitive)?.contentOrNull != Config.EXPORT_MARKER) return false
        val settings = root["settings"] as? JsonObject ?: return false
        // Unknown keys are ignored and missing ones get their defaults, so files of older / newer versions still load
        val imported = try {
            Config.JSON.decodeFromJsonElement(Config.serializer(), settings)
        } catch (_: Exception) {
            return false
        }
        update { old -> imported.keepingUpdateStateOf(old) }
        return true
    }

    fun resetToDefaults() = update { old -> Config().keepingUpdateStateOf(old) }

    private fun load(): Config = prefs.getString(KEY_CONFIG, null)?.let(Config::fromJson) ?: Config()

    // The update bookkeeping belongs to this installation, not to the settings (an import must not hide an update)
    private fun Config.keepingUpdateStateOf(old: Config) =
        copy(updateLastCheckMs = old.updateLastCheckMs, updateLatest = old.updateLatest)

    companion object {
        const val PREFS_NAME = "settings"
        const val KEY_CONFIG = "config"
        const val EXPORT_VERSION = 1
    }
}
