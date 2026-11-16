package com.moonlightxr.nativeui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Independent geometry contracts for PICO-style spatial popup, subwindow and augment surfaces. */
enum class SpatialSide { Left, Right }

data class SpatialAnchor(val x: Float, val y: Float) {
    init { require(x in 0f..1f && y in 0f..1f) }
}

data class SpatialSurfaceSize(val widthMeters: Float, val heightMeters: Float,
                              val widthPixels: Int, val heightPixels: Int) {
    init {
        require(widthMeters.isFinite() && widthMeters > 0f)
        require(heightMeters.isFinite() && heightMeters > 0f)
        require(widthPixels > 0 && heightPixels > 0)
    }
}

/** Convert an anchored child's world center back to the parent's local frame for XR submission. */
fun spatialChildOffset(parent: UiQuad, child: UiQuad): Vec3 =
    parent.pose.orientation.inverseRotate(child.pose.position - parent.pose.position)

/** Relative rotation must be shared by OpenXR submission and Kotlin laser hit testing. */
fun spatialChildOrientation(parent: UiQuad, child: UiQuad): Quat =
    parent.pose.orientation.conjugate() * child.pose.orientation

/** Prefer a captured local attachment when the parent may have moved before presentation. */
fun spatialRequestOffset(parent: UiQuad, child: UiQuad): Vec3 =
    child.parentLocalOffset ?: spatialChildOffset(parent, child)

fun spatialRequestOrientation(parent: UiQuad, child: UiQuad): Quat =
    child.parentLocalOrientation ?: spatialChildOrientation(parent, child)

/** Places a popup relative to the parent's visible rectangle, in front of that rectangle. */
fun spatialPopupQuad(parent: UiQuad, id: String, anchor: SpatialAnchor,
                     depthMeters: Float, size: SpatialSurfaceSize,
                     localOrientation: Quat = Quat(0f, 0f, 0f, 1f),
                     facing: SpatialFacing = SpatialFacing.ParentAligned): UiQuad {
    require(depthMeters.isFinite() && depthMeters > 0f)
    val rotationLengthSquared = localOrientation.x * localOrientation.x +
        localOrientation.y * localOrientation.y + localOrientation.z * localOrientation.z +
        localOrientation.w * localOrientation.w
    require(rotationLengthSquared.isFinite() && kotlin.math.abs(rotationLengthSquared - 1f) < 0.01f) {
        "Popup local orientation must be a unit quaternion"
    }
    val local = Vec3((anchor.x - 0.5f) * parent.widthMeters,
        (0.5f - anchor.y) * parent.heightMeters, depthMeters)
    return UiQuad(id, parent.pose.copy(position = parent.pose.position +
        parent.pose.orientation.rotate(local),
        orientation = parent.pose.orientation * localOrientation),
        size.widthMeters, size.heightMeters, size.widthPixels, size.heightPixels, facing, parent.id,
        parentLocalOffset = local, parentLocalOrientation = localOrientation)
}

/** Keeps the child beside its parent, with an independent Quad and the parent's head-facing pose. */
fun spatialSubwindowQuad(parent: UiQuad, id: String, side: SpatialSide,
                         gapMeters: Float, size: SpatialSurfaceSize,
                         depthMeters: Float = 0f): UiQuad {
    require(gapMeters.isFinite() && gapMeters >= 0f)
    require(depthMeters.isFinite())
    val x = (parent.widthMeters + size.widthMeters) / 2f + gapMeters
    val signedX = if (side == SpatialSide.Left) -x else x
    val local = Vec3(signedX, 0f, depthMeters)
    return UiQuad(id, parent.pose.copy(position = parent.pose.position +
        parent.pose.orientation.rotate(local)),
        size.widthMeters, size.heightMeters, size.widthPixels, size.heightPixels,
        parentId = parent.id, parentLocalOffset = local,
        parentLocalOrientation = Quat(0f, 0f, 0f, 1f))
}

/** Keep the free menu and its settings Quad adjacent without overlapping their hit planes. */
internal fun spatialMenuExtensionQuad(menu: UiQuad, size: SpatialSurfaceSize): UiQuad {
    val beside = spatialSubwindowQuad(menu, "extension", SpatialSide.Right, 0.08f, size)
    return beside.copy(pose = beside.pose.copy(position = beside.pose.position +
        menu.pose.orientation.rotate(Vec3(0f, 0f, 0.04f))), facing = SpatialFacing.FaceHead)
}

/** Augment alignment is its own normalized point; the default keeps its center on the anchor. */
fun spatialAugmentQuad(parent: UiQuad, id: String, anchor: SpatialAnchor,
                       offsetMeters: Vec3, size: SpatialSurfaceSize,
                       alignment: SpatialAnchor = SpatialAnchor(0.5f, 0.5f),
                       localOrientation: Quat = Quat(0f, 0f, 0f, 1f)): UiQuad {
    require(offsetMeters.x.isFinite() && offsetMeters.y.isFinite() && offsetMeters.z.isFinite())
    val rotationLengthSquared = localOrientation.x * localOrientation.x +
        localOrientation.y * localOrientation.y + localOrientation.z * localOrientation.z +
        localOrientation.w * localOrientation.w
    require(rotationLengthSquared.isFinite() && kotlin.math.abs(rotationLengthSquared - 1f) < 0.01f)
    // Rotate the child's alignment point with the child, so a tilted augment still
    // touches the chosen parent anchor instead of orbiting around an unrotated offset.
    val alignToCenter = localOrientation.rotate(Vec3(
        (0.5f - alignment.x) * size.widthMeters,
        (alignment.y - 0.5f) * size.heightMeters, 0f))
    val local = Vec3((anchor.x - 0.5f) * parent.widthMeters + offsetMeters.x,
        (0.5f - anchor.y) * parent.heightMeters + offsetMeters.y,
        offsetMeters.z) + alignToCenter
    return UiQuad(id, parent.pose.copy(position = parent.pose.position +
        parent.pose.orientation.rotate(local),
        orientation = parent.pose.orientation * localOrientation),
        size.widthMeters, size.heightMeters, size.widthPixels, size.heightPixels,
        parentId = parent.id)
}

/** Implemented by a Native OpenXR layer renderer, never by the PICO OS 6 Spatial runtime. */
interface SpatialFloatingHost {
    /** Returning true only admits the request; Ready is reported separately after an actual frame. */
    fun show(quad: UiQuad, modal: Boolean, focusable: Boolean,
             content: @Composable () -> Unit): Boolean
    /** Admit both non-interactive eye layers together. Unsupported hosts retain a mono fallback. */
    fun showStereo(left: UiQuad, right: UiQuad,
                   content: @Composable (SpatialEye) -> Unit): Boolean = false
    /** Implementations must expose observable Compose state for asynchronous transitions. */
    fun presentationState(id: String): SpatialLayerPresentation
    fun hide(id: String)
}

enum class SpatialLayerPresentation { Missing, Requested, Ready, Failed }

internal fun drawSpatialInlineFallback(visible: Boolean, accepted: Boolean,
                                       presentation: SpatialLayerPresentation): Boolean =
    visible && (!accepted || presentation != SpatialLayerPresentation.Ready)

/** A modal must not flash on the main panel while its independent Quad is starting. */
internal fun drawModalInlineFallback(spatialModalId: String?,
                                     admissionRejected: Boolean,
                                     reservedPopupPending: Boolean = false): Boolean =
    spatialModalId == null || (admissionRejected && !reservedPopupPending)

@Composable
internal fun SpatialFloatingSurface(quad: UiQuad, host: SpatialFloatingHost?,
                                   visible: Boolean, modal: Boolean, focusable: Boolean,
                                   inlineFallback: @Composable (() -> Unit)? = null,
                                   onAdmission: ((Boolean?) -> Unit)? = null,
                                   content: @Composable () -> Unit) {
    val latestContent by rememberUpdatedState(content)
    val latestOnAdmission by rememberUpdatedState(onAdmission)
    var accepted by remember { mutableStateOf(false) }
    DisposableEffect(host, quad, visible, modal, focusable) {
        val admissionCallback = latestOnAdmission
        val admitted = visible && host?.show(quad, modal, focusable) { latestContent() } == true
        accepted = admitted
        admissionCallback?.invoke(admitted)
        onDispose {
            // A rejected/failed/not-yet-presented request may still own cached content.
            if (visible) host?.hide(quad.id)
            accepted = false
            admissionCallback?.invoke(null)
        }
    }
    val presentation = host?.presentationState(quad.id) ?: SpatialLayerPresentation.Missing
    if (drawSpatialInlineFallback(visible, accepted, presentation)) {
        if (inlineFallback != null) inlineFallback()
        else SpatialPanel(modal = modal) { Box(Modifier.padding(12.dp)) { content() } }
    }
}

@Composable
fun SpatialPopupSurface(parent: UiQuad, id: String, anchor: SpatialAnchor,
                        size: SpatialSurfaceSize, host: SpatialFloatingHost?,
                        visible: Boolean, modal: Boolean = false,
                        depthMeters: Float = 0.12f,
                        facing: SpatialFacing = SpatialFacing.ParentAligned,
                        inlineFallback: @Composable (() -> Unit)? = null,
                        onAdmission: ((Boolean?) -> Unit)? = null,
                        content: @Composable () -> Unit) {
    SpatialFloatingSurface(spatialPopupQuad(parent, id, anchor, depthMeters, size,
        facing = facing), host,
        visible, modal, true, inlineFallback = inlineFallback, onAdmission = onAdmission, content = content)
}

@Composable
fun SpatialSubwindowSurface(parent: UiQuad, id: String, side: SpatialSide,
                            size: SpatialSurfaceSize, host: SpatialFloatingHost?,
                            visible: Boolean, focusable: Boolean = true,
                            gapMeters: Float = 0.04f,
                            facing: SpatialFacing = SpatialFacing.ParentAligned,
                            inlineFallback: @Composable (() -> Unit)? = null,
                            depthMeters: Float = 0f,
                            content: @Composable () -> Unit) {
    SpatialFloatingSurface(spatialSubwindowQuad(parent, id, side, gapMeters, size, depthMeters)
        .copy(facing = facing), host,
        visible, false, focusable, inlineFallback = inlineFallback, content = content)
}

@Composable
fun SpatialAugmentSurface(parent: UiQuad, id: String, anchor: SpatialAnchor,
                          offsetMeters: Vec3, size: SpatialSurfaceSize,
                          host: SpatialFloatingHost?, visible: Boolean,
                          focusable: Boolean = true,
                          alignment: SpatialAnchor = SpatialAnchor(0.5f, 0.5f),
                          localOrientation: Quat = Quat(0f, 0f, 0f, 1f),
                          inlineFallback: @Composable (() -> Unit)? = null,
                          content: @Composable () -> Unit) {
    SpatialFloatingSurface(spatialAugmentQuad(parent, id, anchor, offsetMeters, size,
        alignment, localOrientation), host, visible, false, focusable,
        inlineFallback = inlineFallback, content = content)
}
