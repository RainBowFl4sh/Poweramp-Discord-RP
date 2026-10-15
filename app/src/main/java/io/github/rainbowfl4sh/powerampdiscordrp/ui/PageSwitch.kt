package io.github.rainbowfl4sh.powerampdiscordrp.ui

import androidx.compose.foundation.pager.PagerState
import kotlin.math.abs

/**
 * Opens a tab's page: the page next door slides in, a page further away is shown at once. Sliding there would compose,
 * lay out and draw every page in between - each a screen full of settings - while the animation runs, which made
 * jumping across the tabs stutter.
 *
 * The pager composes only the visible pages (no beyondViewportPageCount): composing a neighbour in advance would only
 * move that work to the one after it when the swipe starts, and keep a page busy (live preview, flows) that is not seen.
 */
suspend fun PagerState.showPage(page: Int) {
    if (abs(page - currentPage) <= 1) animateScrollToPage(page) else scrollToPage(page)
}
