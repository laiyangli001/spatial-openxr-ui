package com.moonlightxr.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val panelFill = Color(0xF20D1420)
private val keyFill = Color(0xFF172433)
private val keyHover = Color(0xFF2E6FA8)
private val keyEdge = Color(0xFF33475F)
private val accent = Color(0xFF53BFFF)
private const val UNITS = 18f

@Composable
private fun RowScope.Key(label: String, weight: Float, keyHeight: Dp, onPress: () -> Unit) {
    if (label.isEmpty()) { Spacer(Modifier.weight(weight).height(keyHeight)); return }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) UiFeedback.hover() }
    Box(Modifier.weight(weight).height(keyHeight).padding(2.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(if (hovered) keyHover else keyFill)
        .border(1.dp, if (hovered) accent else keyEdge, RoundedCornerShape(4.dp))
        .hoverable(interactions)
        .clickable(interactionSource = interactions, indication = null) {
            UiFeedback.tap(); onPress()
        }, contentAlignment = Alignment.Center) {
        SpatialText(label, color = Color(0xFFEAF4FF), fontSize = 15.sp,
            fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private data class KeySpec(val label: String, val units: Float)

@Composable
private fun KeyRow(keys: List<KeySpec>, unit: Dp, keyHeight: Dp, onKey: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        keys.forEach { spec -> Key(spec.label, spec.units, keyHeight) { onKey(spec.label) } }
    }
}

private fun row(vararg pairs: Pair<String, Float>) = pairs.map { KeySpec(it.first, it.second) }

/** In-app spatial keyboard; opened by left X, feeds VirtualKeyboardTarget. */
@Composable
internal fun VirtualKeyboardPanel(modifier: Modifier = Modifier, onClose: () -> Unit = {}) {
    var shift by remember { mutableStateOf(false) }
    var caps by remember { mutableStateOf(false) }
    val upper = shift != caps
    fun type(key: String) {
        when (key) {
            "Bksp" -> VirtualKeyboardTarget.backspace()
            "Shift" -> shift = !shift
            "Caps" -> caps = !caps
            "Space" -> VirtualKeyboardTarget.insert(" ")
            "Enter", "Esc", "Close" -> onClose()
            "↑", "↓", "←", "→", "Ins", "Hom", "PgU", "Del", "End", "PgD",
            "PrtSc", "ScrLk", "Pause", "Win", "Apps", "Ctrl", "Alt",
            "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12" -> {}
            else -> VirtualKeyboardTarget.insert(charFor(key, upper))
        }
    }
    val shape = RoundedCornerShape(20.dp)
    BoxWithConstraints(modifier.background(panelFill, shape).border(2.dp, Color(0xFF3E6C9E), shape)
        .padding(horizontal = 14.dp, vertical = 12.dp)) {
        val unit = maxWidth / UNITS
        val keyH = unit - 4.dp
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            SpatialText("空间键盘  ·  左手 X 呼出 / 收起", color = accent, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold)
            SpatialText("激光悬停=震动+提示音  Trigger=输入  Grip=拖动面板",
                color = Color(0xFF7FA3C8), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            KeyRow(row("Esc" to 1.5f, "F1" to 1f, "F2" to 1f, "F3" to 1f, "F4" to 1f,
                "F5" to 1f, "F6" to 1f, "F7" to 1f, "F8" to 1f, "F9" to 1f,
                "F10" to 1f, "F11" to 1f, "F12" to 1f,
                "PrtSc" to 1.5f, "ScrLk" to 1.5f, "Pause" to 1.5f), unit, keyH, ::type)
            KeyRow(row("`" to 1f, "1" to 1f, "2" to 1f, "3" to 1f, "4" to 1f, "5" to 1f,
                "6" to 1f, "7" to 1f, "8" to 1f, "9" to 1f, "0" to 1f, "-" to 1f,
                "=" to 1f, "Bksp" to 2f, "Ins" to 1f, "Hom" to 1f, "PgU" to 1f), unit, keyH, ::type)
            KeyRow(row("Tab" to 1.5f, "Q" to 1f, "W" to 1f, "E" to 1f, "R" to 1f, "T" to 1f,
                "Y" to 1f, "U" to 1f, "I" to 1f, "O" to 1f, "P" to 1f, "[" to 1f,
                "]" to 1f, "\\" to 1.5f, "Del" to 1f, "End" to 1f, "PgD" to 1f), unit, keyH, ::type)
            KeyRow(row("Caps" to 1.75f, "A" to 1f, "S" to 1f, "D" to 1f, "F" to 1f, "G" to 1f,
                "H" to 1f, "J" to 1f, "K" to 1f, "L" to 1f, ";" to 1f, "'" to 1f,
                "Enter" to 2.25f, "" to 3f), unit, keyH, ::type)
            KeyRow(row("Shift" to 2.25f, "Z" to 1f, "X" to 1f, "C" to 1f, "V" to 1f, "B" to 1f,
                "N" to 1f, "M" to 1f, "," to 1f, "." to 1f, "/" to 1f,
                "Shift" to 2.75f, "" to 1f, "↑" to 1f, "" to 1f), unit, keyH, ::type)
            KeyRow(row("Ctrl" to 1.5f, "Win" to 1f, "Alt" to 1.25f, "Space" to 7.5f,
                "Alt" to 1.25f, "Apps" to 1f, "Ctrl" to 1.5f,
                "←" to 1f, "↓" to 1f, "→" to 1f), unit, keyH, ::type)
            Spacer(Modifier.height(6.dp))
            KeyRow(row("Ctrl+C" to 2.2f, "Ctrl+V" to 2.2f, "Ctrl+X" to 2.2f,
                "Del" to 1.6f, "Close" to 2.2f), unit, keyH, ::type)
            Spacer(Modifier.height(unit * 0.5f))
        }
    }
}

private fun charFor(key: String, upper: Boolean): String {
    if (key.length != 1) return key
    val c = key[0]
    if (c.isLetter()) return (if (upper) c.uppercaseChar() else c.lowercaseChar()).toString()
    if (!upper) return key
    return when (c) {
        '`' -> "~"; '1' -> "!"; '2' -> "@"; '3' -> "#"; '4' -> "$"; '5' -> "%"
        '6' -> "^"; '7' -> "&"; '8' -> "*"; '9' -> "("; '0' -> ")"
        '-' -> "_"; '=' -> "+"; '[' -> "{"; ']' -> "}"; '\\' -> "|"
        ';' -> ":"; '\'' -> "\""; ',' -> "<"; '.' -> ">"; '/' -> "?"
        else -> key
    }
}
