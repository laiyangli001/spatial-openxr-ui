package com.moonlightxr.nativeui

import androidx.compose.runtime.Composable
import org.junit.Assert.*
import org.junit.Test

class SpatialLayerRegistryTest {
    @Test fun modalWaitsForIndependentQuadBeforeAppearing() {
        assertFalse(drawModalInlineFallback("settings-reset", admissionRejected = false))
        assertFalse(drawModalInlineFallback("settings-reset", admissionRejected = true,
            reservedPopupPending = true))
        assertTrue(drawModalInlineFallback("settings-reset", admissionRejected = true))
        assertTrue(drawModalInlineFallback(null, admissionRejected = false))
    }
    private val pose = QuadPose(Vec3(0f, 0f, -2f), Quat(0f, 0f, 0f, 1f))
    private fun quad(id: String) = UiQuad(id, pose, 0.5f, 0.3f, 500, 300)
    private class Backend : SpatialLayerBackend {
        override var supportsPresentationScaling=false
        var dynamicWidth=false
        override fun supportsDynamicWidth(request:SpatialLayerRequest)=dynamicWidth
        val requests = mutableListOf<SpatialLayerRequest>()
        val callbacks = mutableListOf<(UiQuad?) -> Unit>()
        val releases = mutableListOf<Pair<String, Long>>()
        var accept = true
        override fun request(request: SpatialLayerRequest, onPresented: (UiQuad?) -> Unit): Boolean {
            requests += request
            callbacks += onPresented
            return accept
        }
        override fun release(id: String, generation: Long) { releases += id to generation }
    }
    private val backend = Backend()
    private val registry = SpatialLayerRegistry(backend)

    @Test fun leaderWidthRequiresOptInAndCannotResizeInteractiveRowsOrPixelBuffers() {
        val requested=quad("leader")
        val stretched=requested.copy(widthMeters=.2f)
        registry.upsert(requested,false,false) {}
        backend.callbacks.last()(stretched)
        assertEquals(SpatialLayerPresentation.Failed,registry.presentationState("leader"))
        backend.dynamicWidth=true
        registry.upsert(requested,false,false) {}
        backend.callbacks.last()(stretched)
        assertEquals(stretched,registry.presentedQuad("leader"))
        for((modal,focusable) in listOf(false to true,true to false)) {
            registry.upsert(requested,modal,focusable) {}
            backend.callbacks.last()(stretched)
            assertEquals(SpatialLayerPresentation.Failed,registry.presentationState("leader"))
        }
        for(invalid in listOf(stretched.copy(widthPixels=600),stretched.copy(heightMeters=.2f),
            stretched.copy(widthMeters=Float.NaN),stretched.copy(widthMeters=0f),stretched.copy(widthMeters=1.1f))) {
            registry.upsert(requested,false,false) {}
            backend.callbacks.last()(invalid)
            assertEquals(SpatialLayerPresentation.Failed,registry.presentationState("leader"))
        }
    }

    @Test fun uniformNativePresentationScaleRequiresExplicitBackendSupport() {
        val requested=quad("panel")
        val doubled=requested.copy(widthMeters=1f,heightMeters=.6f)
        registry.upsert(requested,false,true) {}
        backend.callbacks.last()(doubled)
        assertEquals(SpatialLayerPresentation.Failed,registry.presentationState("panel"))
        backend.supportsPresentationScaling=true
        registry.upsert(requested,false,true) {}
        backend.callbacks.last()(doubled)
        assertEquals(SpatialLayerPresentation.Ready,registry.presentationState("panel"))
        assertEquals(doubled,registry.presentedQuad("panel"))
    }

    @Test fun presentationScalingStillRejectsDistortionAndWrongPixelBuffers() {
        backend.supportsPresentationScaling=true
        val requested=quad("panel")
        for(invalid in listOf(requested.copy(widthMeters=1f,heightMeters=.3f),
            requested.copy(widthMeters=1f,heightMeters=.6f,widthPixels=1000),
            requested.copy(widthMeters=Float.NaN),requested.copy(widthMeters=-.5f,heightMeters=-.3f))) {
            registry.upsert(requested,false,true) {}
            backend.callbacks.last()(invalid)
            assertEquals(SpatialLayerPresentation.Failed,registry.presentationState("panel"))
        }
    }

    @Test fun admissionRetainsInlineContentUntilTheNativeFirstFrame() {
        assertTrue(registry.upsert(quad("hint"), false, true) {})
        assertEquals(SpatialLayerPresentation.Requested, registry.presentationState("hint"))
        assertTrue(drawSpatialInlineFallback(true, true, registry.presentationState("hint")))
        assertNull(registry.presentedQuad("hint"))
        val submitted = quad("hint").copy(pose = pose.copy(position = Vec3(0.3f, 0.2f, -1.9f)))
        backend.callbacks[0](submitted)
        assertEquals(submitted, registry.presentedQuad("hint"))
        assertFalse(drawSpatialInlineFallback(true, true, registry.presentationState("hint")))
    }

    @Test fun asyncFailureReturnsToInlineAndReleasesCapacityAndResourcesOnce() {
        val host = SpatialLayerBudgetHost(3, 2, registry)
        assertTrue(host.show(quad("hint"), false, false) {})
        assertFalse(host.show(quad("menu"), true, true) {})
        backend.callbacks[0](null)
        assertTrue(drawSpatialInlineFallback(true, true, host.presentationState("hint")))
        assertEquals(0, host.activeLayerCount)
        assertTrue(host.show(quad("menu"), true, true) {})
        host.hide("hint")
        assertEquals(1, backend.releases.count { it.first == "hint" })
        assertNull(registry.content("hint"))
    }

    @Test fun removedAndReopenedIdsIgnoreAnOlderFirstFrame() {
        registry.upsert(quad("popup"), true, true) {}
        registry.remove("popup")
        registry.upsert(quad("popup"), true, true) {}
        assertNotEquals(backend.requests[0].generation, backend.requests[1].generation)
        backend.callbacks[0](quad("popup"))
        assertEquals(SpatialLayerPresentation.Requested, registry.presentationState("popup"))
        backend.callbacks[1](quad("popup"))
        assertEquals(SpatialLayerPresentation.Ready, registry.presentationState("popup"))
    }

    @Test fun replacingGeometryRetiresTheOldRequestAndItsLateCallbacks() {
        registry.upsert(quad("panel"), false, true) {}
        backend.callbacks[0](quad("panel"))
        val resized = quad("panel").copy(widthPixels = 600, widthMeters = 0.6f)
        registry.upsert(resized, false, true) {}
        backend.callbacks[0](quad("panel"))
        assertEquals(SpatialLayerPresentation.Requested, registry.presentationState("panel"))
        backend.callbacks[1](resized)
        assertEquals(resized, registry.presentedQuad("panel"))
        assertEquals(1, backend.releases.size)
    }

    @Test fun updatingOnlyContentDoesNotAllocateAnotherLayer() {
        val first: @Composable () -> Unit = {}
        val second: @Composable () -> Unit = {}
        registry.upsert(quad("panel"), false, true, first)
        backend.callbacks[0](quad("panel"))
        registry.upsert(quad("panel"), false, true, second)
        assertEquals(1, backend.requests.size)
        assertSame(second, registry.content("panel"))
        assertEquals(SpatialLayerPresentation.Ready, registry.presentationState("panel"))
    }

    @Test fun closeReleasesAllWindowsAndCannotBeRevivedByCallbacks() {
        registry.upsert(quad("one"), false, true) {}
        registry.upsert(quad("two"), false, false) {}
        registry.close(); registry.close()
        backend.callbacks[0](quad("one")); backend.callbacks[1](quad("two"))
        assertEquals(2, backend.releases.size)
        assertEquals(SpatialLayerPresentation.Missing, registry.presentationState("one"))
        assertFalse(registry.upsert(quad("three"), false, true) {})
        assertFalse(drawSpatialInlineFallback(false, false, SpatialLayerPresentation.Missing))
    }

    @Test fun rejectedAllocationAndInvalidFirstFramesNeverHideTheFallback() {
        backend.accept = false
        assertFalse(registry.upsert(quad("popup"), true, true) {})
        assertEquals(SpatialLayerPresentation.Failed, registry.presentationState("popup"))
        backend.callbacks[0](quad("popup"))
        assertEquals(SpatialLayerPresentation.Failed, registry.presentationState("popup"))
        backend.accept = true
        registry.upsert(quad("popup"), true, true) {}
        backend.callbacks[1](quad("wrong-id"))
        assertEquals(SpatialLayerPresentation.Failed, registry.presentationState("popup"))
        assertTrue(drawSpatialInlineFallback(true, true, registry.presentationState("popup")))
    }

    @Test fun independentWindowsAcknowledgeTheirOwnContentAndGeometry() {
        registry.upsert(quad("left"), false, true) {}
        registry.upsert(quad("right"), false, false) {}
        backend.callbacks[1](quad("right"))
        assertEquals(SpatialLayerPresentation.Requested, registry.presentationState("left"))
        assertEquals(SpatialLayerPresentation.Ready, registry.presentationState("right"))
        assertFalse(registry.requestFor("right")!!.focusable)
        backend.callbacks[0](quad("left").copy(pose = pose.copy(position = Vec3(Float.NaN, 0f, -2f))))
        assertEquals(SpatialLayerPresentation.Failed, registry.presentationState("left"))
    }

    @Test fun backendExceptionFallsBackAndItsPartialResourcesAreReleasedOnce() {
        var releases = 0
        val failing = SpatialLayerRegistry(object : SpatialLayerBackend {
            override fun request(request: SpatialLayerRequest, onPresented: (UiQuad?) -> Unit): Boolean {
                throw IllegalStateException("allocation failed")
            }
            override fun release(id: String, generation: Long) { releases++ }
        })
        assertFalse(failing.upsert(quad("partial"), true, true) {})
        assertEquals(SpatialLayerPresentation.Failed, failing.presentationState("partial"))
        assertTrue(drawSpatialInlineFallback(true, false, failing.presentationState("partial")))
        failing.remove("partial")
        assertEquals(1, releases)
        assertNull(failing.content("partial"))
    }
}
