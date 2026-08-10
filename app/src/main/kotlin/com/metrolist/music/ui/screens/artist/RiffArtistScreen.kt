package com.metrolist.music.ui.screens.artist

import android.app.Activity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import kotlin.math.ceil
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.RiffPlayingBars
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.theme.riffControlColors
import com.metrolist.music.ui.theme.RiffSubtextWeight
import com.metrolist.music.viewmodels.ArtistViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val page = viewModel.artistPage
    val localArtist by viewModel.libraryArtist.collectAsStateWithLifecycle()
    val savedAlbumIds by viewModel.savedAlbumIds.collectAsStateWithLifecycle()
    val likedSongIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val albumSavedOverrides by viewModel.albumSavedOverrides.collectAsStateWithLifecycle()
    val libraryAlbumStateLoaded by viewModel.libraryAlbumStateLoaded.collectAsStateWithLifecycle()
    val subscribed by viewModel.isChannelSubscribed.collectAsStateWithLifecycle()
    val mutationError by viewModel.mutationError.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current ?: return
    val playingId by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val menuState = LocalMenuState.current
    val context = LocalContext.current
    val view = LocalView.current
    val listState = rememberLazyListState()

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
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    val songSection = remember(page?.sections) {
        page?.sections?.firstOrNull { section -> section.items.any { it is SongItem } }
    }
    val popularSongs = remember(songSection) { songSection?.items?.filterIsInstance<SongItem>().orEmpty() }
    val popularSongPages = remember(popularSongs) { popularSongs.take(16).chunked(4) }
    val albumSections = remember(page?.sections) {
        page?.sections?.filter { section -> section.items.any { it is AlbumItem } }.orEmpty()
    }
    val albumsSection = remember(albumSections) {
        albumSections.firstOrNull { section ->
            !section.title.contains("new", true) &&
                !section.title.contains("single", true) &&
                !section.title.contains("ep", true)
        }
    }
    val singlesSection = remember(albumSections) {
        albumSections.firstOrNull { section ->
            section.title.contains("single", true) || section.title.contains("ep", true)
        }
    }
    val albums = remember(albumsSection) { albumsSection?.items?.filterIsInstance<AlbumItem>().orEmpty() }
    val singles = remember(singlesSection) { singlesSection?.items?.filterIsInstance<AlbumItem>().orEmpty() }
    val related = remember(page?.sections) {
        page?.sections?.flatMap { it.items.filterIsInstance<ArtistItem>() }?.distinctBy { it.id }.orEmpty()
    }
    val newRelease = remember(albumSections) {
        albumSections.firstOrNull { it.title.contains("new", true) }
            ?.items?.filterIsInstance<AlbumItem>()?.firstOrNull()
            ?: albumSections.firstOrNull()?.items?.filterIsInstance<AlbumItem>()?.firstOrNull()
    }
    LaunchedEffect(newRelease?.id, libraryAlbumStateLoaded) {
        newRelease?.let(viewModel::refreshAlbumSavedState)
    }
    // Hoisted to screen scope (not inside the LazyColumn item) so it's fetched once and doesn't
    // flicker/re-fetch every time the Latest Release card scrolls back into view. If the featured
    // release has no year it may be an unreleased pre-save — look up its release date (cached).
    var newReleaseTs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(newRelease?.id) {
        val rel = newRelease
        newReleaseTs = if (rel != null && rel.year == null) {
            runCatching { YouTube.album(rel.id).getOrNull()?.releaseTimestampMs }.getOrNull()
        } else {
            null
        }
    }
    val compactHeaderVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 330 }
    }

    LaunchedEffect(mutationError) {
        if (mutationError) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.riff_library_sync_failed),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            viewModel.clearMutationError()
        }
    }

    fun playPopular(startIndex: Int = 0) {
        if (popularSongs.isNotEmpty()) {
            playerConnection.playQueue(
                ListQueue(
                    title = page?.artist?.title ?: localArtist?.artist?.name.orEmpty(),
                    items = popularSongs.map { it.toMediaItem() },
                    startIndex = startIndex.coerceIn(popularSongs.indices),
                ),
            )
        } else {
            page?.artist?.shuffleEndpoint?.let { playerConnection.playQueue(YouTubeQueue(it)) }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(38.dp),
        ) {
            item("hero") {
                ArtistHero(
                    name = page?.artist?.title ?: localArtist?.artist?.name.orEmpty(),
                    imageUrl = page?.artist?.thumbnail ?: localArtist?.artist?.thumbnailUrl,
                    subscribers = page?.subscriberCountText,
                    monthlyListeners = page?.monthlyListenerCount,
                    subscribed = subscribed,
                    onBack = navController::navigateUp,
                    onSubscribe = viewModel::toggleChannelSubscription,
                    onRadio = { page?.artist?.radioEndpoint?.let { playerConnection.playQueue(YouTubeQueue(it)) } },
                    onPlay = ::playPopular,
                )
            }

            newRelease?.let { release ->
                item("new_release") {
                    RiffSection(heading = stringResource(R.string.riff_new_release)) {
                        NewReleaseRow(
                            album = release,
                            releaseTimestampMs = newReleaseTs,
                            saved = listOf(release.id, release.playlistId, release.libraryIdentityKey())
                                .firstNotNullOfOrNull { albumSavedOverrides[it] }
                                ?: if (libraryAlbumStateLoaded) {
                                    release.id in savedAlbumIds ||
                                        release.playlistId in savedAlbumIds ||
                                        release.libraryIdentityKey() in savedAlbumIds
                                } else {
                                    null
                                },
                            onOpen = { navController.navigate("album/${release.id}") },
                            onSave = { viewModel.toggleAlbumSaved(release) },
                        )
                    }
                }
            }

            if (popularSongs.isNotEmpty()) {
                item("popular") {
                    val pagerState = rememberPagerState(pageCount = { popularSongPages.size })
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        SectionHeading(stringResource(R.string.riff_popular_songs))
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                        ) { pageIndex ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                popularSongPages[pageIndex].forEachIndexed { itemIndex, song ->
                                    val songIndex = pageIndex * 4 + itemIndex
                                    PopularSongRow(
                                        song = song,
                                        liked = song.id in likedSongIds,
                                        active = playingId?.id == song.id,
                                        playing = isPlaying,
                                        onClick = { playPopular(songIndex) },
                                        onMore = { menuState.show { YouTubeSongMenu(song, menuState::dismiss) } },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (albums.isNotEmpty()) {
                item("albums") {
                    ArtistCarousel(
                        title = stringResource(R.string.albums),
                        albums = albums,
                        onShowMore = albumsSection?.moreEndpoint?.let { endpoint ->
                            {
                                navController.navigate(
                                    "artist/${viewModel.artistId}/items?browseId=${android.net.Uri.encode(endpoint.browseId)}&params=${android.net.Uri.encode(endpoint.params.orEmpty())}",
                                )
                            }
                        },
                        onAlbum = { navController.navigate("album/${it.id}") },
                    )
                }
            }

            if (singles.isNotEmpty()) {
                item("singles") {
                    ArtistCarousel(
                        title = stringResource(R.string.riff_singles_eps),
                        albums = singles,
                        onShowMore = singlesSection?.moreEndpoint?.let { endpoint ->
                            {
                                navController.navigate(
                                    "artist/${viewModel.artistId}/items?browseId=${android.net.Uri.encode(endpoint.browseId)}&params=${android.net.Uri.encode(endpoint.params.orEmpty())}",
                                )
                            }
                        },
                        onAlbum = { navController.navigate("album/${it.id}") },
                    )
                }
            }

            if (related.isNotEmpty()) {
                item("related") {
                    RelatedArtists(related) { navController.navigate("artist/${it.id}") }
                }
            }

            item("about") {
                ArtistStatsCard(
                    description = page?.description,
                    subscribers = page?.subscriberCountText,
                    listeners = page?.monthlyListenerCount,
                )
            }
        }

        AnimatedVisibility(
            visible = compactHeaderVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .96f), shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 38.dp, start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(R.drawable.tabler_ic_chevron_left_outline), null)
                    }
                    Text(
                        page?.artist?.title ?: localArtist?.artist?.name.orEmpty(),
                        Modifier.weight(1f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = ::playPopular) {
                        Icon(painterResource(R.drawable.tabler_ic_player_play_filled), stringResource(R.string.riff_play_artist))
                    }
                }
            }
        }
    }
}

private fun AlbumItem.libraryIdentityKey(): String =
    "album:${title.lowercase().filter(Char::isLetterOrDigit)}:${year?.toString().orEmpty()}"

@Composable
private fun ArtistHero(
    name: String,
    imageUrl: String?,
    subscribers: String?,
    monthlyListeners: String?,
    subscribed: Boolean,
    onBack: () -> Unit,
    onSubscribe: () -> Unit,
    onRadio: () -> Unit,
    onPlay: () -> Unit,
) {
    val controls = riffControlColors()
    Box(Modifier.fillMaxWidth().height(430.dp)) {
        val heroTextColor = MaterialTheme.colorScheme.onBackground
        AsyncImage(imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(.68f),
                        .22f to Color.Transparent,
                        .43f to Color.Transparent,
                        .66f to MaterialTheme.colorScheme.background.copy(alpha = .48f),
                        .80f to MaterialTheme.colorScheme.background.copy(alpha = .92f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                    startY = 0f,
                ),
            ),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 58.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIcon(R.drawable.tabler_ic_chevron_left_outline, onBack)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onSubscribe,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = controls.accent,
                    contentColor = controls.onAccent,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(stringResource(if (subscribed) R.string.subscribed else R.string.subscribe), fontWeight = FontWeight.Medium)
                Box(
                    Modifier
                        .padding(horizontal = 9.dp)
                        .size(4.dp)
                        .background(controls.onAccent.copy(.55f), CircleShape),
                )
                Text(
                    subscribers?.substringBefore(' ')?.ifBlank { null }
                        ?: stringResource(R.string.riff_unknown_count),
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onRadio,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = controls.accent,
                    contentColor = controls.onAccent,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(painterResource(R.drawable.tabler_ic_radio), null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.riff_radio), fontWeight = FontWeight.Medium)
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 104.dp, bottom = 6.dp)) {
            Text(
                name,
                color = heroTextColor,
                fontSize = 40.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val stats = listOfNotNull(subscribers, monthlyListeners).joinToString(" • ")
            if (stats.isNotEmpty()) Text(stats, color = heroTextColor.copy(.72f), fontSize = 14.sp, fontWeight = RiffSubtextWeight)
        }
        Surface(
            onClick = onPlay,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).size(66.dp),
            shape = CircleShape,
            color = controls.accent,
            contentColor = controls.onAccent,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.tabler_ic_player_play_filled), stringResource(R.string.riff_play_artist), Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun CircleIcon(icon: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.Black.copy(.48f), contentColor = Color.White) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(painterResource(icon), null, Modifier.size(22.dp)) }
    }
}

@Composable
private fun RiffSection(heading: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(heading, horizontalPadding = 0.dp)
        content()
    }
}

@Composable
private fun SectionHeading(title: String, horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp, trailing: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        trailing?.let { Text(stringResource(R.string.riff_show_more), Modifier.clickable(onClick = it).padding(8.dp), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun NewReleaseRow(
    album: AlbumItem,
    saved: Boolean?,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    releaseTimestampMs: Long? = null,
) {
    val controls = riffControlColors()
    val countdownText = releaseTimestampMs?.let { ts ->
        val days = ceil((ts - System.currentTimeMillis()) / 86_400_000.0).toInt()
        when {
            days <= 0 -> stringResource(R.string.riff_releases_today)
            days == 1 -> stringResource(R.string.riff_releases_tomorrow)
            else -> stringResource(R.string.riff_releases_in_days, days)
        }
    }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(album.thumbnail, null, Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(album.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (countdownText != null) {
                Row(
                    Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    PresaveBadge()
                    Text(countdownText, color = controls.accent, fontSize = 13.sp, fontWeight = RiffSubtextWeight)
                }
            } else {
                Text(
                    listOfNotNull(album.artists?.joinToString { it.name }, album.year?.toString()).joinToString(" \u2022 "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = RiffSubtextWeight,
                )
            }
        }
        Surface(onClick = onSave, enabled = saved != null, shape = CircleShape, color = controls.secondary, contentColor = controls.onSecondary) {
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                if (saved == null) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = controls.onSecondary)
                } else {
                    Icon(
                        painterResource(if (saved) R.drawable.tabler_ic_circle_check_filled else R.drawable.tabler_ic_plus_outline),
                        stringResource(if (saved) R.string.riff_remove_release else R.string.riff_save_release),
                        tint = if (saved) controls.accent else controls.onSecondary,
                    )
                }
            }
        }
      }
    }
}

@Composable
private fun PopularSongRow(song: SongItem, liked: Boolean, active: Boolean, playing: Boolean, onClick: () -> Unit, onMore: () -> Unit) {
    val controls = riffControlColors()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(song.thumbnail, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    song.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
                    song.viewsText?.takeIf { it.isNotBlank() },
                ).joinToString(" \u2022 "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = RiffSubtextWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (active) RiffPlayingBars(animated = playing, color = MaterialTheme.colorScheme.onSurface)
        if (liked) {
            Icon(
                painterResource(R.drawable.tabler_ic_thumb_up_filled),
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(17.dp),
                tint = controls.accent,
            )
        }
        IconButton(onClick = onMore) { Icon(painterResource(R.drawable.tabler_ic_dots_vertical_outline), null) }
    }
}

@Composable
private fun ArtistCarousel(title: String, albums: List<AlbumItem>, onShowMore: (() -> Unit)?, onAlbum: (AlbumItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(title, trailing = onShowMore)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(albums.take(12), key = { it.id }) { album ->
                Column(Modifier.width(140.dp).clickable { onAlbum(album) }) {
                    AsyncImage(album.thumbnail, null, Modifier.size(140.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    Text(album.title, Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // A discography album with no year is an unreleased pre-save — badge it where the
                    // year would go.
                    if (album.year == null) {
                        PresaveBadge(Modifier.padding(top = 3.dp))
                    } else {
                        Text(album.year.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = RiffSubtextWeight)
                    }
                }
            }
        }
    }
}

/** Small rounded-rectangle "Presave" badge for unreleased albums. */
@Composable
private fun PresaveBadge(modifier: Modifier = Modifier) {
    val controls = riffControlColors()
    Box(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(controls.accent)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            stringResource(R.string.riff_presave_badge),
            color = controls.onAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun RelatedArtists(artists: List<ArtistItem>, onArtist: (ArtistItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(stringResource(R.string.riff_related_artists))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(artists.take(12), key = { it.id }) { artist ->
                Column(Modifier.width(104.dp).clickable { onArtist(artist) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(artist.thumbnail, null, Modifier.size(104.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Text(
                        artist.title,
                        Modifier.padding(top = 8.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistStatsCard(description: String?, subscribers: String?, listeners: String?) {
    val controls = riffControlColors()
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.about_artist), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = RiffSubtextWeight,
                    lineHeight = 20.sp,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(
                    value = metricNumber(subscribers),
                    label = stringResource(R.string.riff_subscribers),
                    background = controls.nestedSurface,
                    modifier = Modifier.weight(1f),
                )
                Stat(
                    value = metricNumber(listeners),
                    label = stringResource(R.string.riff_monthly_listeners),
                    background = controls.nestedSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, background: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = background) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 15.dp)) {
            Text(value.ifBlank { stringResource(R.string.riff_unknown_count) }, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun metricNumber(value: String?): String =
    value.orEmpty()
        .trim()
        .substringBefore(' ')
        .substringBefore('\u00A0')
