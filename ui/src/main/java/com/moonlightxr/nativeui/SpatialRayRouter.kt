package com.moonlightxr.nativeui

/** Prevents a press started on one Quad from leaking to another Quad while the popup appears. */
class SpatialRayRouter(private val main: QuadPointerSink,
                       private val popup: () -> QuadPointerSink?,
                       private val extension: () -> QuadPointerSink? = { null },
                       private val onPointer: (UiQuadHit?, Boolean) -> Unit = { _, _ -> },
                       private val spatial: (String) -> QuadPointerSink? = { null },
                       private val spatialSinks: () -> Collection<QuadPointerSink> = { emptyList() }) {
    private var target: String? = null
    private var previousTriggerDown = false
    private var awaitRelease = false

    fun update(plan: SpatialUiLayerPlan, origin: Vec3, direction: Vec3,
               triggerDown: Boolean, verticalScroll: Float) {
        val hit = plan.hit(origin, direction)
        val next = hit?.quad?.id
        if (target != next) {
            // A held press is owned by its starting surface. Never synthesize DOWN on
            // another surface (or on re-entry after leaving the source) until release.
            if (previousTriggerDown) awaitRelease = true
            sinkFor(target)?.cancel()
        }
        target = next
        if (!triggerDown) awaitRelease = false
        val effectiveDown = triggerDown && !awaitRelease
        previousTriggerDown = triggerDown
        onPointer(hit, effectiveDown)
        val sink = sinkFor(next)
        if (sink != null && hit != null) sink.update(hit.pixel, effectiveDown, verticalScroll)
        else cancelSinks()
    }

    private fun sinkFor(id: String?): QuadPointerSink? = when (id) {
        "main" -> main
        "popup" -> popup()
        "extension" -> extension()
        null -> null
        else -> spatial(id)
    }

    private fun cancelSinks() {
        main.cancel()
        popup()?.cancel()
        extension()?.cancel()
        spatialSinks().forEach { it.cancel() }
    }

    fun cancel() {
        cancelSinks()
        target = null
        // Focus/tracking cancellation must not turn a still-held Trigger into a click.
        awaitRelease = true
        onPointer(null, false)
    }
}
