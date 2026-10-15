package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.ReleaseInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.UpdateResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    private val day = UpdateSchedule.DAY_MS
    private val now = 1_800_000_000_000L

    private fun config(frequency: Int, lastCheck: Long, on: Boolean = true) =
        Config(updateCheck = on, updateFrequency = frequency, updateLastCheckMs = lastCheck)

    @Test
    fun switchedOffIsNeverDue() {
        assertFalse(UpdateSchedule.isDue(config(Config.UPDATE_EVERY_START, 0, on = false), now, false))
        assertFalse(UpdateSchedule.isDue(config(Config.UPDATE_DAILY, 0, on = false), now, false))
    }

    @Test
    fun everyStartMeansOncePerSession() {
        assertTrue(UpdateSchedule.isDue(config(Config.UPDATE_EVERY_START, now - 1000), now, false))
        assertFalse(UpdateSchedule.isDue(config(Config.UPDATE_EVERY_START, now - 1000), now, true))
    }

    @Test
    fun intervals() {
        assertTrue(UpdateSchedule.isDue(config(Config.UPDATE_DAILY, 0), now, true))   // never checked
        assertFalse(UpdateSchedule.isDue(config(Config.UPDATE_DAILY, now - day + 1), now, false))
        assertTrue(UpdateSchedule.isDue(config(Config.UPDATE_DAILY, now - day), now, false))
        assertFalse(UpdateSchedule.isDue(config(Config.UPDATE_WEEKLY, now - 6 * day), now, false))
        assertTrue(UpdateSchedule.isDue(config(Config.UPDATE_WEEKLY, now - 7 * day), now, false))
        assertFalse(UpdateSchedule.isDue(config(Config.UPDATE_MONTHLY, now - 29 * day), now, false))
        assertTrue(UpdateSchedule.isDue(config(Config.UPDATE_MONTHLY, now - 30 * day), now, false))
    }

    @Test
    fun clockSetBackMakesItDue() {
        assertTrue(UpdateSchedule.isDue(config(Config.UPDATE_MONTHLY, now + day), now, false))
    }

    @Test
    fun unknownFrequencyFallsBackToDaily() {
        assertEquals(day, UpdateSchedule.intervalMs(7))
        assertEquals(0, UpdateSchedule.intervalMs(Config.UPDATE_EVERY_START))
    }

    // ---- what the About tab shows

    /** Plain numeric comparison for the tests (the real one is UpdateChecker.isNewer). */
    private val newer: (String, String) -> Boolean = { a, b ->
        val x = a.split('.').map { it.toInt() }
        val y = b.split('.').map { it.toInt() }
        (0 until maxOf(x.size, y.size)).map { (x.getOrElse(it) { 0 }) - (y.getOrElse(it) { 0 }) }.firstOrNull { it != 0 }?.let { it > 0 } ?: false
    }
    private val releases = "https://github.com/x/y/releases/latest"
    private val release = ReleaseInfo("1.1.0", "https://github.com/x/y/releases/tag/v1.1.0", "https://github.com/x/y/app.apk", "notes")

    private fun view(state: UpdateCheckState, c: Config = Config()) = UpdateView.of(state, c, "1.0.0", releases, newer)

    @Test
    fun neverChecked() {
        assertEquals(UpdateView.Kind.NEVER, view(UpdateCheckState.Idle).kind)
    }

    @Test
    fun checking() {
        assertEquals(UpdateView.Kind.CHECKING, view(UpdateCheckState.Checking, Config(updateLatest = "2.0.0")).kind)
    }

    @Test
    fun newerReleaseOffersTheApk() {
        val v = view(UpdateCheckState.Done(release), Config(updateLastCheckMs = 5, updateLatest = "1.1.0"))
        assertEquals(UpdateView(UpdateView.Kind.AVAILABLE, "1.1.0", release.apkUrl, release.pageUrl, 5), v)
        // a release without an APK: its page
        val noApk = view(UpdateCheckState.Done(release.copy(apkUrl = "")))
        assertEquals(release.pageUrl, noApk.downloadUrl)
    }

    @Test
    fun sameVersionIsUpToDate() {
        val v = view(UpdateCheckState.Done(release.copy(version = "1.0.0")), Config(updateLastCheckMs = 5, updateLatest = "1.0.0"))
        assertEquals(UpdateView(UpdateView.Kind.LATEST, lastCheckMs = 5), v)
    }

    @Test
    fun anEarlierCheckIsRemembered() {
        val v = view(UpdateCheckState.Idle, Config(updateLastCheckMs = 5, updateLatest = "1.2.0"))
        assertEquals(UpdateView(UpdateView.Kind.AVAILABLE, "1.2.0", releases, releases, 5), v)
        // even when this check failed
        assertEquals(UpdateView.Kind.AVAILABLE, view(UpdateCheckState.Failed(UpdateResult.Reason.OFFLINE), Config(updateLatest = "1.2.0")).kind)
    }

    @Test
    fun failedCheck() {
        val v = view(UpdateCheckState.Failed(UpdateResult.Reason.RATE_LIMITED), Config(updateLastCheckMs = 5))
        assertEquals(UpdateView(UpdateView.Kind.FAILED, lastCheckMs = 5, failure = UpdateResult.Reason.RATE_LIMITED), v)
    }

    @Test
    fun onlyAnUnreachableGitHubIsNoConnection() {
        assertEquals(StatusLine(UiText(R.string.upd_failed), Severity.ERROR), UpdateView.failureLine(UpdateResult.Reason.OFFLINE))
        // GitHub answered: its rate limit (60 an hour per IP address, shared behind carrier NAT), no release yet
        assertEquals(
            StatusLine(UiText(R.string.upd_rate_limited), Severity.WARNING),
            UpdateView.failureLine(UpdateResult.Reason.RATE_LIMITED),
        )
        assertEquals(StatusLine(UiText(R.string.upd_no_release), Severity.INFO), UpdateView.failureLine(UpdateResult.Reason.NO_RELEASE))
        assertEquals(StatusLine(UiText(R.string.upd_bad_answer), Severity.ERROR), UpdateView.failureLine(UpdateResult.Reason.BAD_ANSWER))
        assertEquals(
            UpdateResult.Reason.entries.size,
            UpdateResult.Reason.entries.map { UpdateView.failureLine(it).text }.toSet().size,
        )
    }
}
