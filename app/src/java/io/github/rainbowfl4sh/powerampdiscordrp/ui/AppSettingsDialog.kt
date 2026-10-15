package io.github.rainbowfl4sh.powerampdiscordrp.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SwitchRow
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
        AppSettingsDialog(appearance, onChange = model::setAppearance, onDismiss = { settingsOpen = false })
    }
}

/**
 * How the app looks: theme (system / light / dark), AMOLED black, dynamic color. Every change is saved and shown at
 * once, behind the open dialog.
 */
@Composable
fun AppSettingsDialog(
    appearance: Appearance,
    onChange: (Appearance) -> Unit,
    onDismiss: () -> Unit,
    systemDark: Boolean = isSystemInDarkTheme(),
    dynamicColorAvailable: Boolean = supportsDynamicColor(),
) {
    val dark = ResolvedTheme.of(appearance, systemDark).dark
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_settings_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.app_settings_theme),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
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
}

@StringRes
private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

/** A radio button with its label; the whole row selects it. */
@Composable
private fun ThemeOption(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
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
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}
