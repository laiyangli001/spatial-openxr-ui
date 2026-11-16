package com.moonlightxr.nativeui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialThemeManagerTest {
    @Test fun picoOpaquePanelsAreOffWhiteAndGlassPanelsKeepRealAlpha() {
        val palette = SpatialPalettes.PicoFallback
        assertEquals(1f, palette.panelTop.alpha, 0f)
        assertEquals(1f, palette.panelBottom.alpha, 0f)
        assertEquals(1f, palette.popupTop.alpha, 0f)
        assertEquals(1f, palette.popupBottom.alpha, 0f)
        assertFalse(palette.panelTop == Color.White)
        assertFalse(palette.panelBottom == Color.White)
        assertEquals(Color.Transparent, palette.canvas)
        assertTrue(palette.text.luminance() < 0.2f)
        val glass = SpatialPalettes.PicoGlass
        assertTrue(glass.panelTop.alpha > 0.2f)
        assertTrue(glass.panelTop.alpha < 0.4f)
        assertTrue(glass.panelBottom.alpha > 0.15f)
        assertTrue(glass.panelBottom.alpha < 0.3f)
        assertTrue(glass.popupTop.alpha < 0.6f)
        assertTrue(glass.popupBottom.alpha < 0.5f)
        assertTrue(glass.text.luminance() > 0.8f)
        val moonlightGlass = SpatialPalettes.MoonlightBlue
        assertTrue(moonlightGlass.panelTop.alpha in 0.2f..0.4f)
        assertTrue(moonlightGlass.panelBottom.alpha in 0.2f..0.4f)
        assertTrue(moonlightGlass.popupTop.alpha < 0.7f)
    }

    @Test fun bothModesSwitchEverySharedToken() {
        try {
            SpatialThemeManager.installPicoSystemPalette(null)
            SpatialThemeManager.select(SpatialThemeMode.MoonlightBlue)
            SpatialThemeManager.selectSurface(SpatialSurfaceMode.Glass)
            assertEquals(SpatialPalettes.MoonlightBlue.panelTop, SpatialTokens.panelTop)
            assertEquals(SpatialPalettes.MoonlightBlue.accent, SpatialTokens.accent)

            SpatialThemeManager.select(SpatialThemeMode.PicoNative)
            SpatialThemeManager.selectSurface(SpatialSurfaceMode.Opaque)
            assertFalse(SpatialThemeManager.isUsingSystemPalette)
            assertEquals(SpatialPalettes.PicoFallback.panelTop, SpatialTokens.panelTop)
            assertEquals(SpatialPalettes.PicoFallback.accent, SpatialTokens.accent)

            SpatialThemeManager.selectSurface(SpatialSurfaceMode.Glass)
            assertEquals(SpatialPalettes.PicoGlass.panelTop, SpatialTokens.panelTop)
            SpatialThemeManager.select(SpatialThemeMode.MoonlightBlue)
            assertEquals(SpatialSurfaceMode.Glass, SpatialThemeManager.surfaceMode)
            SpatialThemeManager.selectSurface(SpatialSurfaceMode.Opaque)
            assertEquals(SpatialPalettes.MoonlightOpaque.panelTop, SpatialTokens.panelTop)
            SpatialThemeManager.select(SpatialThemeMode.PicoNative)
            assertEquals(SpatialSurfaceMode.Glass, SpatialThemeManager.surfaceMode)

            SpatialThemeManager.installPicoSystemPalette(SpatialPalettes.MoonlightBlue)
            assertTrue(SpatialThemeManager.isUsingSystemPalette)
            assertEquals(SpatialPalettes.MoonlightBlue.panelTop, SpatialTokens.panelTop)
        } finally {
            SpatialThemeManager.installPicoSystemPalette(null)
            SpatialThemeManager.select(SpatialThemeMode.MoonlightBlue)
            SpatialThemeManager.selectSurface(SpatialSurfaceMode.Glass)
            SpatialThemeManager.select(SpatialThemeMode.PicoNative)
            SpatialThemeManager.selectSurface(SpatialSurfaceMode.Opaque)
            SpatialThemeManager.select(SpatialThemeMode.MoonlightBlue)
        }
    }
}
