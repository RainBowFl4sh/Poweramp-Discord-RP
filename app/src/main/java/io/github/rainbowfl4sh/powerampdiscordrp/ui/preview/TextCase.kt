package io.github.rainbowfl4sh.powerampdiscordrp.ui.preview

import androidx.annotation.StringRes
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot

/** Which texts are edited on the Display tab - and shown by the live preview: playback, while paused, radio / streams. */
enum class TextCase(@StringRes val label: Int) {
    PLAYING(R.string.disp_texts_for_playing),
    PAUSED(R.string.disp_texts_for_paused),
    STREAM(R.string.disp_texts_for_stream),
    ;

    /**
     * The track as the preview shows it while these texts are edited: paused (from now on) or a stream. The settings that
     * would hide it then (hide while paused, the paused timeout, hide streams) are left out, so the texts can be seen.
     */
    fun apply(c: Config, s: PlaybackSnapshot, nowMs: Long): Pair<Config, PlaybackSnapshot> = when (this) {
        PLAYING -> c to s
        PAUSED -> c.copy(pausedBehavior = Config.PAUSED_SHOW, clearAfterPausedMin = 0) to
            if (s.state == PlayState.PAUSED) s else s.copy(state = PlayState.PAUSED, stampMs = nowMs)
        STREAM -> c.copy(hideStreams = false) to s.copy(track = s.track.copy(isStream = true))
    }
}
