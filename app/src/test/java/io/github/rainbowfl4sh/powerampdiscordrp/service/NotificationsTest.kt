package io.github.rainbowfl4sh.powerampdiscordrp.service

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus.Kind
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.Hidden
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.TransportError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val song = TrackInfo(realId = 1, title = "Bohemian Rhapsody", artist = "Queen", path = "/m/q.flac")
    private val playing = PlaybackSnapshot(PlayState.PLAYING, song, 1_000, 5)
    private val paused = playing.copy(state = PlayState.PAUSED)

    private fun content(status: EngineStatus, snapshot: PlaybackSnapshot = playing) =
        Notifications.contentOf(app, status, snapshot)

    @Test
    fun showingTitleAndArtist() {
        val c = content(EngineStatus(kind = Kind.CONNECTED, hidden = Hidden.NO, visible = true))
        assertEquals("Bohemian Rhapsody – Queen", c.title)
        assertEquals("Showing on Discord", c.text)
    }

    @Test
    fun pausedStates() {
        assertEquals(
            "Paused – shown on Discord",
            content(EngineStatus(kind = Kind.CONNECTED, hidden = Hidden.NO, visible = true), paused).text,
        )
        assertEquals("Paused – hidden on Discord", content(EngineStatus(kind = Kind.CONNECTED, hidden = Hidden.PAUSED), paused).text)
        assertEquals(
            "Paused – hidden on Discord",
            content(EngineStatus(kind = Kind.CONNECTED, hidden = Hidden.PAUSED_TIMEOUT), paused).text,
        )
    }

    @Test
    fun connectionProblemsAreShownInsteadOfTheTrackState() {
        assertEquals("Connecting to Discord…", content(EngineStatus(kind = Kind.CONNECTING, hidden = Hidden.NO)).text)
        assertEquals(
            "The Discord app is not installed",
            content(EngineStatus(kind = Kind.NOT_CONNECTED, error = TransportError.DISCORD_NOT_INSTALLED)).text,
        )
        assertEquals(
            "Discord rejected the application ID",
            content(EngineStatus(kind = Kind.NOT_CONNECTED, error = TransportError.INVALID_CLIENT_ID)).text,
        )
        assertEquals(
            "Not connected to Discord – retrying",
            content(EngineStatus(kind = Kind.NOT_CONNECTED, error = TransportError.CLOSED)).text,
        )
        assertEquals("No Discord application ID configured", content(EngineStatus(kind = Kind.NO_APP_ID)).text)
        assertEquals("Discord Rich Presence is switched off", content(EngineStatus(kind = Kind.DISABLED)).text)
    }

    @Test
    fun idleEngine() {
        // Something to show, but not connected yet
        assertEquals("Connecting to Discord…", content(EngineStatus(kind = Kind.IDLE, hidden = Hidden.NO)).text)
        // Nothing known about Poweramp yet
        val waiting = content(EngineStatus(kind = Kind.IDLE, hidden = Hidden.STOPPED), PlaybackSnapshot())
        assertEquals("Waiting for Poweramp…", waiting.text)
        assertEquals("Poweramp Discord RP", waiting.title)
        // Hidden by a filter
        assertEquals("Nothing shown on Discord", content(EngineStatus(kind = Kind.CONNECTED, hidden = Hidden.STREAM)).text)
        assertEquals(
            "Test presence shown on Discord",
            content(EngineStatus(kind = Kind.CONNECTED, testing = true, visible = true)).text,
        )
    }

    @Test
    fun shownOnlyWhileDiscordReallyShowsIt() {
        // connected, but the first update still waits for the send limit
        assertEquals("Sending to Discord…", content(EngineStatus(kind = Kind.CONNECTED, hidden = Hidden.NO)).text)
        assertEquals("Sending to Discord…", content(EngineStatus(kind = Kind.CONNECTED, testing = true)).text)
        // Discord rejected the update: not "Showing on Discord", also not for a test presence
        val rejected = EngineStatus(kind = Kind.CONNECTED, hidden = Hidden.NO, activityError = "child \"details_url\" fails")
        assertEquals("Discord did not accept the presence – details in the app", content(rejected).text)
        assertEquals(
            "Discord did not accept the presence – details in the app",
            content(rejected.copy(testing = true)).text,
        )
    }

    @Test
    fun titleFallbacks() {
        val noArtist = playing.copy(track = song.copy(artist = ""))
        assertEquals("Bohemian Rhapsody", content(EngineStatus(kind = Kind.CONNECTED), noArtist).title)
        val fileOnly = playing.copy(track = TrackInfo(path = "/storage/Music/01 Intro.mp3"))
        assertEquals("01 Intro.mp3", content(EngineStatus(kind = Kind.CONNECTED), fileOnly).title)
    }

    @Test
    fun serviceNotification() {
        Notifications.ensureChannels(app)
        val n = Notifications.presence(app, NotificationContent("Song – Artist", "Showing on Discord"))
        assertEquals(Notifications.CHANNEL_PRESENCE, n.channelId)
        assertEquals("Song – Artist", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Showing on Discord", n.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull(n.contentIntent)
        assertEquals(1, n.actions.size)
        assertEquals("Stop", n.actions[0].title.toString())
        val stop = shadowOf(n.actions[0].actionIntent).savedIntent
        assertEquals(PresenceService.ACTION_STOP, stop.action)
        assertEquals(PresenceService::class.java.name, stop.component!!.className)

        val manager = app.getSystemService(NotificationManager::class.java)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(Notifications.CHANNEL_PRESENCE).importance)
    }

    @Test
    fun backgroundBlockedNotificationOpensTheApp() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        Notifications.showBackgroundBlocked(app)
        val manager = shadowOf(app.getSystemService(NotificationManager::class.java))
        val n = manager.getNotification(Notifications.ID_BACKGROUND_BLOCKED)
        assertNotNull(n)
        assertEquals(Notifications.CHANNEL_ALERTS, n.channelId)
        assertEquals("Discord presence could not start", n.extras.getString(Notification.EXTRA_TITLE))
        val open = shadowOf(n.contentIntent).savedIntent
        assertEquals("io.github.rainbowfl4sh.powerampdiscordrp.ui.MainActivity", open.component!!.className)
        assertTrue(n.flags and NotificationCompat.FLAG_AUTO_CANCEL != 0)

        Notifications.cancelBackgroundBlocked(app)
        assertNull(manager.getNotification(Notifications.ID_BACKGROUND_BLOCKED))
    }

    @Test
    fun backgroundBlockedAlertComesOnceADayAtMost() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(NotificationManager::class.java)
        val t0 = 1_700_000_000_000L
        Notifications.showBackgroundBlocked(app, t0)
        val first = shadowOf(manager).getNotification(Notifications.ID_BACKGROUND_BLOCKED)
        assertNotNull(first)

        // Next songs while it is shown: not posted again
        Notifications.showBackgroundBlocked(app, t0 + 60_000)
        assertSame(first, shadowOf(manager).getNotification(Notifications.ID_BACKGROUND_BLOCKED))

        // Dismissed by the user: no new alert for the rest of the day
        manager.cancel(Notifications.ID_BACKGROUND_BLOCKED)
        Notifications.showBackgroundBlocked(app, t0 + 3_600_000)
        assertNull(shadowOf(manager).getNotification(Notifications.ID_BACKGROUND_BLOCKED))

        Notifications.showBackgroundBlocked(app, t0 + Notifications.BLOCKED_ALERT_INTERVAL_MS)
        assertNotNull(shadowOf(manager).getNotification(Notifications.ID_BACKGROUND_BLOCKED))
    }
}
