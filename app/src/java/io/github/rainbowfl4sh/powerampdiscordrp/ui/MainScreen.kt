package io.github.rainbowfl4sh.powerampdiscordrp.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.AppLogo
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.AboutPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.AdvancedPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.CoverPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.DisplayPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.SourcesPage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.screens.StatusPage
import kotlinx.coroutines.launch

/** The tabs, in the order of the AIMP plugin's settings page (General -> Status here). */
enum class Page(@StringRes val title: Int) {
    STATUS(R.string.tab_status),
    DISPLAY(R.string.tab_display),
    COVER(R.string.tab_cover),
    SOURCES(R.string.tab_sources),
    ADVANCED(R.string.tab_advanced),
    ABOUT(R.string.tab_about),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(model: AppModel) {
    val pages = Page.entries
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(model, resources) {
        model.messages.collect { snackbar.showSnackbar(it.resolve(resources)) }
    }
    // The user may have installed Discord, allowed notifications etc. in the meantime
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refreshEnvironment() }
    // Android 13+: without it the presence service's status notification stays invisible
    AskForNotificationsOnFirstStart(onAnswer = model::refreshEnvironment)

    val openPage: (Page) -> Unit = { page -> scope.launch { pager.showPage(page.ordinal) } }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { AppLogo(32.dp, Modifier.padding(start = 16.dp, end = 4.dp)) },
                    title = { Text(stringResource(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = { AppMenu(model) },
                )
                PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 8.dp) {
                    pages.forEach { page ->
                        Tab(
                            selected = pager.currentPage == page.ordinal,
                            onClick = { openPage(page) },
                            text = { Text(stringResource(page.title), maxLines = 1) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) { index ->
            when (pages[index]) {
                Page.STATUS -> StatusPage(model, openPage)
                Page.DISPLAY -> DisplayPage(model)
                Page.COVER -> CoverPage(model)
                Page.SOURCES -> SourcesPage(model, openPage)
                Page.ADVANCED -> AdvancedPage(model)
                Page.ABOUT -> AboutPage(model)
            }
        }
    }
}

/** Scrolling page content, limited in width on tablets / landscape. */
@Composable
fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}
