package com.moonlightxr.nativeui

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.view.Choreographer
import android.view.TextureView
import android.widget.FrameLayout
import com.google.android.filament.View as FilamentView
import com.google.android.filament.Camera
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import com.google.android.filament.utils.Float3
import java.nio.ByteBuffer
import kotlin.math.sin

/**
 * Scheme B: Filament renders a GLB into a transparent TextureView, which is
 * then composited inside the existing Compose UI texture / OpenXR Quad. The
 * foreground Home text Quad owns the input target.
 *
 * The 2D label stays in Compose; only the icon uses Filament.
 */
internal class FilamentHomeIcon(context: Context, private val assetPath: String,
                                private val stereoEye: SpatialEye? = null,
                                private val quadWidth: Float = 0.22f,
                                private val quadHeight: Float = 0.22f) : FrameLayout(context) {
    companion object {
        private const val TAG = "MoonlightFilamentIcon"
    }

    private val textureView = TextureView(context).apply { isOpaque = false }
    private var viewer: ModelViewer? = null
    private var frameCallback: Choreographer.FrameCallback? = null
    private var framesRendered = 0
    private var framesAttempted = 0
    private var lastRenderNanos = 0L
    private var firstRenderNanos = 0L
    private var stereoHeadPose: QuadPose? = null
    private var stereoQuadPose: QuadPose? = null
    internal var stereoPoseSource: StereoPoseSource? = null
    var onFirstFrameRendered: (() -> Unit)? = null

    fun updateStereoPose(head: QuadPose?, quad: QuadPose?) {
        stereoHeadPose = head
        stereoQuadPose = quad
    }

    init {
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewer != null) return
        try {
            // The utility loader also loads gltfio JNI; Filament.init() alone
            // leaves UbershaderProvider unresolved on Android.
            Utils.init()
            val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }
            viewer = ModelViewer(textureView, uiHelper = helper, manipulator = null).also { model ->
                model.view.blendMode = FilamentView.BlendMode.TRANSLUCENT
                model.renderer.clearOptions = model.renderer.clearOptions.apply { clear = true }
                model.loadModelGlb(ByteBuffer.wrap(readAsset(context.assets, assetPath)))
                if (stereoEye == null) {
                    model.transformToUnitCube(Float3(0f, 0f, -2.3f))
                    model.cameraFocalLength = if (assetPath.endsWith("home_streaming_pony.glb")) 35f else 28f
                    model.camera.lookAt(0.0, 0.0, 0.0, 0.0, 0.0, -2.3, 0.0, 1.0, 0.0)
                } else {
                    model.transformToUnitCube(Float3(0f, 0f, 0f))
                    val tm = model.engine.transformManager
                    val root = tm.getInstance(checkNotNull(model.asset).root)
                    val transform = tm.getTransform(root, null as FloatArray?)
                    val extent = minOf(quadWidth, quadHeight)
                    // The unit-cube fit spans 2 units; convert its whole transform to meters.
                    for (column in 0..3) for (row in 0..2)
                        transform[column * 4 + row] *= extent * 0.72f / 2f
                    transform[14] -= extent * 0.08f
                    tm.setTransform(root, transform)
                    model.autoPlayAnimations = false
                    val refresh = display?.refreshRate?.takeIf { it.isFinite() && it > 0f } ?: 60f
                    model.framePacer.configure(refresh, (1_000_000_000.0 / refresh).toLong())
                }
                model.engine.lightManager.setDirection(
                    model.engine.lightManager.getInstance(model.light), -0.25f, -0.25f, -0.93f)
            }
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    val model = viewer ?: return
                    if (stereoEye == null && frameTimeNanos - lastRenderNanos < 33_000_000L) {
                        Choreographer.getInstance().postFrameCallback(this)
                        return
                    }
                    lastRenderNanos = frameTimeNanos
                    try {
                        if (firstRenderNanos == 0L) firstRenderNanos = frameTimeNanos
                        var cameraReady = stereoEye == null
                        if (stereoEye == null) {
                            val seconds = (frameTimeNanos - firstRenderNanos) * 1e-9
                            val eyeX = 0.62 * sin(seconds * 0.85)
                            model.camera.lookAt(eyeX, 0.18, 0.0,
                                0.0, 0.0, -2.3, 0.0, 1.0, 0.0)
                        } else {
                            val head = stereoPoseSource?.head ?: stereoHeadPose
                            val quad = stereoPoseSource?.quad ?: stereoQuadPose
                            val eyePosition = stereoPoseSource?.eyePose(stereoEye)?.position
                            if (head != null && quad != null && eyePosition != null) {
                                val worldCamera = quad.orientation.inverseRotate(
                                    eyePosition - quad.position)
                                val scale = stereoPoseSource?.scale ?: 1f
                                val camera = homeIconCameraPosition(worldCamera, scale)
                                val frustum = quadEyeFrustum(camera, quadWidth, quadHeight)
                                if (frustum != null) {
                                    model.camera.setProjection(Camera.Projection.PERSPECTIVE,
                                        frustum.left, frustum.right, frustum.bottom, frustum.top,
                                        frustum.near, frustum.far)
                                    model.camera.lookAt(camera.x.toDouble(), camera.y.toDouble(),
                                        camera.z.toDouble(), camera.x.toDouble(), camera.y.toDouble(),
                                        camera.z.toDouble() - 1.0, 0.0, 1.0, 0.0)
                                    cameraReady = true
                                }
                                if (framesAttempted == 90) Log.i(TAG,
                                    "stereo eye=$stereoEye camera=$camera scale=$scale quad=${quad.position}")
                            }
                        }
                        framesAttempted++
                        if (cameraReady && model.render(frameTimeNanos)) {
                            framesRendered++
                            if (framesRendered == 1) onFirstFrameRendered?.invoke()
                        }
                        if (framesAttempted == 90) Log.i(TAG,
                            "probe attempted=$framesAttempted rendered=$framesRendered size=${textureView.width}x${textureView.height}")
                    } catch (error: RuntimeException) {
                        Log.e(TAG, "Filament probe render failed", error)
                        return
                    }
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
            frameCallback = callback
            Choreographer.getInstance().postFrameCallback(callback)
            Log.i(TAG, "probe started asset=$assetPath")
        } catch (error: Throwable) {
            Log.e(TAG, "Filament probe initialization failed", error)
        }
    }

    override fun onDetachedFromWindow() {
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
        // ModelViewer observes TextureView detachment and destroys its own engine.
        super.onDetachedFromWindow()
        viewer = null
        Log.i(TAG, "probe stopped attempted=$framesAttempted rendered=$framesRendered")
    }

    private fun readAsset(assets: AssetManager, name: String): ByteArray =
        assets.open(name).use { it.readBytes() }
}
