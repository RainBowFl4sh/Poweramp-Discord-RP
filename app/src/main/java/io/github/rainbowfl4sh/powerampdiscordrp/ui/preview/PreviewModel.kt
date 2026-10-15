package io.github.rainbowfl4sh.powerampdiscordrp.ui.preview

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.ActivityTexts
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.Hidden
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.PresenceTexts
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.StatusStrings

/** PresenceTexts.compute - replaced by a fake in tests. */
typealias TextsComputer = (c: Config, s: PlaybackSnapshot, nowMs: Long, pausedForMs: Long, strings: StatusStrings) -> ActivityTexts

/** Localized texts of the example track shown while nothing plays. */
data class ExampleTexts(val title: String, val artist: String, val album: String)

/** Which line Discord shows in the member list ("Listening to ..."). */
enum class MemberLine { APP_NAME, STATE, DETAILS }

/** Everything the live preview draws (port of the AIMP plugin's BuildPreview). */
data class Preview(
    val texts: ActivityTexts,
    val example: Boolean,          // nothing plays: an example track is shown
    val listening: Boolean,        // "Listening to" (activity type 2) or "Playing"
    val progress: Boolean,         // progress bar with both times
    val elapsed: Boolean,          // "01:23 elapsed" instead of a bar (Playing, or the duration is unknown)
    val memberLine: MemberLine,
    val memberText: String,        // text after "Listening to" in the member list ("" = the application's name)
    val smallIcon: Boolean,
) {
    val hidden: Boolean get() = texts.hidden != Hidden.NO
}

object PreviewModel {
    /** Example position / length: 1:23 of 3:45. */
    const val EXAMPLE_POS_MS = 83_000L
    const val EXAMPLE_DUR_MS = 225_000L

    /**
     * Since when the example track is current, for the text rotation ("||"): a fixed time, so its alternatives take turns
     * on the clock (the example is built anew every second with the stamp "now" - as anchor that would show the first
     * alternative forever).
     */
    const val EXAMPLE_TRACK_SINCE_MS = 0L

    /** A playing example track (frozen at 1:23: its stamp is "now"; its texts rotate from [EXAMPLE_TRACK_SINCE_MS]). */
    fun exampleSnapshot(t: ExampleTexts, nowMs: Long): PlaybackSnapshot = PlaybackSnapshot(
        state = PlayState.PLAYING,
        track = TrackInfo(
            title = t.title,
            artist = t.artist,
            album = t.album,
            path = "/storage/emulated/0/Music/${t.artist} - ${t.title}.flac",
            durationMs = EXAMPLE_DUR_MS,
            fileType = 0,
            posInList = 4,
            listSize = 12,
            sampleRate = 44_100,
            channels = 2,
            bitsPerSample = 16,
            bitRate = 920,
            codec = "FLAC",
            rating = 4,
        ),
        positionMs = EXAMPLE_POS_MS,
        stampMs = nowMs,
        trackSinceMs = EXAMPLE_TRACK_SINCE_MS,
    )

    /** True if the preview has to show the example instead of what Poweramp reported. */
    fun needsExample(s: PlaybackSnapshot): Boolean = s.state == PlayState.STOPPED || s.track.isEmpty

    /**
     * Preview of [c] (the CURRENT edits) for [live] (what Poweramp plays) at [nowMs] (monotonic, like the snapshot's stamp).
     * [live] is the engine's snapshot: its [PlaybackSnapshot.trackSinceMs] makes rotating texts ("||") show the alternative
     * Discord gets.
     */
    fun build(
        c: Config,
        live: PlaybackSnapshot,
        nowMs: Long,
        example: ExampleTexts,
        strings: StatusStrings,
        compute: TextsComputer = PresenceTexts::compute,
    ): Preview {
        val useExample = needsExample(live)
        val s = if (useExample) exampleSnapshot(example, nowMs) else live
        val pausedFor = pausedForMs(s, nowMs)
        val t = compute(c, s, nowMs, pausedFor, strings)

        val listening = c.activityType != Config.ACTIVITY_PLAYING
        val timed = c.showTimestamps && t.playing
        val progress = timed && listening && t.durMs > 0
        val line = when (c.statusDisplay) {
            Config.STATUS_DISPLAY_STATE -> MemberLine.STATE
            Config.STATUS_DISPLAY_DETAILS -> MemberLine.DETAILS
            else -> MemberLine.APP_NAME
        }
        // an empty line makes Discord fall back to the application's name
        val memberText = when (line) {
            MemberLine.STATE -> t.state
            MemberLine.DETAILS -> t.details
            MemberLine.APP_NAME -> ""
        }
        return Preview(
            texts = t,
            example = useExample,
            listening = listening,
            progress = progress,
            elapsed = timed && !progress,
            memberLine = line,
            memberText = memberText,
            smallIcon = c.showSmallIcon,
        )
    }

    /**
     * How long [s] has been paused at [nowMs], for "remove the presence after N minutes": from the snapshot's stamp,
     * like the engine and PowerampBridge count it (the pause, or a later seek / track change while paused - Poweramp's
     * broadcasts re-stamp the snapshot). No stamp: just paused (the engine counts from when it heard of the pause then).
     */
    fun pausedForMs(s: PlaybackSnapshot, nowMs: Long): Long =
        if (s.state != PlayState.PAUSED || s.stampMs == 0L) 0 else (nowMs - s.stampMs).coerceAtLeast(0)

    /**
     * [p] without the playing position (0): what stays the same from one second to the next while a track plays - unless
     * a template shows the position (%pos%, %bar%, %percent%). The live preview redraws its card only when this changes.
     */
    fun withoutPosition(p: Preview): Preview = if (p.texts.posMs == 0L) p else p.copy(texts = p.texts.copy(posMs = 0))

    /** Fraction of the progress bar (0..1). */
    fun fraction(posMs: Long, durMs: Long): Float =
        if (durMs <= 0) 0f else (posMs.toDouble() / durMs).coerceIn(0.0, 1.0).toFloat()
}
