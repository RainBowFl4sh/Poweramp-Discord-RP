package io.github.rainbowfl4sh.powerampdiscordrp.cover

import android.content.Context
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.LocalCoverSource
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Logger
import io.github.rainbowfl4sh.powerampdiscordrp.poweramp.Poweramp
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * The cover Poweramp shows for a track, from its album art provider (content://com.maxmpz.audioplayer.aa/files/<realId>):
 * embedded art, a folder image or one Poweramp downloaded. The provider may hand out a pipe, so the stream is read
 * as is (no size known in advance) and capped at [maxBytes].
 */
class AndroidLocalCover(
    context: Context,
    private val log: Logger = Logger.NONE,
    private val maxBytes: Int = MAX_BYTES,
) : LocalCoverSource {
    private val resolver = context.applicationContext.contentResolver

    override fun read(track: TrackInfo): ByteArray? {
        if (track.realId <= 0) return null
        val uri = Poweramp.albumArtUri(track.realId)
        return try {
            // Poweramp's provider hands out nothing (instead of throwing) when it fails inside, e.g. while it starts
            val stream = resolver.openInputStream(uri) ?: run {
                log.log("Local cover: Poweramp returned no data for $uri")
                return null
            }
            val bytes = stream.use { readCapped(it, maxBytes) } ?: run {
                log.log("Local cover: too large (over ${maxBytes / (1024 * 1024)} MB), skipped")
                return null
            }
            bytes.takeIf { it.isNotEmpty() }
        } catch (_: FileNotFoundException) {
            null   // Poweramp has no cover for this track
        } catch (e: Exception) {
            // SecurityException (provider not exported any more), IOException, Poweramp not installed, ...
            log.log("Local cover: could not read $uri: $e")
            null
        }
    }

    companion object {
        /** Embedded art of a few MB is common; anything beyond this is not worth uploading. */
        const val MAX_BYTES = 12 * 1024 * 1024

        /** The whole stream, or null if it is longer than [limit] bytes. */
        fun readCapped(input: InputStream, limit: Int): ByteArray? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > limit) return null
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }
}
