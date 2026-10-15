package io.github.rainbowfl4sh.powerampdiscordrp.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.rainbowfl4sh.powerampdiscordrp.Graph
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.sampleSizeFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Pictures shown by the screens (current cover, Discord avatar): downloaded with the app's HTTP client off the main
 * thread and kept in a small in-memory cache, so switching tabs or rotating does not load them again.
 */
object RemoteImages {
    private const val MAX_BYTES = 8 * 1024 * 1024

    /** Edge of the squares the pictures are kept as: enough for the biggest one on screen (112 dp, the cover tab). */
    internal const val MAX_PX = 512

    // at most 1 MB per picture (MAX_PX squared, 4 bytes per pixel)
    private val cache = LruCache<String, ImageBitmap>(16)

    /** Failed URLs are not tried again for a while (a broken cover link would otherwise be fetched on every recomposition). */
    private val failed = LruCache<String, Long>(32)
    private const val RETRY_MS = 60_000L

    fun peek(url: String): ImageBitmap? = cache.get(url)

    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        failed.get(url)?.let { if (System.currentTimeMillis() - it < RETRY_MS) return null }
        val image = withContext(Dispatchers.IO) {
            try {
                download(url)?.let(::decode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Graph.log.log("Preview: could not load picture $url: $e")
                null
            }
        }
        if (image != null) cache.put(url, image) else failed.put(url, System.currentTimeMillis())
        return image
    }

    private fun download(url: String): ByteArray? {
        val request = Request.Builder().url(url).build()
        Graph.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body.byteStream().use { readLimited(it) }
        }
    }

    private fun readLimited(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > MAX_BYTES) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /**
     * Decoded here, on the IO thread, into what the screens draw: every picture is shown cropped into a square, so it is
     * kept as its centered square of at most [maxPx] (subsampled while decoding, then scaled). A 3000 px cover takes 1 MB
     * instead of 36 MB, and drawing it does not scale the whole picture down again in every frame. null = no picture.
     */
    internal fun decode(bytes: ByteArray, maxPx: Int = MAX_PX): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxPx) }
        return try {
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            val square = squareOf(decoded, maxPx)
            if (square !== decoded) decoded.recycle()
            // prepareToDraw: the upload to the GPU starts now instead of in the first frame that shows the picture
            square.asImageBitmap().also { it.prepareToDraw() }
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** The centered square of [b], scaled down to at most [maxPx] ([b] itself if it is that already). */
    private fun squareOf(b: Bitmap, maxPx: Int): Bitmap {
        val side = minOf(b.width, b.height)
        val size = minOf(side, maxPx)
        if (b.width == b.height && side == size) return b
        val left = (b.width - side) / 2
        val top = (b.height - side) / 2
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { out ->
            Canvas(out).drawBitmap(b, Rect(left, top, left + side, top + side), Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }
}

/** A picture being loaded: [image] once it is there, [loading] while the download runs. */
data class RemoteImage(val image: ImageBitmap?, val loading: Boolean)

/** The picture behind [url] with its loading state (a blank URL is "no picture, not loading"). */
@Composable
fun rememberRemoteImageState(url: String?): RemoteImage {
    val key = url?.trim().orEmpty()
    val initial = if (key.isEmpty()) RemoteImage(null, false) else RemoteImages.peek(key).let { RemoteImage(it, it == null) }
    val state by produceState(initialValue = initial, key) {
        // a new URL: drop the old picture at once, then load the new one
        val cached = if (key.isEmpty()) null else RemoteImages.peek(key)
        value = RemoteImage(cached, key.isNotEmpty() && cached == null)
        if (value.loading) value = RemoteImage(RemoteImages.load(key), false)
    }
    return state
}

/** The picture behind [url] (null while loading, on failure or for a blank URL). */
@Composable
fun rememberRemoteImage(url: String?): ImageBitmap? = rememberRemoteImageState(url).image
