package io.github.rainbowfl4sh.powerampdiscordrp.account

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.rainbowfl4sh.powerampdiscordrp.Graph
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The exported activity behind the OAuth2 redirect URI: only Discord's answer to the login this app started counts,
 * anything else is ignored. Runs with the app's own Application (Graph and the account linker are wired).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRedirectActivityTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun open(uri: String?): AuthRedirectActivity {
        val intent = Intent(app, AuthRedirectActivity::class.java).setAction(Intent.ACTION_VIEW)
        if (uri != null) intent.data = Uri.parse(uri)
        return Robolectric.buildActivity(AuthRedirectActivity::class.java, intent).setup().get()
    }

    /** Waits for a log line of the linker (it handles the answer in the background). */
    private fun awaitLog(text: String) {
        val deadline = System.currentTimeMillis() + 10_000
        while (Graph.log.lines.value.none { text in it } && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("log: ${Graph.log.lines.value}", Graph.log.lines.value.any { text in it })
    }

    @Test
    fun discordsAnswerBringsTheAppBack() {
        val activity = open("powerampdiscordrp://auth/callback?code=c0de&state=st4te")
        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(MainActivity::class.java.name, started.component!!.className)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(activity.isFinishing)
        // no login was started: the answer is ignored - nothing is linked, nothing sent to Discord
        awaitLog("ignored an answer of Discord - no login in progress")
        assertNull(Graph.account.account.value)
        assertTrue(Graph.log.lines.value.none { "c0de" in it || "st4te" in it })
    }

    @Test
    fun anAnswerForAnotherLoginLeavesTheLoginWaiting() {
        val linker = Graph.account
        assertNotNull(linker.start("1559554394301800528"))
        open("powerampdiscordrp://auth/callback?code=c0de&state=forged")
        awaitLog("it belongs to another login")
        assertEquals(LinkState.Waiting, linker.state.value)
        assertNull(linker.account.value)
        linker.cancel()
    }

    @Test
    fun otherLinksOnlyCloseIt() {
        for (uri in listOf(
            "powerampdiscordrp://evil/callback?code=c&state=s",
            "powerampdiscordrp://auth/callback/x?code=c&state=s",
            "powerampdiscordrp://auth/callback?state=s",
            "https://discord.com/oauth2/authorize",
            null,
        )) {
            val activity = open(uri)
            assertNull("$uri", shadowOf(activity).nextStartedActivity)
            assertTrue("$uri", activity.isFinishing)
        }
    }

    @Test
    fun onlyTheRedirectUriReachesIt() {
        fun resolves(uri: String): Boolean {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE)
            return app.packageManager.queryIntentActivities(intent, 0).any { it.activityInfo.name == AuthRedirectActivity::class.java.name }
        }
        assertTrue(resolves("powerampdiscordrp://auth/callback?code=c&state=s"))
        assertFalse(resolves("powerampdiscordrp://auth/other?code=c&state=s"))
        assertFalse(resolves("powerampdiscordrp://evil/callback?code=c&state=s"))
        assertFalse(resolves("https://auth/callback?code=c&state=s"))

        val info = app.packageManager.getActivityInfo(ComponentName(app, AuthRedirectActivity::class.java), PackageManager.GET_META_DATA)
        assertTrue("the browser / the Discord app must reach it", info.exported)
        assertEquals("never in the recent apps", ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS, info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS)
        assertEquals("an empty task affinity: no other app's task can take it in", "", info.taskAffinity.orEmpty())
    }
}
