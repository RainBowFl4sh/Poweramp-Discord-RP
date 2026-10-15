package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import androidx.annotation.StringRes
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.TextRotation

/** A placeholder of the text templates and what it stands for (shown in the help list of the Display tab). */
data class Placeholder(val name: String, @StringRes val description: Int) {
    val token: String get() = "%$name%"
}

/** Result of an insertion: the new text and where the cursor / selection is afterwards. */
data class TextEdit(val text: String, val selectionStart: Int, val selectionEnd: Int = selectionStart)

object Placeholders {
    /** Every placeholder PresenceTexts understands, in the order of the chips (most used first). */
    val ALL: List<Placeholder> = listOf(
        Placeholder("title", R.string.ph_title),
        Placeholder("artist", R.string.ph_artist),
        Placeholder("album", R.string.ph_album),
        Placeholder("filename", R.string.ph_filename),
        Placeholder("ext", R.string.ph_ext),
        Placeholder("pos", R.string.ph_pos),
        Placeholder("dur", R.string.ph_dur),
        Placeholder("percent", R.string.ph_percent),
        Placeholder("bar", R.string.ph_bar),
        Placeholder("status", R.string.ph_status),
        Placeholder("codec", R.string.ph_codec),
        Placeholder("bitrate", R.string.ph_bitrate),
        Placeholder("samplerate", R.string.ph_samplerate),
        Placeholder("bits", R.string.ph_bits),
        Placeholder("track", R.string.ph_track),
        Placeholder("listsize", R.string.ph_listsize),
        Placeholder("rating", R.string.ph_rating),
    )

    /**
     * Replaces the selection ([selectionStart]..[selectionEnd], either order, clamped to the text) with [token];
     * the cursor ends up right after the inserted token.
     */
    fun insert(text: String, selectionStart: Int, selectionEnd: Int, token: String): TextEdit {
        val a = selectionStart.coerceIn(0, text.length)
        val b = selectionEnd.coerceIn(0, text.length)
        val from = minOf(a, b)
        val to = maxOf(a, b)
        val result = text.substring(0, from) + token + text.substring(to)
        return TextEdit(result, from + token.length)
    }

    /**
     * Inserts the rotation separator "||" ([TextRotation]) like [insert], with a space on each side unless the text has
     * one there already (none before it at the start): "%artist%" with the cursor at the end -> "%artist% || ".
     */
    fun insertSeparator(text: String, selectionStart: Int, selectionEnd: Int): TextEdit {
        val from = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val to = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val before = if (from > 0 && !text[from - 1].isWhitespace()) " " else ""
        val after = if (to < text.length && text[to].isWhitespace()) "" else " "
        return insert(text, selectionStart, selectionEnd, before + TextRotation.SEPARATOR + after)
    }
}
