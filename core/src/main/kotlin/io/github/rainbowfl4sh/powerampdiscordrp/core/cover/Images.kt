package io.github.rainbowfl4sh.powerampdiscordrp.core.cover

/** Image format sniffing on the first bytes (port of the AIMP plugin's util::LooksLikeImage, plus AVIF / HEIF). */
internal object Images {
    /** Brands of the ISO base media "ftyp" box that mean a still image (MP4 / M4A use the same box - not accepted). */
    private val IMAGE_BRANDS = setOf("avif", "avis", "heic", "heix", "heim", "heis", "hevc", "hevx", "hevm", "hevs", "mif1", "msf1")

    fun looksLikeImage(b: ByteArray): Boolean {
        if (b.size < 12) return false
        return isJpeg(b) || isPng(b) || startsWith(b, 0, "GIF8") || startsWith(b, 0, "BM") ||
            (startsWith(b, 0, "RIFF") && startsWith(b, 8, "WEBP")) ||
            (startsWith(b, 4, "ftyp") && String(b, 8, 4, Charsets.ISO_8859_1) in IMAGE_BRANDS)
    }

    /** Formats every upload host takes as they are (used when the encoder cannot handle the picture). */
    fun isJpegOrPng(b: ByteArray): Boolean = b.size > 4 && (isJpeg(b) || isPng(b))

    /** File name extension for an upload ("png" for PNG, otherwise "jpg"). */
    fun extension(b: ByteArray): String = if (b.size > 4 && isPng(b)) "png" else "jpg"

    fun mimeType(b: ByteArray): String = if (extension(b) == "png") "image/png" else "image/jpeg"

    private fun isJpeg(b: ByteArray) = b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()

    private fun isPng(b: ByteArray) = b.size >= 4 && b[0] == 0x89.toByte() && startsWith(b, 1, "PNG")

    private fun startsWith(b: ByteArray, offset: Int, ascii: String): Boolean {
        if (b.size < offset + ascii.length) return false
        return ascii.indices.all { b[offset + it] == ascii[it].code.toByte() }
    }
}
