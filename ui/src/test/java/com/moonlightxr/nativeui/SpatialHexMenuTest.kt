package com.moonlightxr.nativeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialHexMenuTest {
    @Test fun everyVisibleCellHasAnActionAndItsOwnScaledHitRegion() {
        val cells = HexMenuGeometry.cells
        assertEquals(7, cells.size)
        assertEquals(listOf("基本", "多显示器", "画面", "AI 3D", "控制", "高级").toSet(),
            cells.filterIndexed { index, _ -> index != HexMenuGeometry.Stream }.map { it.label }.toSet())
        assertTrue(cells.all { it.label.isNotBlank() && it.label != "+" })
        cells.forEachIndexed { index, cell ->
            assertEquals(index, HexMenuGeometry.hit(cell.x, cell.y, 360f, 450f))
            assertEquals(index, HexMenuGeometry.hit(cell.x * 1.5f, cell.y * 1.5f, 540f, 675f))
        }
    }

    @Test fun transparentCornersAndHorizontalAndDiagonalGapsDoNotActivateCells() {
        val cells = HexMenuGeometry.cells
        val first = cells[0]
        assertEquals(HexMenuGeometry.Outside, HexMenuGeometry.hit(
            first.x + HexMenuGeometry.halfWidth * 0.95f,
            first.y - HexMenuGeometry.Radius * 0.95f, 360f, 450f))
        for (neighbor in listOf(cells[1], cells[HexMenuGeometry.Stream])) {
            assertEquals(HexMenuGeometry.Outside, HexMenuGeometry.hit(
                (first.x + neighbor.x) / 2f, (first.y + neighbor.y) / 2f, 360f, 450f))
        }
        assertEquals(HexMenuGeometry.Outside, HexMenuGeometry.hit(0f, 0f, 360f, 450f))
        assertEquals(HexMenuGeometry.Outside, HexMenuGeometry.hit(Float.NaN, 100f, 360f, 450f))
        assertEquals(HexMenuGeometry.Outside, HexMenuGeometry.hit(100f, 100f, 0f, 450f))
    }

    @Test fun artworkFitsTheMenuBoundsWithTheSameRadiusForEveryCell() {
        HexMenuGeometry.cells.forEach { cell ->
            assertTrue(cell.x - HexMenuGeometry.halfWidth > 0f)
            assertTrue(cell.x + HexMenuGeometry.halfWidth < HexMenuGeometry.Width)
            assertTrue(cell.y - HexMenuGeometry.Radius > 0f)
            assertTrue(cell.y + HexMenuGeometry.Radius < HexMenuGeometry.Height)
        }
    }

    @Test fun reopeningMenuClearsSelectionForHexAndFlatLayouts() {
        for (layout in listOf("六边形菜单", "平面菜单")) {
            val state = MenuUiState()
            state.menuLayout = layout
            state.selectWheelCategory("高级")
            state.openWheel()
            assertEquals(MenuPage.Wheel, state.page)
            assertNull(state.wheelSelectedCategory)
            state.selectWheelCategory("画面")
            assertEquals("画面", state.category)
        }
    }
}

