package io.github.rainbowfl4sh.powerampdiscordrp.service

import android.content.SharedPreferences
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.TransportError

/**
 * The application ID Discord rejected (close code 4000), as the engine reported it. Poweramp's broadcasts do not start
 * the presence service for it: Discord would only reject it again, on every song. Persisted, because the engine forgets
 * it with the process.
 *
 * Ends when Discord accepts the ID ([onStatus], e.g. after Reconnect / Test on the status page) or the application ID in
 * the settings changes ([clear]).
 */
class RejectedAppId(private val prefs: SharedPreferences) {
    @Volatile
    private var id = prefs.getString(KEY, null).orEmpty()

    /** True if Discord rejected this application ID. */
    fun matches(clientId: String): Boolean = clientId.isNotEmpty() && clientId == id

    /** Every engine status: a rejection is remembered, a working connection ends it. */
    fun onStatus(status: EngineStatus) {
        when {
            status.kind == EngineStatus.Kind.NOT_CONNECTED && status.error == TransportError.INVALID_CLIENT_ID ->
                set(status.clientId)
            status.kind == EngineStatus.Kind.CONNECTED -> set("")
        }
    }

    fun clear() = set("")

    @Synchronized
    private fun set(clientId: String) {
        if (clientId == id) return
        id = clientId
        prefs.edit().putString(KEY, clientId).apply()
    }

    private companion object {
        const val KEY = "rejectedAppId"
    }
}
