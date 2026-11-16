package com.moonlightxr.nativeui

/** XR render frames arrive faster than a busy Compose UI thread can consume hover events. */
internal data class PointerFrame(
    val values: FloatArray,
    val triggerDown: Boolean,
    val verticalScroll: Float,
)

internal sealed interface PointerInputEvent {
    data class Frame(val sample: PointerFrame) : PointerInputEvent
    data object Cancel : PointerInputEvent
}

/**
 * Keeps Trigger edges and scroll samples in order while replacing queued hover or held-motion
 * samples with the latest position.
 * One UI runnable drains a bounded batch, so a short click cannot sit behind hundreds of stale
 * no-button ray positions. All methods may be called from either the XR or Android UI thread.
 */
internal class PointerFrameMailbox {
    private data class QueuedFrame(val event: PointerInputEvent, val triggerEdge: Boolean)
    private val pending = ArrayDeque<QueuedFrame>()
    private var scheduled = false
    private var lastTriggerDown = false
    private var lastWasCancel = false

    @Synchronized
    fun offer(frame: PointerFrame): Boolean {
        val triggerEdge = frame.triggerDown != lastTriggerDown
        lastTriggerDown = frame.triggerDown
        lastWasCancel = false
        val last = pending.lastOrNull()
        if (!triggerEdge && frame.verticalScroll == 0f &&
            last != null && !last.triggerEdge &&
            (last.event as? PointerInputEvent.Frame)?.sample?.let {
                it.triggerDown == frame.triggerDown && it.verticalScroll == 0f
            } == true) {
            pending.removeLast()
        }
        pending.addLast(QueuedFrame(PointerInputEvent.Frame(frame), triggerEdge))
        if (scheduled) return false
        scheduled = true
        return true
    }

    @Synchronized
    fun offerCancel(): Boolean {
        if (lastWasCancel) return false
        pending.clear()
        pending.addLast(QueuedFrame(PointerInputEvent.Cancel, false))
        lastTriggerDown = false
        lastWasCancel = true
        if (scheduled) return false
        scheduled = true
        return true
    }

    @Synchronized
    fun takeBatch(limit: Int): List<PointerInputEvent> {
        require(limit > 0)
        return buildList {
            repeat(minOf(limit, pending.size)) { add(pending.removeFirst().event) }
        }
    }

    /** Returns true if another UI runnable must be posted. */
    @Synchronized
    fun finishBatch(): Boolean {
        if (pending.isNotEmpty()) return true
        scheduled = false
        return false
    }

    @Synchronized
    fun clear() {
        pending.clear()
        lastTriggerDown = false
        lastWasCancel = false
    }
}
