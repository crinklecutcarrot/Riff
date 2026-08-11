package com.metrolist.music.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import androidx.palette.graphics.Palette
import androidx.core.graphics.ColorUtils
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.MediaInfo
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.SliderStyle
import com.metrolist.music.constants.SliderStyleKey
import com.metrolist.music.constants.SquigglySliderKey
import com.metrolist.music.constants.RiffArtworkCardKey
import com.metrolist.music.extensions.metadata
import com.metrolist.music.models.SongRating
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.PlayerSliderTrack
import com.metrolist.music.ui.component.SquigglySlider
import com.metrolist.music.ui.component.WavySlider
import com.metrolist.music.ui.menu.PlayerMenu
import com.metrolist.music.ui.theme.RiffAccent
import com.metrolist.music.ui.theme.RiffSubtextWeight
import com.metrolist.music.ui.utils.ShowMediaInfo
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class RiffPlayerTab {
    HIGHLIGHT,
    LYRICS,
}

private fun brightAlbumAccent(rgb: Int): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.68f)
    hsl[2] = hsl[2].coerceIn(0.66f, 0.74f)
    val saturated = Color(ColorUtils.HSLToColor(hsl))
    return if (saturated.luminance() < 0.46f) lerp(saturated, Color.White, 0.2f) else saturated
}

private data class RiffArtistCardContent(
    val key: String,
    val name: String,
    val imageUrl: String?,
    val description: String,
    val subscribers: String,
)

@Composable
fun RiffFullPlayer(
    playerState: BottomSheetState,
    queueState: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val metadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentFormat by playerConnection.currentFormat.collectAsStateWithLifecycle()
    val rating by playerConnection.currentSongRating.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()

    // Adjacent artwork so the album-cover swipe can preview the song coming in. Null when there's no
    // distinct neighbour (also excludes the repeat-one case where "next" is the current track).
    val prevArtworkUrl = remember(metadata?.id, canSkipPrevious) {
        runCatching {
            playerConnection.player.let { p ->
                p.previousMediaItemIndex.takeIf { it != C.INDEX_UNSET }
                    ?.let { p.getMediaItemAt(it).metadata }
                    ?.takeIf { it.id != metadata?.id }
                    ?.thumbnailUrl
            }
        }.getOrNull()
    }
    val nextArtworkUrl = remember(metadata?.id, canSkipNext) {
        runCatching {
            playerConnection.player.let { p ->
                p.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }
                    ?.let { p.getMediaItemAt(it).metadata }
                    ?.takeIf { it.id != metadata?.id }
                    ?.thumbnailUrl
            }
        }.getOrNull()
    }

    val menuState = LocalMenuState.current
    val pageState = LocalBottomSheetPageState.current
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(RiffPlayerTab.HIGHLIGHT) }
    var artworkCard by rememberPreference(RiffArtworkCardKey, true)
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var pendingSeek by remember { mutableStateOf<Long?>(null) }
    var seekReleaseJob by remember { mutableStateOf<Job?>(null) }
    val seekScope = rememberCoroutineScope()
    var mediaInfo by remember { mutableStateOf<MediaInfo?>(null) }
    var selectedArtistIndex by rememberSaveable(metadata?.id) { mutableStateOf(0) }
    var artistPages by remember(metadata?.id) { mutableStateOf<Map<String, ArtistPage>>(emptyMap()) }
    var albumBaseColor by remember { mutableStateOf(Color(0xFF182020)) }
    var albumAccentColor by remember { mutableStateOf(RiffAccent) }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val stageHeight = screenHeight - 48.dp
    val listState = rememberLazyListState()
    val compactBarThreshold = with(LocalDensity.current) { (stageHeight - 150.dp).roundToPx() }
    val showCompactBar by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > compactBarThreshold
        }
    }
    val playerDismissNestedScrollConnection =
        remember(playerState, listState) {
            object : NestedScrollConnection {
                var gestureActive = false
                var dismissGesture = false

                private fun isAtTop(): Boolean =
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero

                    if (!gestureActive) {
                        gestureActive = true
                        // Eligibility is locked when the gesture begins. Reaching the top during
                        // a scroll does not turn that same gesture into a player-dismiss gesture.
                        dismissGesture = isAtTop() && available.y > 0f
                    }

                    return if (dismissGesture) {
                        playerState.dispatchRawDelta(available.y)
                        available
                    } else {
                        Offset.Zero
                    }
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!dismissGesture) {
                        gestureActive = false
                        return Velocity.Zero
                    }

                    playerState.performFling(-available.y, null)
                    gestureActive = false
                    dismissGesture = false
                    return available
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    gestureActive = false
                    dismissGesture = false
                    return Velocity.Zero
                }
            }
        }
    val playerHeaderDragModifier =
        Modifier.pointerInput(playerState) {
            val velocityTracker = VelocityTracker()
            detectVerticalDragGestures(
                onDragStart = { velocityTracker.resetTracking() },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPointerInputChange(change)
                    playerState.dispatchRawDelta(dragAmount)
                },
                onDragCancel = {
                    velocityTracker.resetTracking()
                    playerState.expandSoft()
                },
                onDragEnd = {
                    val velocity = -velocityTracker.calculateVelocity().y
                    velocityTracker.resetTracking()
                    playerState.performFling(velocity, null)
                },
            )
        }
    LaunchedEffect(metadata?.id) {
        mediaInfo = metadata?.id?.let { YouTube.getMediaInfo(it).getOrNull() }
    }

    val artists = metadata?.artists.orEmpty()
    val selectedArtist = artists.getOrNull(selectedArtistIndex)
    val selectedArtistPage = selectedArtist?.id?.let(artistPages::get)

    LaunchedEffect(metadata?.id, artists.size) {
        if (selectedArtistIndex !in artists.indices) selectedArtistIndex = 0
    }

    LaunchedEffect(selectedArtist?.id) {
        val artistId = selectedArtist?.id ?: return@LaunchedEffect
        if (artistId in artistPages) return@LaunchedEffect
        YouTube.artist(artistId).getOrNull()?.let { page ->
            artistPages = artistPages + (artistId to page)
        }
    }

    LaunchedEffect(metadata?.id, metadata?.thumbnailUrl) {
        albumBaseColor = Color(0xFF182020)
        albumAccentColor = RiffAccent
        val artworkUrl = metadata?.thumbnailUrl ?: return@LaunchedEffect
        val request =
            ImageRequest.Builder(context)
                .data(artworkUrl)
                .size(160, 160)
                .allowHardware(false)
                .build()
        val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
            ?: return@LaunchedEffect
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val accentSource =
            palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
        accentSource?.let { albumAccentColor = brightAlbumAccent(it) }
        val extracted =
            palette.darkVibrantSwatch?.rgb
                ?: palette.darkMutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: return@LaunchedEffect
        albumBaseColor = lerp(Color(extracted), Color.Black, 0.58f)
    }

    LaunchedEffect(metadata?.id, isPlaying) {
        duration =
            metadata?.duration?.takeIf { it > 0 }?.toLong()?.times(1000L)
                ?: runCatching { playerConnection.player.duration }.getOrDefault(0L)
        while (isActive) {
            if (pendingSeek == null) {
                position = runCatching { playerConnection.player.currentPosition }.getOrDefault(0L)
                runCatching { playerConnection.player.duration }
                    .getOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { duration = it }
            }
            delay(if (isPlaying) 150L else 500L)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .blur((8f * queueState.progress).dp)
                .background(albumBaseColor),
    ) {
        AsyncImage(
            model = metadata?.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.18f
                        scaleY = 1.18f
                    }
                    .blur(54.dp)
                    .alpha(0.5f),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.48f),
                        0.28f to albumBaseColor.copy(alpha = 0.48f),
                        0.62f to albumBaseColor.copy(alpha = 0.82f),
                        1f to albumBaseColor,
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(playerDismissNestedScrollConnection),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                RiffHighlightStage(
                    screenWidth = screenWidth,
                    screenHeight = stageHeight,
                    mediaMetadata = metadata,
                    artworkCard = artworkCard,
                    artworkUrl = metadata?.thumbnailUrl,
                    title = metadata?.title.orEmpty(),
                    artists = metadata?.artists.orEmpty(),
                    rating = rating,
                    position = pendingSeek ?: position,
                    duration = duration,
                    isPlaying = isPlaying,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    albumBaseColor = albumBaseColor,
                    albumAccentColor = albumAccentColor,
                    selectedTab = selectedTab,
                    headerDragModifier = playerHeaderDragModifier,
                    onSelectTab = { selectedTab = it },
                    onCollapse = { playerState.collapseSoft() },
                    onMore = {
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = metadata,
                                playerBottomSheetState = playerState,
                                onShowDetailsDialog = {
                                    metadata?.id?.let { id -> pageState.show { ShowMediaInfo(id) } }
                                },
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                    onToggleArtwork = { artworkCard = !artworkCard },
                    prevArtworkUrl = prevArtworkUrl,
                    nextArtworkUrl = nextArtworkUrl,
                    onSwipeToPrevious = { runCatching { playerConnection.player.seekToPreviousMediaItem() } },
                    onSwipeToNext = { runCatching { playerConnection.player.seekToNextMediaItem() } },
                    onArtistClick = { id ->
                        id?.let {
                            navController.navigate("artist/$id")
                            playerState.collapseSoft()
                        }
                    },
                    onAlbumClick = {
                        metadata?.album?.id?.let { id ->
                            navController.navigate("album/$id")
                            playerState.collapseSoft()
                        }
                    },
                    onLike = playerConnection::toggleLike,
                    onDislike = playerConnection::toggleDislike,
                    onSeek = {
                        seekReleaseJob?.cancel()
                        pendingSeek = it
                    },
                    onSeekFinished = {
                        pendingSeek?.let { target ->
                            playerConnection.player.seekTo(target)
                            position = target
                            seekReleaseJob?.cancel()
                            seekReleaseJob =
                                seekScope.launch {
                                    // Keep the tapped position on screen while Media3 catches up.
                                    // Clearing it synchronously exposes the old polled position and
                                    // makes a responsive tap look delayed or as if it snapped back.
                                    delay(350L)
                                    if (pendingSeek == target) pendingSeek = null
                                }
                        }
                    },
                    onShuffle = { playerConnection.player.shuffleModeEnabled = !shuffleEnabled },
                    onPrevious = playerConnection::seekToPrevious,
                    onPlayPause = playerConnection::togglePlayPause,
                    onNext = playerConnection::seekToNext,
                    onRepeat = {
                        playerConnection.player.repeatMode =
                            when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                    },
                    onOpenQueue = { queueState.expandSoft() },
                )
            }

            item {
                Box(Modifier.fillMaxWidth().background(Color.Transparent)) {
                    RiffArtistCard(
                        artistName = selectedArtistPage?.artist?.title ?: selectedArtist?.name.orEmpty(),
                        imageUrl = selectedArtistPage?.artist?.thumbnail
                            ?: mediaInfo?.authorThumbnail.takeIf { selectedArtistIndex == 0 },
                        description = selectedArtistPage?.description.orEmpty(),
                        subscribers = selectedArtistPage?.subscriberCountText
                            ?: mediaInfo?.subscribers.orEmpty().takeIf { selectedArtistIndex == 0 }.orEmpty(),
                        artistCount = artists.size,
                        onPrevious = {
                            if (artists.isNotEmpty()) {
                                selectedArtistIndex = (selectedArtistIndex - 1 + artists.size) % artists.size
                            }
                        },
                        onNext = {
                            if (artists.isNotEmpty()) {
                                selectedArtistIndex = (selectedArtistIndex + 1) % artists.size
                            }
                        },
                        onClick = {
                            selectedArtist?.id?.let { id ->
                                navController.navigate("artist/$id")
                                playerState.collapseSoft()
                            }
                        },
                    )
                }
            }

            item {
                Box(Modifier.fillMaxWidth().background(Color.Transparent)) {
                    RiffSongMetadataCard(
                    info = mediaInfo,
                    bitrate = currentFormat?.bitrate,
                    codecs = currentFormat?.codecs,
                    fallbackDescription =
                        listOfNotNull(
                            metadata?.title,
                            metadata?.artists?.joinToString { it.name },
                            metadata?.album?.title,
                        ).joinToString(" • "),
                    )
                }
            }

            item {
                Box(Modifier.fillMaxWidth().background(Color.Transparent)) {
                    Spacer(
                        Modifier
                            .height(28.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showCompactBar,
            enter = slideInVertically(tween(260)) { -it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            RiffCompactPlayerBar(
                title = metadata?.title.orEmpty(),
                artist = metadata?.artists?.joinToString { it.name }.orEmpty(),
                isPlaying = isPlaying,
                canSkipNext = canSkipNext,
                albumBaseColor = albumBaseColor,
                onPlayPause = playerConnection::togglePlayPause,
                onNext = playerConnection::seekToNext,
            )
        }
    }
}

@Composable
private fun RiffLyricsStage(
    screenHeight: androidx.compose.ui.unit.Dp,
    mediaMetadata: MediaMetadata?,
    title: String,
    artists: List<MediaMetadata.Artist>,
    rating: SongRating,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    selectedTab: RiffPlayerTab,
    onSelectTab: (RiffPlayerTab) -> Unit,
    onCollapse: () -> Unit,
    onMore: () -> Unit,
    onArtistClick: (String?) -> Unit,
    onAlbumClick: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(screenHeight)) {
        RiffPlayerHeader(selectedTab, RiffAccent, onSelectTab, onCollapse, onMore)

        RiffTrackIdentityWithActions(
            title = title,
            artists = artists,
            thumbnailUrl = mediaMetadata?.thumbnailUrl,
            showThumbnail = false,
            rating = rating,
            onArtistClick = onArtistClick,
            onAlbumClick = onAlbumClick,
            onLike = onLike,
            onDislike = onDislike,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp),
        )

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(top = 164.dp)
                    .height((screenHeight - 472.dp).coerceIn(260.dp, 430.dp))
                    .clip(RoundedCornerShape(18.dp)),
        ) {
            InlineLyricsView(
                mediaMetadata = mediaMetadata,
                showLyrics = true,
                positionProvider = { position },
                lyricsAccentOverride = Color.White,
            )
        }

        RiffTransport(
            position = position,
            duration = duration,
            isPlaying = isPlaying,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            activeColor = RiffAccent,
            onSeek = onSeek,
            onSeekFinished = onSeekFinished,
            onShuffle = onShuffle,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onRepeat = onRepeat,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 118.dp),
        )

        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 42.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RiffPillButton(
                icon = R.drawable.tabler_ic_adjustments_outline,
                label = stringResource(R.string.advanced),
                onClick = onOpenAdvanced,
            )
            RiffPillButton(
                icon = R.drawable.tabler_ic_list_outline,
                label = stringResource(R.string.riff_queue_list),
                onClick = onOpenQueue,
            )
        }
    }
}

@Composable
private fun RiffHighlightStage(
    screenWidth: androidx.compose.ui.unit.Dp,
    screenHeight: androidx.compose.ui.unit.Dp,
    mediaMetadata: MediaMetadata?,
    artworkCard: Boolean,
    artworkUrl: String?,
    title: String,
    artists: List<MediaMetadata.Artist>,
    rating: SongRating,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    albumBaseColor: Color,
    albumAccentColor: Color,
    selectedTab: RiffPlayerTab,
    headerDragModifier: Modifier,
    onSelectTab: (RiffPlayerTab) -> Unit,
    onCollapse: () -> Unit,
    onMore: () -> Unit,
    onToggleArtwork: () -> Unit,
    prevArtworkUrl: String?,
    nextArtworkUrl: String?,
    onSwipeToPrevious: () -> Unit,
    onSwipeToNext: () -> Unit,
    onArtistClick: (String?) -> Unit,
    onAlbumClick: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val cardSize = (screenWidth - 44.dp).coerceAtMost(430.dp)
    val lyricsTop = 124.dp
    val lyricsHeight = (screenHeight - 478.dp).coerceIn(260.dp, 420.dp)
    val artworkLeft by animateDpAsState(
        if (artworkCard) 22.dp else 0.dp,
        tween(475),
        label = "riffArtworkLeft",
    )
    val artworkTop by animateDpAsState(
        if (artworkCard) 124.dp else 0.dp,
        tween(475),
        label = "riffArtworkTop",
    )
    val artworkWidth by animateDpAsState(
        if (artworkCard) cardSize else screenWidth,
        tween(475),
        label = "riffArtworkWidth",
    )
    val artworkHeight by animateDpAsState(
        if (artworkCard) cardSize else screenHeight * 0.72f,
        tween(475),
        label = "riffArtworkHeight",
    )
    val artworkRadius by animateDpAsState(
        if (artworkCard) 14.dp else 0.dp,
        tween(475),
        label = "riffArtworkRadius",
    )
    val zoomScrimAlpha by animateFloatAsState(
        targetValue = if (artworkCard) 0f else 1f,
        animationSpec = tween(360),
        label = "riffZoomScrimAlpha",
    )

    // Album-cover swipe carousel state. Neighbours travel a full screen-width so they rest fully
    // off-screen; the current cover fades/slides out while the incoming one fades/slides in, and the
    // song only commits on release (then re-centers when the track id actually changes).
    val artworkTravelPx = with(LocalDensity.current) { screenWidth.toPx() }
    val artworkSwipeScope = rememberCoroutineScope()
    val artworkOffsetX = remember { Animatable(0f) }
    val artworkSettleSpec = remember { spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) }
    val hasPrevArtwork = prevArtworkUrl != null
    val hasNextArtwork = nextArtworkUrl != null
    LaunchedEffect(mediaMetadata?.id) {
        artworkOffsetX.snapTo(0f)
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(screenHeight),
    ) {
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState == RiffPlayerTab.LYRICS) {
                    (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(320)) { -it / 3 } + fadeOut(tween(220)))
                } else {
                    (slideInHorizontally(tween(320)) { -it / 3 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(220)))
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "riffPlayerMediaTab",
        ) { tab ->
            when (tab) {
                RiffPlayerTab.HIGHLIGHT -> {
                    Box(Modifier.fillMaxSize()) {
                        val w = artworkTravelPx
                        // Previous / next covers, then the current on top, all sharing the zoom
                        // geometry and shifted by the live drag offset.
                        if (prevArtworkUrl != null) {
                            RiffHighlightArtworkLayer(
                                artworkUrl = prevArtworkUrl,
                                left = artworkLeft, top = artworkTop,
                                width = artworkWidth, height = artworkHeight, radius = artworkRadius,
                                zoomScrimAlpha = zoomScrimAlpha,
                                translationX = { artworkOffsetX.value - w },
                                layerAlpha = { (1f - kotlin.math.abs(artworkOffsetX.value - w) / w).coerceIn(0f, 1f) },
                            )
                        }
                        if (nextArtworkUrl != null) {
                            RiffHighlightArtworkLayer(
                                artworkUrl = nextArtworkUrl,
                                left = artworkLeft, top = artworkTop,
                                width = artworkWidth, height = artworkHeight, radius = artworkRadius,
                                zoomScrimAlpha = zoomScrimAlpha,
                                translationX = { artworkOffsetX.value + w },
                                layerAlpha = { (1f - kotlin.math.abs(artworkOffsetX.value + w) / w).coerceIn(0f, 1f) },
                            )
                        }
                        RiffHighlightArtworkLayer(
                            artworkUrl = artworkUrl,
                            left = artworkLeft, top = artworkTop,
                            width = artworkWidth, height = artworkHeight, radius = artworkRadius,
                            zoomScrimAlpha = zoomScrimAlpha,
                            translationX = { artworkOffsetX.value },
                            layerAlpha = { (1f - kotlin.math.abs(artworkOffsetX.value) / w).coerceIn(0f, 1f) },
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(screenHeight - 342.dp)
                                    .clickable(onClick = onToggleArtwork)
                                    .pointerInput(hasNextArtwork, hasPrevArtwork, w) {
                                        if (w <= 0f) return@pointerInput
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                val current = artworkOffsetX.value
                                                val threshold = w * 0.3f
                                                when {
                                                    current <= -threshold && hasNextArtwork ->
                                                        artworkSwipeScope.launch { artworkOffsetX.animateTo(-w, tween(240)); onSwipeToNext() }
                                                    current >= threshold && hasPrevArtwork ->
                                                        artworkSwipeScope.launch { artworkOffsetX.animateTo(w, tween(240)); onSwipeToPrevious() }
                                                    else -> artworkSwipeScope.launch { artworkOffsetX.animateTo(0f, artworkSettleSpec) }
                                                }
                                            },
                                            onDragCancel = { artworkSwipeScope.launch { artworkOffsetX.animateTo(0f, artworkSettleSpec) } },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                val min = if (hasNextArtwork) -w else 0f
                                                val max = if (hasPrevArtwork) w else 0f
                                                artworkSwipeScope.launch { artworkOffsetX.snapTo((artworkOffsetX.value + dragAmount).coerceIn(min, max)) }
                                            },
                                        )
                                    },
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .alpha(zoomScrimAlpha)
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.7f),
                                        0.62f to Color.Black.copy(alpha = 0.3f),
                                        1f to Color.Transparent,
                                    ),
                                ),
                        )
                    }
                }

                RiffPlayerTab.LYRICS -> {
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp)
                                    .offset(y = lyricsTop)
                                    .height(lyricsHeight)
                                    .graphicsLayer {
                                        clip = true
                                        shape = RoundedCornerShape(18.dp)
                                    }
                                    .clipToBounds(),
                        ) {
                            InlineLyricsView(
                                mediaMetadata = mediaMetadata,
                                showLyrics = true,
                                positionProvider = { position },
                                lyricsAccentOverride = Color.White,
                                lyricsAnchorRatioOverride = 0.35f,
                                lyricsTextSizeOverride = 30f,
                            )
                        }
                    }
                }
            }
        }

        RiffPlayerHeader(
            selectedTab = selectedTab,
            activeColor = albumAccentColor,
            onSelectTab = onSelectTab,
            onCollapse = onCollapse,
            onMore = onMore,
            modifier = headerDragModifier,
        )

        RiffTrackIdentityWithActions(
            title = title,
            artists = artists,
            thumbnailUrl = artworkUrl,
            // Show the small cover next to the title whenever the big artwork isn't on screen:
            // when it's been zoomed full-bleed (!artworkCard) OR when the lyrics tab has taken its
            // place. Reuses the same width/alpha morph animation for the lyrics transition.
            showThumbnail = !artworkCard || selectedTab == RiffPlayerTab.LYRICS,
            rating = rating,
            onArtistClick = onArtistClick,
            onAlbumClick = onAlbumClick,
            onLike = onLike,
            onDislike = onDislike,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 282.dp),
        )

        RiffTransport(
            position = position,
            duration = duration,
            isPlaying = isPlaying,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            activeColor = albumAccentColor,
            onSeek = onSeek,
            onSeekFinished = onSeekFinished,
            onShuffle = onShuffle,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onRepeat = onRepeat,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 118.dp),
        )

        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left pill: where audio is playing. Flexes into the remaining space and ellipsizes so it
            // never collides with the queue pill, which stays pinned to the right.
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                RiffOutputPill()
            }
            Spacer(Modifier.width(12.dp))
            RiffPillButton(
                icon = R.drawable.tabler_ic_list_outline,
                label = stringResource(R.string.riff_queue_list),
                onClick = onOpenQueue,
            )
        }
    }
}

/**
 * One album-cover layer of the HIGHLIGHT swipe carousel. Shares the zoom geometry (offset/size/
 * radius) and the full-bleed fade mask with its siblings; [translationX]/[layerAlpha] are read in
 * the draw phase so the drag animates without recomposing.
 */
@Composable
private fun RiffHighlightArtworkLayer(
    artworkUrl: String?,
    left: androidx.compose.ui.unit.Dp,
    top: androidx.compose.ui.unit.Dp,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
    zoomScrimAlpha: Float,
    translationX: () -> Float,
    layerAlpha: () -> Float,
) {
    Box(
        modifier =
            Modifier
                .offset(x = left, y = top)
                .width(width)
                .height(height)
                .graphicsLayer {
                    this.translationX = translationX()
                    this.alpha = layerAlpha()
                }
                .clip(RoundedCornerShape(radius))
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                0f to Color.Black,
                                0.54f to Color.Black,
                                0.78f to Color.Black.copy(alpha = 1f - (0.3f * zoomScrimAlpha)),
                                0.94f to Color.Black.copy(alpha = 1f - zoomScrimAlpha),
                                1f to Color.Black.copy(alpha = 1f - zoomScrimAlpha),
                            ),
                        blendMode = BlendMode.DstIn,
                    )
                },
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun RiffPlayerHeader(
    selectedTab: RiffPlayerTab,
    activeColor: Color,
    onSelectTab: (RiffPlayerTab) -> Unit,
    onCollapse: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RiffCircleButton(R.drawable.tabler_ic_chevron_down_outline, onCollapse)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            RiffTab(
                text = stringResource(R.string.riff_highlight),
                selected = selectedTab == RiffPlayerTab.HIGHLIGHT,
                activeColor = activeColor,
                onClick = { onSelectTab(RiffPlayerTab.HIGHLIGHT) },
            )
            RiffTab(
                text = stringResource(R.string.lyrics),
                selected = selectedTab == RiffPlayerTab.LYRICS,
                activeColor = activeColor,
                onClick = { onSelectTab(RiffPlayerTab.LYRICS) },
            )
        }
        RiffCircleButton(R.drawable.tabler_ic_dots_vertical_outline, onMore)
    }
}

@Composable
private fun RiffTab(text: String, selected: Boolean, activeColor: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) activeColor else Color.White.copy(alpha = 0.58f),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}

@Composable
private fun RiffCircleButton(icon: Int, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun RiffTrackIdentityWithActions(
    title: String,
    artists: List<MediaMetadata.Artist>,
    thumbnailUrl: String?,
    showThumbnail: Boolean,
    rating: SongRating,
    onArtistClick: (String?) -> Unit,
    onAlbumClick: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbnailWidth by animateDpAsState(
        targetValue = if (showThumbnail) 52.dp else 0.dp,
        animationSpec = tween(500),
        label = "riffMetadataArtworkWidth",
    )
    val thumbnailAlpha by animateFloatAsState(
        targetValue = if (showThumbnail) 1f else 0f,
        animationSpec = tween(360),
        label = "riffMetadataArtworkAlpha",
    )
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnailWidth > 0.dp) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .width(thumbnailWidth)
                        .height(52.dp)
                        .alpha(thumbnailAlpha)
                        .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(13.dp * thumbnailAlpha))
        }
        Column(Modifier.weight(1f)) {
            RiffScrollingTitle(
                text = title,
                fontSize = 20.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
                onClick = onAlbumClick,
            )
            Spacer(Modifier.height(4.dp))
            RiffClickableArtists(
                artists = artists,
                color = Color.White.copy(alpha = 0.66f),
                onArtistClick = onArtistClick,
            )
        }
        Spacer(Modifier.width(10.dp))
        RiffRatingControl(
            rating = rating,
            onLike = onLike,
            onDislike = onDislike,
        )
    }
}

@Composable
private fun RiffClickableArtists(
    artists: List<MediaMetadata.Artist>,
    color: Color,
    onArtistClick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artistText = remember(artists, color) {
        buildAnnotatedString {
            artists.forEachIndexed { index, artist ->
                val artistId = artist.id
                if (artistId != null) {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = artistId,
                            styles = TextLinkStyles(SpanStyle(color = color)),
                        ) { onArtistClick(artistId) },
                    ) {
                        append(artist.name)
                    }
                } else {
                    append(artist.name)
                }
                if (index != artists.lastIndex) append(", ")
            }
        }
    }
    Text(
        text = artistText,
        color = color,
        fontSize = 14.sp,
        fontWeight = RiffSubtextWeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RiffScrollingTitle(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush =
                            Brush.horizontalGradient(
                                0f to Color.White,
                                0.72f to Color.White,
                                1f to Color.Transparent,
                            ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 1400,
                    velocity = 28.dp,
                )
                .clickable(onClick = onClick),
    )
}

@Composable
private fun RiffCompactPlayerBar(
    title: String,
    artist: String,
    isPlaying: Boolean,
    canSkipNext: Boolean,
    albumBaseColor: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(lerp(albumBaseColor, Color.Black, 0.16f).copy(alpha = 0.98f))
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(64.dp)
                .padding(start = 20.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            RiffScrollingTitle(
                text = title,
                fontSize = 15.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = artist,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp,
                fontWeight = RiffSubtextWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painterResource(
                if (isPlaying) R.drawable.tabler_ic_player_pause_filled
                else R.drawable.tabler_ic_player_play_filled,
            ),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(46.dp).padding(12.dp).clickable(onClick = onPlayPause),
        )
        Icon(
            painterResource(R.drawable.tabler_ic_player_track_next_filled),
            contentDescription = null,
            tint = if (canSkipNext) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(46.dp).padding(12.dp).clickable(enabled = canSkipNext, onClick = onNext),
        )
    }
}

@Composable
private fun RiffLibraryButton(inLibrary: Boolean, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.11f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter =
                painterResource(
                    if (inLibrary) R.drawable.tabler_ic_circle_check_filled
                    else R.drawable.tabler_ic_plus_outline,
                ),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RiffTrackIdentity(
    title: String,
    artist: String,
    thumbnailUrl: String?,
    showThumbnail: Boolean,
    onArtistClick: () -> Unit = {},
) {
    val thumbnailWidth by animateDpAsState(
        targetValue = if (showThumbnail) 52.dp else 0.dp,
        animationSpec = tween(500),
        label = "riffMiniArtworkWidth",
    )
    val thumbnailAlpha by animateFloatAsState(
        targetValue = if (showThumbnail) 1f else 0f,
        animationSpec = tween(350),
        label = "riffMiniArtworkAlpha",
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnailWidth > 0.dp) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .width(thumbnailWidth)
                        .height(52.dp)
                        .alpha(thumbnailAlpha)
                        .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(13.dp * thumbnailAlpha))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = artist,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                fontWeight = RiffSubtextWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onArtistClick),
            )
        }
    }
}

@Composable
private fun RiffTransport(
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    activeColor: Color,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.STANDARD)
    val squigglySlider by rememberPreference(SquigglySliderKey, false)
    val whiteSliderColors =
        SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.24f),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
    )
    Column(modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 22.dp)) {
        val sliderValue = position.coerceAtLeast(0L).toFloat()
        val sliderRange = 0f..duration.coerceAtLeast(1L).toFloat()
        when (sliderStyle) {
            SliderStyle.DEFAULT -> {
                Slider(
                    value = sliderValue,
                    valueRange = sliderRange,
                    onValueChange = { onSeek(it.toLong()) },
                    onValueChangeFinished = onSeekFinished,
                    colors = whiteSliderColors,
                )
            }

            SliderStyle.STANDARD -> {
                Slider(
                    value = sliderValue,
                    valueRange = sliderRange,
                    onValueChange = { onSeek(it.toLong()) },
                    onValueChangeFinished = onSeekFinished,
                    colors = whiteSliderColors,
                    thumb = {
                        Box(
                            Modifier
                                .offset(y = 2.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                    },
                    track = { sliderState ->
                        PlayerSliderTrack(
                            sliderState = sliderState,
                            colors = whiteSliderColors,
                            trackHeight = 4.dp,
                        )
                    },
                )
            }

            SliderStyle.WAVY -> {
                if (squigglySlider) {
                    SquigglySlider(
                        value = sliderValue,
                        valueRange = sliderRange,
                        onValueChange = { onSeek(it.toLong()) },
                        onValueChangeFinished = onSeekFinished,
                        colors = whiteSliderColors,
                        isPlaying = isPlaying,
                    )
                } else {
                    WavySlider(
                        value = sliderValue,
                        valueRange = sliderRange,
                        onValueChange = { onSeek(it.toLong()) },
                        onValueChangeFinished = onSeekFinished,
                        colors = whiteSliderColors,
                        isPlaying = isPlaying,
                    )
                }
            }

            SliderStyle.SLIM -> {
                Slider(
                    value = sliderValue,
                    valueRange = sliderRange,
                    onValueChange = { onSeek(it.toLong()) },
                    onValueChangeFinished = onSeekFinished,
                    colors = whiteSliderColors,
                    thumb = { Spacer(Modifier.size(0.dp)) },
                    track = { sliderState ->
                        PlayerSliderTrack(
                            sliderState = sliderState,
                            colors = whiteSliderColors,
                        )
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().offset(y = (-10).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(makeTimeString(position), color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
            Text(
                if (duration > 0) makeTimeString(duration) else "",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RiffTransportIcon(
                R.drawable.tabler_ic_arrows_shuffle_outline,
                shuffleEnabled,
                onShuffle,
                activeColor = activeColor,
            )
            RiffTransportIcon(
                R.drawable.tabler_ic_player_track_prev_filled,
                false,
                onPrevious,
                enabled = canSkipPrevious,
                dimWhenInactive = false,
                size = 29,
            )
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(
                        if (isPlaying) R.drawable.tabler_ic_player_pause_filled
                        else R.drawable.tabler_ic_player_play_filled,
                    ),
                    null,
                    tint = Color(0xFF111113),
                    modifier = Modifier.size(34.dp),
                )
            }
            RiffTransportIcon(
                R.drawable.tabler_ic_player_track_next_filled,
                false,
                onNext,
                enabled = canSkipNext,
                dimWhenInactive = false,
                size = 29,
            )
            RiffTransportIcon(
                if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.tabler_ic_repeat_once_outline
                else R.drawable.tabler_ic_repeat_outline,
                repeatMode != Player.REPEAT_MODE_OFF,
                onRepeat,
                activeColor = activeColor,
            )
        }
    }
}

@Composable
private fun RiffTransportIcon(
    icon: Int,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    dimWhenInactive: Boolean = true,
    size: Int = 23,
    activeColor: Color = Color.White,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint =
            if (!enabled) Color.White.copy(alpha = 0.24f)
            else if (active) activeColor
            else if (dimWhenInactive) Color.White.copy(alpha = 0.72f)
            else Color.White,
        modifier = Modifier.size(44.dp).padding((44 - size).dp / 2).clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun RiffRatingControl(
    rating: SongRating,
    onLike: () -> Unit,
    onDislike: () -> Unit,
) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.11f)),
    ) {
        RiffRatingIcon(
            if (rating == SongRating.LIKED) R.drawable.tabler_ic_thumb_up_filled else R.drawable.tabler_ic_thumb_up_outline,
            rating == SongRating.LIKED,
            stringResource(R.string.riff_like),
            onLike,
        )
        Box(Modifier.size(width = 1.dp, height = 22.dp).background(Color.White.copy(alpha = 0.16f)).align(Alignment.CenterVertically))
        RiffRatingIcon(
            if (rating == SongRating.DISLIKED) R.drawable.tabler_ic_thumb_down_filled else R.drawable.tabler_ic_thumb_down_outline,
            rating == SongRating.DISLIKED,
            stringResource(R.string.riff_dislike),
            onDislike,
        )
    }
}

@Composable
private fun RiffRatingIcon(icon: Int, active: Boolean, description: String, onClick: () -> Unit) {
    Icon(
        painterResource(icon),
        contentDescription = description,
        tint = if (active) Color.White else Color.White.copy(alpha = 0.76f),
        modifier = Modifier.size(44.dp).padding(12.dp).clickable(onClick = onClick),
    )
}

@Composable
private fun RiffPillButton(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.11f))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(17.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Current audio output route. [external] is true when audio is routed off the built-in device. */
private data class RiffAudioOutput(val name: String, val external: Boolean)

private data class RiffOutputPillState(val label: String, val icon: Int)

/**
 * The left player pill: shows where audio is playing ("Playing on Phone" / "Playing on <device>").
 * Tapping it opens the Android media-output switcher. When the route changes (e.g. earbuds connect)
 * the icon + label swap with a springy slide-down: the old content drops out and the new drops in
 * from the top.
 */
@Composable
private fun RiffOutputPill(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val output = rememberAudioOutput()
    val deviceName = remember(output.name) {
        if (output.name.length > 26) output.name.take(26).trimEnd() + "…" else output.name
    }
    val state =
        RiffOutputPillState(
            label =
                if (output.external && deviceName.isNotBlank()) {
                    stringResource(R.string.riff_playing_on_device, deviceName)
                } else {
                    stringResource(R.string.riff_playing_on_phone)
                },
            icon =
                if (output.external) R.drawable.tabler_ic_device_airpods_outline
                else R.drawable.tabler_ic_device_mobile_outline,
        )
    Row(
        modifier =
            modifier
                .height(44.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.11f))
                .clickable { openOutputSwitcher(context) }
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                val slide =
                    spring<IntOffset>(
                        dampingRatio = 0.6f,
                        stiffness = Spring.StiffnessMedium,
                        visibilityThreshold = IntOffset(1, 1),
                    )
                (slideInVertically(slide) { -it } + fadeIn(tween(140))) togetherWith
                    (slideOutVertically(slide) { it } + fadeOut(tween(140)))
            },
            label = "riffOutputPill",
        ) { s ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painterResource(s.icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    s.label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Tracks the selected live-audio route via the framework MediaRouter and re-reads it whenever routes
 * change, so the pill updates the moment a Bluetooth device connects or disconnects.
 */
@Composable
private fun rememberAudioOutput(): RiffAudioOutput {
    val context = LocalContext.current
    val router = remember {
        context.getSystemService(android.content.Context.MEDIA_ROUTER_SERVICE) as android.media.MediaRouter
    }
    var output by remember { mutableStateOf(readAudioOutput(router)) }
    DisposableEffect(router) {
        val callback =
            object : android.media.MediaRouter.SimpleCallback() {
                private fun refresh() { output = readAudioOutput(router) }
                override fun onRouteSelected(r: android.media.MediaRouter, type: Int, info: android.media.MediaRouter.RouteInfo) = refresh()
                override fun onRouteUnselected(r: android.media.MediaRouter, type: Int, info: android.media.MediaRouter.RouteInfo) = refresh()
                override fun onRouteChanged(r: android.media.MediaRouter, info: android.media.MediaRouter.RouteInfo) = refresh()
                override fun onRouteAdded(r: android.media.MediaRouter, info: android.media.MediaRouter.RouteInfo) = refresh()
                override fun onRouteRemoved(r: android.media.MediaRouter, info: android.media.MediaRouter.RouteInfo) = refresh()
            }
        router.addCallback(android.media.MediaRouter.ROUTE_TYPE_LIVE_AUDIO, callback)
        output = readAudioOutput(router)
        onDispose { router.removeCallback(callback) }
    }
    return output
}

private fun readAudioOutput(router: android.media.MediaRouter): RiffAudioOutput {
    val selected = router.getSelectedRoute(android.media.MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
    // The default live-audio route is the built-in output (phone speaker / wired). Anything else
    // selected means audio has been routed to an external device such as Bluetooth earbuds.
    val external = selected != null && selected != router.defaultRoute
    return RiffAudioOutput(name = selected?.name?.toString().orEmpty(), external = external)
}

private fun openOutputSwitcher(context: android.content.Context) {
    val pm = context.packageManager

    // 1) Ask SystemUI to show its native Media Output dialog — the output switcher the user sees
    //    from the volume panel. Its receiver is exported with no permission, so any app may
    //    broadcast to it. (settingslib MediaOutputConstants: ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG /
    //    EXTRA_PACKAGE_NAME = "package_name".)
    val dialog =
        android.content.Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
            .setPackage("com.android.systemui")
            .putExtra("package_name", context.packageName)
    if (pm.queryBroadcastReceivers(dialog, 0).isNotEmpty()) {
        context.sendBroadcast(dialog)
        return
    }

    // 2) Settings "Media output" panel where the SystemUI receiver isn't available.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        val panel =
            android.content.Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
                .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
        if (panel.resolveActivity(pm) != null) {
            runCatching { context.startActivity(panel) }
            return
        }
    }

    // 3) Last resort: Bluetooth / connected-devices settings.
    runCatching {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
    }
}

@Composable
private fun RiffArtistCard(
    artistName: String,
    imageUrl: String?,
    description: String,
    subscribers: String,
    artistCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
) {
    val content =
        RiffArtistCardContent(
            key = "$artistName|$imageUrl",
            name = artistName,
            imageUrl = imageUrl,
            description = description,
            subscribers = subscribers,
        )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 8.dp)
                .height(286.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick),
    ) {
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            label = "riffArtistCard",
        ) { artistContent ->
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = artistContent.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.18f),
                                0.48f to Color.Black.copy(alpha = 0.08f),
                                1f to Color.Black.copy(alpha = 0.82f),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                ) {
                    Text(
                        text = artistContent.name,
                        color = Color.White,
                        fontSize = 29.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (artistContent.description.isNotBlank()) {
                        Text(
                            text = artistContent.description,
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (artistContent.subscribers.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = artistContent.subscribers,
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.riff_artists),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
        )
        if (artistCount > 1) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black.copy(alpha = 0.34f)),
            ) {
                RiffArtistSwitcherButton(
                    icon = R.drawable.tabler_ic_chevron_left_outline,
                    description = stringResource(R.string.riff_previous_artist),
                    onClick = onPrevious,
                )
                RiffArtistSwitcherButton(
                    icon = R.drawable.tabler_ic_chevron_right_outline,
                    description = stringResource(R.string.riff_next_artist),
                    onClick = onNext,
                )
            }
        }
    }
}

@Composable
private fun RiffArtistSwitcherButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(42.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun RiffSongMetadataCard(
    info: MediaInfo?,
    bitrate: Int?,
    codecs: String?,
    fallbackDescription: String,
) {
    val description = info?.description?.takeIf { it.isNotBlank() } ?: fallbackDescription
    var descriptionExpanded by rememberSaveable(description) { mutableStateOf(false) }
    val streamDescription =
        listOfNotNull(
            bitrate?.let { "${it / 1000} kbps" },
            codecs?.takeIf { it.isNotBlank() },
        ).joinToString(" • ")
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        info?.uploadDate?.takeIf { it.isNotBlank() }?.let {
            RiffMetadataLine(stringResource(R.string.riff_published_on, it), emphasized = true)
        }
        if (streamDescription.isNotBlank()) {
            RiffMetadataLine(stringResource(R.string.riff_audio_stream, streamDescription), emphasized = true)
        }
        info?.viewCount?.let {
            RiffMetadataLine(stringResource(R.string.riff_views, riffCompactNumber(it)), emphasized = true)
        }
        info?.let { mediaInfo ->
            RiffMetadataLine(
                stringResource(
                    R.string.riff_ratings,
                    riffCompactNumber(mediaInfo.like ?: 0),
                    riffCompactNumber(mediaInfo.dislike ?: 0),
                ),
                emphasized = false,
            )
        }
        RiffMetadataLine(stringResource(R.string.description), emphasized = true)
        RiffMetadataLine(
            text = description,
            emphasized = false,
            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
        )
        Text(
            text = stringResource(if (descriptionExpanded) R.string.riff_less else R.string.riff_more),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { descriptionExpanded = !descriptionExpanded },
        )
    }
}

@Composable
private fun RiffMetadataLine(text: String, emphasized: Boolean, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text = text,
        color = if (emphasized) Color.White else Color.White.copy(alpha = 0.62f),
        fontSize = if (emphasized) 16.sp else 14.sp,
        lineHeight = if (emphasized) 20.sp else 19.sp,
        fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun riffCompactNumber(value: Int): String =
    when {
        value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000f).removeSuffix(".0M") + if (value % 1_000_000 == 0) "M" else ""
        value >= 1_000 -> String.format("%.1fK", value / 1_000f).removeSuffix(".0K") + if (value % 1_000 == 0) "K" else ""
        else -> value.toString()
    }

@Composable
private fun RiffContextCard(
    title: String,
    primary: String,
    secondary: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.09f))
                .clickable(onClick = onClick)
                .padding(20.dp),
    ) {
        Text(title.uppercase(), color = RiffAccent.copy(alpha = 0.82f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Text(primary, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (secondary.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                secondary,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
