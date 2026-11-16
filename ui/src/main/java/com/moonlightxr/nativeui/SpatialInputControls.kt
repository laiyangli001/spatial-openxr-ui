package com.moonlightxr.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Shared field roles, including disabled surfaces which otherwise retain Material defaults. */
@Composable
private fun spatialTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SpatialTokens.text,
    unfocusedTextColor = SpatialTokens.text,
    disabledTextColor = SpatialTokens.disabled,
    focusedContainerColor = SpatialTokens.field,
    unfocusedContainerColor = SpatialTokens.field,
    disabledContainerColor = SpatialTokens.field.copy(alpha = SpatialTokens.field.alpha * 0.5f),
    focusedBorderColor = SpatialTokens.outline,
    unfocusedBorderColor = SpatialTokens.outline.copy(alpha = 0.45f),
    disabledBorderColor = SpatialTokens.disabled.copy(alpha = 0.45f),
    focusedPlaceholderColor = SpatialTokens.muted,
    unfocusedPlaceholderColor = SpatialTokens.muted,
    disabledPlaceholderColor = SpatialTokens.disabled,
    cursorColor = SpatialTokens.accentStrong,
)

@Composable
fun SpatialTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    placeholder: String = "", enabled: Boolean = true, readOnly: Boolean = false,
    singleLine: Boolean = true,
) {
    val currentValue by rememberUpdatedState(value)
    OutlinedTextField(value = value, onValueChange = { if (it != value) UiFeedback.key(); onValueChange(it) },
        modifier = modifier.onFocusChanged { focus ->
            if (focus.isFocused) {
                VirtualKeyboardTarget.attach({ currentValue }, onValueChange)
                VirtualKeyboardCallbacks.open()
            } else VirtualKeyboardTarget.detach()
        },
        enabled = enabled, readOnly = readOnly, singleLine = singleLine,
        placeholder = { if (placeholder.isNotEmpty()) SpatialText(placeholder) },
        shape = SpatialTokens.controlShape,
        colors = spatialTextFieldColors())
}

@Composable
fun SpatialSearchField(value: String, onValueChange: (String) -> Unit,
                       onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    val currentValue by rememberUpdatedState(value)
    OutlinedTextField(value = value, onValueChange = { if (it != value) UiFeedback.key(); onValueChange(it) },
        modifier = modifier.onFocusChanged { focus ->
            if (focus.isFocused) {
                VirtualKeyboardTarget.attach({ currentValue }, onValueChange)
                VirtualKeyboardCallbacks.open()
            } else VirtualKeyboardTarget.detach()
        },
        singleLine = true, placeholder = { SpatialText("搜索", color = SpatialTokens.muted) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(value) }),
        shape = SpatialTokens.controlShape,
        colors = spatialTextFieldColors())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpatialRangeSlider(value: Float, onValueChange: (Float) -> Unit,
                       modifier: Modifier = Modifier, valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
                       enabled: Boolean = true, steps: Int = 0, compactThumb: Boolean = false) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val dragged by interactions.collectIsDraggedAsState()
    val emphasized = enabled && (hovered || pressed || dragged)
    val colors = SliderDefaults.colors(
        thumbColor = SpatialTokens.onAccent,
        activeTrackColor = SpatialTokens.accentStrong,
        inactiveTrackColor = SpatialTokens.field,
        activeTickColor = SpatialTokens.outline,
        inactiveTickColor = SpatialTokens.muted)
    Slider(value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange, modifier = modifier.hoverable(interactions, enabled), valueRange = valueRange,
        enabled = enabled, steps = steps,
        colors = colors, interactionSource = interactions,
        thumb = {
            Box(Modifier.size(if (compactThumb) 24.dp else 36.dp)
                .clip(CircleShape)
                .background(if (!enabled) SpatialTokens.disabled else SpatialTokens.onAccent)
                .border(if (emphasized) 3.dp else 2.dp, SpatialTokens.accentStrong, CircleShape))
        },
        track = { sliderState -> SliderDefaults.Track(sliderState = sliderState,
            enabled = enabled, colors = colors, thumbTrackGapSize = 0.dp,
            drawStopIndicator = null) })
}

@Composable
fun SpatialSegmentSlider(value: Int, segmentCount: Int, onValueChange: (Int) -> Unit,
                         modifier: Modifier = Modifier, enabled: Boolean = true, compactThumb: Boolean = false) {
    require(segmentCount > 0)
    SpatialRangeSlider(value.toFloat().coerceIn(0f, segmentCount.toFloat()),
        { onValueChange(it.roundToInt().coerceIn(0, segmentCount)) }, modifier,
        valueRange = 0f..segmentCount.toFloat(), enabled = enabled, steps = segmentCount - 1,
        compactThumb = compactThumb)
}

/** Compact editable numeric field with the same repeat-on-hold +/- controls as the slider. */
@Composable
fun SpatialNumberField(value: Int, onValueChange: (Int) -> Unit,
                       modifier: Modifier = Modifier, range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
                       step: Int = 1, enabled: Boolean = true, editable: Boolean = true,
                       compact: Boolean = false) {
    require(step > 0)
    var input by remember { mutableStateOf(value.toString()) }
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(value, editing) { if (!editing) input = value.toString() }
    fun commitInput() {
        val next = input.toIntOrNull()?.coerceIn(range) ?: value
        input = next.toString()
        if (next != value) onValueChange(next)
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        SpatialRepeatStepButton("−", enabled && value > range.first, compact) {
            onValueChange(boundedStep(value, -step, range))
        }
        if (editable) {
            OutlinedTextField(input, { if (it != input) UiFeedback.key(); input = it },
                Modifier.width(88.dp).onFocusChanged { focus ->
                    if (editing && !focus.isFocused) commitInput()
                    editing = focus.isFocused
                }, enabled = enabled, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitInput() }),
                shape = SpatialTokens.controlShape,
                colors = spatialTextFieldColors())
        } else SpatialText(value.toString(), color = SpatialTokens.text)
        SpatialRepeatStepButton("+", enabled && value < range.last, compact) {
            onValueChange(boundedStep(value, step, range))
        }
    }
}

internal fun boundedStep(value: Int, step: Int, range: IntRange): Int =
    (value.toLong() + step).coerceIn(range.first.toLong(), range.last.toLong()).toInt()

internal data class SpatialStepVisual(val fill: Color, val content: Color, val outline: Color, val stroke: Dp)

internal fun resolveSpatialStepVisual(enabled: Boolean, hovered: Boolean, pressed: Boolean): SpatialStepVisual = when {
    !enabled -> SpatialStepVisual(SpatialTokens.field.copy(alpha = 0.35f), SpatialTokens.disabled,
        SpatialTokens.outline.copy(alpha = 0.2f), 1.dp)
    pressed -> SpatialStepVisual(SpatialTokens.accentStrong, SpatialTokens.onAccent,
        SpatialTokens.accent, 1.5.dp)
    hovered -> SpatialStepVisual(lerp(SpatialTokens.field, SpatialTokens.accentStrong, 0.28f),
        SpatialTokens.text, SpatialTokens.outline, 1.5.dp)
    else -> SpatialStepVisual(SpatialTokens.field, SpatialTokens.text,
        SpatialTokens.outline.copy(alpha = 0.7f), 1.dp)
}

/** One step on release, then repeated steps while held; uses the latest callback after recomposition. */
@Composable
fun SpatialRepeatStepButton(label: String, enabled: Boolean, compact: Boolean = false,
                            modifier: Modifier = Modifier,
                            onStep: () -> Unit) {
    val currentStep by rememberUpdatedState(onStep)
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    var pressed by remember { mutableStateOf(false) }
    val visual = resolveSpatialStepVisual(enabled, hovered, pressed)
    val shape = RoundedCornerShape(if (compact) 5.dp else 7.dp)
    Box(modifier.size(if (compact) 30.dp else 44.dp)
        .hoverable(interactions, enabled)
        .pointerInput(enabled) {
            if (enabled) detectTapGestures(onPress = {
                coroutineScope {
                    pressed = true
                    var repeated = false
                    val repeatJob = launch {
                        delay(360)
                        while (true) {
                            currentStep()
                            repeated = true
                            delay(90)
                        }
                    }
                    try {
                        val released = tryAwaitRelease()
                        if (released && !repeated) currentStep()
                    } finally {
                        repeatJob.cancel()
                        pressed = false
                    }
                }
            })
        }
        .semantics {
            role = Role.Button
            if (!enabled) disabled()
            onClick(label) { if (enabled) { currentStep(); true } else false }
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(if (compact) 26.dp else 44.dp).clip(shape)
            .background(visual.fill).border(visual.stroke, visual.outline, shape),
            contentAlignment = Alignment.Center) {
            SpatialText(label, color = visual.content)
        }
    }
}
