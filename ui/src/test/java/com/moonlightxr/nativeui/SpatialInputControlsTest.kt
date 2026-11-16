package com.moonlightxr.nativeui

import org.junit.Assert.assertEquals
import org.junit.Test

class SpatialInputControlsTest {
    @Test fun stepClampsWithoutIntegerOverflow() {
        assertEquals(100, boundedStep(99, 5, 0..100))
        assertEquals(0, boundedStep(1, -5, 0..100))
        assertEquals(Int.MAX_VALUE, boundedStep(Int.MAX_VALUE, 10, Int.MIN_VALUE..Int.MAX_VALUE))
        assertEquals(Int.MIN_VALUE, boundedStep(Int.MIN_VALUE, -10, Int.MIN_VALUE..Int.MAX_VALUE))
    }
}
