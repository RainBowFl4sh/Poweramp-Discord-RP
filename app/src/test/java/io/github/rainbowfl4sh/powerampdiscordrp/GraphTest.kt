package io.github.rainbowfl4sh.powerampdiscordrp

import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The process start (App) prepares the settings and the engine in the background, the screen finds them ready. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GraphTest {
    @Test
    fun theAppWarmsUpTheEngineInTheBackground() {
        val deadline = System.currentTimeMillis() + 10_000
        while (Graph.engineIfCreated() == null && System.currentTimeMillis() < deadline) Thread.sleep(10)
        val engine = Graph.engineIfCreated()
        assertNotNull(engine)
        // created with the settings: it shows the resting status before the service ever ran
        assertTrue(engine!!.status.value.kind in setOf(EngineStatus.Kind.IDLE, EngineStatus.Kind.NO_APP_ID))
    }

    @Test
    fun theScreensAppInfoKnowsNothingBeforeItsFirstLookup() {
        // peek() does not create the lookup (and the HTTP client with it) - the screen calls it on the main thread
        assertNull(Graph.appInfoSource.peek("1559554394301800528"))
    }
}
