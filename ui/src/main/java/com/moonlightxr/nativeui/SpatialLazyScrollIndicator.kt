package com.moonlightxr.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Scroll-index mapping shared by list, grid and staggered-grid indicators. */
internal fun scrollIndexForFraction(fraction: Float, itemCount: Int, visibleItems: Int = 1): Int {
    val maxStart = (itemCount.coerceAtLeast(0) - visibleItems.coerceAtLeast(1)).coerceAtLeast(0)
    return ((fraction.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f) * maxStart).toInt()
}

@Composable
private fun BoxScope.SpatialLazyIndicator(
    itemCount: Int,
    firstVisibleItem: Int,
    visibleItems: Int,
    isScrolling: Boolean,
    orientation: Orientation,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dismissAfterMs: Long = 3000,
) {
    if (itemCount <= visibleItems || itemCount == 0) return
    var visible by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(isScrolling, dragging, dismissAfterMs) {
        if (isScrolling || dragging) {
            visible = true
        } else {
            delay(dismissAfterMs.coerceAtLeast(0))
            visible = false
        }
    }
    val vertical = orientation == Orientation.Vertical
    val container = if (vertical) modifier.align(Alignment.CenterEnd).width(18.dp)
        .fillMaxHeight().padding(horizontal = 5.dp)
        else modifier.align(Alignment.BottomCenter).height(18.dp)
            .fillMaxWidth().padding(vertical = 5.dp)
    BoxWithConstraints(container) {
        val available = if (vertical) maxHeight else maxWidth
        val thumbExtent = (available * (visibleItems.toFloat() / itemCount))
            .coerceAtLeast(40.dp).coerceAtMost(available)
        val travel = (available - thumbExtent).coerceAtLeast(0.dp)
        val maxIndex = (itemCount - visibleItems).coerceAtLeast(1)
        val fraction = (firstVisibleItem.toFloat() / maxIndex).coerceIn(0f, 1f)
        var seekFraction by remember(itemCount) { mutableStateOf(fraction) }
        LaunchedEffect(fraction, dragging) { if (!dragging) seekFraction = fraction }
        Box(Modifier.fillMaxWidth().fillMaxHeight().clip(SpatialTokens.controlShape)
            .background(SpatialTokens.field.copy(alpha = if (visible) 0.75f else 0.45f)))
        val thumb = if (vertical) Modifier.offset(y = travel * seekFraction)
            .fillMaxWidth().height(thumbExtent)
            else Modifier.offset(x = travel * seekFraction).width(thumbExtent).fillMaxHeight()
        Box(thumb
            .clip(SpatialTokens.controlShape)
            .background(SpatialTokens.accent.copy(alpha = if (visible) 1f else 0.75f))
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { target ->
                    if (!target.isFinite()) false else {
                        onSeek(scrollIndexForFraction(target, itemCount, visibleItems))
                        true
                    }
                }
            }
            .pointerInput(itemCount, travel, vertical) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, delta ->
                    change.consume()
                    val travelPx = travel.toPx()
                    if (travelPx > 0f) {
                        seekFraction = (seekFraction +
                            (if (vertical) delta.y else delta.x) / travelPx).coerceIn(0f, 1f)
                        onSeek(scrollIndexForFraction(seekFraction, itemCount, visibleItems))
                    }
                }
            })
    }
}

@Composable
fun BoxScope.SpatialScrollIndicator(
    state: LazyListState, modifier: Modifier = Modifier, dismissAfterMs: Long = 3000,
) {
    val scope = rememberCoroutineScope()
    val info = state.layoutInfo
    SpatialLazyIndicator(info.totalItemsCount, state.firstVisibleItemIndex,
        info.visibleItemsInfo.size, state.isScrollInProgress, info.orientation,
        { index -> scope.launch { state.scrollToItem(index) } }, modifier, dismissAfterMs)
}

@Composable
fun BoxScope.SpatialScrollIndicator(
    state: LazyGridState, modifier: Modifier = Modifier, dismissAfterMs: Long = 3000,
) {
    val scope = rememberCoroutineScope()
    val info = state.layoutInfo
    SpatialLazyIndicator(info.totalItemsCount, state.firstVisibleItemIndex,
        info.visibleItemsInfo.size, state.isScrollInProgress, info.orientation,
        { index -> scope.launch { state.scrollToItem(index) } }, modifier, dismissAfterMs)
}

@Composable
fun BoxScope.SpatialScrollIndicator(
    state: LazyStaggeredGridState, modifier: Modifier = Modifier, dismissAfterMs: Long = 3000,
) {
    val scope = rememberCoroutineScope()
    val info = state.layoutInfo
    SpatialLazyIndicator(info.totalItemsCount,
        state.firstVisibleItemIndex, info.visibleItemsInfo.size,
        state.isScrollInProgress, info.orientation,
        { index -> scope.launch { state.scrollToItem(index) } }, modifier, dismissAfterMs)
}
