package io.github.rainbowfl4sh.powerampdiscordrp.core.model

/**
 * Plain-data description of the track Poweramp is playing, taken from its TRACK_CHANGED broadcast
 * (Poweramp API "track" bundle, identical in builds 1025 and 1032).
 */
data class TrackInfo(
    val id: Long = 0,
    val realId: Long = 0,            // Poweramp file id - key for its album art provider (content://com.maxmpz.audioplayer.aa/files/<realId>)
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val path: String = "",           // file path, content:// URI or stream URL
    val durationMs: Long = 0,        // 0 = unknown (e.g. internet radio)
    val fileType: Int = 0,           // PowerampAPI.Track.FileType.*
    val isStream: Boolean = false,
    val posInList: Int = 0,          // 0-based position in the current list (Poweramp "posInList")
    val listSize: Int = 0,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val bitsPerSample: Int = 0,
    val bitRate: Int = 0,            // kbit/s
    val codec: String = "",
    val rating: Int = 0,             // 0..5
) {
    /** Nothing usable to show (no title and no path). */
    val isEmpty: Boolean get() = title.isBlank() && path.isBlank()
}
