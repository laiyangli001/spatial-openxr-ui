package com.moonlightxr.nativeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialUiLayersTest {
    private val main = UiQuad("main", QuadPose(Vec3(0f, 0f, -2f), Quat(0f, 0f, 0f, 1f)),
        2f, 1f, 2000, 1000)
    private val popup = popupInFrontOf(main, 0.12f, 0.8f, 0.5f, 800, 500)

    @Test fun visibleParametersWinOverPhysicallyNearerHomeForegroundAndFreeMenu() {
        val foreground=main.copy(id="home-zz-foreground",pose=main.pose.copy(position=Vec3(0f,0f,-1.5f)))
        val menu=main.copy(id="category-menu",parentId="free-menu",pose=main.pose.copy(position=Vec3(0f,0f,-1.8f)))
        val parameters=main.copy(id="extension",pose=main.pose.copy(position=Vec3(0f,0f,-2.2f)))
        val plan=SpatialUiLayerPlan(main,null,false,LayerBudget(8,0,0),parameters,
            spatial=listOf(SpatialInteractiveQuad(foreground,false,true),SpatialInteractiveQuad(menu,false,true)))
        assertEquals("extension",plan.hit(Vec3(0f,0f,0f),Vec3(0f,0f,-1f))?.quad?.id)
        assertEquals("category-menu",plan.copy(extension=null).hit(Vec3(0f,0f,0f),Vec3(0f,0f,-1f))?.quad?.id)
        assertEquals("home-zz-foreground",plan.copy(extension=null,spatial=listOf(
            SpatialInteractiveQuad(foreground,false,true))).hit(Vec3(0f,0f,0f),Vec3(0f,0f,-1f))?.quad?.id)
    }

    @Test fun modalPopupUsesSecondQuadAndOnlyItsHitTarget() {
        val plan = SpatialUiLayerPlan(main, popup, popupOpen = true,
            budget = LayerBudget(3, projectionAndVideoLayers = 1, backgroundLayers = 0))
        assertTrue(plan.separatePopup)
        assertFalse(plan.inlinePopup)
        assertEquals(listOf("main", "popup"), plan.visibleQuads.map { it.id })
        val center = plan.hit(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f))!!
        assertEquals("popup", center.quad.id)
        assertEquals(400f, center.pixel.x, 0.001f)
        assertEquals(1.88f, center.pixel.distanceMeters, 0.001f)
        // Outside the modal popup, the underlying menu must not be clickable.
        assertNull(plan.hit(Vec3(0.8f, 0f, 0f), Vec3(0f, 0f, -1f)))
    }

    @Test fun exhaustedLayerBudgetFallsBackToInlinePopup() {
        val plan = SpatialUiLayerPlan(main, popup, popupOpen = true,
            budget = LayerBudget(2, projectionAndVideoLayers = 1, backgroundLayers = 0))
        assertFalse(plan.separatePopup)
        assertTrue(plan.inlinePopup)
        assertEquals(listOf("main"), plan.visibleQuads.map { it.id })
        assertEquals("main", plan.hit(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f))?.quad?.id)
    }

    @Test fun nonModalPopupCanBeClickedAndNearestSurfaceWins() {
        val extension = UiQuad("extension", main.pose.copy(
            position = Vec3(0.7f, 0f, -1.8f)), 0.7f, 0.8f, 700, 800)
        val plan = SpatialUiLayerPlan(main, popup, popupOpen = true,
            budget = LayerBudget(4, 0, 0), extension = extension, popupModal = false)
        assertEquals("popup", plan.hit(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f))?.quad?.id)
        assertEquals("extension", plan.hit(Vec3(0.7f, 0f, 0f),
            Vec3(0f, 0f, -1f))?.quad?.id)
        assertEquals("main", plan.hit(Vec3(-0.8f, 0f, 0f), Vec3(0f, 0f, -1f))?.quad?.id)
    }

    @Test fun inlineModalBlocksOtherSpatialSurfacesUntilPopupIsReady() {
        val child = UiQuad("category-menu", main.pose.copy(
            position = Vec3(0f, 0f, -1.85f)), 0.6f, 0.7f, 600, 700)
        val plan = SpatialUiLayerPlan(main, null, popupOpen = true,
            budget = LayerBudget(4, 0, 0), spatial = listOf(
                SpatialInteractiveQuad(child, modal = false, focusable = true)))
        assertTrue(plan.inlinePopup)
        assertEquals("main", plan.hit(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f))?.quad?.id)
    }

    @Test fun extensionRemainsClickableAcrossTransparentMainPlane() {
        val extension = UiQuad("extension", main.pose.copy(
            position = Vec3(0.7f, 0f, -2.1f)), 0.7f, 0.8f, 700, 800)
        val plan = SpatialUiLayerPlan(main, null, popupOpen = false,
            budget = LayerBudget(3, 0, 0), extension = extension)
        assertEquals("extension", plan.hit(Vec3(0.7f, 0f, 0f),
            Vec3(0f, 0f, -1f))?.quad?.id)
    }

    @Test fun popupOffsetFollowsRotatedPanelNormal() {
        val yaw90 = Quat(0f, 0.70710677f, 0f, 0.70710677f)
        val rotatedMain = main.copy(pose = QuadPose(Vec3(1f, 2f, 3f), yaw90))
        val rotatedPopup = popupInFrontOf(rotatedMain, 0.1f, 0.8f, 0.5f, 800, 500)
        assertEquals(1.1f, rotatedPopup.pose.position.x, 0.0001f)
        assertEquals(2f, rotatedPopup.pose.position.y, 0.0001f)
        assertEquals(3f, rotatedPopup.pose.position.z, 0.0001f)
    }

    @Test fun extensionIsIndependentHitTargetAndModalPopupTakesPriority() {
        val extension = spatialPopupQuad(main, "extension", SpatialAnchor(0.81f, 0.5f),
            0.05f, SpatialSurfaceSize(0.7125f, 0.84375f, 570, 675),
            facing = SpatialFacing.FaceHead)
        val rayOrigin = Vec3(0.62f, 0f, 0f)
        val rayDirection = Vec3(0f, 0f, -1f)
        val normal = SpatialUiLayerPlan(main, null, false, LayerBudget(3, 0, 0), extension)
        assertEquals(listOf("main", "extension"), normal.visibleQuads.map { it.id })
        assertEquals("extension", normal.hit(rayOrigin, rayDirection)?.quad?.id)
        val modal = SpatialUiLayerPlan(main, popup.copy(pose = popup.pose.copy(
            position = Vec3(0.62f, 0f, -1.76f))), true, LayerBudget(3, 0, 0), extension)
        assertEquals(listOf("main", "extension", "popup"), modal.visibleQuads.map { it.id })
        assertEquals("popup", modal.hit(rayOrigin, rayDirection)?.quad?.id)
    }

    @Test fun freeCategoryMenuWinsRayWhileHomeRemainsVisible() {
        val category = UiQuad("category-menu", main.pose.copy(
            position = Vec3(0f, 0f, -1.85f)), 0.675f, 0.84375f, 540, 675,
            parentId = "free-menu")
        val extension = UiQuad("extension", main.pose.copy(
            position = Vec3(0.75f, 0f, -1.81f)), 0.7125f, 0.84375f, 570, 675)
        val plan = SpatialUiLayerPlan(main, null, false, LayerBudget(4, 0, 0), extension,
            spatial = listOf(SpatialInteractiveQuad(category, modal = false, focusable = true)))
        assertEquals(listOf("main", "extension", "category-menu"),
            plan.visibleQuads.map { it.id })
        assertEquals("category-menu", plan.hit(Vec3(0f, 0f, 0f),
            Vec3(0f, 0f, -1f))?.quad?.id)
        assertEquals("extension", plan.hit(Vec3(0.75f, 0f, 0f),
            Vec3(0f, 0f, -1f))?.quad?.id)
        assertEquals("main", plan.hit(Vec3(-0.75f, 0f, 0f),
            Vec3(0f, 0f, -1f))?.quad?.id)
    }

    @Test fun enlargedFreeMenuBehindTransparentHomeStillReceivesShortPress() {
        val enlarged = UiQuad("category-menu", QuadPose(Vec3(0f, 0f, -2.4f),
            Quat(0f, 0f, 0f, 1f)), 1.35f, 1.6875f, 540, 675,
            parentId = "free-menu")
        val plan = SpatialUiLayerPlan(main, null, false, LayerBudget(4, 0, 0),
            spatial = listOf(SpatialInteractiveQuad(enlarged, false, true)))
        val origin = Vec3(0f, 0f, 0f)
        val direction = Vec3(0f, 0f, -1f)
        assertEquals("category-menu", plan.hit(origin, direction)?.quad?.id)
        val routed = mutableListOf<Pair<PixelHit?, Boolean>>()
        val sink = object : QuadPointerSink {
            override fun update(hit: PixelHit?, triggerDown: Boolean, verticalScroll: Float) {
                routed += hit to triggerDown
            }
            override fun cancel() = Unit
        }
        val router = SpatialRayRouter(sink, popup = { null }, spatial = { sink })
        router.update(plan, origin, direction, false, 0f)
        router.update(plan, origin, direction, true, 0f)
        router.update(plan, origin, direction, false, 0f)
        assertEquals(listOf(false, true, false), routed.map { it.second })
    }

    @Test fun twoLayerBudgetKeepsExtensionButPlacesModalInline() {
        val extension = spatialPopupQuad(main, "extension", SpatialAnchor(0.81f, 0.5f),
            0.05f, SpatialSurfaceSize(0.7125f, 0.84375f, 570, 675))
        val plan = SpatialUiLayerPlan(main, popup, true, LayerBudget(2, 0, 0), extension)
        assertTrue(plan.inlinePopup)
        assertEquals(listOf("main", "extension"), plan.visibleQuads.map { it.id })
        // The modal still blocks clicks on the extension, even when rendered inline.
        assertEquals("main", plan.hit(Vec3(0.62f, 0f, 0f), Vec3(0f, 0f, -1f))?.quad?.id)
    }
}
