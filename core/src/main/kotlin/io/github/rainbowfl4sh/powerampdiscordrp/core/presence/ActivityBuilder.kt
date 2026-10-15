package io.github.rainbowfl4sh.powerampdiscordrp.core.presence

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Builds the "activity" object of a SET_ACTIVITY frame (Discord RPC / Social SDK activity format). */
object ActivityBuilder {
    /** Image keys of the Discord application's art assets (Developer Portal -> Rich Presence -> Art Assets). */
    const val ASSET_LOGO = "poweramp"
    const val ASSET_PLAY = "play"
    const val ASSET_PAUSE = "pause"

    /** Activity name sent along (the Discord app shows the application's name). */
    const val ACTIVITY_NAME = "Poweramp"

    /**
     * Activity for the given texts, or null if [texts] is hidden.
     * - type = c.activityType, status_display_type = c.statusDisplay, name = ACTIVITY_NAME
     * - details / state / details_url only if not empty
     * - timestamps (only while playing and c.showTimestamps): start = wallNowMs - posMs, end = start + durMs (if durMs > 0);
     *   in milliseconds, or seconds if c.timestampsInSeconds
     * - assets: large_image = coverUrl (if not blank) else ASSET_LOGO, large_text; small_image = play/pause + small_text if c.showSmallIcon
     * - [images]: asset key -> picture URL of the application's art assets; a key found there is sent as that URL (the
     *   Discord Android app shows URLs but not keys), otherwise the key itself
     */
    fun build(
        c: Config,
        texts: ActivityTexts,
        coverUrl: String?,
        wallNowMs: Long,
        images: Map<String, String> = emptyMap(),
    ): JsonObject? {
        if (texts.hidden != Hidden.NO) return null
        return buildJsonObject {
            put("name", ACTIVITY_NAME)
            put("type", c.activityType)
            put("status_display_type", c.statusDisplay)
            if (texts.details.isNotEmpty()) put("details", texts.details)
            if (texts.state.isNotEmpty()) put("state", texts.state)
            if (texts.detailsUrl.isNotEmpty()) put("details_url", texts.detailsUrl)

            // progress bar (Listening) or elapsed counter (Playing)
            if (c.showTimestamps && texts.playing) {
                putTimestamps(c, startMs = wallNowMs - texts.posMs, durMs = texts.durMs)
            }

            // cover (or the application's logo) + play / pause icon
            putJsonObject("assets") {
                put("large_image", coverUrl?.takeIf { it.isNotBlank() } ?: image(ASSET_LOGO, images))
                if (texts.largeText.isNotEmpty()) put("large_text", texts.largeText)
                if (c.showSmallIcon) {
                    put("small_image", image(if (texts.playing) ASSET_PLAY else ASSET_PAUSE, images))
                    if (texts.smallText.isNotEmpty()) put("small_text", texts.smallText)
                }
            }
        }
    }

    /** A test presence ("Test - Poweramp Discord Rich Presence <version>") shown for a few seconds from the Advanced tab. */
    fun test(c: Config, version: String, line: String, wallNowMs: Long, images: Map<String, String> = emptyMap()): JsonObject = buildJsonObject {
        put("name", ACTIVITY_NAME)
        put("type", c.activityType)
        put("details", Templates.field("Test - Poweramp Discord Rich Presence $version"))
        Templates.field(line).takeIf { it.isNotEmpty() }?.let { put("state", it) }
        putTimestamps(c, startMs = wallNowMs, durMs = 0)
        putJsonObject("assets") {
            put("large_image", image(ASSET_LOGO, images))
            put("small_image", image(ASSET_PLAY, images))
        }
    }

    /** What is sent for an art asset: its picture URL if known (and within Discord's 256 characters), else its key. */
    fun image(key: String, images: Map<String, String>): String =
        images[key]?.takeIf { it.length <= PresenceTexts.MAX_URL_LENGTH && PresenceTexts.isHttpUrl(it) } ?: key

    private fun JsonObjectBuilder.putTimestamps(c: Config, startMs: Long, durMs: Long) {
        putJsonObject("timestamps") {
            if (c.timestampsInSeconds) {   // older clients / bridges expect unix seconds
                val start = Math.floorDiv(startMs, 1000L)
                put("start", start)
                if (durMs > 0) put("end", start + (durMs + 500) / 1000)
            } else {
                put("start", startMs)
                if (durMs > 0) put("end", startMs + durMs)
            }
        }
    }
}
