package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which options the tabs offer as effective, and what an export carries. */
class SettingsHintsTest {
    @Test
    fun powerampsCoverNeedsAnUploadHost() {
        val c = Config(coverEnabled = true, localCovers = true, preferLocal = true, uploadHost = Config.UPLOAD_X0)
        assertTrue(CoverOptions.usesPowerampCover(c))
        assertTrue(CoverOptions.prefersPowerampCover(c))
        // "Off (online lookup only)": every track is looked up online, "prefer" changes nothing
        val off = c.copy(uploadHost = Config.UPLOAD_OFF)
        assertFalse(CoverOptions.usesPowerampCover(off))
        assertFalse(CoverOptions.prefersPowerampCover(off))
        assertFalse(CoverOptions.prefersPowerampCover(c.copy(localCovers = false)))
        assertFalse(CoverOptions.prefersPowerampCover(c.copy(coverEnabled = false)))
        assertFalse(CoverOptions.prefersPowerampCover(c.copy(preferLocal = false)))
        assertTrue(CoverOptions.usesPowerampCover(c.copy(preferLocal = false, uploadHost = Config.UPLOAD_IMGUR)))
    }

    @Test
    fun anExportNamesTheKeysItCarries() {
        assertEquals(emptyList<String>(), ExportKeys.services(Config()))
        assertEquals(emptyList<String>(), ExportKeys.services(Config(imgurClientId = "  ", spotifySecret = "")))
        val all = Config(imgurClientId = "abc123", spotifyId = "id", spotifySecret = "secret", discogsToken = "token")
        assertEquals(listOf("Imgur", "Spotify", "Discogs"), ExportKeys.services(all))
        assertEquals(listOf("Spotify"), ExportKeys.services(Config(spotifySecret = "secret")))
        assertEquals(listOf("Discogs"), ExportKeys.services(Config(discogsToken = "token")))
    }
}
