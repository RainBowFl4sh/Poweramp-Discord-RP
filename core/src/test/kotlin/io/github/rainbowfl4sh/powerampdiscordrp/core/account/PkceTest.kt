package io.github.rainbowfl4sh.powerampdiscordrp.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class PkceTest {
    private val unreserved = Regex("[A-Za-z0-9._~-]+")

    @Test
    fun challengeMatchesTheRfc7636TestVector() {
        // RFC 7636, Appendix B
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun verifiersHaveTheRightLengthAndCharacters() {
        val random = SecureRandom()
        val verifiers = (1..200).map { Pkce.newVerifier(random) }
        verifiers.forEach { v ->
            assertEquals(v, 43, v.length)   // 32 random bytes, base64url without padding
            assertTrue(v, unreserved.matches(v))
            assertTrue(v, Pkce.isValidVerifier(v))
            // the challenge is base64url of 32 bytes, too: no padding, no '+' or '/'
            val c = Pkce.challenge(v)
            assertEquals(43, c.length)
            assertTrue(c, unreserved.matches(c))
        }
        assertEquals("every verifier is new", verifiers.size, verifiers.toSet().size)
    }

    @Test
    fun statesAreRandomAndSafeInAUrl() {
        val a = Pkce.newState()
        val b = Pkce.newState()
        assertNotEquals(a, b)
        assertEquals(32, a.length)
        assertTrue(unreserved.matches(a))
    }

    @Test
    fun verifierRules() {
        assertFalse(Pkce.isValidVerifier("a".repeat(42)))
        assertTrue(Pkce.isValidVerifier("a".repeat(43)))
        assertTrue(Pkce.isValidVerifier("a".repeat(128)))
        assertFalse(Pkce.isValidVerifier("a".repeat(129)))
        assertFalse(Pkce.isValidVerifier("a".repeat(42) + "/"))
        assertFalse(Pkce.isValidVerifier("a".repeat(42) + "="))
    }
}
