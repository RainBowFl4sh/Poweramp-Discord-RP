package io.github.rainbowfl4sh.powerampdiscordrp.account

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.rainbowfl4sh.powerampdiscordrp.Graph
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.DiscordOAuth
import io.github.rainbowfl4sh.powerampdiscordrp.ui.MainActivity

/**
 * Receives Discord's answer to "Link Discord account" (powerampdiscordrp://auth/callback?code=..&state=..), hands it to
 * the account linker and brings the app's screen back. Shows nothing itself.
 *
 * Exported, because the Discord app or the browser opens it - so it trusts nothing it gets: only a link with exactly the
 * redirect URI's scheme, host and path is passed on, and the linker ignores every answer that does not carry the state
 * of the one login this app started (code and state never reach the log). Anything else just closes it again.
 */
class AuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data?.toString()
        if (uri != null && DiscordOAuth.parseRedirect(uri) != null) {
            Graph.onAccountRedirect(uri)
            // back to the app's screen (the one that is open already, if any) instead of the browser tab
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        finish()
    }
}
