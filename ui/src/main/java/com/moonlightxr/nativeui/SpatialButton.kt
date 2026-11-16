package com.moonlightxr.nativeui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Local dimensions tuned for MoonlightXR; PICO's public API names do not specify dp values. */
enum class SpatialButtonSize(val minHeight: Dp, val horizontalPadding: Dp, val verticalPadding: Dp, val iconGap: Dp) {
    Dense(32.dp, 8.dp, 3.dp, 4.dp),
    Small(40.dp, 12.dp, 6.dp, 6.dp),
    Regular(52.dp, 18.dp, 10.dp, 8.dp),
    Max(60.dp, 24.dp, 12.dp, 10.dp),
}

enum class SpatialButtonVariant { Primary, Secondary }

/** The equivalent of PICO ButtonColors, owned by this app and independent of its runtime. */
data class SpatialButtonColors(
    val container: Color,
    val content: Color,
    val hoveredContainer: Color,
    val pressedContainer: Color,
    val selectedContainer: Color,
    val disabledContainer: Color,
    val disabledContent: Color,
    val outline: Color,
)

object SpatialButtonDefaults {
    fun colors(variant: SpatialButtonVariant = SpatialButtonVariant.Secondary): SpatialButtonColors =
        when (variant) {
            SpatialButtonVariant.Primary -> SpatialButtonColors(
                container = SpatialTokens.accentStrong,
                content = SpatialTokens.onAccent,
                hoveredContainer = SpatialTokens.accent,
                pressedContainer = SpatialTokens.accentStrong.copy(alpha = 0.72f),
                selectedContainer = SpatialTokens.accentStrong,
                disabledContainer = SpatialTokens.field.copy(alpha = 0.38f),
                disabledContent = SpatialTokens.disabled,
                outline = SpatialTokens.outline,
            )
            SpatialButtonVariant.Secondary -> SpatialButtonColors(
                container = SpatialTokens.field,
                content = SpatialTokens.text,
                hoveredContainer = SpatialTokens.field.copy(alpha = 0.98f),
                pressedContainer = SpatialTokens.accentStrong,
                selectedContainer = SpatialTokens.accentStrong.copy(alpha = 0.86f),
                disabledContainer = SpatialTokens.field.copy(alpha = 0.38f),
                disabledContent = SpatialTokens.disabled,
                outline = SpatialTokens.outline,
            )
        }
}

internal data class SpatialButtonVisual(val container: Color, val content: Color, val border: Color, val borderWidth: Dp)

internal fun resolveSpatialButtonVisual(
    colors: SpatialButtonColors, enabled: Boolean, hovered: Boolean, pressed: Boolean, selected: Boolean,
): SpatialButtonVisual {
    val container = when {
        !enabled -> colors.disabledContainer
        pressed -> colors.pressedContainer
        selected -> colors.selectedContainer
        hovered -> colors.hoveredContainer
        else -> colors.container
    }
    val emphasized = enabled && (hovered || pressed || selected)
    return SpatialButtonVisual(
        container,
        if (!enabled) colors.disabledContent else if (pressed || selected)
            SpatialTokens.onAccent else colors.content,
        colors.outline.copy(alpha = if (emphasized) 1f else if (enabled) 0.4f else 0.2f),
        if (emphasized) 1.5.dp else 0.7.dp,
    )
}

/**
 * App-owned Compose counterpart of the PICO Spatial UI Button contract: size, colors, optional
 * icon slots and enabled/hover/pressed feedback. Existing `selected` callers are retained for
 * menu choices; it is not a claim that PICO Button itself has a selected parameter.
 */
@Composable
fun SpatialButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
    size: SpatialButtonSize = if (compact) SpatialButtonSize.Small else SpatialButtonSize.Regular,
    variant: SpatialButtonVariant = SpatialButtonVariant.Secondary,
    colors: SpatialButtonColors = SpatialButtonDefaults.colors(variant),
    leadingIcon: (@Composable RowScope.() -> Unit)? = null,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    labelFontSize: TextUnit = TextUnit.Unspecified,
) {
    SpatialButtonBase(onClick, modifier, selected, enabled, size, colors, leadingIcon, trailingIcon) {
        SpatialText(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center, fontSize = labelFontSize,
            lineHeight = if (labelFontSize != TextUnit.Unspecified) labelFontSize * 1.3f else TextUnit.Unspecified)
    }
}

/** Slot-based form for controls that need custom content rather than a single text label. */
@Composable
fun SpatialButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: SpatialButtonSize = SpatialButtonSize.Regular,
    variant: SpatialButtonVariant = SpatialButtonVariant.Secondary,
    colors: SpatialButtonColors = SpatialButtonDefaults.colors(variant),
    leadingIcon: (@Composable RowScope.() -> Unit)? = null,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    SpatialButtonBase(onClick, modifier, selected, enabled, size, colors, leadingIcon, trailingIcon, content)
}

@Composable
private fun SpatialButtonBase(
    onClick: () -> Unit,
    modifier: Modifier,
    selected: Boolean,
    enabled: Boolean,
    size: SpatialButtonSize,
    colors: SpatialButtonColors,
    leadingIcon: (@Composable RowScope.() -> Unit)?,
    trailingIcon: (@Composable RowScope.() -> Unit)?,
    content: @Composable RowScope.() -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var hovered by remember { mutableStateOf(false) }
    val visual = resolveSpatialButtonVisual(colors, enabled, hovered, pressed, selected)
    val typography = when (size) {
        SpatialButtonSize.Dense -> MaterialTheme.typography.labelMedium.copy(fontSize = 16.sp, lineHeight = 20.sp)
        SpatialButtonSize.Small -> MaterialTheme.typography.labelMedium.copy(fontSize = 16.sp, lineHeight = 20.sp)
        SpatialButtonSize.Regular -> MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp, lineHeight = 23.sp)
        SpatialButtonSize.Max -> MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp)
    }
    Row(
        modifier
            .clip(SpatialTokens.controlShape)
            .background(visual.container)
            .border(BorderStroke(visual.borderWidth, visual.border), SpatialTokens.controlShape)
            .pointerInput(enabled) {
                awaitPointerEventScope {
                    while (true) {
                        when (awaitPointerEvent().type) {
                            PointerEventType.Enter -> hovered = true
                            PointerEventType.Exit -> hovered = false
                        }
                    }
                }
            }
            .semantics { this.selected = selected }
            .clickable(interactionSource = interactions, indication = null, enabled = enabled,
                role = Role.Button, onClick = onClick)
            .heightIn(min = size.minHeight)
            .padding(horizontal = size.horizontalPadding, vertical = size.verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(size.iconGap, Alignment.CenterHorizontally),
    ) {
        CompositionLocalProvider(LocalContentColor provides visual.content) {
            ProvideTextStyle(typography) {
                leadingIcon?.invoke(this)
                content()
                trailingIcon?.invoke(this)
            }
        }
    }
}
