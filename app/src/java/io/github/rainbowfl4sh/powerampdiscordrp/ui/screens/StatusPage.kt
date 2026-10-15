package io.github.rainbowfl4sh.powerampdiscordrp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlayState
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.settings.EnvState
import io.github.rainbowfl4sh.powerampdiscordrp.settings.EnvironmentCheck
import io.github.rainbowfl4sh.powerampdiscordrp.ui.Links
import io.github.rainbowfl4sh.powerampdiscordrp.ui.AppModel
import io.github.rainbowfl4sh.powerampdiscordrp.ui.Page
import io.github.rainbowfl4sh.powerampdiscordrp.ui.PageColumn
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.InfoRow
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.Note
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SectionCard
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SeverityIcon
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.CheckAction
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.CheckItem
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.Formats
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.SetupChecks
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.Severity
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.StatusLine
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.StatusTexts
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.text
import io.github.rainbowfl4sh.powerampdiscordrp.ui.openStorePage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.rememberNotificationRequest
import io.github.rainbowfl4sh.powerampdiscordrp.ui.startSafely

/** Status tab: master switch, connection, what plays, setup checklist and hints. */
@Composable
fun StatusPage(model: AppModel, openPage: (Page) -> Unit) {
    val config by model.config.collectAsStateWithLifecycle()
    val env by model.env.collectAsStateWithLifecycle()
    val status by model.status.collectAsStateWithLifecycle()
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    // the texts change with every update of the engine (the position moves on), the page only shows why nothing is shown
    val texts = model.texts.collectAsStateWithLifecycle()
    val hidden by remember(texts) { derivedStateOf { texts.value.hidden } }
    val serviceRunning by model.serviceRunning.collectAsStateWithLifecycle()
    val stoppedByUser by model.stoppedByUser.collectAsStateWithLifecycle()

    val defaultClientId = model.defaultClientId
    val checks = remember(env, config, defaultClientId) { env?.let { SetupChecks.items(it, config, defaultClientId) } }
    val problems = checks?.any { it.severity == Severity.ERROR || it.severity == Severity.WARNING } == true
    val nowPlaying = remember(hidden, status, stoppedByUser) { StatusTexts.nowPlaying(hidden, status, stoppedByUser) }

    PageColumn {
        MasterSwitch(config.enabled, model::setEnabled)
        // something to fix: the checklist comes first, otherwise the connection does
        if (problems) Checklist(checks, model, openPage)
        ConnectionCard(status, config, defaultClientId, serviceRunning, model)
        NowPlayingCard(snapshot, nowPlaying, env)
        if (!problems) Checklist(checks, model, openPage)
        HintsCard()
    }
}

@Composable
private fun MasterSwitch(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // the whole card is the switch (one control for accessibility services)
        Row(
            Modifier
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.master_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(if (enabled) R.string.master_on else R.string.master_off),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionCard(
    status: EngineStatus,
    config: Config,
    defaultClientId: String,
    serviceRunning: Boolean,
    model: AppModel,
) {
    val shown = StatusTexts.connectionStatus(
        status, config, defaultClientId,
        // a running service tries the ID itself as soon as there is something to show
        appIdRejected = !serviceRunning && model.isAppIdRejected(config.effectiveClientId(defaultClientId)),
    )
    val line = StatusTexts.connection(shown)
    SectionCard(title = stringResource(R.string.conn_title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (shown.kind == EngineStatus.Kind.CONNECTING) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                SeverityIcon(line.severity)
            }
            Spacer(Modifier.width(12.dp))
            Text(line.text.text(), style = MaterialTheme.typography.titleMedium)
        }
        val detail = StatusTexts.errorDetail(shown)
        if (detail.isNotEmpty()) {
            Text(
                stringResource(R.string.conn_detail, detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val rejected = StatusTexts.activityError(status)
        if (rejected.isNotEmpty()) Note(stringResource(R.string.conn_activity_error, rejected), severity = Severity.WARNING)
        StatusTexts.testNote(status)?.let { Note(it.text()) }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        val none = stringResource(R.string.conn_none)
        val user = StatusTexts.userName(status.user)
        if (user.isNotEmpty()) InfoRow(stringResource(R.string.conn_user), user)
        val appId = status.clientId.ifBlank { config.effectiveClientId(defaultClientId) }
        val own = config.useCustomApp && appId.isNotBlank() && appId == config.customClientId.trim()
        InfoRow(
            stringResource(R.string.conn_app_id),
            when {
                appId.isBlank() -> none
                own -> stringResource(R.string.conn_app_own, appId)
                else -> stringResource(R.string.conn_app_builtin, appId)
            },
        )
        // formatted once per update sent (a localized formatter is built for it), not on every change of the status
        val lastSent = remember(status.lastSentWallMs) { if (status.lastSentWallMs > 0) Formats.clock(status.lastSentWallMs) else null }
        InfoRow(stringResource(R.string.conn_last_update), lastSent ?: stringResource(R.string.conn_never))
        InfoRow(stringResource(R.string.conn_connections), status.connects.toString())
        InfoRow(
            stringResource(R.string.conn_service),
            stringResource(if (serviceRunning) R.string.conn_service_running else R.string.conn_service_stopped),
        ) {
            TextButton(onClick = { if (serviceRunning) model.stopService() else model.startService(quiet = false) }) {
                Text(stringResource(if (serviceRunning) R.string.conn_service_stop else R.string.conn_service_start))
            }
        }
        // without an application ID there is nothing to connect with (the checklist leads to the Advanced tab)
        val canConnect = config.enabled && SetupChecks.hasUsableAppId(config, defaultClientId)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalButton(onClick = model::sendTest, enabled = canConnect) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.btn_test))
            }
            OutlinedButton(onClick = model::reconnect, enabled = canConnect) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.btn_reconnect))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NowPlayingCard(snapshot: PlaybackSnapshot, shown: StatusLine, env: EnvState?) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.now_title)) {
        val track = snapshot.track
        if (snapshot.state == PlayState.STOPPED || track.isEmpty) {
            Text(stringResource(R.string.now_nothing), style = MaterialTheme.typography.bodyLarge)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title.ifBlank { track.path.substringAfterLast('/') },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" – ")
                    if (sub.isNotEmpty()) {
                        Text(sub, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                // a label, not a button
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (snapshot.state == PlayState.PLAYING) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            stringResource(if (snapshot.state == PlayState.PLAYING) R.string.word_playing else R.string.word_paused),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            SeverityIcon(shown.severity, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(shown.text.text(), style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(
                onClick = { context.startSafely(EnvironmentCheck.launchPoweramp(context)) },
                enabled = env == null || env.powerampVersion != null,
            ) {
                Text(stringResource(R.string.btn_open_poweramp))
            }
            OutlinedButton(
                onClick = { context.startSafely(EnvironmentCheck.launchDiscord(context)) },
                enabled = env == null || env.discordInstalled,
            ) {
                Text(stringResource(R.string.btn_open_discord))
            }
        }
    }
}

@Composable
private fun Checklist(checks: List<CheckItem>?, model: AppModel, openPage: (Page) -> Unit) {
    val context = LocalContext.current
    val requestNotifications = rememberNotificationRequest(model::refreshEnvironment)
    val onAction: (CheckAction) -> Unit = { action ->
        when (action) {
            CheckAction.GET_POWERAMP, CheckAction.UPDATE_POWERAMP -> context.openStorePage(EnvironmentCheck.POWERAMP_PACKAGE)
            CheckAction.GET_DISCORD, CheckAction.UPDATE_DISCORD ->
                context.openStorePage(EnvironmentCheck.DISCORD_PACKAGES.firstOrNull() ?: Links.DISCORD_PACKAGE)
            CheckAction.SET_APP_ID -> openPage(Page.ADVANCED)
            CheckAction.BATTERY -> context.startSafely(EnvironmentCheck.batteryOptimizationIntent(context))
            CheckAction.NOTIFICATIONS -> requestNotifications()
        }
    }
    SectionCard(title = stringResource(R.string.check_title)) {
        if (checks == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.check_checking))
            }
        } else {
            checks.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 2.dp))
                ChecklistRow(item, onAction)
            }
        }
    }
}

@Composable
private fun ChecklistRow(item: CheckItem, onAction: (CheckAction) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        SeverityIcon(item.severity, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(item.title), style = MaterialTheme.typography.titleSmall)
            Text(item.detail.text(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val action = item.action
            if (action != null) {
                FilledTonalButton(onClick = { onAction(action) }, modifier = Modifier.padding(top = 6.dp)) {
                    Text(stringResource(item.actionLabel))
                }
            }
        }
    }
}

@Composable
private fun HintsCard() {
    SectionCard(title = stringResource(R.string.hints_title)) {
        Hint(R.string.hint_privacy_title, R.string.hint_privacy)
        Hint(R.string.hint_poweramp_title, R.string.hint_poweramp)
    }
}

@Composable
private fun Hint(title: Int, text: Int) {
    Row(verticalAlignment = Alignment.Top) {
        SeverityIcon(Severity.INFO, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
