package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Tab clicks ([showPage]): a distant page is shown without composing the pages in between, a neighbour slides in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PageSwitchTest {
    private val composed = mutableListOf<Int>()
    private val offsets = mutableListOf<Float>()   // pager.currentPageOffsetFraction, frame by frame
    private lateinit var pager: PagerState
    private lateinit var scope: CoroutineScope

    private fun showPager() {
        MainThreadCompose.reset()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            pager = rememberPagerState { 6 }
            scope = rememberCoroutineScope()
            LaunchedEffect(pager) { snapshotFlow { pager.currentPageOffsetFraction }.collect { offsets += it } }
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                composed += page
                Box(Modifier.fillMaxSize())
            }
        }
        idle(Duration.ofMillis(100))
    }

    private fun idle(time: Duration) = shadowOf(Looper.getMainLooper()).idleFor(time)

    @Test
    fun aDistantPageIsShownAtOnce() {
        showPager()
        assertEquals(listOf(0), composed)

        composed.clear()
        scope.launch { pager.showPage(5) }
        idle(Duration.ofMillis(100))

        assertEquals(5, pager.currentPage)
        assertEquals(listOf(5), composed.distinct())   // pages 1 to 4 were never composed
        assertTrue(offsets.all { it == 0f })   // no frames on the way
    }

    @Test
    fun aNeighbourSlidesIn() {
        showPager()
        scope.launch { pager.showPage(3) }
        idle(Duration.ofMillis(100))
        composed.clear()

        offsets.clear()
        scope.launch { pager.showPage(4) }
        idle(Duration.ofSeconds(2))

        assertEquals(4, pager.currentPage)
        assertEquals(0f, pager.currentPageOffsetFraction)
        assertEquals(listOf(4), composed.distinct())
        assertTrue(offsets.any { it != 0f })   // frames on the way: it slid
    }
}
