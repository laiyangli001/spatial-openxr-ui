package com.moonlightxr.nativeui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A tiny, repeatable frost image. Rasterized once; the UI Quad still keeps its source alpha. */
private object FrostedGlassGrain {
    private val image by lazy {
        val side = 256
        val cellSize = 32
        val cellCount = side / cellSize
        val pixels = IntArray(side * side)
        fun sample(x: Int, y: Int): Float {
            val wrappedX = (x + cellCount) % cellCount
            val wrappedY = (y + cellCount) % cellCount
            var hash = wrappedX * 73856093 xor wrappedY * 19349663 xor 0x41C64E6D
            hash = hash xor (hash ushr 13)
            return (hash and 0xFF) / 255f
        }
        fun fade(value: Float) = value * value * (3f - 2f * value)
        for (y in 0 until side) {
            val cellY = y / cellSize
            val fy = fade((y % cellSize) / cellSize.toFloat())
            for (x in 0 until side) {
                val cellX = x / cellSize
                val fx = fade((x % cellSize) / cellSize.toFloat())
                val top = sample(cellX, cellY) * (1f - fx) + sample(cellX + 1, cellY) * fx
                val bottom = sample(cellX, cellY + 1) * (1f - fx) + sample(cellX + 1, cellY + 1) * fx
                val haze = top * (1f - fy) + bottom * fy
                val alpha = 3 + (haze * 11f).toInt()
                pixels[y * side + x] = (alpha shl 24) or 0x00DCEBFF
            }
        }
        Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    val brush: Brush by lazy {
        ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated))
    }
}

/** Local design tokens; visual reference only, not PICO Spatial SDK API values. */
object SpatialTokens {
    // Primary navigation must remain more prominent than 16sp parameter names / 14sp values.
    val primaryMenuFontSize = 20.sp
    val primaryMenuLineHeight = 26.sp
    val canvas get() = SpatialThemeManager.palette.canvas
    val panelTop get() = SpatialThemeManager.palette.panelTop
    val panelBottom get() = SpatialThemeManager.palette.panelBottom
    val popupTop get() = SpatialThemeManager.palette.popupTop
    val popupBottom get() = SpatialThemeManager.palette.popupBottom
    val field get() = SpatialThemeManager.palette.field
    val accent get() = SpatialThemeManager.palette.accent
    val accentStrong get() = SpatialThemeManager.palette.accentStrong
    val onAccent get() = SpatialThemeManager.palette.onAccent
    val outline get() = SpatialThemeManager.palette.outline
    val text get() = SpatialThemeManager.palette.text
    val muted get() = SpatialThemeManager.palette.muted
    val disabled get() = SpatialThemeManager.palette.disabled
    val danger get() = SpatialThemeManager.palette.danger
    val panelShape = RoundedCornerShape(28.dp)
    val controlShape = RoundedCornerShape(14.dp)
    val panelPadding = PaddingValues(26.dp)
}

/** PICO's type hierarchy mapped to ordinary Compose, without a Spatial SDK runtime dependency. */
private val spatialTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 54.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun SpatialTheme(content: @Composable () -> Unit) {
    val lightSurface = SpatialTokens.panelBottom.luminance() > 0.5f
    MaterialTheme(
        colorScheme = if (lightSurface) lightColorScheme(
            primary = SpatialTokens.accentStrong,
            onPrimary = SpatialTokens.onAccent,
            surface = SpatialTokens.panelBottom,
            onSurface = SpatialTokens.text,
            surfaceVariant = SpatialTokens.field,
            onSurfaceVariant = SpatialTokens.muted,
            outline = SpatialTokens.outline,
        ) else darkColorScheme(
            primary = SpatialTokens.accent,
            onPrimary = SpatialTokens.onAccent,
            surface = SpatialTokens.panelBottom,
            onSurface = SpatialTokens.text,
            surfaceVariant = SpatialTokens.field,
            onSurfaceVariant = SpatialTokens.muted,
            outline = SpatialTokens.outline,
        ),
        typography = spatialTypography,
    ) {
        CompositionLocalProvider(LocalContentColor provides SpatialTokens.text, content = content)
    }
}

@Composable
fun SpatialPanel(modifier: Modifier = Modifier, modal: Boolean = false,
                 content: @Composable () -> Unit) {
    val localGlass = SpatialThemeManager.surfaceMode == SpatialSurfaceMode.Glass &&
        !(SpatialThemeManager.mode == SpatialThemeMode.PicoNative &&
            SpatialThemeManager.isUsingSystemPalette)
    Box(
        modifier
            .shadow(18.dp, SpatialTokens.panelShape, clip = false,
                ambientColor = SpatialTokens.accent.copy(alpha = 0.75f),
                spotColor = SpatialTokens.accent.copy(alpha = 0.65f))
            .clip(SpatialTokens.panelShape)
            .background(Brush.verticalGradient(if (modal)
                listOf(SpatialTokens.popupTop, SpatialTokens.popupBottom)
                else listOf(SpatialTokens.panelTop, SpatialTokens.panelBottom)))
            .then(if (localGlass) Modifier.drawWithCache {
                // The low-opacity frost image and sheen soften contrast while retaining a
                // clear view of the scene behind the Quad. Neither samples the scene itself.
                val sheen = Brush.verticalGradient(listOf(
                    Color.White.copy(alpha = 0.06f), Color.Transparent,
                    Color.White.copy(alpha = 0.02f)))
                val frost = FrostedGlassGrain.brush
                onDrawWithContent {
                    drawRect(sheen)
                    drawRect(frost)
                    drawContent()
                }
            } else Modifier)
            .border(BorderStroke(1.5.dp, SpatialTokens.outline), SpatialTokens.panelShape)
    ) { content() }
}
