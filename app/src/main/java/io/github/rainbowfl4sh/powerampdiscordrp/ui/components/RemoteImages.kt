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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.rainbowfl4sh.powerampdiscordrp.Graph
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.sampleSizeFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Pictures shown by the screens (current cover, Discord avatar): downloaded with the app's HTTP client off the main
 * thread and kept in a small in-memory cache, so switching tabs or rotating does not load them again. The cache is
 * dropped by [clear] when the UI is hidden: the process lives on with the presence service while nothing draws them.
 */
object RemoteImages {
    private const val MAX_BYTES = 8 * 1024 * 1024

    /** Edge of the squares the pictures are kept as: enough for the biggest one on screen (112 dp, the cover tab). */
    internal const val MAX_PX = 512

    /**
     * Bytes the cache keeps: a few covers plus the small avatar / logo pictures - what one screen shows (at most 1 MB per
     * picture: MAX_PX squared, 4 bytes per pixel). The process outlives the screens (the presence service), so this is
     * also emptied when the UI is hidden ([clear]).
     */
    internal const val CACHE_BYTES = 4 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().byteCount
    }

    /** Failed URLs are not tried again for a while (a broken cover link would otherwise be fetched on every recomposition). */
    private val failed = LruCache<String, Long>(32)
    private const val RETRY_MS = 60_000L

    /**
     * Downloads run here, not in the caller: callers of the same URL share one download, and a caller that goes away
     * (a page leaving the screen) does not throw away a download that is finished anyway, it still lands in the cache.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = HashMap<String, Deferred<ImageBitmap?>>()

    /** The bytes behind a URL, null = not there; replaceable by tests. */
    internal var fetch: (String) -> ByteArray? = ::download

    fun peek(url: String): ImageBitmap? = cache.get(url)

    /**
     * Drops the cached pictures (the UI is hidden: nothing draws them until it comes back). Pictures still on screen keep
     * their own reference and are not recycled; the garbage collector frees them once nothing draws them.
     */
    fun clear() = cache.evictAll()

    /** Bytes of the cached pictures. */
    internal fun cachedBytes(): Int = cache.size()

    internal fun put(url: String, image: ImageBitmap) {
        cache.put(url, image)
    }

    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        if (!Graph.isInitialized) return null   // the app's HTTP client (and log) are not there: screens drawn without the app
        failed.get(url)?.let { if (System.currentTimeMillis() - it < RETRY_MS) return null }
        val job = synchronized(inFlight) {
            inFlight[url] ?: scope.async { loadNow(url) }.also { job ->
                inFlight[url] = job
                job.invokeOnCompletion { synchronized(inFlight) { if (inFlight[url] === job) inFlight.remove(url) } }
            }
        }
        return job.await()
    }

    private fun loadNow(url: String): ImageBitmap? {
        val image = try {
            fetch(url)?.let(::decode)
        } catch (e: Exception) {
            Graph.log.log("Preview: could not load picture $url: $e")
            null
        }
        if (image != null) put(url, image) else failed.put(url, System.currentTimeMillis())
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
