package com.moonlightxr.nativeui

/** A UI surface's interaction rectangle must match its OpenXR Quad dimensions and pose. */
/** FaceHead turns horizontally toward the viewer while keeping the panel vertical. */
enum class SpatialFacing { ParentAligned, FaceHead }
enum class UiEyeVisibility(val nativeValue: Int) { Both(0), Left(1), Right(2) }

data class UiQuad(
    val id: String,
    val pose: QuadPose,
    val widthMeters: Float,
    val heightMeters: Float,
    val widthPixels: Int,
    val heightPixels: Int,
    val facing: SpatialFacing = SpatialFacing.ParentAligned,
    val parentId: String? = null,
    val eyeVisibility: UiEyeVisibility = UiEyeVisibility.Both,
    val stereoGroupId: String? = null,
    /** Stable parent-space attachment; avoids deriving an offset from a stale world pose. */
    val parentLocalOffset: Vec3? = null,
    val parentLocalOrientation: Quat? = null,
)

data class UiQuadHit(val quad: UiQuad, val pixel: PixelHit)
data class SpatialInteractiveQuad(val quad: UiQuad, val modal: Boolean, val focusable: Boolean)

/**
 * Pure policy shared by XR compositor submission and ray routing. The caller supplies the actual
 * maxLayerCount from xrGetSystemProperties, then submits only [visibleQuads]. Keeping this as a
 * function prevents the image and its click target from ending up on different depth planes.
 */
data class SpatialUiLayerPlan(
    val main: UiQuad,
    val popup: UiQuad?,
    val popupOpen: Boolean,
    val budget: LayerBudget,
    val extension: UiQuad? = null,
    val popupModal: Boolean = true,
    val spatial: List<SpatialInteractiveQuad> = emptyList(),
) {
    val separateExtension: Boolean get() = extension != null && budget.mayUseUiLayers(2)
    val separatePopup: Boolean get() = popupOpen && popup != null &&
        budget.mayUseUiLayers(2 + if (separateExtension) 1 else 0)
    val inlinePopup: Boolean get() = popupOpen && !separatePopup
    val visibleQuads: List<UiQuad> get() = buildList {
        add(main)
        if (separateExtension) add(checkNotNull(extension))
        if (separatePopup) add(checkNotNull(popup))
        spatial.forEach { add(it.quad) }
    }

    /** A modal popup receives all rays while it is open; the underlying menu cannot be clicked. */
    fun hit(rayOrigin: Vec3, rayDirection: Vec3): UiQuadHit? {
        val spatialModal = spatial.lastOrNull { it.modal && it.focusable }
        if (spatialModal != null) return QuadInput.intersect(rayOrigin, rayDirection,
            spatialModal.quad.pose, spatialModal.quad.widthMeters,
            spatialModal.quad.heightMeters, spatialModal.quad.widthPixels,
            spatialModal.quad.heightPixels)?.let { UiQuadHit(spatialModal.quad, it) }
        // While a modal is pending, its inline fallback lives on main; no other surface
        // may receive the ray. Once ready, only the dedicated popup may receive it.
        if (popupOpen && popupModal) {
            val target = if (separatePopup) checkNotNull(popup) else main
            return QuadInput.intersect(rayOrigin, rayDirection, target.pose,
                target.widthMeters, target.heightMeters, target.widthPixels,
                target.heightPixels)?.let { UiQuadHit(target, it) }
        }
        // Popup and extension can cross the transparent main Quad plane. They remain
        // clickable there, while ordinary spatial children still obey actual depth.
        val overlays = buildList {
            if (separatePopup) add(checkNotNull(popup))
            if (separateExtension) add(checkNotNull(extension))
        }
        fun hits(quads: List<UiQuad>) = quads.mapNotNull { quad ->
            QuadInput.intersect(rayOrigin, rayDirection, quad.pose, quad.widthMeters,
                quad.heightMeters, quad.widthPixels, quad.heightPixels)?.let { UiQuadHit(quad, it) }
        }
        val overlayHits = hits(overlays)
        val spatialHits = hits(spatial.filter { it.focusable }.map { it.quad })
        // The free menu is an independent overlay. The transparent Home backing Quad
        // must not steal its clicks after the user moves or scales it behind Home.
        val freeMenuHits = spatialHits.filter { it.quad.parentId == "free-menu" }
        // A head-facing rail can cross the transparent backing plane near its inner edge.
        // Its visible buttons must win over main and the Home foreground's empty pixels.
        val sidebarHits = spatialHits.filter { it.quad.id == "side-navigation" }
        val activeOverlays = overlayHits + freeMenuHits + sidebarHits
        val candidates = if (activeOverlays.isNotEmpty()) activeOverlays +
            spatialHits.filter { it.quad.parentId != "free-menu" &&
                it.quad.id != "side-navigation" &&
                (sidebarHits.isEmpty() || it.quad.id != "home-zz-foreground") }
            else spatialHits + hits(listOf(main))
        // These surfaces are submitted after Home regardless of physical distance.
        // Match compositor order instead of letting Home's transparent text plane steal input.
        fun overlayRank(quad: UiQuad): Int = when {
            quad.id == "popup" || quad.parentId == "popup" -> 5
            quad.id == "extension" || quad.parentId == "extension" -> 4
            quad.parentId == "free-menu" -> 3
            else -> 2
        }
        return if(activeOverlays.isNotEmpty()) candidates.minWithOrNull(
            compareByDescending<UiQuadHit> { overlayRank(it.quad) }.thenBy { it.pixel.distanceMeters })
        else candidates.minByOrNull { it.pixel.distanceMeters }
    }
}

/** Depth is along the menu's own normal, so turning the menu does not break the 3-D spacing. */
fun popupInFrontOf(main: UiQuad, gapMeters: Float, widthMeters: Float, heightMeters: Float,
                   widthPixels: Int, heightPixels: Int): UiQuad {
    require(gapMeters > 0f && widthMeters > 0f && heightMeters > 0f)
    return UiQuad("popup", main.pose.copy(
        position = main.pose.position + main.pose.orientation.rotate(Vec3(0f, 0f, gapMeters))),
        widthMeters, heightMeters, widthPixels, heightPixels)
}
