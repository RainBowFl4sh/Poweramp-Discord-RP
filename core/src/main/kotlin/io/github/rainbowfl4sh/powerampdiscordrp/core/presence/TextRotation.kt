package io.github.rainbowfl4sh.powerampdiscordrp.core.presence

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config

/**
 * Text rotation: "||" separates alternatives in a text template (details, state, largeText, smallText), e.g.
 * "%artist% || %title%". The alternatives take turns every [Config.effectiveRotationSeconds], starting with the first one
 * when a track becomes current ([io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot.trackSinceMs]).
 *
 * One step counter for all fields: at step k a field shows its k-th non-empty alternative (modulo their number), so all
 * fields change at the same moments and fields with the same number of alternatives stay paired (with 2 and 3 the pairs
 * drift). Alternatives that come out empty for a track (a missing tag) are skipped. A single '|' is ordinary text; the
 * title link is never rotated.
 */
object TextRotation {
    /** Separates the alternatives of a text template. */
    const val SEPARATOR = "||"

    /** The parts of [template] between the separators as they are (not expanded, not trimmed); without one: [template]. */
    fun alternatives(template: String): List<String> =
        if (SEPARATOR in template) template.split(SEPARATOR) else listOf(template)

    /** True if [template] has two or more alternatives that are not blank: only then can its text change over time. */
    fun rotates(template: String): Boolean =
        SEPARATOR in template && alternatives(template).count { it.isNotBlank() } >= 2

    /** Length of one rotation step in ms (the configured interval, clamped). */
    fun intervalMs(c: Config): Long = c.effectiveRotationSeconds() * 1000L

    /** Step at [nowMs] for a track current since [sinceMs]: 0 during the first interval (and before [sinceMs]). */
    fun step(sinceMs: Long, nowMs: Long, intervalMs: Long): Long =
        (nowMs - sinceMs).coerceAtLeast(0) / intervalMs.coerceAtLeast(1)

    /** When the step after the one at [nowMs] begins. */
    fun nextStepAt(sinceMs: Long, nowMs: Long, intervalMs: Long): Long =
        sinceMs + (step(sinceMs, nowMs, intervalMs) + 1) * intervalMs.coerceAtLeast(1)

    /** The value shown at [step] among the non-empty [values]; "" if all are empty. */
    fun pick(values: List<String>, step: Long): String {
        val shown = values.filter { it.isNotEmpty() }
        return if (shown.isEmpty()) "" else shown[(step.coerceAtLeast(0) % shown.size).toInt()]
    }
}
