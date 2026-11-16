package com.moonlightxr.nativeui

import org.junit.Assert.*
import org.junit.Test

class SpatialScrollCapturePlanTest {
    @Test fun coversEntireContentWithoutGapsOrDuplicateBottomPages() {
        for (maximum in listOf(0, 1, 44, 558, 559, 1900, 8000)) {
            val positions = spatialScrollCapturePositions(maximum, 558)
            assertEquals(0, positions.first())
            assertEquals(maximum, positions.last())
            assertEquals(positions.distinct(), positions)
            positions.zipWithNext().forEach { (a, b) -> assertTrue(b > a && b - a < 558) }
        }
    }
    @Test fun smallViewportStillMakesProgress() {
        assertEquals(listOf(0, 1, 2, 3), spatialScrollCapturePositions(3, 1))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnmeasuredScrollRange() { spatialScrollCapturePositions(Int.MAX_VALUE, 558) }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnmeasuredViewport() { spatialScrollCapturePositions(100, 0) }
}
