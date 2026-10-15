package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.rainbowfl4sh.powerampdiscordrp.settings.EnvState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.AppTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config as RobolectricConfig

/** Android 13+ never asks for the notification permission by itself (targetSdk 33+): the app asks once. */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34], application = Application::class)   // the app's own Application wires the whole runtime
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationPermissionTest {
    private val scope = MainScope()

    @After
    fun tearDown() = scope.cancel()

    /**
     * Opens the main screen like MainActivity does. With a finished environment check: an endless progress animation
     * in an activity left open would keep Robolectric's looper busy forever.
     */
    private fun start(): ComponentActivity {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val env = EnvState("build-1032-uni", 1032, true, true, false, true, false)
        activity.setContent { AppTheme { MainScreen(FakeAppModel(scope, env = env)) } }
        ShadowLooper.idleMainLooper()
        return activity
    }

    @Test
    fun askedOnceOnTheFirstStart() {
        val first = start()
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            shadowOf(first).lastRequestedPermission?.requestedPermissions?.toList(),
        )
        assertTrue(NotificationAskState(first).asked)

        // the next start (also after the dialog was only closed) leaves it to the checklist's "Allow"
        val second = start()
        assertNull(shadowOf(second).lastRequestedPermission)
    }

    @Test
    fun notAskedWhenAllowed() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val activity = start()
        assertNull(shadowOf(activity).lastRequestedPermission)
        assertFalse(NotificationAskState(activity).asked)
    }
}
