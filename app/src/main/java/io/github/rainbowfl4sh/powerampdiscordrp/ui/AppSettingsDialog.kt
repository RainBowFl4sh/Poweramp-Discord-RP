package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SwitchRow
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.AppLanguage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.AppLanguages
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.Appearance
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.ResolvedTheme
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.ThemeMode
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.supportsDynamicColor

/** The top bar's three-dot menu: "App settings" opens [AppSettingsDialog]. */
@Composable
fun AppMenu(model: AppModel) {
    var expanded by remember { mutableStateOf(false) }
    // kept when the activity is recreated (e.g. rotation) while the dialog is open
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_settings_title)) },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = {
                    expanded = false
                    settingsOpen = true
                },
            )
        }
    }
    if (settingsOpen) {
        val appearance by model.appearance.collectAsStateWithLifecycle()
        val language by model.language.collectAsStateWithLifecycle()
        val activity = LocalActivity.current
        AppSettingsDialog(
            appearance,
            onChange = model::setAppearance,
            onDismiss = { settingsOpen = false },
            language = language,
            // the dialog stays open in the recreated activity (settingsOpen is saved)
            onLanguageChange = { if (model.setLanguage(it)) activity?.recreate() },
        )
    }
}

/**
 * How the app looks: language, theme (system / light / dark), AMOLED black, dynamic color. Every change is saved and
 * shown at once, behind the open dialog. [systemLanguageSetting]: Android 13+ also has the app's language in its settings.
 */
@Composable
fun AppSettingsDialog(
    appearance: Appearance,
    onChange: (Appearance) -> Unit,
    onDismiss: () -> Unit,
    language: AppLanguage?,
    onLanguageChange: (AppLanguage?) -> Unit,
    systemDark: Boolean = isSystemInDarkTheme(),
    dynamicColorAvailable: Boolean = supportsDynamicColor(),
    systemLanguageSetting: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
) {
    val dark = ResolvedTheme.of(appearance, systemDark).dark
    var languagesOpen by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_settings_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionTitle(R.string.app_settings_language)
                LanguageRow(language, systemLanguageSetting) { languagesOpen = true }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle(R.string.app_settings_theme)
                Spacer(Modifier.height(4.dp))
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        ThemeOption(
                            label = themeLabel(mode),
                            selected = appearance.theme == mode,
                            onClick = { if (appearance.theme != mode) onChange(appearance.copy(theme = mode)) },
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                // only meaningful while the dark theme is in effect: shown (with its value) but disabled otherwise
                SwitchRow(
                    title = stringResource(R.string.app_settings_amoled),
                    subtitle = stringResource(if (dark) R.string.app_settings_amoled_on else R.string.app_settings_amoled_needs_dark),
                    checked = appearance.amoled,
                    enabled = dark,
                    onCheckedChange = { onChange(appearance.copy(amoled = it)) },
                )
                if (dynamicColorAvailable) {
                    SwitchRow(
                        title = stringResource(R.string.app_settings_dynamic),
                        subtitle = stringResource(R.string.app_settings_dynamic_sub),
                        checked = appearance.dynamicColor,
                        onCheckedChange = { onChange(appearance.copy(dynamicColor = it)) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
    // after the settings dialog: its window opens on top of it
    if (languagesOpen) {
        LanguageDialog(
            selected = language,
            onSelect = {
                languagesOpen = false
                if (it != language) onLanguageChange(it)
            },
            onDismiss = { languagesOpen = false },
        )
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** The chosen language (its own name) - tapped, the list of languages opens. */
@Composable
private fun LanguageRow(language: AppLanguage?, systemLanguageSetting: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(language?.autonym ?: stringResource(R.string.language_system), style = MaterialTheme.typography.bodyLarge)
        if (systemLanguageSetting) {
            Text(
                stringResource(R.string.app_settings_language_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "System default" and every language by its own name, in the order of [AppLanguages.ALL]. */
@Composable
private fun LanguageDialog(selected: AppLanguage?, onSelect: (AppLanguage?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_settings_language)) },
        text = {
            LazyColumn(Modifier.selectableGroup()) {
                item {
                    ChoiceRow(stringResource(R.string.language_system), null, selected == null) { onSelect(null) }
                }
                items(AppLanguages.ALL, key = { it.tag }) { language ->
                    ChoiceRow(
                        label = language.autonym,
                        note = if (language.aiTranslation) stringResource(R.string.language_ai) else null,
                        selected = language == selected,
                        // the name in its own language: right script, direction and line breaks
                        localeTag = language.tag,
                    ) { onSelect(language) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@StringRes
private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

/** A radio button with its label; the whole row selects it. */
@Composable
private fun ThemeOption(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) =
    ChoiceRow(stringResource(label), null, selected, onClick = onClick)

/** A radio button with its label and an optional smaller [note]; the whole row selects it. */
@Composable
private fun ChoiceRow(label: String, note: String?, selected: Boolean, localeTag: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            val style = MaterialTheme.typography.bodyLarge
            Text(
                label,
                style = if (localeTag == null) style else style.copy(localeList = LocaleList(localeTag), textDirection = TextDirection.Content),
            )
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
