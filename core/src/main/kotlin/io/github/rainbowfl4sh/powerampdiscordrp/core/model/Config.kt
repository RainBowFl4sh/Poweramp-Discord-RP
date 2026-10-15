package io.github.rainbowfl4sh.powerampdiscordrp.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * All user settings (same options as the AIMP plugin where they make sense on Android).
 * Stored as JSON; unknown / missing keys fall back to the defaults below, so old exports stay importable.
 */
@Serializable
data class Config(
    // --- General
    val enabled: Boolean = true,
    val activityType: Int = ACTIVITY_LISTENING,     // 2 = Listening (progress bar), 0 = Playing
    val statusDisplay: Int = STATUS_DISPLAY_STATE,  // 0 = app name, 1 = state line, 2 = details line (Discord status_display_type)
    val showTimestamps: Boolean = true,             // progress bar / elapsed time
    val pausedBehavior: Int = PAUSED_HIDE,          // 0 = show "Paused", 1 = hide the presence (lets other activities show)
    val clearAfterPausedMin: Int = 0,               // with PAUSED_SHOW: hide after N minutes, 0 = never
    val hideStreams: Boolean = false,               // internet radio / streams
    val excludePaths: String = "",                  // ';' separated substrings of the path (case-insensitive)

    // --- Display
    val details: String = "%title%",
    val state: String = "%artist%",
    val largeText: String = "%album%",
    val smallText: String = "%status%",
    val showSmallIcon: Boolean = true,
    val barLength: Int = 12,                        // characters for %bar%
    val refreshSeconds: Int = 15,                   // refresh interval when %pos% / %bar% / %percent% are used
    val rotationSeconds: Int = 10,                  // "||" in a text: its alternatives take turns every N seconds (5..60)
    val titleLink: Boolean = true,                  // clickable song title (Discord details_url)
    val titleLinkCustom: Boolean = false,           // false = DEFAULT_TITLE_LINK (YouTube search)
    val titleLinkUrl: String = DEFAULT_TITLE_LINK,

    // --- Cover art
    val coverEnabled: Boolean = true,
    val localCovers: Boolean = true,                // the cover Poweramp shows (embedded / folder / downloaded by Poweramp) -> upload host
    val uploadHost: Int = UPLOAD_X0,                // 0 = off, 1 = catbox.moe, 2 = Imgur, 3 = x0.at
    val preferLocal: Boolean = true,                // local cover first, online lookup only if there is none
    val imgurClientId: String = "",
    val srcSpotify: Boolean = false,                // needs Client ID + Secret
    val srcDeezer: Boolean = true,
    val srcItunes: Boolean = true,
    val srcBandcamp: Boolean = true,
    val srcDiscogs: Boolean = false,                // needs a personal access token
    val srcMusicBrainz: Boolean = true,             // MusicBrainz + Cover Art Archive
    val spotifyId: String = "",
    val spotifySecret: String = "",
    val discogsToken: String = "",
    val coverMaxPx: Int = 512,                      // uploaded covers are cropped to a square of this size (JPEG)

    // --- Advanced
    val useCustomApp: Boolean = false,              // use an own Discord application
    val customClientId: String = "",
    val timestampsInSeconds: Boolean = false,       // compatibility: send Discord timestamps in seconds instead of milliseconds

    // --- Updates (About tab)
    val updateCheck: Boolean = true,
    val updateFrequency: Int = UPDATE_DAILY,        // 0 = every app start, 1 = daily, 2 = weekly, 3 = monthly
    @SerialName("updateLastCheck") val updateLastCheckMs: Long = 0,
    val updateLatest: String = "",                  // newest version found by the last check
) {
    /** Application ID to use: the own one if enabled and valid, otherwise [defaultClientId] (may be blank = not configured). */
    fun effectiveClientId(defaultClientId: String): String {
        val own = customClientId.trim()
        return if (useCustomApp && isValidClientId(own)) own else defaultClientId.trim()
    }

    /** Link template for the clickable title, "" = no link. */
    fun effectiveTitleLink(): String = when {
        !titleLink -> ""
        titleLinkCustom -> titleLinkUrl.trim()
        else -> DEFAULT_TITLE_LINK
    }

    /** [rotationSeconds] clamped to [ROTATION_MIN_SECONDS]..[ROTATION_MAX_SECONDS] (an imported / edited file may hold anything). */
    fun effectiveRotationSeconds(): Int = rotationSeconds.coerceIn(ROTATION_MIN_SECONDS, ROTATION_MAX_SECONDS)

    /** Settings for export: the update bookkeeping is not part of it. */
    fun forExport(): Config = copy(updateLastCheckMs = 0, updateLatest = "")

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        const val ACTIVITY_PLAYING = 0
        const val ACTIVITY_LISTENING = 2

        const val STATUS_DISPLAY_NAME = 0
        const val STATUS_DISPLAY_STATE = 1
        const val STATUS_DISPLAY_DETAILS = 2

        const val PAUSED_SHOW = 0
        const val PAUSED_HIDE = 1

        const val UPLOAD_OFF = 0
        const val UPLOAD_CATBOX = 1
        const val UPLOAD_IMGUR = 2
        const val UPLOAD_X0 = 3

        const val UPDATE_EVERY_START = 0
        const val UPDATE_DAILY = 1
        const val UPDATE_WEEKLY = 2
        const val UPDATE_MONTHLY = 3

        /**
         * Shortest text rotation interval: Discord allows 5 presence updates per 20 s, a change every 5 s leaves room for
         * the track's own updates.
         */
        const val ROTATION_MIN_SECONDS = 5
        const val ROTATION_MAX_SECONDS = 60

        const val DEFAULT_TITLE_LINK = "https://www.youtube.com/results?search_query=%artist%+%title%"

        /** Marker so an import can recognize a settings file of this app. */
        const val EXPORT_MARKER = "poweramp-discord-rp"

        val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            prettyPrint = true
        }

        /** Discord application IDs are snowflakes: 17-20 digits. */
        fun isValidClientId(id: String): Boolean = id.length in 17..20 && id.all { it.isDigit() }

        /** Parses a stored / exported settings JSON; null if it is not valid. */
        fun fromJson(json: String): Config? = try {
            JSON.decodeFromString(serializer(), json)
        } catch (_: Exception) {
            null
        }
    }
}
