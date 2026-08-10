package com.metrolist.innertube.pages

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.MusicResponsiveHeaderRenderer
import com.metrolist.innertube.models.MusicResponsiveListItemRenderer
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.getItems
import com.metrolist.innertube.models.oddElements
import com.metrolist.innertube.models.response.BrowseResponse
import com.metrolist.innertube.models.splitBySeparator
import com.metrolist.innertube.utils.parseTime

data class AlbumPage(
    val album: AlbumItem,
    val songs: List<SongItem>,
    val otherVersions: List<AlbumItem>,
    val type: String? = null,
    val isInLibrary: Boolean? = null,
    // For upcoming/pre-save albums: the release date as epoch millis (from the ANDROID_MUSIC
    // countdown timer). Null for already-released albums.
    val releaseTimestampMs: Long? = null,
) {
    companion object {
        fun getPlaylistId(response: BrowseResponse): String? {
            var playlistId = response.microformat?.microformatDataRenderer?.urlCanonical?.substringAfterLast('=')
            if (playlistId == null)
            {
                playlistId = response.header?.musicDetailHeaderRenderer?.menu?.menuRenderer?.topLevelButtons?.firstOrNull()
                    ?.buttonRenderer?.navigationEndpoint?.watchPlaylistEndpoint?.playlistId
            }
            return playlistId
        }

        fun getTitle(response: BrowseResponse): String? {
            val title = getHeader(response)?.title ?: response.header?.musicDetailHeaderRenderer?.title
            return title?.runs?.firstOrNull()?.text
        }

        fun getYear(response: BrowseResponse): Int? {
            val title = getHeader(response)?.subtitle ?: response.header?.musicDetailHeaderRenderer?.subtitle
            return title?.runs?.lastOrNull()?.text?.toIntOrNull()
        }

        fun getType(response: BrowseResponse): String? {
            val subtitle = getHeader(response)?.subtitle ?: response.header?.musicDetailHeaderRenderer?.subtitle
            return subtitle?.runs?.firstOrNull()?.text?.takeUnless { it.toIntOrNull() != null }
        }

        fun getThumbnail(response: BrowseResponse): String? {
            return response.background?.getThumbnailUrl() ?: response.header?.musicDetailHeaderRenderer?.thumbnail
                ?.getThumbnailUrl()
        }

        fun getArtists(response: BrowseResponse): List<Artist> {
            val artists = getHeader(response)?.straplineTextOne?.runs?.oddElements()?.map {
                Artist(
                    name = it.text,
                    id = it.navigationEndpoint?.browseEndpoint?.browseId
                )
            } ?: response.header?.musicDetailHeaderRenderer?.subtitle?.runs?.splitBySeparator()?.getOrNull(1)?.oddElements()?.map {
                Artist(
                    name = it.text,
                    id = it.navigationEndpoint?.browseEndpoint?.browseId
                )
            } ?: emptyList()

            return artists
        }

        private fun getHeader(response: BrowseResponse): MusicResponsiveHeaderRenderer? {
            val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs
                ?: response.contents?.twoColumnBrowseResultsRenderer?.tabs
            val section =
                tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            val header = section?.musicResponsiveHeaderRenderer
            return header
        }

        /**
         * Reads YT Music's current add/remove state directly from the album header.
         * Null means this response did not expose a library control, so callers should
         * preserve their existing local state instead of guessing.
         */
        fun getLibraryState(response: BrowseResponse): Boolean? {
            val responsiveToggle = getHeader(response)?.buttons
                ?.asSequence()
                ?.mapNotNull { it.toggleButtonRenderer }
                ?.firstOrNull { PageHelper.isLibraryIcon(it.defaultIcon?.iconType) }
            if (responsiveToggle?.isToggled != null) return responsiveToggle.isToggled

            val detailToggle = response.header?.musicDetailHeaderRenderer?.menu?.menuRenderer?.items
                ?.asSequence()
                ?.mapNotNull { it.toggleMenuServiceItemRenderer }
                ?.firstOrNull { PageHelper.isLibraryIcon(it.defaultIcon.iconType) }
            if (detailToggle?.isSelected != null) return detailToggle.isSelected

            // Older responses did not expose a boolean toggle state. Retain icon
            // inference only as a compatibility fallback for those responses.
            val icon = responsiveToggle?.defaultIcon?.iconType
                ?: detailToggle?.defaultIcon?.iconType
                ?: return null
            return PageHelper.isSavedLibraryIcon(icon)
        }

        fun getSongs(response: BrowseResponse, album: AlbumItem): List<SongItem> {
            val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs ?: response.contents?.twoColumnBrowseResultsRenderer?.tabs
            val primaryContents = tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents
                ?.firstOrNull()
                ?.musicShelfRenderer
                ?.contents
            val secondaryShelf = response.contents
                ?.twoColumnBrowseResultsRenderer
                ?.secondaryContents
                ?.sectionListRenderer
                ?.contents
                ?.firstOrNull()
            val shelfContents = primaryContents
                ?: secondaryShelf?.musicPlaylistShelfRenderer?.contents
                ?: secondaryShelf?.musicShelfRenderer?.contents

            val songs = shelfContents?.getItems()?.mapNotNull {
                getSong(it, album)
            }
            return songs ?: emptyList()
        }

        fun getSong(renderer: MusicResponsiveListItemRenderer, album: AlbumItem? = null): SongItem? {
            // Extract library tokens using the new method that properly handles multiple toggle items
            val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)
            val metadataRuns = renderer.flexColumns
                .drop(1)
                .flatMap { column ->
                    column.musicResponsiveListItemFlexColumnRenderer.text?.runs.orEmpty()
                } + renderer.fixedColumns.orEmpty().flatMap { column ->
                    column.musicResponsiveListItemFlexColumnRenderer.text?.runs.orEmpty()
                }

            val parsedDuration = renderer.fixedColumns?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                ?.text?.parseTime()

            // A track is playable only if it exposes a watch endpoint. Unreleased pre-save tracks
            // still carry a videoId in playlistItemData (and even a duration), but have NO watch
            // endpoint anywhere — flag those unavailable so the UI can grey them out and block play.
            val playableVideoId = renderer.navigationEndpoint?.watchEndpoint?.videoId
                ?: renderer.overlay?.musicItemThumbnailOverlayRenderer
                    ?.content?.musicPlayButtonRenderer
                    ?.playNavigationEndpoint?.watchEndpoint?.videoId
                ?: renderer.flexColumns.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text?.runs?.firstOrNull()
                    ?.navigationEndpoint?.watchEndpoint?.videoId

            return SongItem(
                id = renderer.playlistItemData?.videoId
                    ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
                    ?: renderer.overlay?.musicItemThumbnailOverlayRenderer
                        ?.content?.musicPlayButtonRenderer
                        ?.playNavigationEndpoint?.watchEndpoint?.videoId
                    ?: renderer.flexColumns.firstOrNull()
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text?.runs?.firstOrNull()
                        ?.navigationEndpoint?.watchEndpoint?.videoId
                    ?: return null,
                // Released tracks expose the title as a MUSIC_VIDEO-typed run; unreleased pre-save
                // tracks have no watch endpoint on the title run, so fall back to the plain first
                // flex-column text (otherwise the whole tracklist gets dropped).
                title = PageHelper.extractRuns(renderer.flexColumns, "MUSIC_VIDEO").firstOrNull()?.text
                    ?: renderer.flexColumns.firstOrNull()
                        ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
                    ?: return null,
                artists = PageHelper.extractRuns(renderer.flexColumns, "MUSIC_PAGE_TYPE_ARTIST").map{
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                }.ifEmpty {
                    // Label-uploaded albums (e.g. "OLAK5uy_…" art tracks) name the performing
                    // artist as a plain-text run with no artist link, while the album header
                    // strapline is the record label / distributor channel. Prefer that
                    // plain-text artist over inheriting the label as the track artist.
                    renderer.flexColumns.getOrNull(1)
                        ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                        ?.splitBySeparator()?.firstOrNull()?.oddElements()
                        ?.map { Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId) }
                        ?.filter { it.name.isNotBlank() }
                        ?.takeIf { it.isNotEmpty() }
                    // Final fallback: inherit the album artist when the row has no artist at all.
                        ?: album?.artists ?: emptyList()
                },
                album = album?.let {
                    Album(it.title, it.browseId)
                } ?: renderer.flexColumns.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.let {
                    Album(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId!!
                    )
                }!!,
                duration = parsedDuration,
                isAvailable = playableVideoId != null,
                viewsText = PageHelper.extractViewCountText(metadataRuns),
                musicVideoType = renderer.musicVideoType,
                thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: album?.thumbnail!!,
                explicit = renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
                libraryAddToken = libraryTokens.addToken,
                libraryRemoveToken = libraryTokens.removeToken,
                isInLibrary = PageHelper.extractLibraryStateFromMenuItems(renderer.menu?.menuRenderer?.items),
            )
        }
    }
}
