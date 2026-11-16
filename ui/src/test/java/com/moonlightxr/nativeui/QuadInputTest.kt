package com.moonlightxr.nativeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadInputTest {
    private val pose = QuadPose(Vec3(0f, 0f, -2f), Quat(0f, 0f, 0f, 1f))

    @Test fun centerAndCornersMapToTexturePixels() {
        val center = QuadInput.intersect(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f), pose, 2f, 1f, 2000, 1000)!!
        assertEquals(1000f, center.x, 0.001f)
        assertEquals(500f, center.y, 0.001f)
        assertEquals(2f, center.distanceMeters, 0.001f)
        val topLeft = QuadInput.intersect(Vec3(-1f, 0.5f, 0f), Vec3(0f, 0f, -1f), pose, 2f, 1f, 2000, 1000)!!
        assertEquals(0f, topLeft.x, 0.001f)
        assertEquals(0f, topLeft.y, 0.001f)
    }

    @Test fun backfaceAndOutOfBoundsAreNotClickable() {
        assertNull(QuadInput.intersect(Vec3(0f, 0f, -3f), Vec3(0f, 0f, 1f), pose, 2f, 1f, 2000, 1000))
        assertNull(QuadInput.intersect(Vec3(2f, 0f, 0f), Vec3(0f, 0f, -1f), pose, 2f, 1f, 2000, 1000))
    }

    @Test fun layerBudgetFallsBackBeforeOverflow() {
        val budget = LayerBudget(3, projectionAndVideoLayers = 1, backgroundLayers = 1)
        assertEquals(1, budget.availableUiLayerCount)
        assertTrue(budget.mayUseUiLayers(1))
        assertFalse(budget.useSeparatePopup())
    }

    @Test fun qaBackdropConsumesOneOfTheNativeCompositionLayers() {
        val withBackdrop = nativeMenuLayerBudget(3, qaBackdropRequested = true)
        assertEquals(1, withBackdrop.availableUiLayerCount)
        assertFalse(withBackdrop.mayUseUiLayers(2))
        assertFalse(withBackdrop.mayUseUiLayers(3))
        assertFalse(nativeMenuLayerBudget(3, qaBackdropRequested = false).mayUseUiLayers(3))
        // The native host suppresses the QA backdrop when the runtime has only one layer.
        assertTrue(nativeMenuLayerBudget(1, qaBackdropRequested = true).mayUseUiLayers(1))
    }
    @Test fun nativeSceneAndStereoIconsLeaveOnlyThirteenUiLayersOnSixteenLayerRuntime() {
        val budget=nativeMenuLayerBudget(16,false)
        assertEquals(3,budget.projectionAndVideoLayers)
        assertEquals(13,budget.availableUiLayerCount)
        assertTrue(budget.mayUseUiLayers(13))
        assertFalse(budget.mayUseUiLayers(14))
        val qa=nativeMenuLayerBudget(16,true)
        assertEquals(12,qa.availableUiLayerCount)
        // Ten ordinary floating surfaces plus main/extension/popup consume all 13 UI slots.
        assertTrue(budget.mayUseUiLayers(3+10))
        assertFalse(budget.mayUseUiLayers(3+11))
    }
    @Test fun disabledNativeFeaturesAreNotChargedOnSmallRuntimes() {
        assertEquals(0,nativeMenuLayerBudget(1,false).projectionAndVideoLayers)
        assertEquals(1,nativeMenuLayerBudget(2,false).projectionAndVideoLayers)
        assertEquals(1,nativeMenuLayerBudget(3,false).projectionAndVideoLayers)
        assertEquals(3,nativeMenuLayerBudget(4,false).projectionAndVideoLayers)
    }

}
