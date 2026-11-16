package com.moonlightxr.nativeui

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.SurfaceTexture
import android.graphics.PorterDuff
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Surface
import android.view.Choreographer
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Compose render producer for a native GLES OpenXR host. This class does not submit OpenXR frames.
 * Create it after an external OES texture name exists; call [latchFrameOnGlThread] only with the
 * owning GLES context current. The native consumer still must blit OES -> acquired XR swapchain.
 */
class ComposeSurfaceProducer(
    private val activity: ComponentActivity,
    externalOesTexture: Int,
    val width: Int,
    val height: Int,
    private val manualSurfaceRendering: Boolean = true,
    private val outputSurface: Surface? = null,
    private val content: @Composable () -> Unit,
) : SpatialPointerEventSink {
    companion object {
        // 1.5 physical pixels per dp: the 894 dp main layout and 360 dp popup both fit
        // their independent 1600x900 and 800x500 XR textures without clipping.
        private const val UI_DENSITY_DPI = 240
    }
    private val main = Handler(Looper.getMainLooper())
    private val hasFrame = AtomicBoolean(false)
    private val needsManualDraw = AtomicBoolean(true)
    private var latchedFrames = 0
    private var availableFrames = 0
    private val texture = if (outputSurface == null) SurfaceTexture(externalOesTexture) else null
    private val surface: Surface
    private val displaySink: ImageReader?
    private val virtualDisplay: VirtualDisplay
    private val presentation: MenuPresentation
    private var downTime = 0L
    private var manualFrames = 0
    private var closed = false
    private var frameCallback: Choreographer.FrameCallback? = null
    // Observe UI traversal damage directly. The PICO virtual compositor may stop
    // producing ImageReader buffers even while Compose's view content changes.
    private val sourcePreDraw = android.view.ViewTreeObserver.OnPreDrawListener {
        needsManualDraw.set(true)
        true
    }
    internal val displayId: Int get() = virtualDisplay.display.displayId
    internal val sourceHasWindowFocus: Boolean
        get() = presentation.window?.decorView?.hasWindowFocus() == true

    init {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Construct on Android main thread" }
        require((externalOesTexture > 0 || outputSurface != null) && width > 0 && height > 0)
        texture?.setDefaultBufferSize(width, height)
        texture?.setOnFrameAvailableListener({
            hasFrame.set(true)
            availableFrames++
            if (availableFrames <= 3) Log.i("MoonlightSpatialUI", "virtual display frame available #$availableFrames ${width}x$height")
        }, main)
        surface = outputSurface ?: Surface(checkNotNull(texture))
        displaySink = if (manualSurfaceRendering) ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader ->
                // The attached HWUI window is our damage signal. Rasterize the manual
                // SurfaceTexture only when that window actually produces a new frame.
                reader.acquireLatestImage()?.let { image -> image.close(); needsManualDraw.set(true) }
            }, main)
        } else null
        val manager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        virtualDisplay = checkNotNull(manager.createVirtualDisplay(
            "MoonlightXR-Compose", width, height, UI_DENSITY_DPI,
            displaySink?.surface ?: surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
        ))
        presentation = MenuPresentation(activity, virtualDisplay.display, content)
        Log.i("MoonlightSpatialUI", "virtual display id=${virtualDisplay.display.displayId} ${width}x$height")
        presentation.show()
        presentation.window?.decorView?.viewTreeObserver?.addOnPreDrawListener(sourcePreDraw)
        if (manualSurfaceRendering) {
            // Keep a live sink for the virtual display's HWUI window; write our own
            // SurfaceTexture from the attached Compose view without disconnecting that sink.
            val choreographer = Choreographer.getInstance()
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (closed) return
                    val root = presentation.window?.decorView
                    if (root != null && root.width > 0 && root.height > 0 && needsManualDraw.getAndSet(false)) {
                        try {
                            val canvas = surface.lockHardwareCanvas()
                            try {
                                canvas.drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                root.draw(canvas)
                            } finally { surface.unlockCanvasAndPost(canvas) }
                            manualFrames++
                            if (manualFrames <= 3) Log.i("MoonlightSpatialUI", "manual Surface frame #$manualFrames ${width}x$height")
                        } catch (error: RuntimeException) {
                            Log.e("MoonlightSpatialUI", "manual Surface draw failed", error)
                            closed = true
                            return
                        }
                    }
                    choreographer.postFrameCallback(this)
                }
            }
            frameCallback = callback
            choreographer.postFrameCallback(callback)
        }
    }

    private class MenuPresentation(private val owner: ComponentActivity, display: android.view.Display,
                                   private val content: @Composable () -> Unit) :
        Presentation(owner, display) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            Log.i("MoonlightSpatialUI", "Presentation.onCreate display=${display.displayId} densityDpi=${context.resources.displayMetrics.densityDpi}")
            window?.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            val root = checkNotNull(window?.decorView)
            root.setViewTreeLifecycleOwner(owner)
            root.setViewTreeViewModelStoreOwner(owner)
            root.setViewTreeSavedStateRegistryOwner(owner)
            setContentView(ComposeView(context).apply {
                setContent {
                    SideEffect { Log.i("MoonlightSpatialUI", "Compose content committed display=${display.displayId}") }
                    val qaLocale = if ((owner.applicationInfo.flags and
                        android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                        owner.intent.getStringExtra("qa_locale")?.takeIf { it in setOf("en", "zh-CN") }
                    else null
                    SpatialLocaleOverride(qaLocale) { SpatialTheme {
                        SpatialTextMenuHost(Modifier.fillMaxSize()) { content() }
                    } }
                }
            })
            root.post { Log.i("MoonlightSpatialUI", "Presentation decor=${root.width}x${root.height}") }
        }
    }

    /** Returns true only when a new buffer was latched. Do not call on the Android main thread. */
    fun latchFrameOnGlThread(transform: FloatArray): Boolean {
        require(transform.size >= 16)
        val texture = texture ?: return false
        if (!hasFrame.getAndSet(false)) return false
        texture.updateTexImage()
        texture.getTransformMatrix(transform)
        latchedFrames++
        if (latchedFrames <= 3) Log.i("MoonlightSpatialUI",
            "latched frame #$latchedFrames ${width}x$height transform=${transform[0]},${transform[5]},${transform[12]},${transform[13]}")
        return true
    }

    /** QA-only source image, before OES transfer or XR composition; does not need an active XR frame. */
    fun captureSourceBitmap(): Bitmap? {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (closed) return null
        val root = presentation.window?.decorView ?: return null
        if (root.width != width || root.height != height) return null
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
            root.draw(canvas)
        }
    }

    /** Pixel coordinates are top-left-origin, produced by [QuadInput.intersect]. */
    override fun pointer(x: Float, y: Float, action: Int) =
        pointer(x, y, action, android.os.SystemClock.uptimeMillis())

    fun pointer(x: Float, y: Float, action: Int, eventTime: Long, touchInput: Boolean = false) {
        require(action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE ||
            action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ||
            action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_HOVER_ENTER ||
            action == MotionEvent.ACTION_HOVER_EXIT)
        val dispatch = {
            if (!closed) {
                if (action == MotionEvent.ACTION_DOWN) downTime = eventTime
                val contact = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE
                val properties = arrayOf(MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = if (touchInput) MotionEvent.TOOL_TYPE_FINGER else MotionEvent.TOOL_TYPE_MOUSE
                })
                val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y
                    pressure = if (contact) 1f else 0f
                })
                // Keep source, tool type and primary-button state consistent from hover
                // through DOWN/MOVE/UP. Compose must see one device, not alternating mouse/finger.
                val event = MotionEvent.obtain(if (downTime == 0L) eventTime else downTime,
                    eventTime, action, 1, properties, coordinates, 0,
                    if (contact && !touchInput) MotionEvent.BUTTON_PRIMARY else 0, 1f, 1f, 0, 0,
                    if (touchInput) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_MOUSE, 0)
                if (action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_EXIT)
                    presentation.window?.decorView?.dispatchGenericMotionEvent(event)
                else presentation.window?.decorView?.dispatchTouchEvent(event)
                event.recycle()
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) downTime = 0L
            }
        }
        // Native input is already routed onto the UI thread. Posting it again makes a
        // short DOWN/UP wait behind rendering work and can make a tap feel like a hold.
        if (Looper.myLooper() == main.looper) dispatch() else main.post { dispatch() }
    }

    /** Joystick vertical motion in Android scroll units; dispatch on the same UI timeline. */
    override fun scroll(x: Float, y: Float, vertical: Float) =
        scroll(x, y, vertical, android.os.SystemClock.uptimeMillis())

    fun scroll(x: Float, y: Float, vertical: Float, eventTime: Long) {
        if (vertical == 0f) return
        main.post {
            presentation.window?.decorView?.let {
                dispatchSpatialScroll(it, x, y, vertical, eventTime)
            }
        }
    }

    /** Call on main thread after native rendering stops; release the GL texture on its GL thread. */
    fun close() {
        check(Looper.myLooper() == Looper.getMainLooper())
        closed = true
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
        presentation.window?.decorView?.viewTreeObserver?.takeIf { it.isAlive }
            ?.removeOnPreDrawListener(sourcePreDraw)
        presentation.dismiss()
        virtualDisplay.release()
        displaySink?.close()
        surface.release()
        texture?.release()
    }
}

/** Share the exact Android mouse-wheel event between the XR bridge and panel QA. */
internal fun dispatchSpatialScroll(view: View, x: Float, y: Float, vertical: Float,
                                   eventTime: Long = android.os.SystemClock.uptimeMillis()): Boolean {
    if (vertical == 0f) return false
    val properties = arrayOf(MotionEvent.PointerProperties().apply {
        id = 0
        toolType = MotionEvent.TOOL_TYPE_MOUSE
    })
    val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
        this.x = x
        this.y = y
        setAxisValue(MotionEvent.AXIS_VSCROLL, vertical)
    })
    val event = MotionEvent.obtain(eventTime, eventTime, MotionEvent.ACTION_SCROLL, 1,
        properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
    return try { view.dispatchGenericMotionEvent(event) } finally { event.recycle() }
}
