package com.moonlightxr.nativeui

/** Actions owned by the host activity for the spatial keyboard. */
internal object VirtualKeyboardCallbacks {
    var close: () -> Unit = {}
    var open: () -> Unit = {}
}
