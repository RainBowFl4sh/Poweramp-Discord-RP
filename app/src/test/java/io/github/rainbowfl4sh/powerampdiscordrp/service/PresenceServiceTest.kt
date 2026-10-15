package io.github.rainbowfl4sh.powerampdiscordrp.service

import android.app.Application
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The paths of the service that end before the presence engine is started (the engine itself is tested in core).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PresenceServiceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun startRequestsTheForegroundServiceAndEndsAUserStop() {
        PresenceService.stop(app)
        assertTrue(PresenceService.stoppedByUser.value)

        assertTrue(PresenceService.start(app))
        assertFalse(PresenceService.stoppedByUser.value)
        val started = shadowOf(app).nextStartedService
        assertNotNull(started)
        assertEquals(PresenceService::class.java.name, started.component!!.className)
    }

    @Test
    fun stopActionGoesToTheForegroundFirstThenStops() {
        PresenceService.start(app)
        val controller = Robolectric.buildService(
            PresenceService::class.java,
            Intent(app, PresenceService::class.java).setAction(PresenceService.ACTION_STOP),
        ).create().startCommand(0, 7)
        val shadow = shadowOf(controller.get())

        // startForegroundService() demands startForeground() also when the start only stops the service
        assertEquals(Notifications.ID_PRESENCE, shadow.lastForegroundNotificationId)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, controller.get().foregroundServiceType)
        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.notificationShouldRemoved)
        assertEquals(7, shadow.stopSelfResultId)
        assertTrue(PresenceService.stoppedByUser.value)
        assertFalse(PresenceService.running.value)
        controller.destroy()
        PresenceService.start(app)
    }

    @Test
    fun refusedForegroundStopsAtOnce() {
        PresenceService.start(app)
        val controller = Robolectric.buildService(PresenceService::class.java, Intent(app, PresenceService::class.java))
        shadowOf(controller.get()).setThrowInStartForeground(IllegalStateException("not allowed"))
        controller.create().startCommand(0, 3)

        val shadow = shadowOf(controller.get())
        assertEquals(3, shadow.stopSelfResultId)
        assertFalse(PresenceService.running.value)
        assertFalse(PresenceService.stoppedByUser.value)   // not the user's doing
        controller.destroy()
    }
}
