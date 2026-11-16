package com.moonlightxr.nativeui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

/** PICO Option counterpart: a selected choice, not an action Button. */
@Composable
fun SpatialOption(
    label: String,
    selected: Boolean,
    onSelectChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
    inlineMenu: Boolean = false,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var hovered by remember { mutableStateOf(false) }
    val fill = when {
        !enabled -> SpatialTokens.field.copy(alpha = 0.35f)
        pressed -> SpatialTokens.accentStrong
        selected -> SpatialTokens.accentStrong.copy(alpha = 0.86f)
        hovered -> SpatialTokens.field.copy(alpha = 0.86f)
        inlineMenu -> Color.Transparent
        else -> SpatialTokens.field
    }
    val shape = if (inlineMenu) RoundedCornerShape(0.dp) else SpatialTokens.controlShape
    Row(
        modifier.clip(shape).background(fill)
            .then(if (inlineMenu) Modifier else Modifier.border(if (selected || hovered) 1.5.dp else 0.7.dp,
                SpatialTokens.outline.copy(alpha = if (enabled) 0.8f else 0.2f), shape))
            .pointerInput(enabled) {
                awaitPointerEventScope {
                    while (true) when (awaitPointerEvent().type) {
                        PointerEventType.Enter -> hovered = true
                        PointerEventType.Exit -> hovered = false
                    }
                }
            }
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton,
                interactionSource = interactions, indication = null,
                onClick = { onSelectChange(true) })
            .heightIn(min = if (compact) 30.dp else 40.dp)
            .padding(horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 3.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        val optionContent = when {
            !enabled -> SpatialTokens.disabled
            pressed || selected -> SpatialTokens.onAccent
            else -> SpatialTokens.text
        }
        CompositionLocalProvider(LocalContentColor provides optionContent) {
            leadingIcon?.invoke()
            SpatialText(label, color = optionContent, fontSize = if (inlineMenu) 14.sp else TextUnit.Unspecified,
                fontWeight = FontWeight.Medium, maxLines = if (inlineMenu) 1 else Int.MAX_VALUE)
        }
    }
}

@Composable
fun SpatialToggleButton(
    label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var hovered by remember { mutableStateOf(false) }
    val fill = when {
        pressed -> SpatialTokens.accent
        checked -> SpatialTokens.accentStrong
        hovered -> SpatialTokens.field.copy(alpha = 0.86f)
        else -> SpatialTokens.field
    }
    Box(modifier.clip(SpatialTokens.controlShape)
        .background(if (enabled) fill else fill.copy(alpha = 0.35f))
        .border(BorderStroke(1.dp, SpatialTokens.outline.copy(alpha = if (enabled) 0.8f else 0.2f)),
            SpatialTokens.controlShape)
        .pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) when (awaitPointerEvent().type) {
                    PointerEventType.Enter -> hovered = true
                    PointerEventType.Exit -> hovered = false
                }
            }
        }
        .toggleable(checked, enabled = enabled, role = Role.Checkbox,
            interactionSource = interactions, indication = null, onValueChange = onCheckedChange)
        .heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        SpatialText(label, color = if (enabled) SpatialTokens.text else SpatialTokens.disabled)
    }
}

@Composable
fun SpatialIconButton(
    contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, content: @Composable () -> Unit,
) {
    val localizedDescription = spatialString(contentDescription)
    SpatialButton(onClick = onClick,
        modifier = modifier.size(44.dp).semantics { this.contentDescription = localizedDescription },
        enabled = enabled, size = SpatialButtonSize.Small) { content() }
}

@Composable
fun SpatialToggleIconButton(
    contentDescription: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit,
) {
    val localizedDescription = spatialString(contentDescription)
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var hovered by remember { mutableStateOf(false) }
    val fill = when {
        !enabled -> SpatialTokens.field.copy(alpha = 0.35f)
        pressed -> SpatialTokens.accent
        checked -> SpatialTokens.accentStrong
        hovered -> SpatialTokens.field.copy(alpha = 0.86f)
        else -> SpatialTokens.field
    }
    Box(modifier.size(44.dp).clip(SpatialTokens.controlShape)
        .background(fill)
        .border(1.dp, SpatialTokens.outline.copy(alpha = if (enabled) 1f else 0.3f),
            SpatialTokens.controlShape)
        .pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) when (awaitPointerEvent().type) {
                    PointerEventType.Enter -> hovered = true
                    PointerEventType.Exit -> hovered = false
                }
            }
        }
        .semantics { this.contentDescription = localizedDescription }
        .toggleable(checked, enabled = enabled, role = Role.Checkbox,
            interactionSource = interactions, indication = null,
            onValueChange = onCheckedChange), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) SpatialTokens.text else SpatialTokens.disabled) {
            content()
        }
    }
}

@Composable
fun SpatialLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var hovered by remember { mutableStateOf(false) }
    SpatialText(label, modifier.pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) when (awaitPointerEvent().type) {
                    PointerEventType.Enter -> hovered = true
                    PointerEventType.Exit -> hovered = false
                }
            }
        }.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 4.dp, vertical = 8.dp),
        color = if (enabled) SpatialTokens.accent else SpatialTokens.disabled,
        textDecoration = if (hovered && enabled) TextDecoration.Underline else TextDecoration.None)
}

@Composable
fun SpatialChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    SpatialButton(label, onClick, modifier, enabled = enabled, compact = true)
}

@Composable
fun SpatialToggleableChip(
    label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    SpatialToggleButton(label, checked, onCheckedChange, modifier, enabled)
}

@Composable
fun SpatialBadge(label: String, modifier: Modifier = Modifier, color: Color = SpatialTokens.accentStrong) {
    Box(modifier.clip(SpatialTokens.controlShape).background(color)
        .padding(horizontal = 8.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
        SpatialText(label, color = SpatialTokens.text)
    }
}

@Composable
fun SpatialCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit,
                    modifier: Modifier = Modifier, enabled: Boolean = true) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier,
        enabled = enabled, colors = CheckboxDefaults.colors(
            checkedColor = SpatialTokens.accentStrong,
            uncheckedColor = SpatialTokens.outline,
            checkmarkColor = SpatialTokens.onAccent,
            disabledCheckedColor = SpatialTokens.disabled,
            disabledUncheckedColor = SpatialTokens.disabled))
}

@Composable
fun SpatialSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?,
                  modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier,
        enabled = enabled, colors = SwitchDefaults.colors(
            checkedTrackColor = SpatialTokens.accentStrong,
            checkedThumbColor = SpatialTokens.onAccent,
            uncheckedTrackColor = SpatialTokens.field,
            uncheckedThumbColor = SpatialTokens.onAccent,
            uncheckedBorderColor = SpatialTokens.outline,
            disabledCheckedTrackColor = SpatialTokens.disabled,
            disabledUncheckedTrackColor = SpatialTokens.disabled.copy(alpha = 0.4f)))
}
