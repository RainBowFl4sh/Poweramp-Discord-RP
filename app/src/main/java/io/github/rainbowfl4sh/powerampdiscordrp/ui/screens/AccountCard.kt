package io.github.rainbowfl4sh.powerampdiscordrp.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkState
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkedAccount
import io.github.rainbowfl4sh.powerampdiscordrp.ui.AppModel
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.Note
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.SectionCard
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.AccountTexts
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.text
import io.github.rainbowfl4sh.powerampdiscordrp.ui.openUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Status tab: the Discord account linked through OAuth2 - its name and picture with Refresh / Unlink - or a hint and
 * "Link Discord account". [canLink]: there is an application ID to log in with.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountCard(model: AppModel, canLink: Boolean) {
    val account by model.account.collectAsStateWithLifecycle()
    val link by model.accountLink.collectAsStateWithLifecycle()
    val startLink = rememberAccountLinkStarter(model)
    val busy = link == LinkState.Waiting || link == LinkState.Working

    SectionCard(title = stringResource(R.string.conn_user)) {
        val linked = account
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountAvatar(model, linked, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (linked != null) {
                    Text(linked.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // the unique username under the display name, as Discord shows it in a profile
                    if (linked.displayName != linked.username) {
                        Text(
                            "@" + linked.username,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(stringResource(R.string.acc_not_linked), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.acc_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        when (val state = link) {
            LinkState.Waiting -> {
                Note(stringResource(R.string.acc_waiting))
                Note(AccountTexts.redirectHint.text())
            }
            LinkState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.acc_working), style = MaterialTheme.typography.bodyMedium)
            }
            is LinkState.Failed -> {
                val line = AccountTexts.failure(state.error)
                Note(line.text.text(), severity = line.severity)
                val detail = AccountTexts.detail(state)
                if (detail.isNotEmpty()) {
                    Text(
                        stringResource(R.string.conn_detail, detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LinkState.Idle -> Unit
        }
        if (!canLink && !busy) Note(stringResource(R.string.acc_no_app_id))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                // Discord's page may have been left without an answer: start a fresh login or stop waiting. Both are
                // off while Discord's page is just opening, so the second tap of a double tap on "Link" does nothing
                link == LinkState.Waiting -> {
                    OutlinedButton(onClick = startLink, enabled = canLink && !startLink.starting) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.acc_restart))
                    }
                    TextButton(onClick = model::cancelAccountLink, enabled = !startLink.starting) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
                link == LinkState.Working -> Unit
                linked == null -> FilledTonalButton(onClick = startLink, enabled = canLink) {
                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.acc_link))
                }
                else -> {
                    OutlinedButton(onClick = startLink, enabled = canLink) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.acc_refresh))
                    }
                    TextButton(onClick = model::unlinkAccount) { Text(stringResource(R.string.acc_unlink)) }
                }
            }
        }
        Text(
            stringResource(R.string.acc_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Starts linking the Discord account: a new login is saved, then Discord's authorization page opens - in the Discord
 * app, which handles discord.com links itself (already logged in), or else in the browser.
 *
 * Taps are ignored while a start is under way and for [SETTLE_MS] after the page was opened (the other app takes a
 * moment to cover the screen): a double tap opens one page, and its answer is the one the app waits for.
 */
@Stable
internal class AccountLinkStarter(
    private val model: AppModel,
    private val context: Context,
    private val scope: CoroutineScope,
) : () -> Unit {
    /** A start is under way or Discord's page is just opening. */
    var starting by mutableStateOf(false)
        private set

    override fun invoke() {
        if (starting) return
        starting = true
        scope.launch {
            try {
                val url = model.startAccountLink() ?: return@launch
                // no app opens the page: there is no answer to wait for
                if (!context.openUrl(url)) {
                    model.cancelAccountLink()
                    return@launch
                }
                delay(SETTLE_MS)
            } finally {
                starting = false
            }
        }
    }

    companion object {
        const val SETTLE_MS = 1_000L
    }
}

@Composable
internal fun rememberAccountLinkStarter(model: AppModel): AccountLinkStarter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(model, context, scope) { AccountLinkStarter(model, context, scope) }
}

/** The linked account's round picture (its initial while it loads or offline), a person icon without an account. */
@Composable
private fun AccountAvatar(model: AppModel, account: LinkedAccount?, size: Dp) {
    val url = account?.avatarUrl.orEmpty()
    // a new state per URL: after Refresh with a new picture, another account or Unlink the old picture is not kept
    val picture by key(model, url) {
        produceState<ImageBitmap?>(url.takeIf { it.isNotEmpty() }?.let(model::cachedPicture), model, url) {
            if (value == null && url.isNotEmpty()) value = model.loadPicture(url)
        }
    }
    val description = account?.let { stringResource(R.string.cd_account_avatar, it.displayName) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .semantics {
                if (description != null) {
                    contentDescription = description
                    role = Role.Image
                } else {
                    hideFromAccessibility()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val image = picture
        when {
            image != null -> Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            account != null -> Text(
                account.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                // the picture's description says who it is
                modifier = Modifier.semantics { hideFromAccessibility() },
            )
            else -> Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}
