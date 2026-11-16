package org.spatialopenxr.sample

import android.os.Bundle
import android.opengl.GLES20
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.moonlightxr.nativeui.*
import java.util.concurrent.CountDownLatch

/** Small GLES host: one stationary UI Quad and right-hand simple-controller input. */
class XrActivity : ComponentActivity() {
    private var nativeHandle = 0L
    @Volatile private var producer: ComposeSurfaceProducer? = null
    private var router: QuadPointerRouter? = null
    private var texture = 0
    private var hasSource = false
    private var pointer by mutableStateOf<PixelHit?>(null)
    private var statusMessage by mutableStateOf("Initializing OpenXR")
    private val transform = FloatArray(16)
    private val quad = UiQuad("main", QuadPose(Vec3(0f, 0f, -2f), Quat(0f, 0f, 0f, 1f)),
        1.5f, 1f, 1200, 800)
    private external fun startNative(): Long
    private external fun resumeNative(handle: Long, resumed: Boolean)
    private external fun stopNative(handle: Long)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text(statusMessage) }
        nativeHandle = startNative()
    }
    override fun onResume() { super.onResume(); resumeNative(nativeHandle, true) }
    override fun onPause() { resumeNative(nativeHandle, false); router?.cancel(); super.onPause() }
    override fun onDestroy() {
        val handle = nativeHandle
        nativeHandle = 0
        // Joining the render thread on the UI thread would deadlock producer cleanup.
        Thread({ stopNative(handle) }, "SpatialUI-stop").start()
        super.onDestroy()
    }
    /** Called with the sample's EGL context current. */
    fun createProducer() {
        texture = NativeGlesBridge.createExternalTexture()
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        runOnUiThread {
            try {
                if (!isDestroyed) {
                    val next = ComposeSurfaceProducer(this, texture, 1200, 800, content = {
                        Box(Modifier.fillMaxSize()) {
                            Gallery()
                            Canvas(Modifier.fillMaxSize()) {
                                pointer?.let { drawCircle(Color.Cyan, 9f, Offset(it.x, it.y)) }
                            }
                        }
                    })
                    router = QuadPointerRouter(next)
                    producer = next
                }
            } catch (error: Throwable) { failure = error }
            finally { done.countDown() }
        }
        done.await()
        failure?.let { throw IllegalStateException("Compose producer creation failed", it) }
    }
    fun drawProducer(image: Int): Boolean {
        val source = producer ?: return false
        if (source.latchFrameOnGlThread(transform)) hasSource = true
        return hasSource && NativeGlesBridge.copyToAcquiredSwapchain(texture, image, 1200, 800, transform)
    }
    fun inputRay(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, valid: Boolean, pressed: Boolean) {
        runOnUiThread {
            if (!isDestroyed && valid) {
                pointer = QuadInput.intersect(Vec3(ox, oy, oz), Vec3(dx, dy, dz),
                    quad.pose, quad.widthMeters, quad.heightMeters, quad.widthPixels, quad.heightPixels)
                router?.update(pointer, pressed)
            } else { pointer = null; router?.cancel() }
        }
    }
    fun closeProducer() {
        val done = CountDownLatch(1)
        runOnUiThread {
            try { router?.cancel(); router = null; producer?.close(); producer = null }
            finally { done.countDown() }
        }
        done.await()
        if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
        texture = 0
    }
    fun reportStatus(message: String) {
        Log.i("SpatialOpenXR", message)
        runOnUiThread { statusMessage = message }
    }
    companion object { init { System.loadLibrary("spatial_xr_sample") } }
}
