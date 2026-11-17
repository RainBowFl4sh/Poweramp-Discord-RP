package io.github.rainbowfl4sh.powerampdiscordrp.core.update

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Outcome of [UpdateDownloader.download]. */
sealed interface DownloadResult {
    data class Done(val file: File) : DownloadResult

    /** [detail] is for the log. */
    data class Failed(val reason: Reason, val detail: String) : DownloadResult

    enum class Reason {
        NO_APK,        // the release has no APK of this project
        NO_CHECKSUM,   // neither GitHub's digest nor a checksum file: never installed unchecked
        OFFLINE,       // GitHub could not be reached or the connection broke
        DAMAGED,       // HTTP error, too large, or the SHA-256 does not match
        STORAGE,       // the file could not be written
    }
}

/**
 * Downloads a release's APK into a file and checks it against the SHA-256 GitHub publishes for it (the asset's digest)
 * or the release's checksum file - like the AIMP plugin, which installs nothing it could not check. Blocking.
 */
class UpdateDownloader(private val http: OkHttpClient) {

    /**
     * @param target     where the APK goes (written to "<target>.part" first, renamed once it checked out)
     * @param onProgress percent 0..100 (-1 while the size is unknown), called from the downloading thread
     */
    fun download(release: ReleaseInfo, target: File, onProgress: (Int) -> Unit = {}): DownloadResult {
        if (release.apkUrl.isBlank()) return DownloadResult.Failed(DownloadResult.Reason.NO_APK, "no APK in release ${release.version}")
        val expected = try {
            release.apkSha256.ifEmpty { checksumFile(release.checksumUrl) }
        } catch (e: IOException) {
            return DownloadResult.Failed(DownloadResult.Reason.OFFLINE, "checksum: $e")
        }
        if (expected.isEmpty()) return DownloadResult.Failed(DownloadResult.Reason.NO_CHECKSUM, "no checksum for ${release.apkUrl}")

        val part = File(target.path + ".part")
        try {
            target.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            val call = http.newCall(Request.Builder().url(release.apkUrl).get().build())
            call.timeout().timeout(DOWNLOAD_TIMEOUT_SEC, TimeUnit.SECONDS)
            call.execute().use { response ->
                if (response.code != 200) return DownloadResult.Failed(DownloadResult.Reason.DAMAGED, "HTTP ${response.code}")
                val body = response.body
                val total = body.contentLength()
                if (total > MAX_APK_BYTES) return DownloadResult.Failed(DownloadResult.Reason.DAMAGED, "too large: $total bytes")
                var done = 0L
                var lastPercent = -2
                part.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            done += n
                            if (done > MAX_APK_BYTES) {
                                return DownloadResult.Failed(DownloadResult.Reason.DAMAGED, "too large: more than $MAX_APK_BYTES bytes")
                            }
                            digest.update(buffer, 0, n)
                            out.write(buffer, 0, n)
                            val percent = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else -1
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expected) {
                return DownloadResult.Failed(DownloadResult.Reason.DAMAGED, "SHA-256 $actual, expected $expected")
            }
            if (target.exists() && !target.delete() || !part.renameTo(target)) {
                return DownloadResult.Failed(DownloadResult.Reason.STORAGE, "could not write ${target.path}")
            }
            return DownloadResult.Done(target)
        } catch (e: IOException) {
            val reason = if (e is java.io.FileNotFoundException) DownloadResult.Reason.STORAGE else DownloadResult.Reason.OFFLINE
            return DownloadResult.Failed(reason, e.toString())
        } finally {
            part.delete()
        }
    }

    /** The checksum in the release's "<apk>.sha256" file; "" if there is none (IOException: not reachable). */
    private fun checksumFile(url: String): String {
        if (url.isBlank()) return ""
        val call = http.newCall(Request.Builder().url(url).get().build())
        call.timeout().timeout(CHECKSUM_TIMEOUT_SEC, TimeUnit.SECONDS)
        call.execute().use { response ->
            if (response.code != 200) return ""
            val text = response.body.source().let { source ->
                source.request(MAX_CHECKSUM_BYTES)
                source.buffer.readUtf8(minOf(source.buffer.size, MAX_CHECKSUM_BYTES))
            }
            return UpdateChecker.sha256Of(text)
        }
    }

    companion object {
        const val MAX_APK_BYTES = 64L shl 20
        private const val MAX_CHECKSUM_BYTES = 1024L
        private const val DOWNLOAD_TIMEOUT_SEC = 300L
        private const val CHECKSUM_TIMEOUT_SEC = 15L
    }
}
