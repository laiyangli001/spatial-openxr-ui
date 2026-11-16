package com.moonlightxr.nativeui

import kotlin.math.abs

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(other: Vec3) = x * other.x + y * other.y + z * other.z
    fun cross(other: Vec3) = Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)
}

data class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {
    operator fun times(other: Quat): Quat = Quat(
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w,
        w * other.w - x * other.x - y * other.y - z * other.z,
    )
    fun conjugate(): Quat = Quat(-x, -y, -z, w)

    fun rotate(v: Vec3): Vec3 {
        val qv = Vec3(x, y, z)
        val t = qv.cross(v) * 2f
        return v + t * w + qv.cross(t)
    }

    fun inverseRotate(v: Vec3): Vec3 {
        // Unit OpenXR quaternion: q^-1 * v * q. Validate normalization at the native boundary.
        val qv = Vec3(-x, -y, -z)
        val t = qv.cross(v) * 2f
        return v + t * w + qv.cross(t)
    }
}

data class QuadPose(val position: Vec3, val orientation: Quat)
data class PixelHit(val x: Float, val y: Float, val distanceMeters: Float)

/** OpenXR Quad local +Z is the visible front, +Y is up; aim ray travels toward -Z. */
object QuadInput {
    fun intersect(
        rayOrigin: Vec3, rayDirection: Vec3, pose: QuadPose,
        widthMeters: Float, heightMeters: Float, widthPixels: Int, heightPixels: Int,
    ): PixelHit? {
        if (widthMeters <= 0f || heightMeters <= 0f || widthPixels <= 0 || heightPixels <= 0) return null
        val origin = pose.orientation.inverseRotate(rayOrigin - pose.position)
        val direction = pose.orientation.inverseRotate(rayDirection)
        if (direction.z >= -1e-6f) return null
        val t = -origin.z / direction.z
        if (t < 0f || !t.isFinite()) return null
        val x = origin.x + direction.x * t
        val y = origin.y + direction.y * t
        if (abs(x) > widthMeters / 2f || abs(y) > heightMeters / 2f) return null
        return PixelHit(
            (x / widthMeters + 0.5f) * widthPixels,
            (0.5f - y / heightMeters) * heightPixels,
            t,
        )
    }
}

/** Max layer count is runtime data, not a PICO model constant. */
data class LayerBudget(val maxLayerCount: Int, val projectionAndVideoLayers: Int, val backgroundLayers: Int) {
    val availableUiLayerCount: Int get() =
        (maxLayerCount.toLong() - projectionAndVideoLayers - backgroundLayers)
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    fun mayUseUiLayers(requestedUiLayers: Int): Boolean =
        maxLayerCount > 0 && requestedUiLayers >= 0 &&
            projectionAndVideoLayers >= 0 && backgroundLayers >= 0 &&
            projectionAndVideoLayers.toLong() + backgroundLayers + requestedUiLayers <= maxLayerCount

    fun useSeparatePopup(): Boolean = mayUseUiLayers(2)
}

/** Worst-case native footprint: one hand Projection and two eye-only icon Quads.
 * Main/extension/popup are UI layers and are reserved separately by the floating host.
 * The host only creates the scene at >=2 layers and stereo icons at >=4 layers.
 */
internal fun nativeMenuLayerBudget(maxLayerCount: Int, qaBackdropRequested: Boolean): LayerBudget =
    LayerBudget(maxLayerCount, projectionAndVideoLayers =
        (if(maxLayerCount>=2) 1 else 0) + (if(maxLayerCount>=4) 2 else 0),
        backgroundLayers = if (qaBackdropRequested && maxLayerCount >= 2) 1 else 0)
