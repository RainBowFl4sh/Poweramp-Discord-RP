package io.github.rainbowfl4sh.powerampdiscordrp.core.account

import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A login this app started and waits for. Kept (with the code verifier) until Discord comes back, at most
 * [MAX_AGE_MS]: the process may be gone by then, the storage is not.
 */
data class PendingLogin(
    val state: String,
    val verifier: String,
    val clientId: String,
    val startedAtMs: Long,   // unix ms
) {
    /** Too old - or from the future, when the clock was changed meanwhile. */
    fun isExpired(nowMs: Long): Boolean = nowMs < startedAtMs || nowMs - startedAtMs > MAX_AGE_MS

    companion object {
        const val MAX_AGE_MS = 10 * 60 * 1000L
    }
}

/** Where the linked profile and a pending login are kept (the app: its own SharedPreferences file). */
interface AccountStorage {
    fun loadAccount(): LinkedAccount?

    /** null = no account linked. */
    fun saveAccount(account: LinkedAccount?)

    fun loadPending(): PendingLogin?

    /** null = none; written before Discord's page opens (synchronously: the process may be gone right after). */
    fun savePending(pending: PendingLogin?)
}

/** Where linking the Discord account stands. */
sealed interface LinkState {
    data object Idle : LinkState

    /** Discord's authorization page was opened; waiting for Discord to come back. */
    data object Waiting : LinkState

    /** Discord came back with a code: the profile is being read. */
    data object Working : LinkState

    /** The last attempt failed (until the next one). */
    data class Failed(val error: LinkError, val detail: String = "") : LinkState
}

/**
 * Links the Discord account (optional): starts the OAuth2 login, checks what comes back to the redirect URI and keeps
 * the public profile. Security: every redirect must carry the state of the one pending login, which is deleted with its
 * code verifier once used or after [PendingLogin.MAX_AGE_MS]; anything else is ignored (and logged). Codes, tokens,
 * states and verifiers never go to the log.
 *
 * Threading: every method may be called from any thread; [start] and [handleRedirect] block (storage, network), the
 * others only touch the storage. [scope] runs [onRedirect] and the expiry of a pending login.
 */
class AccountLinker(
    private val storage: AccountStorage,
    private val oauth: () -> DiscordOAuth,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val log: Logger = Logger.NONE,
    newRandom: () -> SecureRandom = ::SecureRandom,
) {
    private val random by lazy(newRandom)   // seeding may take a moment: on the first login, not with the screen
    private val lock = Any()
    private val _account = MutableStateFlow(storage.loadAccount())
    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    private val _results = MutableSharedFlow<LinkResult>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var expiry: Job? = null

    /** Counts the logins: the result of one that was cancelled or followed by a newer one changes no state. */
    private var attempt = 0

    /** The linked account, null = none. */
    val account: StateFlow<LinkedAccount?> = _account.asStateFlow()

    val state: StateFlow<LinkState> = _state.asStateFlow()

    /** Every finished login (for a short confirmation on the screen); nothing is replayed. */
    val results: SharedFlow<LinkResult> = _results.asSharedFlow()

    init {
        // a login still waiting from an earlier process goes on waiting (until it expires)
        synchronized(lock) {
            val pending = storage.loadPending()
            when {
                pending == null -> Unit
                pending.isExpired(clock.wallMs()) -> storage.savePending(null)
                else -> {
                    _state.value = LinkState.Waiting
                    scheduleExpiry(pending)
                }
            }
        }
    }

    /**
     * Starts a login for application [clientId]: a new state and code verifier are saved, and Discord's authorization
     * page is returned to open. null if [clientId] is no application ID. Replaces a login still waiting.
     */
    fun start(clientId: String): String? {
        val id = clientId.trim()
        if (!DiscordAvatars.isSnowflake(id)) return null
        val pending = PendingLogin(Pkce.newState(random), Pkce.newVerifier(random), id, clock.wallMs())
        synchronized(lock) {
            storage.savePending(pending)
            attempt++
            _state.value = LinkState.Waiting
            scheduleExpiry(pending)
        }
        log.log("Discord account: opening Discord's authorization page (application ID $id)")
        return DiscordOAuth.authorizeUrl(id, pending.state, Pkce.challenge(pending.verifier))
    }

    /** Discord came back to the redirect URI: [handleRedirect] on [scope]. */
    fun onRedirect(uri: String) {
        scope.launch { handleRedirect(uri) }
    }

    /**
     * Handles what came back to the redirect URI: true if it answered the pending login (which ends with it), false if
     * it was ignored. Blocking: exchanges the code and reads the profile.
     */
    fun handleRedirect(uri: String): Boolean {
        val redirect = DiscordOAuth.parseRedirect(uri)
        if (redirect == null) {
            log.log("Discord account: ignored a link that is no answer of Discord")
            return false
        }
        val pending: PendingLogin
        val current: Int
        synchronized(lock) {
            val p = storage.loadPending()
            when {
                p == null -> {
                    log.log("Discord account: ignored an answer of Discord - no login in progress")
                    return false
                }
                p.isExpired(clock.wallMs()) -> {
                    endPending()
                    if (_state.value == LinkState.Waiting) _state.value = LinkState.Failed(LinkError.EXPIRED)
                    log.log("Discord account: ignored an answer of Discord - the login took longer than 10 minutes")
                    return false
                }
                !sameState(p.state, redirect.state) -> {
                    // not the answer to this login (an older one, or not from Discord at all): the login goes on waiting
                    log.log("Discord account: ignored an answer of Discord - it belongs to another login")
                    return false
                }
            }
            pending = p
            endPending()   // the state and the verifier are used once
            current = ++attempt
            if (redirect is OAuthRedirect.Code) _state.value = LinkState.Working
        }
        val result = when (redirect) {
            is OAuthRedirect.Error -> DiscordOAuth.failureOf(redirect)
            is OAuthRedirect.Code -> try {
                oauth().link(pending.clientId, redirect.code, pending.verifier, clock.wallMs())
            } catch (e: Exception) {   // defensive: link() reports its failures itself
                LinkResult.Failed(LinkError.BAD_ANSWER, e.javaClass.simpleName)
            }
        }
        finish(current, result)
        return true
    }

    /** Stops waiting for Discord (the pending login is deleted: a late answer is ignored). */
    fun cancel() {
        synchronized(lock) {
            endPending()
            attempt++
            if (_state.value == LinkState.Waiting || _state.value == LinkState.Working) _state.value = LinkState.Idle
        }
        log.log("Discord account: linking cancelled")
    }

    /** Forgets the linked account. Nothing to tell Discord: the access was revoked right after the profile was read. */
    fun unlink() {
        synchronized(lock) {
            storage.saveAccount(null)
            _account.value = null
            if (_state.value is LinkState.Failed) _state.value = LinkState.Idle
        }
        log.log("Discord account: unlinked")
    }

    /** A login that waited longer than [PendingLogin.MAX_AGE_MS] ends (also checked when the screen comes back). */
    fun expireStale() {
        synchronized(lock) {
            val pending = storage.loadPending()
            if (pending == null) {
                if (_state.value == LinkState.Waiting) _state.value = LinkState.Idle
                return
            }
            if (!pending.isExpired(clock.wallMs())) return
            endPending()
            if (_state.value == LinkState.Waiting) _state.value = LinkState.Failed(LinkError.EXPIRED)
        }
        log.log("Discord account: no answer of Discord within 10 minutes - login ended")
    }

    private fun finish(current: Int, result: LinkResult) {
        synchronized(lock) {
            // a profile read is valid even if the user started another login meanwhile; the state is the newer login's
            if (result is LinkResult.Linked) {
                storage.saveAccount(result.account)
                _account.value = result.account
            }
            if (current == attempt) {
                _state.value = when (result) {
                    is LinkResult.Linked -> LinkState.Idle
                    is LinkResult.Failed -> LinkState.Failed(result.error, result.detail)
                }
            }
        }
        when (result) {
            is LinkResult.Linked -> log.log("Discord account: linked as ${result.account.displayName}")
            is LinkResult.Failed -> log.log(
                "Discord account: linking failed (${result.error.name.lowercase()}" +
                    (if (result.detail.isEmpty()) "" else ", ${result.detail}") + ")",
            )
        }
        _results.tryEmit(result)
    }

    /** Deletes the pending login and stops its expiry. Under [lock]. */
    private fun endPending() {
        storage.savePending(null)
        expiry?.cancel()
        expiry = null
    }

    /** Under [lock]. */
    private fun scheduleExpiry(pending: PendingLogin) {
        expiry?.cancel()
        val left = (pending.startedAtMs + PendingLogin.MAX_AGE_MS - clock.wallMs()).coerceAtLeast(0)
        expiry = scope.launch {
            delay(left + EXPIRY_SLACK_MS)
            expireStale()
        }
    }

    private companion object {
        /** The expiry runs a moment after the deadline (a timer may fire early by the clock's resolution). */
        const val EXPIRY_SLACK_MS = 1_000L

        /** Compared in constant time: how much of a guess matches must not show in the timing. */
        fun sameState(expected: String, actual: String): Boolean =
            MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), actual.toByteArray(Charsets.UTF_8))
    }
}
