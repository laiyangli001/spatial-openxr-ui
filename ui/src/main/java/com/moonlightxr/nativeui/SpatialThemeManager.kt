package com.moonlightxr.nativeui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class SpatialThemeMode { MoonlightBlue, PicoNative }
enum class SpatialSurfaceMode { Glass, Opaque }

/** System colors can be injected by the OS 6 prototype; OS 5 uses the explicit fallback palette. */
data class SpatialPalette(
    val canvas: Color,
    val panelTop: Color,
    val panelBottom: Color,
    val popupTop: Color,
    val popupBottom: Color,
    val field: Color,
    val accent: Color,
    val accentStrong: Color,
    val onAccent: Color,
    val outline: Color,
    val text: Color,
    val muted: Color,
    val disabled: Color,
    val danger: Color,
)

object SpatialPalettes {
    val MoonlightBlue = SpatialPalette(
        canvas = Color(0x00102039),
        panelTop = Color(0x5716365B),
        panelBottom = Color(0x43091A32),
        popupTop = Color(0x9E14365B),
        popupBottom = Color(0x8C081A32),
        field = Color(0xB9244569),
        accent = Color(0xFF39A9FF),
        accentStrong = Color(0xFF0879EE),
        onAccent = Color.White,
        outline = Color(0xFF74C9FF),
        text = Color(0xFFF5FAFF),
        muted = Color(0xFFB7D3ED),
        disabled = Color(0xFF677F97),
        danger = Color(0xFFFF8F85),
    )

    val MoonlightOpaque = MoonlightBlue.copy(
        panelTop = Color(0xFF16365B),
        panelBottom = Color(0xFF091A32),
        popupTop = Color(0xFF14365B),
        popupBottom = Color(0xFF081A32),
        field = Color(0xFF244569),
    )

    /** Opaque, cool off-white approximation. PICO does not publish fixed system RGB values. */
    val PicoFallback = SpatialPalette(
        canvas = Color.Transparent,
        panelTop = Color(0xFFF3F5F8),
        panelBottom = Color(0xFFE8ECF1),
        popupTop = Color(0xFFF8F9FB),
        popupBottom = Color(0xFFEDF1F5),
        field = Color(0xFFDFE5EC),
        accent = Color(0xFF367BE8),
        accentStrong = Color(0xFF2C6EDB),
        onAccent = Color.White,
        outline = Color(0xFFB8C4D2),
        text = Color(0xFF202632),
        muted = Color(0xFF5B6473),
        disabled = Color(0xFF8A929E),
        danger = Color(0xFFB3261E),
    )

    /** See-through OS 5 approximation of PICO's dark Vibrant roles, not compositor blur. */
    val PicoGlass = PicoFallback.copy(
        panelTop = Color(0x45172235),
        panelBottom = Color(0x33121D2E),
        popupTop = Color(0x8C172235),
        popupBottom = Color(0x75121D2E),
        field = Color(0xA6344B65),
        outline = Color(0xC4D8EBFF),
        text = Color(0xFFF5F9FF),
        muted = Color(0xFFD2DFF0),
        disabled = Color(0xFF95A7BC),
        danger = Color(0xFFFF8F85),
    )
}

object SpatialThemeManager {
    private const val PREFS = "moonlightxr_spatial_ui"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_PICO_SURFACE = "pico_surface_mode"
    private const val KEY_MOONLIGHT_SURFACE = "moonlight_surface_mode"

    var mode by mutableStateOf(SpatialThemeMode.MoonlightBlue)
        private set
    private var picoSurfaceMode by mutableStateOf(SpatialSurfaceMode.Opaque)
    private var moonlightSurfaceMode by mutableStateOf(SpatialSurfaceMode.Glass)
    private var picoSystemPalette by mutableStateOf<SpatialPalette?>(null)

    val surfaceMode: SpatialSurfaceMode get() = when (mode) {
        SpatialThemeMode.MoonlightBlue -> moonlightSurfaceMode
        SpatialThemeMode.PicoNative -> picoSurfaceMode
    }
    val isUsingSystemPalette: Boolean get() = mode == SpatialThemeMode.PicoNative && picoSystemPalette != null
    val palette: SpatialPalette get() = when (mode) {
        SpatialThemeMode.MoonlightBlue -> if (surfaceMode == SpatialSurfaceMode.Glass)
            SpatialPalettes.MoonlightBlue else SpatialPalettes.MoonlightOpaque
        SpatialThemeMode.PicoNative -> picoSystemPalette ?: if (surfaceMode == SpatialSurfaceMode.Glass)
            SpatialPalettes.PicoGlass else SpatialPalettes.PicoFallback
    }

    fun installPicoSystemPalette(palette: SpatialPalette?) { picoSystemPalette = palette }
    fun select(mode: SpatialThemeMode) { this.mode = mode }
    fun selectSurface(mode: SpatialSurfaceMode) {
        when (this.mode) {
            SpatialThemeMode.MoonlightBlue -> moonlightSurfaceMode = mode
            SpatialThemeMode.PicoNative -> picoSurfaceMode = mode
        }
    }

    fun selectSurface(context: Context, mode: SpatialSurfaceMode) {
        selectSurface(mode)
        val key = if (this.mode == SpatialThemeMode.PicoNative) KEY_PICO_SURFACE else KEY_MOONLIGHT_SURFACE
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, mode.name).apply()
    }

    fun select(context: Context, mode: SpatialThemeMode) {
        select(mode)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, mode.name).apply()
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME, null)
        mode = SpatialThemeMode.entries.firstOrNull { it.name == saved }
            ?: SpatialThemeMode.MoonlightBlue
        picoSurfaceMode = SpatialSurfaceMode.entries.firstOrNull {
            it.name == prefs.getString(KEY_PICO_SURFACE, null)
        } ?: SpatialSurfaceMode.Opaque
        moonlightSurfaceMode = SpatialSurfaceMode.entries.firstOrNull {
            it.name == prefs.getString(KEY_MOONLIGHT_SURFACE, null)
        } ?: SpatialSurfaceMode.Glass
    }
}

/** Theme choice persists across both the native XR host and the 2-D preview. */
@Composable
fun SpatialThemeManagementOption(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SpatialParameterRow {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SpatialText("界面主题", Modifier.weight(1f), color = SpatialTokens.text)
                    SpatialParameterHelp("界面主题")
                }
                SpatialSegmentControl(listOf("月光蓝", "PICO"),
                    if (SpatialThemeManager.mode == SpatialThemeMode.MoonlightBlue) 0 else 1,
                    { index -> SpatialThemeManager.select(context,
                        if (index == 0) SpatialThemeMode.MoonlightBlue else SpatialThemeMode.PicoNative) })
            }
        }
        if (LocalGroupedParameters.current) SpatialDivider(Modifier.padding(horizontal = 10.dp))
        SpatialParameterRow {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SpatialText("面板背景", Modifier.weight(1f), color = SpatialTokens.text)
                    SpatialParameterHelp("面板背景")
                }
                SpatialSegmentControl(listOf("透明 · 雾面", "不透明"),
                    if (SpatialThemeManager.surfaceMode == SpatialSurfaceMode.Glass) 0 else 1,
                    { index -> SpatialThemeManager.selectSurface(context,
                        if (index == 0) SpatialSurfaceMode.Glass else SpatialSurfaceMode.Opaque) },
                    Modifier.recordParameterControl("面板背景"))
            }
        }
    }
}
