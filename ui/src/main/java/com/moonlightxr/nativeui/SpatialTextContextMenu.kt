package com.moonlightxr.nativeui

import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.roundToInt

/** Owns only a Compose session: never creates an Android Popup or ActionMode window. */
internal class SpatialTextContextMenuProvider : TextContextMenuProvider {
    internal class Session(val data: TextContextMenuDataProvider) : TextContextMenuSession {
        private val closed = CompletableDeferred<Unit>()
        override fun close() { closed.complete(Unit) }
        suspend fun awaitClose() { closed.await() }
    }

    var active by mutableStateOf<Session?>(null)
        private set

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        val current = Session(dataProvider)
        active?.close()
        active = current
        try {
            current.awaitClose()
        } finally {
            // A replaced session must not clear its successor when its coroutine resumes.
            if (active === current) active = null
        }
    }

    fun close() { active?.close() }
}

/** All coordinates are local to the containing Compose surface. */
internal fun selectionToolbarOffset(anchor: Rect, container: IntSize, toolbar: IntSize,
                                    gap: Float): IntOffset {
    val maxX = (container.width - toolbar.width).coerceAtLeast(0).toFloat()
    val maxY = (container.height - toolbar.height).coerceAtLeast(0).toFloat()
    val x = (anchor.center.x - toolbar.width / 2f).coerceIn(0f, maxX)
    val above = anchor.top - toolbar.height - gap
    val y = (if (above >= 0f) above else anchor.bottom + gap).coerceIn(0f, maxY)
    return IntOffset(x.roundToInt(), y.roundToInt())
}
