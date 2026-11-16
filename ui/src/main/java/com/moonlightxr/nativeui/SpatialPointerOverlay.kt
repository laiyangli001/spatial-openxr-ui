package com.moonlightxr.nativeui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max
import kotlin.math.roundToInt

/** The same ray/Quad hit drives input and its visual at a stable render resolution. */
data class SpatialPointerVisual(val quad: UiQuad, val hit: PixelHit, val pressed: Boolean)

private const val POINTER_PIXEL_STEP = 2f
private const val POINTER_DISTANCE_STEP = 0.01f
private val stablePointerPose = QuadPose(Vec3(0f, 0f, 0f), Quat(0f, 0f, 0f, 1f))

/** Rendering needs the panel size and hit point, not the moving FaceHead pose. */
internal fun stablePointerVisual(hit: UiQuadHit?, pressed: Boolean): SpatialPointerVisual? {
    hit ?: return null
    val panel = hit.quad.copy(
        pose = stablePointerPose,
        facing = SpatialFacing.ParentAligned,
        parentId = null,
        eyeVisibility = UiEyeVisibility.Both,
        stereoGroupId = null,
        parentLocalOffset = null,
        parentLocalOrientation = null,
    )
    return SpatialPointerVisual(panel, PixelHit(
        stablePointerCoordinate(hit.pixel.x),
        stablePointerCoordinate(hit.pixel.y),
        ((hit.pixel.distanceMeters / POINTER_DISTANCE_STEP).roundToInt() *
            POINTER_DISTANCE_STEP),
    ), pressed)
}

/** Keep sub-pixel tracking noise from repainting a full parameter-panel Surface. */
internal fun stablePointerCoordinate(value: Float): Float =
    (value / POINTER_PIXEL_STEP).roundToInt() * POINTER_PIXEL_STEP

/** A panel observes only its own cursor so pointer motion elsewhere cannot redraw Home. */
internal fun pointerVisualsForPanel(
    primary: SpatialPointerVisual?,
    secondary: SpatialPointerVisual?,
    panelId: String,
): List<SpatialPointerVisual> = buildList {
    primary?.takeIf { it.quad.id == panelId }?.let(::add)
    secondary?.takeIf { it.quad.id == panelId }?.let(::add)
}

internal data class SpatialPointerGeometry(val center: Offset, val radius: Size, val stroke: Float)

internal fun pointerGeometry(pointer: SpatialPointerVisual): SpatialPointerGeometry {
    val quad = pointer.quad
    // The old D2S reticle was 24 mm across at 2 m, with a blue outer
    // disc and a white center. Keep that angular size at other distances.
    val radiusMeters = 0.012f * (max(0.001f, pointer.hit.distanceMeters) / 2f)
        .coerceIn(0.35f, 12f)
    val pixelsPerMeterX = quad.widthPixels / quad.widthMeters
    val pixelsPerMeterY = quad.heightPixels / quad.heightMeters
    return SpatialPointerGeometry(Offset(pointer.hit.x, pointer.hit.y),
        Size(max(7f, radiusMeters * pixelsPerMeterX),
            max(7f, radiusMeters * pixelsPerMeterY)),
        max(2f, 0.0015f * minOf(pixelsPerMeterX, pixelsPerMeterY)))
}

/** Draw last in its own source Surface. It neither takes input nor consumes an OpenXR layer. */
@Composable
internal fun SpatialPointerOverlay(state: MenuUiState, panelId: String) {
    val pointers by remember(state, panelId) {
        derivedStateOf {
            pointerVisualsForPanel(state.pointerVisual, state.secondaryPointerVisual, panelId)
        }
    }
    if (pointers.isEmpty()) return
    val pointerShape = state.cursorType
    Canvas(Modifier.fillMaxSize()) {
        for (pointer in pointers) {
            val color = if (pointer.pressed) SpatialTokens.accentStrong else Color(0xFF50B4FF)
            val geometry = pointerGeometry(pointer)
            val center = geometry.center
            val radius = geometry.radius
            val stroke = geometry.stroke
            val halo = Color.Black.copy(alpha = 0.8f)
            if (pointerShape == "Pointer") {
                val path = Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(center.x + radius.width * 1.4f, center.y + radius.height * 1.7f)
                    lineTo(center.x + radius.width * 0.2f, center.y + radius.height * 1.3f)
                    close()
                }
                drawPath(path, color)
                drawPath(path, halo, style = Stroke(stroke))
            } else {
                val topLeft = center - Offset(radius.width, radius.height)
                val diameter = Size(radius.width * 2f, radius.height * 2f)
                drawOval(color.copy(alpha = 0.46f),
                    center - Offset(radius.width * 1.35f, radius.height * 1.35f),
                    Size(radius.width * 2.7f, radius.height * 2.7f))
                drawOval(Color.Black.copy(alpha = 0.9f),
                    center - Offset(radius.width + stroke, radius.height + stroke),
                    Size(diameter.width + stroke * 2f, diameter.height + stroke * 2f))
                drawOval(color.copy(alpha = 0.92f), topLeft, diameter)
                val innerRatio = 0.0056f / 0.0096f
                drawOval(Color.White.copy(alpha = 0.92f),
                    center - Offset(radius.width * innerRatio, radius.height * innerRatio),
                    Size(diameter.width * innerRatio, diameter.height * innerRatio))
            }
        }
    }
}
