package com.moonlightxr.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Applied only inside the wheel extension Quad; full-size pages keep their controls. */
internal val LocalExtensionControlDensity = compositionLocalOf { false }
enum class ExtensionRowDecoration { Card, Divider }
internal val LocalExtensionRowDecoration = compositionLocalOf { ExtensionRowDecoration.Card }
internal val LocalSpatialScrollScope = compositionLocalOf { SpatialScrollScope.Content }

/** Native stick input is integrated over time; Android/QA wheel events share 20 px/unit. */
internal fun extensionScrollPixels(wheelUnits: Float): Float =
    if (wheelUnits.isFinite()) -wheelUnits * 20f else 0f

/** Keep parameter decoration in the extension only; other menus retain their own layout. */
@Composable
internal fun SpatialParameterRow(content: @Composable () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) UiFeedback.hover() }
    Box(Modifier.fillMaxWidth().hoverable(interactions)) {
        SpatialParameterRowLayout(content)
    }
}
@Composable
private fun SpatialParameterRowLayout(content: @Composable () -> Unit) {
    if (LocalGroupedParameters.current) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) { content() }
        return
    }
    if (!LocalExtensionControlDensity.current) {
        content()
        return
    }
    when (LocalExtensionRowDecoration.current) {
        ExtensionRowDecoration.Card -> {
            val shape = RoundedCornerShape(9.dp)
            Column(Modifier.fillMaxWidth().clip(shape)
                .background(SpatialTokens.field.copy(alpha = 0.42f))
                .border(1.dp, SpatialTokens.outline.copy(alpha = 0.28f), shape)
                .padding(horizontal = 8.dp, vertical = 2.dp)) { content() }
        }
        ExtensionRowDecoration.Divider -> {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                content()
                SpatialDivider(Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
fun SpatialMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    SpatialListItem(label, Modifier.fillMaxWidth(), onClick = onClick, selected = selected,
        headlineFontSize = SpatialTokens.primaryMenuFontSize)
}

/** Android-style trailing value and double chevron; its choice list stays in the source Quad. */
@Composable
fun SpatialDropdown(label: String, options: List<String>, value: String,
                    onValueChange: (String) -> Unit, popupHost: MenuUiState? = null,
                    compact: Boolean = false, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    val dense = compact || LocalExtensionControlDensity.current
    val host = popupHost ?: LocalParameterHelpHost.current
    val panelId = LocalParameterPanelId.current
    val controlKey = LocalParameterIdentity.current ?: label
    val active = expanded || host?.inlineChoices?.let { it.controlKey == controlKey && it.anchor.panelId == panelId } == true
    LaunchedEffect(enabled, host, panelId, controlKey) {
        if (!enabled) {
            expanded = false
            host?.dismissOwnedInlineChoices(panelId, controlKey)
        }
    }
    DisposableEffect(host, panelId, controlKey) {
        onDispose { host?.dismissOwnedInlineChoices(panelId, controlKey) }
    }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    SpatialParameterRow { Column(verticalArrangement = Arrangement.spacedBy(if (dense) 2.dp else 4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = if (dense) 36.dp else 44.dp)
            .recordParameterControl(label)
            .hoverable(interactions, enabled)
            .clickable(interactionSource = interactions, indication = null, enabled = enabled, role = Role.Button) {
                if (host != null) {
                    if (active) host.dismissInlineChoices()
                    else host.parameterControlAnchors["$panelId:$controlKey"]?.let { anchor ->
                        host.showOwnedInlineChoices(label, options, value, anchor, controlKey, onValueChange)
                    }
                } else expanded = !expanded
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialText(label, Modifier.weight(1f), color = if (enabled) SpatialTokens.text else SpatialTokens.disabled, maxLines = 1)
            Row(Modifier.width(if (dense) 132.dp else 156.dp)
                .heightIn(min = if (dense) 36.dp else 44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (active || hovered) SpatialTokens.field.copy(alpha = 0.4f) else Color.Transparent)
                .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SpatialText(value, Modifier.weight(1f), color = SpatialTokens.muted, fontSize = 14.sp,
                    textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val arrowColor = if (active) SpatialTokens.accent else SpatialTokens.muted
                Canvas(Modifier.size(12.dp, 18.dp)) {
                    val stroke = 1.5.dp.toPx()
                    fun line(ax: Float, ay: Float, bx: Float, by: Float) {
                        drawLine(arrowColor, Offset(size.width * ax, size.height * ay),
                            Offset(size.width * bx, size.height * by), strokeWidth = stroke, cap = StrokeCap.Round)
                    }
                    line(0.2f, 0.35f, 0.5f, 0.15f); line(0.5f, 0.15f, 0.8f, 0.35f)
                    line(0.2f, 0.65f, 0.5f, 0.85f); line(0.5f, 0.85f, 0.8f, 0.65f)
                }
            }
            SpatialParameterHelp(label)
        }
        if (expanded && host == null) {
            Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    SpatialOption(option, option == value, {
                        onValueChange(option)
                        expanded = false
                    }, Modifier.fillMaxWidth())
                }
            }
        }
    } }
}

@Composable
fun SpatialToggle(label: String, checked: Boolean, compact: Boolean = false,
                  enabled: Boolean = true,
                  onCheckedChange: (Boolean) -> Unit) {
    val dense = compact || LocalExtensionControlDensity.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    SpatialParameterRow { Row(Modifier.fillMaxWidth().heightIn(min = if (dense) 36.dp else 52.dp)
        .recordParameterControl(label)
        .background(when {
            pressed -> SpatialTokens.accent.copy(alpha = 0.28f)
            hovered -> SpatialTokens.accent.copy(alpha = 0.16f)
            else -> Color.Transparent
        })
        .hoverable(interactions, enabled)
        .toggleable(value = checked, interactionSource = interactions, indication = null,
            enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpatialText(label, Modifier.weight(1f), color = if (enabled) SpatialTokens.text else SpatialTokens.disabled, maxLines = 1)
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            SpatialSwitch(checked, null, enabled = enabled)
        }
        SpatialParameterHelp(label)
    } }
}

@Composable
fun SpatialSlider(label: String, value: Float, displayMaximumPercent: Int = 100,
                  compact: Boolean = false,
                  enabled: Boolean = true,
                  onValueChange: (Float) -> Unit) {
    require(displayMaximumPercent > 0)
    val dense = compact || LocalExtensionControlDensity.current
    SpatialParameterRow { Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SpatialText(label, Modifier.weight(1f),
                color = if (enabled) SpatialTokens.text else SpatialTokens.disabled)
            SpatialText("${(value * displayMaximumPercent).roundToInt()}%",
                color = if (enabled) SpatialTokens.muted else SpatialTokens.disabled)
            SpatialParameterHelp(label)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SpatialRepeatStepButton("−", enabled = enabled && value > 0f, compact = dense,
                modifier = Modifier.recordParameterControl("$label:minus")) {
                onValueChange(adjustPercent(value, -1, displayMaximumPercent))
            }
            SpatialRangeSlider(value, onValueChange,
                Modifier.weight(1f).then(if (dense) Modifier.height(30.dp) else Modifier)
                    .recordParameterControl("$label:slider"),
                valueRange = 0f..1f, enabled = enabled, compactThumb = dense)
            SpatialRepeatStepButton("+", enabled = enabled && value < 1f, compact = dense,
                modifier = Modifier.recordParameterControl("$label:plus")) {
                onValueChange(adjustPercent(value, 1, displayMaximumPercent))
            }
        }
    } }
}

/** Integer-valued settings use visible ticks and snap points, not percentage formatting. */
@Composable
fun SpatialDiscreteParameterSlider(label: String, value: Int, maximum: Int = PictureFilterLevels.Maximum,
                                   onValueChange: (Int) -> Unit) {
    require(maximum > 0)
    val dense = LocalExtensionControlDensity.current
    val level = value.coerceIn(0, maximum)
    SpatialParameterRow { Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SpatialText(label, Modifier.weight(1f), color = SpatialTokens.text)
            SpatialText(level.toString(), color = SpatialTokens.muted)
            SpatialParameterHelp(label)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SpatialRepeatStepButton("−", enabled = level > 0, compact = dense,
                modifier = Modifier.recordParameterControl("$label:minus")) { onValueChange(level - 1) }
            SpatialSegmentSlider(level, maximum, onValueChange,
                Modifier.weight(1f).then(if (dense) Modifier.height(30.dp) else Modifier)
                    .recordParameterControl("$label:slider"), compactThumb = dense)
            SpatialRepeatStepButton("+", enabled = level < maximum, compact = dense,
                modifier = Modifier.recordParameterControl("$label:plus")) { onValueChange(level + 1) }
        }
    } }
}

internal fun adjustPercent(value: Float, deltaPercent: Int, maximumPercent: Int = 100): Float {
    require(maximumPercent > 0)
    return ((value * maximumPercent).roundToInt() + deltaPercent)
        .coerceIn(0, maximumPercent) / maximumPercent.toFloat()
}

@Composable
fun SpatialStepper(label: String, value: Int, range: IntRange, compact: Boolean = false,
                   onValueChange: (Int) -> Unit) {
    val dense = compact || LocalExtensionControlDensity.current
    SpatialParameterRow { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        SpatialText(label, Modifier.weight(1f), color = SpatialTokens.text)
        SpatialNumberField(value, onValueChange, range = range, editable = false, compact = dense)
        SpatialParameterHelp(label)
    } }
}

@Composable
fun SpatialScrollArea(modifier: Modifier = Modifier, scroll: ScrollState = rememberScrollState(),
                      reserveIndicatorSpace: Boolean = true,
                      content: @Composable () -> Unit) {
    val helpHost = LocalParameterHelpHost.current
    val panelId = LocalParameterPanelId.current
    val scope = LocalSpatialScrollScope.current
    val registry = helpHost?.scrollRegistry
    val owner = remember(registry, panelId, scope, scroll) { Any() }
    DisposableEffect(registry, owner) {
        onDispose { registry?.remove(owner) }
    }
    LaunchedEffect(scroll.value) { helpHost?.hideParameterHelp() }
    androidx.compose.foundation.layout.Box(modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        registry?.update(owner, SpatialScrollViewport(panelId, scope,
            bounds.left, bounds.top, bounds.right, bounds.bottom)) { pixels ->
            scroll.dispatchRawDelta(pixels)
        }
    }) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll)
            .padding(end = if (reserveIndicatorSpace) 18.dp else 0.dp)) { content() }
        SpatialScrollIndicator(scroll)
    }
}
