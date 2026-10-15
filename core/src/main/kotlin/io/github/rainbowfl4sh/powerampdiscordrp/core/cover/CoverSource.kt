package io.github.rainbowfl4sh.powerampdiscordrp.core.cover

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo

/**
 * A publicly reachable cover URL for a track.
 * [source]: "Deezer", "iTunes", ... for online lookups, "poweramp|x0.at" style ("<origin>|<host>") for uploaded local covers.
 */
data class CoverResult(
    val url: String = "",
    val source: String = "",
    val storedWallMs: Long = 0,   // when it was cached (uploaded covers are checked again when older than 3 days)
) {
    val isEmpty: Boolean get() = url.isEmpty()
    val isUploaded: Boolean get() = source.contains('|')
}

/** Raw image bytes of the cover Poweramp shows for a track (embedded, folder image or downloaded by Poweramp). */
fun interface LocalCoverSource {
    fun read(track: TrackInfo): ByteArray?
}

/** Decodes an image, crops it to a centered square, scales it to at most [maxPx] and encodes JPEG. null = cannot decode. */
fun interface ImageEncoder {
    fun toJpeg(image: ByteArray, maxPx: Int): ByteArray?
}

/** What the presence engine needs from the cover resolver (faked in engine tests). */
interface CoverSource {
    /** Fast, never touches the network. */
    fun peekCache(track: TrackInfo): CoverResult

    /** May block for several seconds (network) - call from a background thread. Empty result = nothing found. */
    fun resolve(track: TrackInfo, config: Config): CoverResult

    /** Drops this track's cover from the cache (resolved again next time), also where other tracks share its URL. */
    fun forget(track: TrackInfo)

    fun clearCache()

    /** The URL delivers a picture (first bytes are checked). */
    fun reachable(url: String): Boolean

    /**
     * Like [reachable], but tells a broken picture apart from our own network trouble: the cover repair must only act on
     * [Probe.BROKEN] (the host answered, but not with a picture), never on [Probe.UNKNOWN] (timeout, no connection, DNS, ...).
     */
    fun probe(url: String): Probe = if (reachable(url)) Probe.OK else Probe.BROKEN
}

/** Result of [CoverSource.probe]. */
enum class Probe { OK, BROKEN, UNKNOWN }
