package com.moonlightxr.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Composable
fun SpatialDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp) {
    Box(modifier.fillMaxWidth().height(thickness).background(SpatialTokens.outline.copy(alpha = 0.32f)))
}

@Composable
fun SpatialListItem(
    headline: String, modifier: Modifier = Modifier, supporting: String? = null,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null, selected: Boolean = false, enabled: Boolean = true,
    headlineFontSize: TextUnit = TextUnit.Unspecified,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var hovered by remember { mutableStateOf(false) }
    val fill = when {
        !enabled -> SpatialTokens.field.copy(alpha = 0.35f)
        pressed -> SpatialTokens.accentStrong
        selected -> SpatialTokens.accentStrong.copy(alpha = 0.8f)
        hovered -> SpatialTokens.field.copy(alpha = 0.86f)
        else -> SpatialTokens.field
    }
    val body = modifier.clip(SpatialTokens.controlShape).background(fill)
        .pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) when (awaitPointerEvent().type) {
                    PointerEventType.Enter -> hovered = true
                    PointerEventType.Exit -> hovered = false
                }
            }
        }
    val clickModifier = if (onClick != null) body.clickable(enabled = enabled, role = Role.Button,
        interactionSource = interactions, indication = null, onClick = onClick)
        .semantics { this.selected = selected } else body
    Row(clickModifier.heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            SpatialText(headline, color = if (!enabled) SpatialTokens.disabled else
                if (selected || pressed) SpatialTokens.onAccent else SpatialTokens.text,
                fontWeight = if (headlineFontSize == TextUnit.Unspecified ||
                    headlineFontSize == SpatialTokens.primaryMenuFontSize) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = headlineFontSize,
                lineHeight = if (headlineFontSize != TextUnit.Unspecified) headlineFontSize * 1.3f else TextUnit.Unspecified,
                style = MaterialTheme.typography.titleMedium)
            if (supporting != null) SpatialText(supporting,
                color = if (!enabled) SpatialTokens.disabled else
                    if (selected || pressed) SpatialTokens.onAccent else SpatialTokens.muted,
                style = MaterialTheme.typography.bodyMedium)
        }
        trailing?.invoke()
    }
}

@Composable
fun SpatialTitleBar(
    title: String, modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp,
    color: Color = SpatialTokens.text,
    minHeight: Dp = 48.dp,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = minHeight), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke(this)
        SpatialText(title, Modifier.weight(1f), color = color,
            fontSize = fontSize, fontWeight = FontWeight.Bold)
        trailing?.invoke(this)
    }
}

@Composable
fun SpatialLinearProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = modifier,
        color = SpatialTokens.accentStrong, trackColor = SpatialTokens.field)
}

@Composable
fun SpatialCircularProgress(progress: Float? = null, modifier: Modifier = Modifier) {
    if (progress == null) CircularProgressIndicator(modifier = modifier, color = SpatialTokens.accent)
    else CircularProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = modifier,
        color = SpatialTokens.accent, trackColor = SpatialTokens.field)
}

@Composable
fun SpatialPageControl(currentIndex: Int, total: Int, onSelect: (Int) -> Unit,
                       modifier: Modifier = Modifier) {
    require(total >= 0)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { index ->
            SpatialIconButton(stringResource(R.string.ui_page_number, index + 1),
                { onSelect(index) }, Modifier.size(32.dp)) {
                Box(Modifier.size(if (index == currentIndex) 10.dp else 7.dp)
                    .clip(SpatialTokens.controlShape)
                    .background(if (index == currentIndex) SpatialTokens.accent else SpatialTokens.muted))
            }
        }
    }
}

@Composable
fun SpatialSegmentControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
                          modifier: Modifier = Modifier) {
    val dense = LocalExtensionControlDensity.current
    Row(modifier.selectableGroup().clip(SpatialTokens.controlShape).background(SpatialTokens.field)
        .padding(if (dense) 2.dp else 4.dp),
        horizontalArrangement = Arrangement.spacedBy(if (dense) 3.dp else 4.dp)) {
        options.forEachIndexed { index, label ->
            SpatialOption(label, selectedIndex == index, { onSelect(index) },
                Modifier.weight(1f), compact = dense)
        }
    }
}

@Composable
fun SpatialSideNavigation(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SpatialPanel(modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(SpatialTokens.panelPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            header?.invoke()
            content()
        }
    }
}

/** ScrollState-backed indicator; dragging moves the content in either orientation. */
@Composable
fun BoxScope.SpatialScrollIndicator(
    state: ScrollState, modifier: Modifier = Modifier,
    orientation: Orientation = Orientation.Vertical, dismissAfterMs: Long = 3000,
) {
    if (state.maxValue <= 0) return
    var visible by remember(state) { mutableStateOf(false) }
    var dragging by remember(state) { mutableStateOf(false) }
    var lastValue by remember(state) { mutableIntStateOf(state.value) }
    LaunchedEffect(state.value, state.isScrollInProgress, dragging, dismissAfterMs) {
        if (state.value != lastValue || state.isScrollInProgress || dragging) visible = true
        lastValue = state.value
        if (!state.isScrollInProgress && !dragging) {
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
        // BoxWithConstraints composes later than its caller. Content/overlays can shrink the
        // ScrollState to zero between the outer guard and this subcomposition.
        val maximum = state.maxValue
        if (maximum <= 0) return@BoxWithConstraints
        val available = if (vertical) maxHeight else maxWidth
        val viewport = state.viewportSize.toFloat().coerceAtLeast(1f)
        val content = viewport + maximum
        val thumbExtent = (available * (viewport / content)).coerceAtLeast(40.dp)
            .coerceAtMost(available)
        val travel = (available - thumbExtent).coerceAtLeast(0.dp)
        val fraction = spatialScrollFraction(state.value, maximum)
        Box(Modifier.fillMaxWidth().fillMaxHeight().clip(SpatialTokens.controlShape)
            .background(SpatialTokens.field.copy(alpha = if (visible) 0.75f else 0.45f)))
        val thumb = if (vertical) Modifier.offset(y = travel * fraction)
            .fillMaxWidth().height(thumbExtent)
            else Modifier.offset(x = travel * fraction).width(thumbExtent).fillMaxHeight()
        Box(thumb
            .clip(SpatialTokens.controlShape)
            .background(SpatialTokens.accent.copy(alpha = if (visible) 1f else 0.75f))
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { target ->
                    if (!target.isFinite()) false else {
                        state.dispatchRawDelta(target.coerceIn(0f, 1f) * state.maxValue - state.value)
                        true
                    }
                }
            }
            .pointerInput(state.maxValue, travel, vertical) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, dragAmount ->
                    change.consume()
                    val travelPx = travel.toPx()
                    if (travelPx > 0f) state.dispatchRawDelta(
                        (if (vertical) dragAmount.y else dragAmount.x) * state.maxValue / travelPx)
                }
            })
    }
}

internal fun spatialScrollFraction(value: Int, maximum: Int): Float =
    if (maximum <= 0) 0f else value.coerceIn(0, maximum).toFloat() / maximum
