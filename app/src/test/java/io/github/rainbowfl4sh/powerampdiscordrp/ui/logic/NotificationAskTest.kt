package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAskTest {
    @Test
    fun askedOnceOnTheFirstStartFromAndroid13() {
        assertTrue(NotificationAsk.askOnStart(sdk = 33, granted = false, askedBefore = false))
        assertTrue(NotificationAsk.askOnStart(sdk = 36, granted = false, askedBefore = false))
        assertFalse(NotificationAsk.askOnStart(sdk = 33, granted = false, askedBefore = true))
        assertFalse(NotificationAsk.askOnStart(sdk = 33, granted = true, askedBefore = false))
        // before Android 13 notifications are allowed without asking
        assertFalse(NotificationAsk.askOnStart(sdk = 32, granted = false, askedBefore = false))
    }

    @Test
    fun aClosedDialogDoesNotOpenTheSettings() {
        // the first request, closed with Back / a tap outside: Android gives no rationale, before and after
        assertFalse(NotificationAsk.openSettingsAfter(granted = false, rationaleBefore = false, rationaleAfter = false, askedBefore = false, answerMs = 100))
        // closed again after a "no": the rationale stays
        assertFalse(NotificationAsk.openSettingsAfter(granted = false, rationaleBefore = true, rationaleAfter = true, askedBefore = true, answerMs = 100))
    }

    @Test
    fun aNoInTheDialogIsRespected() {
        // first "no": now a rationale
        assertFalse(NotificationAsk.openSettingsAfter(granted = false, rationaleBefore = false, rationaleAfter = true, askedBefore = false, answerMs = 100))
        // second "no" (Android 11+ asks no more): the rationale turns false - still the user's answer right now
        assertFalse(NotificationAsk.openSettingsAfter(granted = false, rationaleBefore = true, rationaleAfter = false, askedBefore = true, answerMs = 100))
    }

    @Test
    fun closingTheDialogTwiceDoesNotOpenTheSettings() {
        // the first-start dialog and then the checklist's dialog, both closed with Back: same flags as "no dialog", but
        // a person needs longer than Android's instant answer
        assertFalse(NotificationAsk.openSettingsAfter(granted = false, rationaleBefore = false, rationaleAfter = false, askedBefore = true, answerMs = 1_500))
        assertFalse(NotificationAsk.openSettingsAfter(granted = false, rationaleBefore = false, rationaleAfter = false, askedBefore = true, answerMs = NotificationAsk.NO_DIALOG_MAX_MS))
        // a clock oddity (negative time) never opens the settings
        assertFalse(NotificationAsk.openSettingsAfter(granted = false, rationaleBefore = false, rationaleAfter = false, askedBefore = true, answerMs = -1))
    }

    @Test
    fun noDialogAnyMoreOpensTheSettings() {
        assertTrue(NotificationAsk.openSettingsAfter(granted = false, rationaleBefore = false, rationaleAfter = false, askedBefore = true, answerMs = 100))
        assertFalse(NotificationAsk.openSettingsAfter(granted = true, rationaleBefore = false, rationaleAfter = false, askedBefore = true, answerMs = 100))
    }
}
