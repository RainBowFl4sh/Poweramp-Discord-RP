package io.github.rainbowfl4sh.powerampdiscordrp.discord

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import androidx.test.core.app.ApplicationProvider
import com.discord.socialsdk.rpc.IDiscordRpcCallback
import com.discord.socialsdk.rpc.IDiscordRpcConnection
import com.discord.socialsdk.rpc.IDiscordRpcService
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.RpcTransport
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.TransportError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscordBinderTransportTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val component = ComponentName("com.discord", "com.discord.socialrpc.DiscordRpcService")
    private val bindIntent = Intent(DiscordBinderTransport.SERVICE_ACTION).setPackage("com.discord")

    /** What the Discord app's service saw. */
    private class FakeDiscord : IDiscordRpcService.Stub() {
        var connects = 0
        var applicationId = 0L
        var version: String? = null
        var callback: IDiscordRpcCallback? = null
        var connectError: RemoteException? = null
        var noConnection = false
        var sendError: RemoteException? = null
        val sent = mutableListOf<String>()
        var disconnects = 0
        val calls = mutableListOf<String>()   // sendFrame / disconnect in the order Discord got them

        override fun connect(applicationId: Long, version: String?, callback: IDiscordRpcCallback?): IDiscordRpcConnection? {
            connectError?.let { throw it }
            connects++
            this.applicationId = applicationId
            this.version = version
            this.callback = callback
            if (noConnection) return null
            return object : IDiscordRpcConnection.Stub() {
                override fun sendFrame(frame: String?) {
                    sendError?.let { throw it }
                    sent += frame.orEmpty()
                    calls += "send:${frame.orEmpty()}"
                }

                override fun disconnect() {
                    disconnects++
                    calls += "disconnect"
                }
            }
        }
    }

    private class RecordingListener : RpcTransport.Listener {
        val events = mutableListOf<String>()
        override fun onReady(frame: String) {
            events += "ready"
        }

        override fun onFrame(frame: String) {
            events += "frame:$frame"
        }

        override fun onClosed(error: TransportError, code: Int, reason: String) {
            events += "closed:$error:$code"
        }
    }

    private val discord = FakeDiscord()
    private val listener = RecordingListener()

    // Binder calls run right away on the calling thread: the test controls the order
    private val transport = DiscordBinderTransport(app, io = Executor { it.run() }).also { it.setListener(listener) }

    @Before
    fun installDiscord() {
        val pm = shadowOf(app.packageManager)
        pm.addServiceIfNotPresent(component)
        pm.addIntentFilterForService(component, IntentFilter(DiscordBinderTransport.SERVICE_ACTION))
        shadowOf(app).setComponentNameAndServiceForBindServiceForIntent(bindIntent, component, discord)
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun connectAndReady(appId: Long = 1_234_567_890_123_456_789L) {
        transport.connect(appId)
        idleMain()   // onServiceConnected
        discord.callback!!.onFrame(READY)
    }

    @Test
    fun connectsWithApplicationIdAndVersionThenReady() {
        transport.connect(1_234_567_890_123_456_789L)
        assertFalse(transport.isReady)
        idleMain()
        assertEquals(1, discord.connects)
        assertEquals(1_234_567_890_123_456_789L, discord.applicationId)
        assertEquals("1", discord.version)
        assertFalse(transport.isReady)   // not before READY

        discord.callback!!.onFrame(READY)
        assertTrue(transport.isReady)
        assertEquals(listOf("ready"), listener.events)
    }

    @Test
    fun otherFramesGoToOnFrame() {
        connectAndReady()
        val answer = """{"cmd":"SET_ACTIVITY","data":{"name":"Poweramp"},"evt":null,"nonce":"n1"}"""
        discord.callback!!.onFrame(answer)
        assertEquals(listOf("ready", "frame:$answer"), listener.events)
    }

    @Test
    fun sendOnlyWhenReady() {
        assertFalse(transport.send("""{"cmd":"SET_ACTIVITY"}"""))
        transport.connect(1L)
        idleMain()
        assertFalse(transport.send("""{"cmd":"SET_ACTIVITY"}"""))
        discord.callback!!.onFrame(READY)
        assertTrue(transport.send("""{"cmd":"SET_ACTIVITY","nonce":"a"}"""))
        assertEquals(listOf("""{"cmd":"SET_ACTIVITY","nonce":"a"}"""), discord.sent)
    }

    @Test
    fun invalidClientIdClose() {
        connectAndReady()
        discord.callback!!.onClose(4000, "Invalid Client ID")
        assertFalse(transport.isReady)
        assertEquals(listOf("ready", "closed:INVALID_CLIENT_ID:4000"), listener.events)
        assertTrue(shadowOf(app).unboundServiceConnections.isNotEmpty())
        assertFalse(transport.send("{}"))
    }

    @Test
    fun otherCloseCode() {
        connectAndReady()
        discord.callback!!.onClose(1000, "")
        assertEquals("closed:CLOSED:1000", listener.events.last())
        assertTrue(shadowOf(app).unboundServiceConnections.isNotEmpty())
    }

    @Test
    fun serviceDisconnected() {
        connectAndReady()
        val connection: ServiceConnection = shadowOf(app).boundServiceConnections.single()
        connection.onServiceDisconnected(component)
        assertFalse(transport.isReady)
        assertEquals("closed:CLOSED:-1", listener.events.last())
        // Late callbacks of the closed connection are ignored
        discord.callback!!.onFrame(READY)
        discord.callback!!.onClose(4000, "late")
        assertEquals(2, listener.events.size)
    }

    @Test
    fun bindingDied() {
        connectAndReady()
        shadowOf(app).boundServiceConnections.single().onBindingDied(component)
        assertEquals("closed:CLOSED:-1", listener.events.last())
    }

    @Test
    fun discordNotInstalled() {
        val t = DiscordBinderTransport(app, packages = listOf("com.discord.missing"), io = Executor { it.run() })
        t.setListener(listener)
        t.connect(1L)
        assertEquals(listOf("closed:DISCORD_NOT_INSTALLED:-1"), listener.events)
        assertTrue(shadowOf(app).boundServiceConnections.isEmpty())
    }

    @Test
    fun bindRefused() {
        shadowOf(app).declareActionUnbindable(DiscordBinderTransport.SERVICE_ACTION)
        transport.connect(1L)
        idleMain()
        assertEquals(listOf("closed:BIND_FAILED:-1"), listener.events)
        assertEquals(0, discord.connects)
    }

    @Test
    fun connectFails() {
        discord.connectError = RemoteException("boom")
        transport.connect(1L)
        idleMain()
        assertEquals(listOf("closed:CONNECT_FAILED:-1"), listener.events)
        assertTrue(shadowOf(app).unboundServiceConnections.isNotEmpty())
    }

    @Test
    fun deliberateDisconnect() {
        connectAndReady()
        transport.disconnect()
        assertFalse(transport.isReady)
        assertEquals(1, discord.disconnects)
        assertTrue(shadowOf(app).unboundServiceConnections.isNotEmpty())
        assertEquals(listOf("ready"), listener.events)   // no onClosed for a deliberate disconnect
        // Discord's own close notification after our disconnect is not reported either
        discord.callback!!.onClose(1000, "bye")
        assertEquals(listOf("ready"), listener.events)
    }

    @Test
    fun disconnectBeforeServiceConnected() {
        transport.connect(1L)
        transport.disconnect()
        idleMain()
        assertEquals(0, discord.connects)
        assertTrue(listener.events.isEmpty())
    }

    @Test
    fun reconnectReplacesTheOldConnection() {
        connectAndReady(appId = 1L)
        val firstCallback = discord.callback!!
        transport.connect(2L)
        assertEquals(1, discord.disconnects)   // the old connection is closed (presence removed)
        idleMain()
        assertEquals(2L, discord.applicationId)
        assertFalse(transport.isReady)

        firstCallback.onClose(1000, "old")     // nothing heard from the replaced connection
        discord.callback!!.onFrame(READY)
        assertTrue(transport.isReady)
        assertEquals(listOf("ready", "ready"), listener.events)
    }

    @Test
    fun nullBinding() {
        transport.connect(1L)
        idleMain()
        shadowOf(app).boundServiceConnections.single().onNullBinding(component)
        assertEquals("closed:CLOSED:-1", listener.events.last())
        assertTrue(shadowOf(app).unboundServiceConnections.isNotEmpty())
    }

    @Test
    fun discordProcessDied() {
        connectAndReady()
        val connection = shadowOf(app).boundServiceConnections.single()
        (connection as IBinder.DeathRecipient).binderDied()
        assertFalse(transport.isReady)
        assertEquals(listOf("ready", "closed:CLOSED:-1"), listener.events)
        assertTrue(shadowOf(app).unboundServiceConnections.isNotEmpty())
    }

    @Test
    fun discordReturnsNoConnection() {
        discord.noConnection = true
        transport.connect(1L)
        idleMain()
        assertEquals(listOf("closed:CONNECT_FAILED:-1"), listener.events)
    }

    @Test
    fun sendingFailureClosesTheConnection() {
        connectAndReady()
        discord.sendError = RemoteException("dead")
        assertTrue(transport.send("{}"))   // queued; the failure arrives through the listener
        assertFalse(transport.isReady)
        assertEquals(listOf("ready", "closed:CLOSED:-1"), listener.events)
    }

    @Test
    fun noReadyInTimeGivesUp() {
        val t = DiscordBinderTransport(app, io = Executor { it.run() }, readyTimeoutMs = 5_000)
        t.setListener(listener)
        t.connect(1L)
        idleMain()
        assertEquals(1, discord.connects)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertTrue(listener.events.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(listOf("closed:CONNECT_FAILED:-1"), listener.events)
        assertEquals(1, discord.disconnects)   // Discord's half-open connection is closed as well
        assertTrue(shadowOf(app).unboundServiceConnections.isNotEmpty())
        // READY after giving up is ignored
        discord.callback!!.onFrame(READY)
        assertFalse(t.isReady)
    }

    @Test
    fun readyInTimeCancelsTheTimeout() {
        val t = DiscordBinderTransport(app, io = Executor { it.run() }, readyTimeoutMs = 5_000)
        t.setListener(listener)
        t.connect(1L)
        idleMain()
        discord.callback!!.onFrame(READY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertTrue(t.isReady)
        assertEquals(listOf("ready"), listener.events)
    }

    /** Like the real worker thread, runs the queued binder calls later: when the test says so. */
    private class DeferredExecutor : Executor {
        private val queue = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            queue.addLast(command)
        }

        fun runAll() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    private fun readyWithDeferredWorker(io: DeferredExecutor, appId: Long = 1L) =
        DiscordBinderTransport(app, io = io).also { t ->
            t.setListener(listener)
            t.connect(appId)
            io.runAll()   // bindService
            idleMain()    // onServiceConnected
            io.runAll()   // connect
            discord.callback!!.onFrame(READY)
            assertTrue(t.isReady)
        }

    @Test
    fun framesQueuedBeforeADeliberateDisconnectAreSentFirst() {
        val io = DeferredExecutor()
        val t = readyWithDeferredWorker(io)
        // The engine clears the presence and disconnects at once, before the worker got to the frame
        assertTrue(t.send(CLEAR))
        t.disconnect()
        assertFalse(t.isReady)
        assertFalse(t.send(CLEAR))   // nothing new after disconnect()
        assertTrue(discord.calls.isEmpty())
        io.runAll()
        assertEquals(listOf("send:$CLEAR", "disconnect"), discord.calls)
        assertEquals(listOf("ready"), listener.events)
    }

    @Test
    fun framesQueuedBeforeAReplacingConnectGoToTheOldConnection() {
        val io = DeferredExecutor()
        val t = readyWithDeferredWorker(io, appId = 1L)
        assertTrue(t.send(CLEAR))
        t.connect(2L)
        io.runAll()
        assertEquals(listOf("send:$CLEAR", "disconnect"), discord.calls)
        idleMain()
        io.runAll()
        assertEquals(2L, discord.applicationId)
    }

    @Test
    fun framesQueuedWhenDiscordClosesTheConnectionAreDropped() {
        val io = DeferredExecutor()
        val t = readyWithDeferredWorker(io)
        assertTrue(t.send(CLEAR))
        discord.callback!!.onClose(1000, "bye")
        io.runAll()
        assertTrue(discord.calls.isEmpty())   // no frame and no disconnect() for a connection Discord ended
        assertEquals(listOf("ready", "closed:CLOSED:1000"), listener.events)
    }

    @Test
    fun noListenerIsFine() {
        transport.setListener(null)
        connectAndReady()
        assertTrue(transport.isReady)
    }

    @Test
    fun readyFrameDetection() {
        assertTrue(DiscordBinderTransport.isReadyFrame(READY))
        assertFalse(DiscordBinderTransport.isReadyFrame("""{"cmd":"DISPATCH","evt":"ERROR"}"""))
        assertFalse(DiscordBinderTransport.isReadyFrame("""{"cmd":"SET_ACTIVITY","evt":null}"""))
        assertFalse(DiscordBinderTransport.isReadyFrame("not json"))
    }

    private companion object {
        const val CLEAR = """{"cmd":"SET_ACTIVITY","args":{"pid":1,"activity":null},"nonce":"clear"}"""
        const val READY = """{"cmd":"DISPATCH","data":{"v":1,"config":{"cdn_host":"cdn.discordapp.com"},""" +
            """"user":{"id":"0","username":"Discord","discriminator":"0"}},"evt":"READY","nonce":null}"""
    }
}
