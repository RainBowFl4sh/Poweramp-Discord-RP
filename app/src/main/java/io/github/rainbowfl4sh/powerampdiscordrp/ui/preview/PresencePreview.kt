package io.github.rainbowfl4sh.powerampdiscordrp.ui.preview

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.assets.AppInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.assets.AppInfoSource
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.PlaybackSnapshot
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.ActivityBuilder
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.StatusStrings
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.DiscordUser
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.AppLogo
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.rememberRemoteImage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.Formats
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.StatusTexts
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.DiscordColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * What Discord will show for the settings being edited: the activity card of the profile and the member list entry,
 * drawn in Discord's dark colors. Follows the edits at once ([config] is the editor's draft) and the playback [clock].
 */
@Composable
fun PresencePreview(
    config: Config,
    snapshot: PlaybackSnapshot,
    status: EngineStatus,
    appInfo: AppInfoSource,
    defaultClientId: String,
    modifier: Modifier = Modifier,
    clock: State<Long> = rememberPreviewClock(),
) {
    val example = ExampleTexts(
        title = stringResource(R.string.prev_example_title),
        artist = stringResource(R.string.prev_example_artist),
        album = stringResource(R.string.prev_example_album),
    )
    val strings = StatusStrings(playing = stringResource(R.string.word_playing), paused = stringResource(R.string.word_paused))
    // Built again with every tick of the clock (cheap), but a tick only reaches what it changes: the card is drawn again
    // when its texts change (%pos% / %bar% / %percent%, the paused timeout), the progress line follows the position
    val built = remember(config, snapshot, example, strings, clock) {
        derivedStateOf { PreviewModel.build(config, snapshot, clock.value, example, strings) }
    }
    val still = remember(built) { derivedStateOf { PreviewModel.withoutPosition(built.value) } }
    val position = remember(built) { derivedStateOf { built.value.texts.posMs } }
    val positionMs = remember(position) { { position.value } }
    val preview = still.value

    // the application's name and pictures exactly as Discord has them (Developer Portal), looked up once and cached
    val clientId = config.effectiveClientId(defaultClientId)
    val info by produceState<AppInfo?>(appInfo.peek(clientId), clientId, appInfo) {
        value = withContext(Dispatchers.IO) { appInfo.fetch(clientId) } ?: value
    }
    val ownApp = config.useCustomApp && Config.isValidClientId(config.customClientId.trim())
    val appName = info?.name?.takeIf { it.isNotBlank() }
        ?: if (ownApp) stringResource(R.string.prev_app_own) else ActivityBuilder.ACTIVITY_NAME
    val coverUrl = if (!preview.example && config.coverEnabled) status.coverUrl else ""
    PresencePreviewContent(preview, appName, coverUrl, status.user, modifier, info?.images.orEmpty(), positionMs)
}

/**
 * The live preview's clock (elapsedRealtime, like the snapshot's stamps): moves on every second while the screen is
 * visible - the progress line moves on, a paused timeout expires - and stands still while the app is in the background.
 */
@Composable
fun rememberPreviewClock(): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(SystemClock.elapsedRealtime(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = SystemClock.elapsedRealtime()
                delay(1_000)
            }
        }
    }
}

/** Tests: told the name of each part of the preview when it is composed (what a tick of the clock recomposes). */
internal val LocalPreviewTrace = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * Draws a computed [Preview]: the profile's activity card, the member list entry and short notes below.
 * [positionMs]: the playing position for the progress line (read only there, so its ticking redraws nothing else).
 */
@Composable
fun PresencePreviewContent(
    preview: Preview,
    appName: String,
    coverUrl: String,
    user: DiscordUser?,
    modifier: Modifier = Modifier,
    images: Map<String, String> = emptyMap(),   // art asset key -> picture URL (AppInfo.images)
    positionMs: () -> Long = { preview.texts.posMs },
) {
    val headerFormat = if (preview.listening) R.string.prev_listening else R.string.prev_playing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DiscordColors.Panel)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActivityCard(preview, stringResource(headerFormat, appName), coverUrl, images, positionMs)
        MemberRow(
            user = user,
            line = if (preview.hidden) "" else stringResource(headerFormat, preview.memberText.ifEmpty { appName }),
        )
        if (!preview.hidden) {
            val t = preview.texts
            if (preview.smallIcon && t.smallText.isNotEmpty()) Note(stringResource(R.string.prev_icon_tip, t.smallText))
            Note(
                if (t.detailsUrl.isNotEmpty()) stringResource(R.string.prev_link, Formats.shortUrl(t.detailsUrl))
                else stringResource(R.string.prev_no_link),
            )
        }
        if (preview.example) Note(stringResource(R.string.prev_example), DiscordColors.Orange)
    }
}

@Composable
private fun ActivityCard(preview: Preview, header: String, coverUrl: String, images: Map<String, String>, positionMs: () -> Long) {
    LocalPreviewTrace.current("card")
    val t = preview.texts
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DiscordColors.Card)
            .padding(12.dp),
    ) {
        if (preview.hidden) {
            // Discord shows nothing: say why instead
            Text(stringResource(StatusTexts.hidden(t.hidden)), color = DiscordColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        } else {
            ActivityContent(preview, header, coverUrl, images, positionMs)
        }
    }
}

@Composable
private fun ActivityContent(preview: Preview, header: String, coverUrl: String, images: Map<String, String>, positionMs: () -> Long) {
    val t = preview.texts
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Line(header, DiscordColors.Text, 13.sp, FontWeight.SemiBold, Modifier.weight(1f))
            Text("•••", color = DiscordColors.Text, fontSize = 12.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp)) {
                Cover(coverUrl, images[ActivityBuilder.ASSET_LOGO].orEmpty(), 76.dp)
                if (preview.smallIcon) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(DiscordColors.Card),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayPauseBadge(
                            t.playing,
                            images[if (t.playing) ActivityBuilder.ASSET_PLAY else ActivityBuilder.ASSET_PAUSE].orEmpty(),
                            24.dp,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (t.details.isNotEmpty()) Line(t.details, DiscordColors.White, 15.sp, FontWeight.SemiBold)
                if (t.state.isNotEmpty()) Line(t.state, DiscordColors.Text, 13.sp)
                if (t.largeText.isNotEmpty()) Line(t.largeText, DiscordColors.Text, 13.sp)
                if (preview.progress) {
                    Spacer(Modifier.height(4.dp))
                    ProgressLine(positionMs, t.durMs)
                } else if (preview.elapsed) {
                    ElapsedLine(positionMs)
                }
            }
        }
    }
}

@Composable
private fun Cover(url: String, logoUrl: String, size: Dp) {
    // without a cover Discord shows the application's "poweramp" picture (both loaded unconditionally: composables)
    val cover = rememberRemoteImage(url)
    val logo = rememberRemoteImage(logoUrl)
    val image = cover ?: logo
    val description = stringResource(R.string.cov_image_desc)
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        // the application's picture is not known (yet): the app's own logo
        AppLogo(size)
    }
}

/** Position, bar and length: the position is read by its text and while drawing the bar only (it changes every second). */
@Composable
private fun ProgressLine(positionMs: () -> Long, durMs: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PositionText(positionMs)
        Spacer(Modifier.width(8.dp))
        Canvas(
            Modifier
                .weight(1f)
                .height(4.dp),
        ) {
            val fraction = PreviewModel.fraction(positionMs(), durMs)
            val r = CornerRadius(size.height / 2)
            drawRoundRect(DiscordColors.Track, cornerRadius = r)
            if (fraction > 0f) {
                drawRoundRect(DiscordColors.White, size = Size(maxOf(size.height, size.width * fraction), size.height), cornerRadius = r)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(Formats.discordTime(durMs), color = DiscordColors.Text, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun PositionText(positionMs: () -> Long) {
    LocalPreviewTrace.current("position")
    Text(Formats.discordTime(positionMs()), color = DiscordColors.Text, fontSize = 12.sp, maxLines = 1)
}

/** "01:23 elapsed" (Playing, or the length is unknown). */
@Composable
private fun ElapsedLine(positionMs: () -> Long) {
    LocalPreviewTrace.current("position")
    Line(stringResource(R.string.prev_elapsed, Formats.discordTime(positionMs())), DiscordColors.Text, 12.sp)
}

/**
 * The small image: the application's "play" / "pause" picture; while it is not known (yet) a plain dark circle with
 * a white play triangle / pause bars.
 */
@Composable
private fun PlayPauseBadge(playing: Boolean, url: String, size: Dp) {
    val description = stringResource(if (playing) R.string.word_playing else R.string.word_paused)
    val picture = rememberRemoteImage(url)
    if (picture != null) {
        Image(
            bitmap = picture,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
        return
    }
    Canvas(
        Modifier
            .size(size)
            .semantics { contentDescription = description },
    ) {
        val r = this.size.minDimension / 2
        val c = center
        drawCircle(Color.Black, radius = r, center = c)
        if (playing) {
            val path = Path().apply {
                moveTo(c.x - r * 0.3f, c.y - r * 0.45f)
                lineTo(c.x - r * 0.3f, c.y + r * 0.45f)
                lineTo(c.x + r * 0.5f, c.y)
                close()
            }
            drawPath(path, Color.White)
        } else {
            val w = r * 0.28f
            val h = r * 0.84f
            val corner = CornerRadius(r * 0.06f)
            drawRoundRect(Color.White, topLeft = Offset(c.x - r * 0.42f, c.y - r * 0.42f), size = Size(w, h), cornerRadius = corner)
            drawRoundRect(Color.White, topLeft = Offset(c.x + r * 0.14f, c.y - r * 0.42f), size = Size(w, h), cornerRadius = corner)
        }
    }
}

/** Member list entry: avatar with the online dot, name and "Listening to ...". */
@Composable
private fun MemberRow(user: DiscordUser?, line: String) {
    val name = StatusTexts.userName(user).ifEmpty { stringResource(R.string.prev_you) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DiscordColors.Row)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp)) {
            Avatar(user, name)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(DiscordColors.Row),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(DiscordColors.Green),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Line(name, DiscordColors.White, 15.sp, FontWeight.SemiBold)
            if (line.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("♫", color = DiscordColors.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Line(line, DiscordColors.Muted, 13.sp)
                }
            }
        }
    }
}

@Composable
private fun Avatar(user: DiscordUser?, name: String) {
    val image = rememberRemoteImage(avatarUrl(user))
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DiscordColors.Blurple),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Discord CDN link of the user's avatar, "" if Discord reported none (or something that is not an avatar hash). */
fun avatarUrl(user: DiscordUser?): String {
    if (user == null) return ""
    val id = user.id.trim()
    val hash = user.avatar.trim()
    if (id.isEmpty() || !id.all { it.isDigit() }) return ""
    if (hash.isEmpty() || !hash.all { it in '0'..'9' || it in 'a'..'f' || it == '_' }) return ""
    return "https://cdn.discordapp.com/avatars/$id/$hash.png?size=64"
}

@Composable
private fun Line(
    text: String,
    color: Color,
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
) {
    Text(text, color = color, fontSize = size, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
private fun Note(text: String, color: Color = DiscordColors.Muted) {
    Text(text, color = color, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
}
