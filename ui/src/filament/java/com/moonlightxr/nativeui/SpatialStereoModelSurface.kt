package com.moonlightxr.nativeui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Filament redraws a GLB from each tracked eye into an eye-only OpenXR Quad. */
@Composable
fun SpatialStereoModelSurface(quad: UiQuad, headPose: QuadPose?, quadPose: QuadPose?,
                                       host: SpatialFloatingHost?,
                                       assetPath: String = "models/home_streaming_pony.glb",
                                       poseSource: StereoPoseSource? = null,
                                       onFrameRendered: ((SpatialEye) -> Unit)? = null) {
    val pair = remember(quad) { stereoImageQuads(quad) }
    val latestHead by rememberUpdatedState(headPose)
    val latestQuad by rememberUpdatedState(quadPose)
    val latestFrameCallback by rememberUpdatedState(onFrameRendered)
    DisposableEffect(host, pair, assetPath, poseSource) {
        val admitted = host?.showStereo(pair.first, pair.second) { eye ->
            AndroidView(
                factory = {
                    FilamentHomeIcon(it, assetPath, eye, quad.widthMeters, quad.heightMeters).apply {
                        stereoPoseSource = poseSource
                        onFirstFrameRendered = { latestFrameCallback?.invoke(eye) }
                    }
                },
                update = { view -> view.updateStereoPose(latestHead, latestQuad) },
                modifier = Modifier.fillMaxSize(),
            )
        } == true
        onDispose {
            if (admitted) {
                host.hide(pair.first.id)
                host.hide(pair.second.id)
            }
        }
    }
}


