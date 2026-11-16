package com.moonlightxr.nativeui

/** Render-thread-only GLES helpers. The OpenXR host owns acquire/wait/release and xrEndFrame. */
object NativeGlesBridge {
    init { System.loadLibrary("moonlight_spatial_ui_bridge") }

    /** Must run with the same EGL context used by the native OpenXR GLES session. */
    external fun createExternalTexture(): Int

    /** Copy the latest SurfaceTexture image into an already acquired GL_TEXTURE_2D UI swapchain image. */
    external fun copyToAcquiredSwapchain(
        externalTexture: Int, swapchainTexture: Int,
        width: Int, height: Int, surfaceTransform: FloatArray,
        directGlQaPattern: Boolean = false,
        layerRole: Int = 0, // 0 main, 1 popup, 2 wheel extension (debug labels/pattern)
        colorCheckParameters: FloatArray? = null,
    ): Boolean

    /** Explicit colour diagnostic / debug QA: read the unprojected UI Quad texture while its swapchain image is acquired. */
    external fun readAcquiredTextureRgba(swapchainTexture: Int, width: Int, height: Int): ByteArray?
}
