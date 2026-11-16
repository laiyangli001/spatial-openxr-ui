package com.moonlightxr.nativeui

internal enum class SpatialScrollScope { Content, InlineChoices, Modal }

/** Viewports use the same source-surface pixels as QuadInput and Compose pointer events. */
internal data class SpatialScrollViewport(
    val panelId: String, val scope: SpatialScrollScope,
    val left: Float, val top: Float, val right: Float, val bottom: Float,
) {
    val area: Float get() = (right - left) * (bottom - top)
    fun contains(hit: PixelHit): Boolean = hit.x >= left && hit.x < right &&
        hit.y >= top && hit.y < bottom
}

/** Direct stick scrolling avoids Android mouse-wheel delivery on offscreen Compose surfaces. */
internal class SpatialScrollRegistry {
    private data class Entry(val viewport: SpatialScrollViewport, val scroll: (Float) -> Unit)
    private val entries = mutableMapOf<Any, Entry>()

    fun update(owner: Any, viewport: SpatialScrollViewport, scroll: (Float) -> Unit) {
        if (!viewport.area.isFinite() || viewport.right <= viewport.left || viewport.bottom <= viewport.top) {
            remove(owner)
            return
        }
        entries[owner] = Entry(viewport, scroll)
    }

    fun remove(owner: Any) { entries.remove(owner) }

    fun scroll(panelId: String, scope: SpatialScrollScope, hit: PixelHit, wheelUnits: Float): Boolean {
        if (!wheelUnits.isFinite() || wheelUnits == 0f) return false
        val target = entries.values.filter {
            it.viewport.panelId == panelId && it.viewport.scope == scope && it.viewport.contains(hit)
        }.minByOrNull { it.viewport.area } ?: return false
        target.scroll(extensionScrollPixels(wheelUnits))
        // A viewport owns the event even at its boundary; never scroll a covered parent.
        return true
    }
}
