package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

/**
 * Keeps an input field in step with the setting it edits. The setting reaches the screen a moment after the field
 * changed it (editor draft -> StateFlow -> Compose state, then an effect), so the screen may still bring a value the
 * field itself sent earlier while newer keystrokes are in the field already. Such an echo must not replace them (the
 * cursor would jump to the end and a keystroke in between would be lost). Only a value the field did not send - the
 * setting was changed elsewhere (import, reset) - replaces what the field shows.
 *
 * Works without any assumption about when the echo arrives. Must be used from one thread (the main thread).
 */
class FieldSync<T> {
    private val sent = ArrayDeque<T>()   // values the field sent that did not come back yet, oldest first

    /** The field sent [value] to the setting (the user typed). */
    fun sent(value: T) {
        if (sent.lastOrNull() == value) return
        sent.addLast(value)
        if (sent.size > MAX_PENDING) sent.removeFirst()
    }

    /**
     * The setting is [value] now; the field shows [shown] (null = nothing valid, e.g. a number being typed).
     * @return true if the field has to show [value]: it was changed elsewhere
     */
    fun changedElsewhere(value: T, shown: T?): Boolean {
        if (value == shown) {   // in step: everything sent before has arrived (or was overtaken)
            sent.clear()
            return false
        }
        val echo = sent.indexOf(value)
        if (echo >= 0) {   // an earlier value of the field itself: the newer ones are still on their way
            repeat(echo + 1) { sent.removeFirst() }
            return false
        }
        sent.clear()
        return true
    }

    /** The setting as far as the field knows: the newest value it sent that did not come back yet, else [value]. */
    fun latest(value: T): T = sent.lastOrNull() ?: value

    private companion object {
        const val MAX_PENDING = 256
    }
}
