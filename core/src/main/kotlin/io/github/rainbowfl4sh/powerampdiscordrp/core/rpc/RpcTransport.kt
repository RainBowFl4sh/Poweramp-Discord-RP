package io.github.rainbowfl4sh.powerampdiscordrp.core.rpc

/** Why a connection could not be made / was lost. */
enum class TransportError { NONE, DISCORD_NOT_INSTALLED, BIND_FAILED, CONNECT_FAILED, CLOSED, INVALID_CLIENT_ID }

/**
 * Connection to the Discord client. Android: binder service of the Discord app (app module);
 * tests: a fake. All methods are non-blocking and may be called from any thread.
 */
interface RpcTransport {
    interface Listener {
        /** Connection established and Discord sent READY (frame = the raw READY frame). */
        fun onReady(frame: String)

        /** Any other frame from Discord. */
        fun onFrame(frame: String)

        /** Connection closed or failed. [code] = Discord close code (e.g. 4000 invalid client id) or -1. */
        fun onClosed(error: TransportError, code: Int, reason: String)
    }

    /** True once READY was received and until the connection is closed. */
    val isReady: Boolean

    fun setListener(listener: Listener?)

    /**
     * Starts connecting with this application ID (asynchronous; result via the listener). A connection that is not READY
     * within [READY_TIMEOUT_MS] is reported with onClosed(CONNECT_FAILED, ...) by the transport itself.
     */
    fun connect(applicationId: Long)

    /** Closes the connection (Discord removes the presence). No onClosed callback for a deliberate disconnect. */
    fun disconnect()

    /** Sends one JSON frame; false if not ready or sending failed. */
    fun send(frame: String): Boolean

    companion object {
        /**
         * How long Discord may take from connect() to READY, including starting the Discord app. The presence engine's
         * own connect deadline is a bit longer (a safety net), so the transport's timeout is the one that fires.
         */
        const val READY_TIMEOUT_MS = 30_000L
    }
}
