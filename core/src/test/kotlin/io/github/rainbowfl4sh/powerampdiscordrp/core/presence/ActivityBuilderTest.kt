package io.github.rainbowfl4sh.powerampdiscordrp.core.presence

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityBuilderTest {
    private val wall = 1_700_000_000_500L
    private val texts = ActivityTexts(
        hidden = Hidden.NO, playing = true, posMs = 60_000, durMs = 200_000,
        details = "Song", state = "Artist", largeText = "Album", smallText = "Playing",
        detailsUrl = "https://example.com/?q=Song",
    )

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.obj(key: String) = getValue(key).jsonObject

    @Test
    fun `hidden texts give no activity`() {
        for (h in Hidden.entries.filter { it != Hidden.NO }) {
            assertNull(ActivityBuilder.build(Config(), texts.copy(hidden = h), "https://img/x.jpg", wall))
        }
    }

    @Test
    fun `full activity with cover and millisecond timestamps`() {
        val c = Config(activityType = Config.ACTIVITY_LISTENING, statusDisplay = Config.STATUS_DISPLAY_DETAILS)
        val a = ActivityBuilder.build(c, texts, "https://img/x.jpg", wall)!!
        assertEquals(ActivityBuilder.ACTIVITY_NAME, a.str("name"))
        assertEquals(2, a.getValue("type").jsonPrimitive.int)
        assertEquals(2, a.getValue("status_display_type").jsonPrimitive.int)
        assertEquals("Song", a.str("details"))
        assertEquals("Artist", a.str("state"))
        assertEquals("https://example.com/?q=Song", a.str("details_url"))

        val ts = a.obj("timestamps")
        assertEquals(wall - 60_000, ts.getValue("start").jsonPrimitive.long)
        assertEquals(wall - 60_000 + 200_000, ts.getValue("end").jsonPrimitive.long)

        val assets = a.obj("assets")
        assertEquals("https://img/x.jpg", assets.str("large_image"))
        assertEquals("Album", assets.str("large_text"))
        assertEquals(ActivityBuilder.ASSET_PLAY, assets.str("small_image"))
        assertEquals("Playing", assets.str("small_text"))
    }

    @Test
    fun `timestamps in seconds`() {
        val c = Config(timestampsInSeconds = true)
        val ts = ActivityBuilder.build(c, texts.copy(durMs = 200_400), null, wall)!!.obj("timestamps")
        val start = (wall - 60_000) / 1000
        assertEquals(start, ts.getValue("start").jsonPrimitive.long)
        assertEquals(start + 200, ts.getValue("end").jsonPrimitive.long)   // duration rounded to whole seconds
        val rounded = ActivityBuilder.build(c, texts.copy(durMs = 200_500), null, wall)!!.obj("timestamps")
        assertEquals(start + 201, rounded.getValue("end").jsonPrimitive.long)
    }

    @Test
    fun `no end without a duration`() {
        val ts = ActivityBuilder.build(Config(), texts.copy(durMs = 0), null, wall)!!.obj("timestamps")
        assertEquals(setOf("start"), ts.keys)
    }

    @Test
    fun `no timestamps while paused or when switched off`() {
        val paused = ActivityBuilder.build(Config(), texts.copy(playing = false), null, wall)!!
        assertFalse("timestamps" in paused)
        assertEquals(ActivityBuilder.ASSET_PAUSE, paused.obj("assets").str("small_image"))
        assertFalse("timestamps" in ActivityBuilder.build(Config(showTimestamps = false), texts, null, wall)!!)
    }

    @Test
    fun `logo without a cover`() {
        for (cover in listOf(null, "", "   ")) {
            val a = ActivityBuilder.build(Config(), texts, cover, wall)!!
            assertEquals(ActivityBuilder.ASSET_LOGO, a.obj("assets").str("large_image"))
        }
    }

    @Test
    fun `empty fields are left out`() {
        val bare = texts.copy(details = "", largeText = "", smallText = "", detailsUrl = "")
        val a = ActivityBuilder.build(Config(), bare, null, wall)!!
        assertFalse("details" in a)
        assertFalse("details_url" in a)
        assertEquals("Artist", a.str("state"))
        val assets = a.obj("assets")
        assertFalse("large_text" in assets)
        assertFalse("small_text" in assets)
        assertEquals(ActivityBuilder.ASSET_PLAY, assets.str("small_image"))

        val noState = ActivityBuilder.build(Config(), texts.copy(state = ""), null, wall)!!
        assertFalse("state" in noState)
    }

    @Test
    fun `no small icon when switched off`() {
        val assets = ActivityBuilder.build(Config(showSmallIcon = false), texts, null, wall)!!.obj("assets")
        assertEquals(setOf("large_image", "large_text"), assets.keys)
    }

    @Test
    fun `playing activity type and status display`() {
        val c = Config(activityType = Config.ACTIVITY_PLAYING, statusDisplay = Config.STATUS_DISPLAY_NAME)
        val a = ActivityBuilder.build(c, texts, null, wall)!!
        assertEquals(0, a.getValue("type").jsonPrimitive.int)
        assertEquals(0, a.getValue("status_display_type").jsonPrimitive.int)
    }

    @Test
    fun `test presence`() {
        val a = ActivityBuilder.test(Config(activityType = Config.ACTIVITY_PLAYING), "1.2.3", "It works!", wall)
        assertEquals(ActivityBuilder.ACTIVITY_NAME, a.str("name"))
        assertEquals(0, a.getValue("type").jsonPrimitive.int)
        assertEquals("Test - Poweramp Discord Rich Presence 1.2.3", a.str("details"))
        assertEquals("It works!", a.str("state"))
        assertEquals(setOf("start"), a.obj("timestamps").keys)
        assertEquals(wall, a.obj("timestamps").getValue("start").jsonPrimitive.long)
        assertEquals(ActivityBuilder.ASSET_LOGO, a.obj("assets").str("large_image"))
        assertEquals(ActivityBuilder.ASSET_PLAY, a.obj("assets").str("small_image"))

        val seconds = ActivityBuilder.test(Config(timestampsInSeconds = true), "", "", wall)
        assertEquals(wall / 1000, seconds.obj("timestamps").getValue("start").jsonPrimitive.long)
        assertEquals("Test - Poweramp Discord Rich Presence", seconds.str("details"))
        assertNotNull(seconds["type"])
        assertFalse("state" in seconds)
    }

    private val images = mapOf(
        ActivityBuilder.ASSET_LOGO to "https://cdn.test/app-assets/1/10.png",
        ActivityBuilder.ASSET_PLAY to "https://cdn.test/app-assets/1/11.png",
        ActivityBuilder.ASSET_PAUSE to "https://cdn.test/app-assets/1/12.png",
    )

    @Test
    fun `art assets are sent as their picture URLs when known`() {
        val playing = ActivityBuilder.build(Config(), texts, null, wall, images)!!.obj("assets")
        assertEquals("https://cdn.test/app-assets/1/10.png", playing.str("large_image"))   // logo without a cover
        assertEquals("https://cdn.test/app-assets/1/11.png", playing.str("small_image"))
        val paused = ActivityBuilder.build(Config(), texts.copy(playing = false), "https://img/x.jpg", wall, images)!!.obj("assets")
        assertEquals("https://img/x.jpg", paused.str("large_image"))   // the cover still wins
        assertEquals("https://cdn.test/app-assets/1/12.png", paused.str("small_image"))

        val test = ActivityBuilder.test(Config(), "1.0.0", "ok", wall, images).obj("assets")
        assertEquals("https://cdn.test/app-assets/1/10.png", test.str("large_image"))
        assertEquals("https://cdn.test/app-assets/1/11.png", test.str("small_image"))
    }

    @Test
    fun `unknown or unusable pictures fall back to the asset key`() {
        assertEquals(ActivityBuilder.ASSET_PLAY, ActivityBuilder.image(ActivityBuilder.ASSET_PLAY, emptyMap()))
        assertEquals(ActivityBuilder.ASSET_PLAY, ActivityBuilder.image(ActivityBuilder.ASSET_PLAY, mapOf("play" to "ftp://x/y.png")))
        val long = "https://cdn.test/" + "x".repeat(300) + ".png"
        assertEquals(ActivityBuilder.ASSET_PLAY, ActivityBuilder.image(ActivityBuilder.ASSET_PLAY, mapOf("play" to long)))
        val noPictures = ActivityBuilder.build(Config(), texts, null, wall)!!.obj("assets")
        assertEquals(ActivityBuilder.ASSET_LOGO, noPictures.str("large_image"))
        assertEquals(ActivityBuilder.ASSET_PLAY, noPictures.str("small_image"))
    }
}
