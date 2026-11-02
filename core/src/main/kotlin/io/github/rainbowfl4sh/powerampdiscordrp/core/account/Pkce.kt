package io.github.rainbowfl4sh.powerampdiscordrp.core.account

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Proof Key for Code Exchange (RFC 7636) for the OAuth2 login of a public client: the app has no client secret, so the
 * authorization code alone must be worthless to whoever intercepts the redirect. Only the S256 method is used.
 */
object Pkce {
    /** Characters RFC 7636 allows in a code verifier ("unreserved": A-Z a-z 0-9 - . _ ~). */
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    private val encoder = Base64.getUrlEncoder().withoutPadding()

    /** A new code verifier: 32 random bytes as base64url without padding - 43 characters, 256 bits of entropy. */
    fun newVerifier(random: SecureRandom = SecureRandom()): String = randomToken(random, 32)

    /** A new value for the "state" parameter (ties the redirect to the login this app started). */
    fun newState(random: SecureRandom = SecureRandom()): String = randomToken(random, 24)

    /** code_challenge = BASE64URL(SHA-256(ASCII(code_verifier))) without padding (RFC 7636 4.2, "S256"). */
    fun challenge(verifier: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    /** 43-128 unreserved characters (RFC 7636 4.1). */
    fun isValidVerifier(verifier: String): Boolean = verifier.length in 43..128 && verifier.all { it in UNRESERVED }

    private fun randomToken(random: SecureRandom, bytes: Int): String = encoder.encodeToString(ByteArray(bytes).also(random::nextBytes))
}
