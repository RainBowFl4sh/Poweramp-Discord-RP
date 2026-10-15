package io.github.rainbowfl4sh.powerampdiscordrp.core.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Minimal logging sink shared by all components. Messages are English (they end up in bug reports). */
fun interface Logger {
    fun log(message: String)

    companion object {
        val NONE = Logger { }
    }
}

/** Keeps the last [capacity] lines for the "recent log" on the Advanced tab; optionally forwards them (e.g. to logcat). */
class RingLog(
    private val capacity: Int = 300,
    private val clock: Clock = Clock.System,
    private val forward: Logger = Logger.NONE,
) : Logger {
    private val format = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    override fun log(message: String) {
        forward.log(message)
        val line = synchronized(format) { format.format(Date(clock.wallMs())) } + "  " + message
        while (true) {
            val old = _lines.value
            val new = if (old.size >= capacity) old.drop(old.size - capacity + 1) + line else old + line
            if (_lines.compareAndSet(old, new)) return
        }
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
