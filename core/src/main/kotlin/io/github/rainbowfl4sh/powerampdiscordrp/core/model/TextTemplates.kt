package io.github.rainbowfl4sh.powerampdiscordrp.core.model

/** The four text templates of a presence (first / second line, cover tooltip, small icon tooltip). */
data class TextTemplates(
    val details: String,
    val state: String,
    val largeText: String,
    val smallText: String,
) {
    /** These templates where they are not blank, [base]'s elsewhere. */
    fun over(base: TextTemplates) = TextTemplates(
        details.ifBlank { base.details },
        state.ifBlank { base.state },
        largeText.ifBlank { base.largeText },
        smallText.ifBlank { base.smallText },
    )
}
