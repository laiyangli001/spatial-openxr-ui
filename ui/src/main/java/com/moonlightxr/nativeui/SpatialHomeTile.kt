package com.moonlightxr.nativeui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Shared footprint for the backing texture and the independent foreground text Quad. */
internal object HomeEntryLayout {
    val width = 140.dp
    val height = 176.dp
    val iconSize = 76.dp
    val labelWidth = 100.dp
    val labelHeight = 34.dp
}

/** The foreground uses only the artwork rectangle, without main's transparent margins. */
internal object HomeSurfaceLayout {
    const val widthPixels = 1280
    const val heightPixels = 720
    const val foregroundDepthMeters = 0.20f

    /** Startup and model loading must not paint a temporary label at the background depth. */
    fun textOnBacking(hasSpatialHost: Boolean, admissionRejected: Boolean,
                      presentation: SpatialLayerPresentation): Boolean =
        !hasSpatialHost || admissionRejected || presentation == SpatialLayerPresentation.Failed

    fun foregroundQuad(main: UiQuad): UiQuad = spatialPopupQuad(main, "home-zz-foreground",
        SpatialAnchor(0.5f, 0.5f), foregroundDepthMeters,
        SpatialSurfaceSize(main.widthMeters * widthPixels / main.widthPixels,
            main.heightMeters * heightPixels / main.heightPixels, widthPixels, heightPixels))
}

/** Highlight the entire entry footprint on the foreground text plane. */
internal fun Modifier.homeEntryHoverFrame(highlighted: Boolean): Modifier {
    val shape = RoundedCornerShape(15.dp)
    return clip(shape)
        .background(if (highlighted) Color(0x44506A83) else Color.Transparent)
        .then(if (highlighted) Modifier.border(1.dp, Color(0x99B8DFFF), shape) else Modifier)
}

@Composable
internal fun HomeEntryCard(highlighted: Boolean, enabled: Boolean,
                           onHoverChange: ((Boolean) -> Unit)?, onClick: () -> Unit,
                           modifier: Modifier = Modifier,
                           content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val latestOnHoverChange by rememberUpdatedState(onHoverChange)
    Column(modifier.width(HomeEntryLayout.width).height(HomeEntryLayout.height)
        .homeEntryHoverFrame(highlighted && enabled)
        .then(if (enabled) Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    when (awaitPointerEvent().type) {
                        PointerEventType.Enter -> latestOnHoverChange?.invoke(true)
                        PointerEventType.Exit -> latestOnHoverChange?.invoke(false)
                    }
                }
            }
        }.clickable(interactionSource = interaction, indication = null,
            role = Role.Button, onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content)
}

/** The label is visual content; the matching card handles the full entry area. */
@Composable
internal fun HomeEntryLabel(label: String, visible: Boolean = true) {
    Box(Modifier.width(HomeEntryLayout.labelWidth).height(HomeEntryLayout.labelHeight)
        .alpha(if (visible) 1f else 0f),
        contentAlignment = Alignment.Center) {
        SpatialText(label, color = Color.White, fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold)
    }
}

/** The tile supplies layout and the model anchor; foreground card owns input when ready. */
@Composable
fun SpatialHomeTile(
    label: String,
    @DrawableRes icon: Int,
    stereoIconReady: Boolean = false,
    labelVisible: Boolean = true,
    highlighted: Boolean = false,
    onIconAnchor: ((SpatialAnchor) -> Unit)? = null,
    onHoverChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
) {
    HomeEntryCard(highlighted, labelVisible, onHoverChange, onClick) {
        Box(Modifier.size(HomeEntryLayout.iconSize).onGloballyPositioned { coordinates ->
            val root = coordinates.findRootCoordinates().size
            if (onIconAnchor != null && root.width > 0 && root.height > 0) {
                val center = coordinates.boundsInRoot().center
                // Pixel quantization keeps the attached Quad stable while unrelated
                // Home state changes trigger Compose layout callbacks.
                onIconAnchor(SpatialAnchor(
                    (center.x.roundToInt().toFloat() / root.width).coerceIn(0f, 1f),
                    (center.y.roundToInt().toFloat() / root.height).coerceIn(0f, 1f)))
            }
        }) {
            if (!stereoIconReady) {
                Image(painterResource(icon), contentDescription = null,
                    Modifier.size(HomeEntryLayout.iconSize))
            }
        }
        HomeEntryLabel(label, visible = labelVisible)
    }
}
