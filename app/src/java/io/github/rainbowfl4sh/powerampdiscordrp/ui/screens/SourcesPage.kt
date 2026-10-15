package io.github.rainbowfl4sh.powerampdiscordrp.ui.screens

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.ui.Links
import io.github.rainbowfl4sh.powerampdiscordrp.ui.AppModel
import io.github.rainbowfl4sh.powerampdiscordrp.ui.Page
import io.github.rainbowfl4sh.powerampdiscordrp.ui.PageColumn
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.LinkButton
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.Note
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SectionCard
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SettingTextField
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SwitchRow
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.CoverOptions
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.Severity

/** Sources tab: online cover lookups (public image links, nothing is uploaded), tried from top to bottom. */
@Composable
fun SourcesPage(model: AppModel, openPage: (Page) -> Unit) {
    val c by model.config.collectAsStateWithLifecycle()
    val editor = model.editor
    val on = c.coverEnabled

    PageColumn {
        SectionCard {
            Text(stringResource(R.string.src_info), style = MaterialTheme.typography.bodyMedium)
            if (!c.coverEnabled) {
                Note(stringResource(R.string.src_covers_off), severity = Severity.WARNING)
                TextButton(onClick = { openPage(Page.COVER) }) { Text(stringResource(R.string.src_open_cover_tab)) }
            } else if (CoverOptions.prefersPowerampCover(c)) {
                Note(stringResource(R.string.src_prefer_local_note))
            }
        }

        // the order in which the cover resolver asks them
        SectionCard(title = stringResource(R.string.src_list_title)) {
            SwitchRow(
                title = stringResource(R.string.src_spotify),
                subtitle = stringResource(R.string.src_spotify_sub),
                checked = c.srcSpotify,
                enabled = on,
                onCheckedChange = { v -> editor.set { it.copy(srcSpotify = v) } },
            )
            if (c.srcSpotify) {
                SettingTextField(
                    value = c.spotifyId,
                    onValueChange = { v -> editor.edit { it.copy(spotifyId = v) } },
                    label = stringResource(R.string.src_client_id),
                    enabled = on,
                )
                SettingTextField(
                    value = c.spotifySecret,
                    onValueChange = { v -> editor.edit { it.copy(spotifySecret = v) } },
                    label = stringResource(R.string.src_client_secret),
                    secret = true,
                    enabled = on,
                )
                if (c.spotifyId.isBlank() || c.spotifySecret.isBlank()) {
                    Note(stringResource(R.string.src_spotify_missing), severity = Severity.WARNING)
                }
                LinkButton(stringResource(R.string.src_create_app), Links.SPOTIFY_DASHBOARD)
            }
            HorizontalDivider()
            SwitchRow(
                title = stringResource(R.string.src_deezer),
                subtitle = stringResource(R.string.src_no_key),
                checked = c.srcDeezer,
                enabled = on,
                onCheckedChange = { v -> editor.set { it.copy(srcDeezer = v) } },
            )
            HorizontalDivider()
            SwitchRow(
                title = stringResource(R.string.src_itunes),
                subtitle = stringResource(R.string.src_no_key),
                checked = c.srcItunes,
                enabled = on,
                onCheckedChange = { v -> editor.set { it.copy(srcItunes = v) } },
            )
            HorizontalDivider()
            SwitchRow(
                title = stringResource(R.string.src_bandcamp),
                subtitle = stringResource(R.string.src_bandcamp_sub),
                checked = c.srcBandcamp,
                enabled = on,
                onCheckedChange = { v -> editor.set { it.copy(srcBandcamp = v) } },
            )
            HorizontalDivider()
            SwitchRow(
                title = stringResource(R.string.src_discogs),
                subtitle = stringResource(R.string.src_discogs_sub),
                checked = c.srcDiscogs,
                enabled = on,
                onCheckedChange = { v -> editor.set { it.copy(srcDiscogs = v) } },
            )
            if (c.srcDiscogs) {
                SettingTextField(
                    value = c.discogsToken,
                    onValueChange = { v -> editor.edit { it.copy(discogsToken = v) } },
                    label = stringResource(R.string.src_token),
                    secret = true,
                    enabled = on,
                )
                if (c.discogsToken.isBlank()) Note(stringResource(R.string.src_discogs_missing), severity = Severity.WARNING)
                LinkButton(stringResource(R.string.src_get_token), Links.DISCOGS_DEVELOPERS)
            }
            HorizontalDivider()
            SwitchRow(
                title = stringResource(R.string.src_musicbrainz),
                subtitle = stringResource(R.string.src_no_key),
                checked = c.srcMusicBrainz,
                enabled = on,
                onCheckedChange = { v -> editor.set { it.copy(srcMusicBrainz = v) } },
            )
        }
    }
}
