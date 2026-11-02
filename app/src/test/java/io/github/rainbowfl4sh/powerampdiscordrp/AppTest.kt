package io.github.rainbowfl4sh.powerampdiscordrp

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.RemoteImages
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The process outlives the screens (presence service): their pictures are let go when the UI is hidden. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppTest {
    private val app: App = ApplicationProvider.getApplicationContext()
    private val url = "https://example.com/cover.png"

    @Test
    fun hidingTheUiDropsTheScreensPictures() {
        RemoteImages.put(url, Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).asImageBitmap())
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)   // UI still visible: kept
        assertNotNull(RemoteImages.peek(url))
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertNull(RemoteImages.peek(url))
    }
}
