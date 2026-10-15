package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.Hidden
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.DiscordUser
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.TransportError
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusTextsTest {
    private val playing = PlaybackSnapshot(PlayState.PLAYING, TrackInfo(title = "Song", path = "/m/a.flac"))

    @Test
    fun everyHiddenReasonHasItsOwnText() {
        val ids = Hidden.entries.map(StatusTexts::hidden)
        assertEquals(Hidden.entries.size, ids.toSet().size)
        assertEquals(R.string.hidden_no, StatusTexts.hidden(Hidden.NO))
        assertEquals(R.string.hidden_disabled, StatusTexts.hidden(Hidden.DISABLED))
        assertEquals(R.string.hidden_stopped, StatusTexts.hidden(Hidden.STOPPED))
        assertEquals(R.string.hidden_stream, StatusTexts.hidden(Hidden.STREAM))
        assertEquals(R.string.hidden_path, StatusTexts.hidden(Hidden.PATH_FILTER))
        assertEquals(R.string.hidden_paused, StatusTexts.hidden(Hidden.PAUSED))
        assertEquals(R.string.hidden_timeout, StatusTexts.hidden(Hidden.PAUSED_TIMEOUT))
    }

    @Test
    fun everyTransportErrorHasItsOwnText() {
        val ids = TransportError.entries.map(StatusTexts::transportError)
        assertEquals(TransportError.entries.size, ids.toSet().size)
        assertEquals(R.string.err_invalid_client_id, StatusTexts.transportError(TransportError.INVALID_CLIENT_ID))
        assertEquals(R.string.err_bind_failed, StatusTexts.transportError(TransportError.BIND_FAILED))
        assertEquals(R.string.err_discord_missing, StatusTexts.transportError(TransportError.DISCORD_NOT_INSTALLED))
    }

    @Test
    fun connectedShowsTheUserName() {
        val st = EngineStatus(kind = EngineStatus.Kind.CONNECTED, user = DiscordUser(id = "1", username = "flash", globalName = "Fl4sh"))
        assertEquals(StatusLine(UiText(R.string.conn_connected_as, "Fl4sh"), Severity.OK), StatusTexts.connection(st))
        val noGlobal = st.copy(user = DiscordUser(username = "flash"))
        assertEquals(UiText(R.string.conn_connected_as, "flash"), StatusTexts.connection(noGlobal).text)
        val anonymous = st.copy(user = DiscordUser())
        assertEquals(UiText(R.string.conn_connected), StatusTexts.connection(anonymous).text)
        assertEquals(UiText(R.string.conn_connected), StatusTexts.connection(st.copy(user = null)).text)
    }

    @Test
    fun connectionKinds() {
        assertEquals(Severity.INFO, StatusTexts.connection(EngineStatus(kind = EngineStatus.Kind.IDLE)).severity)
        assertEquals(UiText(R.string.conn_disabled), StatusTexts.connection(EngineStatus(kind = EngineStatus.Kind.DISABLED)).text)
        assertEquals(
            StatusLine(UiText(R.string.conn_no_app_id), Severity.ERROR),
            StatusTexts.connection(EngineStatus(kind = EngineStatus.Kind.NO_APP_ID)),
        )
        assertEquals(UiText(R.string.conn_connecting), StatusTexts.connection(EngineStatus(kind = EngineStatus.Kind.CONNECTING)).text)
    }

    @Test
    fun notConnectedNamesTheErrorAndItsSeverity() {
        val closed = EngineStatus(kind = EngineStatus.Kind.NOT_CONNECTED, error = TransportError.CLOSED, errorDetail = " bye ")
        assertEquals(StatusLine(UiText(R.string.err_closed), Severity.WARNING), StatusTexts.connection(closed))
        assertEquals("bye", StatusTexts.errorDetail(closed))
        val rejected = closed.copy(error = TransportError.INVALID_CLIENT_ID)
        assertEquals(StatusLine(UiText(R.string.err_invalid_client_id), Severity.ERROR), StatusTexts.connection(rejected))
        // a detail left over from an earlier failure is not shown once connected
        assertEquals("", StatusTexts.errorDetail(closed.copy(kind = EngineStatus.Kind.CONNECTED)))
    }

    @Test
    fun coverSources() {
        assertEquals(UiText(R.string.cov_src_cache), StatusTexts.coverSource(""))
        assertEquals(UiText(R.string.cov_src_online, "Deezer"), StatusTexts.coverSource("Deezer"))
        assertEquals(
            UiText(R.string.cov_src_uploaded, UiText(R.string.cov_src_poweramp), "x0.at"),
            StatusTexts.coverSource("poweramp|x0.at"),
        )
    }

    @Test
    fun coverStates() {
        val st = EngineStatus(
            kind = EngineStatus.Kind.CONNECTED, visible = true, coverUrl = "https://x0.at/a.jpg", coverSource = "poweramp|x0.at",
        )
        val c = Config()
        assertEquals(UiText(R.string.cov_off), StatusTexts.coverState(c.copy(coverEnabled = false), playing, st))
        assertEquals(UiText(R.string.cov_nothing), StatusTexts.coverState(c, PlaybackSnapshot(), st))
        assertEquals(UiText(R.string.cov_searching), StatusTexts.coverState(c, playing, st.copy(coverSearching = true, coverUrl = "")))
        // shown with the logo: nothing found, or Discord could not load the cover (the engine then reports no URL)
        assertEquals(UiText(R.string.cov_none), StatusTexts.coverState(c, playing, st.copy(coverUrl = "", coverSource = "")))
        assertEquals(
            UiText(R.string.cov_source, StatusTexts.coverSource("poweramp|x0.at")),
            StatusTexts.coverState(c, playing, st),
        )
        // a cached cover is known before anything is shown
        assertEquals(
            UiText(R.string.cov_source, StatusTexts.coverSource("poweramp|x0.at")),
            StatusTexts.coverState(c, playing, st.copy(kind = EngineStatus.Kind.IDLE, visible = false)),
        )
    }

    @Test
    fun noCoverYetWhileDiscordShowsNothing() {
        val c = Config()
        val notCached = EngineStatus(kind = EngineStatus.Kind.CONNECTED, visible = true)
        // looked up / uploaded only once the track is shown: not "no cover found" while stopped, connecting, ...
        for (st in listOf(
            EngineStatus(),   // service stopped (the engine rests)
            EngineStatus(kind = EngineStatus.Kind.CONNECTING),
            EngineStatus(kind = EngineStatus.Kind.NOT_CONNECTED, error = TransportError.CONNECT_FAILED),
            notCached.copy(visible = false, activityError = "Invalid Form Body"),
            notCached.copy(testing = true),   // the test presence is shown, not the track
        )) {
            assertEquals("$st", UiText(R.string.cov_not_yet), StatusTexts.coverState(c, playing, st))
        }
        assertEquals(UiText(R.string.cov_none), StatusTexts.coverState(c, playing, notCached))
    }

    // ---- "Now playing": only what Discord really shows is "shown"

    private val shown = EngineStatus(kind = EngineStatus.Kind.CONNECTED, visible = true, hidden = Hidden.NO)

    @Test
    fun theTrackIsShownOnlyWhileDiscordShowsIt() {
        assertEquals(StatusLine(UiText(R.string.hidden_no), Severity.OK), StatusTexts.nowPlaying(Hidden.NO, shown, false))
        // not (yet) shown: never the green "Discord shows this track"
        val notShown = listOf(
            EngineStatus(),   // service not running
            EngineStatus(kind = EngineStatus.Kind.DISABLED),
            EngineStatus(kind = EngineStatus.Kind.NO_APP_ID),
            EngineStatus(kind = EngineStatus.Kind.CONNECTING),
            EngineStatus(kind = EngineStatus.Kind.NOT_CONNECTED, error = TransportError.DISCORD_NOT_INSTALLED),
            EngineStatus(kind = EngineStatus.Kind.NOT_CONNECTED, error = TransportError.INVALID_CLIENT_ID),
        )
        notShown.forEach { st ->
            assertEquals("$st", StatusLine(UiText(R.string.now_not_connected), Severity.INFO), StatusTexts.nowPlaying(Hidden.NO, st, false))
        }
        // connected, the update is on its way (Discord's rate limit)
        assertEquals(
            StatusLine(UiText(R.string.now_sending), Severity.INFO),
            StatusTexts.nowPlaying(Hidden.NO, shown.copy(visible = false), false),
        )
    }

    @Test
    fun stoppedByTheUser() {
        // the engine still computes the texts for every Poweramp broadcast: hidden stays NO
        val stopped = EngineStatus(kind = EngineStatus.Kind.IDLE, hidden = Hidden.NO)
        assertEquals(StatusLine(UiText(R.string.now_user_stopped), Severity.INFO), StatusTexts.nowPlaying(Hidden.NO, stopped, true))
        // a reason of its own (paused, filtered) is still the better explanation
        assertEquals(StatusLine(UiText(R.string.hidden_paused), Severity.INFO), StatusTexts.nowPlaying(Hidden.PAUSED, stopped, true))
    }

    @Test
    fun hiddenReasonsComeFirst() {
        Hidden.entries.filter { it != Hidden.NO }.forEach { h ->
            assertEquals(StatusLine(UiText(StatusTexts.hidden(h)), Severity.INFO), StatusTexts.nowPlaying(h, shown, false))
        }
    }

    @Test
    fun discordRejectedTheUpdate() {
        val rejected = shown.copy(visible = false, activityError = "Invalid Form Body")
        assertEquals(StatusLine(UiText(R.string.now_rejected), Severity.WARNING), StatusTexts.nowPlaying(Hidden.NO, rejected, false))
        assertEquals("Invalid Form Body", StatusTexts.activityError(rejected))
        // reported only while connected (a new connection gets everything again)
        assertEquals("", StatusTexts.activityError(rejected.copy(kind = EngineStatus.Kind.NOT_CONNECTED)))
        assertEquals("", StatusTexts.activityError(shown))
    }

    @Test
    fun theTestPresenceIsShownOnlyOnceDiscordShowsIt() {
        assertEquals(null, StatusTexts.testNote(shown))
        val testing = shown.copy(testing = true)
        assertEquals(UiText(R.string.conn_testing), StatusTexts.testNote(testing))
        assertEquals(StatusLine(UiText(R.string.now_after_test), Severity.INFO), StatusTexts.nowPlaying(Hidden.NO, testing, false))
        // nothing plays: Discord still shows the test, not "nothing"
        assertEquals(StatusLine(UiText(R.string.now_test_shown), Severity.INFO), StatusTexts.nowPlaying(Hidden.STOPPED, testing, false))
        assertEquals(
            StatusLine(UiText(R.string.hidden_stopped), Severity.INFO),
            StatusTexts.nowPlaying(Hidden.STOPPED, testing.copy(kind = EngineStatus.Kind.CONNECTING, visible = false), false),
        )
        // the engine counts the test from the request on
        assertEquals(UiText(R.string.conn_test_waiting), StatusTexts.testNote(testing.copy(kind = EngineStatus.Kind.CONNECTING, visible = false)))
        assertEquals(UiText(R.string.conn_test_waiting), StatusTexts.testNote(testing.copy(visible = false)))
        // cannot be sent at all: the headline says why
        assertEquals(null, StatusTexts.testNote(EngineStatus(kind = EngineStatus.Kind.NO_APP_ID, testing = true)))
        assertEquals(
            null,
            StatusTexts.testNote(EngineStatus(kind = EngineStatus.Kind.NOT_CONNECTED, error = TransportError.DISCORD_NOT_INSTALLED, testing = true)),
        )
        assertEquals(null, StatusTexts.testNote(testing.copy(visible = false, activityError = "Invalid Form Body")))
    }

    // ---- connection headline while the engine rests

    private val builtIn = "1555109720807702559"

    private fun headline(st: EngineStatus, c: Config, default: String = builtIn, rejected: Boolean = false): StatusLine =
        StatusTexts.connection(StatusTexts.connectionStatus(st, c, default, rejected))

    @Test
    fun aRestingEngineShowsWhatTheSettingsSay() {
        val fresh = EngineStatus()   // a new engine: IDLE until a session runs or the settings change
        assertEquals(StatusLine(UiText(R.string.conn_disabled), Severity.INFO), headline(fresh, Config(enabled = false)))
        assertEquals(StatusLine(UiText(R.string.conn_no_app_id), Severity.ERROR), headline(fresh, Config(), default = ""))
        assertEquals(
            StatusLine(UiText(R.string.conn_no_app_id), Severity.ERROR),
            headline(fresh, Config(useCustomApp = true, customClientId = "123"), default = ""),
        )
        // a built-in ID the engine cannot use at all (a broken build)
        assertEquals(StatusLine(UiText(R.string.conn_no_app_id), Severity.ERROR), headline(fresh, Config(), default = "abc"))
        assertEquals(StatusLine(UiText(R.string.conn_idle), Severity.INFO), headline(fresh, Config()))
        assertEquals(
            StatusLine(UiText(R.string.conn_idle), Severity.INFO),
            headline(fresh, Config(useCustomApp = true, customClientId = "123456789012345678"), default = ""),
        )
    }

    @Test
    fun aRestingEngineShowsARejectionOfAnEarlierProcess() {
        // the engine forgot it with the process; Poweramp does not start the service for this ID
        val rejected = StatusTexts.connectionStatus(EngineStatus(), Config(), builtIn, appIdRejected = true)
        assertEquals(EngineStatus.Kind.NOT_CONNECTED, rejected.kind)
        assertEquals(TransportError.INVALID_CLIENT_ID, rejected.error)
        assertEquals(StatusLine(UiText(R.string.err_invalid_client_id), Severity.ERROR), StatusTexts.connection(rejected))
        assertEquals("", StatusTexts.errorDetail(rejected))
        // switched off / no ID: that is what keeps it from connecting first
        assertEquals(UiText(R.string.conn_disabled), headline(EngineStatus(), Config(enabled = false), rejected = true).text)
        assertEquals(UiText(R.string.conn_no_app_id), headline(EngineStatus(), Config(), default = "", rejected = true).text)
    }

    @Test
    fun aWorkingEngineIsNotOverruled() {
        // e.g. a test presence while switched off: the engine connects anyway
        val connecting = EngineStatus(kind = EngineStatus.Kind.CONNECTING)
        assertEquals(connecting, StatusTexts.connectionStatus(connecting, Config(enabled = false), builtIn, appIdRejected = true))
        val connected = EngineStatus(kind = EngineStatus.Kind.CONNECTED)
        assertEquals(connected, StatusTexts.connectionStatus(connected, Config(), builtIn, appIdRejected = true))
        val rejected = EngineStatus(kind = EngineStatus.Kind.NOT_CONNECTED, error = TransportError.INVALID_CLIENT_ID, errorDetail = "Unknown")
        assertEquals(rejected, StatusTexts.connectionStatus(rejected, Config(), builtIn, appIdRejected = false))
        assertEquals("Unknown", StatusTexts.errorDetail(rejected))
    }
}
