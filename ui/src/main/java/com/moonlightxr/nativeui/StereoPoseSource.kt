package com.moonlightxr.nativeui

/** Updated on the UI thread by OpenXR; Filament reads it on that same thread. */
class StereoPoseSource {
    var head: QuadPose? = null
        private set
    var quad: QuadPose? = null
        private set
    var scale: Float = 1f
        private set
    private var leftEye: QuadPose? = null
    private var rightEye: QuadPose? = null
    fun eyePose(eye: SpatialEye): QuadPose? = if (eye == SpatialEye.Left) leftEye else rightEye
    fun update(headPose: QuadPose, quadPose: QuadPose, presentationScale: Float = 1f,
               left: QuadPose? = null, right: QuadPose? = null) {
        head = headPose
        quad = quadPose
        scale = presentationScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        leftEye = left
        rightEye = right
    }
    fun clear() {
        head = null
        quad = null
        scale = 1f
        leftEye = null
        rightEye = null
    }
}



