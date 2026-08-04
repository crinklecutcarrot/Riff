/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.theme.RiffSubtextWeight
import com.metrolist.music.ui.utils.SnapLayoutInfoProvider
import com.metrolist.music.viewmodels.ChartsViewModel
import com.metrolist.music.viewmodels.ExploreViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    navController: NavController,
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    chartsViewModel: ChartsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val explorePage by exploreViewModel.explorePage.collectAsStateWithLifecycle()
    val chartsPage by chartsViewModel.chartsPage.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop by backStackEntry
        ?.savedStateHandle
        ?.getStateFlow("scrollToTop", false)
        ?.collectAsStateWithLifecycle() ?: return

    LaunchedEffect(Unit) {
        if (chartsPage == null) chartsViewModel.loadCharts()
    }
    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            listState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        item("search_entry") {
            Surface(
                onClick = { navController.navigate(Screens.Search.route) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier.height(52.dp).padding(horizontal = 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.tabler_ic_search_outline),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                    Text(
                        text = "Songs, albums, artists…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        fontWeight = RiffSubtextWeight,
                    )
                }
            }
        }

        val topSongs = chartsPage?.sections.orEmpty().firstOrNull {
            it.title.contains("Top songs", ignoreCase = true)
        }
        if (topSongs == null) {
            item("top_songs_spacing") { Spacer(Modifier.height(12.dp)) }
            item("top_songs_title") { NavigationTitle("Top songs") }
            item("top_songs_loading") {
                TextPlaceholder(
                    height = 250.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
            }
        } else {
            item("top_songs_spacing") { Spacer(Modifier.height(12.dp)) }
            item("top_songs_title") {
                NavigationTitle(
                    title = "Top songs",
                    actionText = stringResource(R.string.riff_show_more),
                    onClick = {
                        navController.navigate("online_playlist/PL4fGSI1pDJn6O1LS0XSdF3RyO0Rq_LDeI")
                    },
                )
            }
            item("top_songs_content") {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val widthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                    val itemWidth = maxWidth * widthFactor
                    val gridState = rememberLazyGridState()
                    val snapProvider = remember(gridState) {
                        SnapLayoutInfoProvider(gridState) { layoutSize, itemSize ->
                            layoutSize * widthFactor / 2f - itemSize / 2f
                        }
                    }
                    LazyHorizontalGrid(
                        state = gridState,
                        rows = GridCells.Fixed(4),
                        flingBehavior = rememberSnapFlingBehavior(snapProvider),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.fillMaxWidth().height(ListItemHeight * 4),
                    ) {
                        itemsIndexed(
                            topSongs.items.filterIsInstance<SongItem>().distinctBy { it.id },
                            key = { _, song -> "search_hub_top_song_${song.id}" },
                        ) { index, song ->
                            YouTubeListItem(
                                item = song,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                isSwipeable = false,
                                subtitleTopPadding = 6.dp,
                                trailingContent = {
                                    ChartRankBadge(
                                        position = song.chartPosition ?: index + 1,
                                        rawChange = song.chartChange,
                                    )
                                    IconButton(onClick = {
                                        menuState.show { YouTubeSongMenu(song, menuState::dismiss) }
                                    }) {
                                        Icon(painterResource(R.drawable.tabler_ic_dots_vertical_outline), null)
                                    }
                                },
                                modifier = Modifier.width(itemWidth).combinedClickable(
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) playerConnection.togglePlayPause()
                                        else playerConnection.playQueue(
                                            YouTubeQueue(WatchEndpoint(videoId = song.id), song.toMediaMetadata()),
                                        )
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show { YouTubeSongMenu(song, menuState::dismiss) }
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }

        val releases = explorePage?.newReleaseAlbums.orEmpty().distinctBy { it.id }.take(10)
        item("new_releases_spacing") { Spacer(Modifier.height(12.dp)) }
        item("new_releases_title") {
            NavigationTitle(
                title = "New Releases",
                actionText = stringResource(R.string.riff_show_more),
                onClick = { navController.navigate("new_release") },
            )
        }
        if (explorePage == null) {
            item("releases_loading") {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(3, key = { "release_loading_$it" }) {
                        TextPlaceholder(
                            height = 152.dp,
                            modifier = Modifier.width(152.dp).padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        } else {
            item("new_releases_content") {
                LazyRow(
                    contentPadding = WindowInsets.systemBars
                        .only(WindowInsetsSides.Horizontal)
                        .asPaddingValues(),
                ) {
                    items(releases, key = { "search_hub_release_${it.id}" }) { album ->
                        YouTubeGridItem(
                            item = album,
                            badges = {},
                            isActive = mediaMetadata?.album?.id == album.id,
                            isPlaying = isPlaying,
                            coroutineScope = scope,
                            thumbnailHeight = 152.dp,
                            modifier = Modifier.combinedClickable(
                                onClick = { navController.navigate("album/${album.id}") },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show { YouTubeAlbumMenu(album, menuState::dismiss) }
                                },
                            ),
                        )
                    }
                }
            }
        }

        explorePage?.moodAndGenres?.takeIf { it.isNotEmpty() }?.let { moods ->
            item("moods_spacing") { Spacer(Modifier.height(12.dp)) }
            item("moods_title") {
                NavigationTitle(
                    title = stringResource(R.string.mood_and_genres),
                    actionText = stringResource(R.string.riff_show_more),
                    onClick = { navController.navigate("mood_and_genres") },
                )
            }
            item("moods_content") {
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(4),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height((MoodAndGenresButtonHeight + 12.dp) * 4 + 12.dp),
                ) {
                    items(moods) { mood ->
                        MoodAndGenresButton(
                            title = mood.title,
                            onClick = {
                                navController.navigate("youtube_browse/${mood.endpoint.browseId}?params=${mood.endpoint.params}")
                            },
                            modifier = Modifier.padding(6.dp).width(180.dp),
                        )
                    }
                }
            }
        }

        chartsPage?.sections?.firstOrNull { it.title == "Top music videos" }?.let { videos ->
            item("videos_spacing") { Spacer(Modifier.height(12.dp)) }
            item("videos_title") { NavigationTitle(stringResource(R.string.top_music_videos)) }
            item("videos_content") {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
                    items(videos.items.filterIsInstance<SongItem>().distinctBy { it.id }, key = { "hub_video_${it.id}" }) { video ->
                        YouTubeGridItem(
                            item = video,
                            isActive = video.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            coroutineScope = scope,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    if (video.id == mediaMetadata?.id) playerConnection.togglePlayPause()
                                    else playerConnection.playQueue(
                                        YouTubeQueue(WatchEndpoint(videoId = video.id), video.toMediaMetadata()),
                                    )
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show { YouTubeSongMenu(video, menuState::dismiss) }
                                },
                            ),
                        )
                    }
                }
            }
        }

        item("hub_bottom_space") { Spacer(Modifier.height(24.dp)) }
    }
}

private enum class ChartMovement { UP, DOWN, SAME }

private fun chartMovement(position: Int, rawChange: String?): ChartMovement {
    val normalized = rawChange.orEmpty().trim().lowercase()
    if (normalized.contains("up") || normalized.contains("▲") || normalized.contains("â–²")) {
        return ChartMovement.UP
    }
    if (normalized.contains("down") || normalized.contains("▼") || normalized.contains("â–¼")) {
        return ChartMovement.DOWN
    }
    normalized.filter(Char::isDigit).toIntOrNull()?.let { previousPosition ->
        return when {
            position < previousPosition -> ChartMovement.UP
            position > previousPosition -> ChartMovement.DOWN
            else -> ChartMovement.SAME
        }
    }
    return ChartMovement.SAME
}

@Composable
internal fun ChartRankBadge(position: Int, rawChange: String?) {
    val dark = isSystemInDarkTheme()
    val movement = chartMovement(position, rawChange)
    val accent = when (movement) {
        ChartMovement.UP -> if (dark) Color(0xFF4ADE80) else Color(0xFF15803D)
        ChartMovement.DOWN -> if (dark) Color(0xFFFB7185) else Color(0xFFBE123C)
        ChartMovement.SAME -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = if (dark) 0.18f else 0.12f),
        contentColor = accent,
        modifier = Modifier.padding(start = 6.dp).height(28.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (movement) {
                ChartMovement.UP -> Icon(
                    painterResource(R.drawable.arrow_upward),
                    contentDescription = "Moved up",
                    modifier = Modifier.size(12.dp),
                )
                ChartMovement.DOWN -> Icon(
                    painterResource(R.drawable.arrow_downward),
                    contentDescription = "Moved down",
                    modifier = Modifier.size(12.dp),
                )
                ChartMovement.SAME -> Box(
                    Modifier.size(6.dp).clip(CircleShape).background(accent),
                )
            }
            Text(
                text = "#$position",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
