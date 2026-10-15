package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import androidx.annotation.StringRes
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.Hidden
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.DiscordUser
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.TransportError

/** How a status line is drawn (icon / color). */
enum class Severity { OK, INFO, WARNING, ERROR }

/** A status text with its severity. */
data class StatusLine(val text: UiText, val severity: Severity)

/** Engine / presence state -> the texts of the status page, cover tab and live preview (port of the AIMP page's texts). */
object StatusTexts {
    /** Why Discord shows nothing (or that it shows the track). */
    @StringRes
    fun hidden(h: Hidden): Int = when (h) {
        Hidden.NO -> R.string.hidden_no
        Hidden.DISABLED -> R.string.hidden_disabled
        Hidden.STOPPED -> R.string.hidden_stopped
        Hidden.STREAM -> R.string.hidden_stream
        Hidden.PATH_FILTER -> R.string.hidden_path
        Hidden.PAUSED -> R.string.hidden_paused
        Hidden.PAUSED_TIMEOUT -> R.string.hidden_timeout
    }

    /** Why the connection could not be made / was lost. */
    @StringRes
    fun transportError(e: TransportError): Int = when (e) {
        TransportError.NONE -> R.string.err_none
        TransportError.DISCORD_NOT_INSTALLED -> R.string.err_discord_missing
        TransportError.BIND_FAILED -> R.string.err_bind_failed
        TransportError.CONNECT_FAILED -> R.string.err_connect_failed
        TransportError.CLOSED -> R.string.err_closed
        TransportError.INVALID_CLIENT_ID -> R.string.err_invalid_client_id
    }

    /**
     * The engine's status as the connection card shows it. The engine starts out IDLE and works out "switched off" /
     * "no application ID" only when a session runs or the settings change, and it forgets a rejected application ID
     * with the process: in a fresh process (presence switched off, or nothing that may start the service) the settings
     * and the runtime's remembered rejection say what an IDLE engine cannot.
     * @param appIdRejected Discord rejected the application ID in use before, and no session tried it again since
     *                      (the presence service is not started for it)
     */
    fun connectionStatus(st: EngineStatus, c: Config, defaultClientId: String, appIdRejected: Boolean): EngineStatus = when {
        st.kind != EngineStatus.Kind.IDLE -> st
        !c.enabled -> st.copy(kind = EngineStatus.Kind.DISABLED)
        !SetupChecks.hasUsableAppId(c, defaultClientId) -> st.copy(kind = EngineStatus.Kind.NO_APP_ID)
        appIdRejected -> st.copy(kind = EngineStatus.Kind.NOT_CONNECTED, error = TransportError.INVALID_CLIENT_ID)
        else -> st
    }

    /** Headline of the connection card for the status' own [EngineStatus.kind]. */
    fun connection(st: EngineStatus): StatusLine = when (st.kind) {
        EngineStatus.Kind.IDLE -> StatusLine(UiText(R.string.conn_idle), Severity.INFO)
        EngineStatus.Kind.DISABLED -> StatusLine(UiText(R.string.conn_disabled), Severity.INFO)
        EngineStatus.Kind.NO_APP_ID -> StatusLine(UiText(R.string.conn_no_app_id), Severity.ERROR)
        EngineStatus.Kind.CONNECTING -> StatusLine(UiText(R.string.conn_connecting), Severity.INFO)
        EngineStatus.Kind.CONNECTED -> {
            val name = userName(st.user)
            StatusLine(
                if (name.isEmpty()) UiText(R.string.conn_connected) else UiText(R.string.conn_connected_as, name),
                Severity.OK,
            )
        }
        EngineStatus.Kind.NOT_CONNECTED -> StatusLine(
            UiText(transportError(st.error)),
            // a closed connection is retried by itself; the others need the user
            if (st.error == TransportError.CLOSED || st.error == TransportError.NONE) Severity.WARNING else Severity.ERROR,
        )
    }

    /** Discord's own message (English) or the exception text, only worth showing while not connected. */
    fun errorDetail(st: EngineStatus): String =
        if (st.kind == EngineStatus.Kind.NOT_CONNECTED) st.errorDetail.trim() else ""

    /** Connected, but Discord did not take the last presence update: its message (English), "" = it did. */
    fun activityError(st: EngineStatus): String =
        if (st.kind == EngineStatus.Kind.CONNECTED) st.activityError.trim() else ""

    /**
     * Note about a requested test presence, null = none. The engine counts the test from the request on, also while it
     * still connects (or cannot): "shown" only once Discord shows it.
     */
    fun testNote(st: EngineStatus): UiText? = when {
        !st.testing -> null
        st.visible -> UiText(R.string.conn_testing)
        activityError(st).isNotEmpty() -> null   // Discord did not take it: the card says why
        st.kind == EngineStatus.Kind.CONNECTING || st.kind == EngineStatus.Kind.CONNECTED -> UiText(R.string.conn_test_waiting)
        else -> null   // no application ID / not connected: the headline says why it cannot be sent
    }

    /**
     * Whether Discord shows the track (status page). [hidden] = what the presence of this track would be: a track that
     * is to be shown is only "shown" while Discord really shows it ([EngineStatus.visible]).
     * @param stoppedByUser the user stopped the presence until Poweramp plays again
     */
    fun nowPlaying(hidden: Hidden, st: EngineStatus, stoppedByUser: Boolean): StatusLine = when {
        // the test presence is shown whatever plays
        st.visible && st.testing ->
            StatusLine(UiText(if (hidden == Hidden.NO) R.string.now_after_test else R.string.now_test_shown), Severity.INFO)
        hidden != Hidden.NO -> StatusLine(UiText(hidden(hidden)), Severity.INFO)
        stoppedByUser -> StatusLine(UiText(R.string.now_user_stopped), Severity.INFO)
        activityError(st).isNotEmpty() -> StatusLine(UiText(R.string.now_rejected), Severity.WARNING)
        st.visible -> StatusLine(UiText(R.string.hidden_no), Severity.OK)
        st.kind == EngineStatus.Kind.CONNECTED -> StatusLine(UiText(R.string.now_sending), Severity.INFO)
        else -> StatusLine(UiText(R.string.now_not_connected), Severity.INFO)
    }

    /** Display name of the Discord user ("" if Discord reported none). */
    fun userName(user: DiscordUser?): String {
        if (user == null) return ""
        return user.globalName.trim().ifEmpty { user.username.trim() }
    }

    /** "poweramp|x0.at" -> "Poweramp's cover, uploaded to x0.at"; "Deezer" -> "found on Deezer"; "" -> "cover cache". */
    fun coverSource(source: String): UiText {
        val s = source.trim()
        if (s.isEmpty()) return UiText(R.string.cov_src_cache)
        val bar = s.indexOf('|')
        if (bar < 0) return UiText(R.string.cov_src_online, s)
        val host = s.substring(bar + 1).trim()
        // the only local origin on Android is the cover Poweramp shows (embedded, folder image or downloaded by it)
        return UiText(R.string.cov_src_uploaded, UiText(R.string.cov_src_poweramp), host)
    }

    /**
     * What the cover tab says about the current cover. [EngineStatus.coverUrl] is the cover Discord gets: from the cache
     * as soon as the track plays, found / uploaded only once Discord shows the track, "" while the logo is used (also
     * when Discord could not load the cover).
     */
    fun coverState(c: Config, snapshot: PlaybackSnapshot, st: EngineStatus): UiText = when {
        !c.coverEnabled -> UiText(R.string.cov_off)
        snapshot.state == PlayState.STOPPED || snapshot.track.isEmpty -> UiText(R.string.cov_nothing)
        st.coverSearching -> UiText(R.string.cov_searching)
        st.coverUrl.isNotBlank() -> UiText(R.string.cov_source, coverSource(st.coverSource))
        !st.visible || st.testing -> UiText(R.string.cov_not_yet)   // not looked for yet
        else -> UiText(R.string.cov_none)
    }
}
