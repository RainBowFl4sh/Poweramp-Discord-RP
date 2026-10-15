package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigEditorTest {
    /** Like SettingsStore: every change is visible at once; counts the writes. */
    private class FakeStore(initial: Config = Config()) : ConfigStore {
        val flow = MutableStateFlow(initial)
        var writes = 0
        override val config: StateFlow<Config> = flow
        override fun update(change: (Config) -> Config) {
            val new = change(flow.value)
            if (new == flow.value) return
            writes++
            flow.value = new
        }
    }

    private fun TestScope.editor(store: FakeStore) = ConfigEditor(store, backgroundScope, debounceMs = 400).also { runCurrent() }

    @Test
    fun typingShowsAtOnceAndIsSavedAfterTheDebounce() = runTest {
        val store = FakeStore()
        val editor = editor(store)

        editor.edit { it.copy(details = "%t") }
        editor.edit { it.copy(details = "%ti") }
        editor.edit { it.copy(details = "%title% x") }
        assertEquals("%title% x", editor.draft.value.details)
        assertEquals("%title%", store.flow.value.details)   // not saved yet
        assertTrue(editor.hasPending)

        advanceTimeBy(399)
        runCurrent()
        assertEquals(0, store.writes)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, store.writes)   // one write for the whole burst
        assertEquals("%title% x", store.flow.value.details)
        assertFalse(editor.hasPending)
    }

    @Test
    fun everyKeystrokeRestartsTheDebounce() = runTest {
        val store = FakeStore()
        val editor = editor(store)
        editor.edit { it.copy(state = "a") }
        advanceTimeBy(300)
        editor.edit { it.copy(state = "ab") }
        advanceTimeBy(300)
        runCurrent()
        assertEquals(0, store.writes)
        advanceTimeBy(101)
        runCurrent()
        assertEquals("ab", store.flow.value.state)
        assertEquals(1, store.writes)
    }

    @Test
    fun setSavesAtOnceAndKeepsPendingEdits() = runTest {
        val store = FakeStore()
        val editor = editor(store)
        editor.edit { it.copy(details = "typed") }
        editor.set { it.copy(showSmallIcon = false) }
        assertFalse(store.flow.value.showSmallIcon)
        assertEquals("%title%", store.flow.value.details)
        assertEquals("typed", editor.draft.value.details)
        assertFalse(editor.draft.value.showSmallIcon)

        advanceTimeBy(500)
        runCurrent()
        assertEquals("typed", store.flow.value.details)
        assertFalse(store.flow.value.showSmallIcon)
    }

    @Test
    fun flushSavesAtOnce() = runTest {
        val store = FakeStore()
        val editor = editor(store)
        editor.edit { it.copy(excludePaths = "Podcasts") }
        editor.flush()
        assertEquals("Podcasts", store.flow.value.excludePaths)
        assertEquals(1, store.writes)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(1, store.writes)   // the debounce job was cancelled
        editor.flush()                   // nothing pending: no write
        assertEquals(1, store.writes)
    }

    @Test
    fun changesFromElsewhereShowUpWithPendingEditsOnTop() = runTest {
        val store = FakeStore()
        val editor = editor(store)
        editor.edit { it.copy(details = "mine") }
        store.flow.value = store.flow.value.copy(updateLatest = "1.2.0", details = "imported")
        runCurrent()
        assertEquals("1.2.0", editor.draft.value.updateLatest)
        assertEquals("mine", editor.draft.value.details)
    }

    @Test
    fun discardDropsPendingEdits() = runTest {
        val store = FakeStore()
        val editor = editor(store)
        editor.edit { it.copy(details = "mine") }
        editor.discard()
        assertEquals("%title%", editor.draft.value.details)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(0, store.writes)
        assertEquals("%title%", store.flow.value.details)
    }

    @Test
    fun editsAreAppliedToTheNewestStoredConfig() = runTest {
        val store = FakeStore()
        val editor = editor(store)
        editor.edit { it.copy(details = "mine") }
        // the update check stores its result while the user types
        store.update { it.copy(updateLastCheckMs = 42) }
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(42, store.flow.value.updateLastCheckMs)
        assertEquals("mine", store.flow.value.details)
    }
}
