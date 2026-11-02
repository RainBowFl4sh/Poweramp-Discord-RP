package io.github.rainbowfl4sh.powerampdiscordrp.ui.components

import android.app.Application
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import io.github.rainbowfl4sh.powerampdiscordrp.App
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pictures of the screens are decoded into the centered square they are shown as, limited in size; downloads are shared
 * and kept, and the cache is dropped when the UI is hidden.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemoteImagesTest {
    private fun png(width: Int, height: Int, draw: Canvas.() -> Unit = {}): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).draw()
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun Canvas.fill(color: Int, left: Int, right: Int) =
        drawRect(left.toFloat(), 0f, right.toFloat(), height.toFloat(), Paint().apply { this.color = color })

    @Test
    fun aBigWidePictureBecomesItsCenteredSquareAtMostMaxPx() {
        // 2400 x 1200: red | green (the centered 1200 x 1200 square) | blue
        val image = png(2400, 1200) {
            fill(Color.RED, 0, 600)
            fill(Color.GREEN, 600, 1800)
            fill(Color.BLUE, 1800, 2400)
        }
        val out = RemoteImages.decode(image)!!.asAndroidBitmap()
        assertEquals(RemoteImages.MAX_PX, out.width)
        assertEquals(RemoteImages.MAX_PX, out.height)
        for ((x, y) in listOf(2 to 2, out.width - 3 to 2, 2 to out.height - 3, out.width / 2 to out.height / 2)) {
            val pixel = out.getPixel(x, y)
            assertTrue("got #${Integer.toHexString(pixel)} at $x,$y", Color.green(pixel) > 200 && Color.red(pixel) < 60 && Color.blue(pixel) < 60)
        }
    }

    @Test
    fun aSmallPictureIsCroppedButNotEnlarged() {
        val out = RemoteImages.decode(png(80, 120))!!.asAndroidBitmap()
        assertEquals(80, out.width)
        assertEquals(80, out.height)
    }

    @Test
    fun aSmallSquareStaysAsItIs() {
        val out = RemoteImages.decode(png(64, 64) { drawColor(Color.BLUE) })!!.asAndroidBitmap()
        assertEquals(64, out.width)
        assertEquals(Color.BLUE, out.getPixel(10, 10))
    }

    @Test
    fun theCacheIsLimitedInBytesAndCanBeCleared() {
        RemoteImages.clear()
        val cover = RemoteImages.decode(png(RemoteImages.MAX_PX, RemoteImages.MAX_PX))!!   // 1 MB
        repeat(16) { RemoteImages.put("https://example.com/$it.png", cover) }
        assertTrue(RemoteImages.cachedBytes() <= RemoteImages.CACHE_BYTES)
        // the newest pictures stay, the oldest went
        assertNotNull(RemoteImages.peek("https://example.com/15.png"))
        assertNull(RemoteImages.peek("https://example.com/0.png"))

        RemoteImages.clear()
        assertEquals(0, RemoteImages.cachedBytes())
        assertNull(RemoteImages.peek("https://example.com/15.png"))
    }

    @Test
    fun somethingElseIsNoPicture() {
        assertNull(RemoteImages.decode("<html>not found</html>".toByteArray()))
        assertNull(RemoteImages.decode(ByteArray(0)))
    }

    private val realFetch = RemoteImages.fetch

    @After
    fun restore() {
        RemoteImages.fetch = realFetch
        RemoteImages.clear()
    }

    @Test
    fun hidingTheUiDropsTheCachedPictures() = runBlocking {
        val bytes = png(64, 64)
        RemoteImages.fetch = { bytes }
        val url = "https://example.com/trim.png"
        assertNotNull(RemoteImages.load(url))
        // while the UI is visible, a memory warning keeps them
        App().onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertNotNull(RemoteImages.peek(url))
        App().onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertNull(RemoteImages.peek(url))
    }

    @Test
    fun theCacheIsLimitedInBytesNotInPictures() = runBlocking {
        val bytes = png(RemoteImages.MAX_PX, RemoteImages.MAX_PX)
        RemoteImages.fetch = { bytes }
        val urls = (0 until 16).map { "https://example.com/big$it.png" }
        for (url in urls) assertNotNull(RemoteImages.load(url))
        // 16 pictures of 1 MB: only the last few stay
        val kept = urls.count { RemoteImages.peek(it) != null }
        assertTrue("kept $kept", kept in 1..4)
        assertNotNull(RemoteImages.peek(urls.last()))
    }

    @Test
    fun callersOfTheSameUrlShareOneDownload() = runBlocking {
        val bytes = png(64, 64)
        val calls = AtomicInteger()
        val release = CountDownLatch(1)
        RemoteImages.fetch = { calls.incrementAndGet(); release.await(5, TimeUnit.SECONDS); bytes }
        val url = "https://example.com/shared.png"
        val first = async(Dispatchers.Default) { RemoteImages.load(url) }
        val second = async(Dispatchers.Default) { RemoteImages.load(url) }
        withTimeout(5_000) { while (calls.get() == 0) delay(10) }
        delay(100)
        release.countDown()
        assertNotNull(first.await())
        assertNotNull(second.await())
        assertEquals(1, calls.get())
    }

    @Test
    fun aDownloadWhoseCallerWentAwayStillLandsInTheCache() = runBlocking {
        val bytes = png(64, 64)
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        RemoteImages.fetch = { started.complete(Unit); release.await(5, TimeUnit.SECONDS); bytes }
        val url = "https://example.com/cancelled.png"
        val caller = async(Dispatchers.Default) { RemoteImages.load(url) }
        started.await()
        caller.cancel()
        release.countDown()
        withTimeout(5_000) { while (RemoteImages.peek(url) == null) delay(10) }
    }
}
