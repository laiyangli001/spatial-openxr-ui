package com.moonlightxr.nativeui

/** Overlapping viewports cover every content pixel, including a partially clipped final row. */
internal fun spatialScrollCapturePositions(maximum: Int, viewport: Int): List<Int> {
    require(maximum in 0 until Int.MAX_VALUE && viewport > 0)
    val stride = (viewport.toLong() * 3 / 4).coerceAtLeast(1)
    require(maximum.toLong() / stride <= 64) { "Scroll catalog exceeds the bounded QA run" }
    return buildList {
        add(0)
        while (last() < maximum) add((last().toLong() + stride).coerceAtMost(maximum.toLong()).toInt())
    }
}
