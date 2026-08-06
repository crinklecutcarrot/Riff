/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import android.content.Intent
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.SpeedDialItem
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.NewAction
import com.metrolist.music.ui.component.NewActionGrid
import com.metrolist.music.ui.component.YouTubeListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun YouTubeArtistMenu(
    artist: ArtistItem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val libraryArtist by database.artist(artist.id).collectAsStateWithLifecycle(initialValue = null)
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost
    val isPinned by database.speedDialDao.isPinned(artist.id).collectAsStateWithLifecycle(initialValue = false)
    val coroutineScope = rememberCoroutineScope()

    YouTubeListItem(
        item = artist,
        trailingContent = {},
    )

    RiffMenuDivider()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(unbounded = true)
            .padding(bottom = 20.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()),
    ) {
            NewActionGrid(
                actions = buildList {
                    if (!isGuest) {
                        artist.radioEndpoint?.let { watchEndpoint ->
                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.tabler_ic_radio),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    text = stringResource(R.string.start_radio),
                                    onClick = {
                                        playerConnection.playQueue(YouTubeQueue(watchEndpoint))
                                        onDismiss()
                                    }
                                )
                            )
                        }
                    }

                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(if (isPinned) R.drawable.tabler_ic_minus_outline else R.drawable.tabler_ic_plus_outline),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            text = if (isPinned) stringResource(R.string.unpin) else stringResource(R.string.pin),
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    if (isPinned) {
                                        database.speedDialDao.delete(artist.id)
                                    } else {
                                        database.speedDialDao.insert(SpeedDialItem.fromYTItem(artist))
                                    }
                                }
                                onDismiss()
                            }
                        )
                    )

                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.tabler_ic_share_3),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            text = stringResource(R.string.share),
                            onClick = {
                                val intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, artist.shareLink)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                                onDismiss()
                            }
                        )
                    )

                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(if (libraryArtist?.artist?.bookmarkedAt != null) R.drawable.tabler_ic_bell_filled else R.drawable.tabler_ic_bell_outline),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            text = if (libraryArtist?.artist?.bookmarkedAt != null) stringResource(R.string.subscribed) else stringResource(R.string.subscribe),
                            onClick = {
                                database.query {
                                    val libraryArtistValue = libraryArtist
                                    if (libraryArtistValue != null) {
                                        update(libraryArtistValue.artist.toggleLike())
                                    } else {
                                        insert(
                                            ArtistEntity(
                                                id = artist.id,
                                                name = artist.title,
                                                channelId = artist.channelId,
                                                thumbnailUrl = artist.thumbnail,
                                            ).toggleLike()
                                        )
                                    }
                                }
                            }
                        )
                    )
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                columns = if (isGuest) 1 else 3
            )
    }
}
