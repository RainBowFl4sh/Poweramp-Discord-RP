package io.github.rainbowfl4sh.powerampdiscordrp.core.account

import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.BufferedSource
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** Why linking the Discord account did not work. */
enum class LinkError {
    CANCELLED,            // the user did not allow it (access_denied)
    REDIRECT_NOT_ADDED,   // the Discord application does not list DiscordOAuth.REDIRECT_URI under OAuth2 -> Redirects
    NOT_PUBLIC_CLIENT,    // the token exchange wants a client secret: "Public Client" is off (401 invalid_client)
    EXPIRED,              // the code was used or had expired, or the login took longer than PendingLogin.MAX_AGE_MS
    NETWORK,              // Discord could not be reached (offline, DNS, timeout)
    DISCORD_ERROR,        // any other refusal by Discord - the detail says which
    BAD_ANSWER,           // an answer unlike what Discord's API returns (malformed JSON, no user ID)
}

/** Outcome of [DiscordOAuth.link]. */
sealed interface LinkResult {
    data class Linked(val account: LinkedAccount) : LinkResult

    /** [detail]: for the log and, for [LinkError.DISCORD_ERROR], the screen (Discord's own words). Never holds a secret. */
    data class Failed(val error: LinkError, val detail: String = "") : LinkResult
}

/** What Discord sent back to [DiscordOAuth.REDIRECT_URI]. */
sealed interface OAuthRedirect {
    val state: String

    /** The user allowed it: [code] is exchanged for a token (with the code verifier only this app knows). */
    data class Code(val code: String, override val state: String) : OAuthRedirect

    /** The user or Discord refused, e.g. error "access_denied". */
    data class Error(val error: String, val description: String, override val state: String) : OAuthRedirect
}

/**
 * Discord's OAuth2 authorization code flow with PKCE for a public client (no client secret), scope "identify" only:
 *   1. [authorizeUrl] is opened - in the Discord app, which handles discord.com links itself, or in the browser
 *   2. Discord redirects to [REDIRECT_URI] with ?code=..&state=.. ([parseRedirect])
 *   3. [link]: POST {api}/oauth2/token (code + code_verifier) -> access token, GET {api}/users/@me -> the public
 *      profile, and POST {api}/oauth2/token/revoke right away: the app keeps the profile, never a token.
 * The network calls are blocking (call them from a background thread) and never throw.
 */
class DiscordOAuth(
    private val http: OkHttpClient,
    private val log: Logger = Logger.NONE,
    private val apiBase: String = API_BASE,
    private val timeoutSec: Long = TIMEOUT_SEC,
) {
    private val api get() = apiBase.trimEnd('/')

    /**
     * Exchanges [code] for a token, reads the profile with it and revokes the token again (also when reading failed).
     * [nowMs] becomes [LinkedAccount.linkedAtMs].
     */
    fun link(clientId: String, code: String, verifier: String, nowMs: Long, redirectUri: String = REDIRECT_URI): LinkResult {
        val token = when (val t = exchange(clientId, code, verifier, redirectUri)) {
            is Exchange.Granted -> t.accessToken
            is Exchange.Refused -> return t.failure
        }
        return try {
            profile(token, nowMs)
        } finally {
            revoke(clientId, token)
        }
    }

    /** Revokes [token] (and with it the whole grant's tokens); false if Discord could not be told. */
    fun revoke(clientId: String, token: String): Boolean = try {
        val answer = send {
            url("$api/oauth2/token/revoke")
            post(
                FormBody.Builder()
                    .add("token", token)
                    .add("token_type_hint", "access_token")
                    .add("client_id", clientId)
                    .build(),
            )
        }
        if (answer.code !in 200..299) log.log("Discord account: revoking the access failed (HTTP ${answer.code})")
        answer.code in 200..299
    } catch (e: Exception) {   // the token was never stored; it expires on Discord's side by itself
        log.log("Discord account: revoking the access failed (${e.javaClass.simpleName}: ${e.message ?: "no details"})")
        false
    }

    private sealed interface Exchange {
        class Granted(val accessToken: String) : Exchange
        class Refused(val failure: LinkResult.Failed) : Exchange
    }

    private fun exchange(clientId: String, code: String, verifier: String, redirectUri: String): Exchange {
        val answer = try {
            send {
                url("$api/oauth2/token")
                post(
                    FormBody.Builder()
                        .add("grant_type", "authorization_code")
                        .add("code", code)
                        .add("redirect_uri", redirectUri)
                        .add("client_id", clientId)
                        .add("code_verifier", verifier)
                        .build(),
                )
            }
        } catch (e: IOException) {
            return Exchange.Refused(networkFailure("token", e))
        } catch (e: Exception) {
            return Exchange.Refused(LinkResult.Failed(LinkError.BAD_ANSWER, "token: ${e.javaClass.simpleName}"))
        }
        if (answer.code != 200) return Exchange.Refused(tokenRefusal(answer))
        val json = parseObject(answer.body)
            ?: return Exchange.Refused(LinkResult.Failed(LinkError.BAD_ANSWER, "token: HTTP 200, not a JSON object"))
        val token = json.str("access_token")
        val type = json.str("token_type")
        if (token.isEmpty() || (type.isNotEmpty() && !type.equals("Bearer", ignoreCase = true))) {
            return Exchange.Refused(LinkResult.Failed(LinkError.BAD_ANSWER, "token: HTTP 200 without a bearer token"))
        }
        return Exchange.Granted(token)
    }

    /** The token endpoint said no: what to tell the user ("Public Client" off, redirect missing, code expired, ...). */
    private fun tokenRefusal(answer: Answer): LinkResult.Failed {
        val json = parseObject(answer.body)
        val error = json?.str("error").orEmpty()
        val description = json?.str("error_description").orEmpty().ifEmpty { json?.str("message").orEmpty() }
        val kind = when {
            // a client that is not public must authenticate with its secret, which this app does not have
            answer.code == 401 || error == "invalid_client" -> LinkError.NOT_PUBLIC_CLIENT
            mentionsRedirect(description) -> LinkError.REDIRECT_NOT_ADDED
            error == "invalid_grant" -> LinkError.EXPIRED
            else -> LinkError.DISCORD_ERROR
        }
        return LinkResult.Failed(kind, detail("HTTP ${answer.code}", error, description))
    }

    private fun profile(token: String, nowMs: Long): LinkResult {
        val answer = try {
            send {
                url("$api/users/@me")
                header("Authorization", "Bearer $token")
                get()
            }
        } catch (e: IOException) {
            return networkFailure("profile", e)
        } catch (e: Exception) {
            return LinkResult.Failed(LinkError.BAD_ANSWER, "profile: ${e.javaClass.simpleName}")
        }
        if (answer.code != 200) {
            val json = parseObject(answer.body)
            return LinkResult.Failed(LinkError.DISCORD_ERROR, detail("HTTP ${answer.code}", "", json?.str("message").orEmpty()))
        }
        val json = parseObject(answer.body) ?: return LinkResult.Failed(LinkError.BAD_ANSWER, "profile: HTTP 200, not a JSON object")
        val id = json.str("id").trim()
        val username = json.str("username").trim()
        if (!DiscordAvatars.isSnowflake(id) || username.isEmpty()) {
            return LinkResult.Failed(LinkError.BAD_ANSWER, "profile: HTTP 200 without a user ID and name")
        }
        return LinkResult.Linked(
            LinkedAccount(
                id = id,
                username = username.take(MAX_NAME_LENGTH),
                globalName = json.str("global_name").trim().take(MAX_NAME_LENGTH),
                avatar = json.str("avatar").trim().take(MAX_NAME_LENGTH),
                linkedAtMs = nowMs,
            ),
        )
    }

    /** status + body; [body] null = larger than anything Discord sends for these calls. */
    private class Answer(val code: Int, val body: String?)

    private fun send(build: Request.Builder.() -> Unit): Answer {
        val call = http.newCall(Request.Builder().apply(build).build())
        call.timeout().timeout(timeoutSec, TimeUnit.SECONDS)
        return call.execute().use { response -> Answer(response.code, readLimited(response.body.source())) }
    }

    private fun networkFailure(step: String, e: IOException) =
        LinkResult.Failed(LinkError.NETWORK, "$step: ${e.javaClass.simpleName}: ${e.message ?: "no details"}")

    companion object {
        const val API_BASE = "https://discord.com/api/v10"
        const val AUTHORIZE_URL = "https://discord.com/oauth2/authorize"

        /** Where Discord sends the user back; registered for this app in the manifest (scheme, host and path). */
        const val REDIRECT_SCHEME = "powerampdiscordrp"
        const val REDIRECT_HOST = "auth"
        const val REDIRECT_PATH = "/callback"
        const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST$REDIRECT_PATH"

        /** Name, display name, ID and avatar - nothing else (no e-mail, no servers). */
        const val SCOPE = "identify"

        const val TIMEOUT_SEC = 20L
        private const val MAX_ANSWER_BYTES = 64L * 1024
        private const val MAX_REDIRECT_LENGTH = 4096
        private const val MAX_PARAM_LENGTH = 1024
        private const val MAX_NAME_LENGTH = 100

        private val JSON = Json { isLenient = true; ignoreUnknownKeys = true }

        /**
         * Discord's authorization page for [clientId]. prompt=none: Discord skips its consent screen while it still holds
         * an authorization of this application, and asks otherwise. As [link] revokes the access right away, that is
         * usually gone, so "Refresh" normally shows the page again (one tap on Authorize).
         */
        fun authorizeUrl(
            clientId: String,
            state: String,
            challenge: String,
            redirectUri: String = REDIRECT_URI,
            base: String = AUTHORIZE_URL,
        ): String = base.toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("scope", SCOPE)
            .addQueryParameter("state", state)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("prompt", "none")
            .build()
            .toString()

        /**
         * The answer in a link to [REDIRECT_URI] (exactly that scheme, host and path); null for anything else, a
         * parameter given twice, one that is too long or a missing state. Checking the state is up to the caller.
         */
        fun parseRedirect(uri: String): OAuthRedirect? {
            if (uri.length > MAX_REDIRECT_LENGTH) return null
            val parsed = try {
                URI(uri)
            } catch (_: URISyntaxException) {
                return null
            }
            if (!REDIRECT_SCHEME.equals(parsed.scheme, ignoreCase = true) ||
                !REDIRECT_HOST.equals(parsed.host, ignoreCase = true) ||
                parsed.rawPath != REDIRECT_PATH
            ) {
                return null
            }
            val params = queryParameters(parsed.rawQuery.orEmpty()) ?: return null
            val state = params["state"].orEmpty()
            val code = params["code"]
            val error = params["error"]
            if (state.isEmpty()) return null
            return when {
                code != null && error == null && code.isNotEmpty() -> OAuthRedirect.Code(code, state)
                error != null && code == null && error.isNotEmpty() ->
                    OAuthRedirect.Error(error, params["error_description"].orEmpty(), state)
                else -> null
            }
        }

        /** What the user is told about a refusal that came back with the redirect. */
        fun failureOf(redirect: OAuthRedirect.Error): LinkResult.Failed {
            val kind = when {
                redirect.error == "access_denied" -> LinkError.CANCELLED
                mentionsRedirect(redirect.description) -> LinkError.REDIRECT_NOT_ADDED
                else -> LinkError.DISCORD_ERROR
            }
            return LinkResult.Failed(kind, detail("", redirect.error, redirect.description))
        }

        private fun mentionsRedirect(text: String) = "redirect_uri" in text.lowercase()

        /** "HTTP 400 invalid_grant: Invalid "code" in request." - the parts that are there. */
        private fun detail(status: String, error: String, description: String): String {
            val head = listOf(status, error.trim()).filter { it.isNotEmpty() }.joinToString(" ")
            val text = description.trim().take(MAX_PARAM_LENGTH)
            return if (text.isEmpty()) head else if (head.isEmpty()) text else "$head: $text"
        }

        /** Decoded query parameters; null if one is given twice, too long or not properly encoded. */
        private fun queryParameters(rawQuery: String): Map<String, String>? {
            val result = HashMap<String, String>()
            if (rawQuery.isEmpty()) return result
            for (pair in rawQuery.split('&')) {
                if (pair.isEmpty()) continue
                val key = decode(pair.substringBefore('=')) ?: return null
                val value = decode(pair.substringAfter('=', "")) ?: return null
                if (value.length > MAX_PARAM_LENGTH || result.put(key, value) != null) return null
            }
            return result
        }

        private fun decode(text: String): String? = try {
            URLDecoder.decode(text, "UTF-8")
        } catch (_: IllegalArgumentException) {
            null
        }

        private fun parseObject(text: String?): JsonObject? = try {
            text?.let { JSON.parseToJsonElement(it) as? JsonObject }
        } catch (_: Exception) {
            null
        }

        /** String (or number) value, "" if missing or JSON null. */
        private fun JsonObject.str(key: String): String =
            (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content ?: ""

        private fun readLimited(source: BufferedSource): String? {
            val buffer = Buffer()
            while (source.read(buffer, 8192) != -1L) {
                if (buffer.size > MAX_ANSWER_BYTES) return null
            }
            return buffer.readUtf8()
        }
    }
}
