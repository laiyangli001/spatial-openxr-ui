package com.moonlightxr.nativeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpatialWindowControlsTest {
    private val main = UiQuad("main", QuadPose(Vec3(0f, 0f, -2f), Quat(0f, 0f, 0f, 1f)),
        2f, 1f, 1600, 800)
    private val childSize = SpatialSurfaceSize(0.5f, 0.25f, 400, 200)

    @Test fun popupUsesParentLocalCoordinatesAndSitsInFront() {
        val popup = spatialPopupQuad(main, "choices", SpatialAnchor(0.75f, 0.25f), 0.12f, childSize)
        assertEquals(0.5f, popup.pose.position.x, 0.0001f)
        assertEquals(0.25f, popup.pose.position.y, 0.0001f)
        assertEquals(-1.88f, popup.pose.position.z, 0.0001f)
        val offset = spatialChildOffset(main, popup)
        assertEquals(0.5f, offset.x, 0.0001f)
        assertEquals(0.25f, offset.y, 0.0001f)
        assertEquals(0.12f, offset.z, 0.0001f)
    }

    @Test fun popupRequestKeepsExtensionAnchorWhenParentMovesBeforeFirstFrame() {
        val popup = spatialPopupQuad(main.copy(id = "extension"), "settings-reset",
            SpatialAnchor(0.75f, 0.25f), 0.24f, childSize)
        val movedParent = main.copy(id = "extension", pose = main.pose.copy(
            position = Vec3(1.5f, 0.4f, -3f)))
        assertEquals(0.5f, spatialRequestOffset(movedParent, popup).x, 0.0001f)
        assertEquals(0.25f, spatialRequestOffset(movedParent, popup).y, 0.0001f)
        assertEquals(0.24f, spatialRequestOffset(movedParent, popup).z, 0.0001f)
    }

    @Test fun popupCanTiltWithoutMovingItsAnchor() {
        val yaw = Quat(0f, 0.25881904f, 0f, 0.9659258f)
        val popup = spatialPopupQuad(main, "tilted", SpatialAnchor(0.75f, 0.25f),
            0.12f, childSize, yaw)
        val offset = spatialChildOffset(main, popup)
        val relative = spatialChildOrientation(main, popup)
        assertEquals(0.5f, offset.x, 0.0001f)
        assertEquals(0.25f, offset.y, 0.0001f)
        assertEquals(yaw.y, relative.y, 0.0001f)
        val eye = popup.pose.position + popup.pose.orientation.rotate(Vec3(0f, 0f, 1f))
        val hit = QuadInput.intersect(eye, popup.pose.position - eye, popup.pose,
            popup.widthMeters, popup.heightMeters, popup.widthPixels, popup.heightPixels)
        assertEquals(200f, hit?.x ?: -1f, 0.001f)
        assertEquals(100f, hit?.y ?: -1f, 0.001f)
    }

    @Test fun subwindowsStayBesideParent() {
        assertEquals(-1.35f, spatialSubwindowQuad(main, "left", SpatialSide.Left, 0.1f,
            childSize).pose.position.x, 0.0001f)
        assertEquals(1.35f, spatialSubwindowQuad(main, "right", SpatialSide.Right, 0.1f,
            childSize).pose.position.x, 0.0001f)
    }

    @Test fun scaledFreeMenuAndExtensionKeepAClickableGap() {
        val menu = UiQuad("category-menu", main.pose, 0.675f, 0.84375f, 540, 675,
            parentId = "free-menu")
        val extension = spatialMenuExtensionQuad(menu,
            SpatialSurfaceSize(0.7125f, 0.84375f, 570, 675))
        for (scale in listOf(1f, 2f)) {
            val scaledMenu = menu.copy(widthMeters = menu.widthMeters * scale,
                heightMeters = menu.heightMeters * scale)
            val offset = spatialChildOffset(menu, extension)
            val scaledExtension = extension.copy(pose = extension.pose.copy(position =
                menu.pose.position + menu.pose.orientation.rotate(offset * scale)),
                widthMeters = extension.widthMeters * scale,
                heightMeters = extension.heightMeters * scale)
            val menuRight = scaledMenu.pose.position.x + scaledMenu.widthMeters / 2f
            val extensionLeft = scaledExtension.pose.position.x - scaledExtension.widthMeters / 2f
            assertEquals(0.08f * scale, extensionLeft - menuRight, 0.0001f)
            val plan = SpatialUiLayerPlan(main, null, false, LayerBudget(4, 0, 0),
                extension = scaledExtension, spatial = listOf(
                    SpatialInteractiveQuad(scaledMenu, modal = false, focusable = true)))
            val target = Vec3(menuRight - 0.03f * scale, 0f, menu.pose.position.z)
            val direction = target - Vec3(0f, 0f, 0f)
            assertEquals("category-menu", plan.hit(Vec3(0f, 0f, 0f), direction)?.quad?.id)
        }
    }

    @Test fun augmentCanSitAboveParent() {
        val augment = spatialAugmentQuad(main, "toolbar", SpatialAnchor(0.5f, 0f),
            Vec3(0f, 0.1f, 0.05f), childSize)
        assertEquals(0.6f, augment.pose.position.y, 0.0001f)
        assertEquals(-1.95f, augment.pose.position.z, 0.0001f)
    }

    @Test fun augmentBottomAlignmentKeepsQuickBarOutsideTheTopEdge() {
        val bar = spatialAugmentQuad(main, "quickbar", SpatialAnchor(0.5f, 0f),
            Vec3(0f, 0.025f, 0.02f), SpatialSurfaceSize(0.9f, 0.16f, 900, 160),
            alignment = SpatialAnchor(0.5f, 1f))
        assertEquals(0.605f, bar.pose.position.y, 0.0001f)
        assertEquals(0.025f, bar.pose.position.y - bar.heightMeters / 2f - main.heightMeters / 2f,
            0.0001f)
        assertEquals(-1.98f, bar.pose.position.z, 0.0001f)
    }

    @Test fun augmentAlignmentAndRotationStayRelativeToParent() {
        val yaw = Quat(0f, 0.25881904f, 0f, 0.9659258f)
        val augment = spatialAugmentQuad(main, "corner", SpatialAnchor(0f, 0f),
            Vec3(0f, 0f, 0.016f), childSize,
            alignment = SpatialAnchor(0f, 0f), localOrientation = yaw)
        val alignedCorner = augment.pose.position + augment.pose.orientation.rotate(
            Vec3(-childSize.widthMeters / 2f, childSize.heightMeters / 2f, 0f))
        assertEquals(-1f, alignedCorner.x, 0.0001f)
        assertEquals(0.5f, alignedCorner.y, 0.0001f)
        assertEquals(-1.984f, alignedCorner.z, 0.0001f)
        assertEquals(yaw.y, spatialChildOrientation(main, augment).y, 0.0001f)
        assertThrows(IllegalArgumentException::class.java) {
            spatialAugmentQuad(main, "bad", SpatialAnchor(0.5f, 0.5f), Vec3(0f, 0f, 0f),
                childSize, localOrientation = Quat(0f, 0f, 0f, 2f))
        }
    }

    @Test fun invalidSizeAndAnchorAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { SpatialAnchor(-0.1f, 0f) }
        assertThrows(IllegalArgumentException::class.java) { SpatialSurfaceSize(0f, 1f, 100, 100) }
        assertThrows(IllegalArgumentException::class.java) {
            spatialPopupQuad(main, "bad", SpatialAnchor(0.5f, 0.5f), 0.12f, childSize,
                Quat(0f, 0f, 0f, 2f))
        }
    }
}
