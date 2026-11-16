package com.moonlightxr.nativeui

import android.view.MotionEvent
import kotlin.math.roundToInt

interface QuadPointerSink {
    fun update(hit: PixelHit?, triggerDown: Boolean, verticalScroll: Float = 0f)
    fun cancel()
}

interface SpatialPointerEventSink {
    fun pointer(x: Float, y: Float, action: Int)
    fun scroll(x: Float, y: Float, vertical: Float)
}

/** Feed once per focused XR input frame, after locating the controller aim space. */
class QuadPointerRouter(private val producer: SpatialPointerEventSink,
                        private val onScroll: ((Float) -> Unit)? = null,
                        private val scrollOverride: ((PixelHit, Float) -> Boolean)? = null) : QuadPointerSink {
    private var hovering = false
    private var pressed = false
    private var lastX = 0f
    private var lastY = 0f
    private var lastHoverX = Int.MIN_VALUE
    private var lastHoverY = Int.MIN_VALUE

    override fun update(hit: PixelHit?, triggerDown: Boolean, verticalScroll: Float) {
        if (hit == null) {
            if (pressed) producer.pointer(lastX, lastY, MotionEvent.ACTION_CANCEL)
            if (hovering) producer.pointer(lastX, lastY, MotionEvent.ACTION_HOVER_EXIT)
            pressed = false
            hovering = false
            return
        }
        lastX = hit.x
        lastY = hit.y
        val hoverX = stablePointerCoordinate(hit.x).roundToInt()
        val hoverY = stablePointerCoordinate(hit.y).roundToInt()
        if (!hovering) {
            producer.pointer(hit.x, hit.y, MotionEvent.ACTION_HOVER_ENTER)
            lastHoverX = hoverX
            lastHoverY = hoverY
        }
        // Hover and drag are separate phases of one mouse stream. Interleaving a mouse
        // HOVER_MOVE with every held touch MOVE used to restart Compose slider gestures.
        if (!pressed && !triggerDown && (hoverX != lastHoverX || hoverY != lastHoverY)) {
            producer.pointer(hit.x, hit.y, MotionEvent.ACTION_HOVER_MOVE)
            lastHoverX = hoverX
            lastHoverY = hoverY
        }
        hovering = true
        when {
            triggerDown && !pressed -> producer.pointer(hit.x, hit.y, MotionEvent.ACTION_DOWN)
            triggerDown && pressed -> producer.pointer(hit.x, hit.y, MotionEvent.ACTION_MOVE)
            !triggerDown && pressed -> {
                producer.pointer(hit.x, hit.y, MotionEvent.ACTION_UP)
                if (hoverX != lastHoverX || hoverY != lastHoverY) {
                    producer.pointer(hit.x, hit.y, MotionEvent.ACTION_HOVER_MOVE)
                    lastHoverX = hoverX
                    lastHoverY = hoverY
                }
            }
        }
        pressed = triggerDown
        if (verticalScroll != 0f && !triggerDown) {
            if (scrollOverride?.invoke(hit, verticalScroll) == true) return
            if (onScroll != null) onScroll(verticalScroll)
            else producer.scroll(hit.x, hit.y, verticalScroll)
        }
    }

    override fun cancel() = update(null, false)
}
