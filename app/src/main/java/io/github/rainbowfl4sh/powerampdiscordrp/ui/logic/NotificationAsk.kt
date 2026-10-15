package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

/**
 * Android 13's notification permission (the status notification of the presence service, the "background start
 * blocked" alert): when to ask, and what a "denied" answer means. With targetSdk 33+ Android never asks by itself.
 */
object NotificationAsk {
    /** Android 13 (API 33) made posting notifications a runtime permission. */
    fun isRuntimePermission(sdk: Int): Boolean = sdk >= 33

    /** On the first start: asked once per installation while not allowed. */
    fun askOnStart(sdk: Int, granted: Boolean, askedBefore: Boolean): Boolean =
        isRuntimePermission(sdk) && !granted && !askedBefore

    /**
     * After a request: true if only the app's notification settings can allow notifications - Android answered
     * "denied" without showing its dialog (the user denied twice before, Android 11+).
     *
     * Android gives no rationale before the first request, after a dialog that was only closed (Back, a tap outside)
     * and once the dialog is no longer shown; so the dialog counts as not shown only if the app asked before and there
     * was no rationale before and after. A "no" given in the dialog is respected: the first one leaves a rationale, the
     * second one turns it from true to false.
     *
     * Closing the dialog twice in a row looks the same by these flags, so the answer must also have come back faster than
     * a person closes a dialog ([answerMs] < [NO_DIALOG_MAX_MS]): Android's answer without a dialog takes a few hundred
     * milliseconds at most.
     */
    fun openSettingsAfter(
        granted: Boolean,
        rationaleBefore: Boolean,
        rationaleAfter: Boolean,
        askedBefore: Boolean,
        answerMs: Long,
    ): Boolean = !granted && askedBefore && !rationaleBefore && !rationaleAfter && answerMs in 0 until NO_DIALOG_MAX_MS

    /** Longest time Android takes to answer a permission request without showing its dialog. */
    const val NO_DIALOG_MAX_MS = 700L
}
