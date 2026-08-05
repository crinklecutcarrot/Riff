/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.music.LocalNavController
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.R
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.constants.ListThumbnailSize
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.constants.DislikedSongsKey
import com.metrolist.music.db.entities.SpeedDialItem
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.SongRating
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.ListDialog
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.ui.component.NewAction
import com.metrolist.music.ui.component.NewActionGrid
import com.metrolist.music.ui.utils.ShowMediaInfo
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.ui.theme.RiffSubtextWeight
import com.metrolist.music.utils.joinByBullet
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.RemoteMutationResult
import com.metrolist.music.utils.SongRatingSyncResult
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime

@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubeSongMenu(
    song: SongItem,
    onDismiss: () -> Unit,
    onHistoryRemoved: () -> Unit = {}
) {
    val menuState = LocalMenuState.current
    val navController = LocalNavController.current
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val librarySong by database.song(song.id).collectAsStateWithLifecycle(initialValue = null)
    val download by LocalDownloadUtil.current.getDownload(song.id).collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    val listenTogetherManager = LocalListenTogetherManager.current
    val isPinned by database.speedDialDao.isPinned(song.id).collectAsStateWithLifecycle(initialValue = false)
    var remoteLibraryState by remember(song.id, song.isInLibrary) {
        mutableStateOf(song.isInLibrary)
    }
    val effectiveLibraryState = remoteLibraryState
        ?: (librarySong?.song?.inLibrary != null).takeIf { librarySong != null }
    val isEpisode = song.isEpisode
    var dislikedSongIds by rememberPreference(DislikedSongsKey, emptySet())
    val menuRating =
        when {
            librarySong?.song?.liked == true -> SongRating.LIKED
            song.id in dislikedSongIds -> SongRating.DISLIKED
            else -> SongRating.NEUTRAL
        }

    fun setMenuRating(target: SongRating) {
        val original = librarySong?.song
        val base = original ?: song.toMediaMetadata().toSongEntity()
        val wasDisliked = song.id in dislikedSongIds
        val updated =
            base.copy(
                liked = target == SongRating.LIKED,
                likedDate = if (target == SongRating.LIKED) LocalDateTime.now() else null,
                inLibrary = if (target == SongRating.LIKED) base.inLibrary ?: LocalDateTime.now() else base.inLibrary,
            )

        dislikedSongIds =
            dislikedSongIds.toMutableSet().apply {
                if (target == SongRating.DISLIKED) add(song.id) else remove(song.id)
            }
        database.query {
            if (original == null) insert(updated) else update(updated)
        }
        coroutineScope.launch {
            if (syncUtils.rateSongNow(song.id, target) != SongRatingSyncResult.SUCCESS) {
                dislikedSongIds =
                    dislikedSongIds.toMutableSet().apply {
                        if (wasDisliked) add(song.id) else remove(song.id)
                    }
                database.query {
                    update(original ?: base)
                }
            }
        }
    }
    val artists = remember {
        song.artists.mapNotNull {
            it.id?.let { artistId ->
                MediaMetadata.Artist(id = artistId, name = it.name)
            }
        }
    }
    val primaryArtistId = song.artists.firstOrNull()?.id
    var artistThumbnailUrl by remember(song.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(primaryArtistId) {
        artistThumbnailUrl = primaryArtistId
            ?.let { YouTube.artist(it).getOrNull()?.artist?.thumbnail }
    }

    var showChoosePlaylistDialog by rememberSaveable {  
        mutableStateOf(false)  
    }  

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            database.withTransaction {
                insert(song.toMediaMetadata())
            }
            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { browseId ->
                    YouTube.addToPlaylist(browseId, song.id)
                }
            }
            listOf(song.id)
        },
        onGetSongIds = { listOf(song.id) },
        onDismiss = { showChoosePlaylistDialog = false }
    )  

    var showSelectArtistDialog by rememberSaveable {  
        mutableStateOf(false)  
    }  

    if (showSelectArtistDialog) {
        ViewArtistsDialog(
            artists = artists,
            onDismiss = { showSelectArtistDialog = false },
            onArtistClick = { artist ->
                navController.navigate("artist/${artist.id}")
                showSelectArtistDialog = false
                onDismiss()
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.thumbnail.resize(200, 200),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                modifier = Modifier.basicMarquee(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight(650),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = joinByBullet(
                    song.artists.joinToString { it.name },
                    song.duration?.let { makeTimeString(it * 1000L) },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = RiffSubtextWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isEpisode) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                // For episodes, show saved state and toggle save for later
            val isFavorite = if (isEpisode) librarySong?.song?.inLibrary != null else librarySong?.song?.liked == true
            IconButton(
                onClick = {
                    if (isEpisode) {
                        // Episode: toggle save for later
                        val currentLibrarySong = librarySong
                        val isCurrentlySaved = currentLibrarySong?.song?.inLibrary != null
                        val shouldBeSaved = !isCurrentlySaved

                        // Update local database first (optimistic update)
                        database.query {
                            if (currentLibrarySong != null) {
                                update(currentLibrarySong.song.copy(inLibrary = if (shouldBeSaved) LocalDateTime.now() else null))
                            } else {
                                insert(song.toMediaMetadata().toSongEntity().copy(inLibrary = LocalDateTime.now(), isEpisode = true))
                            }
                        }

                        // Sync with YouTube (handles login check internally)
                        coroutineScope.launch(Dispatchers.IO) {
                            val setVideoId = if (isCurrentlySaved) song.setVideoId ?: database.getSetVideoId(song.id)?.setVideoId else null
                            syncUtils.saveEpisode(song.id, shouldBeSaved, setVideoId)
                        }
                    } else {
                        // Regular song: toggle like
                        database.transaction {
                            librarySong.let { librarySong ->
                                val s: SongEntity
                                if (librarySong == null) {
                                    insert(song.toMediaMetadata(), SongEntity::toggleLike)
                                    s = song.toMediaMetadata().toSongEntity().let(SongEntity::toggleLike)
                                } else {
                                    s = librarySong.song.toggleLike()
                                    update(s)
                                }
                                syncUtils.likeSong(s)
                            }
                        }
                    }
                },
            ) {
                Icon(
                    painter = painterResource(
                        if (isFavorite) R.drawable.tabler_ic_thumb_up_filled
                        else R.drawable.tabler_ic_thumb_up_outline,
                    ),
                    tint = LocalContentColor.current,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            }
        } else {
            RiffSheetRatingControl(
                rating = menuRating,
                onLike = {
                    setMenuRating(if (menuRating == SongRating.LIKED) SongRating.NEUTRAL else SongRating.LIKED)
                },
                onDislike = {
                    setMenuRating(if (menuRating == SongRating.DISLIKED) SongRating.NEUTRAL else SongRating.DISLIKED)
                },
            )
        }
    }

    RiffMenuDivider()

    val bottomSheetPageState = LocalBottomSheetPageState.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost
    val isPodcast = song.album?.let { !it.id.startsWith("MPREb_") } ?: false
    val destinationAndLibraryItems =
        buildList {
            if (artists.isNotEmpty() && !isPodcast) {
                add(
                    Material3MenuItemData(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = stringResource(R.string.view_artist), maxLines = 1)
                                Spacer(Modifier.width(8.dp))
                                RiffDestinationPill(
                                    imageUrl = artistThumbnailUrl.takeIf { artists.size == 1 },
                                    iconRes = R.drawable.tabler_ic_users.takeIf { artists.size > 1 },
                                    label = if (artists.size > 1) "${artists.size} Artists"
                                        else song.artists.joinToString { it.name }.truncateMenuLabel(28),
                                    circularImage = true,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                        },
                        icon = { Icon(painterResource(R.drawable.tabler_ic_user), null, Modifier.size(24.dp)) },
                        trailingContent = {
                            Icon(
                                painterResource(R.drawable.tabler_ic_arrow_up_right),
                                null,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            if (artists.size == 1) {
                                navController.navigate("artist/${artists[0].id}")
                                onDismiss()
                            } else {
                                showSelectArtistDialog = true
                            }
                        },
                    ),
                )
            }
            song.album?.let { album ->
                add(
                    Material3MenuItemData(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(if (isPodcast) R.string.view_podcast else R.string.view_album),
                                    maxLines = 1,
                                )
                                Spacer(Modifier.width(8.dp))
                                RiffDestinationPill(
                                    imageUrl = song.thumbnail,
                                    label = album.name.truncateMenuLabel(28),
                                    circularImage = true,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                        },
                        icon = { Icon(painterResource(R.drawable.tabler_ic_disc), null, Modifier.size(24.dp)) },
                        trailingContent = {
                            Icon(
                                painterResource(R.drawable.tabler_ic_arrow_up_right),
                                null,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            navController.navigate(if (isPodcast) "online_podcast/${album.id}" else "album/${album.id}")
                            onDismiss()
                        },
                    ),
                )
            }
            if (!isGuest) {
                add(
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.add_to_queue)) },
                        description = { Text(text = stringResource(R.string.add_to_queue_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            playerConnection.addToQueue(song.toMediaItem())
                            onDismiss()
                        },
                    ),
                )
            }
            add(
                Material3MenuItemData(
                    title = {
                        Text(
                            if (effectiveLibraryState == true) stringResource(R.string.remove_from_library)
                            else stringResource(R.string.add_to_library),
                        )
                    },
                    icon = {
                        Icon(
                            painterResource(
                                if (effectiveLibraryState == true) R.drawable.tabler_ic_circle_check_filled
                                else R.drawable.tabler_ic_library_plus,
                            ),
                            null,
                            Modifier.size(24.dp),
                        )
                    },
                    onClick = {
                        val wasInLibrary = effectiveLibraryState == true
                        remoteLibraryState = !wasInLibrary
                        coroutineScope.launch {
                            val result = syncUtils.setSongLibraryNow(song.id, !wasInLibrary)
                            if (result == RemoteMutationResult.SUCCESS) {
                                librarySong?.song?.let { localSong ->
                                    database.update(
                                        localSong.copy(inLibrary = if (!wasInLibrary) LocalDateTime.now() else null),
                                    )
                                }
                            } else {
                                remoteLibraryState = wasInLibrary
                            }
                        }
                    },
                ),
            )
            add(
                Material3MenuItemData(
                    title = {
                        Text(if (isPinned) stringResource(R.string.unpin_from_speed_dial) else stringResource(R.string.pin_to_speed_dial))
                    },
                    icon = {
                        Icon(
                            painterResource(if (isPinned) R.drawable.tabler_ic_pinned_off else R.drawable.tabler_ic_pin),
                            null,
                            Modifier.size(24.dp),
                        )
                    },
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            if (isPinned) database.speedDialDao.delete(song.id)
                            else database.speedDialDao.insert(SpeedDialItem.fromYTItem(song))
                        }
                        onDismiss()
                    },
                ),
            )
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()),
    ) {
        item {
            NewActionGrid(
                actions = listOfNotNull(
                    if (!isGuest) {
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.tabler_ic_radio),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            text = stringResource(R.string.start_radio),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            onClick = {
                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                                onDismiss()
                            }
                        )
                    } else null,
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.tabler_ic_playlist_add),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        text = stringResource(R.string.add_to_playlist),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            showChoosePlaylistDialog = true
                        }
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.tabler_ic_player_skip_forward),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        text = stringResource(R.string.play_next),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            playerConnection.playNext(song.copy(thumbnail = song.thumbnail.resize(544,544)).toMediaItem())
                            onDismiss()
                        }
                    )
                ),
                columns = if (isGuest) 2 else 3,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
        }

        item {
            Material3MenuGroup(
                items = listOfNotNull(
                    if (listenTogetherManager != null && listenTogetherManager.isInRoom && !listenTogetherManager.isHost) {
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.suggest_to_host)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.queue_music),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                val durationMs = if (song.duration != null && song.duration!! > 0) song.duration!! * 1000L else 180000L
                                val trackInfo = com.metrolist.music.listentogether.TrackInfo(
                                    id = song.id,
                                    title = song.title,
                                    artist = artists.joinToString(", ") { it.name },
                                    album = song.album?.name,
                                    duration = durationMs,
                                    thumbnail = song.thumbnail
                                )
                                listenTogetherManager.suggestTrack(trackInfo)
                                onDismiss()
                            }
                        )
                    } else null,
                    *destinationAndLibraryItems.toTypedArray(),
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.share)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, song.shareLink)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                            onDismiss()
                        },
                    ),
                )
            )
        }

        item { RiffMenuDivider() }

        item {
            Material3MenuGroup(
                items = buildList {
                    // Save/Remove for Later option for podcast episodes
                    if (song.isEpisode) {
                        if (song.setVideoId != null) {
                            // Episode is saved - show remove option
                            add(
                                Material3MenuItemData(
                                    title = { Text(text = stringResource(R.string.remove_episode_from_saved)) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.remove),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        // Update local database first (optimistic update)
                                        database.query {
                                            librarySong?.song?.let { update(it.copy(inLibrary = null)) }
                                        }
                                        // Sync with YouTube (handles login check internally)
                                        syncUtils.saveEpisode(song.id, false, song.setVideoId)
                                        onDismiss()
                                    }
                                )
                            )
                        } else {
                            // Episode not saved - show save option
                            add(
                                Material3MenuItemData(
                                    title = { Text(text = stringResource(R.string.save_episode_for_later)) },
                                    description = { Text(text = stringResource(R.string.save_episode_for_later_desc)) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.playlist_add),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        // Update local database first (optimistic update)
                                        database.query {
                                            if (librarySong != null) {
                                                update(librarySong!!.song.copy(inLibrary = java.time.LocalDateTime.now()))
                                            } else {
                                                insert(song.toMediaMetadata().toSongEntity().copy(inLibrary = java.time.LocalDateTime.now(), isEpisode = true))
                                            }
                                        }
                                        // Sync with YouTube (handles login check internally)
                                        syncUtils.saveEpisode(song.id, true, null)
                                        onDismiss()
                                    }
                                )
                            )
                        }
                    }
                    if (song.historyRemoveToken != null) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.remove_from_history)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    coroutineScope.launch {
                                        Timber.d("[HISTORY_REMOVE] Removing song ${song.id} from YTM history")
                                        YouTube.feedback(listOf(song.historyRemoveToken!!))
                                            .onSuccess {
                                                Timber.d("[HISTORY_REMOVE] Successfully removed from YTM history")
                                            }
                                            .onFailure { e ->
                                                Timber.e(e, "[HISTORY_REMOVE] Failed to remove from YTM history")
                                            }
                                        delay(500)
                                        onHistoryRemoved()
                                        onDismiss()
                                    }
                                }
                            )
                        )
                    }
                }
            )
        }

        if (song.isEpisode || song.historyRemoveToken != null) {
            item { RiffMenuDivider() }
        }

        item {
            Material3MenuGroup(
                items = listOf(
                    when (download?.state) {
                        Download.STATE_COMPLETED -> {
                            Material3MenuItemData(
                                title = {
                                    Text(
                                        text = stringResource(R.string.remove_download)
                                    )
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.offline),
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    DownloadService.sendRemoveDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        song.id,
                                        false,
                                    )
                                }
                            )
                        }
                        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.downloading)) },
                                icon = {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                },
                                onClick = {
                                    DownloadService.sendRemoveDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        song.id,
                                        false,
                                    )
                                }
                            )
                        }
                        else -> {
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.action_download)) },
                                description = { Text(text = stringResource(R.string.download_desc)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.download),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    database.transaction {
                                        insert(song.toMediaMetadata())
                                    }
                                    val downloadRequest = DownloadRequest
                                        .Builder(song.id, song.id.toUri())
                                        .setCustomCacheKey(song.id)
                                        .setData(song.title.toByteArray())
                                        .build()
                                    DownloadService.sendAddDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        downloadRequest,
                                        false,
                                    )
                                }
                            )
                        }
                    }
                )
            )
        }

        item { RiffMenuDivider() }

        item {
            Material3MenuGroup(
                items =
                    listOf(
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.details)) },
                            description = { Text(text = stringResource(R.string.details_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.tabler_ic_info_circle),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onDismiss()
                                bottomSheetPageState.show { ShowMediaInfo(song.id) }
                            },
                        ),
                    ),
            )
        }
    }
}

@Composable
internal fun RiffMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

@Composable
internal fun RiffDestinationPill(
    imageUrl: String?,
    label: String,
    circularImage: Boolean,
    modifier: Modifier = Modifier,
    @androidx.annotation.DrawableRes iconRes: Int? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 3.dp, end = 9.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp).padding(2.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    placeholder = painterResource(if (circularImage) R.drawable.artist else R.drawable.album),
                    error = painterResource(if (circularImage) R.drawable.artist else R.drawable.album),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(if (circularImage) CircleShape else RoundedCornerShape(5.dp)),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = RiffSubtextWeight,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ColumnScope.item(content: @Composable ColumnScope.() -> Unit) = content()

internal fun String.truncateMenuLabel(maxCharacters: Int): String =
    if (length <= maxCharacters) this else take(maxCharacters).trimEnd() + "…"
