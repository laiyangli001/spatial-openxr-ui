package com.moonlightxr.nativeui

import androidx.compose.runtime.Composable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialPopupCoordinatorTest {
    private val main = UiQuad("main", QuadPose(Vec3(0f, 0f, -2f), Quat(0f, 0f, 0f, 1f)),
        2f, 1f, 2000, 1000)
    private val popup = popupInFrontOf(main, 0.1f, 0.8f, 0.5f, 800, 500)

    private class Backend : PopupQuadBackend {
        var callback: ((Boolean) -> Unit)? = null
        var lastRequestedQuad: UiQuad? = null
        var requested = 0
        var released = 0
        override fun requestPopupQuad(quad: UiQuad, onReady: (Boolean) -> Unit) {
            requested++
            lastRequestedQuad = quad
            callback = onReady
        }
        override fun releasePopupQuad(quad: UiQuad) { released++ }
    }

    private fun unavailableSpatialHost() = object : SpatialFloatingHost {
        override fun show(quad: UiQuad, modal: Boolean, focusable: Boolean,
                          content: @Composable () -> Unit) = false
        override fun presentationState(id: String) = SpatialLayerPresentation.Missing
        override fun hide(id: String) {}
    }

    @Test fun rejectedSharedLayerUsesReservedPopupWithoutAnInlineFlash() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialPopupCoordinator(state, main, popup, backend, LayerBudget(3, 0, 0))
        state.spatialFloatingHost = unavailableSpatialHost()
        state.confirmResetSettings(SettingsResetScope.Picture)
        state.reportSpatialModalAdmission("settings-reset", false)
        assertEquals(1, backend.requested)
        assertTrue(coordinator.awaitingPresentation)
        assertFalse(drawModalInlineFallback(state.spatialModalId, true, coordinator.awaitingPresentation))
        backend.callback!!(true)
        assertTrue(coordinator.plan().separatePopup)
        assertEquals("popup", coordinator.plan().hit(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f))?.quad?.id)
        state.closeDialog()
        assertFalse(state.showDialog)
        assertEquals(1, backend.released)
        assertEquals(null, state.rejectedSpatialModalId)
    }

    @Test fun reservedLayerFailureOrNoBudgetKeepsTheModalVisibleOnMain() {
        for (layers in listOf(1, 3)) {
            val state = MenuUiState()
            val backend = Backend()
            val coordinator = SpatialPopupCoordinator(state, main, popup, backend, LayerBudget(layers, 0, 0))
            state.spatialFloatingHost = unavailableSpatialHost()
            state.openDialog()
            state.reportSpatialModalAdmission("menu-confirmation", false)
            if (layers == 3) backend.callback!!(false)
            assertFalse(coordinator.awaitingPresentation)
            assertFalse(state.separatePopupAllowed)
            assertTrue(drawModalInlineFallback(state.spatialModalId,
                state.rejectedSpatialModalId == state.spatialModalId, coordinator.awaitingPresentation))
            assertEquals("main", coordinator.plan().hit(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f))?.quad?.id)
            state.closeDialog()
            assertFalse(coordinator.plan().popupOpen)
        }
    }

    @Test fun oldReservedAllocationCannotDuplicateANewerGenericModal() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialPopupCoordinator(state, main, popup, backend, LayerBudget(3, 0, 0))
        state.spatialFloatingHost = unavailableSpatialHost()
        state.confirmResetSettings()
        state.reportSpatialModalAdmission("settings-reset", false)
        val late = backend.callback!!
        state.closeDialog()
        state.openDialog()
        state.reportSpatialModalAdmission("settings-reset", false)
        late(true)
        assertEquals("menu-confirmation", state.spatialModalId)
        assertEquals(1, backend.requested)
        assertEquals(1, backend.released)
        assertFalse(coordinator.plan().separatePopup)
        assertEquals(null, state.rejectedSpatialModalId)
    }

    @Test fun reservedModalUsesItsMovedExtensionAsTheParent() {
        val state = MenuUiState()
        val extension = UiQuad("extension", QuadPose(Vec3(1.5f, 1f, -4f),
            Quat(0f, 0.38268343f, 0f, 0.9238795f)), 0.7f, 0.8f, 570, 675)
        SpatialExtensionCoordinator(state, main, extension, object : ExtensionQuadBackend {
            override fun requestExtensionQuad(quad: UiQuad, onReady: (Boolean) -> Unit) { onReady(true) }
            override fun releaseExtensionQuad(quad: UiQuad) {}
        }, LayerBudget(4, 0, 0))
        state.openWheel()
        state.selectWheelCategory("AI 3D")
        val backend = Backend()
        SpatialPopupCoordinator(state, main, popup, backend, LayerBudget(4, 0, 0))
        state.spatialFloatingHost = unavailableSpatialHost()
        state.confirmResetSettings()
        state.reportSpatialModalAdmission("settings-reset", false)
        val requested = checkNotNull(backend.lastRequestedQuad)
        assertEquals("extension", requested.parentId)
        val local = spatialChildOffset(extension, requested)
        assertEquals(0f, local.x, 0.0001f)
        assertEquals(0f, local.y, 0.0001f)
        assertEquals(0.24f, local.z, 0.0001f)
        assertEquals(SpatialFacing.FaceHead, requested.facing)
    }

    @Test fun staysInlineUntilSecondQuadIsReadyThenReleasesItOnDismiss() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialPopupCoordinator(state, main, popup, backend, LayerBudget(3, 1, 0))
        state.openDialog()
        assertTrue(state.showDialog)
        assertTrue(coordinator.plan().inlinePopup)
        assertEquals(1, backend.requested)
        backend.callback!!(true)
        assertTrue(state.separatePopupAllowed)
        assertTrue(coordinator.plan().separatePopup)
        assertEquals(2, coordinator.plan().visibleQuads.size)
        state.closeDialog()
        assertFalse(state.showDialog)
        assertEquals(1, backend.released)
        assertEquals(1, coordinator.plan().visibleQuads.size)
    }

    @Test fun budgetExhaustionKeepsSamePopupClickableInline() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialPopupCoordinator(state, main, popup, backend, LayerBudget(2, 1, 0))
        state.openDialog()
        assertTrue(coordinator.plan().inlinePopup)
        assertEquals(0, backend.requested)
        coordinator.updateLayerBudget(LayerBudget(3, 1, 0))
        assertEquals(1, backend.requested)
        backend.callback!!(true)
        coordinator.updateLayerBudget(LayerBudget(2, 1, 0))
        assertTrue(coordinator.plan().inlinePopup)
        assertEquals(1, backend.released)
    }

    @Test fun resetUsesGenericModalLayerWithoutRequestingTheDedicatedPopup() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialPopupCoordinator(state, main, popup, backend, LayerBudget(3, 0, 0))
        state.spatialFloatingHost = object : SpatialFloatingHost {
            override fun show(quad: UiQuad, modal: Boolean, focusable: Boolean,
                              content: @Composable () -> Unit) = true
            override fun presentationState(id: String) = SpatialLayerPresentation.Ready
            override fun hide(id: String) = Unit
        }
        state.confirmResetSettings(SettingsResetScope.Picture)
        assertEquals("settings-reset", state.spatialModalId)
        assertTrue(coordinator.plan().inlinePopup)
        assertEquals(0, backend.requested)
        coordinator.updateLayerBudget(LayerBudget(2, 0, 0))
        assertEquals(0, backend.requested)

        val modal = spatialPopupQuad(main, "settings-reset", SpatialAnchor(0.5f, 0.5f),
            0.12f, SpatialSurfaceSize(0.9f, 0.5625f, 800, 500))
        val plan = coordinator.plan().copy(spatial = listOf(SpatialInteractiveQuad(modal, true, true)))
        val origin = modal.pose.position + Vec3(0f, 0f, 1f)
        assertEquals("settings-reset", plan.hit(origin, modal.pose.position - origin)?.quad?.id)
        state.closeDialog()
        assertFalse(state.showDialog)
        assertEquals(0, backend.released)
    }

    @Test fun buttonConfirmationUsesTheGenericModalLayer() {
        val state = MenuUiState()
        val backend = Backend()
        SpatialPopupCoordinator(state, main, popup, backend, LayerBudget(3, 0, 0))
        state.spatialFloatingHost = object : SpatialFloatingHost {
            override fun show(quad: UiQuad, modal: Boolean, focusable: Boolean,
                              content: @Composable () -> Unit) = true
            override fun presentationState(id: String) = SpatialLayerPresentation.Ready
            override fun hide(id: String) = Unit
        }
        state.openDialog()
        assertEquals("menu-confirmation", state.spatialModalId)
        assertEquals(0, backend.requested)
        state.closeDialog()
        assertFalse(state.showDialog)
    }

    @Test fun lateAllocationAfterDismissCannotReopenThePopup() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialPopupCoordinator(state, main, popup, backend, LayerBudget(3, 1, 0))
        state.openDialog()
        val first = backend.callback!!
        state.closeDialog()
        first(true)
        assertFalse(state.showDialog)
        assertFalse(state.separatePopupAllowed)
        assertEquals(1, backend.released)
        state.openDialog()
        assertEquals(2, backend.requested)
    }

    @Test fun dropdownChoiceUsesSameSecondQuadAndClearsOnClose() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialPopupCoordinator(state, main, popup, backend, LayerBudget(3, 1, 0))
        var chosen = "Black"
        state.showChoices("背景", listOf("Black", "HDR 湖畔"), chosen) { chosen = it }
        assertTrue(state.floatingPopup is FloatingPopup.Choices)
        assertTrue(coordinator.plan().inlinePopup)
        backend.callback!!(true)
        assertTrue(coordinator.plan().separatePopup)
        (state.floatingPopup as FloatingPopup.Choices).onSelected("HDR 湖畔")
        state.closeDialog()
        assertEquals("HDR 湖畔", chosen)
        assertEquals(null, state.floatingPopup)
        assertFalse(coordinator.plan().separatePopup)
        assertEquals(1, backend.released)
    }

    @Test fun choiceDialogUsesGenericModalWhenSpatialHostExists() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialPopupCoordinator(state, main, popup, backend, LayerBudget(4, 0, 0))
        state.spatialFloatingHost = object : SpatialFloatingHost {
            override fun show(quad: UiQuad, modal: Boolean, focusable: Boolean,
                              content: @Composable () -> Unit) = true
            override fun presentationState(id: String) = SpatialLayerPresentation.Ready
            override fun hide(id: String) = Unit
        }
        state.showChoices("背景", listOf("Black", "HDR 湖畔"), "Black") {}
        assertEquals("menu-choices", state.spatialModalId)
        assertTrue(coordinator.plan().inlinePopup)
        assertEquals(0, backend.requested)
        coordinator.updateLayerBudget(LayerBudget(3, 0, 0))
        assertEquals(0, backend.requested)
        state.closeDialog()
        assertFalse(state.showDialog)
        assertEquals(0, backend.released)
    }

    @Test fun popupHitPlaneTracksRotatedPanelPose() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialPopupCoordinator(state, main, popup, backend, LayerBudget(2, 0, 0))
        state.openDialog()
        backend.callback!!(true)
        val rotated = QuadPose(Vec3(1f, 2f, 3f), Quat(0f, 0.70710677f, 0f, 0.70710677f))
        coordinator.updatePanelPose(rotated)
        val plan = coordinator.plan()
        val popupPose = checkNotNull(plan.popup).pose
        assertEquals(1.1f, popupPose.position.x, 0.0001f)
        assertEquals(2f, popupPose.position.y, 0.0001f)
        assertEquals(3f, popupPose.position.z, 0.0001f)
        assertEquals("popup", plan.hit(Vec3(3f, 2f, 3f), Vec3(-1f, 0f, 0f))?.quad?.id)
    }

    @Test fun anchoredPopupKeepsItsOffsetAndHitAreaAfterPanelRotates() {
        val state = MenuUiState()
        val backend = Backend()
        val anchored = spatialPopupQuad(main, "popup", SpatialAnchor(0.75f, 0.25f), 0.18f,
            SpatialSurfaceSize(0.65f, 0.4f, 800, 500))
        val coordinator = SpatialPopupCoordinator(state, main, anchored, backend, LayerBudget(2, 0, 0))
        state.openDialog()
        backend.callback!!(true)
        val rotated = QuadPose(Vec3(1f, 2f, 3f), Quat(0f, 0.70710677f, 0f, 0.70710677f))
        coordinator.updatePanelPose(rotated)
        val popup = checkNotNull(coordinator.plan().popup)
        val offset = spatialChildOffset(coordinator.plan().main, popup)
        assertEquals(0.5f, offset.x, 0.0001f)
        assertEquals(0.25f, offset.y, 0.0001f)
        assertEquals(0.18f, offset.z, 0.0001f)
        assertEquals(0.65f, popup.widthMeters, 0.0001f)
        assertEquals(0.4f, popup.heightMeters, 0.0001f)
        val eye = popup.pose.position + popup.pose.orientation.rotate(Vec3(0f, 0f, 1f))
        val direction = popup.pose.position - eye
        val hit = coordinator.plan().hit(eye, direction)
        assertEquals("popup", hit?.quad?.id)
        assertEquals(400f, hit?.pixel?.x ?: -1f, 0.001f)
        assertEquals(250f, hit?.pixel?.y ?: -1f, 0.001f)
    }

    @Test fun tiltedPopupKeepsIndependentOrientationAfterParentRotates() {
        val state = MenuUiState()
        val backend = Backend()
        val yaw = Quat(0f, 0.25881904f, 0f, 0.9659258f)
        val tilted = spatialPopupQuad(main, "popup", SpatialAnchor(0.75f, 0.25f), 0.12f,
            SpatialSurfaceSize(0.8f, 0.5f, 800, 500), yaw)
        val coordinator = SpatialPopupCoordinator(state, main, tilted, backend, LayerBudget(2, 0, 0))
        state.openDialog()
        backend.callback!!(true)
        val parentPose = QuadPose(Vec3(1f, 2f, 3f), Quat(0f, 0.70710677f, 0f, 0.70710677f))
        coordinator.updatePanelPose(parentPose)
        val current = checkNotNull(coordinator.plan().popup)
        val relative = spatialChildOrientation(coordinator.plan().main, current)
        assertEquals(yaw.y, relative.y, 0.0001f)
        assertEquals(yaw.w, relative.w, 0.0001f)
        val eye = current.pose.position + current.pose.orientation.rotate(Vec3(0f, 0f, 1f))
        val hit = coordinator.plan().hit(eye, current.pose.position - eye)
        assertEquals("popup", hit?.quad?.id)
        assertEquals(400f, hit?.pixel?.x ?: -1f, 0.001f)
        assertEquals(250f, hit?.pixel?.y ?: -1f, 0.001f)
    }

    @Test fun submittedHeadFacingPoseIsTheOnlyPopupRayPlaneForThatFrame() {
        val state = MenuUiState()
        val backend = Backend()
        val headFacing = spatialPopupQuad(main, "popup", SpatialAnchor(0.75f, 0.25f), 0.12f,
            SpatialSurfaceSize(0.8f, 0.5f, 800, 500), facing = SpatialFacing.FaceHead)
        val coordinator = SpatialPopupCoordinator(state, main, headFacing, backend, LayerBudget(2, 0, 0))
        state.openDialog()
        backend.callback!!(true)
        coordinator.updatePanelPose(main.pose)
        val submittedPose = QuadPose(headFacing.pose.position,
            Quat(0f, 0.25881904f, 0f, 0.9659258f))
        coordinator.updatePresentedPopupPose(submittedPose)
        val presented = checkNotNull(coordinator.plan().popup)
        assertEquals(SpatialFacing.FaceHead, presented.facing)
        assertEquals(submittedPose, presented.pose)
        val eye = submittedPose.position + submittedPose.orientation.rotate(Vec3(0f, 0f, 1f))
        val hit = coordinator.plan().hit(eye, submittedPose.position - eye)
        assertEquals("popup", hit?.quad?.id)
        assertEquals(400f, hit?.pixel?.x ?: -1f, 0.001f)
        assertEquals(250f, hit?.pixel?.y ?: -1f, 0.001f)
        state.closeDialog()
        state.openDialog()
        assertEquals(SpatialFacing.FaceHead, backend.lastRequestedQuad?.facing)
        assertEquals(headFacing.pose.orientation, backend.lastRequestedQuad?.pose?.orientation)
    }

    @Test fun failedPopupAllocationLeavesInlineChoicesOpen() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialPopupCoordinator(state, main, popup, backend, LayerBudget(2, 0, 0))
        state.showChoices("背景", listOf("Black", "HDR 湖畔"), "Black") { }
        backend.callback!!(false)
        assertTrue(state.showDialog)
        assertTrue(coordinator.plan().inlinePopup)
        assertEquals(listOf("main"), coordinator.plan().visibleQuads.map { it.id })
        assertEquals(0, backend.released)
    }

    @Test fun popupCanResizeBetweenUsesWithoutChangingItsAnchor() {
        val state = MenuUiState()
        val backend = Backend()
        val anchored = spatialPopupQuad(main, "popup", SpatialAnchor(0.75f, 0.25f), 0.12f,
            SpatialSurfaceSize(1.08f, 0.675f, 960, 600))
        val coordinator = SpatialPopupCoordinator(state, main, anchored, backend, LayerBudget(2, 0, 0))
        state.openDialog()
        backend.callback!!(true)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            coordinator.resizePopup(SpatialSurfaceSize(0.9f, 0.5625f, 800, 500))
        }
        state.closeDialog()
        coordinator.resizePopup(SpatialSurfaceSize(0.9f, 0.5625f, 800, 500))
        state.openDialog()
        backend.callback!!(true)
        val resized = checkNotNull(coordinator.plan().popup)
        assertEquals(800, resized.widthPixels)
        assertEquals(500, resized.heightPixels)
        val offset = spatialChildOffset(coordinator.plan().main, resized)
        assertEquals(0.5f, offset.x, 0.0001f)
        assertEquals(0.25f, offset.y, 0.0001f)
        val eye = resized.pose.position + resized.pose.orientation.rotate(Vec3(0f, 0f, 1f))
        val hit = coordinator.plan().hit(eye, resized.pose.position - eye)
        assertEquals("popup", hit?.quad?.id)
        assertEquals(400f, hit?.pixel?.x ?: -1f, 0.001f)
        assertEquals(250f, hit?.pixel?.y ?: -1f, 0.001f)
    }

    @Test fun wheelDropdownOpensOverExtensionAndResumesItAfterTwoLayerModal() {
        val state = MenuUiState()
        val popupBackend = Backend()
        val extensionBackend = object : ExtensionQuadBackend {
            var ready: ((Boolean) -> Unit)? = null
            var releases = 0
            override fun requestExtensionQuad(quad: UiQuad, onReady: (Boolean) -> Unit) {
                ready = onReady
            }
            override fun releaseExtensionQuad(quad: UiQuad) { releases++ }
        }
        val extension = spatialPopupQuad(main, "extension", SpatialAnchor(0.81f, 0.5f),
            0.05f, SpatialSurfaceSize(0.7125f, 0.84375f, 570, 675))
        SpatialExtensionCoordinator(state, main, extension, extensionBackend, LayerBudget(2, 0, 0))
        val coordinator = SpatialPopupCoordinator(state, main, popup, popupBackend, LayerBudget(2, 0, 0))
        state.openWheel()
        state.selectWheelCategory("基本")
        extensionBackend.ready!!(true)
        assertEquals(listOf("main", "extension"), coordinator.plan().visibleQuads.map { it.id })
        state.showChoices("背景", listOf("Black", "HDR 湖畔"), "Black") { }
        assertEquals(1, extensionBackend.releases)
        assertEquals(1, popupBackend.requested)
        popupBackend.callback!!(true)
        assertEquals(listOf("main", "popup"), coordinator.plan().visibleQuads.map { it.id })
        assertEquals(0.62f, checkNotNull(coordinator.plan().popup).pose.position.x, 0.0001f)
        state.closeDialog()
        assertEquals(listOf("main"), coordinator.plan().visibleQuads.map { it.id })
        assertFalse(state.separateExtensionAllowed) // waits for the new first frame
        extensionBackend.ready!!(true)
        assertTrue(state.separateExtensionAllowed)
    }
}
