package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.ReleaseInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.UpdateResult

/** When the automatic update check runs (like the AIMP plugin: every start / daily / weekly / monthly). */
object UpdateSchedule {
    const val DAY_MS = 86_400_000L

    fun intervalMs(frequency: Int): Long = when (frequency) {
        Config.UPDATE_EVERY_START -> 0
        Config.UPDATE_WEEKLY -> 7 * DAY_MS
        Config.UPDATE_MONTHLY -> 30 * DAY_MS
        else -> DAY_MS
    }

    /**
     * True if the automatic check is due at app start.
     * @param checkedThisSession a check already ran since the process started ("every start" = once per start)
     */
    fun isDue(c: Config, nowWallMs: Long, checkedThisSession: Boolean): Boolean {
        if (!c.updateCheck) return false
        if (c.updateFrequency == Config.UPDATE_EVERY_START) return !checkedThisSession
        val last = c.updateLastCheckMs
        if (last <= 0 || last > nowWallMs) return true   // never checked, or the clock was set back
        return nowWallMs - last >= intervalMs(c.updateFrequency)
    }
}

/** Progress of the update check started from this screen (the result of earlier checks is in the settings). */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class Failed(val reason: UpdateResult.Reason) : UpdateCheckState
    data class Done(val release: ReleaseInfo) : UpdateCheckState
}

/** What the About tab shows about updates. */
data class UpdateView(
    val kind: Kind,
    val version: String = "",
    val downloadUrl: String = "",   // the APK if the release has one, else the release page
    val pageUrl: String = "",
    val lastCheckMs: Long = 0,
    val failure: UpdateResult.Reason = UpdateResult.Reason.OFFLINE,   // why, with Kind.FAILED
) {
    enum class Kind { NEVER, CHECKING, LATEST, AVAILABLE, FAILED }

    companion object {
        /**
         * Why the check found no release. Only a GitHub that could not be reached is "no connection": its rate limit
         * (often hit behind a carrier's shared address) and a project without a release are answers.
         */
        fun failureLine(reason: UpdateResult.Reason): StatusLine = when (reason) {
            UpdateResult.Reason.OFFLINE -> StatusLine(UiText(R.string.upd_failed), Severity.ERROR)
            UpdateResult.Reason.RATE_LIMITED -> StatusLine(UiText(R.string.upd_rate_limited), Severity.WARNING)
            UpdateResult.Reason.NO_RELEASE -> StatusLine(UiText(R.string.upd_no_release), Severity.INFO)
            UpdateResult.Reason.BAD_ANSWER -> StatusLine(UiText(R.string.upd_bad_answer), Severity.ERROR)
        }

        /**
         * @param releasesUrl page of the newest release, used when only the version of an earlier check is known
         * @param isNewer     UpdateChecker.isNewer (candidate, current)
         */
        fun of(
            state: UpdateCheckState,
            c: Config,
            currentVersion: String,
            releasesUrl: String,
            isNewer: (String, String) -> Boolean,
        ): UpdateView {
            if (state is UpdateCheckState.Checking) return UpdateView(Kind.CHECKING, lastCheckMs = c.updateLastCheckMs)
            if (state is UpdateCheckState.Done && isNewer(state.release.version, currentVersion)) {
                val r = state.release
                return UpdateView(
                    Kind.AVAILABLE, r.version, r.apkUrl.ifBlank { r.pageUrl.ifBlank { releasesUrl } },
                    r.pageUrl.ifBlank { releasesUrl }, c.updateLastCheckMs,
                )
            }
            val stored = c.updateLatest.trim()
            if (stored.isNotEmpty() && isNewer(stored, currentVersion)) {   // found by an earlier check
                return UpdateView(Kind.AVAILABLE, stored, releasesUrl, releasesUrl, c.updateLastCheckMs)
            }
            if (state is UpdateCheckState.Failed) {
                return UpdateView(Kind.FAILED, lastCheckMs = c.updateLastCheckMs, failure = state.reason)
            }
            return if (c.updateLastCheckMs > 0) UpdateView(Kind.LATEST, lastCheckMs = c.updateLastCheckMs)
            else UpdateView(Kind.NEVER)
        }
    }
}
