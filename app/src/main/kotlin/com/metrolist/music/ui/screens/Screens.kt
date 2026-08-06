/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrolist.music.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.tabler_ic_home_outline,
        iconIdActive = R.drawable.tabler_ic_home_filled,
        route = "home"
    )

    object Explore : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.tabler_ic_search_outline,
        iconIdActive = R.drawable.tabler_ic_search_outline,
        route = "explore"
    )

    object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.tabler_ic_search_outline,
        iconIdActive = R.drawable.tabler_ic_search_outline,
        route = "search_input"
    )

    object ListenTogether : Screens(
        titleId = R.string.together,
        iconIdInactive = R.drawable.tabler_ic_users_outline,
        iconIdActive = R.drawable.tabler_ic_users_outline,
        route = "listen_together"
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.tabler_ic_library_outline,
        iconIdActive = R.drawable.tabler_ic_library_filled,
        route = "library"
    )

    companion object {
        // Search is now the Explore/Search/Discover hub. Search remains a
        // routable screen for focused input and results, but is no longer a
        // separate bottom-navigation destination.
        val MainScreens = listOf(Home, Explore, ListenTogether, Library)
    }
}
