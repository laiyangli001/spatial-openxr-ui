package com.moonlightxr.nativeui

import org.junit.Assert.*
import org.junit.Test

class SpatialStepVisualTest {
    @Test fun disabledAlwaysWinsOverOldHoverAndPress() {
        val disabled = resolveSpatialStepVisual(false, false, false)
        assertEquals(disabled, resolveSpatialStepVisual(false, true, true))
        assertEquals(SpatialTokens.disabled, disabled.content)
    }

    @Test fun heldStepsUseTheAccentAndReadableOnAccentText() {
        val pressed = resolveSpatialStepVisual(true, true, true)
        assertEquals(SpatialTokens.accentStrong, pressed.fill)
        assertEquals(SpatialTokens.onAccent, pressed.content)
        assertEquals(pressed, resolveSpatialStepVisual(true, false, true))
    }

    @Test fun hoverIsVisibleButDistinctFromAPress() {
        val idle = resolveSpatialStepVisual(true, false, false)
        val hover = resolveSpatialStepVisual(true, true, false)
        val press = resolveSpatialStepVisual(true, true, true)
        assertNotEquals(idle.fill, hover.fill)
        assertNotEquals(press.fill, hover.fill)
        assertTrue(hover.stroke > idle.stroke)
    }
}
