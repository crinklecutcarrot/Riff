/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.utils.completed
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.RemoteMutationResult
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val savedAlbumIds = MutableStateFlow<Set<String>>(emptySet())
    val albumSavedOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val libraryAlbumStateLoaded = MutableStateFlow(false)
    val mutationError = MutableStateFlow(false)

    private suspend fun load() {
        YouTube
            .explore()
            .onSuccess { page ->
                val releases =
                    page.newReleaseAlbums.ifEmpty {
                        YouTube.newReleaseAlbums().getOrDefault(emptyList())
                    }
                val artists: MutableMap<Int, String> = mutableMapOf()
                val favouriteArtists: MutableMap<Int, String> = mutableMapOf()
                database.allArtistsByPlayTime().first().let { list ->
                    var favIndex = 0
                    for ((artistsIndex, artist) in list.withIndex()) {
                        artists[artistsIndex] = artist.id
                        if (artist.artist.bookmarkedAt != null) {
                            favouriteArtists[favIndex] = artist.id
                            favIndex++
                        }
                    }
                }
                explorePage.value =
                    page.copy(
                        newReleaseAlbums =
                        releases
                            .sortedBy { album ->
                                val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                                val firstArtistKey =
                                    artistIds.firstNotNullOfOrNull { artistId ->
                                        if (artistId in favouriteArtists.values) {
                                            favouriteArtists.entries.firstOrNull { it.value == artistId }?.key
                                        } else {
                                            artists.entries.firstOrNull { it.value == artistId }?.key
                                        }
                                    } ?: Int.MAX_VALUE
                                firstArtistKey
                            }.filterExplicit(context.dataStore.get(HideExplicitKey, false)),
                    )
            }.onFailure {
                reportException(it)
            }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            load()
        }
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.library("FEmusic_liked_albums").completed()
                .onSuccess { page ->
                    savedAlbumIds.value =
                        page.items
                            .filterIsInstance<AlbumItem>()
                            .flatMap(::albumKeys)
                            .toSet()
                    libraryAlbumStateLoaded.value = true
                }.onFailure(::reportException)
        }
    }

    fun toggleAlbumSaved(album: AlbumItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val keys = albumKeys(album).toSet()
            val oldIds = savedAlbumIds.value
            val oldOverrides = albumSavedOverrides.value
            val current = keys.firstNotNullOfOrNull { oldOverrides[it] } ?: keys.any { it in oldIds }
            val shouldSave = !current

            albumSavedOverrides.value = oldOverrides + keys.associateWith { shouldSave }
            savedAlbumIds.value = if (shouldSave) oldIds + keys else oldIds - keys

            if (syncUtils.setPlaylistSavedNow(album.playlistId, shouldSave) != RemoteMutationResult.SUCCESS) {
                albumSavedOverrides.value = oldOverrides
                savedAlbumIds.value = oldIds
                mutationError.value = true
            }
        }
    }

    fun clearMutationError() {
        mutationError.value = false
    }
}

private fun albumKeys(album: AlbumItem): List<String> =
    listOf(
        album.browseId,
        album.playlistId,
        "album:${album.title.lowercase().filter(Char::isLetterOrDigit)}:${album.year?.toString().orEmpty()}",
    )
