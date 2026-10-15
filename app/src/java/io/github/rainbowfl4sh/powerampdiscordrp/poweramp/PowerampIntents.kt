package io.github.rainbowfl4sh.powerampdiscordrp.poweramp

import android.os.Bundle
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.poweramp.Poweramp.Track

/**
 * Reads Poweramp's broadcast extras. Lenient on purpose: the receiver is exported, so the extras may come from any app
 * and in any shape - wrong types read as "missing" instead of throwing.
 */
object PowerampIntents {
    /** The track of a TRACK_CHANGED broadcast: the "track" bundle, or the same keys at the top level. null = none. */
    fun trackOf(extras: Bundle): TrackInfo? {
        val bundle = extras.getBundle(Poweramp.EXTRA_TRACK)
            ?: extras.takeIf { it.containsKey(Track.PATH) || it.containsKey(Track.TITLE) }
            ?: return null
        return trackFromBundle(bundle)
    }

    fun trackFromBundle(b: Bundle): TrackInfo {
        val path = b.string(Track.PATH)
        val fileType = b.int(Track.FILE_TYPE) ?: Track.FILE_TYPE_UNKNOWN
        // durMs exists since build 948; "dur" (seconds) is the fallback for anything older
        val durationMs = b.long(Track.DURATION_MS)?.takeIf { it > 0 }
            ?: ((b.long(Track.DURATION) ?: 0L) * 1000)
        return TrackInfo(
            id = b.long(Track.ID) ?: 0,
            realId = b.long(Track.REAL_ID) ?: 0,
            title = b.string(Track.TITLE),
            artist = b.string(Track.ARTIST),
            album = b.string(Track.ALBUM),
            path = path,
            durationMs = durationMs.coerceAtLeast(0),
            fileType = fileType,
            isStream = isStream(fileType, path),
            posInList = (b.int(Track.POS_IN_LIST) ?: 0).coerceAtLeast(0),
            listSize = (b.int(Track.LIST_SIZE) ?: 0).coerceAtLeast(0),
            sampleRate = b.int(Track.SAMPLE_RATE) ?: 0,
            channels = b.int(Track.CHANNELS) ?: 0,
            bitsPerSample = b.int(Track.BITS_PER_SAMPLE) ?: 0,
            bitRate = ((b.long(Track.BIT_RATE) ?: 0L).coerceAtLeast(0) + 500).div(1000).toInt(),
            codec = b.string(Track.CODEC),
            rating = (b.int(Track.RATING) ?: 0).coerceIn(0, 5),
        )
    }

    /** Internet radio / streams: Poweramp's stream file type, or a plain http(s) URL as path. */
    fun isStream(fileType: Int, path: String): Boolean =
        fileType == Track.FILE_TYPE_STREAM ||
            path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true)

    /** Play state of a STATUS_CHANGED broadcast; null if it carries none. */
    fun playStateOf(extras: Bundle): PlayState? {
        extras.int(Poweramp.EXTRA_STATE)?.let { state ->
            return when (state) {
                Poweramp.STATE_PLAYING -> PlayState.PLAYING
                Poweramp.STATE_PAUSED -> PlayState.PAUSED
                else -> PlayState.STOPPED   // STATE_STOPPED and STATE_NO_STATE (Poweramp still loading)
            }
        }
        // Without "state" (never seen from build 700 on): the deprecated status / paused extras
        val status = extras.int(Poweramp.EXTRA_STATUS)
        val paused = extras.value(Poweramp.EXTRA_PAUSED) as? Boolean
        return when {
            status == Poweramp.STATUS_STOPPED -> PlayState.STOPPED
            paused != null -> if (paused) PlayState.PAUSED else PlayState.PLAYING
            status == Poweramp.STATUS_PLAYING -> PlayState.PLAYING
            status == Poweramp.STATUS_PAUSED -> PlayState.PAUSED
            else -> null
        }
    }

    /** In-track position in milliseconds ("pos" is in seconds); null if the broadcast has none. */
    fun positionMs(extras: Bundle): Long? = extras.long(Poweramp.EXTRA_POS)?.takeIf { it >= 0 }?.times(1000)

    /** When Poweramp sent the broadcast (unix ms), 0 = unknown. */
    fun timestamp(extras: Bundle): Long = extras.long(Poweramp.EXTRA_TS)?.takeIf { it > 0 } ?: 0

    @Suppress("DEPRECATION")   // Bundle.get(): the only way to read a value without knowing its exact type
    private fun Bundle.value(key: String): Any? = try {
        get(key)
    } catch (_: RuntimeException) {
        null   // unparcelable foreign data
    }

    private fun Bundle.long(key: String): Long? = when (val v = value(key)) {
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull()
        else -> null
    }

    private fun Bundle.int(key: String): Int? =
        long(key)?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt()

    private fun Bundle.string(key: String): String = (value(key) as? CharSequence)?.toString().orEmpty()
}
