package com.moonlightxr.nativeui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

enum class SpatialCoachmarkDirection { Above, Below, Left, Right }

/** In-surface anchor placement. A cross-Quad anchor is resolved by the floating host instead. */
@Composable
fun SpatialCoachmarkBox(
    visible: Boolean,
    message: String,
    anchor: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    direction: SpatialCoachmarkDirection = SpatialCoachmarkDirection.Below,
    onDismiss: (() -> Unit)? = null,
) {
    val mark: @Composable () -> Unit = {
        SpatialCoachmark(true, message, onDismiss = onDismiss)
    }
    val pointer: @Composable () -> Unit = {
        Canvas(Modifier.size(14.dp)) {
            val path = Path().apply {
                when (direction) {
                    SpatialCoachmarkDirection.Below -> {
                        moveTo(size.width / 2f, 0f); lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                    }
                    SpatialCoachmarkDirection.Above -> {
                        moveTo(0f, 0f); lineTo(size.width, 0f)
                        lineTo(size.width / 2f, size.height)
                    }
                    SpatialCoachmarkDirection.Left -> {
                        moveTo(0f, 0f); lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                    }
                    SpatialCoachmarkDirection.Right -> {
                        moveTo(size.width, 0f); lineTo(0f, size.height / 2f)
                        lineTo(size.width, size.height)
                    }
                }
                close()
            }
            drawPath(path, SpatialTokens.popupBottom)
        }
    }
    when (direction) {
        SpatialCoachmarkDirection.Above -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (visible) { mark(); pointer() }; anchor()
        }
        SpatialCoachmarkDirection.Below -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            anchor(); if (visible) { pointer(); mark() }
        }
        SpatialCoachmarkDirection.Left -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            if (visible) { mark(); pointer() }; anchor()
        }
        SpatialCoachmarkDirection.Right -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            anchor(); if (visible) { pointer(); mark() }
        }
    }
}
