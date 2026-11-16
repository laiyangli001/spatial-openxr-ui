package com.moonlightxr.nativeui

import org.junit.Assert.*
import org.junit.Test

class SpatialScrollRegistryTest {
    private val registry = SpatialScrollRegistry()
    private val hit = PixelHit(60f, 80f, 2f)
    private fun viewport(panel: String = "main", scope: SpatialScrollScope = SpatialScrollScope.Content,
                         left: Float = 20f, top: Float = 30f, right: Float = 200f, bottom: Float = 300f) =
        SpatialScrollViewport(panel, scope, left, top, right, bottom)

    @Test fun routesOnlyInsideTheHitSurfacesViewport() {
        var delta = 0f
        registry.update("main", viewport()) { delta += it }
        assertFalse(registry.scroll("extension", SpatialScrollScope.Content, hit, -0.15f))
        assertFalse(registry.scroll("main", SpatialScrollScope.Content, hit.copy(y = 20f), -0.15f))
        assertTrue(registry.scroll("main", SpatialScrollScope.Content, hit, -0.15f))
        assertEquals(3f, delta, 0.0001f)
    }

    @Test fun nestedViewportOwnsTheEventEvenWhenItCannotScrollFurther() {
        var parentCalls = 0
        var childCalls = 0
        registry.update("parent", viewport()) { parentCalls++ }
        registry.update("child", viewport(right = 100f, bottom = 120f)) { childCalls++ }
        assertTrue(registry.scroll("main", SpatialScrollScope.Content, hit, -0.15f))
        assertEquals(0, parentCalls)
        assertEquals(1, childCalls)
    }

    @Test fun disposedViewportAndInvalidInputDoNotReceiveScroll() {
        var calls = 0
        registry.update("owner", viewport()) { calls++ }
        assertFalse(registry.scroll("main", SpatialScrollScope.Content, hit, Float.NaN))
        registry.remove("owner")
        assertFalse(registry.scroll("main", SpatialScrollScope.Content, hit, -0.15f))
        registry.update("owner", viewport(right = 0f)) { calls++ }
        assertFalse(registry.scroll("main", SpatialScrollScope.Content, hit, -0.15f))
        assertEquals(0, calls)
    }

    @Test fun inlineChoicesBlockTheParentAndScrollOnlyUnderTheList() {
        val state = MenuUiState()
        var parentCalls = 0
        var choiceCalls = 0
        state.scrollRegistry.update("parent", viewport()) { parentCalls++ }
        state.scrollRegistry.update("choices", viewport(scope = SpatialScrollScope.InlineChoices,
            right = 100f, bottom = 120f)) { choiceCalls++ }
        state.showInlineChoices("背景", listOf("Black", "HDR"), "Black",
            ParameterHelpAnchor("main", 0.5f, 0.5f)) {}
        assertTrue(state.scrollSurface("main", hit, -0.15f))
        assertTrue(state.scrollSurface("main", hit.copy(y = 180f), -0.15f))
        assertEquals(1, choiceCalls)
        assertEquals(0, parentCalls)
        state.dismissInlineChoices()
        assertTrue(state.scrollSurface("main", hit.copy(y = 180f), -0.15f))
        assertEquals(1, parentCalls)
    }

    @Test fun inlineModalFallbackCannotScrollTheMenuBehindIt() {
        val state = MenuUiState()
        var parentCalls = 0
        var modalCalls = 0
        state.scrollRegistry.update("parent", viewport()) { parentCalls++ }
        state.scrollRegistry.update("modal", viewport(scope = SpatialScrollScope.Modal,
            right = 100f, bottom = 120f)) { modalCalls++ }
        state.openDialog()
        assertTrue(state.scrollSurface("main", hit, -0.15f))
        assertTrue(state.scrollSurface("main", hit.copy(y = 180f), -0.15f))
        assertEquals(1, modalCalls)
        assertEquals(0, parentCalls)
    }
}
