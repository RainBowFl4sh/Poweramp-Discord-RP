package io.github.rainbowfl4sh.powerampdiscordrp.core.engine

import io.github.rainbowfl4sh.powerampdiscordrp.core.assets.AppInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.assets.AppInfoSource
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.CoverResult
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Probe
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus.Kind
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.ActivityBuilder
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.Hidden
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.StatusStrings
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.RpcTransport
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.TransportError
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class PresenceEngineTest {
    private val transport = FakeTransport()
    private val covers = FakeCovers()
    private val io = ManualDispatcher()
    private val logLines = mutableListOf<String>()
    private var config = Config()
    private var configReads = 0   // the actor reads the settings once per wakeup

    /** The engine's log helps a lot when a timing test fails. */
    @get:Rule
    val dumpLogOnFailure = object : TestWatcher() {
        override fun failed(e: Throwable?, description: Description?) {
            println("---- engine log of ${description?.methodName}")
            synchronized(logLines) { logLines.forEach(::println) }
        }
    }

    private fun TestScope.newEngine(
        defaultClientId: String = CLIENT_ID,
        idleGraceMs: Long = 60_000,
        appInfo: AppInfoSource = AppInfoSource.NONE,
    ) = PresenceEngine(
        transport = transport,
        covers = covers,
        config = {
            configReads++
            config
        },
        defaultClientId = defaultClientId,
        strings = { StatusStrings() },
        scope = backgroundScope,
        ioDispatcher = io,
        clock = FakeClock(testScheduler),
        log = Logger { synchronized(logLines) { logLines += it } },
        pid = PID,
        version = "1.0.0",
        minSendIntervalMs = 2_000,
        idleGraceMs = idleGraceMs,
        appInfo = appInfo,
    )

    private fun TestScope.now() = testScheduler.currentTime

    private fun TestScope.playing(track: TrackInfo = SONG, posMs: Long = now()) =
        PlaybackSnapshot(PlayState.PLAYING, track, posMs, now())

    private fun TestScope.paused(track: TrackInfo = SONG, posMs: Long = now()) =
        PlaybackSnapshot(PlayState.PAUSED, track, posMs, now())

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    /** Started with something playing and connected: the first presence went out at time 0. */
    private fun TestScope.connectedEngine(idleGraceMs: Long = 60_000): PresenceEngine {
        val engine = newEngine(idleGraceMs = idleGraceMs)
        engine.submit(playing())
        engine.start()
        runCurrent()
        transport.ready()
        runCurrent()
        assertEquals(1, transport.frames.size)
        return engine
    }

    private val PresenceEngine.st get() = status.value

    // ------------------------------------------------------------------------------------------- connection

    @Test
    fun `connects only once something is to be shown`() = runTest {
        val engine = newEngine()
        engine.start()
        runCurrent()
        assertTrue(transport.connects.isEmpty())
        assertEquals(Kind.IDLE, engine.st.kind)

        engine.submit(PlaybackSnapshot(PlayState.STOPPED, SONG))
        runCurrent()
        engine.submit(paused())   // default: paused presences are hidden
        runCurrent()
        assertTrue(transport.connects.isEmpty())
        assertEquals(Hidden.PAUSED, engine.st.hidden)

        engine.submit(playing())
        runCurrent()
        assertEquals(listOf(CLIENT_ID.toLong()), transport.connects)
        assertEquals(Kind.CONNECTING, engine.st.kind)
        assertEquals(CLIENT_ID, engine.st.clientId)
        assertEquals(Hidden.NO, engine.st.hidden)
    }

    @Test
    fun `first presence goes out right after READY`() = runTest {
        val engine = newEngine()
        engine.submit(playing())
        engine.start()
        runCurrent()
        assertTrue(transport.frames.isEmpty())

        advance(1_000)   // Discord takes a second to answer
        transport.ready("rainbow")
        runCurrent()
        assertEquals(1, transport.frames.size)
        assertEquals(PID, transport.pidOf(transport.frames[0]))
        val a = transport.lastActivity!!
        assertEquals("Song", a.text("details"))
        assertEquals("Artist", a.text("state"))
        assertEquals(ActivityBuilder.ASSET_LOGO, a.largeImage())
        // playing since time 0: the progress bar starts there
        assertEquals(FakeClock.WALL_BASE, a.getValue("timestamps").jsonObject.getValue("start").jsonPrimitive.long)

        val st = engine.st
        assertEquals(Kind.CONNECTED, st.kind)
        assertEquals(TransportError.NONE, st.error)
        assertEquals(1, st.connects)
        assertEquals("rainbow", st.user?.username)
        assertTrue(st.visible)
        assertEquals(FakeClock.WALL_BASE + 1_000, st.lastSentWallMs)
    }

    @Test
    fun `lost connection is retried with growing pauses`() = runTest {
        val engine = connectedEngine()
        advance(1_000)
        transport.close(TransportError.CLOSED, 1006, "gone")
        runCurrent()
        assertEquals(Kind.NOT_CONNECTED, engine.st.kind)
        assertEquals(TransportError.CLOSED, engine.st.error)
        assertEquals("gone", engine.st.errorDetail)
        assertFalse(engine.st.visible)

        advance(4_999)
        assertEquals(1, transport.connects.size)
        advance(1)
        assertEquals(2, transport.connects.size)

        for (pause in listOf(10_000L, 20_000L, 40_000L, 60_000L, 60_000L)) {
            transport.close(TransportError.CONNECT_FAILED)
            runCurrent()
            val before = transport.connects.size
            advance(pause - 1)
            assertEquals("retry before $pause ms", before, transport.connects.size)
            advance(1)
            assertEquals("retry after $pause ms", before + 1, transport.connects.size)
        }

        // a working connection starts over with the short pause
        transport.ready()
        runCurrent()
        assertEquals(2, engine.st.connects)
        transport.close(TransportError.CLOSED)
        runCurrent()
        val before = transport.connects.size
        advance(4_999)
        assertEquals(before, transport.connects.size)
        advance(1)
        assertEquals(before + 1, transport.connects.size)
    }

    @Test
    fun `no retries while nothing is to be shown`() = runTest {
        val engine = connectedEngine()
        advance(3_000)
        engine.submit(PlaybackSnapshot(PlayState.STOPPED, SONG))
        runCurrent()
        transport.close(TransportError.CLOSED)
        runCurrent()
        advance(10 * 60_000L)
        assertEquals(1, transport.connects.size)
        // something to show again: connected at once (the pause is long over)
        engine.submit(playing())
        runCurrent()
        assertEquals(2, transport.connects.size)
    }

    @Test
    fun `Discord not answering counts as a failed connection`() = runTest {
        // a transport without its own READY timeout: the engine's deadline is the safety net
        val engine = newEngine()
        engine.submit(playing())
        engine.start()
        runCurrent()
        advance(PresenceEngine.CONNECT_TIMEOUT_MS - 1)
        assertEquals(Kind.CONNECTING, engine.st.kind)
        advance(1)
        assertEquals(Kind.NOT_CONNECTED, engine.st.kind)
        assertEquals(TransportError.CONNECT_FAILED, engine.st.error)
        assertEquals(1, transport.disconnects)
        advance(5_000)
        assertEquals(2, transport.connects.size)
    }

    @Test
    fun `the transport's READY timeout is not cut short by the engine`() = runTest {
        assertTrue(PresenceEngine.CONNECT_TIMEOUT_MS > RpcTransport.READY_TIMEOUT_MS)
        val engine = newEngine()
        engine.submit(playing())
        engine.start()
        runCurrent()
        // a slow Discord (cold start) still gets the transport's full budget
        advance(RpcTransport.READY_TIMEOUT_MS - 1)
        assertEquals(Kind.CONNECTING, engine.st.kind)
        assertEquals(0, transport.disconnects)

        // the transport gives up and says why; the engine retries after the usual pause
        advance(1)
        transport.close(TransportError.CONNECT_FAILED, -1, "Discord did not answer within 30 s")
        runCurrent()
        assertEquals(Kind.NOT_CONNECTED, engine.st.kind)
        assertEquals(TransportError.CONNECT_FAILED, engine.st.error)
        assertEquals("Discord did not answer within 30 s", engine.st.errorDetail)
        advance(4_999)
        assertEquals(1, transport.connects.size)
        advance(1)
        assertEquals(2, transport.connects.size)

        // Discord answers within the budget of the next attempt: no deadline of the first one interferes
        advance(RpcTransport.READY_TIMEOUT_MS - 1_000)
        transport.ready()
        runCurrent()
        assertEquals(Kind.CONNECTED, engine.st.kind)
        assertEquals(1, transport.frames.size)
        advance(60_000)
        assertEquals(Kind.CONNECTED, engine.st.kind)
        assertEquals(2, transport.connects.size)
    }

    @Test
    fun `rejected application ID is not retried until reconnect`() = runTest {
        val engine = newEngine()
        engine.submit(playing())
        engine.start()
        runCurrent()
        transport.close(TransportError.CLOSED, 4000, "Invalid Client ID")
        runCurrent()
        assertEquals(Kind.NOT_CONNECTED, engine.st.kind)
        assertEquals(TransportError.INVALID_CLIENT_ID, engine.st.error)
        assertEquals("Invalid Client ID", engine.st.errorDetail)

        advance(10 * 60_000L)
        assertEquals(1, transport.connects.size)
        assertTrue("nothing can be shown with this ID", engine.idle.value)

        engine.reconnect()
        runCurrent()
        assertEquals(2, transport.connects.size)
        assertEquals(Kind.CONNECTING, engine.st.kind)
        assertEquals(TransportError.NONE, engine.st.error)
        assertFalse(engine.idle.value)
    }

    @Test
    fun `rejected application ID survives a restart and other settings, a new ID is tried`() = runTest {
        val engine = newEngine()
        engine.submit(playing())
        engine.start()
        runCurrent()
        transport.close(TransportError.INVALID_CLIENT_ID, 4000, "Invalid Client ID")
        runCurrent()
        engine.stop()
        runCurrent()
        assertRejected(engine)

        engine.start()   // the service is started again by the next Poweramp broadcast
        runCurrent()
        assertRejected(engine)   // not "not connected - retrying": nothing is retried
        advance(60_000)
        assertEquals(1, transport.connects.size)
        assertRejected(engine)
        assertTrue(engine.idle.value)

        // started while nothing plays: still the rejection, not IDLE
        engine.stop()
        runCurrent()
        engine.submit(PlaybackSnapshot(PlayState.STOPPED, SONG))
        engine.start()
        runCurrent()
        assertRejected(engine)
        advance(60_000)
        assertRejected(engine)
        engine.stop()
        runCurrent()
        assertRejected(engine)

        engine.submit(playing())
        engine.start()
        runCurrent()
        // another setting does not make the same ID work (same rule as the app's RejectedAppId for service starts)
        config = config.copy(details = "%artist% - %title%")
        engine.refresh()
        runCurrent()
        advance(60_000)
        assertEquals(1, transport.connects.size)
        assertRejected(engine)

        // a different application ID is tried at once
        config = config.copy(useCustomApp = true, customClientId = OWN_ID)
        engine.refresh()
        runCurrent()
        assertEquals(2, transport.connects.size)
        assertEquals(Kind.CONNECTING, engine.st.kind)
        assertEquals(TransportError.NONE, engine.st.error)
    }

    @Test
    fun `status shows a settings problem before the engine ever started`() = runTest {
        config = Config(enabled = false)
        assertEquals(Kind.DISABLED, newEngine().st.kind)
        config = Config()
        assertEquals(Kind.NO_APP_ID, newEngine(defaultClientId = "").st.kind)
        assertEquals(Kind.IDLE, newEngine().st.kind)
    }

    @Test
    fun `test presence tries a rejected application ID again`() = runTest {
        val engine = newEngine()
        engine.submit(playing())
        engine.start()
        runCurrent()
        transport.close(TransportError.INVALID_CLIENT_ID, 4000, "Invalid Client ID")
        runCurrent()
        engine.stop()
        runCurrent()
        engine.submit(PlaybackSnapshot(PlayState.STOPPED, SONG))
        engine.start()
        runCurrent()
        assertRejected(engine)

        engine.sendTest()
        runCurrent()
        assertEquals(2, transport.connects.size)
        assertEquals(Kind.CONNECTING, engine.st.kind)
        assertEquals(TransportError.NONE, engine.st.error)
        assertTrue(engine.st.testing)
        transport.ready()
        runCurrent()
        assertTrue(transport.lastActivity!!.text("details")!!.startsWith("Test - "))

        // rejected again: reported as such, also after the test
        transport.close(TransportError.INVALID_CLIENT_ID, 4000, "Invalid Client ID")
        runCurrent()
        assertRejected(engine)
        advance(20_000)
        assertRejected(engine)
        assertEquals(2, transport.connects.size)
    }

    @Test
    fun `test presence while resting forgets a rejected application ID`() = runTest {
        val engine = newEngine()
        engine.submit(playing())
        engine.start()
        runCurrent()
        transport.close(TransportError.INVALID_CLIENT_ID, 4000, "Invalid Client ID")
        runCurrent()
        engine.stop()
        runCurrent()
        assertRejected(engine)

        engine.sendTest()   // the app starts the service right after
        assertEquals(Kind.IDLE, engine.st.kind)
        engine.start()
        runCurrent()
        assertEquals(2, transport.connects.size)
        assertTrue(engine.st.testing)
    }

    @Test
    fun `a test connects at once, also during a retry pause`() = runTest {
        val engine = connectedEngine()
        transport.close(TransportError.CLOSED, 1006, "gone")
        runCurrent()
        assertEquals(1, transport.connects.size)
        advance(1_000)
        engine.sendTest()
        runCurrent()
        assertEquals(2, transport.connects.size)
    }

    @Test
    fun `changing away from a rejected application ID and back tries it again`() = runTest {
        val engine = newEngine()
        engine.submit(playing())
        engine.start()
        runCurrent()
        transport.close(TransportError.INVALID_CLIENT_ID, 4000, "Invalid Client ID")
        runCurrent()
        assertRejected(engine)

        // while running: own ID, then back to the built-in one -> the built-in ID is asked again
        config = Config(useCustomApp = true, customClientId = OWN_ID)
        engine.refresh()
        runCurrent()
        config = Config()
        engine.refresh()
        runCurrent()
        assertEquals(CLIENT_ID.toLong(), transport.connects.last())
        assertEquals(Kind.CONNECTING, engine.st.kind)

        // while resting: rejected again, service stopped, ID changed away and back -> next start asks Discord
        transport.close(TransportError.INVALID_CLIENT_ID, 4000, "Invalid Client ID")
        runCurrent()
        engine.stop()
        runCurrent()
        val before = transport.connects.size
        config = Config(useCustomApp = true, customClientId = OWN_ID)
        engine.refresh()
        config = Config()
        engine.refresh()
        assertEquals(Kind.IDLE, engine.st.kind)   // no longer shown as rejected
        engine.start()
        runCurrent()
        assertEquals(before + 1, transport.connects.size)
        assertEquals(CLIENT_ID.toLong(), transport.connects.last())
    }

    @Test
    fun `art assets go out as picture URLs once the application is looked up`() = runTest {
        val pictures = mapOf(
            ActivityBuilder.ASSET_LOGO to "https://cdn.test/app-assets/$CLIENT_ID/10.png",
            ActivityBuilder.ASSET_PLAY to "https://cdn.test/app-assets/$CLIENT_ID/11.png",
            ActivityBuilder.ASSET_PAUSE to "https://cdn.test/app-assets/$CLIENT_ID/12.png",
        )
        val lookups = mutableListOf<String>()
        var known: AppInfo? = null
        val source = object : AppInfoSource {
            override fun peek(appId: String) = known
            override fun fetch(appId: String): AppInfo? {
                lookups += appId
                known = AppInfo("Poweramp", pictures)
                return known
            }
        }
        val engine = newEngine(appInfo = source)
        engine.submit(playing())
        engine.start()
        runCurrent()
        // Discord answers READY before the lookup (started with the connection) finished: keys at first
        transport.ready()
        runCurrent()
        assertEquals(ActivityBuilder.ASSET_PLAY, transport.lastActivity!!.smallImage())

        io.runAll()
        runCurrent()
        advance(2_000)   // Discord's rate limit
        val a = transport.lastActivity!!
        assertEquals("https://cdn.test/app-assets/$CLIENT_ID/11.png", a.smallImage())
        assertEquals("https://cdn.test/app-assets/$CLIENT_ID/10.png", a.largeImage())   // the logo, no cover found
        assertEquals(listOf(CLIENT_ID), lookups)

        // the logo's URL is the application's own picture: not checked / repaired like a cover
        advance(30_000)
        assertTrue(covers.checked.isEmpty())

        // paused: the pause picture
        engine.submit(paused())
        config = config.copy(pausedBehavior = Config.PAUSED_SHOW)
        engine.refresh()
        advance(2_000)
        assertEquals("https://cdn.test/app-assets/$CLIENT_ID/12.png", transport.lastActivity!!.smallImage())
    }

    @Test
    fun `the lookup is made with every connection, the pictures already known are used at once`() = runTest {
        var fetches = 0
        val info = AppInfo("Poweramp", mapOf(ActivityBuilder.ASSET_PLAY to "https://cdn.test/11.png"))
        val source = object : AppInfoSource {
            override fun peek(appId: String) = info
            override fun fetch(appId: String): AppInfo {
                fetches++
                return info
            }
        }
        val engine = newEngine(appInfo = source)
        engine.submit(playing())
        engine.start()
        runCurrent()
        transport.ready()
        runCurrent()
        assertEquals("https://cdn.test/11.png", transport.lastActivity!!.smallImage())   // cached: the first update has it
        io.runAll()
        runCurrent()
        assertEquals(1, fetches)
        val frames = transport.frames.size
        advance(5_000)
        assertEquals(frames, transport.frames.size)   // nothing new learned: nothing sent again

        transport.close(TransportError.CLOSED, 1006, "gone")
        advance(5_000)   // reconnect
        io.runAll()
        runCurrent()
        assertEquals(2, fetches)
    }

    private fun assertRejected(engine: PresenceEngine) {
        assertEquals(Kind.NOT_CONNECTED, engine.st.kind)
        assertEquals(TransportError.INVALID_CLIENT_ID, engine.st.error)
        assertEquals("Invalid Client ID", engine.st.errorDetail)
        assertFalse(engine.st.visible)
    }

    @Test
    fun `changed application ID reconnects`() = runTest {
        val engine = connectedEngine()
        advance(3_000)
        config = Config(useCustomApp = true, customClientId = OWN_ID)
        engine.refresh()
        runCurrent()
        assertNull("cleared before switching", transport.lastActivity)
        assertEquals(1, transport.disconnects)
        assertEquals(listOf(CLIENT_ID.toLong(), OWN_ID.toLong()), transport.connects)
        assertEquals(OWN_ID, engine.st.clientId)

        transport.ready()
        runCurrent()
        assertEquals(3, transport.frames.size)
        assertEquals("Song", transport.lastActivity!!.text("details"))

        engine.refresh()   // same ID: the connection stays
        advance(5_000)
        assertEquals(2, transport.connects.size)
    }

    @Test
    fun `no application ID - NO_APP_ID, no connection, idle`() = runTest {
        val engine = newEngine(defaultClientId = "")
        engine.submit(playing())
        engine.start()
        runCurrent()
        assertEquals(Kind.NO_APP_ID, engine.st.kind)
        assertEquals("", engine.st.clientId)
        assertTrue(transport.connects.isEmpty())
        advance(59_999)
        assertFalse(engine.idle.value)
        advance(1)
        assertTrue(engine.idle.value)

        config = Config(useCustomApp = true, customClientId = OWN_ID)
        engine.refresh()
        runCurrent()
        assertEquals(listOf(OWN_ID.toLong()), transport.connects)
        assertEquals(Kind.CONNECTING, engine.st.kind)
        assertFalse(engine.idle.value)
    }

    @Test
    fun `disabled - presence cleared, disconnected, DISABLED`() = runTest {
        val engine = connectedEngine()
        advance(3_000)
        config = config.copy(enabled = false)
        engine.refresh()
        runCurrent()
        assertEquals(2, transport.frames.size)
        assertNull(transport.lastActivity)
        assertEquals(1, transport.disconnects)
        assertEquals(Kind.DISABLED, engine.st.kind)
        assertEquals(Hidden.DISABLED, engine.texts.value.hidden)
        assertFalse(engine.st.visible)

        engine.submit(playing())
        advance(10_000)
        assertEquals(1, transport.connects.size)

        config = config.copy(enabled = true)
        engine.refresh()
        runCurrent()
        assertEquals(2, transport.connects.size)
    }

    @Test
    fun `status follows the settings while the engine rests`() = runTest {
        val engine = newEngine()
        config = Config(enabled = false)
        engine.refresh()
        assertEquals(Kind.DISABLED, engine.st.kind)
        assertEquals(Hidden.DISABLED, engine.texts.value.hidden)
        config = Config()
        engine.refresh()
        assertEquals(Kind.IDLE, engine.st.kind)
        assertEquals(CLIENT_ID, engine.st.clientId)

        val noId = newEngine(defaultClientId = "")
        noId.refresh()
        assertEquals(Kind.NO_APP_ID, noId.st.kind)
    }

    // ------------------------------------------------------------------------------------------- sending

    @Test
    fun `updates are coalesced - the latest wins`() = runTest {
        val engine = connectedEngine()
        advance(500)
        engine.submit(playing(OTHER))
        runCurrent()
        advance(500)
        engine.submit(playing(THIRD))
        runCurrent()
        assertEquals(1, transport.frames.size)
        advance(999)
        assertEquals(1, transport.frames.size)
        advance(1)
        assertEquals(2, transport.frames.size)
        assertEquals("Third", transport.lastActivity!!.text("details"))
        advance(10_000)
        assertEquals(2, transport.frames.size)
    }

    @Test
    fun `same presence is not sent again - small timestamp drift ignored`() = runTest {
        val engine = connectedEngine()   // playing from position 0 at time 0
        advance(3_000)
        engine.submit(PlaybackSnapshot(PlayState.PLAYING, SONG, positionMs = 3_800, stampMs = now()))   // 0.8 s drift
        runCurrent()
        advance(3_000)
        engine.submit(PlaybackSnapshot(PlayState.PLAYING, SONG, positionMs = 4_500, stampMs = now()))   // 1.5 s drift
        runCurrent()
        assertEquals(1, transport.frames.size)

        engine.submit(PlaybackSnapshot(PlayState.PLAYING, SONG, positionMs = 60_000, stampMs = now()))   // seek
        runCurrent()
        assertEquals(2, transport.frames.size)
    }

    @Test
    fun `paused with hiding - cleared once, connection kept`() = runTest {
        val engine = connectedEngine()
        advance(3_000)
        engine.submit(paused())
        runCurrent()
        assertEquals(2, transport.frames.size)
        assertNull(transport.lastActivity)
        assertEquals(0, transport.disconnects)
        assertEquals(Kind.CONNECTED, engine.st.kind)
        assertEquals(Hidden.PAUSED, engine.st.hidden)
        assertFalse(engine.st.visible)

        advance(3_000)
        engine.submit(paused(posMs = 3_000))
        runCurrent()
        assertEquals("cleared only once", 2, transport.frames.size)

        advance(3_000)
        engine.submit(playing(posMs = 3_000))
        runCurrent()
        assertEquals(3, transport.frames.size)
        assertEquals("Song", transport.lastActivity!!.text("details"))
        assertTrue(engine.st.visible)
        assertEquals(1, transport.connects.size)
    }

    @Test
    fun `paused presence is hidden after clearAfterPausedMin`() = runTest {
        config = Config(pausedBehavior = Config.PAUSED_SHOW, clearAfterPausedMin = 1)
        val engine = connectedEngine()
        advance(3_000)
        engine.submit(paused())
        runCurrent()
        assertEquals(2, transport.frames.size)
        val pausedActivity = transport.lastActivity!!
        assertEquals(ActivityBuilder.ASSET_PAUSE, pausedActivity.getValue("assets").jsonObject.text("small_image"))
        assertFalse("timestamps" in pausedActivity)

        advance(59_999)
        assertEquals(2, transport.frames.size)
        advance(1)
        assertEquals(3, transport.frames.size)
        assertNull(transport.lastActivity)
        assertEquals(Hidden.PAUSED_TIMEOUT, engine.texts.value.hidden)
        assertEquals(Hidden.PAUSED_TIMEOUT, engine.st.hidden)
    }

    @Test
    fun `paused timeout counts from when Poweramp paused`() = runTest {
        config = Config(pausedBehavior = Config.PAUSED_SHOW, clearAfterPausedMin = 1)
        val engine = newEngine()
        advance(10 * 60_000L)
        // cold start: the sticky broadcast tells of a pause 5 minutes ago
        engine.submit(PlaybackSnapshot(PlayState.PAUSED, SONG, 30_000, stampMs = now() - 5 * 60_000L))
        assertEquals(Hidden.PAUSED_TIMEOUT, engine.texts.value.hidden)
        engine.start()
        runCurrent()
        assertTrue("nothing to show: no connection", transport.connects.isEmpty())
        engine.stop()
        runCurrent()

        // paused 40 s ago: hidden 20 s from now, not a full minute
        engine.submit(playing())
        engine.submit(PlaybackSnapshot(PlayState.PAUSED, SONG, 30_000, stampMs = now() - 40_000))
        assertEquals(Hidden.NO, engine.texts.value.hidden)
        engine.start()
        runCurrent()
        transport.ready()
        runCurrent()
        assertEquals(1, transport.frames.size)
        advance(19_999)
        assertEquals(1, transport.frames.size)
        advance(1)
        assertEquals(2, transport.frames.size)
        assertNull(transport.lastActivity)
        assertEquals(Hidden.PAUSED_TIMEOUT, engine.st.hidden)

        // a seek while paused is stamped anew: shown again for the full time (as the app decides it, too)
        advance(3_000)
        engine.submit(PlaybackSnapshot(PlayState.PAUSED, SONG, 90_000, stampMs = now()))
        runCurrent()
        assertEquals(Hidden.NO, engine.texts.value.hidden)
        assertEquals(3, transport.frames.size)
        advance(59_999)
        assertEquals(3, transport.frames.size)
        advance(1)
        assertEquals(4, transport.frames.size)
        assertNull(transport.lastActivity)
    }

    @Test
    fun `periodic refresh while a template shows the position`() = runTest {
        config = Config(state = "%pos% / %dur%", refreshSeconds = 15, pausedBehavior = Config.PAUSED_SHOW)
        val engine = connectedEngine()
        assertEquals("0:00 / 3:20", transport.lastActivity!!.text("state"))
        advance(14_999)
        assertEquals(1, transport.frames.size)
        advance(1)
        assertEquals(2, transport.frames.size)
        assertEquals("0:15 / 3:20", transport.lastActivity!!.text("state"))
        advance(15_000)
        assertEquals("0:30 / 3:20", transport.lastActivity!!.text("state"))

        advance(1_000)
        engine.submit(paused())
        advance(1_000)
        assertEquals(4, transport.frames.size)
        assertEquals("0:31 / 3:20", transport.lastActivity!!.text("state"))
        advance(60_000)
        assertEquals("no refresh while paused", 4, transport.frames.size)
    }

    // ------------------------------------------------------------------------------------------- text rotation

    private fun details(): List<String?> = transport.activities.map { it?.text("details") }

    @Test
    fun `text rotation sends the next alternatives at every step`() = runTest {
        config = Config(details = "%title% || %artist%", state = "%album% || %title%", rotationSeconds = 5)
        val engine = connectedEngine()   // the track is current since time 0
        assertEquals(0, engine.snapshot.value.trackSinceMs)
        assertEquals("Song", transport.lastActivity!!.text("details"))
        assertEquals("Album", transport.lastActivity!!.text("state"))

        advance(4_999)
        assertEquals(1, transport.frames.size)
        advance(1)
        assertEquals(2, transport.frames.size)
        assertEquals("Artist", transport.lastActivity!!.text("details"))
        assertEquals("Song", transport.lastActivity!!.text("state"))
        assertEquals("the status page / preview see the same", "Artist", engine.texts.value.details)
        val reads = configReads
        advance(5_000)
        assertEquals("one wakeup per step", reads + 1, configReads)
        assertEquals(3, transport.frames.size)
        assertEquals("Song", transport.lastActivity!!.text("details"))
        assertEquals("Album", transport.lastActivity!!.text("state"))
        advance(50_000)
        assertEquals("one update per step", 13, transport.frames.size)
    }

    @Test
    fun `a new track starts the rotation with its first alternative`() = runTest {
        config = Config(details = "%title% || %artist%", rotationSeconds = 5)
        val engine = connectedEngine()
        advance(7_000)
        assertEquals(listOf("Song", "Artist"), details())

        // the anchor is the engine's, whatever the snapshot brings along
        engine.submit(playing(THIRD).copy(trackSinceMs = 123))
        runCurrent()
        assertEquals(7_000, engine.snapshot.value.trackSinceMs)
        assertEquals(listOf("Song", "Artist", "Third"), details())
        advance(4_999)   // not at 10 s: the new track's steps count from 7 s
        assertEquals(3, transport.frames.size)
        advance(1)
        assertEquals(listOf("Song", "Artist", "Third", "Band"), details())
        advance(5_000)
        assertEquals("Third", transport.lastActivity!!.text("details"))
    }

    @Test
    fun `pause, resume, seek and a rating change keep the rotation going`() = runTest {
        config = Config(details = "%title% || %artist%", rotationSeconds = 5, pausedBehavior = Config.PAUSED_SHOW)
        val engine = connectedEngine()
        advance(3_000)
        engine.submit(paused(posMs = 3_000))
        runCurrent()
        assertEquals(0, engine.snapshot.value.trackSinceMs)
        advance(2_000)   // 5 s: the paused presence rotates on
        assertEquals(PlayState.PAUSED, engine.snapshot.value.state)
        assertEquals("Artist", transport.lastActivity!!.text("details"))
        assertEquals(ActivityBuilder.ASSET_PAUSE, transport.lastActivity!!.smallImage())

        advance(2_000)
        engine.submit(playing(posMs = 3_000))   // resumed at 7 s
        runCurrent()
        assertEquals(0, engine.snapshot.value.trackSinceMs)
        advance(5_000)
        engine.submit(playing(posMs = 100_000))   // seek at 12 s
        runCurrent()
        assertEquals(0, engine.snapshot.value.trackSinceMs)
        advance(5_000)
        engine.submit(playing(SONG.copy(rating = 5), posMs = 105_000))   // rated at 17 s: the same presence
        runCurrent()
        assertEquals(0, engine.snapshot.value.trackSinceMs)
        advance(3_000)   // 20 s
        // 0 play, 3 pause, 5 step, 7 resume, 10 step, 12 seek, 15 step, 20 step
        assertEquals(listOf("Song", "Song", "Artist", "Artist", "Song", "Song", "Artist", "Song"), details())
    }

    @Test
    fun `playing again after a stop starts the rotation anew`() = runTest {
        config = Config(details = "%title% || %artist%", rotationSeconds = 5)
        val engine = connectedEngine()
        advance(7_000)
        engine.submit(PlaybackSnapshot(PlayState.STOPPED, SONG, 0, now()))
        runCurrent()
        assertNull(transport.lastActivity)
        advance(2_000)
        engine.submit(playing(posMs = 0))   // the same track again at 9 s
        runCurrent()
        assertEquals(9_000, engine.snapshot.value.trackSinceMs)
        assertEquals("Song", transport.lastActivity!!.text("details"))
        advance(4_999)
        assertEquals(4, transport.frames.size)
        advance(1)
        assertEquals("Artist", transport.lastActivity!!.text("details"))
    }

    @Test
    fun `a rotation step waits for the minimum send interval`() = runTest {
        config = Config(details = "%title% || %artist%", rotationSeconds = 5)
        val engine = connectedEngine()
        advance(4_000)
        engine.submit(playing(posMs = 60_000))   // seek at 4 s: sent at once
        runCurrent()
        assertEquals(2, transport.frames.size)
        assertEquals("Song", transport.lastActivity!!.text("details"))
        advance(1_999)   // the step at 5 s waits for the rate limit
        assertEquals(2, transport.frames.size)
        advance(1)
        assertEquals(3, transport.frames.size)
        assertEquals("Artist", transport.lastActivity!!.text("details"))
        advance(3_999)   // the next step is still at 10 s
        assertEquals(3, transport.frames.size)
        advance(1)
        assertEquals(4, transport.frames.size)
        assertEquals("Song", transport.lastActivity!!.text("details"))
        assertEquals(0, engine.snapshot.value.trackSinceMs)
    }

    @Test
    fun `the position refresh and the rotation run side by side`() = runTest {
        config = Config(details = "%title% || %artist%", state = "%pos%", refreshSeconds = 15, rotationSeconds = 20)
        connectedEngine()
        advance(60_000)
        val sent = transport.activities.map { it!!.text("details") + " " + it.text("state") }
        assertEquals(listOf("Song 0:00", "Song 0:15", "Artist 0:20", "Artist 0:35", "Song 0:40", "Song 0:55", "Artist 1:00"), sent)
    }

    @Test
    fun `a position refresh shortly before a rotation step is left to the step`() = runTest {
        config = Config(details = "%title% || %artist%", state = "%pos%", refreshSeconds = 5, rotationSeconds = 6)
        transport.now = { now() }
        connectedEngine()
        advance(6_000)
        val reads = configReads
        advance(5_999)
        assertEquals("no wakeup for a refresh left to the step", reads, configReads)
        advance(18_001)
        // not 5 (refresh) and 7 (the step at 6 held back by the 2 s limit): every step refreshes the position anyway
        assertEquals(listOf(0L, 6_000, 12_000, 18_000, 24_000, 30_000), transport.frameTimes)
        assertEquals("0:30", transport.lastActivity!!.text("state"))
    }

    @Test
    fun `a position refresh that leaves 5 s to the next rotation step is sent`() = runTest {
        config = Config(details = "%title% || %artist%", state = "%pos%", refreshSeconds = 5, rotationSeconds = 10)
        transport.now = { now() }
        connectedEngine()
        advance(30_000)
        assertEquals(listOf(0L, 5_000, 10_000, 15_000, 20_000, 25_000, 30_000), transport.frameTimes)
        assertEquals(listOf("Song", "Song", "Artist", "Artist", "Song", "Song", "Artist"), details())
    }

    @Test
    fun `position refresh and rotation together stay within Discord's 5 updates per 20 seconds`() {
        for (refresh in listOf(5, 10, 15, 30)) {
            for (rotation in (5..20) + listOf(25, 30, 45, 60)) PresenceEngineTest().checkUpdateRate(refresh, rotation)
        }
    }

    /** Sends of 3 minutes with the position refreshed every [refresh] s and the first line rotating every [rotation] s. */
    private fun checkUpdateRate(refresh: Int, rotation: Int) = runTest {
        config = Config(details = "%title% || %artist%", state = "%pos%", refreshSeconds = refresh, rotationSeconds = rotation)
        transport.now = { now() }
        connectedEngine()
        advance(180_000)
        val times = transport.frameTimes
        val label = "refresh $refresh s, rotation $rotation s: $times"
        for (t in times) {
            assertTrue("at most 5 updates in 20 s - $label", times.count { it in t..t + 20_000 } <= 5)
        }
        val gaps = times.zipWithNext { a, b -> b - a }
        assertTrue("updates at least 5 s apart - $label", gaps.all { it >= Config.ROTATION_MIN_SECONDS * 1000L })
        assertTrue("every rotation step is sent - $label", gaps.all { it <= rotation * 1000L })
        assertTrue("the position is not left stale - $label", gaps.all { it <= (refresh + Config.ROTATION_MIN_SECONDS) * 1000L })
    }

    @Test
    fun `a new rotation interval applies at once`() = runTest {
        config = Config(details = "%title% || %artist%", rotationSeconds = 5)
        val engine = connectedEngine()
        advance(8_000)
        assertEquals("Artist", transport.lastActivity!!.text("details"))
        config = config.copy(rotationSeconds = 20)
        engine.refresh()
        runCurrent()
        assertEquals("8 s is in the first step of 20 s", "Song", transport.lastActivity!!.text("details"))
        advance(11_999)
        assertEquals(3, transport.frames.size)
        advance(1)
        assertEquals(listOf("Song", "Artist", "Song", "Artist"), details())
    }

    @Test
    fun `no wakeups when no text rotates`() = runTest {
        val engine = connectedEngine()   // default texts: no rotation, no position
        advance(1_000)
        var reads = configReads
        advance(120_000)
        assertEquals("the engine slept", reads, configReads)
        assertEquals(1, transport.frames.size)

        // one alternative that is not blank, a single '|': no rotation either
        config = Config(details = "%title% ||", state = "%artist% | %album%", smallText = "%status% || %codec%", showSmallIcon = false)
        engine.refresh()
        advance(1_000)
        assertEquals("Artist | Album", transport.lastActivity!!.text("state"))
        reads = configReads
        advance(120_000)
        assertEquals("the engine slept", reads, configReads)
    }

    @Test
    fun `no rotation while the presence is hidden - it goes on once shown again`() = runTest {
        config = Config(details = "%title% || %artist%", rotationSeconds = 5)   // paused presences are hidden
        val engine = connectedEngine(idleGraceMs = 600_000)
        advance(3_000)
        engine.submit(paused(posMs = 3_000))
        runCurrent()
        assertNull(transport.lastActivity)
        val reads = configReads
        advance(120_000)
        assertEquals("no wakeups while hidden", reads, configReads)
        assertEquals(2, transport.frames.size)

        // resumed at 123 s: the same track, so the alternative of that time (step 24), then on at the next step
        engine.submit(playing(posMs = 3_000))
        runCurrent()
        assertEquals(0, engine.snapshot.value.trackSinceMs)
        assertEquals("Song", transport.lastActivity!!.text("details"))
        advance(1_999)
        assertEquals(3, transport.frames.size)
        advance(1)
        assertEquals(4, transport.frames.size)
        assertEquals("Artist", transport.lastActivity!!.text("details"))
    }

    @Test
    fun `no rotation wakeups while not connected`() = runTest {
        config = Config(details = "%title% || %artist%", rotationSeconds = 5)
        connectedEngine()
        advance(3_000)
        transport.close(TransportError.CLOSED)
        runCurrent()
        val connects = transport.connects.size
        advance(5_000)   // the reconnect after 5 s
        assertEquals(connects + 1, transport.connects.size)
        val reads = configReads
        advance(PresenceEngine.CONNECT_TIMEOUT_MS - 1)   // Discord does not answer: only the connect deadline wakes it
        assertEquals("no wakeups at the rotation steps", reads, configReads)
        assertEquals(1, transport.frames.size)
    }

    @Test
    fun `a failed send is repeated, a dead connection reconnects`() = runTest {
        val engine = connectedEngine()
        transport.failSends = true
        advance(3_000)
        engine.submit(playing(OTHER))
        runCurrent()
        assertEquals(1, transport.frames.size)
        transport.failSends = false
        advance(1_999)
        assertEquals(1, transport.frames.size)
        advance(1)
        assertEquals(2, transport.frames.size)
        assertEquals("Other", transport.lastActivity!!.text("details"))

        transport.dropSilently()
        advance(3_000)
        engine.submit(playing(THIRD))
        runCurrent()
        assertEquals(Kind.NOT_CONNECTED, engine.st.kind)
        assertEquals(TransportError.CLOSED, engine.st.error)
        advance(5_000)
        assertEquals(2, transport.connects.size)
    }

    @Test
    fun `Discord's error message is shown until an update is accepted`() = runTest {
        val engine = connectedEngine()
        assertTrue(engine.st.visible)
        val nonce = transport.nonceOf(transport.frames[0])
        transport.answer("""{"cmd":"SET_ACTIVITY","evt":"ERROR","data":{"code":4002,"message":"child \"activity\" fails"},"nonce":"$nonce"}""")
        runCurrent()
        assertEquals("child \"activity\" fails", engine.st.activityError)
        assertEquals("", engine.st.errorDetail)   // the connection itself is fine
        assertEquals(Kind.CONNECTED, engine.st.kind)
        assertFalse("Discord does not show the rejected presence", engine.st.visible)

        // the rejected presence is sent again with the next update even though it looks the same
        advance(3_000)
        engine.submit(playing(posMs = 3_000))
        runCurrent()
        assertEquals(2, transport.frames.size)
        assertFalse("not before Discord took it", engine.st.visible)
        transport.answer("""{"cmd":"SET_ACTIVITY","data":{"assets":{"large_image":"poweramp"}},"nonce":"${transport.nonceOf(transport.frames[1])}"}""")
        runCurrent()
        assertEquals("", engine.st.activityError)
        assertTrue(engine.st.visible)
    }

    @Test
    fun `only the answer to the latest update decides about the error`() = runTest {
        val engine = connectedEngine()
        advance(3_000)
        engine.submit(playing(OTHER))
        runCurrent()
        assertEquals(2, transport.frames.size)

        // the first update is rejected, the latest one may still be taken
        transport.reject(transport.frames[0], "too old")
        runCurrent()
        assertEquals("", engine.st.activityError)
        assertTrue(engine.st.visible)

        transport.reject(transport.frames[1], "bad details_url")
        runCurrent()
        assertEquals("bad details_url", engine.st.activityError)
        assertFalse(engine.st.visible)
        // a late success of an older update does not end it
        transport.accept(transport.frames[0])
        runCurrent()
        assertEquals("bad details_url", engine.st.activityError)

        // hidden before the outdated presence was cleared: that clear is the latest update, Discord takes it
        advance(1_000)
        engine.submit(PlaybackSnapshot(PlayState.STOPPED, OTHER))
        advance(1_000)
        assertEquals(3, transport.frames.size)
        assertNull(transport.lastActivity)
        transport.accept(transport.frames[2])
        runCurrent()
        assertEquals("", engine.st.activityError)
        assertFalse(engine.st.visible)

        // a new connection starts without the old error
        advance(3_000)
        engine.submit(playing(OTHER))
        runCurrent()
        transport.reject(transport.frames[3], "bad details_url")
        runCurrent()
        assertEquals("bad details_url", engine.st.activityError)
        transport.close(TransportError.CLOSED, 1006, "gone")
        runCurrent()
        assertEquals("", engine.st.activityError)
    }

    @Test
    fun `a rejected update clears the outdated presence Discord still shows`() = runTest {
        val engine = connectedEngine()
        transport.accept(transport.frames[0])   // Discord shows "Song"
        runCurrent()
        advance(3_000)
        engine.submit(playing(OTHER))
        runCurrent()
        assertEquals(2, transport.frames.size)
        transport.reject(transport.frames[1], "bad details_url")
        runCurrent()
        assertFalse(engine.st.visible)

        // "Song" is not what plays any more: cleared (after Discord's rate limit), the rejection stays reported
        advance(1_999)
        assertEquals(2, transport.frames.size)
        advance(1)
        assertEquals(3, transport.frames.size)
        assertNull(transport.lastActivity)
        transport.accept(transport.frames[2])
        runCurrent()
        assertEquals("bad details_url", engine.st.activityError)
        assertEquals(Kind.CONNECTED, engine.st.kind)
        assertFalse(engine.st.visible)

        // the rejected presence is not sent again on its own (Discord would reject it again)
        advance(60_000)
        assertEquals(3, transport.frames.size)

        // with the next change it is tried again; rejected again, there is nothing of ours left to clear
        engine.submit(playing(OTHER))
        runCurrent()
        assertEquals(4, transport.frames.size)
        assertEquals("Other", transport.lastActivity!!.text("details"))
        transport.reject(transport.frames[3], "bad details_url")
        runCurrent()
        advance(60_000)
        assertEquals(4, transport.frames.size)
        assertEquals("bad details_url", engine.st.activityError)

        // the next track is taken
        engine.submit(playing(THIRD))
        runCurrent()
        assertEquals(5, transport.frames.size)
        transport.accept(transport.frames[4])
        runCurrent()
        assertEquals("", engine.st.activityError)
        assertTrue(engine.st.visible)
    }

    @Test
    fun `a rejected test presence leaves the presence Discord showed before`() = runTest {
        val engine = connectedEngine()
        transport.accept(transport.frames[0])
        runCurrent()
        advance(3_000)
        engine.sendTest()
        runCurrent()
        assertEquals(2, transport.frames.size)
        transport.reject(transport.frames[1], "bad test")
        runCurrent()
        assertEquals("bad test", engine.st.activityError)
        assertFalse(engine.st.visible)
        advance(PresenceEngine.TEST_DURATION_MS - 1)
        assertEquals("nothing cleared or resent during the test", 2, transport.frames.size)

        // the test is over: the normal presence is sent again and taken
        advance(1)
        assertEquals(3, transport.frames.size)
        assertEquals("Song", transport.lastActivity!!.text("details"))
        transport.accept(transport.frames[2])
        runCurrent()
        assertEquals("", engine.st.activityError)
        assertTrue(engine.st.visible)
    }

    // ------------------------------------------------------------------------------------------- test presence

    @Test
    fun `test presence while nothing plays - 15 s, then cleared`() = runTest {
        val engine = newEngine()
        engine.start()
        runCurrent()
        assertTrue(transport.connects.isEmpty())

        engine.sendTest()
        runCurrent()
        assertEquals(1, transport.connects.size)
        assertTrue(engine.st.testing)
        assertFalse(engine.idle.value)
        transport.ready()
        runCurrent()
        assertEquals(1, transport.frames.size)
        val a = transport.lastActivity!!
        assertEquals("Test - Poweramp Discord Rich Presence 1.0.0", a.text("details"))
        assertEquals(StatusStrings().testLine, a.text("state"))
        assertEquals(ActivityBuilder.ASSET_LOGO, a.largeImage())
        assertEquals(FakeClock.WALL_BASE, a.getValue("timestamps").jsonObject.getValue("start").jsonPrimitive.long)
        assertTrue(engine.st.visible)

        advance(14_999)
        assertEquals(1, transport.frames.size)
        assertTrue(engine.st.testing)
        advance(1)
        assertEquals(2, transport.frames.size)
        assertNull(transport.lastActivity)
        assertFalse(engine.st.testing)
        assertFalse(engine.st.visible)

        advance(60_000)
        assertTrue("idle after the test", engine.idle.value)
    }

    @Test
    fun `test presence while playing - then the normal presence returns`() = runTest {
        val engine = connectedEngine()
        engine.sendTest()
        runCurrent()
        assertEquals(1, transport.frames.size)   // Discord's rate limit first
        advance(2_000)
        assertEquals(2, transport.frames.size)
        assertTrue(transport.lastActivity!!.text("details")!!.startsWith("Test - "))

        engine.submit(playing(OTHER))   // the test is not interrupted
        advance(14_999)
        assertEquals(2, transport.frames.size)
        advance(1)
        assertEquals(3, transport.frames.size)
        assertEquals("Other", transport.lastActivity!!.text("details"))
        assertFalse(engine.st.testing)
    }

    // ------------------------------------------------------------------------------------------- idle

    @Test
    fun `idle when nothing has been shown for idleGraceMs`() = runTest {
        val engine = newEngine()
        engine.start()
        runCurrent()
        assertFalse(engine.idle.value)
        advance(59_999)
        assertFalse(engine.idle.value)
        advance(1)
        assertTrue(engine.idle.value)

        engine.submit(playing())
        runCurrent()
        assertFalse(engine.idle.value)
        transport.ready()
        advance(5 * 60_000L)
        assertFalse(engine.idle.value)

        engine.submit(PlaybackSnapshot(PlayState.STOPPED, SONG))
        advance(59_999)
        assertFalse(engine.idle.value)
        advance(1)
        assertTrue(engine.idle.value)
        assertEquals("connection kept while it was not idle", 1, transport.connects.size)
    }

    // ------------------------------------------------------------------------------------------- covers

    @Test
    fun `cached cover is shown at once`() = runTest {
        covers.cache["Song"] = CoverResult(COVER, "Deezer")
        val engine = connectedEngine()
        assertEquals(COVER, transport.lastActivity!!.largeImage())
        assertEquals(COVER, engine.st.coverUrl)
        assertEquals("Deezer", engine.st.coverSource)
        assertFalse(engine.st.coverSearching)
        assertEquals(0, io.pending)
        assertTrue(covers.resolveCalls.isEmpty())
    }

    @Test
    fun `missing cover - logo first, resolved in the background, then sent`() = runTest {
        covers.found["Song"] = CoverResult(COVER, "iTunes")
        val engine = connectedEngine()
        assertEquals(ActivityBuilder.ASSET_LOGO, transport.lastActivity!!.largeImage())
        assertTrue(engine.st.coverSearching)
        assertTrue("runs on the IO dispatcher", covers.resolveCalls.isEmpty())

        io.runAll()
        runCurrent()
        assertEquals(listOf("Song"), covers.resolveCalls)
        assertFalse(engine.st.coverSearching)
        assertEquals(COVER, engine.st.coverUrl)
        assertEquals("iTunes", engine.st.coverSource)
        assertEquals(1, transport.frames.size)   // Discord's rate limit
        advance(2_000)
        assertEquals(2, transport.frames.size)
        assertEquals(COVER, transport.lastActivity!!.largeImage())
        assertEquals("Song", transport.lastActivity!!.text("details"))
    }

    @Test
    fun `cover found for a previous track is ignored`() = runTest {
        covers.found["Song"] = CoverResult("https://img.test/old.jpg", "Deezer")
        val engine = connectedEngine()
        advance(2_000)
        engine.submit(playing(OTHER))
        runCurrent()
        assertEquals(2, transport.frames.size)
        assertEquals(ActivityBuilder.ASSET_LOGO, transport.lastActivity!!.largeImage())

        io.runAll()   // the lookup for "Song" finishes now
        runCurrent()
        io.runAll()   // then "Other" is looked up (nothing found)
        runCurrent()
        assertEquals(listOf("Song", "Other"), covers.resolveCalls)
        advance(10_000)
        assertEquals(2, transport.frames.size)
        assertTrue(transport.frames.none { "old.jpg" in it })
        assertEquals("", engine.st.coverUrl)
        assertFalse(engine.st.coverSearching)
    }

    @Test
    fun `cover URLs longer than 256 characters are ignored`() = runTest {
        val long = "https://img.test/" + "a".repeat(250)
        covers.cache["Song"] = CoverResult(long, "Deezer")
        covers.found["Song"] = CoverResult(long + "b", "iTunes")
        val engine = connectedEngine()
        assertEquals(ActivityBuilder.ASSET_LOGO, transport.lastActivity!!.largeImage())
        io.runAll()
        runCurrent()
        advance(5_000)
        assertEquals(listOf("Song"), covers.resolveCalls)
        assertEquals(1, transport.frames.size)
        assertEquals("", engine.st.coverUrl)
    }

    @Test
    fun `cover repair - new URLs, then resolved again, then the logo`() = runTest {
        val cover2 = "https://img.test/song-new.jpg"
        covers.cache["Song"] = CoverResult(COVER, "poweramp|x0.at")
        covers.found["Song"] = CoverResult(cover2, "poweramp|catbox.moe")
        covers.reachableAnswer = { false }
        val engine = connectedEngine()
        assertEquals(COVER, transport.lastActivity!!.largeImage())

        // 1st: Discord's copy cannot be loaded -> the same picture under another URL
        transport.answerWithProxy(transport.frames[0], "aaa/https/img.test/song.jpg")
        runCurrent()
        advance(3_999)
        assertTrue("checked 4 s after Discord's answer", covers.checked.isEmpty())
        advance(1)
        io.runAll()
        runCurrent()
        assertEquals(listOf("https://media.discordapp.net/external/aaa/https/img.test/song.jpg"), covers.checked)
        assertEquals(2, transport.frames.size)
        assertEquals("$COVER?r=1", transport.lastActivity!!.largeImage())

        // 2nd
        transport.answerWithProxy(transport.frames[1], "bbb")
        runCurrent()
        advance(4_000)
        io.runAll()
        runCurrent()
        assertEquals(3, transport.frames.size)
        assertEquals("$COVER?r=2", transport.lastActivity!!.largeImage())

        // 3rd: forgotten and resolved again, the logo meanwhile
        transport.answerWithProxy(transport.frames[2], "ccc")
        runCurrent()
        advance(4_000)
        io.runAll()
        runCurrent()
        assertEquals(listOf("Song"), covers.forgotten)
        assertEquals(4, transport.frames.size)
        assertEquals(ActivityBuilder.ASSET_LOGO, transport.lastActivity!!.largeImage())
        assertTrue(engine.st.coverSearching)
        io.runAll()
        runCurrent()
        assertEquals(listOf("Song"), covers.resolveCalls)
        advance(2_000)
        assertEquals(5, transport.frames.size)
        assertEquals(cover2, transport.lastActivity!!.largeImage())

        // 4th: given up - the logo for this track
        transport.answerWithProxy(transport.frames[4], "ddd")
        runCurrent()
        advance(4_000)
        io.runAll()
        runCurrent()
        assertEquals(6, transport.frames.size)
        assertEquals(ActivityBuilder.ASSET_LOGO, transport.lastActivity!!.largeImage())
        assertEquals(4, covers.checked.size)
        advance(60_000)
        io.runAll()
        runCurrent()
        assertEquals(6, transport.frames.size)

        // the next track starts without repairs
        covers.cache["Other"] = CoverResult("https://img.test/other.jpg", "Deezer")
        engine.submit(playing(OTHER))
        runCurrent()
        assertEquals("https://img.test/other.jpg", transport.lastActivity!!.largeImage())
    }

    @Test
    fun `status shows the logo once the cover is given up`() = runTest {
        covers.cache["Song"] = CoverResult(COVER, "Deezer")
        covers.found["Song"] = CoverResult(COVER, "Deezer")
        covers.reachableAnswer = { false }
        val engine = connectedEngine()
        assertEquals(COVER, engine.st.coverUrl)
        repeat(3) {
            transport.answerWithProxy(transport.frames.last(), "k$it")
            runCurrent()
            advance(4_000)
            io.runAll()
            runCurrent()
            advance(2_000)
            io.runAll()   // the lookup after the 3rd round (finds the same cover again)
            runCurrent()
            advance(2_000)
        }
        assertEquals(COVER, transport.lastActivity!!.largeImage())
        assertEquals(COVER, engine.st.coverUrl)
        transport.answerWithProxy(transport.frames.last(), "last")
        runCurrent()
        advance(4_000)
        io.runAll()
        runCurrent()
        assertEquals(ActivityBuilder.ASSET_LOGO, transport.lastActivity!!.largeImage())
        assertEquals("what Discord gets", "", engine.st.coverUrl)
        assertEquals("", engine.st.coverSource)
    }

    @Test
    fun `a hidden track's cover is never looked up`() = runTest {
        config = Config(excludePaths = "/private/")
        covers.cache["Song"] = CoverResult(COVER, "Deezer")
        covers.found["Other"] = CoverResult("https://img.test/other.jpg", "poweramp|x0.at")
        val engine = connectedEngine()
        advance(500)   // the previous presence is still shown: Discord's rate limit delays the clear
        engine.submit(playing(OTHER.copy(path = "/private/Other.mp3")))
        runCurrent()
        assertEquals(Hidden.PATH_FILTER, engine.st.hidden)
        assertEquals(1, transport.frames.size)
        io.runAll()
        advance(2_000)
        io.runAll()
        runCurrent()
        assertEquals(2, transport.frames.size)
        assertNull(transport.lastActivity)
        assertTrue("never uploaded", covers.resolveCalls.isEmpty())
    }

    @Test
    fun `status cover follows the track while not connected`() = runTest {
        covers.cache["Song"] = CoverResult(COVER, "Deezer")
        covers.cache["Other"] = CoverResult("https://img.test/other.jpg", "iTunes")
        val engine = connectedEngine()
        transport.close(TransportError.CLOSED, 1006, "gone")
        runCurrent()
        engine.submit(playing(OTHER))
        runCurrent()
        assertEquals(Kind.NOT_CONNECTED, engine.st.kind)
        assertEquals("https://img.test/other.jpg", engine.st.coverUrl)
        assertEquals("iTunes", engine.st.coverSource)

        engine.submit(playing(THIRD))   // nothing cached: the logo until it is looked up (once shown)
        runCurrent()
        assertEquals("", engine.st.coverUrl)
        assertEquals("", engine.st.coverSource)
        assertTrue(covers.resolveCalls.isEmpty())
    }

    @Test
    fun `no repair when our own network fails`() = runTest {
        covers.cache["Song"] = CoverResult(COVER, "poweramp|x0.at")
        covers.probeAnswer = { Probe.UNKNOWN }
        val engine = connectedEngine()
        transport.answerWithProxy(transport.frames[0], "aaa")
        runCurrent()
        advance(4_000)
        io.runAll()
        runCurrent()
        repeat(4) {   // once more 30 s later, then not again
            advance(30_000)
            io.runAll()
            runCurrent()
        }
        assertEquals(2, covers.checked.size)
        assertEquals("nothing resent", 1, transport.frames.size)
        assertTrue(covers.forgotten.isEmpty())
        assertTrue(covers.resolveCalls.isEmpty())
        assertEquals(COVER, engine.st.coverUrl)

        // not verified: the next update is checked again (and repaired if Discord's copy is really broken)
        covers.probeAnswer = { Probe.BROKEN }
        engine.submit(playing(posMs = 60_000))   // a seek
        runCurrent()
        assertEquals(2, transport.frames.size)
        transport.answerWithProxy(transport.frames[1], "aaa")
        runCurrent()
        advance(4_000)
        io.runAll()
        runCurrent()
        assertEquals(3, covers.checked.size)
        assertEquals(3, transport.frames.size)
        assertEquals("$COVER?r=1", transport.lastActivity!!.largeImage())
    }

    @Test
    fun `a check our own network prevented is made once more`() = runTest {
        covers.cache["Song"] = CoverResult(COVER, "Deezer")
        covers.probeAnswer = { Probe.UNKNOWN }
        connectedEngine()
        transport.answerWithProxy(transport.frames[0], "aaa")
        runCurrent()
        advance(4_000)
        io.runAll()
        runCurrent()
        assertEquals(1, covers.checked.size)

        covers.probeAnswer = { Probe.BROKEN }   // the network is back: Discord's copy is broken indeed
        advance(29_999)
        io.runAll()
        runCurrent()
        assertEquals(1, covers.checked.size)
        advance(1)
        io.runAll()
        runCurrent()
        assertEquals(listOf("https://media.discordapp.net/external/aaa", "https://media.discordapp.net/external/aaa"), covers.checked)
        assertEquals(2, transport.frames.size)
        assertEquals("$COVER?r=1", transport.lastActivity!!.largeImage())
    }

    @Test
    fun `a failing check counts as unknown`() = runTest {
        covers.cache["Song"] = CoverResult(COVER, "Deezer")
        covers.probeAnswer = { throw IllegalStateException("boom") }
        connectedEngine()
        transport.answerWithProxy(transport.frames[0], "aaa")
        runCurrent()
        advance(4_000)
        io.runAll()
        runCurrent()
        advance(30_000)
        assertEquals(1, transport.frames.size)
    }

    @Test
    fun `a cover found OK is not checked again with every refresh`() = runTest {
        config = Config(state = "%pos% / %dur%", refreshSeconds = 15)
        covers.cache["Song"] = CoverResult(COVER, "Deezer")
        val engine = connectedEngine()
        // Discord answers every update with its proxy key
        repeat(8) {
            transport.answerWithProxy(transport.frames.last(), "aaa")
            runCurrent()
            advance(4_000)
            io.runAll()
            runCurrent()
            advance(11_000)
        }
        assertEquals(9, transport.frames.size)
        assertEquals(listOf("https://media.discordapp.net/external/aaa"), covers.checked)

        // the next track is checked once again
        covers.cache["Other"] = CoverResult("https://img.test/other.jpg", "Deezer")
        engine.submit(playing(OTHER))
        advance(2_000)   // Discord's rate limit (a refresh went out just now)
        assertEquals("https://img.test/other.jpg", transport.lastActivity!!.largeImage())
        transport.answerWithProxy(transport.frames.last(), "bbb")
        runCurrent()
        advance(4_000)
        io.runAll()
        runCurrent()
        assertEquals(2, covers.checked.size)
    }

    @Test
    fun `a settings change keeps the pending cover check`() = runTest {
        covers.cache["Song"] = CoverResult(COVER, "Deezer")
        covers.reachableAnswer = { false }
        val engine = connectedEngine()
        transport.answerWithProxy(transport.frames[0], "aaa")
        runCurrent()
        advance(1_000)
        config = config.copy(coverMaxPx = 256)   // changes nothing Discord gets
        engine.refresh()
        runCurrent()
        assertEquals(1, transport.frames.size)
        advance(3_000)
        io.runAll()
        runCurrent()
        assertEquals(1, covers.checked.size)
        assertEquals(2, transport.frames.size)
        assertEquals("$COVER?r=1", transport.lastActivity!!.largeImage())
    }

    @Test
    fun `a check of a picture no longer shown is ignored`() = runTest {
        covers.cache["Song"] = CoverResult(COVER, "Deezer")
        covers.reachableAnswer = { false }
        val engine = connectedEngine()
        transport.answerWithProxy(transport.frames[0], "aaa")
        runCurrent()
        advance(4_000)   // the check runs ...
        // ... while the cover changes (cache cleared and found anew elsewhere)
        covers.cache["Song"] = CoverResult("https://img.test/song2.jpg", "iTunes")
        engine.refresh()
        runCurrent()
        assertEquals(2, transport.frames.size)
        assertEquals("https://img.test/song2.jpg", transport.lastActivity!!.largeImage())
        io.runAll()   // the old picture's check reports "broken"
        runCurrent()
        advance(10_000)
        assertEquals(2, transport.frames.size)
        assertEquals("https://img.test/song2.jpg", transport.lastActivity!!.largeImage())
    }

    @Test
    fun `a check that is due after the track ended is not made`() = runTest {
        covers.cache["Song"] = CoverResult(COVER, "Deezer")
        val engine = connectedEngine()
        transport.answerWithProxy(transport.frames[0], "aaa")
        runCurrent()
        advance(2_000)
        engine.submit(PlaybackSnapshot(PlayState.STOPPED, SONG))
        runCurrent()
        assertNull(transport.lastActivity)
        advance(10_000)
        io.runAll()
        runCurrent()
        assertTrue("Discord's copy of a cover no longer shown is not fetched", covers.checked.isEmpty())
    }

    @Test
    fun `reachable cover needs no repair`() = runTest {
        covers.cache["Song"] = CoverResult(COVER, "Deezer")
        connectedEngine()
        transport.answerWithProxy(transport.frames[0], "aaa")
        runCurrent()
        advance(4_000)
        io.runAll()
        runCurrent()
        advance(30_000)
        assertEquals(1, covers.checked.size)
        assertEquals(1, transport.frames.size)
    }

    @Test
    fun `clearing the cover cache looks the cover up again`() = runTest {
        covers.cache["Song"] = CoverResult(COVER, "Deezer")
        val engine = connectedEngine()
        engine.clearCoverCache()
        io.runAll()
        runCurrent()
        assertEquals(1, covers.cleared)
        advance(2_000)
        assertEquals(2, transport.frames.size)
        assertEquals(ActivityBuilder.ASSET_LOGO, transport.lastActivity!!.largeImage())
        io.runAll()
        runCurrent()
        assertEquals(listOf("Song"), covers.resolveCalls)
    }

    // ------------------------------------------------------------------------------------------- lifecycle

    @Test
    fun `stop clears the presence and disconnects`() = runTest {
        val engine = connectedEngine()
        engine.stop()
        runCurrent()
        assertEquals(2, transport.frames.size)
        assertNull(transport.lastActivity)
        assertEquals(1, transport.disconnects)
        assertNull(transport.listener)
        assertEquals(Kind.IDLE, engine.st.kind)
        assertFalse(engine.st.visible)

        engine.submit(playing(OTHER))
        advance(10_000)
        assertEquals(2, transport.frames.size)
        assertEquals(1, transport.connects.size)
    }

    @Test
    fun `start after stop works again`() = runTest {
        val engine = connectedEngine()
        engine.stop()
        runCurrent()

        engine.submit(playing(OTHER))   // while stopped: texts follow at once
        assertEquals("Other", engine.texts.value.details)
        engine.start()
        runCurrent()
        assertEquals(2, transport.connects.size)
        transport.ready()
        runCurrent()
        assertEquals(3, transport.frames.size)
        assertEquals("Other", transport.lastActivity!!.text("details"))
        assertEquals(2, engine.st.connects)

        // stopped and started again at once: the old run clears first, the new one connects
        engine.stop()
        engine.start()
        runCurrent()
        assertEquals(4, transport.frames.size)
        assertNull(transport.lastActivity)
        assertEquals(3, transport.connects.size)
        transport.ready()
        runCurrent()
        assertEquals("Other", transport.lastActivity!!.text("details"))
    }

    @Test
    fun `submit before start is used once started`() = runTest {
        val engine = newEngine()
        engine.submit(playing())
        assertEquals("Song", engine.texts.value.details)
        assertEquals(Hidden.NO, engine.texts.value.hidden)
        assertEquals(SONG, engine.snapshot.value.track)
        assertTrue(transport.connects.isEmpty())

        engine.start()
        runCurrent()
        transport.ready()
        runCurrent()
        assertEquals("Song", transport.lastActivity!!.text("details"))
    }

    @Test
    fun `start twice runs one session`() = runTest {
        val engine = newEngine()
        engine.submit(playing())
        engine.start()
        engine.start()
        runCurrent()
        assertEquals(1, transport.connects.size)
    }

    private companion object {
        const val CLIENT_ID = "123456789012345678"
        const val OWN_ID = "98765432109876543"
        const val PID = 4242
        const val COVER = "https://img.test/song.jpg"
        val SONG = TrackInfo(id = 1, realId = 11, title = "Song", artist = "Artist", album = "Album", path = "/music/Song.mp3", durationMs = 200_000)
        val OTHER = TrackInfo(id = 2, realId = 12, title = "Other", artist = "Artist", album = "Album 2", path = "/music/Other.mp3", durationMs = 180_000)
        val THIRD = TrackInfo(id = 3, realId = 13, title = "Third", artist = "Band", album = "Album 3", path = "/music/Third.mp3", durationMs = 240_000)
    }
}
