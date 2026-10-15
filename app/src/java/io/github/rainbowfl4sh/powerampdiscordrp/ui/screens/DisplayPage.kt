package io.github.rainbowfl4sh.powerampdiscordrp.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.TextRotation
import io.github.rainbowfl4sh.powerampdiscordrp.ui.AppModel
import io.github.rainbowfl4sh.powerampdiscordrp.ui.PageColumn
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.DropdownField
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.NumberField
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SectionCard
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SettingTextField
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SliderRow
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SwitchRow
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.ConfigEditor
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.FieldSync
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.Formats
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.Placeholders
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.TextEdit
import io.github.rainbowfl4sh.powerampdiscordrp.ui.preview.PresencePreview

/** The settings of the texts section (compared by value: typing elsewhere on the page does not recompose it). */
private data class TextSettings(
    val details: String,
    val state: String,
    val largeText: String,
    val smallText: String,
    val showSmallIcon: Boolean,
    val barLength: Int,
    val refreshSeconds: Int,
    val rotationSeconds: Int,
) {
    constructor(c: Config) : this(
        c.details, c.state, c.largeText, c.smallText, c.showSmallIcon, c.barLength, c.refreshSeconds,
        c.effectiveRotationSeconds(),
    )
}

/** The four text templates of the presence. */
private enum class Template(@StringRes val label: Int, val read: (TextSettings) -> String, val write: (Config, String) -> Config) {
    DETAILS(R.string.disp_details, { it.details }, { c, v -> c.copy(details = v) }),
    STATE(R.string.disp_state, { it.state }, { c, v -> c.copy(state = v) }),
    LARGE_TEXT(R.string.disp_large_text, { it.largeText }, { c, v -> c.copy(largeText = v) }),
    SMALL_TEXT(R.string.disp_small_text, { it.smallText }, { c, v -> c.copy(smallText = v) }),
}

/** Text, cursor and focus of the template fields; the placeholder chips (and "||") insert into the field used last. */
@Stable
private class TemplateFields(initial: TextSettings) {
    val values: Map<Template, MutableState<TextFieldValue>> = Template.entries.associateWith { t ->
        val text = t.read(initial)
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    val sync: Map<Template, FieldSync<String>> = Template.entries.associateWith { FieldSync() }
    val focus: Map<Template, FocusRequester> = Template.entries.associateWith { FocusRequester() }
    var target by mutableStateOf(Template.DETAILS)

    /** The user changed [t]'s text (typing, a placeholder chip): shown at once, saved through the editor. */
    fun edit(t: Template, value: TextFieldValue, editor: ConfigEditor) {
        val changed = value.text != values.getValue(t).value.text
        values.getValue(t).value = value
        if (changed) {
            sync.getValue(t).sent(value.text)
            editor.edit { t.write(it, value.text) }
        }
    }

    /** A chip was tapped: [insert] changes the text and selection of the field used last, which gets the focus back. */
    fun insertIntoTarget(editor: ConfigEditor, insert: (text: String, selectionStart: Int, selectionEnd: Int) -> TextEdit) {
        val t = target
        val current = values.getValue(t).value
        val inserted = insert(current.text, current.selection.start, current.selection.end)
        edit(t, TextFieldValue(inserted.text, TextRange(inserted.selectionStart, inserted.selectionEnd)), editor)
        focus.getValue(t).requestFocus()
    }
}

/**
 * Display tab: live preview, activity type, text templates, clickable title, paused behaviour and filters.
 * Every section gets only the settings it shows, so a keystroke recomposes the preview and the field's own section,
 * not the whole page; what plays (snapshot, status) is followed by the preview alone.
 */
@Composable
fun DisplayPage(model: AppModel) {
    val c by model.config.collectAsStateWithLifecycle()
    val editor = model.editor

    PageColumn {
        SectionCard(title = stringResource(R.string.disp_preview_title)) {
            LivePreview(model, c)
        }
        ActivitySection(c.activityType, c.statusDisplay, c.showTimestamps, editor)
        TextsSection(TextSettings(c), editor)
        TitleLinkSection(c.titleLink, c.titleLinkCustom, c.titleLinkUrl, editor)
        PausedSection(c.pausedBehavior, c.clearAfterPausedMin, editor)
        FiltersSection(c.hideStreams, c.excludePaths, editor)
    }
}

@Composable
private fun LivePreview(model: AppModel, config: Config) {
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    val status by model.status.collectAsStateWithLifecycle()
    PresencePreview(config, snapshot, status, model.appInfo, model.defaultClientId)
}

@Composable
private fun ActivitySection(activityType: Int, statusDisplay: Int, showTimestamps: Boolean, editor: ConfigEditor) {
    SectionCard(title = stringResource(R.string.disp_activity_title)) {
        DropdownField(
            label = stringResource(R.string.disp_type),
            options = listOf(
                Config.ACTIVITY_LISTENING to stringResource(R.string.disp_type_listening),
                Config.ACTIVITY_PLAYING to stringResource(R.string.disp_type_playing),
            ),
            selected = if (activityType == Config.ACTIVITY_PLAYING) Config.ACTIVITY_PLAYING else Config.ACTIVITY_LISTENING,
            onSelect = { v -> editor.set { it.copy(activityType = v) } },
        )
        DropdownField(
            label = stringResource(R.string.disp_status_display),
            options = listOf(
                Config.STATUS_DISPLAY_NAME to stringResource(R.string.disp_status_name),
                Config.STATUS_DISPLAY_STATE to stringResource(R.string.disp_status_state),
                Config.STATUS_DISPLAY_DETAILS to stringResource(R.string.disp_status_details),
            ),
            selected = statusDisplay.coerceIn(Config.STATUS_DISPLAY_NAME, Config.STATUS_DISPLAY_DETAILS),
            onSelect = { v -> editor.set { it.copy(statusDisplay = v) } },
        )
        SwitchRow(
            title = stringResource(R.string.disp_timestamps),
            checked = showTimestamps,
            onCheckedChange = { v -> editor.set { it.copy(showTimestamps = v) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextsSection(c: TextSettings, editor: ConfigEditor) {
    val fields = remember { TemplateFields(c) }
    // changed elsewhere (import, reset): show the new text - an echo of the field's own typing is not that
    Template.entries.forEach { t ->
        val stored = t.read(c)
        LaunchedEffect(t, stored) {
            val state = fields.values.getValue(t)
            if (fields.sync.getValue(t).changedElsewhere(stored, state.value.text)) {
                state.value = TextFieldValue(stored, TextRange(stored.length))
            }
        }
    }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.disp_texts_title)) {
        Template.entries.forEach { t ->
            val state = fields.values.getValue(t)
            OutlinedTextField(
                value = state.value,
                onValueChange = { v -> fields.edit(t, v, editor) },
                label = { Text(stringResource(t.label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fields.focus.getValue(t))
                    .onFocusChanged { if (it.isFocused) fields.target = t },
            )
        }

        Text(
            stringResource(R.string.disp_insert_into, stringResource(fields.target.label)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Placeholders.ALL.forEach { p ->
                AssistChip(
                    onClick = { fields.insertIntoTarget(editor) { text, start, end -> Placeholders.insert(text, start, end, p.token) } },
                    label = { Text(p.token, fontFamily = FontFamily.Monospace) },
                )
            }
            // the rotation separator: alternatives that take turns
            AssistChip(
                onClick = { fields.insertIntoTarget(editor, Placeholders::insertSeparator) },
                label = { Text(TextRotation.SEPARATOR, fontFamily = FontFamily.Monospace) },
            )
        }
        Text(
            stringResource(R.string.disp_rotation_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { showHelp = !showHelp }) {
            Text(stringResource(R.string.disp_placeholders_help))
            Spacer(Modifier.width(4.dp))
            Icon(if (showHelp) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        AnimatedVisibility(showHelp) {
            Column {
                Placeholders.ALL.forEach { p -> HelpRow(p.token, stringResource(p.description)) }
                HelpRow(TextRotation.SEPARATOR, stringResource(R.string.ph_rotation))
            }
        }

        SwitchRow(
            title = stringResource(R.string.disp_small_icon),
            checked = c.showSmallIcon,
            onCheckedChange = { v -> editor.set { it.copy(showSmallIcon = v) } },
        )
        SliderRow(
            label = stringResource(R.string.disp_bar_length),
            value = c.barLength,
            range = 4..30,
            onValueChange = { v -> editor.edit { it.copy(barLength = v) } },
            valueText = stringResource(R.string.disp_bar_length_value, c.barLength),
        )
        SliderRow(
            label = stringResource(R.string.disp_refresh),
            value = c.refreshSeconds,
            range = 5..120,
            step = 5,
            onValueChange = { v -> editor.edit { it.copy(refreshSeconds = v) } },
            valueText = stringResource(R.string.disp_refresh_value, c.refreshSeconds),
        )
        Text(
            stringResource(R.string.disp_refresh_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SliderRow(
            label = stringResource(R.string.disp_rotation),
            value = c.rotationSeconds,
            range = Config.ROTATION_MIN_SECONDS..Config.ROTATION_MAX_SECONDS,
            onValueChange = { v -> editor.edit { it.copy(rotationSeconds = v) } },
            valueText = stringResource(R.string.disp_rotation_value, c.rotationSeconds),
        )
        Text(
            stringResource(R.string.disp_rotation_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One line of the placeholder help: the token and what it stands for. */
@Composable
private fun HelpRow(token: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            token,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.widthIn(min = 112.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TitleLinkSection(titleLink: Boolean, titleLinkCustom: Boolean, titleLinkUrl: String, editor: ConfigEditor) {
    SectionCard(title = stringResource(R.string.disp_link_title)) {
        SwitchRow(
            title = stringResource(R.string.disp_title_link),
            checked = titleLink,
            onCheckedChange = { v -> editor.set { it.copy(titleLink = v) } },
        )
        SwitchRow(
            title = stringResource(R.string.disp_own_link),
            checked = titleLinkCustom,
            enabled = titleLink,
            onCheckedChange = { v -> editor.set { it.copy(titleLinkCustom = v) } },
        )
        if (titleLink && titleLinkCustom) {
            val valid = Formats.isHttpUrl(titleLinkUrl)
            SettingTextField(
                value = titleLinkUrl,
                onValueChange = { v -> editor.edit { it.copy(titleLinkUrl = v) } },
                label = stringResource(R.string.disp_own_link_url),
                supportingText = stringResource(if (valid) R.string.disp_own_link_hint else R.string.disp_link_invalid),
                isError = !valid,
                keyboardType = KeyboardType.Uri,
            )
        }
    }
}

@Composable
private fun PausedSection(pausedBehavior: Int, clearAfterPausedMin: Int, editor: ConfigEditor) {
    SectionCard(title = stringResource(R.string.disp_paused_title)) {
        DropdownField(
            label = stringResource(R.string.disp_paused),
            options = listOf(
                Config.PAUSED_SHOW to stringResource(R.string.disp_paused_show),
                Config.PAUSED_HIDE to stringResource(R.string.disp_paused_hide),
            ),
            selected = if (pausedBehavior == Config.PAUSED_SHOW) Config.PAUSED_SHOW else Config.PAUSED_HIDE,
            onSelect = { v -> editor.set { it.copy(pausedBehavior = v) } },
        )
        NumberField(
            value = clearAfterPausedMin,
            onValueChange = { v -> editor.edit { it.copy(clearAfterPausedMin = v) } },
            range = 0..1440,
            label = stringResource(R.string.disp_clear_after),
            supportingText = stringResource(R.string.disp_clear_after_hint),
            enabled = pausedBehavior == Config.PAUSED_SHOW,
        )
    }
}

@Composable
private fun FiltersSection(hideStreams: Boolean, excludePaths: String, editor: ConfigEditor) {
    SectionCard(title = stringResource(R.string.disp_filters_title)) {
        SwitchRow(
            title = stringResource(R.string.disp_hide_streams),
            checked = hideStreams,
            onCheckedChange = { v -> editor.set { it.copy(hideStreams = v) } },
        )
        SettingTextField(
            value = excludePaths,
            onValueChange = { v -> editor.edit { it.copy(excludePaths = v) } },
            label = stringResource(R.string.disp_exclude_paths),
            placeholder = stringResource(R.string.disp_exclude_paths_hint),
            supportingText = stringResource(R.string.disp_exclude_paths_note),
        )
    }
}
