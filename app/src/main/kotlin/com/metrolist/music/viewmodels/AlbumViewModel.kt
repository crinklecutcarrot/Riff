/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.RemoteMutationResult
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel
@Inject
constructor(
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    enum class LibraryMutation {
        ADDED,
        REMOVED,
        FAILED,
    }

    val albumId = savedStateHandle.get<String>("albumId")!!
    val playlistId = MutableStateFlow("")
    val albumWithSongs =
        database
            .albumWithSongs(albumId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())
    val moreByArtist = MutableStateFlow<List<AlbumItem>>(emptyList())
    val trackPlays = MutableStateFlow<Map<String, String>>(emptyMap())
    val albumType = MutableStateFlow<String?>(null)
    val remoteAlbumPage = MutableStateFlow<AlbumPage?>(null)
    val remoteAlbumSaved = MutableStateFlow<Boolean?>(null)
    private val _mutationError = MutableStateFlow(false)
    val mutationError = _mutationError
    private val _libraryMutations = MutableSharedFlow<LibraryMutation>(extraBufferCapacity = 1)
    val libraryMutations = _libraryMutations.asSharedFlow()

    fun clearMutationError() {
        _mutationError.value = false
    }

    fun toggleAlbumLibrary() {
        val currentAlbum = albumWithSongs.value ?: return
        val currentlyAdded = remoteAlbumSaved.value ?: return
        val shouldAdd = !currentlyAdded
        remoteAlbumSaved.value = shouldAdd

        viewModelScope.launch(Dispatchers.IO) {
            val result = syncUtils.setAlbumLibraryNow(
                playlistId = currentAlbum.album.playlistId ?: playlistId.value,
                saved = shouldAdd,
            )
            if (result == RemoteMutationResult.SUCCESS) {
                database.update(
                    currentAlbum.album.copy(
                        bookmarkedAt = if (shouldAdd) LocalDateTime.now() else null,
                    ),
                )
                _libraryMutations.emit(
                    if (shouldAdd) LibraryMutation.ADDED else LibraryMutation.REMOVED,
                )
            } else {
                remoteAlbumSaved.value = currentlyAdded
                _mutationError.value = true
                _libraryMutations.emit(LibraryMutation.FAILED)
            }
        }
    }

    init {
        YouTube.cachedAlbum(albumId)?.let { cached ->
            remoteAlbumPage.value = cached
            cached.isInLibrary?.let { remoteAlbumSaved.value = it }
            playlistId.value = cached.album.playlistId
            otherVersions.value = cached.otherVersions
            trackPlays.value = cached.songs.associate { it.id to it.viewsText.orEmpty() }
            albumType.value = cached.type
        }
        viewModelScope.launch(Dispatchers.IO) {
            val album = database.album(albumId).first()
            // The completed YouTube library sync is our cache of authoritative
            // account state. Render it immediately instead of briefly showing the
            // album as unsaved while another album request is in flight.
            if (remoteAlbumSaved.value == null) {
                remoteAlbumSaved.value = album?.album?.bookmarkedAt != null
            }
            YouTube
                .album(albumId)
                .onSuccess {
                    remoteAlbumPage.value = it
                    // Some album responses omit the library toggle entirely. Null
                    // means "not supplied", not "not saved".
                    it.isInLibrary?.let { isSaved -> remoteAlbumSaved.value = isSaved }
                    playlistId.value = it.album.playlistId
                    otherVersions.value = it.otherVersions
                    trackPlays.value = it.songs.associate { song ->
                        song.id to (song.viewsText ?: "")
                    }
                    albumType.value = it.type
                    database.transaction {
                        if (album == null) {
                            insert(it)
                        } else {
                            update(album.album, it, album.artists)
                        }
                    }
                    // Persist only states explicitly returned by YT Music. Room is a
                    // cache for rendering; it must not invent song-library state.
                    it.songs.forEach { remoteSong ->
                        remoteSong.isInLibrary?.let { isSaved ->
                            database.songEntity(remoteSong.id)?.let { localSong ->
                                val localSaved = localSong.inLibrary != null
                                if (localSaved != isSaved) {
                                    database.update(
                                        localSong.copy(
                                            inLibrary = if (isSaved) LocalDateTime.now() else null,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    it.album.artists?.firstOrNull()?.id?.let { artistId ->
                        YouTube.artist(artistId)
                            .onSuccess { artistPage ->
                                moreByArtist.value = artistPage.sections
                                    .flatMap { section -> section.items }
                                    .filterIsInstance<AlbumItem>()
                                    .filterNot { candidate -> candidate.id == albumId }
                                    .distinctBy { candidate -> candidate.id }
                                    .take(12)
                            }
                            .onFailure(::reportException)
                    }
                }.onFailure {
                    reportException(it)
                    if (it.message?.contains("NOT_FOUND") == true) {
                        database.query {
                            album?.album?.let(::delete)
                        }
                    }
                }
        }
    }
}
