package io.github.rainbowfl4sh.powerampdiscordrp.service

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskManagerStopTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val prefs = app.getSharedPreferences("task-manager-stop-test", Context.MODE_PRIVATE)
    private var exit: ProcessExit? = null
    private val check = TaskManagerStop(prefs) { exit }

    private val stoppedWhileShowing = ProcessExit(timestampMs = 1_000, stoppedByUser = true, wasRunningService = true)

    @Test
    fun stopOfTheRunningServiceCountsOnceForTheNextBroadcastStart() {
        exit = stoppedWhileShowing
        assertTrue(check.check(startedByPoweramp = true))
        // The same exit record in a later process: already handled
        assertFalse(TaskManagerStop(prefs) { exit }.check(startedByPoweramp = true))
        // A newer one counts again
        exit = stoppedWhileShowing.copy(timestampMs = 2_000)
        assertTrue(TaskManagerStop(prefs) { exit }.check(startedByPoweramp = true))
    }

    @Test
    fun processStartedForTheAppScreenOnlyMarksTheExitAsSeen() {
        // E.g. Settings > Force stop: Poweramp's broadcasts only come again after the app was opened
        exit = stoppedWhileShowing
        assertFalse(check.check(startedByPoweramp = false))
        assertFalse(TaskManagerStop(prefs) { exit }.check(startedByPoweramp = true))
    }

    @Test
    fun otherExitsDoNotCount() {
        exit = stoppedWhileShowing.copy(stoppedByUser = false)   // low memory, crash, update, ...
        assertFalse(check.check(startedByPoweramp = true))
        exit = stoppedWhileShowing.copy(timestampMs = 2_000, wasRunningService = false)   // cached process, no presence
        assertFalse(check.check(startedByPoweramp = true))
        exit = null
        assertFalse(check.check(startedByPoweramp = true))
    }

    @Test
    fun failingLookupCountsAsNothing() {
        assertFalse(TaskManagerStop(prefs) { throw SecurityException("no") }.check(startedByPoweramp = true))
    }

    @Test
    fun readsTheLatestExitRecord() {
        assertNull(TaskManagerStop.lastExitOf(app))
        val manager = shadowOf(app.getSystemService(ActivityManager::class.java))
        manager.addApplicationExitInfo(
            ApplicationExitInfoBuilder.newBuilder()
                .setTimestamp(5_000)
                .setReason(ApplicationExitInfo.REASON_USER_REQUESTED)
                .setImportance(RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE)
                .build(),
        )
        assertEquals(ProcessExit(5_000, stoppedByUser = true, wasRunningService = true), TaskManagerStop.lastExitOf(app))
    }
}
