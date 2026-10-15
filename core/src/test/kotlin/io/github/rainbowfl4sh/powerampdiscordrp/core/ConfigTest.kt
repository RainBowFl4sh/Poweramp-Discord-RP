package io.github.rainbowfl4sh.powerampdiscordrp.core

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {
    @Test
    fun jsonRoundTripKeepsValues() {
        val c = Config(details = "%artist% - %title%", uploadHost = Config.UPLOAD_CATBOX, coverMaxPx = 300)
        assertEquals(c, Config.fromJson(c.toJson()))
    }

    @Test
    fun missingAndUnknownKeysUseDefaults() {
        val c = Config.fromJson("""{"details":"x","somethingNew":42}""")!!
        assertEquals("x", c.details)
        assertEquals(Config().state, c.state)
    }

    @Test
    fun rotationIntervalIsStoredAndOldFilesGetTheDefault() {
        val c = Config(details = "%artist% || %title%", rotationSeconds = 25)
        assertTrue("\"rotationSeconds\": 25" in c.toJson())
        assertEquals(c, Config.fromJson(c.toJson()))
        assertEquals(25, Config.fromJson(c.forExport().toJson())!!.rotationSeconds)
        // an export of an older version has no such key
        assertEquals(10, Config.fromJson("""{"details":"%title%","refreshSeconds":30}""")!!.rotationSeconds)
        assertEquals(10, Config().rotationSeconds)
    }

    @Test
    fun rotationIntervalIsClampedWhereItIsUsed() {
        assertEquals(10, Config().effectiveRotationSeconds())
        assertEquals(Config.ROTATION_MIN_SECONDS, Config(rotationSeconds = 0).effectiveRotationSeconds())
        assertEquals(5, Config(rotationSeconds = 4).effectiveRotationSeconds())
        assertEquals(60, Config(rotationSeconds = 61).effectiveRotationSeconds())
        assertEquals(Config.ROTATION_MAX_SECONDS, Config(rotationSeconds = Int.MAX_VALUE).effectiveRotationSeconds())
        // the stored value stays as it is (an import is not changed behind the user's back)
        assertEquals(2, Config.fromJson("""{"rotationSeconds":2}""")!!.rotationSeconds)
    }

    @Test
    fun invalidJsonIsRejected() {
        assertNull(Config.fromJson("not json"))
    }

    @Test
    fun clientIdSelection() {
        val own = "123456789012345678"
        assertEquals("999999999999999999", Config().effectiveClientId("999999999999999999"))
        assertEquals(own, Config(useCustomApp = true, customClientId = own).effectiveClientId("999999999999999999"))
        assertEquals("999999999999999999", Config(useCustomApp = true, customClientId = "abc").effectiveClientId("999999999999999999"))
        assertTrue(Config.isValidClientId(own))
        assertFalse(Config.isValidClientId("12345"))
    }

    @Test
    fun titleLink() {
        assertEquals(Config.DEFAULT_TITLE_LINK, Config().effectiveTitleLink())
        assertEquals("", Config(titleLink = false).effectiveTitleLink())
        assertEquals("https://x/%title%", Config(titleLinkCustom = true, titleLinkUrl = " https://x/%title% ").effectiveTitleLink())
    }
}
