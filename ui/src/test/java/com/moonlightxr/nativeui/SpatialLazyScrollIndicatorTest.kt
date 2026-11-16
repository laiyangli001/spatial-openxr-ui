package com.moonlightxr.nativeui

import org.junit.Assert.assertEquals
import org.junit.Test

class SpatialLazyScrollIndicatorTest {
    @Test fun dragFractionClampsToValidItem() {
        assertEquals(0, scrollIndexForFraction(-2f, 10))
        assertEquals(4, scrollIndexForFraction(0.5f, 10))
        assertEquals(9, scrollIndexForFraction(2f, 10))
        assertEquals(0, scrollIndexForFraction(1f, 0))
    }

    @Test fun viewportSizeIsExcludedFromSeekRangeSoReleaseDoesNotJump() {
        // 20 visible items leave 80 valid first-item positions, not 99.
        assertEquals(0, scrollIndexForFraction(0f, 100, 20))
        assertEquals(40, scrollIndexForFraction(0.5f, 100, 20))
        assertEquals(80, scrollIndexForFraction(1f, 100, 20))
        for (start in listOf(0, 20, 40, 60, 80)) {
            val displayedFraction = start / 80f
            assertEquals(start, scrollIndexForFraction(displayedFraction, 100, 20))
        }
    }

    @Test fun shortEmptyAndInvalidContentCannotSeekPastItsStartRange() {
        assertEquals(0, scrollIndexForFraction(1f, 5, 5))
        assertEquals(0, scrollIndexForFraction(1f, 5, 20))
        assertEquals(0, scrollIndexForFraction(1f, 0, 20))
        assertEquals(0, scrollIndexForFraction(Float.NaN, 100, 20))
        assertEquals(80, scrollIndexForFraction(2f, 100, 20))
    }
}
