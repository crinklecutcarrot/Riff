package com.metrolist.music.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.metrolist.music.extensions.metadata
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.ui.theme.RiffDockEmpty
import com.metrolist.music.ui.theme.RiffDockFallback
import com.metrolist.music.ui.theme.RiffGeneralSans
import com.metrolist.music.ui.theme.RiffSubtextWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Dock content heights, excluding the system navigation-bar inset. */
val RiffDockNavigationHeight = 100.dp
val RiffStaticDockNavigationHeight = 84.dp
val RiffDockPlayerExpansionHeight = 81.dp
val RiffDockPageSpacing = 24.dp

/** Vertical margin the floating dock reserves above/below its visible pill. */
val RiffDockFloatingVerticalMargin = 8.dp

/**
 * Distance from the system navigation-bar inset up to the TOP of the visible bottom-bar pill/bar
 * (nav only, no mini-player). Static docks are flush, so their bar top is just their own height;
 * floating docks sit [RiffDockFloatingVerticalMargin] above the anchor, so the visible pill top is
 * that much below the reserved [RiffDockNavigationHeight]. Used to place FABs a consistent gap above
 * the bar regardless of dock style.
 */
val RiffFloatingDockBarTop = RiffDockNavigationHeight - RiffDockFloatingVerticalMargin

private const val RIFF_DOCK_ANIMATION_MILLIS = 420

@Composable
fun RiffPlayerDock(
    navigationItems: List<Screens>,
    currentRoute: String?,
    playerConnection: PlayerConnection?,
    onItemClick: (Screens, Boolean) -> Unit,
    onPlayerClick: () -> Unit,
    floating: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val metadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    val isPlaying by playerConnection?.isEffectivelyPlaying?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val canSkipNext by playerConnection?.canSkipNext?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val canSkipPrevious by playerConnection?.canSkipPrevious?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val hasActiveMedia = metadata != null

    // Adjacent tracks so the mini-player swipe can preview the song coming in. Recomputed when the
    // current song or the skip availability changes (covers queue edits / repeat-mode toggles).
    val prevMetadata = remember(metadata?.id, canSkipPrevious) {
        runCatching {
            playerConnection?.player?.let { p ->
                p.previousMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { p.getMediaItemAt(it).metadata }
            }
        }.getOrNull()
    }
    val nextMetadata = remember(metadata?.id, canSkipNext) {
        runCatching {
            playerConnection?.player?.let { p ->
                p.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { p.getMediaItemAt(it).metadata }
            }
        }.getOrNull()
    }
    val context = LocalContext.current
    var extractedColor by remember { mutableStateOf(RiffDockFallback) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(floating, metadata?.id, metadata?.thumbnailUrl) {
        extractedColor = RiffDockFallback
        if (!floating) return@LaunchedEffect
        val artworkUrl = metadata?.thumbnailUrl ?: return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(artworkUrl)
            .size(128, 128)
            .allowHardware(false)
            .build()
        val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
            ?: return@LaunchedEffect
        val palette = Palette.from(bitmap)
            .maximumColorCount(12)
            .resizeBitmapArea(128 * 128)
            .generate()
        val swatchColor = Color(
            palette.getDarkMutedColor(
                palette.getDarkVibrantColor(RiffDockFallback.toArgb()),
            ),
        )
        extractedColor = if (swatchColor.luminance() > 0.32f) {
            lerp(swatchColor, Color.Black, 0.42f)
        } else {
            swatchColor
        }
    }

    LaunchedEffect(playerConnection, metadata?.id, isPlaying) {
        val player = runCatching { playerConnection?.player }.getOrNull()
        if (player == null) {
            progress = 0f
            return@LaunchedEffect
        }
        do {
            val duration = player.duration.takeIf { it > 0 }
                ?: metadata?.duration?.takeIf { it > 0 }?.toLong()?.times(1000L)
                ?: 0L
            progress = if (duration > 0) {
                (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
            delay(if (isPlaying) 100L else 500L)
        } while (isActive)
    }

    val staticDockColor = MaterialTheme.colorScheme.surfaceContainer
    val staticContentColor = MaterialTheme.colorScheme.onSurface
    val dockColor by animateColorAsState(
        targetValue =
            if (!floating) staticDockColor
            else if (hasActiveMedia) extractedColor
            else RiffDockEmpty,
        animationSpec = tween(RIFF_DOCK_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
        label = "riffDockColor",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (!floating) staticContentColor
            else if (hasActiveMedia) Color.White
            else Color(0xFF151516),
        animationSpec = tween(RIFF_DOCK_ANIMATION_MILLIS),
        label = "riffDockContentColor",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (floating) Modifier else Modifier.background(staticDockColor))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .then(
                if (floating) Modifier.padding(horizontal = 12.dp, vertical = RiffDockFloatingVerticalMargin)
                else Modifier,
            ),
    ) {
        val dockShape = if (floating) RoundedCornerShape(30.dp) else RectangleShape
        Surface(
            color = dockColor,
            contentColor = contentColor,
            shape = dockShape,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (floating) {
                        Modifier
                            .dropShadow(RoundedCornerShape(30.dp)) {
                                radius = 22.dp.toPx()
                                spread = 0f
                                offset = Offset(0f, 8.dp.toPx())
                                color = Color.Black
                                alpha = 0.22f
                            }
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = if (hasActiveMedia) 0.08f else 0f),
                                shape = RoundedCornerShape(30.dp),
                            )
                    } else {
                        Modifier
                    },
                )
                .animateContentSize(
                    animationSpec = tween(
                        durationMillis = RIFF_DOCK_ANIMATION_MILLIS,
                        easing = FastOutSlowInEasing,
                    ),
                ),
        ) {
            Column {
                AnimatedVisibility(
                    visible = hasActiveMedia,
                    enter = fadeIn(tween(280)) + slideInVertically(tween(RIFF_DOCK_ANIMATION_MILLIS)) { it / 3 },
                    exit = fadeOut(tween(220)) + slideOutVertically(tween(300)) { it / 3 },
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(78.dp)
                                .clickable(onClick = onPlayerClick)
                                .padding(start = 13.dp, top = 13.dp, end = 10.dp, bottom = 13.dp),
                        ) {
                            RiffDockMediaCarousel(
                                metadata = metadata,
                                prevMetadata = prevMetadata,
                                nextMetadata = nextMetadata,
                                contentColor = contentColor,
                                onPrevious = { runCatching { playerConnection?.player?.seekToPreviousMediaItem() } },
                                onNext = { runCatching { playerConnection?.player?.seekToNextMediaItem() } },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(contentColor.copy(alpha = 0.16f))
                                    .clickable { playerConnection?.togglePlayPause() },
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (isPlaying) {
                                            R.drawable.tabler_ic_player_pause_filled
                                        } else {
                                            R.drawable.tabler_ic_player_play_filled
                                        },
                                    ),
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(25.dp),
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.tabler_ic_player_track_next_filled),
                                contentDescription = null,
                                tint = if (canSkipNext) contentColor else contentColor.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = canSkipNext) { playerConnection?.seekToNext() }
                                    .padding(9.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 13.dp, end = 10.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(contentColor.copy(alpha = 0.22f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(3.dp)
                                    .background(contentColor),
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    navigationItems.forEach { screen ->
                        val selected = isRiffRouteSelected(currentRoute, screen.route)
                        val icon = riffNavigationIcon(screen, selected)
                        val itemColor = if (!floating) {
                            if (selected) contentColor else contentColor.copy(alpha = 0.58f)
                        } else if (hasActiveMedia) {
                            if (selected) Color.White else Color.White.copy(alpha = 0.58f)
                        } else {
                            if (selected) Color(0xFF151516) else Color(0xFF98989B)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(22.dp))
                                .clickable { onItemClick(screen, selected) },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 48.dp, height = 28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) {
                                            if (!floating) contentColor.copy(alpha = 0.14f)
                                            else if (hasActiveMedia) Color.White.copy(alpha = 0.14f)
                                            else Color(0xFFF0F0EF)
                                        } else {
                                            Color.Transparent
                                        },
                                    ),
                            ) {
                                Icon(
                                    painter = painterResource(icon),
                                    contentDescription = stringResource(screen.titleId),
                                    tint = itemColor,
                                    modifier = Modifier.size(23.dp),
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(screen.titleId),
                                color = itemColor,
                                fontFamily = RiffGeneralSans,
                                fontSize = 11.sp,
                                letterSpacing = (-0.01).em,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The swipeable song-info region of the dock mini-player (artwork + title + artist). Dragging
 * horizontally previews the previous/next track sliding in alongside the current one fading out;
 * the song only changes when the finger lifts past the threshold, after which the committed track
 * settles into the center. A plain tap falls through to the parent row (expands the full player).
 */
@Composable
private fun RiffDockMediaCarousel(
    metadata: MediaMetadata?,
    prevMetadata: MediaMetadata?,
    nextMetadata: MediaMetadata?,
    contentColor: Color,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    // Require a genuinely different neighbour: with repeat-one the "next" index is the current track,
    // and committing to it wouldn't change the song id (leaving the carousel stuck off-center).
    val hasNext = nextMetadata != null && nextMetadata.id != metadata?.id
    val hasPrev = prevMetadata != null && prevMetadata.id != metadata?.id

    // Re-center whenever the current song changes — whether from a swipe commit above, the track
    // ending naturally, or the next/prev buttons. The committed track was already animated into the
    // center card, so snapping the offset back to 0 here is seamless (same song, same position).
    LaunchedEffect(metadata?.id) {
        offsetX.snapTo(0f)
    }

    val settleSpec = remember { spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { widthPx = it.width }
            .pointerInput(hasNext, hasPrev, widthPx) {
                val w = widthPx.toFloat()
                if (w <= 0f) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val current = offsetX.value
                        val threshold = w * 0.3f
                        when {
                            current <= -threshold && hasNext ->
                                scope.launch { offsetX.animateTo(-w, tween(220)); onNext() }
                            current >= threshold && hasPrev ->
                                scope.launch { offsetX.animateTo(w, tween(220)); onPrevious() }
                            else -> scope.launch { offsetX.animateTo(0f, settleSpec) }
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, settleSpec) } },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val min = if (hasNext) -w else 0f
                        val max = if (hasPrev) w else 0f
                        scope.launch { offsetX.snapTo((offsetX.value + dragAmount).coerceIn(min, max)) }
                    },
                )
            },
    ) {
        if (widthPx > 0) {
            val w = widthPx.toFloat()
            prevMetadata?.let {
                RiffDockTrackInfo(it, contentColor, Modifier.fillMaxSize().graphicsLayer {
                    translationX = offsetX.value - w
                    alpha = (1f - kotlin.math.abs(offsetX.value - w) / w).coerceIn(0f, 1f)
                })
            }
            nextMetadata?.let {
                RiffDockTrackInfo(it, contentColor, Modifier.fillMaxSize().graphicsLayer {
                    translationX = offsetX.value + w
                    alpha = (1f - kotlin.math.abs(offsetX.value + w) / w).coerceIn(0f, 1f)
                })
            }
            RiffDockTrackInfo(metadata, contentColor, Modifier.fillMaxSize().graphicsLayer {
                translationX = offsetX.value
                alpha = (1f - kotlin.math.abs(offsetX.value) / w).coerceIn(0f, 1f)
            })
        } else {
            RiffDockTrackInfo(metadata, contentColor, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun RiffDockTrackInfo(
    metadata: MediaMetadata?,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        AsyncImage(
            model = metadata?.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = metadata?.title.orEmpty(),
                color = contentColor,
                fontFamily = RiffGeneralSans,
                fontSize = 17.sp,
                letterSpacing = (-0.01).em,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = metadata?.artists?.joinToString(separator = ", ") { it.name }.orEmpty(),
                color = contentColor.copy(alpha = 0.66f),
                fontFamily = RiffGeneralSans,
                fontSize = 13.sp,
                letterSpacing = (-0.01).em,
                lineHeight = 13.sp,
                fontWeight = RiffSubtextWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun riffNavigationIcon(screen: Screens, selected: Boolean): Int = when (screen) {
    Screens.Home -> if (selected) R.drawable.tabler_ic_home_filled else R.drawable.tabler_ic_home_outline
    Screens.Explore -> if (selected) R.drawable.tabler_ic_zoom_filled else R.drawable.tabler_ic_search_outline
    Screens.Search -> if (selected) R.drawable.tabler_ic_zoom_filled else R.drawable.tabler_ic_search_outline
    Screens.ListenTogether -> if (selected) R.drawable.tabler_ic_user_filled else R.drawable.tabler_ic_users_outline
    Screens.Library -> if (selected) R.drawable.tabler_ic_library_filled else R.drawable.tabler_ic_library_outline
}

private fun isRiffRouteSelected(currentRoute: String?, screenRoute: String): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute || currentRoute.startsWith("$screenRoute/")) return true
    return (screenRoute == Screens.Explore.route || screenRoute == "search_input") &&
        (currentRoute.startsWith("search/") || currentRoute == "search/{query}" || currentRoute == "search_input")
}
