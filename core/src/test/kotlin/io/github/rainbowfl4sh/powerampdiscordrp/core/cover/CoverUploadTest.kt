package io.github.rainbowfl4sh.powerampdiscordrp.core.cover

import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.FakeNet.Companion.bytes
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.FakeNet.Companion.json
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.FakeNet.Companion.status
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.FakeNet.Companion.text
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.ENCODED_JPEG
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.HTML
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.JPEG
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.PNG
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.TRACK
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.UPLOAD_ONLY
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CoverUploadTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val net = FakeNet()
    private val clock = FakeClock()
    private val log = RecordingLogger()
    private var localImage: ByteArray? = JPEG
    private var encoded: ByteArray? = ENCODED_JPEG
    private val encoderSizes = mutableListOf<Int>()

    private val file get() = File(tmp.root, "covers.tsv")

    private fun resolver(file: File = this.file) = CoverResolver(
        http = net.client,
        cacheFile = file,
        local = { t -> localImage?.let { pictureOf(t) } },
        encoder = { _, maxPx -> encoderSizes += maxPx; encoded },
        clock = clock,
        log = log,
        endpoints = net.endpoints(),
    )

    /** What the local cover source gives for [track]: every album has its own picture (one upload per picture). */
    private fun pictureOf(track: TrackInfo): ByteArray = localImage!! + track.album.toByteArray()

    @After
    fun tearDown() = net.close()

    /** The picture behind every "https://files.test/img/..." URL (checked after each upload). */
    private fun serveImages(data: ByteArray = JPEG) = net.onPrefix("/img/") { bytes(data, code = 206) }

    private fun bodyOf(path: String) = net.last(path).body!!.utf8()

    @Test
    fun x0UploadIsCheckedAndUsed() {
        serveImages()
        net.on("/x0/", "POST") { text("https://files.test/img/a.jpg\n") }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY.copy(coverMaxPx = 300))

        assertEquals("https://files.test/img/a.jpg", r.url)
        assertEquals("poweramp|x0.at", r.source)
        assertTrue(r.isUploaded)
        assertEquals(listOf(300), encoderSizes)
        val body = bodyOf("/x0/")
        assertTrue(body, body.contains("name=\"file\"; filename=\"cover.jpg\""))
        assertTrue(body.contains("Content-Type: image/jpeg"))
        assertTrue("the encoded JPEG is sent", net.last("/x0/").body!!.indexOf(okio.ByteString.of(*ENCODED_JPEG)) >= 0)
        // the uploaded picture was checked with a ranged GET
        val check = net.last("/img/a.jpg")
        assertEquals("GET", check.method)
        assertEquals("bytes=0-63", check.headers["Range"])
        assertTrue(log.has("Cover uploaded to x0.at: https://files.test/img/a.jpg"))
    }

    @Test
    fun catboxUploadSendsItsFields() {
        serveImages()
        net.on("/catbox/user/api.php", "POST") { text("https://files.test/img/c.jpg") }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY.copy(uploadHost = Config.UPLOAD_CATBOX))

        assertEquals("https://files.test/img/c.jpg", r.url)
        assertEquals("poweramp|catbox.moe", r.source)
        val req = net.last("/catbox/user/api.php")
        assertEquals("*/*", req.headers["Accept"])
        val body = req.body!!.utf8()
        assertTrue(body, Regex("name=\"reqtype\"\r\n(?:[^\r\n]+\r\n)*\r\nfileupload\r\n").containsMatchIn(body))
        assertTrue(body, Regex("name=\"userhash\"\r\n(?:[^\r\n]+\r\n)*\r\n\r\n").containsMatchIn(body))
        assertTrue(body.contains("name=\"fileToUpload\"; filename=\"cover.jpg\""))
        assertEquals(0, net.count("/x0/"))
    }

    @Test
    fun imgurUploadSendsClientIdAndUsesTheLink() {
        serveImages(PNG)
        net.on("/imgur/3/image", "POST") {
            json("""{"data":{"id":"AbCdEf1","type":"image/jpeg","width":512,"height":512,"link":"http://files.test/img/AbCdEf1.jpg"},"success":true,"status":200}""")
        }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY.copy(uploadHost = Config.UPLOAD_IMGUR, imgurClientId = " abc123 "))

        assertEquals("https://files.test/img/AbCdEf1.jpg", r.url)   // http -> https
        assertEquals("poweramp|Imgur", r.source)
        val req = net.last("/imgur/3/image")
        assertEquals("Client-ID abc123", req.headers["Authorization"])
        assertTrue(req.body!!.utf8().contains("name=\"image\"; filename=\"cover.jpg\""))
    }

    @Test
    fun imgurWithoutClientIdUsesX0ThenCatbox() {
        serveImages()
        net.on("/x0/", "POST") { status(503, "busy") }
        net.on("/catbox/user/api.php", "POST") { text("https://files.test/img/c.jpg") }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY.copy(uploadHost = Config.UPLOAD_IMGUR, imgurClientId = ""))

        assertEquals("poweramp|catbox.moe", r.source)
        assertEquals(0, net.count("/imgur/3/image"))
        assertEquals(1, net.count("/x0/"))
        assertTrue(log.has("x0.at upload failed (HTTP 503)"))
    }

    @Test
    fun imgurFailureFallsBackToX0Only() {
        net.on("/imgur/3/image", "POST") { status(429, """{"data":{"error":"Too Many Requests"},"success":false,"status":429}""") }
        net.on("/x0/", "POST") { status(500) }
        net.on("/catbox/user/api.php", "POST") { text("https://files.test/img/c.jpg") }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY.copy(uploadHost = Config.UPLOAD_IMGUR, imgurClientId = "id"))

        assertTrue(r.isEmpty)
        assertEquals(1, net.count("/imgur/3/image"))
        assertEquals(1, net.count("/x0/"))
        assertEquals("like the AIMP plugin: Imgur -> x0.at", 0, net.count("/catbox/user/api.php"))
        assertTrue(log.has("Imgur upload failed (HTTP 429)"))
    }

    @Test
    fun catboxStandsInWhenX0Fails() {
        serveImages()
        net.on("/x0/", "POST") { status(500, "internal error") }
        net.on("/catbox/user/api.php", "POST") { text("https://files.test/img/c.jpg") }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY)

        assertEquals("https://files.test/img/c.jpg", r.url)
        assertEquals("poweramp|catbox.moe", r.source)
    }

    @Test
    fun x0StandsInWhenCatboxDeliversNoPicture() {
        net.on("/img/empty.jpg") { bytes(HTML, code = 200, type = "text/html") }   // e.g. an error page instead of the picture
        net.on("/img/x.jpg") { bytes(JPEG, code = 206) }
        net.on("/catbox/user/api.php", "POST") { text("https://files.test/img/empty.jpg") }
        net.on("/x0/", "POST") { text("https://files.test/img/x.jpg") }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY.copy(uploadHost = Config.UPLOAD_CATBOX))

        assertEquals("https://files.test/img/x.jpg", r.url)
        assertEquals("poweramp|x0.at", r.source)
        assertTrue(log.has("catbox.moe: the uploaded cover cannot be loaded (https://files.test/img/empty.jpg) - not used"))
    }

    @Test
    fun catboxErrorIsLoggedWithServerHeaders() {
        net.on("/catbox/user/api.php", "POST") {
            mockwebserver3.MockResponse.Builder().code(412).addHeader("Server", "nginx").addHeader("CF-RAY", "abc-FRA")
                .body("Uploads without an account are disabled\r\n").build()
        }
        net.on("/x0/", "POST") { status(500) }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY.copy(uploadHost = Config.UPLOAD_CATBOX))

        assertTrue(r.isEmpty)
        assertTrue(log.lines.toString(), log.has("catbox upload failed (HTTP 412, 200 bytes sent): Uploads without an account are disabled"))
        assertTrue(log.has("  catbox: Server: nginx"))
        assertTrue(log.has("  catbox: CF-RAY: abc-FRA"))
    }

    @Test
    fun uploadsAreNotRedirected() {
        // a redirect would turn the upload into an empty GET
        net.on("/x0/", "POST") { mockwebserver3.MockResponse.Builder().code(301).addHeader("Location", "/moved/").build() }
        net.on("/moved/") { text("https://files.test/img/a.jpg") }
        serveImages()
        net.on("/catbox/user/api.php", "POST") { text("https://files.test/img/c.jpg") }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY)

        assertEquals("poweramp|catbox.moe", r.source)
        assertEquals(0, net.count("/moved/"))
        assertTrue(log.has("x0.at upload failed (HTTP 301)"))
    }

    @Test
    fun failedHostIsSkippedFor30Minutes() {
        serveImages()
        net.on("/x0/", "POST") { status(502) }
        net.on("/catbox/user/api.php", "POST") { text("https://files.test/img/c.jpg") }
        val resolver = resolver()

        assertEquals("poweramp|catbox.moe", resolver.resolve(Samples.album(1), UPLOAD_ONLY).source)
        assertEquals(1, net.count("/x0/"))

        clock.advance(29 * 60 * 1000L)
        assertEquals("poweramp|catbox.moe", resolver.resolve(Samples.album(2), UPLOAD_ONLY).source)
        assertEquals("x0.at is skipped", 1, net.count("/x0/"))

        clock.advance(2 * 60 * 1000L)
        net.on("/x0/", "POST") { text("https://files.test/img/x.jpg") }
        assertEquals("poweramp|x0.at", resolver.resolve(Samples.album(3), UPLOAD_ONLY).source)
        assertEquals(2, net.count("/x0/"))
    }

    @Test
    fun shortOutageBlocksNeitherTheHostsNorTheAlbumForLong() {
        serveImages()
        net.on("/x0/", "POST") { text("https://files.test/img/a.jpg") }
        val resolver = resolver()

        net.offline = true   // e.g. subway, Wi-Fi handover: every request fails at once
        assertTrue(resolver.resolve(Samples.album(1), UPLOAD_ONLY).isEmpty)
        assertTrue(log.has("x0.at: request failed (UnknownHostException"))
        assertTrue(log.has("catbox.moe: request failed (UnknownHostException"))

        net.offline = false
        clock.advance(30 * 1000L)
        assertTrue("not at once", resolver.resolve(Samples.album(1), UPLOAD_ONLY).isEmpty)
        assertTrue("hosts skipped for a moment", resolver.resolve(Samples.album(2), UPLOAD_ONLY).isEmpty)
        assertEquals(0, net.requests.size)

        clock.advance(31 * 1000L)   // a minute after the outage - not 10 / 30 minutes
        assertEquals("poweramp|x0.at", resolver.resolve(Samples.album(1), UPLOAD_ONLY).source)
        assertEquals(1, net.count("/x0/"))
        assertEquals(0, net.count("/catbox/user/api.php"))
    }

    @Test
    fun uploadThatCannotBeCheckedSkipsTheHostOnlyForAMinute() {
        var drop = true
        net.on("/x0/", "POST") {
            if (drop) net.offline = true   // the connection drops right after the first upload
            drop = false
            text("https://files.test/img/a.jpg")
        }
        val resolver = resolver()

        assertTrue(resolver.resolve(Samples.album(1), UPLOAD_ONLY).isEmpty)
        assertTrue(log.has("x0.at: the uploaded cover cannot be checked (https://files.test/img/a.jpg) - not used"))

        net.offline = false
        serveImages()
        clock.advance(61 * 1000L)
        assertEquals("poweramp|x0.at", resolver.resolve(Samples.album(2), UPLOAD_ONLY).source)
    }

    @Test
    fun invisibleCharactersInTheImgurClientIdAreDropped() {
        // pasted with a BOM / zero-width space / NBSP: OkHttp would refuse such a header (and the whole lookup failed)
        serveImages()
        net.on("/imgur/3/image", "POST") { json("""{"data":{"link":"https://files.test/img/i.jpg"},"success":true,"status":200}""") }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY.copy(uploadHost = Config.UPLOAD_IMGUR, imgurClientId = "﻿abc​12 3 "))

        assertEquals("poweramp|Imgur", r.source)
        assertEquals("Client-ID abc123", net.last("/imgur/3/image").headers["Authorization"])
    }

    @Test
    fun invisibleImgurClientIdCountsAsNone() {
        serveImages()
        net.on("/x0/", "POST") { text("https://files.test/img/x.jpg") }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY.copy(uploadHost = Config.UPLOAD_IMGUR, imgurClientId = "​ "))

        assertEquals("poweramp|x0.at", r.source)
        assertEquals(0, net.count("/imgur/3/image"))
    }

    @Test
    fun correctedImgurClientIdIsTriedAtOnce() {
        serveImages()
        net.on("/imgur/3/image", "POST") { req ->
            if (req.headers["Authorization"] == "Client-ID good") json("""{"data":{"link":"https://files.test/img/i.jpg"},"success":true}""")
            else status(403, """{"data":{"error":"Invalid client_id"},"success":false,"status":403}""")
        }
        net.on("/x0/", "POST") { status(500) }
        val resolver = resolver()
        val imgur = UPLOAD_ONLY.copy(uploadHost = Config.UPLOAD_IMGUR, imgurClientId = "bad")

        assertTrue(resolver.resolve(TRACK, imgur).isEmpty)
        assertTrue("same settings: waits", resolver.resolve(TRACK, imgur).isEmpty)
        assertEquals(1, net.count("/imgur/3/image"))

        // neither the album's "nothing found" nor Imgur's 30-minute skip outlast the corrected ID
        assertEquals("poweramp|Imgur", resolver.resolve(TRACK, imgur.copy(imgurClientId = "good")).source)
        assertEquals(2, net.count("/imgur/3/image"))
    }

    @Test
    fun clearCacheGivesFailedHostsANewChance() {
        serveImages()
        net.on("/x0/", "POST") { status(502) }
        net.on("/catbox/user/api.php", "POST") { text("https://files.test/img/c.jpg") }
        val resolver = resolver()
        assertEquals("poweramp|catbox.moe", resolver.resolve(Samples.album(1), UPLOAD_ONLY).source)

        resolver.clearCache()

        net.on("/x0/", "POST") { text("https://files.test/img/x.jpg") }
        assertEquals("poweramp|x0.at", resolver.resolve(Samples.album(2), UPLOAD_ONLY).source)
        assertEquals(2, net.count("/x0/"))
    }

    // ---------------------------------------------------------------- one upload per picture

    @Test
    fun samePictureIsUploadedOnceForAllTracksThatShowIt() {
        // Poweramp has no album artist: "X" and "X feat. Y" tracks of one album have different keys
        serveImages()
        var uploads = 0
        net.on("/x0/", "POST") { text("https://files.test/img/${++uploads}.jpg") }
        val resolver = resolver()
        val feat = TRACK.copy(artist = "Daft Punk feat. Romanthony", title = "Too Long")
        assertNotEquals(CoverResolver.keyOf(TRACK), CoverResolver.keyOf(feat))

        val first = resolver.resolve(TRACK, UPLOAD_ONLY)
        clock.advance(60 * 1000L)
        assertEquals("the same upload, with its age", first, resolver.resolve(feat, UPLOAD_ONLY))
        assertEquals(first, resolver().resolve(TRACK.copy(artist = "Daft Punk & Todd Edwards"), UPLOAD_ONLY))   // after a restart
        assertEquals(1, net.count("/x0/"))
        assertEquals(1, net.requests.count { it.url.encodedPath.startsWith("/img/") })

        // another picture or another size is another upload
        assertEquals("https://files.test/img/2.jpg", resolver.resolve(Samples.album(1), UPLOAD_ONLY).url)
        assertEquals("https://files.test/img/3.jpg", resolver.resolve(TRACK.copy(artist = "DP"), UPLOAD_ONLY.copy(coverMaxPx = 300)).url)
    }

    private val fourDays = 4 * 24 * 3600 * 1000L

    private fun writePictureUpload(url: String, ageMs: Long) =
        file.writeText("${CoverResolver.pictureKey(pictureOf(TRACK), 512)}\t$url\tpoweramp|x0.at\t${clock.wall - ageMs}\n")

    @Test
    fun oldUploadOfThePictureIsCheckedBeforeItIsUsedAgain() {
        writePictureUpload("https://files.test/img/old.jpg", fourDays)
        net.on("/img/old.jpg") { bytes(JPEG, code = 206) }
        val resolver = resolver()

        val r = resolver.resolve(TRACK, UPLOAD_ONLY)

        assertEquals(CoverResult("https://files.test/img/old.jpg", "poweramp|x0.at", clock.wall - fourDays), r)
        assertEquals(1, net.count("/img/old.jpg"))
        assertEquals(0, net.count("/x0/"))
        assertEquals("checked in this process", r, resolver.peekCache(TRACK))
    }

    @Test
    fun goneUploadOfThePictureIsReplaced() {
        writePictureUpload("https://files.test/img/old.jpg", fourDays)
        net.on("/img/old.jpg") { status(404) }
        serveImages()
        net.on("/x0/", "POST") { text("https://files.test/img/new.jpg") }

        assertEquals("https://files.test/img/new.jpg", resolver().resolve(TRACK, UPLOAD_ONLY).url)
        assertEquals("https://files.test/img/new.jpg", resolver().resolve(TRACK.copy(artist = "DAFT PUNK feat. X"), UPLOAD_ONLY).url)
        assertEquals(1, net.count("/x0/"))
    }

    @Test
    fun forgetDropsTheUploadForEveryTrackThatSharesIt() {
        serveImages()
        var uploads = 0
        net.on("/x0/", "POST") { text("https://files.test/img/${++uploads}.jpg") }
        val resolver = resolver()
        val feat = TRACK.copy(artist = "Daft Punk feat. Romanthony")
        resolver.resolve(TRACK, UPLOAD_ONLY)
        assertEquals("https://files.test/img/1.jpg", resolver.resolve(feat, UPLOAD_ONLY).url)

        resolver.forget(TRACK)   // Discord cannot show it

        assertTrue(resolver.peekCache(feat).isEmpty)
        assertEquals("uploaded again, not the same URL", "https://files.test/img/2.jpg", resolver.resolve(TRACK, UPLOAD_ONLY).url)
        assertEquals("https://files.test/img/2.jpg", resolver.resolve(feat, UPLOAD_ONLY).url)
        assertEquals("https://files.test/img/2.jpg", resolver().peekCache(feat).url)
        assertEquals(2, net.count("/x0/"))
    }

    @Test
    fun httpAnswerIsUpgradedToHttps() {
        serveImages()
        net.on("/x0/", "POST") { text("  http://files.test/img/a.jpg\r\n") }

        assertEquals("https://files.test/img/a.jpg", resolver().resolve(TRACK, UPLOAD_ONLY).url)
    }

    @Test
    fun answersThatAreNoSingleHttpsUrlAreRejected() {
        serveImages()
        for (answer in listOf("<html>Error</html>", "https://files.test/img/a.jpg https://files.test/img/b.jpg",
            "ftp://files.test/img/a.jpg", "", "https://files.test/img/" + "a".repeat(250) + ".jpg")) {
            assertEquals(answer, null, UploadHosts.singleHttpsUrl(answer))
        }
        net.on("/x0/", "POST") { text("<html><body>502 Bad Gateway</body></html>") }
        net.on("/catbox/user/api.php", "POST") { text("https://files.test/img/c.jpg\nhttps://files.test/img/d.jpg") }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY)

        assertTrue(r.isEmpty)
        assertTrue(log.has("x0.at upload failed (HTTP 200): <html><body>502 Bad Gateway</body></html>"))
        assertEquals("no picture was checked", 0, net.requests.count { it.method == "GET" })
    }

    @Test
    fun tooLongUploadUrlLetsTheNextHostStandIn() {
        serveImages()
        net.on("/x0/", "POST") { text("https://files.test/img/" + "a".repeat(240) + ".jpg") }
        net.on("/catbox/user/api.php", "POST") { text("https://files.test/img/c.jpg") }

        assertEquals("https://files.test/img/c.jpg", resolver().resolve(TRACK, UPLOAD_ONLY).url)
    }

    @Test
    fun rawPngIsUploadedWhenTheEncoderFails() {
        serveImages()
        localImage = PNG
        encoded = null
        net.on("/x0/", "POST") { text("https://files.test/img/a.png") }

        assertEquals("https://files.test/img/a.png", resolver().resolve(TRACK, UPLOAD_ONLY).url)
        val body = bodyOf("/x0/")
        assertTrue(body.contains("filename=\"cover.png\""))
        assertTrue(body.contains("Content-Type: image/png"))
    }

    @Test
    fun undecodableOrHugeRawPicturesAreNotUploaded() {
        net.on("/x0/", "POST") { text("https://files.test/img/a.jpg") }
        encoded = null

        localImage = Samples.GIF   // only JPEG / PNG are uploaded as they are
        assertTrue(resolver().resolve(Samples.album(1), UPLOAD_ONLY).isEmpty)

        localImage = JPEG.copyOf(5 * 1024 * 1024 + 1)
        assertTrue(resolver().resolve(Samples.album(2), UPLOAD_ONLY).isEmpty)

        assertEquals(0, net.count("/x0/"))
    }

    @Test
    fun nothingIsUploadedWhenSwitchedOffOrForStreams() {
        net.on("/x0/", "POST") { text("https://files.test/img/a.jpg") }
        serveImages()
        val resolver = resolver()

        assertTrue(resolver.resolve(Samples.album(1), UPLOAD_ONLY.copy(uploadHost = Config.UPLOAD_OFF)).isEmpty)
        assertTrue(resolver.resolve(Samples.album(2), UPLOAD_ONLY.copy(localCovers = false)).isEmpty)
        assertTrue(resolver.resolve(Samples.album(3), UPLOAD_ONLY.copy(coverEnabled = false)).isEmpty)
        assertTrue(resolver.resolve(Samples.album(4).copy(isStream = true, path = "https://radio.example/stream"), UPLOAD_ONLY).isEmpty)
        localImage = null
        assertTrue(resolver.resolve(Samples.album(5), UPLOAD_ONLY).isEmpty)

        assertEquals(0, net.count("/x0/"))
    }

    @Test
    fun failingLocalSourceOrEncoderNeverThrows() {
        net.on("/x0/", "POST") { text("https://files.test/img/a.jpg") }
        val throwingLocal = CoverResolver(net.client, File(tmp.root, "a.tsv"), { throw SecurityException("no access") },
            { _, _ -> ENCODED_JPEG }, clock, log, net.endpoints())
        assertTrue(throwingLocal.resolve(TRACK, UPLOAD_ONLY).isEmpty)
        assertTrue(log.has("Local cover cannot be read (SecurityException: no access)"))

        serveImages()
        val throwingEncoder = CoverResolver(net.client, File(tmp.root, "b.tsv"), { JPEG },
            { _, _ -> throw IllegalStateException("decoder broken") }, clock, log, net.endpoints())
        // the raw JPEG is uploaded instead
        assertEquals("https://files.test/img/a.jpg", throwingEncoder.resolve(TRACK, UPLOAD_ONLY).url)
    }

    @Test
    fun unreachableHostsGiveAnEmptyResult() {
        val dead = CoverEndpoints(x0 = "http://127.0.0.1:1/", catbox = "http://127.0.0.1:1/api.php")
        val resolver = CoverResolver(net.client, File(tmp.root, "c.tsv"), { JPEG }, { _, _ -> ENCODED_JPEG }, clock, log, dead)

        assertTrue(resolver.resolve(TRACK, UPLOAD_ONLY).isEmpty)
        assertTrue(log.has("x0.at: request failed"))
    }

    @Test
    fun sameAlbumResolvedTwiceAtOnceIsUploadedOnce() {
        serveImages()
        val entered = CountDownLatch(1)
        net.on("/x0/", "POST") {
            entered.countDown()
            Thread.sleep(300)
            text("https://files.test/img/a.jpg")
        }
        val resolver = resolver()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit<CoverResult> { resolver.resolve(TRACK, UPLOAD_ONLY) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = pool.submit<CoverResult> { resolver.resolve(TRACK.copy(title = "Aerodynamic"), UPLOAD_ONLY) }
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1, net.count("/x0/"))
    }

    @Test
    fun differentAlbumsResolveInParallel() {
        serveImages()
        val inside = AtomicInteger()
        val maxInside = AtomicInteger()
        val uploads = AtomicInteger()
        net.on("/x0/", "POST") {
            maxInside.accumulateAndGet(inside.incrementAndGet(), ::maxOf)
            Thread.sleep(200)
            inside.decrementAndGet()
            text("https://files.test/img/${uploads.incrementAndGet()}.jpg")
        }
        val resolver = resolver()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = (1..4).map { n -> pool.submit<CoverResult> { resolver.resolve(Samples.album(n), UPLOAD_ONLY) } }
                .map { it.get(10, TimeUnit.SECONDS) }
            assertTrue(results.all { it.source == "poweramp|x0.at" })
        } finally {
            pool.shutdownNow()
        }
        assertTrue("uploads ran at the same time", maxInside.get() > 1)
        assertFalse(log.has("failed"))
    }
}
