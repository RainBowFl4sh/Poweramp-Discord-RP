package io.github.rainbowfl4sh.powerampdiscordrp.discord

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import com.discord.socialsdk.rpc.IDiscordRpcCallback
import com.discord.socialsdk.rpc.IDiscordRpcConnection
import com.discord.socialsdk.rpc.IDiscordRpcService
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.RpcTransport
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.TransportError
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Logger
import io.github.rainbowfl4sh.powerampdiscordrp.settings.EnvironmentCheck
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * [RpcTransport] over the binder service the Discord Android app exports for the Discord Social SDK
 * (action com.discord.socialsdk.rpc.IDiscordRpcService): bind, connect(applicationId, "1", callback), then the same JSON
 * frames as the desktop RPC travel through IDiscordRpcConnection.sendFrame / IDiscordRpcCallback.onFrame.
 *
 * Every connect() starts a new [Session]; callbacks of an older session (late binder callbacks, a replaced connection)
 * are ignored, so the listener only ever hears about the current one. All binder calls run on [io] (they block until
 * the Discord process answers); connect() itself is asynchronous too, so listener callbacks never arrive from inside
 * a call into the transport.
 *
 * [io] runs one task after the other: frames sent before a deliberate [disconnect] (or a [connect] that replaces the
 * connection) - the engine clears the presence that way - still reach Discord before the connection is closed.
 *
 * A connection that is not READY within [readyTimeoutMs] (Discord hangs while starting, a binding that never connects)
 * is closed with CONNECT_FAILED, so the engine's reconnect logic takes over instead of waiting forever.
 */
class DiscordBinderTransport(
    context: Context,
    private val log: Logger = Logger.NONE,
    private val packages: List<String> = EnvironmentCheck.DISCORD_PACKAGES,
    private val io: Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "DiscordRpc").apply { isDaemon = true } },
    private val readyTimeoutMs: Long = READY_TIMEOUT_MS,
) : RpcTransport {
    private val context: Context = context.applicationContext ?: context
    private val timer = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var current: Session? = null   // guarded by lock

    @Volatile
    private var listener: RpcTransport.Listener? = null

    override val isReady: Boolean get() = synchronized(lock) { current?.ready == true }

    override fun setListener(listener: RpcTransport.Listener?) {
        this.listener = listener
    }

    override fun connect(applicationId: Long) {
        val session = Session(applicationId)
        val previous = synchronized(lock) {
            current.also {
                it?.markClosed(deliberate = true)
                current = session
            }
        }
        // A new connect replaces the old connection (e.g. another application ID)
        if (previous != null) release(previous, sendDisconnect = true)
        timer.postDelayed(session.readyTimeout, readyTimeoutMs)
        execute { bind(session) }
    }

    override fun disconnect() {
        val session = synchronized(lock) {
            current?.also {
                it.markClosed(deliberate = true)
                current = null
            }
        } ?: return
        log.log("Discord: disconnecting")
        release(session, sendDisconnect = true)
    }

    override fun send(frame: String): Boolean {
        val session = synchronized(lock) { current?.takeIf { it.ready } } ?: return false
        return execute {
            // Sent unless Discord closed the connection meanwhile: a frame queued before our own disconnect() goes out
            // first (release() is queued after it and takes the connection away)
            val connection = synchronized(lock) {
                if (session.closed && !session.deliverQueued) null else session.connection
            } ?: return@execute
            try {
                connection.sendFrame(frame)
            } catch (e: RemoteException) {
                close(session, TransportError.CLOSED, -1, "sending failed: $e")
            } catch (e: RuntimeException) {
                // Thrown by Discord's side for this frame only (e.g. a malformed payload) - the connection stays usable
                log.log("Discord: frame rejected: $e")
            }
        }
    }

    // ------------------------------------------------------------------------------------------- connection steps

    private fun bind(session: Session) {
        if (synchronized(lock) { session.closed }) return
        val intent = resolveServiceIntent()
            ?: return close(session, TransportError.DISCORD_NOT_INSTALLED, -1, "the Discord app is not installed")
        val bound = try {
            context.bindService(intent, session, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            log.log("Discord: binding not allowed: $e")
            false
        }
        if (!bound) {
            // Even a failed bindService() keeps the connection registered until unbindService()
            unbindQuietly(session)
            return close(session, TransportError.BIND_FAILED, -1, "Android did not allow binding to ${intent.`package`}")
        }
        val keep = synchronized(lock) {
            if (!session.closed) session.bound = true
            !session.closed
        }
        if (keep) log.log("Discord: binding to ${intent.`package`} (application ${session.applicationId})")
        else unbindQuietly(session)   // disconnected while binding; release() did not see the binding yet
    }

    private fun establish(session: Session, binder: IBinder) {
        val proceed = synchronized(lock) {
            if (!session.closed) session.binder = binder
            !session.closed
        }
        if (!proceed) return
        val connection = try {
            binder.linkToDeath(session, 0)
            IDiscordRpcService.Stub.asInterface(binder).connect(session.applicationId, RPC_VERSION, session.callback)
        } catch (e: RemoteException) {
            return close(session, TransportError.CONNECT_FAILED, -1, "connect failed: $e")
        } catch (e: RuntimeException) {
            // e.g. a SecurityException thrown by Discord's side
            return close(session, TransportError.CONNECT_FAILED, -1, "connect failed: $e")
        } ?: return close(session, TransportError.CONNECT_FAILED, -1, "Discord returned no connection")

        val keep = synchronized(lock) {
            if (!session.closed) session.connection = connection
            !session.closed
        }
        if (keep) {
            log.log("Discord: connected, waiting for READY")
        } else {
            // Closed while connect() was running: nobody else will close this connection
            try {
                connection.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun handleFrame(session: Session, frame: String) {
        val ready = isReadyFrame(frame)
        synchronized(lock) {
            if (session.closed || current !== session) return
            if (ready) session.ready = true
        }
        if (ready) {
            timer.removeCallbacks(session.readyTimeout)
            log.log("Discord: READY")
            listener?.onReady(frame)
        } else {
            listener?.onFrame(frame)
        }
    }

    /**
     * The connection is gone (Discord closed it, the binding died, ...) or is given up ([sendDisconnect]):
     * tell the listener if it was the current one.
     */
    private fun close(session: Session, error: TransportError, code: Int, reason: String, sendDisconnect: Boolean = false) {
        val wasCurrent = synchronized(lock) {
            if (session.closed) return
            session.markClosed()
            (current === session).also { if (it) current = null }
        }
        release(session, sendDisconnect)
        if (!wasCurrent) return
        log.log("Discord: connection closed - $error${if (code >= 0) " ($code)" else ""}: $reason")
        listener?.onClosed(error, code, reason)
    }

    /** Disconnects (if wanted) and unbinds a closed session - on [io], after anything already queued for it. */
    private fun release(session: Session, sendDisconnect: Boolean) {
        timer.removeCallbacks(session.readyTimeout)
        execute {
            val (connection, binder, bound) = synchronized(lock) {
                Triple(session.connection, session.binder, session.bound).also {
                    session.connection = null
                    session.binder = null
                    session.bound = false
                }
            }
            if (sendDisconnect && connection != null) {
                try {
                    connection.disconnect()   // Discord removes the presence of this connection
                } catch (e: Exception) {
                    log.log("Discord: disconnect failed: $e")
                }
            }
            try {
                binder?.unlinkToDeath(session, 0)
            } catch (_: Exception) {
            }
            if (bound) unbindQuietly(session)
        }
    }

    private fun unbindQuietly(connection: ServiceConnection) {
        try {
            context.unbindService(connection)
        } catch (_: IllegalArgumentException) {
            // was not bound
        }
    }

    /** First Discord package that exports the RPC service (action + package = explicit enough for bindService). */
    private fun resolveServiceIntent(): Intent? = packages.firstNotNullOfOrNull { pkg ->
        val intent = Intent(SERVICE_ACTION).setPackage(pkg)
        val found = try {
            context.packageManager.resolveService(intent, 0) != null
        } catch (_: RuntimeException) {
            false
        }
        intent.takeIf { found }
    }

    private fun execute(block: () -> Unit): Boolean = try {
        io.execute(block)
        true
    } catch (e: RejectedExecutionException) {
        log.log("Discord: worker rejected a task: $e")
        false
    }

    /** One bind + connect attempt. Fields are guarded by [lock]. */
    private inner class Session(val applicationId: Long) : ServiceConnection, IBinder.DeathRecipient {
        var closed = false
        var deliverQueued = false   // closed by us: frames queued before still go out (until release())
        var ready = false
        var bound = false
        var binder: IBinder? = null
        var connection: IDiscordRpcConnection? = null

        val readyTimeout = Runnable {
            if (synchronized(lock) { !ready }) {
                val reason = "Discord did not answer within ${readyTimeoutMs / 1000} s"
                close(this, TransportError.CONNECT_FAILED, -1, reason, sendDisconnect = true)
            }
        }

        val callback: IDiscordRpcCallback.Stub = object : IDiscordRpcCallback.Stub() {
            override fun onFrame(frame: String?) {
                if (frame != null) handleFrame(this@Session, frame)
            }

            override fun onClose(code: Int, reason: String?) {
                val error = if (code == CLOSE_INVALID_CLIENT_ID) TransportError.INVALID_CLIENT_ID else TransportError.CLOSED
                close(this@Session, error, code, reason?.takeIf { it.isNotBlank() } ?: "closed by Discord")
            }
        }

        /** [deliberate] = disconnect() / a new connect(), not Discord or Android ending the connection. */
        fun markClosed(deliberate: Boolean = false) {
            closed = true
            ready = false
            deliverQueued = deliberate
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service == null) return close(this, TransportError.CLOSED, -1, "the Discord service returned no binder")
            execute { establish(this, service) }   // connect() blocks on the Discord process: not on the main thread
        }

        override fun onServiceDisconnected(name: ComponentName?) =
            close(this, TransportError.CLOSED, -1, "the Discord service disconnected")

        override fun onBindingDied(name: ComponentName?) =
            close(this, TransportError.CLOSED, -1, "the binding to the Discord app died")

        override fun onNullBinding(name: ComponentName?) =
            close(this, TransportError.CLOSED, -1, "the Discord service refused the binding")

        override fun binderDied() = close(this, TransportError.CLOSED, -1, "the Discord app process died")
    }

    companion object {
        /** Intent action of the Social SDK RPC service exported by the Discord app. */
        const val SERVICE_ACTION = "com.discord.socialsdk.rpc.IDiscordRpcService"

        /** RPC protocol version passed to connect() (the Social SDK uses "1"). */
        const val RPC_VERSION = "1"

        /** How long Discord may take from bindService() to READY (includes starting the Discord app). */
        const val READY_TIMEOUT_MS = 30_000L

        /** Close code for an unknown / invalid application ID (same as the desktop RPC). */
        const val CLOSE_INVALID_CLIENT_ID = 4000

        /** {"cmd":"DISPATCH","evt":"READY",...}: the first frame Discord sends on a usable connection. */
        fun isReadyFrame(frame: String): Boolean = try {
            val o = Json.parseToJsonElement(frame).jsonObject
            o["cmd"]?.jsonPrimitive?.contentOrNull == "DISPATCH" && o["evt"]?.jsonPrimitive?.contentOrNull == "READY"
        } catch (_: Exception) {
            false
        }
    }
}
