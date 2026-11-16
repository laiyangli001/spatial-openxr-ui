package com.moonlightxr.nativeui

import org.junit.Assert.*
import org.junit.Test

class SpatialRayRouterTest {
    private val mainQuad = UiQuad("main", QuadPose(Vec3(0f, 0f, -2f), Quat(0f, 0f, 0f, 1f)),
        2f, 1.125f, 1600, 900)
    private val extensionQuad = mainQuad.copy(id = "extension", pose = mainQuad.pose.copy(position = Vec3(2f, 0f, -2f)))
    private val popupQuad = mainQuad.copy(id = "popup", pose = mainQuad.pose.copy(position = Vec3(0f, 0f, -1.8f)))
    private val budget = LayerBudget(3, 0, 0)
    private val normal = SpatialUiLayerPlan(mainQuad, null, false, budget, extensionQuad)
    private val modal = SpatialUiLayerPlan(mainQuad, popupQuad, true, budget, extensionQuad)

    private class Sink : QuadPointerSink {
        val events = mutableListOf<Pair<PixelHit?, Boolean>>()
        var cancels = 0
        override fun update(hit: PixelHit?, triggerDown: Boolean, verticalScroll: Float) { events += hit to triggerDown }
        override fun cancel() { cancels++ }
    }
    private val main = Sink()
    private val popup = Sink()
    private val extension = Sink()
    private var cursor: UiQuadHit? = null
    private var cursorDown = false
    private val router = SpatialRayRouter(main, { popup }, { extension }, { hit, down -> cursor = hit; cursorDown = down })
    private fun send(x: Float, down: Boolean, plan: SpatialUiLayerPlan = normal) =
        router.update(plan, Vec3(x, 0f, 0f), Vec3(0f, 0f, -1f), down, 0f)

    @Test fun ordinaryPressDragAndReleaseStayOnTheSource() {
        send(0f, false); send(0f, true); send(0.2f, true); send(0.2f, false)
        assertEquals(listOf(false, true, true, false), main.events.map { it.second })
        assertEquals(960f, main.events.last().first!!.x, 0.01f)
        assertEquals(main.events.last().first, cursor?.pixel)
        assertFalse(cursorDown)
    }

    @Test fun heldPressCannotStartANewGestureOnAnotherQuad() {
        send(0f, false); send(0f, true); send(2f, true); send(2f, false)
        assertEquals(1, main.cancels)
        assertEquals(listOf(false, false), extension.events.map { it.second })
        assertEquals("extension", cursor?.quad?.id)
        send(2f, true); send(2f, false)
        assertEquals(listOf(false, false, true, false), extension.events.map { it.second })
    }

    @Test fun modalAppearingWhileHeldDoesNotReceiveAPress() {
        send(0f, false); send(0f, true); send(0f, true, modal)
        assertEquals(listOf(false), popup.events.map { it.second })
        assertFalse(cursorDown)
        send(0f, false, modal); send(0f, true, modal)
        assertTrue(popup.events.last().second)
        assertEquals("popup", cursor?.quad?.id)
    }

    @Test fun leavingAndReenteringWhileHeldCancelsWithoutRestartingDrag() {
        send(0f, false); send(0f, true); send(4f, true)
        assertNull(cursor)
        send(0f, true)
        assertFalse(main.events.last().second)
        send(0f, false); send(0f, true)
        assertTrue(main.events.last().second)
    }

    @Test fun aPressStartedInEmptySpaceMustBeReleasedBeforeEnteringAControl() {
        send(4f, true); send(0f, true)
        assertFalse(main.events.last().second)
        send(0f, false); send(0f, true)
        assertTrue(main.events.last().second)
    }

    @Test fun trackingCancellationClearsCursorAndBlocksHeldTriggerOnResume() {
        send(0f, false); send(0f, true); router.cancel()
        assertNull(cursor)
        send(0f, true)
        assertFalse(main.events.last().second)
        send(0f, false); send(0f, true)
        assertTrue(main.events.last().second)
    }

    @Test fun nonModalHelpDoesNotInterruptSourceInput() {
        val help = modal.copy(popupModal = false)
        send(2f, false); send(2f, true); send(2f, true, help)
        assertTrue(extension.events.last().second)
        assertTrue(popup.events.isEmpty())
        assertEquals("extension", cursor?.quad?.id)
    }

    @Test fun submittedSpatialQuadUsesItsOwnPixelPlaneAndPointerSink() {
        val floating = mainQuad.copy(id = "floating", pose = mainQuad.pose.copy(
            position = Vec3(0f, 0f, -1.7f)), widthMeters = 0.5f,
            heightMeters = 0.3f, widthPixels = 500, heightPixels = 300)
        val sink = Sink()
        val routed = SpatialRayRouter(main, { popup }, { extension },
            { hit, down -> cursor = hit; cursorDown = down },
            spatial = { if (it == "floating") sink else null }, spatialSinks = { listOf(sink) })
        val plan = normal.copy(spatial = listOf(SpatialInteractiveQuad(floating, false, true)))
        routed.update(plan, Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f), true, 0f)
        assertEquals("floating", cursor?.quad?.id)
        assertEquals(250f, sink.events.single().first!!.x, 0.01f)
        assertEquals(150f, sink.events.single().first!!.y, 0.01f)
        assertTrue(sink.events.single().second)
        assertTrue(main.events.isEmpty())
        routed.cancel()
        assertEquals(1, sink.cancels)
    }

    @Test fun aSpatialModalBlocksUnderlyingQuadAndUnsubmittedSurfacesAreNotTargets() {
        val floating = mainQuad.copy(id = "floating", pose = mainQuad.pose.copy(
            position = Vec3(0f, 0f, -1.7f)), widthMeters = 0.5f,
            heightMeters = 0.3f)
        val visible = normal.copy(spatial = listOf(SpatialInteractiveQuad(floating, true, true)))
        assertNull(visible.hit(Vec3(0.8f, 0f, 0f), Vec3(0f, 0f, -1f)))
        assertEquals("floating", visible.hit(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f))?.quad?.id)
        assertEquals("main", normal.hit(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f))?.quad?.id)
        assertEquals("main", normal.copy(spatial = listOf(
            SpatialInteractiveQuad(floating, false, false))).hit(
            Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f))?.quad?.id)
        val behind = floating.copy(pose = floating.pose.copy(position = Vec3(0f, 0f, -2.2f)))
        assertEquals("main", normal.copy(spatial = listOf(
            SpatialInteractiveQuad(behind, false, true))).hit(
            Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f))?.quad?.id)
    }
}
