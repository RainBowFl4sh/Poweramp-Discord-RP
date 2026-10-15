package io.github.rainbowfl4sh.powerampdiscordrp.ui.screens

import android.content.res.Resources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainbowfl4sh.powerampdiscordrp.BuildConfig
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.UpdateChecker
import io.github.rainbowfl4sh.powerampdiscordrp.ui.AppModel
import io.github.rainbowfl4sh.powerampdiscordrp.ui.Links
import io.github.rainbowfl4sh.powerampdiscordrp.ui.PageColumn
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.AppLogo
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.DropdownField
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.LinkButton
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.Note
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SectionCard
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SeverityIcon
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SwitchRow
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.Changelog
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.ChangelogBlock
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.Formats
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.GitHubProfile
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.Severity
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.Span
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UpdateCheckState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UpdateView
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.text
import io.github.rainbowfl4sh.powerampdiscordrp.ui.openUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** About tab: version, author, links, update check, credits and the changelog. */
@Composable
fun AboutPage(model: AppModel) {
    PageColumn {
        HeaderSection(model)
        UpdateSection(model)
        CreditsSection()
        ChangelogSection()
    }
}

/** Logo, name and version, the author with their GitHub picture, the project's links - all centered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderSection(model: AppModel) {
    SectionCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo(72.dp)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            AuthorRow(model)
        }
        // every row centered, also when the buttons wrap ("Report a problem" under the other two on phones)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            LinkButton(stringResource(R.string.about_github), Links.REPO)
            LinkButton(stringResource(R.string.about_releases), Links.RELEASES)
            LinkButton(stringResource(R.string.about_issues), Links.ISSUES)
        }
        Text(
            stringResource(R.string.about_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The author: GitHub profile picture, name and account (the GitHub user of BuildConfig.GITHUB_REPO); opens the profile. */
@Composable
private fun AuthorRow(model: AppModel) {
    val context = LocalContext.current
    val user = GitHubProfile.user(BuildConfig.GITHUB_REPO)
    val name = stringResource(R.string.author_name)
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable { context.openUrl(GitHubProfile.profileUrl(user)) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuthorAvatar(model, GitHubProfile.avatarUrl(user), name, 64.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(R.string.about_by, name), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.about_on_github, user),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Round profile picture; the name's initial in a circle while it loads or without a connection. */
@Composable
private fun AuthorAvatar(model: AppModel, url: String, name: String, size: Dp) {
    val picture = rememberPicture(model, url)
    val description = stringResource(R.string.cd_author_avatar, name)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .semantics {
                contentDescription = description
                role = Role.Image
            },
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null) {
            Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Text(
                name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                // the picture's description says who it is
                modifier = Modifier.semantics { hideFromAccessibility() },
            )
        }
    }
}

/** The picture behind [url] from the model (cached, downloaded off the main thread); null while loading or offline. */
@Composable
private fun rememberPicture(model: AppModel, url: String): ImageBitmap? {
    val picture by produceState(model.cachedPicture(url), model, url) {
        if (value == null) value = model.loadPicture(url)
    }
    return picture
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpdateSection(model: AppModel) {
    val c by model.config.collectAsStateWithLifecycle()
    val state by model.update.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = UpdateView.of(state, c, BuildConfig.VERSION_NAME, Links.LATEST_RELEASE) { a, b -> UpdateChecker.isNewer(a, b) }

    SectionCard(title = stringResource(R.string.about_updates_title)) {
        SwitchRow(
            title = stringResource(R.string.about_update_check),
            checked = c.updateCheck,
            onCheckedChange = { v -> model.editor.set { it.copy(updateCheck = v) } },
        )
        DropdownField(
            label = stringResource(R.string.about_update_freq),
            options = listOf(
                Config.UPDATE_EVERY_START to stringResource(R.string.upd_every_start),
                Config.UPDATE_DAILY to stringResource(R.string.upd_daily),
                Config.UPDATE_WEEKLY to stringResource(R.string.upd_weekly),
                Config.UPDATE_MONTHLY to stringResource(R.string.upd_monthly),
            ),
            selected = c.updateFrequency.coerceIn(Config.UPDATE_EVERY_START, Config.UPDATE_MONTHLY),
            onSelect = { v -> model.editor.set { it.copy(updateFrequency = v) } },
            enabled = c.updateCheck,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            if (view.kind == UpdateView.Kind.CHECKING) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                SeverityIcon(
                    when (view.kind) {
                        UpdateView.Kind.AVAILABLE -> Severity.WARNING
                        UpdateView.Kind.FAILED -> UpdateView.failureLine(view.failure).severity
                        UpdateView.Kind.LATEST -> Severity.OK
                        else -> Severity.INFO
                    },
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                when (view.kind) {
                    UpdateView.Kind.NEVER -> stringResource(R.string.upd_never)
                    UpdateView.Kind.CHECKING -> stringResource(R.string.upd_checking)
                    UpdateView.Kind.LATEST -> stringResource(R.string.upd_latest, Formats.dateTime(view.lastCheckMs))
                    UpdateView.Kind.AVAILABLE -> stringResource(R.string.upd_available, view.version)
                    UpdateView.Kind.FAILED -> UpdateView.failureLine(view.failure).text.text()
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (view.kind == UpdateView.Kind.AVAILABLE) {
                Button(onClick = { context.openUrl(view.downloadUrl) }) {
                    Text(stringResource(R.string.upd_download, view.version))
                }
                TextButton(onClick = { context.openUrl(view.pageUrl) }) { Text(stringResource(R.string.upd_whats_new)) }
            }
            FilledTonalButton(
                onClick = { model.checkForUpdates(manual = true) },
                enabled = state != UpdateCheckState.Checking,
            ) { Text(stringResource(R.string.about_check_now)) }
        }
        Note(stringResource(R.string.about_update_privacy))
    }
}

@Composable
private fun CreditsSection() {
    SectionCard(title = stringResource(R.string.about_credits_title)) {
        Credit(stringResource(R.string.about_credit_poweramp), Links.POWERAMP_API)
        Credit(stringResource(R.string.about_credit_discord), Links.ANTISOCIALCORD)
        Credit(stringResource(R.string.about_credit_covers), null)
        Credit(stringResource(R.string.about_credit_aimp), null)
    }
}

@Composable
private fun Credit(text: String, url: String?) {
    val context = LocalContext.current
    val click = if (url == null) Modifier else Modifier.clickable { context.openUrl(url) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .then(click)
            .padding(vertical = 4.dp),
    ) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ChangelogSection() {
    val resources = LocalResources.current
    // read and parsed on IO: the changelog grows with every version, the tab must not wait for it
    val blocks by produceState(emptyList<ChangelogBlock>(), resources) {
        value = withContext(Dispatchers.IO) { Changelog.parse(readChangelog(resources)) }
    }
    SectionCard(title = stringResource(R.string.about_changelog_title)) {
        val note = stringResource(R.string.about_translation_note)
        if (note.isNotBlank()) Note(note)
        blocks.forEachIndexed { i, block ->
            when (block) {
                is ChangelogBlock.Version -> {
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.about_changelog_version, block.version), style = MaterialTheme.typography.titleMedium)
                        if (Changelog.headingIsVersion(block.version, BuildConfig.VERSION_NAME)) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.about_installed),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                is ChangelogBlock.Heading ->
                    Text(block.text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                is ChangelogBlock.Item -> Row(Modifier.padding(start = if (block.nested) 20.dp else 4.dp)) {
                    Text(if (block.nested) "–" else "•", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(annotated(block.spans), style = MaterialTheme.typography.bodyMedium)
                }
                is ChangelogBlock.Paragraph -> Text(annotated(block.spans), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** res/raw/changelog.md in the app's language (raw-de, raw-ru, raw-uk). */
private fun readChangelog(resources: Resources): String =
    resources.openRawResource(R.raw.changelog).bufferedReader(Charsets.UTF_8).use { it.readText() }

private fun annotated(spans: List<Span>): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        if (span.bold) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(span.text) } else append(span.text)
    }
}
