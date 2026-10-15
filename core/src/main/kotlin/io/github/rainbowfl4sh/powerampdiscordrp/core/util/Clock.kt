package io.github.rainbowfl4sh.powerampdiscordrp.core.util

/** Time source - replaced by a fake in tests. */
interface Clock {
    /** Monotonic milliseconds (Android: SystemClock.elapsedRealtime). Only differences are meaningful. */
    fun nowMs(): Long

    /** Unix time in milliseconds (for Discord timestamps and cache ages). */
    fun wallMs(): Long

    object System : Clock {
        override fun nowMs(): Long = java.lang.System.nanoTime() / 1_000_000
        override fun wallMs(): Long = java.lang.System.currentTimeMillis()
    }
}
