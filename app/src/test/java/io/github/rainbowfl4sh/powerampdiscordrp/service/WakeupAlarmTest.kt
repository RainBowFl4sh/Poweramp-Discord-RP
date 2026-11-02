package io.github.rainbowfl4sh.powerampdiscordrp.service

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WakeupAlarmTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Test
    fun oneInexactAlarmThatWakesTheDeviceAndIsReplacedOrCancelled() {
        WakeupAlarm.set(app, 300_000L)
        var alarm = alarms.nextScheduledAlarm
        assertNotNull(alarm)
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm!!.type)
        assertEquals(300_000L, alarm.triggerAtTime)
        assertTrue("fires in Doze too", alarm.isAllowWhileIdle)
        assertEquals(WakeupReceiver::class.java.name, shadowOf(alarm.operation).savedIntent.component!!.className)

        WakeupAlarm.set(app, 360_000L)
        assertEquals("replaced, not added", 1, alarms.scheduledAlarms.size)
        alarm = alarms.nextScheduledAlarm
        assertEquals(360_000L, alarm!!.triggerAtTime)

        WakeupAlarm.set(app, null)
        assertNull(alarms.nextScheduledAlarm)

        WakeupAlarm.set(app, 1_000L)
        WakeupAlarm.cancel(app)
        assertNull(alarms.nextScheduledAlarm)
    }

    @Test
    fun staleAlarmWithoutARunningServiceDoesNothing() {
        // the process that set it is gone: nothing to wake, and no goAsync() left open
        WakeupReceiver().onReceive(app, android.content.Intent(app, WakeupReceiver::class.java))
        assertEquals(false, PresenceService.running.value)
    }
}
