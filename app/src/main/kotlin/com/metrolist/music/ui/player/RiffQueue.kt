package com.metrolist.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Timeline
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.move
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.BottomSheet
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.RiffPlayingBars
import com.metrolist.music.ui.menu.QueueMenu
import com.metrolist.music.ui.theme.RiffAzeretMono
import com.metrolist.music.ui.theme.RiffSubtextWeight
import com.metrolist.music.ui.theme.riffControlColors
import com.metrolist.music.ui.utils.ShowMediaInfo
import com.metrolist.music.utils.makeTimeString
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun RiffQueue(
    state: BottomSheetState,
    playerBottomSheetState: BottomSheetState,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val pageState = LocalBottomSheetPageState.current
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val mutableQueue = remember { mutableStateListOf<Timeline.Window>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var dismissJob by remember { mutableStateOf<Job?>(null) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var positionedForOpen by remember { mutableStateOf(false) }
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var headerDragDistance by remember { mutableFloatStateOf(0f) }

    val currentUid = queueWindows.getOrNull(currentWindowIndex)?.uid
    val activeIndex = mutableQueue.indexOfFirst { it.uid == currentUid }.takeIf { it >= 0 } ?: currentWindowIndex
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val sheetColor = if (isDark) Color(0xFF0E1014).copy(alpha = 0.96f) else Color(0xFFFAFAF8).copy(alpha = 0.96f)
    val primaryText = if (isDark) Color.White else Color(0xFF111112)
    val secondaryText = primaryText.copy(alpha = if (isDark) 0.56f else 0.58f)
    val tertiaryText = primaryText.copy(alpha = if (isDark) 0.5f else 0.54f)
    val rowColor = primaryText.copy(alpha = if (isDark) 0.07f else 0.05f)
    val controls = riffControlColors()
    val accent = controls.accent
    val onAccent = controls.onAccent
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(queueWindows) {
        mutableQueue.clear()
        mutableQueue.addAll(queueWindows)
    }
    LaunchedEffect(state.isCollapsed, activeIndex, mutableQueue.size) {
        if (state.isCollapsed) {
            positionedForOpen = false
        } else if (!positionedForOpen && activeIndex in mutableQueue.indices) {
            listState.scrollToItem(activeIndex)
            positionedForOpen = true
        }
    }
    LaunchedEffect(mediaMetadata?.id, isPlaying) {
        while (isActive) {
            currentPosition = playerConnection.player.currentPosition.coerceAtLeast(0L)
            delay(if (isPlaying) 500L else 1000L)
        }
    }

    val reorderableState =
        rememberReorderableLazyListState(listState) { from, to ->
            dragInfo = (dragInfo?.first ?: from.index) to to.index
            mutableQueue.move(from.index.coerceIn(mutableQueue.indices), to.index.coerceIn(mutableQueue.indices))
        }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                if (queueWindows.isNotEmpty()) {
                    val safeFrom = from.coerceIn(queueWindows.indices)
                    val safeTo = to.coerceIn(queueWindows.indices)
                    playerConnection.player.moveMediaItem(safeFrom, safeTo)
                }
            }
            dragInfo = null
        }
    }

    val sourceTitle = queueTitle?.takeIf { it.isNotBlank() } ?: mediaMetadata?.album?.title ?: stringResource(R.string.queue)
    val sourceLabel =
        when {
            queueTitle?.contains("radio", ignoreCase = true) == true -> stringResource(R.string.riff_playing_from_radio)
            mediaMetadata?.album?.title != null &&
                (queueTitle == mediaMetadata?.album?.title || queueWindows.all { it.mediaItem.metadata?.album?.id == mediaMetadata?.album?.id }) ->
                stringResource(R.string.riff_playing_from_album)
            !queueTitle.isNullOrBlank() -> stringResource(R.string.riff_playing_from_playlist)
            else -> stringResource(R.string.riff_playing_from_queue)
        }
    val remainingItems = if (activeIndex in mutableQueue.indices) mutableQueue.drop(activeIndex) else mutableQueue
    val remainingSeconds =
        (remainingItems.sumOf { it.mediaItem.metadata?.duration?.toLong() ?: 0L } - currentPosition / 1000L)
            .coerceAtLeast(0L)
    val queueMeta = stringResource(R.string.riff_queue_remaining, remainingItems.size, makeTimeString(remainingSeconds * 1000L))

    BottomSheet(
        state = state,
        modifier = modifier,
        background = {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        },
        collapsedContent = {},
        isExpandable = false,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { state.collapseSoft() },
            )
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.79f)
                        .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                        .background(sheetColor),
            ) {
                Column(
                    modifier =
                        Modifier.pointerInput(state) {
                            detectVerticalDragGestures(
                                onDragStart = { headerDragDistance = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    headerDragDistance += dragAmount
                                    state.dispatchRawDelta(dragAmount)
                                },
                                onDragCancel = { state.expandSoft() },
                                onDragEnd = {
                                    if (headerDragDistance > 48.dp.toPx()) state.collapseSoft()
                                    else state.expandSoft()
                                },
                            )
                        },
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(width = 44.dp, height = 4.dp).clip(CircleShape).background(tertiaryText))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            RiffQueueSectionLabel(sourceLabel, tertiaryText)
                            Text(
                                sourceTitle,
                                color = primaryText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                            Text(
                                queueMeta,
                                color = secondaryText,
                                fontFamily = RiffAzeretMono,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                        RiffQueueCircleButton(
                            icon = if (isPlaying) R.drawable.tabler_ic_player_pause_filled else R.drawable.tabler_ic_player_play_filled,
                            containerColor = accent,
                            contentColor = onAccent,
                            onClick = playerConnection::togglePlayPause,
                        )
                        RiffQueueCircleButton(
                            icon = R.drawable.tabler_ic_player_track_next_filled,
                            containerColor = primaryText.copy(alpha = if (isDark) 0.14f else 0.07f),
                            contentColor = primaryText,
                            enabled = canSkipNext,
                            onClick = playerConnection::seekToNext,
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 22.dp), color = primaryText.copy(alpha = 0.1f))
                }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 112.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(mutableQueue, key = { _, item -> item.uid.hashCode() }) { index, window ->
                            ReorderableItem(reorderableState, key = window.uid.hashCode()) {
                                val currentItem by rememberUpdatedState(window)
                                val itemActive = window.uid == currentUid
                                val dismissState = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.45f })
                                var removed by remember(window.uid) { mutableStateOf(false) }
                                LaunchedEffect(dismissState.currentValue) {
                                    if (!removed && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                        removed = true
                                        playerConnection.player.removeMediaItem(currentItem.firstPeriodIndex)
                                        dismissJob?.cancel()
                                        dismissJob = scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = currentItem.mediaItem.metadata?.title.orEmpty(),
                                                actionLabel = undoLabel,
                                                duration = SnackbarDuration.Short,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                playerConnection.player.addMediaItem(currentItem.mediaItem)
                                                playerConnection.player.moveMediaItem(
                                                    mutableQueue.size,
                                                    currentItem.firstPeriodIndex,
                                                )
                                            }
                                        }
                                    }
                                    if (dismissState.currentValue == SwipeToDismissBoxValue.Settled) removed = false
                                }
                                Column {
                                    SwipeToDismissBox(state = dismissState, backgroundContent = {}) {
                                        RiffQueueRow(
                                            metadata = window.mediaItem.metadata!!,
                                            active = itemActive,
                                            past = index < activeIndex,
                                            playing = isPlaying && itemActive,
                                            primaryText = primaryText,
                                            secondaryText = secondaryText,
                                            tertiaryText = tertiaryText,
                                            rowColor = rowColor,
                                            dragModifier = Modifier.draggableHandle(),
                                            onClick = {
                                                if (itemActive) {
                                                    playerConnection.togglePlayPause()
                                                } else {
                                                    playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                                    playerConnection.player.playWhenReady = true
                                                }
                                            },
                                            onMore = {
                                                menuState.show {
                                                    QueueMenu(
                                                        mediaMetadata = window.mediaItem.metadata!!,
                                                        playerBottomSheetState = playerBottomSheetState,
                                                        onShowDetailsDialog = {
                                                            pageState.show { ShowMediaInfo(window.mediaItem.mediaId) }
                                                        },
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(112.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, sheetColor.copy(alpha = 0.96f), sheetColor),
                                ),
                            ),
                    )
                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 22.dp, vertical = 16.dp)
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(CircleShape)
                                .background(accent)
                                .clickable { state.collapseSoft() },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.tabler_ic_arrow_left_outline), null, tint = onAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(stringResource(R.string.riff_back_to_player), color = onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
                }
            }
        }
    }
}

@Composable
private fun RiffQueueSectionLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = color,
        fontFamily = RiffAzeretMono,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.5.sp,
        modifier = modifier,
    )
}

@Composable
private fun RiffQueueCircleButton(
    icon: Int,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = contentColor, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun RiffQueueRow(
    metadata: MediaMetadata,
    active: Boolean,
    past: Boolean,
    playing: Boolean,
    primaryText: Color,
    secondaryText: Color,
    tertiaryText: Color,
    rowColor: Color,
    dragModifier: Modifier,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (past) 0.62f else 1f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (active) rowColor else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = onMore)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(R.drawable.tabler_ic_grip_vertical_outline),
            null,
            tint = tertiaryText,
            modifier = dragModifier.size(16.dp),
        )
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            coil3.compose.AsyncImage(
                model = metadata.thumbnailUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (active) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
                RiffPlayingBars(animated = playing, color = Color.White)
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(metadata.title, color = primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (metadata.explicit) {
                    Box(
                        modifier = Modifier.size(16.dp).clip(RoundedCornerShape(2.dp)).background(primaryText.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("E", color = secondaryText, fontFamily = RiffAzeretMono, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                metadata.artists.joinToString { it.name }.ifBlank { stringResource(R.string.riff_unknown_artist) },
                color = secondaryText,
                fontSize = 11.5.sp,
                fontWeight = RiffSubtextWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(makeTimeString(metadata.duration * 1000L), color = tertiaryText, fontFamily = RiffAzeretMono, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Icon(
            painterResource(R.drawable.tabler_ic_dots_vertical_outline),
            null,
            tint = tertiaryText,
            modifier = Modifier.size(30.dp).padding(6.dp).clickable(onClick = onMore),
        )
    }
}
