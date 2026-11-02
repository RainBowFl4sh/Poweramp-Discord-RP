package io.github.rainbowfl4sh.powerampdiscordrp.core.account

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Proxy
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class DiscordOAuthTest {
    private val server = MockWebServer().apply { start() }
    private val http = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
    private val log = mutableListOf<String>()
    private val oauth = DiscordOAuth(http, { log += it }, apiBase = server.url("/api/v10").toString(), timeoutSec = 1)

    private val clientId = "1559554394301800528"
    private val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    private val token = "6qrZcUqja7812RVdnEKjpzOL4CvHBFG"

    @After
    fun tearDown() = server.close()

    private fun answer(code: Int, body: String) =
        server.enqueue(MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build())

    private fun tokenAnswer() = answer(
        200,
        """{"token_type":"Bearer","access_token":"$token","expires_in":604800,"refresh_token":"D43f5y0ahjqew82jZ4NViEr2YafMKhue","scope":"identify"}""",
    )

    private fun userAnswer(globalName: String = "\"Fl4sh\"", avatar: String = "\"8342729096ea3675442027381ff50dfe\"") = answer(
        200,
        """{"id":"80351110224678912","username":"flash","avatar":$avatar,"discriminator":"0","public_flags":0,"flags":0,
           "banner":null,"accent_color":null,"global_name":$globalName,"avatar_decoration_data":null,"banner_color":null,
           "clan":null,"primary_guild":null,"mfa_enabled":true,"locale":"de","premium_type":0}""",
    )

    private fun form(req: RecordedRequest): Map<String, String> = req.body!!.utf8().split('&').associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }

    private fun link() = oauth.link(clientId, "code123", verifier, nowMs = 1_800_000_000_000)

    // ---- authorization page and redirect

    @Test
    fun authorizeUrlCarriesEverythingDiscordNeeds() {
        val url = DiscordOAuth.authorizeUrl(clientId, "st4te", Pkce.challenge(verifier)).toHttpUrl()
        assertEquals("https", url.scheme)
        assertEquals("discord.com", url.host)
        assertEquals("/oauth2/authorize", url.encodedPath)
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals(clientId, url.queryParameter("client_id"))
        assertEquals("identify", url.queryParameter("scope"))
        assertEquals("st4te", url.queryParameter("state"))
        assertEquals("powerampdiscordrp://auth/callback", url.queryParameter("redirect_uri"))
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("none", url.queryParameter("prompt"))
        // the verifier itself never leaves the app before the token exchange; the redirect URI is encoded
        assertFalse(url.toString().contains(verifier))
        assertTrue(url.toString().contains("redirect_uri=powerampdiscordrp%3A%2F%2Fauth%2Fcallback"))
    }

    @Test
    fun redirectWithACode() {
        assertEquals(
            OAuthRedirect.Code("NhhvTDYsFcdgNLnnLijcl7Ku7bEEeee", "abc-_~."),
            DiscordOAuth.parseRedirect("powerampdiscordrp://auth/callback?code=NhhvTDYsFcdgNLnnLijcl7Ku7bEEeee&state=abc-_~."),
        )
        // order and case of the scheme / host do not matter, encoded values are decoded
        assertEquals(
            OAuthRedirect.Code("a b", "s/1"),
            DiscordOAuth.parseRedirect("PowerampDiscordRP://AUTH/callback?state=s%2F1&code=a+b"),
        )
    }

    @Test
    fun redirectWithAnError() {
        val denied = DiscordOAuth.parseRedirect(
            "powerampdiscordrp://auth/callback?error=access_denied&error_description=The+resource+owner+or+authorization+server+denied+the+request&state=s1",
        )
        assertEquals(
            OAuthRedirect.Error("access_denied", "The resource owner or authorization server denied the request", "s1"),
            denied,
        )
        assertEquals(LinkError.CANCELLED, DiscordOAuth.failureOf(denied as OAuthRedirect.Error).error)
        val redirect = OAuthRedirect.Error("invalid_request", "Invalid \"redirect_uri\" in request.", "s1")
        assertEquals(LinkError.REDIRECT_NOT_ADDED, DiscordOAuth.failureOf(redirect).error)
        val other = DiscordOAuth.failureOf(OAuthRedirect.Error("invalid_scope", "The requested scope is invalid", "s1"))
        assertEquals(LinkResult.Failed(LinkError.DISCORD_ERROR, "invalid_scope: The requested scope is invalid"), other)
    }

    @Test
    fun anythingElseIsNoRedirect() {
        listOf(
            "https://auth/callback?code=c&state=s",                          // another scheme
            "powerampdiscordrp://evil/callback?code=c&state=s",              // another host
            "powerampdiscordrp://auth/callback/x?code=c&state=s",            // another path
            "powerampdiscordrp://auth?code=c&state=s",
            "powerampdiscordrp:auth/callback?code=c&state=s",                // no host at all
            "powerampdiscordrp://auth/callback?code=c",                      // no state
            "powerampdiscordrp://auth/callback?code=c&state=",
            "powerampdiscordrp://auth/callback?state=s",                     // neither code nor error
            "powerampdiscordrp://auth/callback?code=&state=s",
            "powerampdiscordrp://auth/callback?code=c&error=x&state=s",      // both
            "powerampdiscordrp://auth/callback?code=c&state=s&state=t",      // a parameter twice
            "powerampdiscordrp://auth/callback?code=c&code=d&state=s",
            "powerampdiscordrp://auth/callback?code=%zz&state=s",            // broken encoding
            "powerampdiscordrp://auth/callback?code=${"c".repeat(2000)}&state=s",
            "powerampdiscordrp://auth/callback?code=c&state=s&x=${"y".repeat(5000)}",
            "not a uri at all",
            "",
        ).forEach { assertNull(it, DiscordOAuth.parseRedirect(it)) }
    }

    // ---- token, profile, revoke

    @Test
    fun linkReadsTheProfileAndRevokesTheTokenAtOnce() {
        tokenAnswer()
        userAnswer()
        answer(200, "{}")

        val result = link()

        assertEquals(
            LinkResult.Linked(
                LinkedAccount("80351110224678912", "flash", "Fl4sh", "8342729096ea3675442027381ff50dfe", 1_800_000_000_000),
            ),
            result,
        )
        val exchange = server.takeRequest()
        assertEquals("POST", exchange.method)
        assertEquals("/api/v10/oauth2/token", exchange.url.encodedPath)
        assertTrue(exchange.headers["Content-Type"]!!.startsWith("application/x-www-form-urlencoded"))
        assertEquals(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to "code123",
                "redirect_uri" to "powerampdiscordrp://auth/callback",
                "client_id" to clientId,
                "code_verifier" to verifier,
            ),
            form(exchange),
        )
        assertNull("a public client has no secret", exchange.headers["Authorization"])

        val me = server.takeRequest()
        assertEquals("GET", me.method)
        assertEquals("/api/v10/users/@me", me.url.encodedPath)
        assertEquals("Bearer $token", me.headers["Authorization"])

        val revoke = server.takeRequest()
        assertEquals("POST", revoke.method)
        assertEquals("/api/v10/oauth2/token/revoke", revoke.url.encodedPath)
        assertEquals(mapOf("token" to token, "token_type_hint" to "access_token", "client_id" to clientId), form(revoke))
        assertEquals(3, server.requestCount)
        // nothing secret in the log
        assertTrue(log.none { token in it || verifier in it || "code123" in it })
    }

    @Test
    fun withoutDisplayNameOrAvatar() {
        tokenAnswer()
        userAnswer(globalName = "null", avatar = "null")
        answer(200, "{}")
        val account = (link() as LinkResult.Linked).account
        assertEquals("flash", account.displayName)
        assertEquals("", account.avatar)
        assertEquals("https://cdn.discordapp.com/embed/avatars/5.png", account.avatarUrl)
    }

    @Test
    fun aFailedRevokeKeepsTheProfile() {
        tokenAnswer()
        userAnswer()
        answer(503, "upstream connect error")
        assertTrue(link() is LinkResult.Linked)
        assertTrue(log.any { it.contains("revoking the access failed (HTTP 503)") })
    }

    @Test
    fun notAPublicClient() {
        answer(401, """{"error":"invalid_client"}""")
        val result = link() as LinkResult.Failed
        assertEquals(LinkError.NOT_PUBLIC_CLIENT, result.error)
        assertEquals("HTTP 401 invalid_client", result.detail)
        assertEquals("no profile request, nothing to revoke", 1, server.requestCount)
    }

    @Test
    fun aRedirectTheApplicationDoesNotKnow() {
        answer(400, """{"error":"invalid_grant","error_description":"Invalid \"redirect_uri\" in request."}""")
        assertEquals(LinkError.REDIRECT_NOT_ADDED, (link() as LinkResult.Failed).error)
    }

    @Test
    fun anExpiredOrUsedCode() {
        answer(400, """{"error":"invalid_grant","error_description":"Invalid \"code\" in request."}""")
        val result = link() as LinkResult.Failed
        assertEquals(LinkError.EXPIRED, result.error)
        assertEquals("HTTP 400 invalid_grant: Invalid \"code\" in request.", result.detail)
    }

    @Test
    fun otherRefusalsOfTheTokenEndpoint() {
        answer(429, """{"message":"You are being rate limited.","retry_after":1.5,"global":false}""")
        assertEquals(
            LinkResult.Failed(LinkError.DISCORD_ERROR, "HTTP 429: You are being rate limited."),
            link(),
        )
        answer(500, "<html>oops</html>")
        assertEquals(LinkResult.Failed(LinkError.DISCORD_ERROR, "HTTP 500"), link())
    }

    @Test
    fun malformedTokenAnswers() {
        answer(200, "<html>not json</html>")
        assertEquals(LinkError.BAD_ANSWER, (link() as LinkResult.Failed).error)
        answer(200, """{"token_type":"Bearer"}""")
        assertEquals(LinkError.BAD_ANSWER, (link() as LinkResult.Failed).error)
        answer(200, """{"token_type":"mac","access_token":"x"}""")
        assertEquals(LinkError.BAD_ANSWER, (link() as LinkResult.Failed).error)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun aMalformedProfileStillRevokesTheToken() {
        tokenAnswer()
        answer(200, """{"id":"not a snowflake","username":"x"}""")
        answer(200, "{}")
        assertEquals(LinkError.BAD_ANSWER, (link() as LinkResult.Failed).error)
        server.takeRequest()
        server.takeRequest()
        assertEquals("/api/v10/oauth2/token/revoke", server.takeRequest().url.encodedPath)

        tokenAnswer()
        answer(200, "[1, 2")
        answer(200, "{}")
        assertEquals(LinkError.BAD_ANSWER, (link() as LinkResult.Failed).error)
        assertEquals(6, server.requestCount)
    }

    @Test
    fun aRefusedProfile() {
        tokenAnswer()
        answer(401, """{"message":"401: Unauthorized","code":0}""")
        answer(200, "{}")
        assertEquals(LinkResult.Failed(LinkError.DISCORD_ERROR, "HTTP 401: 401: Unauthorized"), link())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun timeoutsAreNetworkErrors() {
        server.enqueue(MockResponse.Builder().code(200).body("{}").headersDelay(3, TimeUnit.SECONDS).build())
        val result = link() as LinkResult.Failed
        assertEquals(LinkError.NETWORK, result.error)
        assertTrue(result.detail, result.detail.startsWith("token: "))
    }

    @Test
    fun aProfileTimeoutStillTriesToRevoke() {
        tokenAnswer()
        server.enqueue(MockResponse.Builder().code(200).body("{}").headersDelay(3, TimeUnit.SECONDS).build())
        answer(200, "{}")
        val result = link() as LinkResult.Failed
        assertEquals(LinkError.NETWORK, result.error)
        assertTrue(result.detail, result.detail.startsWith("profile: "))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun offline() {
        val port = server.port
        server.close()
        val offline = DiscordOAuth(http, apiBase = "http://127.0.0.1:$port/api/v10", timeoutSec = 1)
        assertEquals(LinkError.NETWORK, (offline.link(clientId, "c", verifier, 0) as LinkResult.Failed).error)
        assertFalse(offline.revoke(clientId, token))
    }
}
