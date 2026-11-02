package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.DiscordOAuth
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkError
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkState
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountTextsTest {
    @Test
    fun everyFailureHasItsOwnText() {
        val texts = LinkError.entries.map { AccountTexts.failure(it).text.id }
        assertEquals(LinkError.entries.size, texts.toSet().size)
        assertEquals(
            StatusLine(UiText(R.string.acc_err_redirect, DiscordOAuth.REDIRECT_URI), Severity.ERROR),
            AccountTexts.failure(LinkError.REDIRECT_NOT_ADDED),
        )
        assertEquals(StatusLine(UiText(R.string.acc_err_public_client), Severity.ERROR), AccountTexts.failure(LinkError.NOT_PUBLIC_CLIENT))
        // the user's own "no" is no problem
        assertEquals(Severity.INFO, AccountTexts.failure(LinkError.CANCELLED).severity)
    }

    @Test
    fun discordsOwnWordsOnlyWhereTheyHelp() {
        val detail = " HTTP 429: You are being rate limited. "
        assertEquals("HTTP 429: You are being rate limited.", AccountTexts.detail(LinkState.Failed(LinkError.DISCORD_ERROR, detail)))
        assertEquals("HTTP 429: You are being rate limited.", AccountTexts.detail(LinkState.Failed(LinkError.NETWORK, detail)))
        assertEquals("HTTP 429: You are being rate limited.", AccountTexts.detail(LinkState.Failed(LinkError.BAD_ANSWER, detail)))
        listOf(LinkError.CANCELLED, LinkError.REDIRECT_NOT_ADDED, LinkError.NOT_PUBLIC_CLIENT, LinkError.EXPIRED).forEach {
            assertEquals("$it", "", AccountTexts.detail(LinkState.Failed(it, detail)))
        }
    }

    @Test
    fun theRedirectHintNamesTheRedirectUri() {
        assertEquals(UiText(R.string.acc_redirect_hint, "powerampdiscordrp://auth/callback"), AccountTexts.redirectHint)
    }
}
