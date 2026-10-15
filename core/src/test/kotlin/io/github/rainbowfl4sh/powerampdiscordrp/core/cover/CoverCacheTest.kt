package io.github.rainbowfl4sh.powerampdiscordrp.core.cover

import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.FakeNet.Companion.bytes
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.FakeNet.Companion.json
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.FakeNet.Companion.status
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.FakeNet.Companion.text
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.ENCODED_JPEG
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.JPEG
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.NO_SOURCES
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.TRACK
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.UPLOAD_ONLY
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

class CoverCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val net = FakeNet()
    private val clock = FakeClock()
    private val log = RecordingLogger()
    private val file get() = File(tmp.root, "covers.tsv")

    private fun resolver() = CoverResolver(net.client, file, { JPEG }, { _, _ -> ENCODED_JPEG }, clock, log, net.endpoints())

    private val deezer = NO_SOURCES.copy(srcDeezer = true)
    private val deezerCover = "https://cdn-images.dzcdn.net/images/cover/2e01/1000x1000-000000-80-0-0.jpg"

    private fun serveDeezer(cover: String = deezerCover) =
        net.on("/deezer/search/album") {
            json("""{"data":[{"id":1,"title":"Discovery","cover_xl":"$cover","artist":{"id":27,"name":"Daft Punk"},"type":"album"}],"total":1}""")
        }

    private fun serveNothing() {
        net.on("/deezer/search/album") { json("""{"data":[],"total":0}""") }
        net.on("/deezer/search/track") { json("""{"data":[],"total":0}""") }
    }

    private fun lines() = file.readLines().filter { it.isNotEmpty() }

    @After
    fun tearDown() = net.close()

    // ---------------------------------------------------------------- keys

    @Test
    fun keyOfMatchesTheAimpPlugin() {
        // FNV-1a 64 over the UTF-16 units of the lower-cased key, computed independently
        assertEquals("c840ab698b2adc25", CoverResolver.keyOf(TrackInfo(artist = "Daft Punk", album = "Discovery", title = "x")))
        assertEquals("580b5cf835597f6c", CoverResolver.keyOf(TrackInfo(artist = "Björk", album = "Homogenic")))
        assertEquals("23eef83df1fba78a", CoverResolver.keyOf(TrackInfo(title = "Song", path = "/storage/emulated/0/Music/Song.mp3")))
        assertEquals("1c71fd0b2b70d55a", CoverResolver.keyOf(TrackInfo(artist = "Radio X", title = "Live", path = "https://stream.example/x", isStream = true)))
        assertEquals("f3cf1851aa41548b", CoverResolver.keyOf(TrackInfo(artist = "Artist", title = "🎵 Title")))
        assertEquals("5739c219449b9663", CoverResolver.keyOf(TrackInfo()))
    }

    @Test
    fun keyOfIsStableAndSharedByAnAlbum() {
        val a = CoverResolver.keyOf(TRACK)
        assertEquals(16, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
        assertEquals(a, CoverResolver.keyOf(TRACK))
        assertEquals("other track of the album", a, CoverResolver.keyOf(TRACK.copy(title = "Aerodynamic", path = "/x/02.mp3", realId = 7)))
        assertEquals("case does not matter", a, CoverResolver.keyOf(TRACK.copy(artist = "DAFT PUNK", album = "discovery")))
        assertNotEquals(a, CoverResolver.keyOf(TRACK.copy(album = "Homework")))
        // no album: the file, else artist + title
        val noAlbum = TRACK.copy(album = " ")
        assertNotEquals(CoverResolver.keyOf(noAlbum), CoverResolver.keyOf(noAlbum.copy(path = "/other.mp3")))
        val stream = TRACK.copy(album = "", path = "HTTP://radio.example/live")
        assertEquals(CoverResolver.keyOf(stream), CoverResolver.keyOf(stream.copy(path = "https://radio.example/other")))
    }

    // ---------------------------------------------------------------- persistence

    @Test
    fun cachedCoversSurviveARestart() {
        serveDeezer()
        val first = resolver().resolve(TRACK, deezer)
        assertEquals(deezerCover, first.url)
        assertEquals(clock.wall, first.storedWallMs)
        assertEquals(listOf("${CoverResolver.keyOf(TRACK)}\t$deezerCover\tDeezer\t${clock.wall}"), lines())

        val again = resolver()
        assertEquals(first, again.peekCache(TRACK))
        assertEquals(first, again.resolve(TRACK.copy(title = "Aerodynamic"), deezer))
        assertEquals("answered from the cache", 1, net.requests.size)
    }

    @Test
    fun brokenLinesInTheCacheFileAreSkipped() {
        val key = CoverResolver.keyOf(TRACK)
        val other = CoverResolver.keyOf(Samples.album(1))
        file.writeText(
            listOf(
                "garbage", "", "\t\t", "\thttps://x.example/a.jpg\tDeezer\t1", "$other\t", "$other\tnot a url\tDeezer\t5",
                "$other\tjavascript:alert(1)\tDeezer\t5", "$key\thttps://old.example/a.jpg\tiTunes\tnot-a-number",
                "$key\thttps://new.example/a.jpg\tDeezer\t1234\r", "${CoverResolver.keyOf(Samples.album(2))}\thttps://b.example/b.jpg",
                "\u0000\u0001binary",
            ).joinToString("\n"),
        )

        val r = resolver()

        assertEquals(CoverResult("https://new.example/a.jpg", "Deezer", 1234), r.peekCache(TRACK))   // the later line wins
        assertTrue(r.peekCache(Samples.album(1)).isEmpty)
        assertEquals(CoverResult("https://b.example/b.jpg", "", 0), r.peekCache(Samples.album(2)))
    }

    @Test
    fun clearCacheDeletesTheFile() {
        serveDeezer()
        val r = resolver()
        r.resolve(TRACK, deezer)
        assertTrue(file.exists())

        r.clearCache()

        assertFalse(file.exists())
        assertTrue(r.peekCache(TRACK).isEmpty)
        assertEquals(deezerCover, r.resolve(TRACK, deezer).url)
        assertEquals(2, net.requests.size)
    }

    @Test
    fun forgetDropsTheCoverAlsoAfterARestart() {
        serveDeezer()
        val r = resolver()
        r.resolve(TRACK, deezer)

        r.forget(TRACK)

        assertTrue(r.peekCache(TRACK).isEmpty)
        assertTrue(resolver().peekCache(TRACK).isEmpty)
        assertEquals(deezerCover, r.resolve(TRACK, deezer).url)   // resolved again at once (no negative cache)
        assertEquals(2, net.requests.size)
        assertEquals(deezerCover, resolver().peekCache(TRACK).url)
    }

    @Test
    fun bigFileIsCompactedOnLoad() {
        file.writeText((0 until 3100).joinToString("") { "%016x\thttps://c.example/%d.jpg\tDeezer\t%d\n".format(it, it, it) })

        val r = resolver()
        assertEquals(3100, lines().size)   // creating the resolver does not read the file (it may happen on the main thread)
        r.peekCache(TRACK)   // the first use does

        val lines = lines()
        assertEquals(2000, lines.size)
        assertTrue(lines.first().startsWith("%016x\t".format(1100)))
        assertTrue(lines.last().startsWith("%016x\t".format(3099)))
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun fileIsCompactedWhenItGrowsTooBig() {
        file.writeText((0 until 3000).joinToString("") { "%016x\thttps://c.example/%d.jpg\tDeezer\t%d\n".format(it, it, it) })
        val r = resolver()
        assertEquals(3000, lines().size)
        serveDeezer()

        r.resolve(TRACK, deezer)

        val lines = lines()
        assertEquals(2000, lines.size)
        assertTrue(lines.last().startsWith(CoverResolver.keyOf(TRACK) + "\t$deezerCover"))
        assertEquals(deezerCover, resolver().peekCache(TRACK).url)
    }

    @Test
    fun duplicateKeysAreKeptOnceWhenCompacting() {
        val key = CoverResolver.keyOf(TRACK)
        file.writeText((0 until 3500).joinToString("") { "$key\thttps://c.example/$it.jpg\tDeezer\t$it\n" })

        val r = resolver()

        assertEquals("https://c.example/3499.jpg", r.peekCache(TRACK).url)   // the first use reads (and compacts) the file
        assertEquals(listOf("$key\thttps://c.example/3499.jpg\tDeezer\t3499"), lines())
    }

    // ---------------------------------------------------------------- negative cache

    @Test
    fun failedLookupsAreRetriedAfter10Minutes() {
        serveNothing()
        val r = resolver()

        assertTrue(r.resolve(TRACK, deezer).isEmpty)
        val asked = net.requests.size
        assertTrue(asked > 0)

        clock.advance(9 * 60 * 1000L)
        assertTrue(r.resolve(TRACK, deezer).isEmpty)
        assertEquals("not asked again", asked, net.requests.size)

        clock.advance(60 * 1000L)
        serveDeezer()
        assertEquals(deezerCover, r.resolve(TRACK, deezer).url)
        assertTrue(net.requests.size > asked)
        assertFalse("nothing negative is written to the file", lines().any { it.contains("\t\t") })
    }

    @Test
    fun otherCoverSettingsEndTheWaitAtOnce() {
        serveNothing()
        net.on("/itunes/search") {
            json("""{"resultCount":1,"results":[{"artistName":"Daft Punk","artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/x/100x100bb.jpg"}]}""")
        }
        val r = resolver()
        assertTrue(r.resolve(TRACK, deezer).isEmpty)
        val asked = net.requests.size

        assertTrue("settings that change nothing for covers", r.resolve(TRACK, deezer.copy(details = "%album%")).isEmpty)
        assertEquals(asked, net.requests.size)

        // e.g. a source switched on, a key entered, another upload host
        assertEquals("iTunes", r.resolve(TRACK, deezer.copy(srcItunes = true)).source)
    }

    @Test
    fun lookupWithoutNetworkIsRetriedAfterAMinute() {
        serveDeezer()
        val r = resolver()
        net.offline = true
        assertTrue(r.resolve(TRACK, deezer).isEmpty)

        net.offline = false
        clock.advance(50 * 1000L)
        assertTrue(r.resolve(TRACK, deezer).isEmpty)
        assertEquals(0, net.requests.size)

        clock.advance(10 * 1000L)
        assertEquals(deezerCover, r.resolve(TRACK, deezer).url)
    }

    @Test
    fun forgetAlsoDropsTheNegativeEntry() {
        serveNothing()
        val r = resolver()
        r.resolve(TRACK, deezer)
        serveDeezer()

        r.forget(TRACK)

        assertEquals(deezerCover, r.resolve(TRACK, deezer).url)
    }

    // ---------------------------------------------------------------- re-check of old uploads

    private fun writeUpload(url: String, ageMs: Long, source: String = "poweramp|x0.at") =
        file.writeText("${CoverResolver.keyOf(TRACK)}\t$url\t$source\t${clock.wall - ageMs}\n")

    private val fourDays = 4 * 24 * 3600 * 1000L

    @Test
    fun oldUploadIsCheckedOnceAndKeptWhileItIsThere() {
        writeUpload("https://files.test/img/old.jpg", fourDays)
        net.on("/img/old.jpg") { bytes(JPEG, code = 206) }
        val r = resolver()

        assertTrue("not shown before it was checked", r.peekCache(TRACK).isEmpty)
        val res = r.resolve(TRACK, UPLOAD_ONLY)

        assertEquals("https://files.test/img/old.jpg", res.url)
        assertEquals(clock.wall - fourDays, res.storedWallMs)
        assertEquals(1, net.count("/img/old.jpg"))
        assertEquals(0, net.count("/x0/"))
        assertEquals(res, r.peekCache(TRACK))
        r.resolve(TRACK, UPLOAD_ONLY)
        assertEquals("checked once per process", 1, net.count("/img/old.jpg"))
    }

    @Test
    fun goneUploadIsUploadedAgain() {
        writeUpload("https://files.test/img/old.jpg", fourDays)
        net.on("/img/old.jpg") { status(404, "gone") }
        net.on("/img/new.jpg") { bytes(JPEG, code = 206) }
        net.on("/x0/", "POST") { text("https://files.test/img/new.jpg") }

        val res = resolver().resolve(TRACK, UPLOAD_ONLY)

        assertEquals("https://files.test/img/new.jpg", res.url)
        assertEquals(clock.wall, res.storedWallMs)
        assertTrue(log.has("Cached cover is gone (https://files.test/img/old.jpg) - looking it up again"))
        assertEquals("https://files.test/img/new.jpg", resolver().peekCache(TRACK).url)
    }

    @Test
    fun oldUploadIsKeptWhenItCannotBeChecked() {
        writeUpload("https://files.test/img/old.jpg", fourDays)
        net.on("/img/old.jpg") { bytes(JPEG, code = 206) }
        net.on("/x0/", "POST") { text("https://files.test/img/new.jpg") }
        val r = resolver()

        net.offline = true   // our network, not the host: no reason to drop it or to upload again
        assertEquals("https://files.test/img/old.jpg", r.resolve(TRACK, UPLOAD_ONLY).url)
        assertTrue(log.has("Cached cover cannot be checked now (https://files.test/img/old.jpg) - kept"))
        assertTrue("checked again next time", r.peekCache(TRACK).isEmpty)

        net.offline = false
        assertEquals("https://files.test/img/old.jpg", r.resolve(TRACK, UPLOAD_ONLY).url)
        assertEquals(1, net.count("/img/old.jpg"))
        assertEquals(0, net.count("/x0/"))
        assertEquals("https://files.test/img/old.jpg", r.peekCache(TRACK).url)
    }

    @Test
    fun goneUploadComesBackAfterARestartWhenNothingReplacedIt() {
        // e.g. the host answered 404 for a while: the next start checks it again
        writeUpload("https://files.test/img/old.jpg", fourDays)
        net.on("/img/old.jpg") { status(404) }
        net.on("/x0/", "POST") { status(500) }
        net.on("/catbox/user/api.php", "POST") { status(500) }

        assertTrue(resolver().resolve(TRACK, UPLOAD_ONLY).isEmpty)

        net.on("/img/old.jpg") { bytes(JPEG) }
        assertEquals("https://files.test/img/old.jpg", resolver().resolve(TRACK, UPLOAD_ONLY).url)
    }

    @Test
    fun recentUploadsAndOnlineCoversAreNotChecked() {
        writeUpload("https://files.test/img/recent.jpg", 2 * 24 * 3600 * 1000L)
        val recent = resolver()
        assertEquals("https://files.test/img/recent.jpg", recent.peekCache(TRACK).url)
        assertEquals("https://files.test/img/recent.jpg", recent.resolve(TRACK, UPLOAD_ONLY).url)

        writeUpload(deezerCover, 400 * 24 * 3600 * 1000L, source = "Deezer")
        val online = resolver()
        assertEquals(deezerCover, online.peekCache(TRACK).url)
        assertEquals(deezerCover, online.resolve(TRACK, UPLOAD_ONLY).url)

        assertEquals(0, net.requests.size)
    }

    @Test
    fun coversAreOffWhenDisabled() {
        serveDeezer()
        val r = resolver()
        assertTrue(r.resolve(TRACK, deezer.copy(coverEnabled = false)).isEmpty)
        assertEquals(0, net.requests.size)
        assertEquals("no negative entry either", deezerCover, r.resolve(TRACK, deezer).url)
    }

    // ---------------------------------------------------------------- reachable

    @Test
    fun reachableAsksForTheFirstBytesWithGet() {
        net.on("/a.jpg") { bytes(JPEG, code = 206) }

        assertTrue(resolver().reachable(net.url("/a.jpg")))

        val req = net.last("/a.jpg")
        assertEquals("GET", req.method)
        assertEquals("bytes=0-63", req.headers["Range"])
    }

    @Test
    fun reachableChecksStatusAndPictureBytes() {
        val r = resolver()
        val pictures = mapOf("jpg" to JPEG, "png" to Samples.PNG, "gif" to Samples.GIF, "bmp" to Samples.BMP,
            "webp" to Samples.WEBP, "avif" to Samples.AVIF, "heic" to Samples.HEIC)
        for ((name, data) in pictures) {
            net.on("/p.$name") { bytes(data) }
            assertTrue(name, r.reachable(net.url("/p.$name")))
        }
        net.on("/video.mp4") { bytes(Samples.MP4, type = "video/mp4") }
        net.on("/page.html") { bytes(Samples.HTML, type = "text/html") }
        net.on("/empty.jpg") { bytes(ByteArray(0)) }
        net.on("/short.jpg") { bytes(JPEG.copyOf(8)) }
        net.on("/missing.jpg") { bytes(JPEG, code = 404) }
        net.on("/error.jpg") { bytes(JPEG, code = 500) }
        for (path in listOf("/video.mp4", "/page.html", "/empty.jpg", "/short.jpg", "/missing.jpg", "/error.jpg", "/unknown.jpg")) {
            assertFalse(path, r.reachable(net.url(path)))
        }
        assertFalse(r.reachable("not a url"))
        assertFalse(r.reachable("http://127.0.0.1:1/a.jpg"))
    }

    @Test
    fun probeTellsABrokenPictureFromOurNetworkTrouble() {
        net.on("/a.jpg") { bytes(JPEG, code = 206) }
        net.on("/page.html") { bytes(Samples.HTML, type = "text/html") }
        net.on("/missing.jpg") { bytes(JPEG, code = 404) }
        net.on("/error.jpg") { status(503) }
        val r = resolver()

        assertEquals(Probe.OK, r.probe(net.url("/a.jpg")))
        // the host answered, but not with the picture
        for (path in listOf("/page.html", "/missing.jpg", "/error.jpg")) assertEquals(path, Probe.BROKEN, r.probe(net.url(path)))
        assertEquals(Probe.BROKEN, r.probe("not a url"))
        // no answer: says nothing about the picture
        assertEquals(Probe.UNKNOWN, r.probe("http://127.0.0.1:1/a.jpg"))
        net.offline = true
        assertEquals(Probe.UNKNOWN, r.probe(net.url("/a.jpg")))
        assertFalse("reachable = OK only", r.reachable(net.url("/a.jpg")))
    }

    @Test
    fun reachableReadsOnlyTheStartOfABigPicture() {
        // a server that ignores the Range header sends everything: only the first bytes are read
        val big = JPEG.copyOf(8 shl 20)
        net.on("/big.jpg") { bytes(big) }

        val started = System.nanoTime()
        assertTrue(resolver().reachable(net.url("/big.jpg")))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000)
    }

    @Test
    fun imageSniffing() {
        assertTrue(Images.looksLikeImage(JPEG))
        assertTrue(Images.looksLikeImage(Samples.AVIF))
        assertFalse(Images.looksLikeImage(Samples.MP4))
        assertFalse(Images.looksLikeImage(JPEG.copyOf(11)))
        assertTrue(Images.isJpegOrPng(Samples.PNG))
        assertFalse(Images.isJpegOrPng(Samples.GIF))
        assertEquals("png", Images.extension(Samples.PNG))
        assertEquals("jpg", Images.extension(JPEG))
    }
}
