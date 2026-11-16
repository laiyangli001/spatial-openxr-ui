package com.moonlightxr.nativeui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlin.math.min

enum class SpatialEye { Left, Right }
enum class SpatialTextureLayout { None, SideBySide, TopAndBottom }

/** Accept only assets in this APK, never arbitrary filesystem or network URLs. */
internal fun spatialStereoAssetPath(assetUri: String): String {
    require(assetUri.startsWith("asset:///")) { "Stereo images must use asset:/// URIs" }
    val path = assetUri.removePrefix("asset:///")
    require(path.isNotEmpty() && path.split('/').none { it.isEmpty() || it == "." || it == ".." })
    return path
}

@Composable
private fun rememberStereoAsset(assetUri: String): ImageBitmap {
    val assets = LocalContext.current.assets
    return remember(assets, assetUri) {
        val path = spatialStereoAssetPath(assetUri)
        assets.open(path).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) { "Invalid stereo image: $assetUri" }
                .asImageBitmap()
        }
    }
}

/** Pixel regions cover the packed source exactly, including odd image dimensions. */
internal fun stereoSourceRect(width: Int, height: Int, layout: SpatialTextureLayout,
                              eye: SpatialEye): IntRect {
    require(width > 0 && height > 0)
    return when (layout) {
        SpatialTextureLayout.None -> IntRect(0, 0, width, height)
        SpatialTextureLayout.SideBySide -> {
            require(width >= 2)
            val split = width / 2
            if (eye == SpatialEye.Left) IntRect(0, 0, split, height)
            else IntRect(split, 0, width, height)
        }
        SpatialTextureLayout.TopAndBottom -> {
            require(height >= 2)
            val split = height / 2
            if (eye == SpatialEye.Left) IntRect(0, 0, width, split)
            else IntRect(0, split, width, height)
        }
    }
}

/** Use one eye's source ratio for both the physical Quad and its render target. */
internal fun fittedStereoQuad(template: UiQuad, width: Int, height: Int,
                              layout: SpatialTextureLayout): UiQuad {
    require(template.widthMeters > 0f && template.heightMeters > 0f)
    require(template.widthPixels > 0 && template.heightPixels > 0)
    val eye = stereoSourceRect(width, height, layout, SpatialEye.Left)
    val aspect = eye.width.toDouble() / eye.height
    val physicalHeight = min(template.heightMeters.toDouble(), template.widthMeters / aspect)
    val physicalWidth = physicalHeight * aspect
    val pixelHeight = min(template.heightPixels.toDouble(), template.widthPixels / aspect)
    val pixelWidth = pixelHeight * aspect
    return template.copy(widthMeters = physicalWidth.toFloat(),
        heightMeters = physicalHeight.toFloat(),
        widthPixels = pixelWidth.roundToInt().coerceAtLeast(1),
        heightPixels = pixelHeight.roundToInt().coerceAtLeast(1))
}

@Composable
private fun stereoPainter(source: ImageBitmap, layout: SpatialTextureLayout, eye: SpatialEye): Painter {
    val rect = stereoSourceRect(source.width, source.height, layout, eye)
    return remember(source, rect) {
        object : Painter() {
            override val intrinsicSize = Size(rect.width.toFloat(), rect.height.toFloat())
            override fun DrawScope.onDraw() {
                drawImage(source, srcOffset = IntOffset(rect.left, rect.top),
                    srcSize = IntSize(rect.width, rect.height),
                    dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1),
                        size.height.roundToInt().coerceAtLeast(1)), filterQuality = FilterQuality.High)
            }
        }
    }
}

/** Per-eye content primitive; use SpatialStereoImageSurface to submit actual stereo layers. */
@Composable
fun SpatialStereoImage(left: Painter, right: Painter, eye: SpatialEye,
                       contentDescription: String?, modifier: Modifier = Modifier) {
    Image(if (eye == SpatialEye.Left) left else right,
        contentDescription?.let { spatialString(it) }, modifier)
}

@Composable
fun SpatialStereoImage(source: ImageBitmap, layout: SpatialTextureLayout, eye: SpatialEye,
                       contentDescription: String?, modifier: Modifier = Modifier) {
    Image(stereoPainter(source, layout, eye), contentDescription?.let { spatialString(it) }, modifier,
        contentScale = ContentScale.Fit)
}

/** PICO-style asset URI overload for callers that render one eye inside their own surface. */
@Composable
fun SpatialStereoImage(assetUri: String, layout: SpatialTextureLayout, eye: SpatialEye,
                       contentDescription: String?, modifier: Modifier = Modifier) {
    SpatialStereoImage(rememberStereoAsset(assetUri), layout, eye, contentDescription, modifier)
}

internal fun stereoImageQuads(template: UiQuad): Pair<UiQuad, UiQuad> {
    require(template.id.isNotBlank() && template.id.length <= 74)
    require(template.eyeVisibility == UiEyeVisibility.Both && template.stereoGroupId == null)
    return template.copy(id = "${template.id}-left", eyeVisibility = UiEyeVisibility.Left,
        stereoGroupId = template.id) to template.copy(id = "${template.id}-right",
        eyeVisibility = UiEyeVisibility.Right, stereoGroupId = template.id)
}

internal fun requireStereoPair(left: UiQuad, right: UiQuad) {
    require(left.id != right.id && !left.stereoGroupId.isNullOrBlank())
    require(left.eyeVisibility == UiEyeVisibility.Left && right.eyeVisibility == UiEyeVisibility.Right)
    require(left.copy(id = right.id, eyeVisibility = UiEyeVisibility.Right) == right) {
        "Stereo eye layers must share geometry, parent, facing and group"
    }
}

/** Non-interactive photo surface. The host must admit two layers and submit both in one XR frame. */
@Composable
fun SpatialStereoImageSurface(quad: UiQuad, left: Painter, right: Painter,
                              host: SpatialFloatingHost?, visible: Boolean,
                              contentDescription: String?, modifier: Modifier = Modifier) {
    val pair = remember(quad) { stereoImageQuads(quad) }
    val latestLeft by rememberUpdatedState(left)
    val latestRight by rememberUpdatedState(right)
    val latestDescription by rememberUpdatedState(contentDescription)
    var admitted by remember { mutableStateOf(false) }
    DisposableEffect(host, pair, visible, left, right) {
        admitted = visible && host?.showStereo(pair.first, pair.second) { eye ->
            SpatialStereoImage(latestLeft, latestRight, eye, latestDescription, Modifier.fillMaxSize())
        } == true
        onDispose {
            if (visible) { host?.hide(pair.first.id); host?.hide(pair.second.id) }
            admitted = false
        }
    }
    val leftState = host?.presentationState(pair.first.id) ?: SpatialLayerPresentation.Missing
    val rightState = host?.presentationState(pair.second.id) ?: SpatialLayerPresentation.Missing
    LaunchedEffect(leftState, rightState) {
        if (leftState == SpatialLayerPresentation.Failed || rightState == SpatialLayerPresentation.Failed) {
            admitted = false
            host?.hide(pair.first.id)
            host?.hide(pair.second.id)
        }
    }
    if (visible && (!admitted || leftState != SpatialLayerPresentation.Ready ||
            rightState != SpatialLayerPresentation.Ready)) {
        SpatialStereoImage(left, right, SpatialEye.Left, contentDescription, modifier)
    }
}

/** Mono sources cost one layer; packed stereo sources use one crop and layer per eye. */
@Composable
fun SpatialStereoImageSurface(quad: UiQuad, source: ImageBitmap, layout: SpatialTextureLayout,
                              host: SpatialFloatingHost?, visible: Boolean,
                              contentDescription: String?, modifier: Modifier = Modifier) {
    val fittedQuad = remember(quad, source.width, source.height, layout) {
        fittedStereoQuad(quad, source.width, source.height, layout)
    }
    if (layout == SpatialTextureLayout.None) {
        SpatialFloatingSurface(fittedQuad, host, visible, modal = false, focusable = false,
            inlineFallback = { SpatialStereoImage(source, layout, SpatialEye.Left, contentDescription, modifier) }) {
            SpatialStereoImage(source, layout, SpatialEye.Left, contentDescription, Modifier.fillMaxSize())
        }
    } else SpatialStereoImageSurface(fittedQuad, stereoPainter(source, layout, SpatialEye.Left),
        stereoPainter(source, layout, SpatialEye.Right), host, visible, contentDescription, modifier)
}

/** Asset sources use the same mono or paired-eye OpenXR Quad path as bitmap sources. */
@Composable
fun SpatialStereoImageSurface(quad: UiQuad, assetUri: String, layout: SpatialTextureLayout,
                              host: SpatialFloatingHost?, visible: Boolean,
                              contentDescription: String?, modifier: Modifier = Modifier) {
    SpatialStereoImageSurface(quad, rememberStereoAsset(assetUri), layout, host, visible,
        contentDescription, modifier)
}

/** Two model views, each rendered from its own eye camera, on matching eye-only Quads. */
@Composable
fun SpatialStereoImageSurface(quad: UiQuad, leftAssetUri: String, rightAssetUri: String,
                              host: SpatialFloatingHost?, visible: Boolean,
                              contentDescription: String?, modifier: Modifier = Modifier) {
    val left = rememberStereoAsset(leftAssetUri)
    val right = rememberStereoAsset(rightAssetUri)
    require(left.width == right.width && left.height == right.height) {
        "Stereo eye textures must have matching dimensions"
    }
    val fittedQuad = remember(quad, left.width, left.height) {
        fittedStereoQuad(quad, left.width, left.height, SpatialTextureLayout.None)
    }
    SpatialStereoImageSurface(fittedQuad, remember(left) { BitmapPainter(left) },
        remember(right) { BitmapPainter(right) }, host, visible, contentDescription, modifier)
}
