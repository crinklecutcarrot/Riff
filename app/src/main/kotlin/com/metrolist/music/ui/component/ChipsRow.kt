/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.constants.AlbumFilter
import com.metrolist.music.constants.AlbumFilterKey
import com.metrolist.music.constants.LibraryFilter
import com.metrolist.music.constants.SongFilter
import com.metrolist.music.constants.SongFilterKey
import com.metrolist.music.ui.screens.OptionStats
import com.metrolist.music.utils.rememberEnumPreference

@Composable
fun <E> ChipsRow(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    labelStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
    ) {
        Spacer(Modifier.width(12.dp))

        chips.forEach { (value, label) ->
            FilterChip(
                label = { Text(text = label, style = labelStyle) },
                selected = currentValue == value,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = containerColor,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                    selectedLabelColor = MaterialTheme.colorScheme.background,
                ),
                onClick = { onValueUpdate(value) },
                shape = RoundedCornerShape(16.dp),
                border = null
            )

            Spacer(Modifier.width(8.dp))
        }
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@Composable
fun <Int> ChoiceChipsRow(
    chips: List<Pair<Int, String>>,
    options: List<Pair<OptionStats, String>>,
    selectedOption: OptionStats,
    onSelectionChange: (OptionStats) -> Unit,
    currentValue: Int,
    onValueUpdate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    var expandIconDegree by remember { mutableFloatStateOf(0f) }
    val rotationAnimation by animateFloatAsState(
        targetValue = expandIconDegree,
        animationSpec = tween(durationMillis = 400),
        label = "",
    )

    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
    ) {
        var expanded by remember { mutableStateOf(false) }

        Column {
            AssistChip(
                onClick = {
                    expanded = !expanded
                    expandIconDegree -= 180
                },
                label = {
                    Text(
                        text =
                        when (selectedOption) {
                            OptionStats.WEEKS -> stringResource(id = R.string.weeks)
                            OptionStats.MONTHS -> stringResource(id = R.string.months)
                            OptionStats.YEARS -> stringResource(id = R.string.years)
                            OptionStats.CONTINUOUS -> stringResource(id = R.string.continuous)
                        },
                    )
                },
                trailingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.tabler_ic_chevron_down_outline),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer(rotationZ = rotationAnimation),
                    )
                },
                shape = RoundedCornerShape(16.dp),
                border = null,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = containerColor,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandIn() + fadeIn(),
                exit = shrinkOut() + fadeOut(),
            ) {
                DropdownMenu(
                    modifier = Modifier.padding(start = 12.dp),
                    expanded = expanded,
                    onDismissRequest = {
                        expanded = false
                        expandIconDegree -= 180
                    },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = option.second) },
                            onClick = {
                                onSelectionChange(option.first)
                                expandIconDegree -= 180
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        AnimatedContent(
            targetState = selectedOption,
            transitionSpec = { slideInHorizontally() + fadeIn() togetherWith slideOutHorizontally() + fadeOut() },
            label = "",
        ) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
            ) {
                chips.forEach { (value, label) ->
                    Spacer(Modifier.width(8.dp))

                    FilterChip(
                        label = { Text(label) },
                        selected = currentValue == value,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = containerColor,
                        ),
                        onClick = { onValueUpdate(value) },
                        shape = RoundedCornerShape(16.dp),
                        border = null
                    )
                }
            }
        }
    }
}

private val PillTabsHeight = 48.dp

/**
 * Library category selector that collapses to the active category once one is
 * picked: [X] [active category] | [sub-filter pills]. Tapping the X (or the
 * active pill) re-expands the full category chooser. Sub-filter pills are bound
 * to the same preference keys the library sub-screens read, so they stay in sync.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryPillTabs(
    filterType: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    labelStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val categories = listOf(
        LibraryFilter.LIBRARY to stringResource(R.string.filter_library),
        LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
        LibraryFilter.SONGS to stringResource(R.string.filter_songs),
        LibraryFilter.ALBUMS to stringResource(R.string.filter_albums),
        LibraryFilter.ARTISTS to stringResource(R.string.filter_artists),
        LibraryFilter.PODCASTS to stringResource(R.string.filter_podcasts),
    )

    // Library is the home/default (full chooser). Selecting any other category
    // collapses the row: the active pill physically translates to the left (a
    // shared element), the other pills cross-fade out, and the sub-filter pills
    // slide in from the right. The reverse plays on tapping the X.
    SharedTransitionLayout(
        // Fixed height so AnimatedContent never animates the container size
        // (the two states have slightly different intrinsic heights, which was
        // making the pill appear to grow/shrink mid-transition).
        modifier = modifier
            .fillMaxWidth()
            .height(PillTabsHeight)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
    ) {
        AnimatedContent(
            targetState = filterType,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(tween(250)) togetherWith fadeOut(tween(200)) using
                    SizeTransform(clip = false) { _, _ -> snap() }
            },
            label = "library_pill_tabs",
        ) { active ->
            val collapsed = active != LibraryFilter.LIBRARY
            if (!collapsed) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(12.dp))
                    categories.forEach { (category, label) ->
                        LibraryPill(
                            label = label,
                            selected = category == active,
                            containerColor = containerColor,
                            labelStyle = labelStyle,
                            onClick = { onFilterChange(category) },
                            modifier = Modifier.sharedBounds(
                                rememberSharedContentState(key = "pill_$category"),
                                this@AnimatedContent,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            } else {
                val activeLabel = categories.firstOrNull { it.first == active }?.second ?: ""
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(12.dp))
                    LibraryPill(
                        label = activeLabel,
                        selected = true,
                        showClear = true,
                        containerColor = containerColor,
                        labelStyle = labelStyle,
                        onClick = { onFilterChange(LibraryFilter.LIBRARY) },
                        modifier = Modifier.sharedBounds(
                            rememberSharedContentState(key = "pill_$active"),
                            this@AnimatedContent,
                        ),
                    )
                    if (active.hasSubFilters()) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .animateEnterExit(
                                    enter = fadeIn(tween(250, delayMillis = 80)) +
                                        slideInHorizontally(tween(320)) { it / 2 },
                                    exit = fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 2 },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PillDivider()
                            Row(
                                modifier = Modifier
                                    .clipToBounds()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LibrarySubFilterChips(active, containerColor, labelStyle)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LibraryFilter.hasSubFilters() =
    this == LibraryFilter.SONGS || this == LibraryFilter.ALBUMS

@Composable
private fun LibraryPill(
    label: String,
    selected: Boolean,
    containerColor: Color,
    labelStyle: TextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showClear: Boolean = false,
) {
    FilterChip(
        modifier = modifier,
        label = {
            Text(
                text = label,
                style = labelStyle,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        },
        selected = selected,
        leadingIcon = if (showClear) {
            {
                Icon(
                    painter = painterResource(R.drawable.tabler_ic_x_outline),
                    contentDescription = stringResource(R.string.close),
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.onBackground,
            selectedLabelColor = MaterialTheme.colorScheme.background,
            selectedLeadingIconColor = MaterialTheme.colorScheme.background,
        ),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = null,
    )
}

@Composable
private fun PillDivider() {
    Spacer(Modifier.width(6.dp))
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 22.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
    Spacer(Modifier.width(6.dp))
}

@Composable
private fun LibrarySubFilterChips(
    filterType: LibraryFilter,
    containerColor: Color,
    labelStyle: TextStyle,
) {
    when (filterType) {
        LibraryFilter.SONGS -> {
            var subFilter by rememberEnumPreference(SongFilterKey, SongFilter.LIBRARY)
            listOf(
                SongFilter.LIBRARY to stringResource(R.string.filter_library),
                SongFilter.LIKED to stringResource(R.string.filter_liked),
                SongFilter.UPLOADED to stringResource(R.string.filter_uploaded),
                SongFilter.DOWNLOADED to stringResource(R.string.filter_downloaded),
            ).forEach { (value, label) ->
                LibraryPill(
                    label = label,
                    selected = subFilter == value,
                    containerColor = containerColor,
                    labelStyle = labelStyle,
                    onClick = { subFilter = value },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        LibraryFilter.ALBUMS -> {
            var subFilter by rememberEnumPreference(AlbumFilterKey, AlbumFilter.LIBRARY)
            listOf(
                AlbumFilter.LIBRARY to stringResource(R.string.filter_library),
                AlbumFilter.UPLOADED to stringResource(R.string.filter_uploaded),
            ).forEach { (value, label) ->
                LibraryPill(
                    label = label,
                    selected = subFilter == value,
                    containerColor = containerColor,
                    labelStyle = labelStyle,
                    onClick = { subFilter = value },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        else -> Unit
    }
}
