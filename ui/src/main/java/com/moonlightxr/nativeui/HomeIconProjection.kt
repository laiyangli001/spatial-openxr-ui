package com.moonlightxr.nativeui

/** Scene distances are meters in the unscaled Quad's local frame. */
internal fun homeIconCameraPosition(worldOffset: Vec3, presentationScale: Float): Vec3 {
    val scale = presentationScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    return worldOffset * (1f / scale)
}

internal data class QuadEyeFrustum(val eye: Vec3, val left: Double, val right: Double,
                                   val bottom: Double, val top: Double,
                                   val near: Double, val far: Double)

/** Parallel camera axes, looking through the actual Quad rectangle at local z=0. */
internal fun quadEyeFrustum(eye: Vec3, width: Float, height: Float,
                            near: Double = 0.005, far: Double = 100.0): QuadEyeFrustum? {
    if (!eye.x.isFinite() || !eye.y.isFinite() || !eye.z.isFinite() ||
        !width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f ||
        !near.isFinite() || !far.isFinite() || near <= 0.0 || far <= near || eye.z <= near) return null
    val factor = near / eye.z
    return QuadEyeFrustum(eye, (-width / 2.0 - eye.x) * factor,
        (width / 2.0 - eye.x) * factor, (-height / 2.0 - eye.y) * factor,
        (height / 2.0 - eye.y) * factor, near, far)
}
