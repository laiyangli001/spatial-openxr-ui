package com.moonlightxr.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

data class InlineChoices(val title: String, val options: List<String>, val selected: String,
                         val anchor: ParameterHelpAnchor, val onSelected: (String) -> Unit,
                         val controlKey: String = title)

internal data class InlineChoicePlacement(val x: Float, val y: Float, val width: Float, val height: Float)

/** Keep both rendering and ray interaction inside the original Quad, even for bottom-row pickers. */
internal fun placeInlineChoices(width: Float, height: Float, anchor: ParameterHelpAnchor,
                               desiredWidth: Float, desiredHeight: Float,
                               margin: Float, gap: Float): InlineChoicePlacement {
    require(width > margin * 2 && height > margin * 2)
    val menuWidth = min(desiredWidth, width - margin * 2)
    val menuHeight = min(desiredHeight, height - margin * 2)
    val anchorTop = (anchor.y - anchor.height / 2f) * height
    val anchorBottom = (anchor.y + anchor.height / 2f) * height
    val below = (height - margin - anchorBottom - gap).coerceAtLeast(0f)
    val above = (anchorTop - gap - margin).coerceAtLeast(0f)
    val openBelow = below >= menuHeight || below >= above
    val available = if (openBelow) below else above
    val actualHeight = min(menuHeight, available).coerceAtLeast(min(40f, menuHeight))
    val y = if (openBelow) anchorBottom + gap else anchorTop - gap - actualHeight
    return InlineChoicePlacement(
        ((anchor.x + anchor.width / 2f) * width - menuWidth)
            .coerceIn(margin, width - margin - menuWidth),
        y.coerceIn(margin, height - margin - actualHeight), menuWidth, actualHeight)
}

/** An ordinary Compose overlay on the same Surface, never Android Popup or a new OpenXR layer. */
@Composable
internal fun SpatialInlineChoiceOverlay(state: MenuUiState, panelId: String) {
    val choices = state.inlineChoices?.takeIf { it.anchor.panelId == panelId } ?: return
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val placement = with(density) { placeInlineChoices(maxWidth.toPx(), maxHeight.toPx(),
            choices.anchor, 200.dp.toPx(), (choices.options.size.coerceAtMost(5) * 40 + 8).dp.toPx(),
            16.dp.toPx(), 4.dp.toPx()) }
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() },
            indication = null) { state.dismissInlineChoices() })
        val shape = RoundedCornerShape(10.dp)
        Box(Modifier.offset { IntOffset(placement.x.roundToInt(), placement.y.roundToInt()) }
            .size(with(density) { placement.width.toDp() }, with(density) { placement.height.toDp() })
            .shadow(8.dp, shape).clip(shape)
            .background(Brush.verticalGradient(listOf(SpatialTokens.popupTop.copy(alpha = 0.98f),
                SpatialTokens.popupBottom.copy(alpha = 0.98f))))
            .border(0.7.dp, SpatialTokens.outline.copy(alpha = 0.55f), shape).padding(vertical = 4.dp)) {
            CompositionLocalProvider(LocalSpatialScrollScope provides SpatialScrollScope.InlineChoices) {
            SpatialScrollArea(Modifier.fillMaxSize(), state.choiceScrollState, reserveIndicatorSpace = false) {
                Column {
                    choices.options.forEach { option ->
                        SpatialOption(option, choices.selected == option,
                            onSelectChange = { state.selectInlineChoice(option) },
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                                .recordParameterControl("choice:$option"),
                            inlineMenu = true)
                    }
                }
            }
            }
        }
    }
}
