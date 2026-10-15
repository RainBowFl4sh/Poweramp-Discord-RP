package io.github.rainbowfl4sh.powerampdiscordrp.core.assets

import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.Proxy

class DiscordAppInfoTest {
    private val server = MockWebServer().apply { start() }
    private val http = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
    private val clock = object : Clock {
        var now = 1_000_000L
        override fun nowMs() = now
        override fun wallMs() = 1_760_000_000_000L + now
    }
    private val api = server.url("/api/v10").toString().trimEnd('/')
    private val info = DiscordAppInfo(http, clock, apiBase = api, cdnBase = "https://cdn.test")

    @After
    fun tearDown() = server.close()

    private fun answer(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    // the real answers of Discord's API for the Poweramp application (trimmed)
    private val assets = """[{"id":"1559555620745650196","type":1,"name":"poweramp"},""" +
        """{"id":"1559555664370729081","type":1,"name":"play"},{"id":"1559555811108192357","type":1,"name":"pause"}]"""
    private val rpc = """{"id":"$APP","name":"Poweramp","icon":"df38aee64c45dd0fee983896f460d100","description":"x","bot_public":true}"""

    @Test
    fun namesAndPicturesOfTheApplication() {
        answer(200, assets)
        answer(200, rpc)

        val i = info.fetch(APP)!!

        assertEquals("Poweramp", i.name)
        assertEquals(
            mapOf(
                "poweramp" to "https://cdn.test/app-assets/$APP/1559555620745650196.png",
                "play" to "https://cdn.test/app-assets/$APP/1559555664370729081.png",
                "pause" to "https://cdn.test/app-assets/$APP/1559555811108192357.png",
            ),
            i.images,
        )
        assertEquals("/api/v10/oauth2/applications/$APP/assets", server.takeRequest().url.encodedPath)
        assertEquals("/api/v10/applications/$APP/rpc", server.takeRequest().url.encodedPath)
        assertEquals(i, info.peek(APP))
    }

    @Test
    fun keptForHoursThenLookedUpAgain() {
        answer(200, assets)
        answer(200, rpc)
        val first = info.fetch(APP)
        clock.now += 60 * 60 * 1000L
        assertEquals(first, info.fetch(APP))   // from the cache
        assertEquals(2, server.requestCount)

        clock.now += 6 * 60 * 60 * 1000L
        answer(200, """[{"id":"1559555664370729081","type":1,"name":"play"}]""")
        answer(200, rpc)
        assertEquals(setOf("play"), info.fetch(APP)!!.images.keys)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun aFailedLookupIsRetriedOnlyAfterAPause() {
        answer(503, "")
        assertNull(info.fetch(APP))
        assertNull(info.fetch(APP))   // no new request yet
        assertEquals(1, server.requestCount)

        clock.now += 10 * 60 * 1000L
        answer(200, assets)
        answer(200, rpc)
        assertEquals(3, info.fetch(APP)!!.images.size)
    }

    @Test
    fun anOldListStaysInUseWhenTheNewLookupFails() {
        answer(200, assets)
        answer(200, rpc)
        val first = info.fetch(APP)
        clock.now += 7 * 60 * 60 * 1000L
        answer(500, "")
        assertEquals(first, info.fetch(APP))
        assertEquals(first, info.peek(APP))
    }

    @Test
    fun withoutTheNameThePicturesStillWork() {
        answer(200, assets)
        answer(404, """{"message":"Unknown Application","code":10002}""")
        val i = info.fetch(APP)!!
        assertEquals("", i.name)
        assertEquals(3, i.images.size)
    }

    @Test
    fun brokenEntriesAreSkipped() {
        answer(200, """[{"id":"x","name":"play"},{"name":"pause"},{"id":"1559555811108192357","name":"pause"},"junk"]""")
        answer(200, rpc)
        assertEquals(setOf("pause"), info.fetch(APP)!!.images.keys)
    }

    @Test
    fun noRequestForSomethingThatIsNoApplicationId() {
        assertNull(info.fetch("abc"))
        assertNull(info.fetch(""))
        assertEquals(0, server.requestCount)
        assertNull(info.peek(APP))
    }

    @Test
    fun anUnexpectedAnswerCountsAsFailed() {
        answer(200, """{"message":"401: Unauthorized"}""")
        assertNull(info.fetch(APP))
    }

    private companion object {
        const val APP = "1559554394301800528"
    }
}
