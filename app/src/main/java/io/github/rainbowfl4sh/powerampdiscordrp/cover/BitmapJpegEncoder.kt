package io.github.rainbowfl4sh.powerampdiscordrp.cover

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.ImageEncoder
import java.io.ByteArrayOutputStream

/**
 * Cover for the upload hosts: centered square crop, at most maxPx wide, JPEG quality 90.
 * Large embedded art is subsampled while decoding (a 3000 px PNG would need 36 MB as a bitmap otherwise).
 */
object BitmapJpegEncoder : ImageEncoder {
    private const val QUALITY = 90

    override fun toJpeg(image: ByteArray, maxPx: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val side = minOf(bounds.outWidth, bounds.outHeight)
        val target = if (maxPx > 0) minOf(maxPx, side) else side
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(side, target)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = try {
            BitmapFactory.decodeByteArray(image, 0, image.size, options)
        } catch (_: OutOfMemoryError) {
            null
        } ?: return null

        var output: Bitmap? = null
        try {
            val square = minOf(decoded.width, decoded.height)
            val size = minOf(target, square)
            val left = (decoded.width - square) / 2
            val top = (decoded.height - square) / 2
            output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            // White background: JPEG has no alpha, transparent PNG areas would turn black
            Canvas(output).apply {
                drawColor(Color.WHITE)
                drawBitmap(
                    decoded,
                    Rect(left, top, left + square, top + square),
                    Rect(0, 0, size, size),
                    Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
                )
            }
            val bytes = ByteArrayOutputStream()
            if (!output.compress(Bitmap.CompressFormat.JPEG, QUALITY, bytes)) return null
            return bytes.toByteArray().takeIf { it.isNotEmpty() }
        } catch (_: OutOfMemoryError) {
            return null
        } finally {
            decoded.recycle()
            output?.recycle()
        }
    }

    /** Largest power of two that keeps the decoded square at least [target] pixels wide. */
    fun sampleSize(side: Int, target: Int): Int {
        var sample = 1
        while (target > 0 && side / (sample * 2) >= target) sample *= 2
        return sample
    }
}
