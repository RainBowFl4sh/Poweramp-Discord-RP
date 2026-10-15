package io.github.rainbowfl4sh.powerampdiscordrp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.ui.Links
import io.github.rainbowfl4sh.powerampdiscordrp.ui.AppModel
import io.github.rainbowfl4sh.powerampdiscordrp.ui.PageColumn
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.AppLogo
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.DropdownField
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.LinkButton
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.Note
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SectionCard
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SettingTextField
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SwitchRow
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.rememberRemoteImageState
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.CoverOptions
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.Severity
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.StatusTexts
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.text
import io.github.rainbowfl4sh.powerampdiscordrp.ui.openUrl

/** Sizes offered for uploaded covers (Discord shows them at most at about 300 px). */
private val COVER_SIZES = listOf(256, 384, 512, 768, 1024)

/** Cover art tab: covers on / off, Poweramp's own cover and where it is uploaded, the current cover. */
@Composable
fun CoverPage(model: AppModel) {
    val c by model.config.collectAsStateWithLifecycle()
    val editor = model.editor

    PageColumn {
        SectionCard {
            SwitchRow(
                title = stringResource(R.string.cov_enable),
                checked = c.coverEnabled,
                onCheckedChange = { v -> editor.set { it.copy(coverEnabled = v) } },
            )
            Text(stringResource(R.string.cov_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard(title = stringResource(R.string.cov_local_title)) {
            val local = c.coverEnabled && c.localCovers
            SwitchRow(
                title = stringResource(R.string.cov_local),
                subtitle = stringResource(R.string.cov_local_sub),
                checked = c.localCovers,
                enabled = c.coverEnabled,
                onCheckedChange = { v -> editor.set { it.copy(localCovers = v) } },
            )
            DropdownField(
                label = stringResource(R.string.cov_upload),
                options = listOf(
                    Config.UPLOAD_X0 to stringResource(R.string.cov_upload_x0),
                    Config.UPLOAD_CATBOX to stringResource(R.string.cov_upload_catbox),
                    Config.UPLOAD_IMGUR to stringResource(R.string.cov_upload_imgur),
                    Config.UPLOAD_OFF to stringResource(R.string.cov_upload_off),
                ),
                selected = c.uploadHost,
                onSelect = { v -> editor.set { it.copy(uploadHost = v) } },
                enabled = local,
            )
            if (c.uploadHost == Config.UPLOAD_IMGUR) {
                SettingTextField(
                    value = c.imgurClientId,
                    onValueChange = { v -> editor.edit { it.copy(imgurClientId = v) } },
                    label = stringResource(R.string.cov_imgur_id),
                    supportingText = if (c.imgurClientId.isBlank()) stringResource(R.string.cov_imgur_missing) else null,
                    isError = local && c.imgurClientId.isBlank(),
                    enabled = local,
                )
                LinkButton(stringResource(R.string.cov_imgur_get), Links.IMGUR_ADD_CLIENT)
            }
            // without an upload host Poweramp's cover has no public link: every track is looked up online
            val uploaded = CoverOptions.usesPowerampCover(c)
            SwitchRow(
                title = stringResource(R.string.cov_prefer_local),
                subtitle = stringResource(R.string.cov_prefer_local_sub),
                checked = c.preferLocal,
                enabled = uploaded,
                onCheckedChange = { v -> editor.set { it.copy(preferLocal = v) } },
            )
            val sizes = (COVER_SIZES + c.coverMaxPx).distinct().sorted()
            DropdownField(
                label = stringResource(R.string.cov_size),
                options = sizes.map { it to stringResource(R.string.cov_size_px, it) },
                selected = c.coverMaxPx,
                onSelect = { v -> editor.set { it.copy(coverMaxPx = v) } },
                enabled = uploaded,
            )
            Note(stringResource(R.string.cov_public_note), severity = Severity.WARNING)
        }

        CurrentCoverSection(model, c)
    }
}

/** The cover of what plays: follows the engine (every track change, every update sent) without the settings above. */
@Composable
private fun CurrentCoverSection(model: AppModel, c: Config) {
    val status by model.status.collectAsStateWithLifecycle()
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.cov_current_title)) {
        val url = if (c.coverEnabled) status.coverUrl.trim() else ""
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                val picture = rememberRemoteImageState(url)
                val image = picture.image
                when {
                    image != null -> Image(
                        bitmap = image,
                        contentDescription = stringResource(R.string.cov_image_desc),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(112.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    picture.loading || status.coverSearching -> CircularProgressIndicator()
                    else -> AppLogo(112.dp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(StatusTexts.coverState(c, snapshot, status).text(), style = MaterialTheme.typography.bodyMedium)
                if (url.isNotEmpty()) {
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { context.openUrl(url) }) { Text(stringResource(R.string.cov_open)) }
                }
            }
        }
        OutlinedButton(onClick = model::clearCoverCache) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cov_clear))
        }
    }
}
