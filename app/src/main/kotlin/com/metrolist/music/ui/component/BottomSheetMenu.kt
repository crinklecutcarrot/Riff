/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalMenuState = compositionLocalOf { MenuState() }

@Stable
class MenuState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)
    var presentationId by mutableIntStateOf(0)
        private set
    var preferredHeightFraction by mutableStateOf(0f)
        private set

    fun show(content: @Composable ColumnScope.() -> Unit) {
        // Install the new menu before revealing the sheet. Showing first lets
        // Material measure the previous (often shorter) menu and animate to a
        // stale anchor, which leaves the next menu stranded partway onscreen.
        this.content = content
        preferredHeightFraction = 0f
        presentationId++
        isVisible = true
    }

    fun requestHeightFraction(fraction: Float) {
        preferredHeightFraction = fraction.coerceIn(0f, 0.84f)
    }

    fun dismiss() {
        isVisible = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedBottomSheet(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    LaunchedEffect(isVisible) {
        if (isVisible) {
            // Give the content one frame to establish its expanded anchor.
            withFrameNanos { }
            sheetState.expand()
        } else {
            sheetState.hide()
        }
    }

    if (!sheetState.isVisible && !isVisible) {
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState,
    background: Color = MaterialTheme.colorScheme.surface,
) {
    val focusManager = LocalFocusManager.current
    val menuMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.84f
    // A fresh SheetState for every presentation prevents an expanded anchor
    // measured for one menu from being reused by the next menu.
    key(state.presentationId) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val density = LocalDensity.current
        val scrollState = rememberScrollState()
        val menuMaxHeightPx = with(density) { menuMaxHeight.roundToPx() }
        var viewportHeightPx by remember { mutableIntStateOf(0) }
        var resolvedHeightPx by remember { mutableIntStateOf(0) }

        // Scrollable content initially reports only its viewport to the sheet.
        // Resolve the real height from viewport + overflow, so every option is
        // visible when it fits and genuinely long menus stop at the screen cap.
        LaunchedEffect(viewportHeightPx, scrollState.maxValue) {
            if (viewportHeightPx > 0 && scrollState.maxValue > 0) {
                val contentHeight = (viewportHeightPx + scrollState.maxValue).coerceAtMost(menuMaxHeightPx)
                if (contentHeight > resolvedHeightPx) {
                    resolvedHeightPx = contentHeight
                    withFrameNanos { }
                    if (state.isVisible) sheetState.expand()
                }
            }
        }

        LaunchedEffect(state.preferredHeightFraction) {
            if (state.preferredHeightFraction > 0f && state.isVisible) {
                withFrameNanos { }
                sheetState.expand()
            }
        }

        AnimatedBottomSheet(
            isVisible = state.isVisible,
            onDismissRequest = {
                focusManager.clearFocus()
                state.isVisible = false
            },
            sheetState = sheetState,
            sheetMaxWidth = Dp.Unspecified,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = background,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 6.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            },
            scrimColor = Color.Black.copy(alpha = 0.55f),
            contentWindowInsets = { WindowInsets(0) },
            // Wrap short menus, but cap long menus at the same deterministic
            // viewport where their option list owns overflow.
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (state.preferredHeightFraction > 0f) {
                        Modifier.height(LocalConfiguration.current.screenHeightDp.dp * state.preferredHeightFraction)
                    } else if (resolvedHeightPx > 0) {
                        Modifier.height(with(density) { resolvedHeightPx.toDp() })
                    } else {
                        Modifier.heightIn(max = menuMaxHeight)
                    },
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (state.preferredHeightFraction > 0f) {
                            Modifier.height(LocalConfiguration.current.screenHeightDp.dp * state.preferredHeightFraction)
                        } else if (resolvedHeightPx > 0) {
                            Modifier.height(with(density) { resolvedHeightPx.toDp() })
                        } else {
                            Modifier.heightIn(max = menuMaxHeight)
                        },
                    )
                    .onSizeChanged { viewportHeightPx = it.height }
                    .verticalScroll(scrollState)
                    .padding(horizontal = 10.dp),
            ) {
                state.content(this)
            }
        }
    }
}
