package com.moonlightxr.nativeui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf

data class SpatialLayerRequest(val quad: UiQuad, val generation: Long, val modal: Boolean, val focusable: Boolean)

/** Native renderer callbacks must return to the UI thread. null means allocation/presentation failed. */
interface SpatialLayerBackend {
    /** A native manipulator may resize the submitted plane without replacing its pixel buffer. */
    val supportsPresentationScaling: Boolean get() = false
    /** Explicit opt-in for a noninteractive leader whose endpoints set its physical width. */
    fun supportsDynamicWidth(request: SpatialLayerRequest): Boolean = false
    fun request(request: SpatialLayerRequest, onPresented: (UiQuad?) -> Unit): Boolean
    fun release(id: String, generation: Long)
}

/**
 * Multi-surface lifetime registry shared by the Compose wrappers and a native layer backend.
 * Admission is not a first-frame acknowledgement. Request generations survive remove/reopen
 * so an old asynchronous acknowledgement cannot revive a destroyed or replaced surface.
 */
class SpatialLayerRegistry(private val backend: SpatialLayerBackend) : SpatialLayerSink {
    private data class Entry(val request: SpatialLayerRequest, val content: @Composable () -> Unit,
                             val state: SpatialLayerPresentation, val presented: UiQuad? = null,
                             val released: Boolean = false)
    private val entries = mutableStateMapOf<String, Entry>()
    private var generation = 0L
    private var closed = false

    override fun presentationState(id: String): SpatialLayerPresentation =
        entries[id]?.state ?: SpatialLayerPresentation.Missing

    fun content(id: String): (@Composable () -> Unit)? = entries[id]?.content
    fun presentedQuad(id: String): UiQuad? = entries[id]?.presented
    fun requestFor(id: String): SpatialLayerRequest? = entries[id]?.request

    override fun upsert(quad: UiQuad, modal: Boolean, focusable: Boolean,
                        content: @Composable () -> Unit): Boolean {
        if (closed) return false
        require(quad.id.isNotBlank())
        require((quad.eyeVisibility == UiEyeVisibility.Both) == (quad.stereoGroupId == null))
        require(quad.stereoGroupId == null || (!modal && !focusable))
        SpatialSurfaceSize(quad.widthMeters, quad.heightMeters, quad.widthPixels, quad.heightPixels)
        val old = entries[quad.id]
        if (old != null && old.state in listOf(SpatialLayerPresentation.Requested, SpatialLayerPresentation.Ready) &&
            old.request.quad == quad && old.request.modal == modal && old.request.focusable == focusable) {
            entries[quad.id] = old.copy(content = content)
            return true
        }
        entries.remove(quad.id)
        if (old != null && !old.released) backend.release(quad.id, old.request.generation)
        val request = SpatialLayerRequest(quad, ++generation, modal, focusable)
        entries[quad.id] = Entry(request, content, SpatialLayerPresentation.Requested)
        val accepted = try { backend.request(request) { presented ->
            val current = entries[quad.id]
            if (!closed && current?.request?.generation == request.generation && !current.released) {
                val orientation = presented?.pose?.orientation
                val lengthSquared = orientation?.let { it.x * it.x + it.y * it.y + it.z * it.z + it.w * it.w }
                val sizeMatches = presented?.let {
                    val widthScale=it.widthMeters/quad.widthMeters
                    val heightScale=it.heightMeters/quad.heightMeters
                    (it.widthMeters==quad.widthMeters && it.heightMeters==quad.heightMeters) ||
                        (backend.supportsDynamicWidth(request) && !request.focusable && !request.modal &&
                            it.widthMeters.isFinite() && it.widthMeters>0f && it.widthMeters<=1f &&
                            it.heightMeters==quad.heightMeters) ||
                        (backend.supportsPresentationScaling && widthScale.isFinite() && heightScale.isFinite() &&
                            widthScale>0f && heightScale>0f &&
                            kotlin.math.abs(widthScale-heightScale)<.001f*maxOf(widthScale,heightScale))
                } ?: false
                val valid = presented != null && presented.id == quad.id &&
                    presented.eyeVisibility == quad.eyeVisibility && presented.stereoGroupId == quad.stereoGroupId &&
                    presented.widthPixels == quad.widthPixels && presented.heightPixels == quad.heightPixels &&
                    sizeMatches &&
                    presented.pose.position.let { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() } &&
                    lengthSquared != null && kotlin.math.abs(lengthSquared - 1f) < 0.01f
                entries[quad.id] = current.copy(state = if (valid) SpatialLayerPresentation.Ready
                    else SpatialLayerPresentation.Failed, presented = if (valid) presented else null,
                    released = !valid)
                if (!valid) backend.release(quad.id, request.generation)
            }
        } } catch (_: RuntimeException) { false }
        if (!accepted && entries[quad.id]?.request?.generation == request.generation) {
            val current = checkNotNull(entries[quad.id])
            entries[quad.id] = current.copy(state = SpatialLayerPresentation.Failed, presented = null, released = true)
            if (!current.released) backend.release(quad.id, request.generation)
        }
        return accepted
    }

    override fun remove(id: String) {
        entries.remove(id)?.let { if (!it.released) backend.release(id, it.request.generation) }
    }

    fun close() {
        if (closed) return
        closed = true
        entries.keys.toList().forEach(::remove)
    }
}
