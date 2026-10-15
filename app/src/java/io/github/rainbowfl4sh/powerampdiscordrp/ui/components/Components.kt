package io.github.rainbowfl4sh.powerampdiscordrp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.FieldSync
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.NumberInput
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.Severity
import io.github.rainbowfl4sh.powerampdiscordrp.ui.openUrl
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.LocalStatusColors

/** A titled group of settings. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

/** A setting with a switch; the whole row toggles it. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val alpha = if (enabled) 1f else 0.38f
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A choice from a fixed list (read-only text field with a menu). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        if (value != selected) onSelect(value)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/**
 * Text field bound to a setting. Keeps its own cursor / selection; when the setting is changed elsewhere
 * (import, reset) the new text replaces what is shown - but not by an echo of the field's own typing ([FieldSync]).
 */
@Composable
fun SettingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val sync = remember { FieldSync<String>() }
    LaunchedEffect(value) {
        if (sync.changedElsewhere(value, field.text)) field = TextFieldValue(value, TextRange(value.length))
    }
    var revealed by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = field,
        onValueChange = {
            val changed = it.text != field.text
            field = it
            if (changed) {
                sync.sent(it.text)
                onValueChange(it.text)
            }
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret && !revealed) KeyboardType.Password else keyboardType,
            autoCorrectEnabled = false,
        ),
        visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) {
            {
                TextButton(onClick = { revealed = !revealed }, enabled = enabled) {
                    Text(stringResource(if (revealed) R.string.field_hide else R.string.field_show))
                }
            }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Whole numbers in [range]; invalid input is shown as an error and not saved, leaving the field restores the setting. */
@Composable
fun NumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    var text by remember { mutableStateOf(value.toString()) }
    val sync = remember { FieldSync<Int>() }
    LaunchedEffect(value) {
        if (sync.changedElsewhere(value, NumberInput.parse(text, range))) text = value.toString()
    }
    val valid = NumberInput.parse(text, range) != null
    OutlinedTextField(
        value = text,
        onValueChange = {
            val before = NumberInput.parse(text, range)
            text = NumberInput.filter(it)
            // compared with the field's own number: [value] may still be an older one
            NumberInput.parse(text, range)?.let { v ->
                if (v != before) {
                    sync.sent(v)
                    onValueChange(v)
                }
            }
        },
        label = { Text(label) },
        supportingText = when {
            !valid -> { { Text(stringResource(R.string.field_range, range.first, range.last)) } }
            supportingText != null -> { { Text(supportingText) } }
            else -> null
        },
        isError = !valid,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused && NumberInput.parse(text, range) == null) text = sync.latest(value).toString() },
    )
}

/** A whole number chosen with a slider in [range] (multiples of [step]); [valueText] is shown next to the label. */
@Composable
fun SliderRow(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    valueText: String,
    modifier: Modifier = Modifier,
    step: Int = 1,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        // snapped here instead of with Slider's steps: those draw a tick mark for every value
        Slider(
            value = value.coerceIn(range).toFloat(),
            onValueChange = { v ->
                val snapped = (Math.round((v - range.first) / step) * step + range.first).coerceIn(range)
                if (snapped != value) onValueChange(snapped)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
    }
}

/** Text button that opens a web page. */
@Composable
fun LinkButton(text: String, url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TextButton(onClick = { context.openUrl(url) }, modifier = modifier) { Text(text) }
}

/** Icon for a status line. */
@Composable
fun SeverityIcon(severity: Severity, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val colors = LocalStatusColors.current
    val (icon: ImageVector, tint: Color) = when (severity) {
        Severity.OK -> Icons.Filled.CheckCircle to colors.ok
        Severity.INFO -> Icons.Filled.Info to colors.info
        Severity.WARNING -> Icons.Filled.Warning to colors.warning
        Severity.ERROR -> Icons.Filled.Warning to colors.error
    }
    val description = stringResource(
        when (severity) {
            Severity.OK -> R.string.cd_ok
            Severity.INFO -> R.string.cd_info
            Severity.WARNING -> R.string.cd_warning
            Severity.ERROR -> R.string.cd_error
        },
    )
    Icon(icon, contentDescription = description, tint = tint, modifier = modifier.size(size))
}

/** A hint below settings (info or warning). */
@Composable
fun Note(text: String, modifier: Modifier = Modifier, severity: Severity = Severity.INFO) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        SeverityIcon(severity, Modifier.padding(top = 1.dp), size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (severity == Severity.WARNING || severity == Severity.ERROR) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A label / value pair (connection details); [trailing] (e.g. a small button) follows the value. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = if (trailing == null) Alignment.Top else Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(0.58f), verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}

/**
 * The app's logo, like Poweramp's raster icon: the launcher icon's layers without their black backdrop - the round dark
 * disc with its blue rim, whose glow fades into whatever is behind the logo (no black band, no dark ring on a light
 * surface). Heads the top bar and the About tab; stands in for a missing cover and, in the preview, for the Discord
 * application's "poweramp" picture (which is round as well).
 */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    // adaptive icon layers are 108 dp; the middle 68 dp (the disc, r 30.6, with its rim and the whole glow, which has
    // faded out at r 34) fill the logo
    val layer = size * (108f / 68f)
    val launcherBackground = ImageVector.vectorResource(R.drawable.ic_launcher_background)
    val disc = remember(launcherBackground) { launcherBackground.withoutPath(LAUNCHER_BACKDROP) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(rememberVectorPainter(disc), contentDescription = null, modifier = Modifier.requiredSize(layer))
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(layer))
    }
}

/** android:name of the launcher background's opaque black square, which the in-app logo leaves out. */
internal const val LAUNCHER_BACKDROP = "backdrop"

/** A copy of this vector without the paths called [name]. */
internal fun ImageVector.withoutPath(name: String): ImageVector {
    val copy = ImageVector.Builder(
        name = this.name,
        defaultWidth = defaultWidth,
        defaultHeight = defaultHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        tintColor = tintColor,
        tintBlendMode = tintBlendMode,
        autoMirror = autoMirror,
    )
    copy.addNodes(root, skip = name)
    return copy.build()
}

private fun ImageVector.Builder.addNodes(group: VectorGroup, skip: String) {
    for (node in group) {
        when (node) {
            is VectorPath -> if (node.name != skip) {
                addPath(
                    pathData = node.pathData,
                    pathFillType = node.pathFillType,
                    name = node.name,
                    fill = node.fill,
                    fillAlpha = node.fillAlpha,
                    stroke = node.stroke,
                    strokeAlpha = node.strokeAlpha,
                    strokeLineWidth = node.strokeLineWidth,
                    strokeLineCap = node.strokeLineCap,
                    strokeLineJoin = node.strokeLineJoin,
                    strokeLineMiter = node.strokeLineMiter,
                    trimPathStart = node.trimPathStart,
                    trimPathEnd = node.trimPathEnd,
                    trimPathOffset = node.trimPathOffset,
                )
            }
            is VectorGroup -> {
                addGroup(
                    name = node.name,
                    rotate = node.rotation,
                    pivotX = node.pivotX,
                    pivotY = node.pivotY,
                    scaleX = node.scaleX,
                    scaleY = node.scaleY,
                    translationX = node.translationX,
                    translationY = node.translationY,
                    clipPathData = node.clipPathData,
                )
                addNodes(node, skip)
                clearGroup()
            }
        }
    }
}
