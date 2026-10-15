package io.github.rainbowfl4sh.powerampdiscordrp.ui

import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlin.coroutines.ContinuationInterceptor

/**
 * Robolectric runs the tests of a sandbox on one main thread and drops whatever is still queued there when a test ends.
 * Compose keeps one dispatcher per thread (AndroidUiDispatcher.CurrentThread, which also drives the frame clock of every
 * composition): if a test ended while that dispatcher waited for a frame callback, it keeps waiting for one that never
 * comes - no recomposition and no effects for every later test of the run (seen as a flaky PreviewTicksTest).
 */
object MainThreadCompose {
    /** Forgets what the main thread's Compose dispatcher still waits for from earlier tests. Call before composing. */
    fun reset() {
        val dispatcher = AndroidUiDispatcher.CurrentThread[ContinuationInterceptor] as? AndroidUiDispatcher ?: return
        val type = AndroidUiDispatcher::class.java
        // private state of Compose's dispatcher (androidx.compose.ui:ui); a field that is gone is skipped
        fun field(name: String) = try {
            type.getDeclaredField(name).apply { isAccessible = true }
        } catch (_: NoSuchFieldException) {
            null
        }
        val lock = field("lock")?.get(dispatcher) ?: return
        synchronized(lock) {
            for (name in listOf("toRunTrampolined", "toRunOnFrame", "spareToRunOnFrame")) {
                (field(name)?.get(dispatcher) as? MutableCollection<*>)?.clear()
            }
            for (name in listOf("scheduledTrampolineDispatch", "scheduledFrameDispatch")) field(name)?.setBoolean(dispatcher, false)
        }
    }
}
