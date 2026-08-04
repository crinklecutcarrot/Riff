package com.metrolist.innertube.utils

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.LibraryPage
import com.metrolist.innertube.pages.PlaylistPage
import timber.log.Timber
import java.security.MessageDigest

@JvmName("completedLibrary")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> = runCatching {
    val page = getOrThrow()
    val songs = page.songs.toMutableList()
    var continuation = page.songsContinuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    // Large libraries routinely exceed 50 pages. Repeated-token detection below
    // is the real infinite-loop guard; this ceiling is only a final safety valve.
    val maxRequests = 1000
    var consecutiveEmptyResponses = 0
    
    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) {
            error("Playlist pagination repeated a continuation token")
        }
        seenContinuations.add(continuation)
        requestCount++
        
        val continuationPage = YouTube.playlistContinuation(continuation).getOrThrow()
        
        if (continuationPage.songs.isEmpty()) {
            consecutiveEmptyResponses++
            // YouTube occasionally returns one or more empty continuation pages at
            // the natural end of large libraries. The token can still change, so
            // repeated-token detection alone cannot identify this terminal state.
            if (consecutiveEmptyResponses >= 2) {
                continuation = null
                break
            }
        } else {
            consecutiveEmptyResponses = 0
            songs += continuationPage.songs
        }
        
        continuation = continuationPage.continuation
    }
    if (continuation != null) error("Playlist pagination exceeded $maxRequests requests")
    PlaylistPage(
        playlist = page.playlist,
        songs = songs,
        songsContinuation = null,
        continuation = page.continuation
    )
}

@JvmName("completedPlaylist")
suspend fun Result<LibraryPage>.completed(): Result<LibraryPage> = runCatching {
    val page = getOrThrow()
    val items = page.items.toMutableList()
    var continuation = page.continuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    // Large libraries routinely exceed 50 pages. Repeated-token detection below
    // is the real infinite-loop guard; this ceiling is only a final safety valve.
    val maxRequests = 1000
    var consecutiveEmptyResponses = 0
    
    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) {
            error("Library pagination repeated a continuation token")
        }
        seenContinuations.add(continuation)
        requestCount++
        
        val continuationPage = YouTube.libraryContinuation(continuation).getOrThrow()
        if (requestCount == 1 || requestCount % 25 == 0) {
            Timber.d(
                "Library pagination page=%d pageItems=%d totalItems=%d",
                requestCount,
                continuationPage.items.size,
                items.size + continuationPage.items.size,
            )
        }
        
        if (continuationPage.items.isEmpty()) {
            consecutiveEmptyResponses++
            // Large YouTube Music library feeds commonly finish with empty
            // continuation responses rather than a response without a token.
            if (consecutiveEmptyResponses >= 2) {
                continuation = null
                break
            }
        } else {
            consecutiveEmptyResponses = 0
            items += continuationPage.items
        }
        
        continuation = continuationPage.continuation
    }
    if (continuation != null) error("Library pagination exceeded $maxRequests requests")
    Timber.d("Library pagination completed pages=%d items=%d", requestCount, items.size)
    LibraryPage(
        items = items,
        continuation = null
    )
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun sha1(str: String): String = MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()

fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split("; ")
        .filter { it.isNotEmpty() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) null
            else part.substring(0, splitIndex) to part.substring(splitIndex + 1)
        }
        .toMap()

fun String.parseTime(): Int? {
    try {
        // YouTube Music returns duration with locale-dependent separators
        // (":" en-US, "." some locales, "," EU). Accept all.
        val parts = split(Regex("[:.,]")).map { it.toInt() }
        if (parts.size == 2) {
            return parts[0] * 60 + parts[1]
        }
        if (parts.size == 3) {
            return parts[0] * 3600 + parts[1] * 60 + parts[2]
        }
    } catch (e: Exception) {
        return null
    }
    return null
}

fun isPrivateId(browseId: String): Boolean {
    return browseId.contains("privately")
}
