/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.recognition

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.db.entities.RecognitionHistory
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.recognition.MusicRecognitionService
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.SearchRoutes
import com.metrolist.shazamkit.models.RecognitionResult
import com.metrolist.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionScreen(
    navController: NavController,
    autoStart: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()

    // Only reset in Ready state: Listening/Processing belong to a running widget-service
    // recognition that must not be cancelled; Success/NoMatch/Error are results pending
    // display and history saving.
    LaunchedEffect(Unit) {
        if (MusicRecognitionService.recognitionStatus.value is RecognitionStatus.Ready) {
            MusicRecognitionService.reset()
        }
    }

    DisposableEffect(Unit) {
        onDispose { MusicRecognitionService.reset() }
    }

    // Observe recognition status from service for real-time updates (Listening -> Processing -> Result)
    val recognitionStatus by MusicRecognitionService.recognitionStatus.collectAsStateWithLifecycle()

    // Recent matches for the idle "Recently recognized" list.
    val recentHistory by database.recognitionHistory().collectAsStateWithLifecycle(initialValue = emptyList())

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            hasPermission = isGranted
            if (isGranted) {
                coroutineScope.launch { MusicRecognitionService.recognize(context) }
            }
        }

    fun startRecognition() {
        if (hasPermission) {
            coroutineScope.launch { MusicRecognitionService.recognize(context) }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        if (autoStart && MusicRecognitionService.recognitionStatus.value is RecognitionStatus.Ready) {
            startRecognition()
        }
    }

    fun resetToReady() {
        MusicRecognitionService.reset()
    }

    fun saveToHistory(result: RecognitionResult) {
        // Skip if the widget service already persisted this result to avoid a duplicate entry
        if (MusicRecognitionService.resultSavedExternally) return
        coroutineScope.launch(Dispatchers.IO) {
            database.query {
                insert(
                    RecognitionHistory(
                        trackId = result.trackId,
                        title = result.title,
                        artist = result.artist,
                        album = result.album,
                        coverArtUrl = result.coverArtUrl,
                        coverArtHqUrl = result.coverArtHqUrl,
                        genre = result.genre,
                        releaseDate = result.releaseDate,
                        label = result.label,
                        shazamUrl = result.shazamUrl,
                        appleMusicUrl = result.appleMusicUrl,
                        spotifyUrl = result.spotifyUrl,
                        isrc = result.isrc,
                        youtubeVideoId = result.youtubeVideoId,
                        recognizedAt = LocalDateTime.now(),
                    ),
                )
            }
        }
    }

    fun playResult(result: RecognitionResult) {
        val videoId = result.youtubeVideoId
        if (videoId != null && playerConnection != null) {
            playerConnection.playQueue(YouTubeQueue(WatchEndpoint(videoId = videoId)))
        } else {
            navController.navigate(SearchRoutes.resultRoute("${result.title} ${result.artist}"))
        }
    }

    // Live elapsed-seconds counter shown inside the circle while listening.
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(recognitionStatus) {
        if (recognitionStatus is RecognitionStatus.Listening) {
            elapsedSeconds = 0
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recognize_music)) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        onLongClick = { navController.backToMain() },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.tabler_ic_arrow_left_outline),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("recognition_history") }) {
                        Icon(
                            painter = painterResource(R.drawable.tabler_ic_history_outline),
                            contentDescription = stringResource(R.string.recognition_history),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        AnimatedContent(
            targetState = recognitionStatus,
            transitionSpec = {
                fadeIn(tween(250)) togetherWith fadeOut(tween(200))
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            label = "recognition_content",
        ) { status ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RecognitionHeader(status)

                RecognitionCircle(
                    status = status,
                    elapsedSeconds = elapsedSeconds,
                    onTap = {
                        when (status) {
                            is RecognitionStatus.Ready,
                            is RecognitionStatus.NoMatch,
                            is RecognitionStatus.Error,
                            -> startRecognition()

                            is RecognitionStatus.Listening -> resetToReady()
                            else -> {}
                        }
                    },
                )

                when (status) {
                    is RecognitionStatus.Ready -> {
                        if (recentHistory.isNotEmpty()) {
                            RecentlyRecognized(
                                items = recentHistory.take(4),
                                onItemClick = { item ->
                                    navController.navigate(
                                        SearchRoutes.resultRoute("${item.title} ${item.artist}"),
                                    )
                                },
                            )
                        }
                    }

                    is RecognitionStatus.Success -> {
                        LaunchedEffect(status.result) { saveToHistory(status.result) }
                        BestMatchCard(
                            result = status.result,
                            onPlay = { playResult(status.result) },
                            onRetry = { startRecognition() },
                            navController = navController,
                            context = context,
                        )
                    }

                    is RecognitionStatus.NoMatch,
                    is RecognitionStatus.Error,
                    -> {
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = { startRecognition() }) {
                            Icon(
                                painter = painterResource(R.drawable.tabler_ic_refresh),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.try_again))
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun RecognitionHeader(status: RecognitionStatus) {
    val title: String
    val subtitle: String?
    when (status) {
        is RecognitionStatus.Ready -> {
            title = stringResource(R.string.rec_whats_playing)
            subtitle = stringResource(R.string.rec_prompt_idle)
        }
        is RecognitionStatus.Listening -> {
            title = stringResource(R.string.listening)
            subtitle = stringResource(R.string.rec_hold_phone)
        }
        is RecognitionStatus.Processing -> {
            title = stringResource(R.string.rec_finding_match)
            subtitle = stringResource(R.string.rec_comparing)
        }
        is RecognitionStatus.Success -> {
            title = stringResource(R.string.rec_got_it)
            subtitle = null
        }
        is RecognitionStatus.NoMatch -> {
            title = stringResource(R.string.no_match_found)
            subtitle = status.message
        }
        is RecognitionStatus.Error -> {
            title = stringResource(R.string.recognition_error)
            subtitle = status.message
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, start = 30.dp, end = 30.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(9.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

@Composable
private fun RecognitionCircle(
    status: RecognitionStatus,
    elapsedSeconds: Int,
    onTap: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val (bg, content, tappable) = when (status) {
        is RecognitionStatus.Ready -> Triple(cs.primary, cs.onPrimary, true)
        is RecognitionStatus.Listening -> Triple(cs.primary, cs.onPrimary, true)
        is RecognitionStatus.Processing -> Triple(cs.primary, cs.onPrimary, false)
        is RecognitionStatus.Success -> Triple(cs.primaryContainer, cs.onPrimaryContainer, false)
        is RecognitionStatus.NoMatch -> Triple(cs.errorContainer, cs.onErrorContainer, true)
        is RecognitionStatus.Error -> Triple(cs.errorContainer, cs.onErrorContainer, true)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (status is RecognitionStatus.Listening) {
            ExpandingRings(color = cs.primary)
        }

        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(bg)
                .then(if (tappable) Modifier.clickable { onTap() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when (status) {
                is RecognitionStatus.Ready -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.tabler_ic_microphone_outline),
                            contentDescription = null,
                            tint = content,
                            modifier = Modifier.size(46.dp),
                        )
                        Text(
                            text = stringResource(R.string.rec_tap_to_listen).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = content,
                            letterSpacing = 1.4.sp,
                        )
                    }
                }

                is RecognitionStatus.Listening -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        EqBars(color = content)
                        Text(
                            text = formatTimer(elapsedSeconds),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = content,
                            letterSpacing = 1.sp,
                        )
                    }
                }

                is RecognitionStatus.Processing -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            color = content,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp),
                        )
                        Text(
                            text = stringResource(R.string.rec_matching).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = content,
                            letterSpacing = 1.4.sp,
                        )
                    }
                }

                is RecognitionStatus.Success -> {
                    Icon(
                        painter = painterResource(R.drawable.tabler_ic_check_outline),
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(58.dp),
                    )
                }

                is RecognitionStatus.NoMatch -> {
                    Icon(
                        painter = painterResource(R.drawable.tabler_ic_x_outline),
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(52.dp),
                    )
                }

                is RecognitionStatus.Error -> {
                    Icon(
                        painter = painterResource(R.drawable.tabler_ic_alert_circle_outline),
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
        }
    }
}

/** Two concentric rings that pulse outward from the mic button while listening. */
@Composable
private fun ExpandingRings(color: Color) {
    val transition = rememberInfiniteTransition(label = "rings")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringPhase",
    )
    Box(
        modifier = Modifier.size(190.dp),
        contentAlignment = Alignment.Center,
    ) {
        listOf(phase, (phase + 0.5f) % 1f).forEach { p ->
            val scale = 1f + p * 0.5f
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = (1f - p) * 0.45f
                    }
                    .border(1.5.dp, color, CircleShape),
            )
        }
    }
}

/** Animated equalizer bars shown inside the circle while listening. */
@Composable
private fun EqBars(color: Color) {
    val heights = listOf(0.38f, 0.72f, 0.96f, 0.6f, 0.88f, 0.46f, 0.66f)
    val transition = rememberInfiniteTransition(label = "eq")
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(46.dp),
    ) {
        heights.forEachIndexed { i, h ->
            val fraction by transition.animateFloat(
                initialValue = h * 0.35f,
                targetValue = h,
                animationSpec = infiniteRepeatable(
                    animation = tween(480 + i * 70, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun BestMatchCard(
    result: RecognitionResult,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    navController: NavController,
    context: Context,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = result.coverArtHqUrl ?: result.coverArtUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(74.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.rec_best_match).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = result.album?.let { "${result.artist} · $it" } ?: result.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(23.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.tabler_ic_player_play_filled),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.play))
                }
                MoreMenuButton(result = result, navController = navController, context = context)
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRetry) {
                    Icon(
                        painter = painterResource(R.drawable.tabler_ic_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun MoreMenuButton(
    result: RecognitionResult,
    navController: NavController,
    context: Context,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedIconButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(23.dp),
            modifier = Modifier.size(46.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.tabler_ic_dots_vertical_outline),
                contentDescription = null,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rec_search_on_ytm)) },
                onClick = {
                    expanded = false
                    navController.navigate(
                        SearchRoutes.resultRoute("${result.title} ${result.artist}"),
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.tabler_ic_search_outline),
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share)) },
                onClick = {
                    expanded = false
                    shareResult(context, result)
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.tabler_ic_share_3),
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@Composable
private fun RecentlyRecognized(
    items: List<RecognitionHistory>,
    onItemClick: (RecognitionHistory) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.rec_recently_recognized).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item) }
                    .padding(vertical = 9.dp),
            ) {
                AsyncImage(
                    model = item.coverArtUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = timeAgo(item.recognizedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatTimer(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

private fun timeAgo(then: LocalDateTime): String {
    val minutes = Duration.between(then, LocalDateTime.now()).toMinutes()
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 1440 -> "${minutes / 60}h"
        else -> "${minutes / 1440}d"
    }
}

private fun shareResult(context: Context, result: RecognitionResult) {
    val text = result.shazamUrl ?: "${result.title} - ${result.artist}"
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(sendIntent, null))
}
