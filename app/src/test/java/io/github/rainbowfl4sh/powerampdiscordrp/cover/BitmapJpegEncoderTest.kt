package io.github.rainbowfl4sh.powerampdiscordrp.cover

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapJpegEncoderTest {
    private fun png(width: Int, height: Int, draw: Canvas.() -> Unit): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).draw()
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun Canvas.fill(color: Int, left: Int, right: Int) =
        drawRect(left.toFloat(), 0f, right.toFloat(), height.toFloat(), Paint().apply { this.color = color })

    private fun decode(jpeg: ByteArray): Bitmap {
        assertEquals(0xFF.toByte(), jpeg[0])   // JPEG SOI marker
        assertEquals(0xD8.toByte(), jpeg[1])
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    }

    private fun assertMostly(expected: Int, actual: Int) {
        val channels = listOf(Color.red(actual), Color.green(actual), Color.blue(actual))
        val wanted = listOf(Color.red(expected), Color.green(expected), Color.blue(expected))
        channels.zip(wanted).forEach { (a, w) -> assertTrue("got #${Integer.toHexString(actual)}", kotlin.math.abs(a - w) < 40) }
    }

    @Test
    fun cropsACenteredSquareAndScalesDown() {
        // 600 x 300: red | green (the centered 300 x 300 square) | blue
        val image = png(600, 300) {
            fill(Color.RED, 0, 150)
            fill(Color.GREEN, 150, 450)
            fill(Color.BLUE, 450, 600)
        }
        val out = decode(BitmapJpegEncoder.toJpeg(image, 100)!!)
        assertEquals(100, out.width)
        assertEquals(100, out.height)
        for ((x, y) in listOf(3 to 3, 96 to 3, 3 to 96, 96 to 96, 50 to 50)) assertMostly(Color.GREEN, out.getPixel(x, y))
    }

    @Test
    fun smallCoversAreNotEnlarged() {
        val out = decode(BitmapJpegEncoder.toJpeg(png(64, 80) { drawColor(Color.BLUE) }, 512)!!)
        assertEquals(64, out.width)
        assertEquals(64, out.height)
    }

    @Test
    fun largeCoversAreSubsampledToTheTarget() {
        val out = decode(BitmapJpegEncoder.toJpeg(png(1500, 1500) { drawColor(Color.YELLOW) }, 512)!!)
        assertEquals(512, out.width)
        assertMostly(Color.YELLOW, out.getPixel(256, 256))
    }

    @Test
    fun transparencyBecomesWhite() {
        val out = decode(BitmapJpegEncoder.toJpeg(png(50, 50) { drawColor(Color.TRANSPARENT) }, 512)!!)
        assertMostly(Color.WHITE, out.getPixel(25, 25))
    }

    @Test
    fun notAnImage() {
        assertNull(BitmapJpegEncoder.toJpeg(byteArrayOf(1, 2, 3, 4), 512))
        assertNull(BitmapJpegEncoder.toJpeg(ByteArray(0), 512))
    }

    @Test
    fun sampleSizeKeepsAtLeastTheTarget() {
        assertEquals(1, BitmapJpegEncoder.sampleSize(512, 512))
        assertEquals(1, BitmapJpegEncoder.sampleSize(1023, 512))
        assertEquals(2, BitmapJpegEncoder.sampleSize(1024, 512))
        assertEquals(4, BitmapJpegEncoder.sampleSize(3000, 512))
        assertEquals(1, BitmapJpegEncoder.sampleSize(300, 512))
    }
}
