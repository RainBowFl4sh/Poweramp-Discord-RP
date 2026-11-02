package io.github.rainbowfl4sh.powerampdiscordrp.core.account

import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class AccountLinkerTest {
    private val server = MockWebServer().apply { start() }
    private val http = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
    private val oauth = DiscordOAuth(http, apiBase = server.url("/api/v10").toString(), timeoutSec = 2)
    private val storage = MemoryStorage()
    private val log = mutableListOf<String>()

    private val clientId = "1559554394301800528"
    private val flash = LinkedAccount("80351110224678912", "flash", "Fl4sh", "8342729096ea3675442027381ff50dfe", WALL_BASE)

    @After
    fun tearDown() = server.close()

    private class MemoryStorage : AccountStorage {
        var account: LinkedAccount? = null
        var pending: PendingLogin? = null
        override fun loadAccount() = account
        override fun saveAccount(account: LinkedAccount?) {
            this.account = account
        }
        override fun loadPending() = pending
        override fun savePending(pending: PendingLogin?) {
            this.pending = pending
        }
    }

    /** Wall clock of the test's virtual time. */
    private fun TestScope.clock() = object : Clock {
        override fun nowMs(): Long = testScheduler.currentTime
        override fun wallMs(): Long = WALL_BASE + testScheduler.currentTime
    }

    private fun TestScope.linker() = AccountLinker(storage, { oauth }, backgroundScope, clock(), { log += it })

    private fun answer(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    /** Discord's answers for a login that works: token, profile, revoke. */
    private fun discordAllows() {
        answer(200, """{"token_type":"Bearer","access_token":"tok3n","expires_in":604800,"refresh_token":"r","scope":"identify"}""")
        answer(200, """{"id":"80351110224678912","username":"flash","global_name":"Fl4sh","avatar":"8342729096ea3675442027381ff50dfe"}""")
        answer(200, "{}")
    }

    private fun redirect(state: String, code: String = "c0de") = "powerampdiscordrp://auth/callback?code=$code&state=$state"

    @Test
    fun startSavesALoginAndGivesDiscordsPage() = runTest {
        val linker = linker()
        val url = linker.start(" $clientId ")!!.toHttpUrl()
        val pending = storage.pending!!
        assertEquals(clientId, pending.clientId)
        assertEquals(WALL_BASE, pending.startedAtMs)
        assertTrue(Pkce.isValidVerifier(pending.verifier))
        assertEquals(clientId, url.queryParameter("client_id"))
        assertEquals(pending.state, url.queryParameter("state"))
        assertEquals(Pkce.challenge(pending.verifier), url.queryParameter("code_challenge"))
        assertEquals(LinkState.Waiting, linker.state.value)
    }

    @Test
    fun noLoginWithoutAnApplicationId() = runTest {
        val linker = linker()
        assertNull(linker.start(""))
        assertNull(linker.start("Poweramp"))
        assertNull(storage.pending)
        assertEquals(LinkState.Idle, linker.state.value)
    }

    @Test
    fun theAnswerToThePendingLoginLinksTheAccount() = runTest {
        val linker = linker()
        val results = mutableListOf<LinkResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { linker.results.toList(results) }
        linker.start(clientId)
        val state = storage.pending!!.state
        val verifier = storage.pending!!.verifier
        discordAllows()

        assertTrue(linker.handleRedirect(redirect(state)))

        assertEquals(flash, linker.account.value)
        assertEquals(flash, storage.account)
        assertNull("the state and the verifier are used once", storage.pending)
        assertEquals(LinkState.Idle, linker.state.value)
        assertEquals(listOf<LinkResult>(LinkResult.Linked(flash)), results)
        val exchange = server.takeRequest().body!!.utf8()
        assertTrue(exchange.contains("code_verifier=$verifier"))
        assertTrue(exchange.contains("code=c0de"))
        assertTrue(log.contains("Discord account: linked as Fl4sh"))
        assertTrue(log.none { state in it || verifier in it || "c0de" in it || "tok3n" in it })

        // the same answer again (replayed): nothing pending any more
        assertFalse(linker.handleRedirect(redirect(state)))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun answersWithoutAPendingLoginAreIgnored() = runTest {
        val linker = linker()
        assertFalse(linker.handleRedirect(redirect("whatever")))
        assertFalse(linker.handleRedirect("powerampdiscordrp://auth/callback?code=x"))
        assertFalse(linker.handleRedirect("https://example.com/?code=x&state=y"))
        assertEquals(0, server.requestCount)
        assertEquals(LinkState.Idle, linker.state.value)
        assertNull(linker.account.value)
    }

    @Test
    fun anAnswerForAnotherLoginIsIgnoredAndTheLoginGoesOnWaiting() = runTest {
        val linker = linker()
        linker.start(clientId)
        val pending = storage.pending!!

        assertFalse(linker.handleRedirect(redirect("forged-${pending.state}")))
        assertFalse(linker.handleRedirect(redirect(pending.state.dropLast(1))))

        assertEquals(0, server.requestCount)
        assertEquals(pending, storage.pending)
        assertEquals(LinkState.Waiting, linker.state.value)
        // the real answer still works
        discordAllows()
        assertTrue(linker.handleRedirect(redirect(pending.state)))
        assertEquals(flash, linker.account.value)
    }

    @Test
    fun aNewerLoginReplacesTheWaitingOne() = runTest {
        val linker = linker()
        linker.start(clientId)
        val first = storage.pending!!.state
        linker.start(clientId)
        val second = storage.pending!!.state
        assertFalse(linker.handleRedirect(redirect(first)))
        discordAllows()
        assertTrue(linker.handleRedirect(redirect(second)))
    }

    @Test
    fun theUserSaysNo() = runTest {
        val linker = linker()
        linker.start(clientId)
        val state = storage.pending!!.state
        assertTrue(linker.handleRedirect("powerampdiscordrp://auth/callback?error=access_denied&error_description=denied&state=$state"))
        assertEquals(LinkState.Failed(LinkError.CANCELLED, "access_denied: denied"), linker.state.value)
        assertNull(storage.pending)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aRefusalKeepsTheAccountLinkedBefore() = runTest {
        storage.account = flash
        val linker = linker()
        linker.start(clientId)   // "Refresh"
        answer(401, """{"error":"invalid_client"}""")
        assertTrue(linker.handleRedirect(redirect(storage.pending!!.state)))
        assertEquals(LinkState.Failed(LinkError.NOT_PUBLIC_CLIENT, "HTTP 401 invalid_client"), linker.state.value)
        assertEquals(flash, linker.account.value)
        assertTrue(log.contains("Discord account: linking failed (not_public_client, HTTP 401 invalid_client)"))
    }

    @Test
    fun aLateAnswerIsIgnored() = runTest {
        val linker = linker()
        linker.start(clientId)
        val state = storage.pending!!.state
        advanceTimeBy(PendingLogin.MAX_AGE_MS + 1)   // past the deadline, before the expiry ran
        assertFalse(linker.handleRedirect(redirect(state)))
        assertEquals(LinkState.Failed(LinkError.EXPIRED), linker.state.value)
        assertNull(storage.pending)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aLoginWithoutAnswerEndsAfterTenMinutes() = runTest {
        val linker = linker()
        linker.start(clientId)
        advanceTimeBy(PendingLogin.MAX_AGE_MS - 1)
        runCurrent()
        assertEquals(LinkState.Waiting, linker.state.value)
        assertNotNull(storage.pending)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(LinkState.Failed(LinkError.EXPIRED), linker.state.value)
        assertNull(storage.pending)
    }

    @Test
    fun cancelEndsTheLogin() = runTest {
        val linker = linker()
        linker.start(clientId)
        val state = storage.pending!!.state
        linker.cancel()
        assertEquals(LinkState.Idle, linker.state.value)
        assertNull(storage.pending)
        assertFalse(linker.handleRedirect(redirect(state)))
    }

    @Test
    fun unlinkForgetsTheAccount() = runTest {
        storage.account = flash
        val linker = linker()
        assertEquals(flash, linker.account.value)
        linker.unlink()
        assertNull(linker.account.value)
        assertNull(storage.account)
    }

    @Test
    fun aWaitingLoginSurvivesTheProcess() = runTest {
        linker().start(clientId)
        val state = storage.pending!!.state
        // a new process: a new linker with the same storage
        advanceTimeBy(60_000)
        val restarted = linker()
        assertEquals(LinkState.Waiting, restarted.state.value)
        discordAllows()
        assertTrue(restarted.handleRedirect(redirect(state)))
        assertEquals(flash.copy(linkedAtMs = WALL_BASE + 60_000), restarted.account.value)
    }

    @Test
    fun anExpiredLoginOfAnEarlierProcessIsDeleted() = runTest {
        storage.pending = PendingLogin("s", "v".repeat(43), clientId, WALL_BASE - PendingLogin.MAX_AGE_MS - 1)
        val linker = linker()
        assertEquals(LinkState.Idle, linker.state.value)
        assertNull(storage.pending)
        // and one "from the future" (the clock was set back) as well
        storage.pending = PendingLogin("s", "v".repeat(43), clientId, WALL_BASE + 60_000)
        linker.expireStale()
        assertNull(storage.pending)
    }

    private companion object {
        const val WALL_BASE = 1_800_000_000_000L
    }
}
