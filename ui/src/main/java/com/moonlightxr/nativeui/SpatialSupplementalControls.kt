package com.moonlightxr.nativeui

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.offset

@Composable
fun SpatialButtonChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SpatialChip(label, onClick, modifier)
}

@Composable
fun SpatialRemovableChip(label: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(SpatialTokens.controlShape).background(SpatialTokens.field)
        .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SpatialText(label, color = SpatialTokens.text)
        SpatialIconButton(stringResource(R.string.ui_remove, spatialString(label)), onRemove,
            Modifier.size(32.dp)) { SpatialText("×", color = SpatialTokens.text) }
    }
}

@Composable
fun SpatialDotBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(9.dp).clip(SpatialTokens.controlShape).background(SpatialTokens.accent))
}

@Composable
fun SpatialNumberBadge(number: Int, modifier: Modifier = Modifier, overflowAt: Int = 99) {
    SpatialBadge(if (number > overflowAt) "$overflowAt+" else number.coerceAtLeast(0).toString(), modifier)
}

@Composable
fun SpatialTriStateCheckbox(state: ToggleableState, onClick: () -> Unit,
                            modifier: Modifier = Modifier, enabled: Boolean = true) {
    TriStateCheckbox(state = state, onClick = onClick, modifier = modifier, enabled = enabled,
        colors = CheckboxDefaults.colors(
            checkedColor = SpatialTokens.accentStrong,
            uncheckedColor = SpatialTokens.outline,
            checkmarkColor = SpatialTokens.onAccent))
}

@Composable
fun SpatialSymbolSlider(value: Float, onValueChange: (Float) -> Unit,
                        symbol: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        symbol()
        SpatialRangeSlider(value, onValueChange, Modifier.weight(1f))
    }
}

@Composable
fun SpatialProgressPageControl(currentIndex: Int, total: Int, progress: Float,
                               onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpatialPageControl(currentIndex, total, onSelect)
        SpatialCircularProgress(progress, Modifier.size(22.dp))
    }
}

/** Compose selection actions are exposed as a themed toolbar within the same surface. */
private class SpatialTextToolbarState : TextToolbar {
    override var status by mutableStateOf(TextToolbarStatus.Hidden)
        private set
    var selectionRect by mutableStateOf(Rect.Zero)
        private set
    var copy by mutableStateOf<(() -> Unit)?>(null)
        private set
    var paste by mutableStateOf<(() -> Unit)?>(null)
        private set
    var cut by mutableStateOf<(() -> Unit)?>(null)
        private set
    var selectAll by mutableStateOf<(() -> Unit)?>(null)
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        selectionRect = rect
        copy = onCopyRequested
        paste = onPasteRequested
        cut = onCutRequested
        selectAll = onSelectAllRequested
        status = TextToolbarStatus.Shown
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
    }
}

@Composable
fun SpatialTextSelection(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SpatialTextMenuHost(modifier) { SelectionContainer { content() } }
}

/** Wrap a complete surface so editors share its bounds without making every label selectable. */
@Composable
internal fun SpatialTextMenuHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val toolbar = remember { SpatialTextToolbarState() }
    val provider = remember { SpatialTextContextMenuProvider() }
    DisposableEffect(provider) { onDispose { provider.close() } }
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    CompositionLocalProvider(LocalTextToolbar provides toolbar,
        LocalTextContextMenuToolbarProvider provides provider,
        LocalTextContextMenuDropdownProvider provides provider) {
        Box(modifier.onGloballyPositioned {
            origin = it.positionInRoot()
            coordinates = it
            containerSize = it.size
        }) {
            content()
            val session = provider.active
            val destination = coordinates?.takeIf { it.isAttached }
            val modernAnchor = if (session != null && destination != null)
                session.data.contentBounds(destination) else null
            if (modernAnchor != null || toolbar.status == TextToolbarStatus.Shown) {
                val anchor = modernAnchor ?: toolbar.selectionRect.translate(-origin)
                val offset = selectionToolbarOffset(anchor, containerSize, toolbarSize,
                    with(density) { 8.dp.toPx() })
                FlowRow(
                    Modifier.offset { offset }
                        .widthIn(max = with(density) { containerSize.width.toDp() })
                        .onGloballyPositioned { toolbarSize = it.size }
                        .clip(SpatialTokens.controlShape)
                        .background(SpatialTokens.popupBottom)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (session != null) {
                        session.data.data().components.filterIsInstance<TextContextMenuItem>().forEach { item ->
                            SpatialButton(item.label, {
                                item.onClick(session)
                                session.close()
                            }, compact = true)
                        }
                    } else {
                        toolbar.copy?.let { SpatialButton("复制", { it(); toolbar.hide() }, compact = true) }
                        toolbar.paste?.let { SpatialButton("粘贴", { it(); toolbar.hide() }, compact = true) }
                        toolbar.cut?.let { SpatialButton("剪切", { it(); toolbar.hide() }, compact = true) }
                        toolbar.selectAll?.let { SpatialButton("全选", { it(); toolbar.hide() }, compact = true) }
                    }
                }
            }
        }
    }
}
