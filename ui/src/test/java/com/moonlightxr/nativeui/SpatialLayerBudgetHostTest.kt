package com.moonlightxr.nativeui

import androidx.compose.runtime.Composable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialLayerBudgetHostTest {
    private val pose = QuadPose(Vec3(0f, 0f, -2f), Quat(0f, 0f, 0f, 1f))
    private fun quad(id: String) = UiQuad(id, pose, 0.5f, 0.3f, 500, 300)

    @Test fun reservesBackgroundAndVideoBeforePopup() {
        val removed = mutableListOf<String>()
        val sink = object : SpatialLayerSink {
            override fun presentationState(id: String) = SpatialLayerPresentation.Ready
            override fun upsert(quad: UiQuad, modal: Boolean, focusable: Boolean,
                                content: @Composable () -> Unit) = true
            override fun remove(id: String) { removed += id }
        }
        val host = SpatialLayerBudgetHost(4, 3, sink)
        assertTrue(host.show(quad("menu"), true, true) {})
        assertEquals(1, host.activeLayerCount)
        assertTrue(host.show(quad("menu"), true, true) {})
        assertFalse(host.show(quad("tooltip"), false, false) {})
        host.hide("menu")
        assertEquals(listOf("menu"), removed)
        assertTrue(host.show(quad("tooltip"), false, false) {})
    }

    @Test fun failedAllocationNeverConsumesCapacity() {
        val sink = object : SpatialLayerSink {
            override fun presentationState(id: String) = SpatialLayerPresentation.Failed
            override fun upsert(quad: UiQuad, modal: Boolean, focusable: Boolean,
                                content: @Composable () -> Unit) = false
            override fun remove(id: String) = Unit
        }
        val host = SpatialLayerBudgetHost(1, 0, sink)
        assertFalse(host.show(quad("popup"), true, true) {})
        assertEquals(0, host.activeLayerCount)
    }

    @Test fun currentNativeSceneAndFloatingMenusShareOneSixteenLayerBudget() {
        val active = mutableSetOf<String>()
        val sink = object : SpatialLayerSink {
            override fun presentationState(id: String) = if (id in active)
                SpatialLayerPresentation.Ready else SpatialLayerPresentation.Missing
            override fun upsert(quad: UiQuad, modal: Boolean, focusable: Boolean,
                                content: @Composable () -> Unit): Boolean {
                active += quad.id
                return true
            }
            override fun remove(id: String) { active -= id }
        }
        val budget=nativeMenuLayerBudget(16,false)
        val host=SpatialLayerBudgetHost(budget.availableUiLayerCount,3,sink)
        // The icon eye pair and hand Projection are owned by native, not by this host.
        listOf("home-zz-foreground","side-navigation","category-menu","controller-performance",
            "controller-guide","controller-b-hint","parameter-help").forEach {
            assertTrue(host.show(quad(it),false,it=="category-menu") {})
        }
        assertTrue(host.show(quad("reset-modal"),true,true) {})
        val (left,right)=stereoImageQuads(quad("spatial-photo"))
        assertTrue(host.showStereo(left,right) {})
        assertEquals(10,host.activeLayerCount)
        assertEquals(16,budget.projectionAndVideoLayers+3+host.activeLayerCount)
        assertFalse(host.show(quad("overflow"),true,true) {})
        host.hide("reset-modal")
        val (extraLeft,extraRight)=stereoImageQuads(quad("second-photo"))
        assertFalse(host.showStereo(extraLeft,extraRight) {})
        assertFalse(extraLeft.id in active || extraRight.id in active)
        assertTrue(host.show(quad("replacement-modal"),true,true) {})
    }
}
