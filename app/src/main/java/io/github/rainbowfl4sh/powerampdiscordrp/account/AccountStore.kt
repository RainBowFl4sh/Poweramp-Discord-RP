package io.github.rainbowfl4sh.powerampdiscordrp.account

import android.content.Context
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.AccountStorage
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.DiscordAvatars
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkedAccount
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.PendingLogin

/**
 * The linked Discord account and a pending login in their own SharedPreferences file: not part of the presence settings
 * (export / import / reset leave them alone), and like everything of this app not part of Android's backups. Holds the
 * public profile only - never a token. A pending login (state, code verifier) lives at most 10 minutes.
 */
class AccountStore(context: Context) : AccountStorage {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadAccount(): LinkedAccount? {
        val id = prefs.getString(KEY_ID, null)?.takeIf(DiscordAvatars::isSnowflake) ?: return null
        val username = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() } ?: return null
        return LinkedAccount(
            id = id,
            username = username,
            globalName = prefs.getString(KEY_GLOBAL_NAME, null).orEmpty(),
            avatar = prefs.getString(KEY_AVATAR, null).orEmpty(),
            linkedAtMs = prefs.getLong(KEY_LINKED_AT, 0),
        )
    }

    override fun saveAccount(account: LinkedAccount?) {
        val edit = prefs.edit()
        if (account == null) {
            edit.remove(KEY_ID).remove(KEY_USERNAME).remove(KEY_GLOBAL_NAME).remove(KEY_AVATAR).remove(KEY_LINKED_AT)
        } else {
            edit.putString(KEY_ID, account.id)
                .putString(KEY_USERNAME, account.username)
                .putString(KEY_GLOBAL_NAME, account.globalName)
                .putString(KEY_AVATAR, account.avatar)
                .putLong(KEY_LINKED_AT, account.linkedAtMs)
        }
        edit.apply()
    }

    override fun loadPending(): PendingLogin? {
        val state = prefs.getString(KEY_PENDING_STATE, null)?.takeIf { it.isNotEmpty() } ?: return null
        val verifier = prefs.getString(KEY_PENDING_VERIFIER, null)?.takeIf { it.isNotEmpty() } ?: return null
        val clientId = prefs.getString(KEY_PENDING_CLIENT_ID, null)?.takeIf { it.isNotEmpty() } ?: return null
        return PendingLogin(state, verifier, clientId, prefs.getLong(KEY_PENDING_STARTED_AT, 0))
    }

    override fun savePending(pending: PendingLogin?) {
        val edit = prefs.edit()
        if (pending == null) {
            // gone from memory at once; the file follows in the background
            edit.remove(KEY_PENDING_STATE).remove(KEY_PENDING_VERIFIER).remove(KEY_PENDING_CLIENT_ID)
                .remove(KEY_PENDING_STARTED_AT).apply()
        } else {
            // on disk before Discord's page opens: the process may be killed while the user is there
            edit.putString(KEY_PENDING_STATE, pending.state)
                .putString(KEY_PENDING_VERIFIER, pending.verifier)
                .putString(KEY_PENDING_CLIENT_ID, pending.clientId)
                .putLong(KEY_PENDING_STARTED_AT, pending.startedAtMs)
                .commit()
        }
    }

    companion object {
        const val PREFS_NAME = "account"
        private const val KEY_ID = "id"
        private const val KEY_USERNAME = "username"
        private const val KEY_GLOBAL_NAME = "globalName"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_LINKED_AT = "linkedAt"
        private const val KEY_PENDING_STATE = "pendingState"
        private const val KEY_PENDING_VERIFIER = "pendingVerifier"
        private const val KEY_PENDING_CLIENT_ID = "pendingClientId"
        private const val KEY_PENDING_STARTED_AT = "pendingStartedAt"
    }
}
