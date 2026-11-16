package com.moonlightxr.nativeui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Phone-settings-style category list, without category group headings or summaries. */
@Composable
fun SpatialFlatCategoryMenu(state: MenuUiState) {
    SpatialPanel(Modifier.fillMaxWidth().height(450.dp)) {
        Column(Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                SpatialText("设置", color = SpatialTokens.text, fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold)
            }
            SpatialScrollArea(Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth()) {
                    FlatCategoryRow("基本", R.drawable.menu_display, Color(0xFF2589EE),
                        state.wheelSelectedCategory == "基本") { state.selectWheelCategory("基本") }
                    FlatCategoryRow("多显示器", R.drawable.menu_multidisplay, Color(0xFF5575D8),
                        state.wheelSelectedCategory == "多显示器") {
                        state.selectWheelCategory("多显示器")
                    }
                    FlatCategoryRow("画面", R.drawable.home_gallery, Color(0xFFE9A12F),
                        state.wheelSelectedCategory == "画面") { state.selectWheelCategory("画面") }
                    FlatCategoryRow("AI 3D", R.drawable.menu_ai3d, Color(0xFF8261DD),
                        state.wheelSelectedCategory == "AI 3D") { state.selectWheelCategory("AI 3D") }
                    FlatCategoryRow("控制", R.drawable.home_streaming, Color(0xFF12AE9C),
                        state.wheelSelectedCategory == "控制") { state.selectWheelCategory("控制") }
                    FlatCategoryRow("高级", R.drawable.home_settings, Color(0xFF788CA9),
                        state.wheelSelectedCategory == "高级") { state.selectWheelCategory("高级") }
                }
            }
        }
    }
}

@Composable
private fun FlatCategoryRow(label: String, @DrawableRes icon: Int, iconColor: Color,
                            selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(hovered) { if (hovered) UiFeedback.hover() }
    val light = SpatialTokens.panelBottom.luminance() > 0.5f
    val rowFill = when {
        selected || pressed -> SpatialTokens.accentStrong.copy(alpha = if (light) 0.22f else 0.36f)
        hovered -> SpatialTokens.accent.copy(alpha = if (light) 0.12f else 0.19f)
        else -> Color.Transparent
    }
    Row(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(12.dp))
        .background(rowFill)
        .hoverable(interaction)
        .clickable(interactionSource = interaction, indication = null,
            role = Role.Button, onClick = { UiFeedback.tap(); onClick() })
        .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
            .background(iconColor), contentAlignment = Alignment.Center) {
            Image(painterResource(icon), null, Modifier.size(31.dp))
        }
        SpatialText(label, Modifier.weight(1f), color = SpatialTokens.text,
            fontSize = SpatialTokens.primaryMenuFontSize, fontWeight = FontWeight.SemiBold,
            maxLines = 1)
        SpatialText("›", color = SpatialTokens.muted, fontSize = 27.sp)
    }
}
