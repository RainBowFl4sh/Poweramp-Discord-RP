package io.github.rainbowfl4sh.powerampdiscordrp.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus.Kind
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.TransportError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RejectedAppIdTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("rejected-app-id-test", Context.MODE_PRIVATE)

    private val rejected = EngineStatus(kind = Kind.NOT_CONNECTED, error = TransportError.INVALID_CLIENT_ID, clientId = ID)

    @Test
    fun rejectionIsRememberedAcrossProcesses() {
        RejectedAppId(prefs).onStatus(rejected)
        val next = RejectedAppId(prefs)
        assertTrue(next.matches(ID))
        assertFalse(next.matches("98765432109876543"))
        assertFalse(next.matches(""))
    }

    @Test
    fun otherProblemsAreNoRejection() {
        val id = RejectedAppId(prefs)
        id.onStatus(EngineStatus(kind = Kind.NOT_CONNECTED, error = TransportError.CLOSED, clientId = ID))
        id.onStatus(EngineStatus(kind = Kind.NO_APP_ID))
        id.onStatus(EngineStatus(kind = Kind.CONNECTING, clientId = ID))
        assertFalse(id.matches(ID))
    }

    @Test
    fun acceptedConnectionOrClearEndsIt() {
        val id = RejectedAppId(prefs)
        id.onStatus(rejected)
        // Not connected for another reason (e.g. stopped): still known as rejected
        id.onStatus(EngineStatus(kind = Kind.IDLE, clientId = ID))
        assertTrue(id.matches(ID))
        id.onStatus(EngineStatus(kind = Kind.CONNECTED, clientId = ID))
        assertFalse(id.matches(ID))
        assertFalse(RejectedAppId(prefs).matches(ID))

        id.onStatus(rejected)
        id.clear()
        assertFalse(RejectedAppId(prefs).matches(ID))
    }

    private companion object {
        const val ID = "12345678901234567"
    }
}
