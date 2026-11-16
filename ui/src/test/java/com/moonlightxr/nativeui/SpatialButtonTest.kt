package com.moonlightxr.nativeui

import org.junit.Assert.assertEquals
import org.junit.Test

class SpatialButtonTest {
    private val colors = SpatialButtonDefaults.colors()

    @Test fun statePriorityIsDisabledThenPressedThenSelectedThenHover() {
        assertEquals(colors.disabledContainer,
            resolveSpatialButtonVisual(colors, false, true, true, true).container)
        assertEquals(colors.pressedContainer,
            resolveSpatialButtonVisual(colors, true, true, true, true).container)
        assertEquals(colors.selectedContainer,
            resolveSpatialButtonVisual(colors, true, true, false, true).container)
        assertEquals(colors.hoveredContainer,
            resolveSpatialButtonVisual(colors, true, true, false, false).container)
        assertEquals(colors.container,
            resolveSpatialButtonVisual(colors, true, false, false, false).container)
    }

    @Test fun disabledTextAndOutlineDoNotKeepTheActiveStyle() {
        val visual = resolveSpatialButtonVisual(colors, false, true, true, true)
        assertEquals(colors.disabledContent, visual.content)
        assertEquals(0.7f, visual.borderWidth.value, 0f)
    }

    @Test fun selectedControlsUseReadableOnAccentText() {
        val selected = resolveSpatialButtonVisual(colors, true, false, false, true)
        assertEquals(SpatialTokens.onAccent, selected.content)
        val pressed = resolveSpatialButtonVisual(colors, true, false, true, false)
        assertEquals(SpatialTokens.onAccent, pressed.content)
        assertEquals(SpatialTokens.onAccent,
            SpatialButtonDefaults.colors(SpatialButtonVariant.Primary).content)
    }

    @Test fun variantsKeepTheirOwnDefaultColors() {
        assertEquals(SpatialTokens.accentStrong,
            SpatialButtonDefaults.colors(SpatialButtonVariant.Primary).container)
        assertEquals(SpatialTokens.field,
            SpatialButtonDefaults.colors(SpatialButtonVariant.Secondary).container)
    }
}
