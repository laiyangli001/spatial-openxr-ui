package com.moonlightxr.nativeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HexMenuThemeTest {
    @Test fun opaqueModeCoversEveryHexStateInBothThemes() {
        listOf(SpatialPalettes.MoonlightOpaque, SpatialPalettes.PicoFallback).forEach { palette ->
            for (active in listOf(false, true))
                for (hovered in listOf(false, true))
                    for (pressed in listOf(false, true)) {
                        val colors = hexFillColors(true, palette === SpatialPalettes.PicoFallback,
                            active, hovered, pressed, palette.panelTop, palette.panelBottom,
                            palette.accentStrong)
                        colors.forEach { assertEquals(1f, it.alpha, 0f) }
                    }
        }
    }

    @Test fun glassModeRetainsSeeThroughIdleHexes() {
        val palette = SpatialPalettes.MoonlightBlue
        val colors = hexFillColors(false, false, false, false, false,
            palette.panelTop, palette.panelBottom, palette.accentStrong)
        assertTrue(colors.all { it.alpha < 1f })
    }
}
