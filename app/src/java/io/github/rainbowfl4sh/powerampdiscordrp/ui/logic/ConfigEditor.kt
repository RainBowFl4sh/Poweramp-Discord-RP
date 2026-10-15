package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the editor reads and writes the settings (the app's SettingsStore, a fake in tests). */
interface ConfigStore {
    val config: StateFlow<Config>
    fun update(change: (Config) -> Config)
}

/**
 * The settings as the screens show them: the stored config plus edits that are not saved yet.
 *
 * Switches and choices are saved at once ([set]). Typing is saved [debounceMs] after the last keystroke ([edit]), so the
 * engine does not rebuild the presence for every character - the live preview follows [draft] right away.
 * Changes made elsewhere (import, reset, update check) show up in [draft] too; pending edits are applied on top.
 * Must be used from one thread (the main thread in the app).
 */
class ConfigEditor(
    private val store: ConfigStore,
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
) {
    private var pending: ((Config) -> Config)? = null
    private var saveJob: Job? = null
    private val _draft = MutableStateFlow(store.config.value)

    val draft: StateFlow<Config> = _draft.asStateFlow()

    /** True while typed changes wait for the debounce. */
    val hasPending: Boolean get() = pending != null

    init {
        scope.launch { store.config.collect { recompute() } }
    }

    /** Saves at once (switches, choices); pending typed edits stay pending. */
    fun set(change: (Config) -> Config) {
        store.update(change)
        recompute()
    }

    /** Visible in [draft] at once, saved after [debounceMs] without further edits. */
    fun edit(change: (Config) -> Config) {
        val before = pending
        pending = if (before == null) change else { c -> change(before(c)) }
        recompute()
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(debounceMs)
            saveJob = null
            flush()
        }
    }

    /** Saves pending edits now (the screen is left, the app goes to the background). */
    fun flush() {
        val change = pending ?: return
        pending = null
        saveJob?.cancel()
        saveJob = null
        store.update(change)
        recompute()
    }

    /** Drops pending edits (before an import / reset replaces all settings). */
    fun discard() {
        pending = null
        saveJob?.cancel()
        saveJob = null
        recompute()
    }

    private fun recompute() {
        val stored = store.config.value
        _draft.value = pending?.invoke(stored) ?: stored
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 400L
    }
}
