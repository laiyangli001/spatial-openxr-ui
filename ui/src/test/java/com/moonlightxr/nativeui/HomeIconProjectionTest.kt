package com.moonlightxr.nativeui

import org.junit.Assert.*
import org.junit.Test

class HomeIconProjectionTest {
    private val width = 0.8f
    private val height = 0.45f

    private fun project(f: QuadEyeFrustum, p: Vec3): Pair<Double, Double> {
        val depth = f.eye.z.toDouble() - p.z
        val x = (p.x.toDouble() - f.eye.x) * f.near / depth
        val y = (p.y.toDouble() - f.eye.y) * f.near / depth
        return Pair((x - f.left) / (f.right - f.left), (y - f.bottom) / (f.top - f.bottom))
    }

    @Test fun quadCornersStayAtTextureCornersForOffAxisEyes() {
        for (z in listOf(1f, 2f, 10f)) for (x in listOf(-0.7f, 0f, 0.7f)) {
            val f = checkNotNull(quadEyeFrustum(Vec3(x, 0.3f, z), width, height))
            for (u in 0..1) for (v in 0..1) {
                val uv = project(f, Vec3((u - 0.5f) * width, (v - 0.5f) * height, 0f))
                assertEquals(u.toDouble(), uv.first, 1e-7)
                assertEquals(v.toDouble(), uv.second, 1e-7)
            }
        }
    }

    @Test fun binocularRaysReconstructRigidModelAfterMovingAndScalingQuad() {
        // Rays through the rendered texture must meet at the original model point.
        // Toe-in or a symmetric lens can look stereo yet fail this invariant.
        for (distance in listOf(1f, 2f, 10f)) for (scale in listOf(0.5f, 1f, 2.5f)) {
            for (offset in listOf(-0.6f, 0f, 0.6f)) {
                val left = homeIconCameraPosition(Vec3(offset - 0.035f, 0.25f, distance), scale)
                val right = homeIconCameraPosition(Vec3(offset + 0.035f, 0.25f, distance), scale)
                val lf = checkNotNull(quadEyeFrustum(left, width, height))
                val rf = checkNotNull(quadEyeFrustum(right, width, height))
                for (x in listOf(-0.06f, 0.06f)) for (y in listOf(-0.06f, 0.06f)) {
                    for (z in listOf(-0.05f, 0.05f)) {
                        val point = Vec3(x, y, z)
                        val lu = project(lf, point)
                        val ru = project(rf, point)
                        val dlx = (lu.first - 0.5) * width - left.x
                        val drx = (ru.first - 0.5) * width - right.x
                        val t = (right.x.toDouble() - left.x) / (dlx - drx)
                        assertEquals(x.toDouble(), left.x + t * dlx, 1e-6)
                        assertEquals(y.toDouble(), left.y + t * ((lu.second - 0.5) * height - left.y), 1e-6)
                        assertEquals(z.toDouble(), left.z * (1.0 - t), 1e-6)
                    }
                }
            }
        }
    }

    @Test fun rotatedPanelStillReconstructsTheSameWorldPoint() {
        for (yaw in listOf(-0.6f, 0f, 0.6f)) for (pitch in listOf(-0.3f, 0.3f)) {
            val rotation = Quat(0f, kotlin.math.sin(yaw / 2), 0f, kotlin.math.cos(yaw / 2)) *
                Quat(kotlin.math.sin(pitch / 2), 0f, 0f, kotlin.math.cos(pitch / 2))
            val origin = Vec3(0.4f, -0.2f, -2f)
            val scale = 1.7f
            val eyes = listOf(Vec3(-0.035f, 0f, 0f), Vec3(0.035f, 0f, 0f))
            val localPoint = Vec3(0.04f, -0.03f, 0.05f)
            val rays = eyes.map { eye ->
                val localEye = homeIconCameraPosition(rotation.inverseRotate(eye - origin), scale)
                val uv = project(checkNotNull(quadEyeFrustum(localEye, width, height)), localPoint)
                val onPlane = Vec3(((uv.first - 0.5) * width).toFloat(),
                    ((uv.second - 0.5) * height).toFloat(), 0f)
                origin + rotation.rotate(onPlane * scale) - eye
            }
            val normal = rays[0].cross(rays[1])
            val t = (eyes[1] - eyes[0]).cross(rays[1]).dot(normal) / normal.dot(normal)
            val reconstructed = eyes[0] + rays[0] * t
            val expected = origin + rotation.rotate(localPoint * scale)
            assertEquals(expected.x, reconstructed.x, 0.001f)
            assertEquals(expected.y, reconstructed.y, 0.001f)
            assertEquals(expected.z, reconstructed.z, 0.001f)
        }
    }

    @Test fun planeHasZeroDisparityAndFrontAndBackHaveOppositeDisparities() {
        val left = checkNotNull(quadEyeFrustum(Vec3(-0.035f, 0f, 2f), width, height))
        val right = checkNotNull(quadEyeFrustum(Vec3(0.035f, 0f, 2f), width, height))
        fun disparity(z: Float) = project(left, Vec3(0f, 0f, z)).first - project(right, Vec3(0f, 0f, z)).first
        assertEquals(0.0, disparity(0f), 1e-10)
        assertTrue(disparity(0.05f) > 0)
        assertTrue(disparity(-0.05f) < 0)
    }

    @Test fun invalidOrBehindQuadEyeDoesNotRenderAClampedProjection() {
        for (z in listOf(-1f, 0f, 0.001f, Float.NaN))
            assertNull(quadEyeFrustum(Vec3(0f, 0f, z), width, height))
        assertNull(quadEyeFrustum(Vec3(0f, 0f, 2f), 0f, height))
        assertNull(quadEyeFrustum(Vec3(0f, 0f, 2f), width, height, far = 0.001))
    }

    @Test fun runtimeEyePosesArePreservedAndCleared() {
        val source = StereoPoseSource()
        val head = QuadPose(Vec3(0f, 0f, 0f), Quat(0f, 0f, 0f, 1f))
        val left = head.copy(position = Vec3(-0.035f, 0.001f, 0.002f))
        val right = head.copy(position = Vec3(0.035f, -0.001f, 0.002f))
        source.update(head, head, 2f, left, right)
        assertEquals(left, source.eyePose(SpatialEye.Left))
        assertEquals(right, source.eyePose(SpatialEye.Right))
        source.clear()
        assertNull(source.eyePose(SpatialEye.Left))
        assertNull(source.eyePose(SpatialEye.Right))
    }
}
