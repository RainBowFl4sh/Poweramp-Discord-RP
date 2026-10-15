package io.github.rainbowfl4sh.powerampdiscordrp.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** Pictures of the screens are decoded into the centered square they are shown as, limited in size. */
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
    fun somethingElseIsNoPicture() {
        assertNull(RemoteImages.decode("<html>not found</html>".toByteArray()))
        assertNull(RemoteImages.decode(ByteArray(0)))
    }
}
