package io.github.rainbowfl4sh.powerampdiscordrp.core.model

enum class PlayState { STOPPED, PAUSED, PLAYING }

/**
 * What Poweramp is doing right now.
 * [positionMs] was the in-track position at [stampMs] (time of [io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock.nowMs]);
 * while playing, the current position is positionMs + (now - stampMs).
 * [trackSinceMs] (same clock) is when [track] became the current track: the text rotation ("||") starts there with the
 * first alternative. The presence engine sets it (PresenceEngine.submit); pause, resume and seeks keep it.
 */
data class PlaybackSnapshot(
    val state: PlayState = PlayState.STOPPED,
    val track: TrackInfo = TrackInfo(),
    val positionMs: Long = 0,
    val stampMs: Long = 0,
    val trackSinceMs: Long = 0,
)
