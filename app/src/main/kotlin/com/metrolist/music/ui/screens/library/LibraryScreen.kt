/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.constants.ChipSortTypeKey
import com.metrolist.music.constants.LibraryFilter
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.ui.component.LibraryPillTabs
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import kotlin.math.roundToInt

private val PillBarHeight = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(scrollBehavior: TopAppBarScrollBehavior) {
    val navController = LocalNavController.current
    val context = LocalContext.current

    // ChipSortTypeKey is the user's *default* library tab (configurable in
    // Appearance settings). Read it once as the starting tab, but keep the live
    // selection session-only (rememberSaveable, not written back) so the app
    // always opens on that default (Library by default) instead of restoring
    // whatever was tapped last session.
    val defaultChip = remember { context.dataStore[ChipSortTypeKey].toEnum(LibraryFilter.LIBRARY) }
    var filterType by rememberSaveable { mutableStateOf(defaultChip) }

    val insets = LocalPlayerAwareWindowInsets.current
    val pillBarHeightPx = with(LocalDensity.current) { PillBarHeight.toPx() }

    // Single persistent pill bar, floated over the sub-screen content so it keeps
    // one instance across category changes (needed for the collapse animation).
    // It hides/shows on scroll in lockstep with the top app bar by riding the same
    // scroll behavior's heightOffset — over-mapped so it slides fully off (the app
    // bar's own collapse range only covers the app bar height).
    val emptyHeader: @Composable () -> Unit = {}

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            // Sub-screens keep the app-bar top inset (so their lists drive the app
            // bar collapse) plus the pill-bar height (so their first item sits below
            // the floating pills).
            LocalPlayerAwareWindowInsets provides insets.add(WindowInsets(top = PillBarHeight)),
        ) {
            when (filterType) {
                LibraryFilter.LIBRARY -> LibraryMixScreen(navController, emptyHeader)
                LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(navController, emptyHeader)
                LibraryFilter.SONGS -> LibrarySongsScreen(navController, emptyHeader)
                LibraryFilter.ALBUMS -> LibraryAlbumsScreen(navController, emptyHeader)
                LibraryFilter.ARTISTS -> LibraryArtistsScreen(navController, emptyHeader)
                LibraryFilter.PODCASTS -> LibraryPodcastsScreen(navController, emptyHeader)
            }
        }

        LibraryPillTabs(
            filterType = filterType,
            onFilterChange = { filterType = it },
            modifier = Modifier
                .windowInsetsPadding(insets.only(WindowInsetsSides.Top))
                .offset {
                    val limit = scrollBehavior.state.heightOffsetLimit
                    // Slide the pills up by the app-bar offset scaled so they fully
                    // clear (app-bar height + pill-bar height) as the bar collapses.
                    val factor = if (limit < 0f) 1f + pillBarHeightPx / -limit else 1f
                    IntOffset(0, (scrollBehavior.state.heightOffset * factor).roundToInt())
                }
                // Opaque bar so the scrolling content doesn't show through the pills.
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 4.dp),
        )
    }
}
