package io.github.rainbowfl4sh.powerampdiscordrp.core.update

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Proxy

class UpdateCheckerTest {
    private val server = MockWebServer().apply { start() }
    private val http = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
    private val repo = "RainBowFl4sh/Poweramp-Discord-RP"
    private val checker = UpdateChecker(http, repo, apiBase = server.url("/").toString())

    @After
    fun tearDown() = server.close()

    private fun answer(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun release(
        tag: String = "v1.2.3",
        name: String = "Poweramp Discord RP 1.2.3",
        assets: String = """
            {"url":"https://api.github.com/repos/$repo/releases/assets/11","id":11,"name":"checksums.txt","label":"",
             "content_type":"text/plain","state":"uploaded","size":180,"download_count":3,
             "browser_download_url":"https://github.com/$repo/releases/download/v1.2.3/checksums.txt"},
            {"url":"https://api.github.com/repos/$repo/releases/assets/12","id":12,"name":"poweramp-discord-rp-1.2.3.APK","label":"",
             "content_type":"application/vnd.android.package-archive","state":"uploaded","size":4567890,"download_count":42,
             "digest":"sha256:0123","browser_download_url":"https://github.com/$repo/releases/download/v1.2.3/poweramp-discord-rp-1.2.3.APK"},
            {"id":13,"name":"poweramp-discord-rp-1.2.3-debug.apk",
             "browser_download_url":"https://github.com/$repo/releases/download/v1.2.3/poweramp-discord-rp-1.2.3-debug.apk"}
        """,
        body: String = "### New\r\n- Covers from x0.at\r\n",
    ) = """
        {"url":"https://api.github.com/repos/$repo/releases/250000001","assets_url":"https://api.github.com/repos/$repo/releases/250000001/assets",
         "html_url":"https://github.com/$repo/releases/tag/$tag","id":250000001,
         "author":{"login":"RainBowFl4sh","id":1,"type":"User"},"node_id":"RE_kwDO","tag_name":"$tag","target_commitish":"main",
         "name":"$name","draft":false,"immutable":false,"prerelease":false,"created_at":"2026-10-01T10:00:00Z",
         "published_at":"2026-10-01T10:05:00Z","assets":[$assets],
         "tarball_url":"https://api.github.com/repos/$repo/tarball/$tag","zipball_url":"https://api.github.com/repos/$repo/zipball/$tag",
         "body":"$body"}
    """.trimIndent()

    @Test
    fun latestReleaseIsParsed() {
        answer(200, release())

        val r = checker.latest()!!

        assertEquals("1.2.3", r.version)
        assertEquals("https://github.com/$repo/releases/tag/v1.2.3", r.pageUrl)
        assertEquals("https://github.com/$repo/releases/download/v1.2.3/poweramp-discord-rp-1.2.3.APK", r.apkUrl)
        assertEquals("### New\n- Covers from x0.at", r.notes)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/repos/$repo/releases/latest", req.url.encodedPath)
        assertEquals("application/vnd.github+json", req.headers["Accept"])
    }

    @Test
    fun versionFromTheTitleWhenTheTagHasNone() {
        answer(200, release(tag = "latest-build", name = "Release 1.3r (beta)"))

        assertEquals("1.3", checker.latest()!!.version)
    }

    @Test
    fun plainNumberTagAndSuffixes() {
        answer(200, release(tag = "v2", name = "Second release"))
        assertEquals("2", checker.latest()!!.version)

        // a pre-release keeps its part: compared as such (and never taken for the release of the same number)
        answer(200, release(tag = "v1.4.0-beta.2", name = "x"))
        assertEquals("1.4.0-beta.2", checker.latest()!!.version)

        answer(200, release(tag = "v1.4.0-rc1+build.7", name = "x"))
        assertEquals("1.4.0-rc1", checker.latest()!!.version)

        answer(200, release(tag = "latest", name = "Version 1.4.1 - more covers"))
        assertEquals("1.4.1", checker.latest()!!.version)
    }

    @Test
    fun betaUsersAreOfferedTheRelease() {
        // build.yml: tag v1.1.0-beta1 -> versionName "1.1.0-beta1"; /releases/latest then returns the final v1.1.0
        answer(200, release(tag = "v1.1.0", name = "Poweramp Discord RP 1.1.0"))
        val release = checker.latest()!!

        assertTrue(UpdateChecker.isNewer(release.version, "1.1.0-beta1"))
        assertFalse(UpdateChecker.isNewer(release.version, "1.1.0"))
    }

    @Test
    fun withoutVersionThereIsNoRelease() {
        answer(200, release(tag = "nightly", name = "Nightly build"))

        assertNull(checker.latest())
    }

    @Test
    fun releaseWithoutApk() {
        answer(200, release(assets = """{"id":1,"name":"source.zip","browser_download_url":"https://github.com/$repo/releases/download/v1.2.3/source.zip"}"""))
        assertEquals("", checker.latest()!!.apkUrl)

        answer(200, release(assets = ""))
        assertEquals("", checker.latest()!!.apkUrl)
    }

    @Test
    fun apkFromSomewhereElseIsNotOffered() {
        answer(200, release(assets = """{"id":1,"name":"app.apk","browser_download_url":"https://evil.example/app.apk"}"""))

        assertEquals("", checker.latest()!!.apkUrl)
    }

    @Test
    fun errorsGiveNull() {
        answer(404, """{"message":"Not Found","documentation_url":"https://docs.github.com/rest/releases/releases#get-the-latest-release","status":"404"}""")
        assertNull(checker.latest())

        answer(403, """{"message":"API rate limit exceeded"}""")
        assertNull(checker.latest())

        answer(200, "<html>not json</html>")
        assertNull(checker.latest())

        answer(200, "[]")
        assertNull(checker.latest())

        assertNull(UpdateChecker(http, repo, apiBase = "http://127.0.0.1:1").latest())
    }

    private fun reasonOf(result: UpdateResult): UpdateResult.Reason = (result as UpdateResult.Failed).reason

    @Test
    fun checkTellsWhyThereIsNoRelease() {
        answer(200, release())
        assertEquals("1.2.3", (checker.check() as UpdateResult.Found).release.version)

        // GitHub answered: not "no connection"
        answer(404, """{"message":"Not Found","status":"404"}""")
        assertEquals(UpdateResult.Reason.NO_RELEASE, reasonOf(checker.check()))

        answer(500, "oops")
        val bad = checker.check() as UpdateResult.Failed
        assertEquals(UpdateResult.Reason.BAD_ANSWER, bad.reason)
        assertEquals("HTTP 500", bad.detail)

        answer(200, "<html>not json</html>")
        assertEquals(UpdateResult.Reason.BAD_ANSWER, reasonOf(checker.check()))
        answer(200, release(tag = "nightly", name = "Nightly build"))
        assertEquals(UpdateResult.Reason.BAD_ANSWER, reasonOf(checker.check()))

        // nobody answered
        assertEquals(UpdateResult.Reason.OFFLINE, reasonOf(UpdateChecker(http, repo, apiBase = "http://127.0.0.1:1").check()))
    }

    @Test
    fun rateLimitIsNotAConnectionProblem() {
        // primary limit: 60 requests an hour per IP address without an account (shared behind carrier NAT)
        server.enqueue(
            MockResponse.Builder().code(403).addHeader("X-RateLimit-Remaining", "0")
                .body("""{"message":"API rate limit exceeded for 203.0.113.7."}""").build(),
        )
        assertEquals(UpdateResult.Reason.RATE_LIMITED, reasonOf(checker.check()))
        // secondary limit
        server.enqueue(MockResponse.Builder().code(403).addHeader("Retry-After", "60").body("{}").build())
        assertEquals(UpdateResult.Reason.RATE_LIMITED, reasonOf(checker.check()))
        server.enqueue(MockResponse.Builder().code(429).body("{}").build())
        assertEquals(UpdateResult.Reason.RATE_LIMITED, reasonOf(checker.check()))
        // a 403 for something else
        server.enqueue(MockResponse.Builder().code(403).addHeader("X-RateLimit-Remaining", "42").body("{}").build())
        assertEquals(UpdateResult.Reason.BAD_ANSWER, reasonOf(checker.check()))
    }

    @Test
    fun isNewerComparesNumerically() {
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.2"))
        assertTrue(UpdateChecker.isNewer("v1.2.4", "1.2.3"))
        assertTrue(UpdateChecker.isNewer("1.2.3", "v1.2"))
        assertTrue(UpdateChecker.isNewer("2", "1.99.99"))
        assertTrue(UpdateChecker.isNewer("1.3.0-beta", "1.2.9"))
        assertTrue(UpdateChecker.isNewer("1.0.1", ""))
        assertFalse(UpdateChecker.isNewer("1.2.3", "1.2.3"))
        assertFalse(UpdateChecker.isNewer("1.2", "1.2.0"))
        assertFalse(UpdateChecker.isNewer("1.2.3-beta", "1.2.3"))
        assertFalse(UpdateChecker.isNewer("1.9.9", "1.10"))
        assertFalse(UpdateChecker.isNewer("", "1.0"))
        assertFalse(UpdateChecker.isNewer("garbage", "1.0"))
    }

    @Test
    fun isNewerOrdersPreReleasesLikeSemanticVersioning() {
        // the release is newer than its pre-releases
        assertTrue(UpdateChecker.isNewer("1.1.0", "1.1.0-beta1"))
        assertTrue(UpdateChecker.isNewer("v1.1.0", "V1.1.0-RC2"))
        assertTrue(UpdateChecker.isNewer("1.1", "1.1.0-beta1"))
        assertFalse(UpdateChecker.isNewer("1.1.0-beta2", "1.1.0"))
        // pre-releases among themselves, numbers in them numerically
        assertTrue(UpdateChecker.isNewer("1.1.0-beta2", "1.1.0-beta1"))
        assertTrue(UpdateChecker.isNewer("1.1.0-beta10", "1.1.0-beta9"))
        assertTrue(UpdateChecker.isNewer("1.1.0-beta.10", "1.1.0-beta.9"))
        assertTrue(UpdateChecker.isNewer("1.1.0-beta1", "1.1.0-beta"))
        assertTrue(UpdateChecker.isNewer("1.1.0-rc1", "1.1.0-beta3"))
        assertTrue(UpdateChecker.isNewer("1.1.0-beta", "1.1.0-alpha.5"))
        assertFalse(UpdateChecker.isNewer("1.1.0-beta1", "1.1.0-beta2"))
        assertFalse(UpdateChecker.isNewer("1.1.0-beta1", "1.1.0-beta1"))
        assertFalse(UpdateChecker.isNewer("1.1.0-beta01", "1.1.0-beta1"))
        // the numbers still come first; build metadata does not count
        assertTrue(UpdateChecker.isNewer("1.1.1-beta1", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("1.1.0", "1.1.1-beta1"))
        assertFalse(UpdateChecker.isNewer("1.1.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("1.1.0+7", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("1.1.0", "1.1.0+7"))
        assertTrue(UpdateChecker.isNewer("1.1.0+7", "1.1.0-beta1+3"))
    }
}
