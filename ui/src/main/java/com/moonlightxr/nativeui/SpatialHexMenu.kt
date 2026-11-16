package com.moonlightxr.nativeui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sqrt

internal data class HexMenuCell(val label: String, val symbol: String, val x: Float, val y: Float)

/** A single regular-hexagon geometry drives artwork, content centers and pointer hit testing. */
internal object HexMenuGeometry {
    const val Width = 360f
    const val Height = 450f
    const val Radius = 64f
    const val Gap = 6f
    const val Outside = -1
    const val Stream = 3
    private val root3 = sqrt(3f)
    val halfWidth = root3 * Radius / 2f
    private val pitchX = halfWidth * 2f + Gap
    private val pitchY = pitchX * root3 / 2f
    val cells = listOf(
        HexMenuCell("基本", "🖥️", Width / 2f - pitchX / 2f, Height / 2f - pitchY),
        HexMenuCell("多显示器", "🖥️", Width / 2f + pitchX / 2f, Height / 2f - pitchY),
        HexMenuCell("画面", "🎨", Width / 2f - pitchX, Height / 2f),
        HexMenuCell("打开串流", "▶️", Width / 2f, Height / 2f),
        HexMenuCell("AI 3D", "🧊", Width / 2f + pitchX, Height / 2f),
        HexMenuCell("控制", "🎮", Width / 2f - pitchX / 2f, Height / 2f + pitchY),
        HexMenuCell("高级", "⚙️", Width / 2f + pitchX / 2f, Height / 2f + pitchY),
    )
    val visibleTopFraction: Float = cells.minOf { it.y - Radius } / Height

    fun hit(x: Float, y: Float, width: Float, height: Float): Int {
        if (!x.isFinite() || !y.isFinite() || !width.isFinite() || !height.isFinite() ||
            width <= 0f || height <= 0f) return Outside
        val localX = x * Width / width
        val localY = y * Height / height
        return cells.indexOfFirst { cell ->
            val dx = abs(localX - cell.x)
            val dy = abs(localY - cell.y)
            dx <= halfWidth && dy <= Radius - dx / root3
        }
    }

}

/** Every fill in opaque mode must be opaque, including hover and selection. */
internal fun hexFillColors(opaque: Boolean, light: Boolean, active: Boolean,
                           hovered: Boolean, pressed: Boolean,
                           top: Color, bottom: Color, accent: Color): List<Color> {
    if (opaque) return when {
        pressed -> listOf(accent.copy(alpha = 1f), accent.copy(alpha = 1f))
        active -> listOf(accent.copy(alpha = 1f), accent.copy(alpha = 1f))
        hovered -> if (light) listOf(Color(0xFFDAE8FA), Color(0xFFC7DDF5))
            else listOf(Color(0xFF235B90), Color(0xFF103D6C))
        else -> listOf(top.copy(alpha = 1f), bottom.copy(alpha = 1f))
    }
    return when {
        pressed -> listOf(accent.copy(alpha = 0.92f), accent.copy(alpha = 0.78f))
        active -> listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.67f))
        hovered -> listOf(top.copy(alpha = 0.72f), bottom.copy(alpha = 0.58f))
        else -> listOf(top, bottom)
    }
}

@Composable
fun SpatialHexMenu(selectedCategory: String?, onSelect: (String) -> Unit, onStream: () -> Unit) {
    var hovered by remember { mutableIntStateOf(HexMenuGeometry.Outside) }
    var pressed by remember { mutableIntStateOf(HexMenuGeometry.Outside) }
    LaunchedEffect(hovered) {
        if (hovered != HexMenuGeometry.Outside) UiFeedback.hover()
    }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnStream by rememberUpdatedState(onStream)
    val light = SpatialTokens.panelBottom.luminance() > 0.5f
    val opaque = SpatialThemeManager.surfaceMode == SpatialSurfaceMode.Opaque
    val idleTop = SpatialTokens.panelTop
    val idleBottom = SpatialTokens.panelBottom
    val idleEdge = if (light) SpatialTokens.accentStrong else Color(0xFF53BFFF)
    val idleText = if (light) SpatialTokens.text else Color.White
    fun activate(index: Int) {
        UiFeedback.tap()
        if (index == HexMenuGeometry.Stream) currentOnStream()
        else currentOnSelect(HexMenuGeometry.cells[index].label)
    }
    Box(Modifier.size(HexMenuGeometry.Width.dp, HexMenuGeometry.Height.dp)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                var held = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull()
                    hovered = if (event.type == PointerEventType.Exit || change == null)
                        HexMenuGeometry.Outside else HexMenuGeometry.hit(change.position.x,
                            change.position.y, size.width.toFloat(), size.height.toFloat())
                    if (change == null) {
                        held = false
                        pressed = HexMenuGeometry.Outside
                        continue
                    }
                    if (change.pressed && !held) {
                        held = true
                        pressed = hovered
                        // Activate on the initial Trigger edge. Waiting for a matching
                        // UP hit made a quick tap easy to lose when the ray moved across
                        // a scaled hex boundary or the extension Quad appeared.
                        if (pressed != HexMenuGeometry.Outside) activate(pressed)
                    } else if (!change.pressed && held) {
                        held = false
                        pressed = HexMenuGeometry.Outside
                    }
                }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val scale = size.width / HexMenuGeometry.Width
            val r = HexMenuGeometry.Radius * scale
            val a = HexMenuGeometry.halfWidth * scale
            HexMenuGeometry.cells.forEachIndexed { index, cell ->
                val center = Offset(cell.x * scale, cell.y * scale)
                val active = cell.label == selectedCategory
                val over = index == hovered
                val held = index == pressed && over
                val path = Path().apply {
                    moveTo(center.x, center.y - r)
                    lineTo(center.x + a, center.y - r / 2f)
                    lineTo(center.x + a, center.y + r / 2f)
                    lineTo(center.x, center.y + r)
                    lineTo(center.x - a, center.y + r / 2f)
                    lineTo(center.x - a, center.y - r / 2f)
                    close()
                }
                val edge = if (active || over) SpatialTokens.accent else idleEdge
                // Wide translucent strokes approximate the SVG glow without enlarging hit regions.
                drawPath(path, edge.copy(alpha = if (active || over) 0.18f else 0.07f),
                    style = Stroke(8f * scale))
                drawPath(path, edge.copy(alpha = 0.12f), style = Stroke(4f * scale))
                val colors = hexFillColors(opaque, light, active, over, held,
                    idleTop, idleBottom, SpatialTokens.accentStrong)
                drawPath(path, Brush.verticalGradient(colors, center.y - r, center.y + r))
                drawPath(path, edge, style = Stroke((if (active || over) 1.8f else 1.1f) * scale))
            }
        }
        HexMenuGeometry.cells.forEachIndexed { index, cell ->
            Column(Modifier.offset((cell.x - 49f).dp, (cell.y - 34f).dp).size(98.dp, 68.dp)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    selected = cell.label == selectedCategory
                    onClick { activate(index); true }
                }, horizontalAlignment = Alignment.CenterHorizontally) {
                SpatialText(cell.symbol, Modifier.clearAndSetSemantics {}, fontSize = 30.sp, lineHeight = 38.sp)
                SpatialText(cell.label, color = if (cell.label == selectedCategory ||
                    index == pressed || index == hovered && !light)
                    Color.White else idleText, fontSize = SpatialTokens.primaryMenuFontSize,
                    lineHeight = SpatialTokens.primaryMenuLineHeight,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}
