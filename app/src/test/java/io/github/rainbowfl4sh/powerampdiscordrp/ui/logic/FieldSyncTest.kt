package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The setting reaches a text field a moment after the field changed it; meanwhile the user may have typed on.
 * [field] plays the field: what it shows, and the setting it sends with every keystroke.
 */
class FieldSyncTest {
    private val sync = FieldSync<String>()
    private var field = ""

    private fun type(text: String) {
        field = text
        sync.sent(text)
    }

    /** The screen brings the setting [value]; the field takes it if it was changed elsewhere. */
    private fun arrives(value: String): Boolean = sync.changedElsewhere(value, field).also { if (it) field = value }

    @Test
    fun aLateEchoDoesNotTakeBackNewerKeystrokes() {
        type("a")
        type("ab")
        // the screen still composes "a" (its effect runs after the next keystroke): the old code reset the field to "a"
        assertFalse(arrives("a"))
        assertEquals("ab", field)
        type("abc")
        assertFalse(arrives("ab"))
        assertFalse(arrives("abc"))
        assertEquals("abc", field)
    }

    @Test
    fun skippedEchoes() {
        // the StateFlow only delivers the newest value: "a" and "ab" never come back
        type("a")
        type("ab")
        type("abc")
        assertFalse(arrives("abc"))
        assertEquals("abc", field)
    }

    @Test
    fun aChangeElsewhereReplacesTheText() {
        type("%title%")
        assertFalse(arrives("%title%"))
        // import / reset
        assertTrue(arrives("%artist% - %title%"))
        assertEquals("%artist% - %title%", field)
        // also while an echo of the field is still on its way
        type("x")
        assertTrue(arrives("imported"))
        assertEquals("imported", field)
        // the echo of "x" can no longer arrive: the setting is the imported text now
        type("importedy")
        assertFalse(arrives("importedy"))
    }

    @Test
    fun typingBackToAnEarlierText() {
        type("a")
        type("ab")
        type("a")   // backspace
        assertFalse(arrives("a"))   // in step with the field, whichever "a" it was
        assertEquals("a", field)
        assertFalse(arrives("a"))
    }

    @Test
    fun latestIsTheNewestValueOnItsWay() {
        val numbers = FieldSync<Int>()
        assertEquals(15, numbers.latest(15))
        numbers.sent(1)
        numbers.sent(12)
        // the setting composed is still 15: a field left with invalid input goes back to 12, not 15
        assertEquals(12, numbers.latest(15))
        // a number field being typed has no valid value: an echo does not reset it
        assertFalse(numbers.changedElsewhere(1, null))
        assertFalse(numbers.changedElsewhere(12, 12))
        assertEquals(30, numbers.latest(30))
        assertTrue(numbers.changedElsewhere(30, null))   // reset elsewhere while the field is empty
    }
}
