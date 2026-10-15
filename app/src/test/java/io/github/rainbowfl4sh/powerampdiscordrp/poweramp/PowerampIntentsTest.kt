package io.github.rainbowfl4sh.powerampdiscordrp.poweramp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PowerampIntentsTest {
    /** The track bundle exactly as Poweramp 1025 / 1032 fills it (yu0.H in the decompiled build). */
    private fun trackBundle(
        fileType: Int = 1,
        path: String = "/storage/emulated/0/Music/Queen/A Night at the Opera/11 Bohemian Rhapsody.flac",
        withFormat: Boolean = true,
    ) = Bundle().apply {
        putLong("id", 77)
        putLong("realId", 1234)
        putInt("cat", 10)
        putParcelable("catUri", Uri.parse("content://com.maxmpz.audioplayer.data/folders/3/files"))
        putBoolean("supportsCatNav", true)
        putInt("fileType", fileType)
        putBoolean("isCue", false)
        putString("path", path)
        putString("title", "Bohemian Rhapsody")
        putString("album", "A Night at the Opera")
        putString("artist", "Queen")
        putInt("durMs", 354_321)
        putInt("rating", 4)
        putInt("dur", 354)
        putInt("posInList", 10)
        putInt("listSize", 12)
        if (withFormat) {
            putInt("sampleRate", 44_100)
            putInt("channels", 2)
            putInt("bitsPerSample", 16)
            putInt("bitRate", 1_411_200)
            putString("codec", "FLAC")
        }
        putInt("flags", 0)
        putInt("lyricsState", 1)
    }

    /** TRACK_CHANGED extras: the track keys at the top level plus "track", "api", "pos", "ts". */
    private fun trackChanged(track: Bundle) = Bundle(track).apply {
        putBundle("track", track)
        putInt("api", 700)
        putInt("pos", 12)
        putLong("ts", 1_700_000_000_000)
    }

    @Test
    fun readsEveryFieldOfTheTrackBundle() {
        val t = PowerampIntents.trackOf(trackChanged(trackBundle()))!!
        assertEquals(77L, t.id)
        assertEquals(1234L, t.realId)
        assertEquals("Bohemian Rhapsody", t.title)
        assertEquals("Queen", t.artist)
        assertEquals("A Night at the Opera", t.album)
        assertTrue(t.path.endsWith("11 Bohemian Rhapsody.flac"))
        assertEquals(354_321L, t.durationMs)
        assertEquals(1, t.fileType)
        assertFalse(t.isStream)
        assertEquals(10, t.posInList)
        assertEquals(12, t.listSize)
        assertEquals(44_100, t.sampleRate)
        assertEquals(2, t.channels)
        assertEquals(16, t.bitsPerSample)
        assertEquals(1411, t.bitRate)   // bit/s -> kbit/s, rounded like Poweramp's own display
        assertEquals("FLAC", t.codec)
        assertEquals(4, t.rating)
    }

    @Test
    fun audioFormatIsZeroWhenPowerampDoesNotKnowIt() {
        val t = PowerampIntents.trackFromBundle(trackBundle(withFormat = false))
        assertEquals(0, t.sampleRate)
        assertEquals(0, t.bitRate)
        assertEquals("", t.codec)
    }

    @Test
    fun topLevelKeysAreUsedWithoutTheTrackBundle() {
        val extras = trackChanged(trackBundle()).apply { remove("track") }
        assertEquals("Bohemian Rhapsody", PowerampIntents.trackOf(extras)!!.title)
    }

    @Test
    fun noTrackWithoutTrackData() {
        val status = Bundle().apply {
            putInt("api", 700)
            putInt("state", 1)
            putLong("ts", 1_700_000_000_000)
        }
        assertNull(PowerampIntents.trackOf(status))
    }

    @Test
    fun durationFallsBackToSeconds() {
        val missing = trackBundle().apply { remove("durMs") }
        assertEquals(354_000L, PowerampIntents.trackFromBundle(missing).durationMs)

        val zero = trackBundle().apply { putInt("durMs", 0) }
        assertEquals(354_000L, PowerampIntents.trackFromBundle(zero).durationMs)

        val none = trackBundle().apply {
            remove("durMs")
            remove("dur")
        }
        assertEquals(0L, PowerampIntents.trackFromBundle(none).durationMs)
    }

    @Test
    fun detectsStreams() {
        // PowerampAPI.Track.FileType.TYPE_STREAM = 23
        assertTrue(PowerampIntents.trackFromBundle(trackBundle(fileType = 23, path = "radio")).isStream)
        assertTrue(PowerampIntents.trackFromBundle(trackBundle(fileType = -1, path = "https://stream.example.org/live.mp3")).isStream)
        assertTrue(PowerampIntents.isStream(0, "HTTP://radio.example.org:8000/"))
        assertFalse(PowerampIntents.isStream(0, "/storage/emulated/0/Music/http-song.mp3"))
        assertFalse(PowerampIntents.isStream(1, "content://com.android.externalstorage.documents/tree/x"))
    }

    @Test
    fun wrongTypesReadAsMissing() {
        val b = trackBundle().apply {
            putString("durMs", "not a number")
            putInt("title", 5)
            putString("rating", "9")
        }
        val t = PowerampIntents.trackFromBundle(b)
        assertEquals(354_000L, t.durationMs)
        assertEquals("", t.title)
        assertEquals(5, t.rating)   // clamped to 0..5
    }

    @Test
    fun playStateFromStateExtra() {
        fun state(v: Int) = PowerampIntents.playStateOf(Bundle().apply { putInt("state", v) })
        assertEquals(PlayState.PLAYING, state(1))
        assertEquals(PlayState.PAUSED, state(2))
        assertEquals(PlayState.STOPPED, state(0))
        assertEquals(PlayState.STOPPED, state(-1))
    }

    @Test
    fun playStateFromStatusChangedBroadcast() {
        // STATUS_CHANGED as sent by build 1032: state wins over the deprecated status / paused extras
        val paused = Bundle().apply {
            putInt("api", 700)
            putInt("pos", 30)
            putLong("ts", 1_700_000_000_000)
            putInt("state", 2)
            putInt("status", 2)
            putBoolean("paused", true)
        }
        assertEquals(PlayState.PAUSED, PowerampIntents.playStateOf(paused))
    }

    @Test
    fun playStateFromLegacyExtras() {
        assertEquals(PlayState.STOPPED, PowerampIntents.playStateOf(Bundle().apply { putInt("status", 3) }))
        assertEquals(PlayState.PAUSED, PowerampIntents.playStateOf(Bundle().apply { putBoolean("paused", true) }))
        assertEquals(PlayState.PLAYING, PowerampIntents.playStateOf(Bundle().apply { putBoolean("paused", false) }))
        assertEquals(PlayState.PLAYING, PowerampIntents.playStateOf(Bundle().apply { putInt("status", 1) }))
        assertNull(PowerampIntents.playStateOf(Bundle().apply { putInt("api", 700) }))
    }

    @Test
    fun positionAndTimestamp() {
        val b = Bundle().apply {
            putInt("pos", 75)
            putLong("ts", 1_700_000_000_123)
        }
        assertEquals(75_000L, PowerampIntents.positionMs(b))
        assertEquals(1_700_000_000_123L, PowerampIntents.timestamp(b))
        assertNull(PowerampIntents.positionMs(Bundle()))
        assertNull(PowerampIntents.positionMs(Bundle().apply { putInt("pos", -1) }))
        assertEquals(0L, PowerampIntents.timestamp(Bundle()))
    }

    @Test
    fun albumArtUri() {
        assertEquals("content://com.maxmpz.audioplayer.aa/files/1234", Poweramp.albumArtUri(1234).toString())
    }

    @Test
    fun intentExtrasRoundTrip() {
        // What the receiver actually gets: the extras of an Intent
        val intent = Intent(Poweramp.ACTION_TRACK_CHANGED_EXPLICIT).putExtras(trackChanged(trackBundle()))
        assertEquals("Queen", PowerampIntents.trackOf(intent.extras!!)!!.artist)
    }
}
