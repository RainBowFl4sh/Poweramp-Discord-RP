package io.github.rainbowfl4sh.powerampdiscordrp.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.ui.Links
import io.github.rainbowfl4sh.powerampdiscordrp.ui.AppModel
import io.github.rainbowfl4sh.powerampdiscordrp.ui.PageColumn
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.InfoRow
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.LinkButton
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.Note
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SectionCard
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SettingTextField
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SwitchRow
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.ExportKeys
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.SetupChecks
import io.github.rainbowfl4sh.powerampdiscordrp.ui.startSafely

/** Lines of the recent log shown on the page (copy / share hand over all of them). */
private const val LOG_LINES_SHOWN = 150

private const val EXPORT_FILE_NAME = "PowerampDiscordRP-settings.json"

/** Advanced tab: own Discord application, compatibility, recent log, settings file. */
@Composable
fun AdvancedPage(model: AppModel) {
    val c by model.config.collectAsStateWithLifecycle()
    val editor = model.editor
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    PageColumn {
        SectionCard(title = stringResource(R.string.adv_app_title)) {
            SwitchRow(
                title = stringResource(R.string.adv_custom_app),
                subtitle = stringResource(R.string.adv_custom_app_sub),
                checked = c.useCustomApp,
                onCheckedChange = { v -> editor.set { it.copy(useCustomApp = v) } },
            )
            if (c.useCustomApp) {
                val id = c.customClientId.trim()
                val valid = Config.isValidClientId(id)
                SettingTextField(
                    value = c.customClientId,
                    onValueChange = { v -> editor.edit { it.copy(customClientId = v) } },
                    label = stringResource(R.string.adv_app_id),
                    supportingText = stringResource(if (valid) R.string.adv_app_id_hint else R.string.adv_app_id_invalid),
                    isError = id.isNotEmpty() && !valid,
                    keyboardType = KeyboardType.Number,
                )
                LinkButton(stringResource(R.string.adv_portal), Links.DISCORD_PORTAL)
                Note(stringResource(R.string.adv_assets_note))
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val effective = c.effectiveClientId(model.defaultClientId)
            InfoRow(
                stringResource(R.string.adv_effective),
                when (SetupChecks.appIdSource(c, model.defaultClientId)) {
                    SetupChecks.AppIdSource.NONE -> stringResource(R.string.adv_effective_none)
                    SetupChecks.AppIdSource.OWN -> stringResource(R.string.conn_app_own, effective)
                    SetupChecks.AppIdSource.BUILT_IN -> stringResource(R.string.conn_app_builtin, effective)
                },
            )
        }

        SectionCard(title = stringResource(R.string.adv_compat_title)) {
            SwitchRow(
                title = stringResource(R.string.adv_seconds),
                subtitle = stringResource(R.string.adv_seconds_sub),
                checked = c.timestampsInSeconds,
                onCheckedChange = { v -> editor.set { it.copy(timestampsInSeconds = v) } },
            )
        }

        LogSection(model)
        SettingsFileSection(model, keys = ExportKeys.services(c), onReset = { confirmReset = true })
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.adv_reset_confirm_title)) },
            text = { Text(stringResource(R.string.adv_reset_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        model.resetToDefaults()
                    },
                ) { Text(stringResource(R.string.adv_reset_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogSection(model: AppModel) {
    val lines by model.logLines.collectAsStateWithLifecycle()
    val shown = remember(lines) { lines.takeLast(LOG_LINES_SHOWN) }
    val context = LocalContext.current
    // One Text per line in a lazy list: only the lines in view are composed and laid out - a new line no longer
    // measures the whole log (up to 150 lines) again. Newest entries at the bottom: opened there, and followed.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = shown.lastIndex.coerceAtLeast(0))
    LaunchedEffect(shown) {
        if (shown.isNotEmpty()) list.scrollToItem(shown.lastIndex)
    }
    SectionCard(title = stringResource(R.string.adv_log_title)) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small),
        ) {
            if (shown.isEmpty()) {
                Text(stringResource(R.string.adv_log_empty), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
            } else {
                SelectionContainer {
                    LazyColumn(state = list, contentPadding = PaddingValues(10.dp)) {
                        items(shown.size) { i ->
                            Text(shown[i], fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = lines.isNotEmpty(),
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.adv_log_title), model.logReport()))
                    // Android 13+ confirms copies itself
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, R.string.msg_log_copied, Toast.LENGTH_SHORT).show()
                    }
                },
            ) { Text(stringResource(R.string.adv_log_copy)) }
            OutlinedButton(
                enabled = lines.isNotEmpty(),
                onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.adv_log_share_subject))
                        .putExtra(Intent.EXTRA_TEXT, model.logReport())
                    context.startSafely(Intent.createChooser(send, context.getString(R.string.adv_log_share)))
                },
            ) { Text(stringResource(R.string.adv_log_share)) }
            TextButton(enabled = lines.isNotEmpty(), onClick = model::clearLog) { Text(stringResource(R.string.adv_log_clear)) }
        }
    }
}

/** [keys] = services whose keys the exported file will contain in plain text (see [ExportKeys]). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsFileSection(model: AppModel, keys: List<String>, onReset: () -> Unit) {
    var confirmExport by rememberSaveable { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) model.exportSettings(uri)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importSettings(uri)
    }
    SectionCard(title = stringResource(R.string.adv_settings_title)) {
        Text(
            stringResource(R.string.adv_settings_info),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // a file with working keys must not end up in a bug report or a shared folder unnoticed
            OutlinedButton(onClick = { if (keys.isEmpty()) export.launch(EXPORT_FILE_NAME) else confirmExport = true }) {
                Text(stringResource(R.string.adv_export))
            }
            // some file managers do not know .json and call it a binary file or plain text
            OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) {
                Text(stringResource(R.string.adv_import))
            }
            TextButton(
                onClick = onReset,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.adv_reset)) }
        }
    }

    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text(stringResource(R.string.adv_export_keys_title)) },
            text = { Text(stringResource(R.string.adv_export_keys_text, keys.joinToString(", "))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmExport = false
                        export.launch(EXPORT_FILE_NAME)
                    },
                ) { Text(stringResource(R.string.adv_export_keys_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmExport = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
