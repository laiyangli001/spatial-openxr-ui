package com.moonlightxr.nativeui

import androidx.compose.runtime.Composable

/** Adapter boundary: production code later supplies Compose surfaces and OpenXR Quad allocation. */
interface SpatialLayerSink {
    fun upsert(quad: UiQuad, modal: Boolean, focusable: Boolean,
               content: @Composable () -> Unit): Boolean
    fun presentationState(id: String): SpatialLayerPresentation
    fun remove(id: String)
}

/** Counts all non-UI composition layers before admitting an independent popup Quad. */
class SpatialLayerBudgetHost(
    maxLayerCount: Int,
    reservedLayerCount: Int,
    private val sink: SpatialLayerSink,
) : SpatialFloatingHost {
    private val capacity: Int
    private val activeIds = LinkedHashSet<String>()

    init {
        require(maxLayerCount >= 0)
        require(reservedLayerCount in 0..maxLayerCount)
        capacity = maxLayerCount - reservedLayerCount
    }

    private fun retireFailedRequests() {
        activeIds.removeAll { sink.presentationState(it) in listOf(
            SpatialLayerPresentation.Missing, SpatialLayerPresentation.Failed) }
    }

    val activeLayerCount: Int get() = synchronized(this) {
        retireFailedRequests()
        activeIds.size
    }

    override fun presentationState(id: String): SpatialLayerPresentation = sink.presentationState(id)

    override fun show(quad: UiQuad, modal: Boolean, focusable: Boolean,
                      content: @Composable () -> Unit): Boolean = synchronized(this) {
        retireFailedRequests()
        if (quad.id !in activeIds && activeIds.size >= capacity) return false
        if (!sink.upsert(quad, modal, focusable, content)) return false
        activeIds.add(quad.id)
        true
    }

    override fun hide(id: String) = synchronized(this) {
        // Retired async failures still need their logical content/cache cleaned up.
        activeIds.remove(id)
        sink.remove(id)
    }

    override fun showStereo(left: UiQuad, right: UiQuad,
                            content: @Composable (SpatialEye) -> Unit): Boolean = synchronized(this) {
        requireStereoPair(left, right)
        retireFailedRequests()
        val ids = setOf(left.id, right.id)
        if (activeIds.size + ids.count { it !in activeIds } > capacity) return false
        if (!sink.upsert(left, false, false) { content(SpatialEye.Left) } ||
            !sink.upsert(right, false, false) { content(SpatialEye.Right) }) {
            ids.forEach { activeIds.remove(it); sink.remove(it) }
            return false
        }
        activeIds.addAll(ids)
        true
    }
}
