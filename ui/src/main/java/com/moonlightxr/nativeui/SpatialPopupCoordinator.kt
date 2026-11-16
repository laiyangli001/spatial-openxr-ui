package com.moonlightxr.nativeui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * XR-host hook. Report ready only after a transparent popup producer, swapchain, and first frame
 * are available; submit the returned [UiQuad] in the host's one xrEndFrame call.
 * Callbacks must be delivered on the Android main thread, where Compose state is mutated.
 */
interface PopupQuadBackend {
    fun requestPopupQuad(quad: UiQuad, onReady: (Boolean) -> Unit)
    fun releasePopupQuad(quad: UiQuad)
}

/**
 * Open a modal popup on its own Quad when the queried runtime budget permits. Until the backend
 * confirms the second layer, the same content remains rendered inline, so the popup never becomes
 * invisible or unclickable while the extra swapchain is being allocated.
 */
class SpatialPopupCoordinator(
    private val state: MenuUiState,
    private var mainQuad: UiQuad,
    private var popupQuad: UiQuad,
    private val backend: PopupQuadBackend,
    var budget: LayerBudget,
) {
    private var generation = 0
    private var requested by mutableStateOf(false)
    val awaitingPresentation: Boolean get() = requested
    private var ready = false
    private var presentedPopupPose: QuadPose? = null
    private val popupLocalOffset = spatialChildOffset(mainQuad, popupQuad)
    private val popupLocalOrientation = spatialChildOrientation(mainQuad, popupQuad)
    private var defaultPopupQuad = popupQuad

    init { state.popupCoordinator = this }

    /** Called from the XR input frame after locating the actual world-space panel pose. */
    fun updatePanelPose(pose: QuadPose) {
        mainQuad = mainQuad.copy(pose = pose)
        presentedPopupPose = null
        positionPopup(pose)
    }

    private fun positionPopup(pose: QuadPose) {
        val help = state.floatingPopup as? FloatingPopup.Help
        if (help != null) {
            val parent = if (help.anchor.panelId == "extension")
                state.extensionCoordinator?.quadForPlan() ?: mainQuad else mainQuad
            popupQuad = parameterHelpQuad(parent, defaultPopupQuad, help.anchor)
            return
        }
        if (state.rejectedSpatialModalId != null && state.rejectedSpatialModalId == state.spatialModalId) {
            val parent = if (state.page == MenuPage.Wheel)
                state.extensionCoordinator?.quadForPlan() ?: mainQuad else mainQuad
            popupQuad = spatialPopupQuad(parent, "popup", SpatialAnchor(0.5f, 0.5f),
                if (parent.id == "extension") 0.24f else 0.12f,
                SpatialSurfaceSize(defaultPopupQuad.widthMeters, defaultPopupQuad.heightMeters,
                    defaultPopupQuad.widthPixels, defaultPopupQuad.heightPixels),
                facing = SpatialFacing.FaceHead)
            return
        }
        // A wheel-originated dropdown belongs over its extension rather than over the wheel.
        // Keep it farther forward than the extension's head-facing near edge.
        val offset = if (state.page == MenuPage.Wheel && state.wheelSelectedCategory != null)
            popupLocalOffset.copy(x = 0.62f, z = maxOf(popupLocalOffset.z, 0.24f))
        else popupLocalOffset
        popupQuad = defaultPopupQuad.copy(pose = pose.copy(
            position = pose.position + pose.orientation.rotate(offset),
            orientation = pose.orientation * popupLocalOrientation))
    }

    /** Use the exact pose of the popup Quad submitted in this XR frame for laser hit testing. */
    fun updatePresentedPopupPose(pose: QuadPose) {
        presentedPopupPose = pose
    }

    fun showPopup() {
        state.showDialog = true
        positionPopup(mainQuad.pose)
        // With only two UI layers, make room for the modal popup. The extension reappears
        // after dismissal; while it is hidden the main Compose surface shows the same content.
        if (state.modalPopupVisible && !budget.mayUseUiLayers(3)) state.extensionCoordinator?.hide()
        if (ready) {
            state.separatePopupAllowed = canUsePopupLayer()
            return
        }
        if (requested || !canUsePopupLayer()) return
        requested = true
        val requestGeneration = ++generation
        backend.requestPopupQuad(popupQuad) { success ->
            if (requestGeneration != generation || !state.showDialog) {
                requested = false
                if (success) backend.releasePopupQuad(popupQuad)
                if (state.showDialog && shouldUseDedicatedPopup() && canUsePopupLayer()) showPopup()
                return@requestPopupQuad
            }
            requested = false
            ready = success
            state.separatePopupAllowed = success && canUsePopupLayer()
            if (success && !canUsePopupLayer()) releaseReady()
        }
    }

    fun dismissPopup() {
        ++generation
        state.separatePopupAllowed = false
        state.showDialog = false
        presentedPopupPose = null
        releaseReady()
        state.extensionCoordinator?.show()
    }

    /** Change the single popup slot between uses; its parent-local anchor is retained. */
    fun resizePopup(size: SpatialSurfaceSize) {
        require(!state.showDialog && !requested && !ready) { "Dismiss popup before resizing its Quad" }
        popupQuad = defaultPopupQuad.copy(widthMeters = size.widthMeters, heightMeters = size.heightMeters,
            widthPixels = size.widthPixels, heightPixels = size.heightPixels)
        defaultPopupQuad = popupQuad
    }

    fun updateLayerBudget(newBudget: LayerBudget) {
        budget = newBudget
        if (!canUsePopupLayer()) {
            state.separatePopupAllowed = false
            ++generation
            releaseReady()
        } else if (state.showDialog && shouldUseDedicatedPopup()) showPopup()
    }

    private fun shouldUseDedicatedPopup(): Boolean = state.spatialFloatingHost == null ||
        (state.rejectedSpatialModalId != null && state.rejectedSpatialModalId == state.spatialModalId) ||
        (state.floatingPopup as? FloatingPopup.Help)?.let {
            state.rejectedParameterHelpOwner == it.owner
        } == true

    /** Use for both frame submission and hit routing. Never submit a popup before it is ready. */
    private fun canUsePopupLayer(): Boolean = budget.mayUseUiLayers(
        2 + if (state.extensionCoordinator?.quadForPlan() != null) 1 else 0)

    fun plan(): SpatialUiLayerPlan = SpatialUiLayerPlan(mainQuad,
        if (ready && state.separatePopupAllowed) popupQuad.copy(
            pose = presentedPopupPose ?: popupQuad.pose) else null, state.showDialog, budget,
        state.extensionCoordinator?.quadForPlan(), popupModal = state.modalPopupVisible)

    private fun releaseReady() {
        if (ready) backend.releasePopupQuad(popupQuad)
        ready = false
    }
}
