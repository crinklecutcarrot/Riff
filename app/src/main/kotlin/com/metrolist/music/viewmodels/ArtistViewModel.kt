/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.innertube.models.filterYoutubeShorts
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.utils.completed
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.deserializeArtistPage
import com.metrolist.music.db.entities.serializeArtistPage
import com.metrolist.music.db.entities.toArtistPage
import com.metrolist.music.extensions.filterExplicit
import com.metrolist.music.extensions.filterExplicitAlbums
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.RemoteMutationResult
import com.metrolist.music.utils.PodcastRefreshTrigger
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import com.metrolist.music.extensions.filterVideoSongs as filterVideoSongsLocal

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val artistId = savedStateHandle.get<String>("artistId")!!
    private val isPodcastChannel = savedStateHandle.get<Boolean>("isPodcastChannel") ?: false
    var artistPage by mutableStateOf<ArtistPage?>(null)

    // Track API subscription state separately
    private val _apiSubscribed = MutableStateFlow<Boolean?>(null)

    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // Combine API state with local database state - local takes precedence when not logged in
    val isChannelSubscribed = kotlinx.coroutines.flow.combine(
        _apiSubscribed,
        database.artist(artistId),
    ) { apiState, localArtist ->
        val locallyBookmarked = localArtist?.artist?.bookmarkedAt != null
        locallyBookmarked || (apiState == true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val librarySongs = context.dataStore.data
        .map { (it[HideExplicitKey] ?: false) to (it[HideVideoSongsKey] ?: false) }
        .distinctUntilChanged()
        .flatMapLatest { (hideExplicit, hideVideoSongs) ->
            database.artistSongsPreview(artistId).map { it.filterExplicit(hideExplicit).filterVideoSongsLocal(hideVideoSongs) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val libraryAlbums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistAlbumsPreview(artistId).map { it.filterExplicitAlbums(hideExplicit) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val likedSongIds = database.likedSongIds()
        .map(List<String>::toSet)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val _remoteSavedAlbumIds = MutableStateFlow<Set<String>>(emptySet())
    private val _albumSavedOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _libraryAlbumStateLoaded = MutableStateFlow(false)
    // Online album state is authoritative from YT Music. Room is only a content
    // cache and must not make a remote album appear saved or unsaved.
    val savedAlbumIds = _remoteSavedAlbumIds.asStateFlow()
    val albumSavedOverrides = _albumSavedOverrides.asStateFlow()
    val libraryAlbumStateLoaded = _libraryAlbumStateLoaded.asStateFlow()

    private val _mutationError = MutableStateFlow(false)
    val mutationError = _mutationError.asStateFlow()

    fun clearMutationError() {
        _mutationError.value = false
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.library("FEmusic_liked_albums").completed()
                .onSuccess { libraryPage ->
                    val remoteAlbums = libraryPage.items.filterIsInstance<AlbumItem>()
                    _remoteSavedAlbumIds.value = remoteAlbums
                        .flatMap { album ->
                            listOf(
                                album.browseId,
                                album.playlistId,
                                albumIdentityKey(album.title, album.year),
                            )
                        }
                        .toSet()
                    _libraryAlbumStateLoaded.value = true

                }
                .onFailure { Timber.w(it, "Unable to refresh saved albums for artist page") }
        }

        viewModelScope.launch {
            // Load cached page first for instant display, then fetch fresh data
            loadCachedPage()

            context.dataStore.data
                .map {
                    Triple(
                        it[HideExplicitKey] ?: false,
                        it[HideVideoSongsKey] ?: false,
                        it[HideYoutubeShortsKey] ?: false
                    )
                }
                .distinctUntilChanged()
                .collect {
                    fetchArtistsFromYTM()
                }
        }
    }

    private suspend fun loadCachedPage() {
        try {
            val cachedJson = database.artist(artistId).firstOrNull()?.artist?.cachedPageJson
            if (cachedJson != null) {
                val cachedDto = withContext(Dispatchers.IO) { deserializeArtistPage(cachedJson) }
                val page = cachedDto.toArtistPage()
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

                val filteredSections = page.sections
                    .map { section ->
                        section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                    }
                    .filter { section -> section.items.isNotEmpty() }

                artistPage = page.copy(sections = filteredSections)
                _apiSubscribed.value = page.isSubscribed
            }
        } catch (e: Exception) {
            reportException(e)
        }
    }

    fun fetchArtistsFromYTM() {
        viewModelScope.launch {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            YouTube.artist(artistId)
                .onSuccess { page ->
                    // Collect all items from all sections and resolve artist IDs once
                    val allItems = page.sections.flatMap { it.items }
                    val resolvedIdMap = if (allItems.isNotEmpty()) {
                        YouTube.resolveArtistIdMap(allItems)
                    } else {
                        emptyMap()
                    }

                    fun com.metrolist.innertube.models.Artist.resolve() =
                        if (id == null) resolvedIdMap[name]?.let { copy(id = it) } ?: this else this

                    // Resolve artist IDs and fetch durations from more endpoint
                    val resolvedSections = page.sections.map { section ->
                        section.copy(items = section.items.map { item ->
                            when (item) {
                                is SongItem -> item.copy(artists = item.artists.map { it.resolve() })
                                is AlbumItem -> item.copy(artists = item.artists?.map { it.resolve() })
                                is PlaylistItem -> item.copy(author = item.author?.resolve())
                                is EpisodeItem -> item.copy(author = item.author?.resolve())
                                is PodcastItem -> item.copy(author = item.author?.resolve())
                                else -> item
                            }
                        })
                    }

                    // The artist landing response normally contains only five popular songs.
                    // Expand that one shelf from its single "more" request so the UI can page
                    // through 15 songs without making a request for every individual track.
                    var expandedSections = resolvedSections
                    val songSectionIndex = resolvedSections.indexOfFirst { section ->
                        section.items.any { it is SongItem }
                    }
                    if (songSectionIndex >= 0) {
                        val songSection = resolvedSections[songSectionIndex]
                        songSection.moreEndpoint?.let { moreEndpoint ->
                            try {
                                YouTube.artistItems(moreEndpoint)
                                    .onSuccess { morePage ->
                                        val landingSongs = songSection.items.filterIsInstance<SongItem>()
                                        val moreSongs = morePage.items
                                            .filterIsInstance<SongItem>()
                                            .map { song -> song.copy(artists = song.artists.map { it.resolve() }) }
                                        val moreById = moreSongs.associateBy { it.id }
                                        val enrichedLandingSongs = landingSongs.map { song ->
                                            val details = moreById[song.id]
                                            song.copy(
                                                duration = song.duration ?: details?.duration,
                                                viewsText = song.viewsText ?: details?.viewsText,
                                            )
                                        }
                                        val expandedSongs = (enrichedLandingSongs + moreSongs)
                                            .distinctBy { it.id }
                                            .take(15)
                                        expandedSections = resolvedSections.toMutableList().also { sections ->
                                            sections[songSectionIndex] = songSection.copy(items = expandedSongs)
                                        }
                                    }
                                    .onFailure(::reportException)
                            } catch (e: Exception) {
                                reportException(e)
                            }
                        }
                    }

                    val resolvedPage = page.copy(sections = expandedSections)
                    val filteredSections = resolvedPage.sections
                        .map { section ->
                            section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                        }
                        .filter { section -> section.items.isNotEmpty() }

                    artistPage = resolvedPage.copy(sections = filteredSections)
                    // Cache page data + persist artist metadata
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val cachedJson = serializeArtistPage(
                                sections = resolvedPage.sections,
                                description = resolvedPage.description,
                                subscriberCountText = resolvedPage.subscriberCountText,
                                monthlyListenerCount = resolvedPage.monthlyListenerCount,
                                isSubscribed = resolvedPage.isSubscribed,
                                artist = resolvedPage.artist,
                            )
                            val existingArtist = database.artist(artistId).firstOrNull()?.artist
                            if (existingArtist != null) {
                                database.update(
                                    existingArtist.copy(
                                        name = resolvedPage.artist.title,
                                        channelId = resolvedPage.artist.channelId ?: existingArtist.channelId,
                                        thumbnailUrl = resolvedPage.artist.thumbnail ?: existingArtist.thumbnailUrl,
                                        cachedPageJson = cachedJson,
                                        lastUpdateTime = java.time.LocalDateTime.now(),
                                    )
                                )
                            } else {
                                val apiArtist = resolvedPage.artist
                                database.insert(
                                    ArtistEntity(
                                        id = artistId,
                                        name = apiArtist.title,
                                        channelId = apiArtist.channelId,
                                        thumbnailUrl = apiArtist.thumbnail,
                                        cachedPageJson = cachedJson,
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            reportException(e)
                        }
                    }
                    // Store API subscription state
                    _apiSubscribed.value = resolvedPage.isSubscribed
                }.onFailure {
                    reportException(it)
                }
        }
    }

    fun toggleChannelSubscription() {
        val isCurrentlySubscribed = isChannelSubscribed.value
        val shouldBeSubscribed = !isCurrentlySubscribed

        // Optimistically update API state for immediate UI feedback
        _apiSubscribed.value = shouldBeSubscribed

        viewModelScope.launch(Dispatchers.IO) {
            val channelId = artistPage?.artist?.channelId
                ?: artistId.takeIf { it.startsWith("UC") }
                ?: YouTube.getChannelId(artistId)
            val originalArtist = libraryArtist.value?.artist
            Timber.d("[CHANNEL_TOGGLE] Inside coroutine, updating database...")
            // Update local database first (optimistic update)
            // Call DAO methods directly - they're synchronous on IO dispatcher
            val artist = originalArtist
            Timber.d("[CHANNEL_TOGGLE] libraryArtist.value?.artist = $artist")
            if (artist != null) {
                val newBookmark = if (shouldBeSubscribed) {
                    artist.bookmarkedAt ?: java.time.LocalDateTime.now()
                } else {
                    null
                }
                // Also set isPodcastChannel if subscribing from podcast context
                val updatedArtist = artist.copy(
                    bookmarkedAt = newBookmark,
                    isPodcastChannel = if (shouldBeSubscribed && isPodcastChannel) true else artist.isPodcastChannel
                )
                Timber.d("[CHANNEL_TOGGLE] Updating existing artist: ${artist.id} -> bookmarkedAt=$newBookmark, isPodcastChannel=${updatedArtist.isPodcastChannel}")
                database.update(updatedArtist)
            } else if (shouldBeSubscribed) {
                Timber.d("[CHANNEL_TOGGLE] No existing artist, inserting new one")
                artistPage?.artist?.let {
                    database.insert(
                        ArtistEntity(
                            id = artistId,
                            name = it.title,
                            channelId = it.channelId,
                            thumbnailUrl = it.thumbnail,
                            bookmarkedAt = java.time.LocalDateTime.now(),
                            isPodcastChannel = isPodcastChannel,
                        )
                    )
                    Timber.d("[CHANNEL_TOGGLE] Inserted new artist: $artistId, isPodcastChannel=$isPodcastChannel")
                } ?: Timber.d("[CHANNEL_TOGGLE] artistPage?.artist is null, cannot insert")
            } else {
                Timber.d("[CHANNEL_TOGGLE] No artist and shouldBeSubscribed=false, nothing to do")
            }

            val result = if (channelId.isNotBlank()) {
                syncUtils.setChannelSubscribedNow(channelId, shouldBeSubscribed)
            } else {
                RemoteMutationResult.FAILED
            }
            if (result != RemoteMutationResult.SUCCESS) {
                _apiSubscribed.value = isCurrentlySubscribed
                val current = database.artist(artistId).firstOrNull()?.artist
                if (originalArtist != null) {
                    database.update(originalArtist)
                } else if (current != null) {
                    database.update(current.copy(bookmarkedAt = null))
                }
                _mutationError.value = true
            } else {
                PodcastRefreshTrigger.triggerRefresh()
            }
        }
    }

    fun toggleAlbumSaved(album: AlbumItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val albumKeys = setOf(
                album.browseId,
                album.playlistId,
                albumIdentityKey(album.title, album.year),
            )
            val originalRemoteIds = _remoteSavedAlbumIds.value
            val originalOverrides = _albumSavedOverrides.value
            val authoritativeOverride = albumKeys.firstNotNullOfOrNull { originalOverrides[it] }
            val shouldSave = !(authoritativeOverride ?: albumKeys.any { it in savedAlbumIds.value })
            _albumSavedOverrides.value = originalOverrides + albumKeys.associateWith { shouldSave }
            _remoteSavedAlbumIds.value = if (shouldSave) {
                originalRemoteIds + albumKeys
            } else {
                originalRemoteIds - albumKeys
            }

            val result = syncUtils.setPlaylistSavedNow(album.playlistId, shouldSave)
            if (result != RemoteMutationResult.SUCCESS) {
                _remoteSavedAlbumIds.value = originalRemoteIds
                _albumSavedOverrides.value = originalOverrides
                _mutationError.value = true
            }
        }
    }

    fun refreshAlbumSavedState(album: AlbumItem) {
        val keys = setOf(
            album.browseId,
            album.playlistId,
            albumIdentityKey(album.title, album.year),
        )
        if (keys.any { it in _albumSavedOverrides.value }) return

        viewModelScope.launch(Dispatchers.IO) {
            val cachedState = YouTube.cachedAlbum(album.browseId)?.isInLibrary
            if (cachedState != null) {
                _albumSavedOverrides.value =
                    _albumSavedOverrides.value + keys.associateWith { cachedState }
                return@launch
            }

            YouTube.album(album.browseId, withSongs = true)
                .onSuccess { albumPage ->
                    val isSaved = albumPage.isInLibrary
                        ?: if (_libraryAlbumStateLoaded.value) {
                            keys.any { it in _remoteSavedAlbumIds.value }
                        } else {
                            null
                        }
                    if (isSaved != null) {
                        _albumSavedOverrides.value =
                            _albumSavedOverrides.value + keys.associateWith { isSaved }
                    }
                }
                .onFailure { error ->
                    reportException(error)
                    if (_libraryAlbumStateLoaded.value) {
                        val isSaved = keys.any { it in _remoteSavedAlbumIds.value }
                        _albumSavedOverrides.value =
                            _albumSavedOverrides.value + keys.associateWith { isSaved }
                    }
                }
        }
    }
}

private fun albumIdentityKey(title: String, year: Int?): String =
    "album:${title.lowercase().filter(Char::isLetterOrDigit)}:${year.orEmptyKey()}"

private fun Int?.orEmptyKey(): String = this?.toString().orEmpty()
