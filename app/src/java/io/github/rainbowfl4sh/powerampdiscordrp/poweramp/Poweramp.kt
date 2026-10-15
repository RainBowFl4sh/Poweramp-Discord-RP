package io.github.rainbowfl4sh.powerampdiscordrp.poweramp

import android.net.Uri

/**
 * The parts of the Poweramp API this app uses (names and values from PowerampAPI.java; checked against the broadcasts
 * of Poweramp builds 1025 and 1032).
 */
object Poweramp {
    const val PACKAGE = "com.maxmpz.audioplayer"

    // Sticky broadcasts: only received while this app is in the foreground (activity or foreground service)
    const val ACTION_TRACK_CHANGED = "com.maxmpz.audioplayer.TRACK_CHANGED"
    const val ACTION_STATUS_CHANGED = "com.maxmpz.audioplayer.STATUS_CHANGED"

    // Sent with setPackage() to every app whose MANIFEST receiver matches - also reaches a stopped / background app.
    // Poweramp refreshes the list of these apps when playback (re)starts.
    const val ACTION_TRACK_CHANGED_EXPLICIT = "com.maxmpz.audioplayer.TRACK_CHANGED_EXPLICIT"
    const val ACTION_STATUS_CHANGED_EXPLICIT = "com.maxmpz.audioplayer.STATUS_CHANGED_EXPLICIT"

    /** Normal (non-sticky) broadcast after a seek; extra [EXTRA_POS] only. */
    const val ACTION_TPOS_SYNC = "com.maxmpz.audioplayer.TPOS_SYNC"

    /** Album art provider: content://com.maxmpz.audioplayer.aa/files/<realId> (exported, no permission). */
    const val AA_AUTHORITY = "com.maxmpz.audioplayer.aa"

    // Extras of TRACK_CHANGED / STATUS_CHANGED / TPOS_SYNC
    const val EXTRA_API = "api"
    const val EXTRA_TRACK = "track"         // Bundle (builds 1025 / 1032 also copy its keys to the top level)
    const val EXTRA_POS = "pos"             // int, in-track position in seconds (optional)
    const val EXTRA_TS = "ts"               // long, System.currentTimeMillis() when Poweramp sent the event
    const val EXTRA_STATE = "state"         // int, STATE_*
    const val EXTRA_STATUS = "status"       // int, deprecated: 1 playing, 2 paused, 3 stopped
    const val EXTRA_PAUSED = "paused"       // boolean, true when paused or stopped

    const val STATE_NO_STATE = -1
    const val STATE_STOPPED = 0
    const val STATE_PLAYING = 1
    const val STATE_PAUSED = 2

    const val STATUS_PLAYING = 1
    const val STATUS_PAUSED = 2
    const val STATUS_STOPPED = 3

    /** Keys of the track bundle (PowerampAPI.Track). */
    object Track {
        const val ID = "id"                     // long, may be a playlist entry id
        const val REAL_ID = "realId"            // long, the file id (key of the album art provider)
        const val FILE_TYPE = "fileType"        // int, FILE_TYPE_*
        const val PATH = "path"
        const val TITLE = "title"
        const val ALBUM = "album"
        const val ARTIST = "artist"
        const val DURATION = "dur"              // int seconds
        const val DURATION_MS = "durMs"         // int milliseconds (build 948+)
        const val RATING = "rating"             // int 0..5
        const val POS_IN_LIST = "posInList"     // int, 0-based
        const val LIST_SIZE = "listSize"
        const val SAMPLE_RATE = "sampleRate"    // the audio format keys are only present if the sample rate is known
        const val CHANNELS = "channels"
        const val BITS_PER_SAMPLE = "bitsPerSample"
        const val BIT_RATE = "bitRate"          // int, bit/s (Poweramp itself shows (bitRate + 500) / 1000 kbps)
        const val CODEC = "codec"

        const val FILE_TYPE_UNKNOWN = -1
        const val FILE_TYPE_STREAM = 23         // PowerampAPI.Track.FileType.TYPE_STREAM
    }

    /** Cover of a track as Poweramp shows it (embedded, folder image or downloaded by Poweramp). */
    fun albumArtUri(realId: Long): Uri =
        Uri.Builder().scheme("content").authority(AA_AUTHORITY).appendPath("files").appendPath(realId.toString()).build()
}
