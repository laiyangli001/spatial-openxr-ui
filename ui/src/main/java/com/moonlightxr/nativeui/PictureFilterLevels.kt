package com.moonlightxr.nativeui
import kotlin.math.roundToInt
/** Legacy sharpenMode / blurMode are 0..5, with 0 off. Keep the existing preference keys compatible. */
internal object PictureFilterLevels {
    const val Maximum = 5
    fun level(value: Float): Int =
        ((if (value.isFinite()) value.coerceIn(0f, 1f) else 0f) * Maximum).roundToInt()
    fun normalized(level: Int): Float = level.coerceIn(0, Maximum) / Maximum.toFloat()
    fun snap(value: Float): Float = normalized(level(value))
}
