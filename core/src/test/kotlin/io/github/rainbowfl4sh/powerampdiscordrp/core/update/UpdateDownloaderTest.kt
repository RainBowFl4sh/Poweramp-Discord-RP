package io.github.rainbowfl4sh.powerampdiscordrp.core.update

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.Proxy
import java.security.MessageDigest

class UpdateDownloaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val http = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
    private val downloader = UpdateDownloader(http)
    private val apk = ByteArray(200_000) { (it * 31).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }

    @After
    fun tearDown() = server.close()

    private fun target() = tmp.root.resolve("updates/PowerampDiscordRP-1.3.0.apk")

    private fun release(sha256: String = "", checksumUrl: String = "") = ReleaseInfo(
        version = "1.3.0", pageUrl = "", apkUrl = server.url("/download/app.apk").toString(), notes = "",
        apkSha256 = sha256, checksumUrl = checksumUrl,
    )

    private fun serveApk(bytes: ByteArray = apk) =
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(bytes)).build())

    @Test
    fun downloadIsCheckedAgainstTheDigest() {
        serveApk()
        val progress = mutableListOf<Int>()

        val r = downloader.download(release(sha256 = sha), target()) { progress += it }

        val file = (r as DownloadResult.Done).file
        assertArrayEquals(apk, file.readBytes())
        assertEquals(100, progress.last())
        assertFalse("no part file left", tmp.root.resolve("updates/PowerampDiscordRP-1.3.0.apk.part").exists())
    }

    @Test
    fun checksumFileWhenThereIsNoDigest() {
        server.enqueue(MockResponse.Builder().code(200).body("$sha  PowerampDiscordRP-v1.3.0.apk\n").build())
        serveApk()

        val r = downloader.download(release(checksumUrl = server.url("/download/app.apk.sha256").toString()), target())

        assertTrue(r is DownloadResult.Done)
        assertEquals("/download/app.apk.sha256", server.takeRequest().url.encodedPath)
        assertEquals("/download/app.apk", server.takeRequest().url.encodedPath)
    }

    @Test
    fun aChangedFileIsRejected() {
        serveApk(apk.copyOf().also { it[1000] = (it[1000] + 1).toByte() })

        val r = downloader.download(release(sha256 = sha), target())

        assertEquals(DownloadResult.Reason.DAMAGED, (r as DownloadResult.Failed).reason)
        assertFalse(target().exists())
    }

    @Test
    fun neverWithoutAChecksum() {
        assertEquals(DownloadResult.Reason.NO_CHECKSUM, (downloader.download(release(), target()) as DownloadResult.Failed).reason)
        // the checksum file is missing
        server.enqueue(MockResponse.Builder().code(404).body("Not Found").build())
        val r = downloader.download(release(checksumUrl = server.url("/x.sha256").toString()), target())
        assertEquals(DownloadResult.Reason.NO_CHECKSUM, (r as DownloadResult.Failed).reason)
        assertEquals("only the checksum was asked", 1, server.requestCount)
    }

    @Test
    fun noApkAndHttpErrors() {
        val none = release(sha256 = sha).copy(apkUrl = "")
        assertEquals(DownloadResult.Reason.NO_APK, (downloader.download(none, target()) as DownloadResult.Failed).reason)

        server.enqueue(MockResponse.Builder().code(404).body("Not Found").build())
        assertEquals(DownloadResult.Reason.DAMAGED, (downloader.download(release(sha256 = sha), target()) as DownloadResult.Failed).reason)

        val offline = release(sha256 = sha).copy(apkUrl = "http://127.0.0.1:1/app.apk")
        assertEquals(DownloadResult.Reason.OFFLINE, (downloader.download(offline, target()) as DownloadResult.Failed).reason)
    }
}
