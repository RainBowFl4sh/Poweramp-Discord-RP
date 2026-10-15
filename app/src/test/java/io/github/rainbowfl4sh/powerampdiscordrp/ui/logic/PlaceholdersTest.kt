package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.TextRotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholdersTest {
    @Test
    fun insertsAtTheCursorAndMovesItBehindTheToken() {
        val e = Placeholders.insert("by ", 3, 3, "%artist%")
        assertEquals("by %artist%", e.text)
        assertEquals(11, e.selectionStart)
        assertEquals(11, e.selectionEnd)
    }

    @Test
    fun insertsInTheMiddle() {
        val e = Placeholders.insert("%title%  %album%", 8, 8, "%artist%")
        assertEquals("%title% %artist% %album%", e.text)
        assertEquals(16, e.selectionStart)
    }

    @Test
    fun replacesTheSelection() {
        val e = Placeholders.insert("Song by Someone", 8, 15, "%artist%")
        assertEquals("Song by %artist%", e.text)
        assertEquals(16, e.selectionStart)
    }

    @Test
    fun reversedSelectionIsTheSameRange() {
        val e = Placeholders.insert("Song by Someone", 15, 8, "%artist%")
        assertEquals("Song by %artist%", e.text)
    }

    @Test
    fun selectionOutsideTheTextIsClamped() {
        assertEquals("ab%title%", Placeholders.insert("ab", 10, 12, "%title%").text)
        assertEquals("%title%ab", Placeholders.insert("ab", -3, -1, "%title%").text)
        assertEquals("%title%", Placeholders.insert("", 0, 0, "%title%").text)
    }

    @Test
    fun theSeparatorGetsASpaceOnEachSide() {
        // the cursor at the end of "%artist%": ready for the next alternative
        val e = Placeholders.insertSeparator("%artist%", 8, 8)
        assertEquals("%artist% || ", e.text)
        assertEquals(12, e.selectionStart)
        assertEquals(12, e.selectionEnd)
        // the separator chip and a placeholder chip make the user's example
        val next = Placeholders.insert(e.text, e.selectionStart, e.selectionEnd, "%title%")
        assertEquals("%artist% || %title%", next.text)
        assertEquals(listOf("%artist%", "%title%"), TextRotation.alternatives(next.text).map { it.trim() })
    }

    @Test
    fun theSeparatorDoesNotDoubleSpaces() {
        // a space before the cursor already
        assertEquals("%artist% || ", Placeholders.insertSeparator("%artist% ", 9, 9).text)
        // between two placeholders separated by a space: that space follows the separator
        val between = Placeholders.insertSeparator("%artist% %album%", 8, 8)
        assertEquals("%artist% || %album%", between.text)
        assertEquals(11, between.selectionStart)
        // right before a placeholder: a space on both sides
        assertEquals("%artist% || %album%", Placeholders.insertSeparator("%artist%%album%", 8, 8).text)
        // at the start nothing is needed before it
        assertEquals("|| %title%", Placeholders.insertSeparator("%title%", 0, 0).text)
        assertEquals("|| ", Placeholders.insertSeparator("", 0, 0).text)
    }

    @Test
    fun theSeparatorReplacesTheSelection() {
        assertEquals("%artist% || %title%", Placeholders.insertSeparator("%artist% - %title%", 8, 11).text)
        // reversed and outside the text: clamped like insert()
        assertEquals("%artist% || %title%", Placeholders.insertSeparator("%artist% - %title%", 11, 8).text)
        assertEquals("ab || ", Placeholders.insertSeparator("ab", 10, 12).text)
    }

    @Test
    fun everyPlaceholderOfPresenceTextsIsOffered() {
        val names = Placeholders.ALL.map { it.name }
        val expected = listOf(
            "artist", "title", "album", "filename", "ext", "pos", "dur", "percent", "bar", "status",
            "codec", "bitrate", "samplerate", "bits", "track", "listsize", "rating",
        )
        assertEquals(expected.sorted(), names.sorted())
        assertEquals(names.size, names.toSet().size)
        assertTrue(Placeholders.ALL.all { it.token == "%${it.name}%" })
        assertEquals(names.size, Placeholders.ALL.map { it.description }.toSet().size)
    }
}
