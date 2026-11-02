package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.DiscordOAuth
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkError
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkState

/** Linking the Discord account -> the texts of the status page's account card. */
object AccountTexts {
    /** Why the last attempt failed, in plain words (with what to do about it). */
    fun failure(error: LinkError): StatusLine = when (error) {
        LinkError.CANCELLED -> StatusLine(UiText(R.string.acc_err_cancelled), Severity.INFO)
        LinkError.REDIRECT_NOT_ADDED -> StatusLine(UiText(R.string.acc_err_redirect, DiscordOAuth.REDIRECT_URI), Severity.ERROR)
        LinkError.NOT_PUBLIC_CLIENT -> StatusLine(UiText(R.string.acc_err_public_client), Severity.ERROR)
        LinkError.EXPIRED -> StatusLine(UiText(R.string.acc_err_expired), Severity.WARNING)
        LinkError.NETWORK -> StatusLine(UiText(R.string.acc_err_network), Severity.WARNING)
        LinkError.DISCORD_ERROR -> StatusLine(UiText(R.string.acc_err_discord), Severity.WARNING)
        LinkError.BAD_ANSWER -> StatusLine(UiText(R.string.acc_err_bad_answer), Severity.WARNING)
    }

    /**
     * Discord's own words (English) or the error, where they add something to [failure]'s text; "" for the failures
     * whose text says it all (and never for a cancel).
     */
    fun detail(state: LinkState.Failed): String = when (state.error) {
        LinkError.DISCORD_ERROR, LinkError.BAD_ANSWER, LinkError.NETWORK -> state.detail.trim()
        else -> ""
    }

    /** The hint about Discord's "Invalid OAuth2 redirect_uri" page while waiting for its answer. */
    val redirectHint: UiText get() = UiText(R.string.acc_redirect_hint, DiscordOAuth.REDIRECT_URI)
}
