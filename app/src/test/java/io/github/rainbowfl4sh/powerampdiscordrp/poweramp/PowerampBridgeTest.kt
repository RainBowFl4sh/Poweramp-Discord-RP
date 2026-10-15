package io.github.rainbowfl4sh.powerampdiscordrp.poweramp

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import io.github.rainbowfl4sh.powerampdiscordrp.AndroidClock
import io.github.rainbowfl4sh.powerampdiscordrp.Graph
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.TransportError
import io.github.rainbowfl4sh.powerampdiscordrp.service.PresenceService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RobolectricConfig
import kotlin.concurrent.thread

/** The service-start decision for the cases decided before the presence texts are computed. */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34])
class PowerampBridgeTest {
    private val song = TrackInfo(realId = 1, title = "Song", artist = "Artist", path = "/m/song.mp3", durationMs = 1_000)

    @Test
    fun nothingToShowWhenSwitchedOff() {
        val playing = PlaybackSnapshot(PlayState.PLAYING, song)
        assertFalse(PowerampBridge.wantsPresence(Config(enabled = false), playing, 0))
    }

    @Test
    fun nothingToShowWhenStopped() {
        assertFalse(PowerampBridge.wantsPresence(Config(), PlaybackSnapshot(PlayState.STOPPED, song), 0))
    }

    @Test
    fun nothingToShowWithoutTrack() {
        // STATUS_CHANGED seen, but no track known (yet)
        assertFalse(PowerampBridge.wantsPresence(Config(), PlaybackSnapshot(PlayState.PLAYING, TrackInfo()), 0))
    }

    @After
    fun forgetRejection() = PresenceService.rejectedAppId.clear()

    @Test
    fun noServiceWithoutApplicationId() {
        // Every song would start the service for a minute just to show "no application ID"
        assertFalse(PowerampBridge.canConnect(Config(), defaultClientId = ""))
        assertTrue(PowerampBridge.canConnect(Config(), defaultClientId = OWN_ID))
        assertTrue(PowerampBridge.canConnect(Config(useCustomApp = true, customClientId = OWN_ID), defaultClientId = ""))
    }

    @Test
    fun noServiceForAnApplicationIdDiscordRejected() {
        val config = Config(useCustomApp = true, customClientId = OWN_ID)
        PresenceService.rejectedAppId.onStatus(
            EngineStatus(kind = EngineStatus.Kind.NOT_CONNECTED, error = TransportError.INVALID_CLIENT_ID, clientId = OWN_ID),
        )
        assertFalse(PowerampBridge.canConnect(config, defaultClientId = ""))
        // Another ID is tried
        assertTrue(PowerampBridge.canConnect(config.copy(customClientId = "98765432109876543"), defaultClientId = ""))

        // A new application ID in the settings gives the old one another chance too
        PowerampBridge.onSettingsChanged(
            ApplicationProvider.getApplicationContext(),
            config.copy(enabled = false),
            config.copy(enabled = false, customClientId = "98765432109876543"),
        )
        assertTrue(PowerampBridge.canConnect(config, defaultClientId = ""))
    }

    @Test
    @Suppress("DEPRECATION")   // sticky broadcasts: only Poweramp still sends them, the test stands in for it
    fun aSyncOnABackgroundThreadHandsTheEngineWhatPowerampPlays() {
        // the screen syncs on a background thread (MainViewModel.syncPoweramp), not on the main thread
        val app = ApplicationProvider.getApplicationContext<Application>()
        val ts = AndroidClock.wallMs()
        app.sendStickyBroadcast(
            Intent(Poweramp.ACTION_TRACK_CHANGED).putExtras(
                Bundle().apply {
                    putBundle("track", Bundle().apply {
                        putLong("id", 7)
                        putLong("realId", 77)
                        putString("title", "Background Song")
                        putString("artist", "Artist")
                        putString("path", "/m/background.mp3")
                        putInt("durMs", 200_000)
                    })
                    putInt("api", 700)
                    putInt("pos", 12)
                    putLong("ts", ts)
                },
            ),
        )

        thread(name = "sync") { PowerampBridge.sync(app) }.join()

        val submitted = Graph.engine.snapshot.value
        assertEquals("Background Song", submitted.track.title)
        // what the engine got is what the bridge knows: the newest snapshot, submitted last (the engine adds the start of
        // the text rotation)
        assertEquals(PowerampBridge.snapshot, submitted.copy(trackSinceMs = 0))
    }

    private companion object {
        const val OWN_ID = "12345678901234567"
    }
}
