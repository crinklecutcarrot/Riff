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
                title = PageHelper.extractRuns(renderer.flexColumns, "MUSIC_VIDEO").firstOrNull()?.text ?: return null,
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
                duration = renderer.fixedColumns?.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.text?.parseTime() ?: return null,
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
