package com.moonlightxr.nativeui

/** Non-modal wheel extension: unlike an inline Compose child it owns its XR pose and hit plane. */
interface ExtensionQuadBackend {
    fun requestExtensionQuad(quad: UiQuad, onReady: (Boolean) -> Unit)
    fun releaseExtensionQuad(quad: UiQuad)
}

class SpatialExtensionCoordinator(
    private val state: MenuUiState,
    private var mainQuad: UiQuad,
    private var extensionQuad: UiQuad,
    private val backend: ExtensionQuadBackend,
    private val budget: LayerBudget,
) {
    internal val localOffset = spatialChildOffset(mainQuad, extensionQuad)
    internal val localOrientation = spatialChildOrientation(mainQuad, extensionQuad)
    private var presentedPose: QuadPose? = null
    private var generation = 0
    private var requested = false
    private var ready = false

    init { state.extensionCoordinator = this }

    fun show() {
        if (state.page != MenuPage.Wheel || state.wheelSelectedCategory == null) return
        // The extension is positioned from the free menu. Until that Quad is presented,
        // its content belongs to the main-panel fallback instead of a floating orphan.
        if (state.spatialFloatingHost != null &&
            state.spatialFloatingHost?.presentationState("category-menu") !=
                SpatialLayerPresentation.Ready) return
        if (ready) return
        if (requested || !budget.mayUseUiLayers(2)) return
        requested = true
        val current = ++generation
        backend.requestExtensionQuad(extensionQuad) { success ->
            if (current != generation) return@requestExtensionQuad
            requested = false
            ready = success
            state.separateExtensionAllowed = success
        }
    }

    fun hide() {
        ++generation
        val wasRequested = requested
        requested = false
        presentedPose = null
        state.separateExtensionAllowed = false
        if (ready || wasRequested) backend.releaseExtensionQuad(extensionQuad)
        ready = false
    }

    fun updatePanelPose(pose: QuadPose) {
        mainQuad = mainQuad.copy(pose = pose)
        presentedPose = null
        extensionQuad = extensionQuad.copy(pose = pose.copy(
            position = pose.position + pose.orientation.rotate(localOffset),
            orientation = pose.orientation * localOrientation))
    }

    fun updatePresentedExtensionPose(pose: QuadPose) { presentedPose = pose }

    fun quadForPlan(): UiQuad? = if (ready && state.separateExtensionAllowed &&
        state.page == MenuPage.Wheel && state.wheelSelectedCategory != null)
        extensionQuad.copy(pose = presentedPose ?: extensionQuad.pose) else null
}
