package com.moonlightxr.nativeui

import androidx.compose.runtime.Composable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialExtensionCoordinatorTest {
    private val main = UiQuad("main", QuadPose(Vec3(0f, 0f, -2f), Quat(0f, 0f, 0f, 1f)),
        2f, 1.125f, 1600, 900)
    private val extension = spatialPopupQuad(main, "extension", SpatialAnchor(0.81f, 0.5f),
        0.05f, SpatialSurfaceSize(0.7125f, 0.84375f, 570, 675),
        facing = SpatialFacing.FaceHead)

    private class Backend : ExtensionQuadBackend {
        var requested = 0
        var released = 0
        var callback: ((Boolean) -> Unit)? = null
        override fun requestExtensionQuad(quad: UiQuad, onReady: (Boolean) -> Unit) {
            requested++
            callback = onReady
        }
        override fun releaseExtensionQuad(quad: UiQuad) { released++ }
    }

    @Test fun movingFreeMenuBeforeOpeningExtensionDoesNotDoubleItsWorldOffset() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialExtensionCoordinator(state, main, extension, backend,
            LayerBudget(3, 0, 0))
        val fixedLocalOffset = spatialChildOffset(main, extension)
        val movedMenu = QuadPose(Vec3(1.4f, 1.5f, -2.2f),
            Quat(0f, 0.25881904f, 0f, 0.9659258f))
        coordinator.updatePanelPose(movedMenu)
        assertEquals(fixedLocalOffset, coordinator.localOffset)
        val movedExtension = movedMenu.position + movedMenu.orientation.rotate(coordinator.localOffset)
        assertEquals(movedExtension, movedMenu.position + movedMenu.orientation.rotate(fixedLocalOffset))
        assertTrue((spatialChildOffset(main, extension.copy(pose =
            extension.pose.copy(position = movedExtension))).x - fixedLocalOffset.x) > 1f)
    }

    @Test fun extensionWaitsForFreeMenuFirstFrameAndCanReturnToInlineFallback() {
        val state = MenuUiState()
        var presentation = SpatialLayerPresentation.Requested
        state.spatialFloatingHost = object : SpatialFloatingHost {
            override fun show(quad: UiQuad, modal: Boolean, focusable: Boolean,
                              content: @Composable () -> Unit) = true
            override fun presentationState(id: String) = presentation
            override fun hide(id: String) = Unit
        }
        val backend = Backend()
        val coordinator = SpatialExtensionCoordinator(state, main, extension, backend,
            LayerBudget(3, 0, 0))
        state.openWheel()
        state.selectWheelCategory("AI 3D")
        assertEquals(0, backend.requested)
        assertFalse(state.separateExtensionAllowed)
        presentation = SpatialLayerPresentation.Ready
        coordinator.show()
        assertEquals(1, backend.requested)
        backend.callback!!(true)
        assertTrue(state.separateExtensionAllowed)
        presentation = SpatialLayerPresentation.Failed
        coordinator.hide()
        assertEquals(1, backend.released)
        assertFalse(state.separateExtensionAllowed)
        coordinator.show()
        assertEquals(1, backend.requested)
    }

    @Test fun inlineUntilFirstFrameThenIndependentAndRestoredOnPageExit() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialExtensionCoordinator(state, main, extension, backend,
            LayerBudget(3, 0, 0))
        state.openWheel()
        state.selectWheelCategory("基本")
        assertEquals(1, backend.requested)
        assertFalse(state.separateExtensionAllowed)
        assertNull(coordinator.quadForPlan())
        backend.callback!!(true)
        assertTrue(state.separateExtensionAllowed)
        assertEquals(extension, coordinator.quadForPlan())
        state.page = MenuPage.Home
        assertFalse(state.separateExtensionAllowed)
        assertNull(coordinator.quadForPlan())
        assertEquals(1, backend.released)
    }

    @Test fun lateReadyAfterLeavingWheelCannotShowOldPanel() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialExtensionCoordinator(state, main, extension, backend,
            LayerBudget(3, 0, 0))
        state.openWheel()
        state.selectWheelCategory("AI 3D")
        val staleReady = backend.callback!!
        state.page = MenuPage.Settings
        staleReady(true)
        assertNull(coordinator.quadForPlan())
        assertFalse(state.separateExtensionAllowed)
        assertEquals(1, backend.released)
    }

    @Test fun presentedHeadFacingPoseDrivesExtensionHitPlane() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialExtensionCoordinator(state, main, extension, backend,
            LayerBudget(3, 0, 0))
        state.openWheel()
        state.selectWheelCategory("基本")
        backend.callback!!(true)
        val submitted = extension.pose.copy(orientation = Quat(0f, 0.25881904f, 0f, 0.9659258f))
        coordinator.updatePanelPose(main.pose)
        coordinator.updatePresentedExtensionPose(submitted)
        val current = checkNotNull(coordinator.quadForPlan())
        assertEquals(submitted, current.pose)
        val origin = submitted.position + submitted.orientation.rotate(Vec3(0f, 0f, 1f))
        val plan = SpatialUiLayerPlan(main, null, false, LayerBudget(3, 0, 0), current)
        assertEquals("extension", plan.hit(origin, submitted.position - origin)?.quad?.id)
    }

    @Test fun changingMenuLayoutKeepsTheReadyExtensionAndItsPresentedPose() {
        val state = MenuUiState()
        val backend = Backend()
        val coordinator = SpatialExtensionCoordinator(state, main, extension, backend,
            LayerBudget(3, 0, 0))
        state.openWheel()
        state.selectWheelCategory("高级")
        backend.callback!!(true)
        val submitted = extension.pose.copy(orientation = Quat(0f, 0.25881904f, 0f, 0.9659258f))
        coordinator.updatePresentedExtensionPose(submitted)

        for (layout in listOf("六边形菜单", "平面菜单").reversed()) {
            state.menuLayout = layout
            assertEquals("高级", state.wheelSelectedCategory)
            assertTrue(state.separateExtensionAllowed)
            assertEquals(submitted, coordinator.quadForPlan()?.pose)
            assertEquals(1, backend.requested)
            assertEquals(0, backend.released)
        }

        state.openWheel()
        assertNull(state.wheelSelectedCategory)
        assertFalse(state.separateExtensionAllowed)
        assertEquals(1, backend.released)
    }
}
