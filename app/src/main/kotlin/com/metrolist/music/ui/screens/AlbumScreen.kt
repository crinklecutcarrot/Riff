/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.AlbumWithSongs
import com.metrolist.music.db.entities.Song
import com.metrolist.music.playback.queues.LocalAlbumRadio
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.RiffPlayingBars
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.SelectionSongMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.theme.RiffAzeretMono
import com.metrolist.music.ui.theme.RiffSubtextWeight
import com.metrolist.music.ui.theme.riffControlColors
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.AlbumViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false

    val playlistId by viewModel.playlistId.collectAsStateWithLifecycle()
    val albumWithSongs by viewModel.albumWithSongs.collectAsStateWithLifecycle()
    val otherVersions by viewModel.otherVersions.collectAsStateWithLifecycle()
    val moreByArtist by viewModel.moreByArtist.collectAsStateWithLifecycle()
    val trackPlays by viewModel.trackPlays.collectAsStateWithLifecycle()
    val albumType by viewModel.albumType.collectAsStateWithLifecycle()
    val remoteAlbumPage by viewModel.remoteAlbumPage.collectAsStateWithLifecycle()
    val remoteAlbumSaved by viewModel.remoteAlbumSaved.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(HideExplicitKey, false)
    val hideVideoSongs by rememberPreference(HideVideoSongsKey, false)

    val filteredSongs = remember(albumWithSongs, hideExplicit, hideVideoSongs) {
        albumWithSongs?.songs.orEmpty()
            .filterNot { hideExplicit && it.song.explicit }
            .filterNot { hideVideoSongs && it.song.isVideo }
    }

    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (previousLightStatusBars != null) {
                controller.isAppearanceLightStatusBars = previousLightStatusBars
            }
        }
    }

    val addedMessage = stringResource(R.string.riff_album_added_to_library)
    val removedMessage = stringResource(R.string.riff_album_removed_from_library)
    val failureMessage = stringResource(R.string.riff_library_sync_failed)
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.libraryMutations.collect { result ->
            snackbarHostState.showSnackbar(
                when (result) {
                    AlbumViewModel.LibraryMutation.ADDED -> addedMessage
                    AlbumViewModel.LibraryMutation.REMOVED -> removedMessage
                    AlbumViewModel.LibraryMutation.FAILED -> failureMessage
                },
            )
        }
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf() }
    val exitSelection = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) BackHandler(onBack = exitSelection)

    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val page = albumWithSongs
    if (page == null || page.songs.isEmpty() || remoteAlbumPage == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val controls = riffControlColors()
    val albumAdded = remoteAlbumSaved == true
    val primaryArtist = page.artists.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
    ) {
        item(key = "riff_album_hero") {
            AlbumHero(
                album = page,
                albumType = albumType,
                onBack = { navController.navigateUp() },
                onBackLong = { navController.backToMain() },
                onArtist = { primaryArtist?.id?.let { navController.navigate("artist/$it") } },
                onMenu = {
                    menuState.show {
                        AlbumMenu(Album(page.album, page.artists), onDismiss = menuState::dismiss)
                    }
                },
            )
        }

        item(key = "riff_album_actions") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = {
                        if (!isListenTogetherGuest) {
                            playerConnection.service.getAutomix(playlistId)
                            playerConnection.playQueue(LocalAlbumRadio(page))
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = controls.accent,
                    contentColor = controls.onAccent,
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.tabler_ic_player_play_filled), stringResource(R.string.play), Modifier.size(19.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(stringResource(R.string.play), fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(
                    onClick = viewModel::toggleAlbumLibrary,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = controls.secondary,
                    contentColor = controls.onSecondary,
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(
                                if (albumAdded) R.drawable.tabler_ic_circle_check_filled
                                else R.drawable.tabler_ic_library_plus,
                            ),
                            stringResource(
                                if (albumAdded) R.string.riff_added_to_library
                                else R.string.riff_add_to_library,
                            ),
                            Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            stringResource(
                                if (albumAdded) R.string.riff_added_to_library
                                else R.string.riff_add_to_library,
                            ),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        itemsIndexed(filteredSongs, key = { index, song -> "riff_album_track_${song.id}_$index" }) { index, song ->
            AlbumTrackRow(
                song = song,
                index = index,
                plays = trackPlays[song.id].orEmpty().ifBlank { stringResource(R.string.riff_plays_unavailable) },
                active = song.id == mediaMetadata?.id,
                playing = isPlaying,
                selected = song.id in selection,
                selecting = inSelectMode,
                onClick = {
                    if (inSelectMode) {
                        if (song.id in selection) selection.remove(song.id) else selection.add(song.id)
                    } else if (!isListenTogetherGuest) {
                        if (song.id == mediaMetadata?.id) playerConnection.togglePlayPause()
                        else {
                            playerConnection.service.getAutomix(playlistId)
                            playerConnection.playQueue(LocalAlbumRadio(page, startIndex = index))
                        }
                    }
                },
                onLongClick = {
                    if (!inSelectMode) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        inSelectMode = true
                        selection.add(song.id)
                    }
                },
                onMore = {
                    val remoteSong = remoteAlbumPage?.songs?.firstOrNull { it.id == song.id }
                    menuState.show {
                        if (remoteSong != null) {
                            YouTubeSongMenu(song = remoteSong, onDismiss = menuState::dismiss)
                        } else {
                            SongMenu(originalSong = song, onDismiss = menuState::dismiss)
                        }
                    }
                },
            )
        }

        if (moreByArtist.isNotEmpty() || otherVersions.isNotEmpty()) {
            item(key = "riff_post_tracks_spacing") {
                Spacer(Modifier.height(24.dp))
            }
        }

        if (moreByArtist.isNotEmpty()) {
            item(key = "riff_more_by") {
                AlbumShelf(
                    title = stringResource(R.string.riff_more_by_artist, primaryArtist?.name.orEmpty()),
                    albums = moreByArtist,
                    onAlbum = { navController.navigate("album/${it.id}") },
                    onLongClick = { item ->
                        menuState.show { YouTubeAlbumMenu(albumItem = item, onDismiss = menuState::dismiss) }
                    },
                )
            }
        }

        if (otherVersions.isNotEmpty()) {
            item(key = "riff_other_versions") {
                AlbumShelf(
                    title = stringResource(R.string.other_versions),
                    albums = otherVersions.distinctBy { it.id },
                    onAlbum = { navController.navigate("album/${it.id}") },
                    onLongClick = { item ->
                        menuState.show { YouTubeAlbumMenu(albumItem = item, onDismiss = menuState::dismiss) }
                    },
                )
            }
        }
    }

    if (inSelectMode) {
        TopAppBar(
            title = { Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size)) },
            navigationIcon = {
                IconButton(onClick = exitSelection) { Icon(painterResource(R.drawable.tabler_ic_x_outline), null) }
            },
            actions = {
                Checkbox(
                    checked = selection.size == filteredSongs.size && selection.isNotEmpty(),
                    onCheckedChange = {
                        selection.clear()
                        if (it) selection.addAll(filteredSongs.map { song -> song.id })
                    },
                )
                IconButton(
                    enabled = selection.isNotEmpty(),
                    onClick = {
                        menuState.show {
                            SelectionSongMenu(
                                songSelection = selection.mapNotNull { id -> filteredSongs.find { it.id == id } },
                                onDismiss = menuState::dismiss,
                                clearAction = exitSelection,
                            )
                        }
                    },
                ) { Icon(painterResource(R.drawable.tabler_ic_dots_vertical_outline), null) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumHero(
    album: AlbumWithSongs,
    albumType: String?,
    onBack: () -> Unit,
    onBackLong: () -> Unit,
    onArtist: () -> Unit,
    onMenu: () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val totalDuration = album.songs.sumOf { it.song.duration.coerceAtLeast(0) }
    val metadata = listOfNotNull(
        albumType?.takeIf { it.isNotBlank() } ?: stringResource(R.string.riff_album_type),
        album.album.year?.toString(),
        pluralStringResource(R.plurals.n_song, album.songs.size, album.songs.size),
        totalDuration.takeIf { it > 0 }?.let { "${it / 60} min" },
    ).joinToString(" \u2022 ")

    Box(Modifier.fillMaxWidth().height(438.dp).clipToBounds().background(background)) {
        AsyncImage(
            model = album.album.thumbnailUrl?.resize(1080, 1080),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().scale(1.2f).blur(32.dp),
        )
        Box(
            Modifier.fillMaxWidth().height(438.dp).background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.62f),
                    0.30f to Color.Black.copy(alpha = 0.22f),
                    0.48f to background.copy(alpha = 0.22f),
                    0.70f to background.copy(alpha = 0.78f),
                    0.86f to background,
                    1f to background,
                ),
            ),
        )

        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(44.dp).combinedClickable(onClick = onBack, onLongClick = onBackLong),
                shape = CircleShape,
                color = Color(0xB3121315),
                contentColor = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.tabler_ic_arrow_left_outline), null, Modifier.size(20.dp)) }
            }
            Surface(onClick = onMenu, modifier = Modifier.size(44.dp), shape = CircleShape, color = Color(0xB3121315), contentColor = Color.White) {
                Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.tabler_ic_dots_vertical_outline), null, Modifier.size(21.dp)) }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 112.dp).size(196.dp),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 24.dp,
        ) {
            AsyncImage(
                model = album.album.thumbnailUrl?.resize(1080, 1080),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 22.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                album.album.title,
                fontSize = 25.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 7.dp).clickable(onClick = onArtist),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                album.artists.firstOrNull()?.thumbnailUrl?.let { thumbnail ->
                    AsyncImage(
                        model = thumbnail.resize(96, 96),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(22.dp).clip(CircleShape),
                    )
                }
                Text(
                    album.artists.joinToString { it.name },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = RiffSubtextWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                metadata.uppercase(),
                Modifier.padding(top = 7.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = RiffAzeretMono,
                fontSize = 10.sp,
                fontWeight = RiffSubtextWeight,
                letterSpacing = 1.1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AlbumCircleAction(onClick: () -> Unit, icon: Int, description: String?, tint: Color? = null) {
    val controls = riffControlColors()
    Surface(onClick = onClick, modifier = Modifier.size(52.dp), shape = CircleShape, color = controls.secondary, contentColor = controls.onSecondary) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), description, Modifier.size(23.dp), tint = tint ?: controls.onSecondary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumTrackRow(
    song: Song,
    index: Int,
    plays: String,
    active: Boolean,
    playing: Boolean,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    val controls = riffControlColors()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (active) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
                else Color.Transparent,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            if (selecting) Checkbox(selected, onCheckedChange = { onClick() }, modifier = Modifier.size(22.dp))
            else if (active) RiffPlayingBars(animated = playing, color = MaterialTheme.colorScheme.onSurface)
            else Text((index + 1).toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = RiffAzeretMono, fontSize = 11.sp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                song.song.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.offset(y = (-3).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (song.song.explicit) {
                    Box(
                        modifier =
                            Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "E",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = RiffAzeretMono,
                            fontSize = 8.sp,
                            lineHeight = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    plays,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = RiffAzeretMono,
                    fontSize = 10.5.sp,
                    fontWeight = RiffSubtextWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (song.song.liked) {
            Icon(
                painterResource(R.drawable.tabler_ic_thumb_up_filled),
                contentDescription = null,
                modifier = Modifier.padding(end = 10.dp).size(17.dp),
                tint = controls.accent,
            )
        }
        Text(
            makeTimeString(song.song.duration.coerceAtLeast(0) * 1000L),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = RiffAzeretMono,
            fontSize = 11.sp,
            fontWeight = RiffSubtextWeight,
        )
        IconButton(onClick = onMore) {
            Icon(painterResource(R.drawable.tabler_ic_dots_vertical_outline), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumShelf(
    title: String,
    albums: List<AlbumItem>,
    onAlbum: (AlbumItem) -> Unit,
    onLongClick: (AlbumItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp)) {
        Text(title, Modifier.padding(horizontal = 22.dp, vertical = 10.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(contentPadding = PaddingValues(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(albums, key = { it.id }) { album ->
                Column(
                    Modifier.width(132.dp).combinedClickable(onClick = { onAlbum(album) }, onLongClick = { onLongClick(album) }),
                ) {
                    AsyncImage(
                        model = album.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(132.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Text(
                        album.title,
                        Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(album.year?.toString(), stringResource(R.string.riff_album_type)).joinToString(" \u2022 "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        fontWeight = RiffSubtextWeight,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
