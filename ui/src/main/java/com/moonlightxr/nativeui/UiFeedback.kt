package com.moonlightxr.nativeui

/** Hover feedback for spatial pointer interactions (haptics and optional UI sound). */
internal object UiFeedback {
    var hover: () -> Unit = {}
    var key: () -> Unit = {}
    var tap: () -> Unit = {}
    /** Controller currently pointing the laser; -1 when none. */
    var pointerHand: Int = -1
}
