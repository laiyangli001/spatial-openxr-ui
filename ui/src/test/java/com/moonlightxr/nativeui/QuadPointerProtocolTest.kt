package com.moonlightxr.nativeui

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test

class QuadPointerProtocolTest {
    private class Events : SpatialPointerEventSink {
        val actions = mutableListOf<Int>()
        var scrolls = 0
        override fun pointer(x: Float, y: Float, action: Int) { actions += action }
        override fun scroll(x: Float, y: Float, vertical: Float) { scrolls++ }
    }
    private val events = Events()
    private val router = QuadPointerRouter(events)
    private val hit = PixelHit(150f, 200f, 1f)

    @Test fun heldGestureContainsOnlyDownMovesAndUpNotHoverMoves() {
        router.update(hit, false)
        router.update(hit, true)
        repeat(5) { router.update(hit.copy(x = hit.x + it), true) }
        router.update(hit, false)
        assertEquals(listOf(MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_DOWN) +
            List(5) { MotionEvent.ACTION_MOVE } +
            listOf(MotionEvent.ACTION_UP), events.actions)
    }

    @Test fun cancellationTerminatesTheDragBeforeExitingHover() {
        router.update(hit, false); router.update(hit, true); router.cancel()
        assertEquals(listOf(MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_HOVER_EXIT), events.actions.takeLast(2))
    }

    @Test fun scrollCannotCompeteWithAnActiveDrag() {
        router.update(hit, false, -0.15f)
        router.update(hit, true, -0.15f)
        router.update(hit, true, -0.15f)
        assertEquals(1, events.scrolls)
        router.update(hit, false, -0.15f)
        assertEquals(2, events.scrolls)
    }

    @Test fun heldStepDoesNotCreateAnotherDownOnEveryFrame() {
        router.update(hit, true)
        repeat(90) { router.update(hit, true) }
        router.update(hit, false)
        assertEquals(1, events.actions.count { it == MotionEvent.ACTION_DOWN })
        assertEquals(90, events.actions.count { it == MotionEvent.ACTION_MOVE })
        assertEquals(1, events.actions.count { it == MotionEvent.ACTION_UP })
    }

    @Test fun directScrollReceivesTheHitAndSuppressesTheAndroidWheelFallback() {
        var received: PixelHit? = null
        var units = 0f
        val directRouter = QuadPointerRouter(events, scrollOverride = { point, axis ->
            received = point
            units = axis
            true
        })
        directRouter.update(hit, false, -0.15f)
        assertEquals(hit, received)
        assertEquals(-0.15f, units, 0f)
        assertEquals(0, events.scrolls)
    }

    @Test fun subPixelHoverJitterDoesNotRepaintCompose() {
        router.update(hit, false)
        repeat(30) { frame ->
            router.update(hit.copy(x = hit.x + (frame % 2) * 0.4f), false)
        }
        assertEquals(listOf(MotionEvent.ACTION_HOVER_ENTER), events.actions)
    }

    @Test fun stableHoverStepStillTracksRealPanelMotion() {
        router.update(hit, false)
        router.update(hit.copy(x = hit.x + 2f), false)
        assertEquals(1, events.actions.count { it == MotionEvent.ACTION_HOVER_MOVE })
    }
}
