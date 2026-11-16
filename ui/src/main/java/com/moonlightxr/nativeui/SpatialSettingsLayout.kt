package com.moonlightxr.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

internal val LocalGroupedParameters = compositionLocalOf { false }
internal val LocalParameterHelpHost = compositionLocalOf<MenuUiState?> { null }
internal val LocalParameterPanelId = compositionLocalOf { "main" }
/** Stable business key when a schema supplies it; translated labels are never identifiers. */
internal val LocalParameterIdentity = compositionLocalOf<String?> { null }

@Composable
internal fun Modifier.recordParameterControl(label: String): Modifier {
    val host = LocalParameterHelpHost.current ?: return this
    val panelId = LocalParameterPanelId.current
    val controlKey = LocalParameterIdentity.current ?: label
    DisposableEffect(host, panelId, controlKey) {
        onDispose { host.parameterControlAnchors.remove("$panelId:$controlKey") }
    }
    return onGloballyPositioned { coordinates ->
        val root = coordinates.findRootCoordinates().size
        val bounds = coordinates.boundsInRoot()
        if (root.width > 0 && root.height > 0) host.parameterControlAnchors["$panelId:$controlKey"] =
            ParameterHelpAnchor(panelId, (bounds.center.x / root.width).coerceIn(0f, 1f),
                (bounds.center.y / root.height).coerceIn(0f, 1f),
                (bounds.width / root.width).coerceIn(0f, 1f),
                (bounds.height / root.height).coerceIn(0f, 1f))
    }
}

/** Position is normalized to the source Compose surface, not its scrollable content. */
data class ParameterHelpAnchor(val panelId: String, val x: Float, val y: Float,
                               val width: Float = 0f, val height: Float = 0f) {
    init { require(panelId.isNotBlank() && panelId.length <= 80 && x in 0f..1f && y in 0f..1f &&
        width in 0f..1f && height in 0f..1f) }
}

/** Anchor the extension's independent help Quad just right of its exclamation mark. */
internal fun parameterHelpQuad(parent: UiQuad, template: UiQuad, anchor: ParameterHelpAnchor): UiQuad {
    val visibleWidth = template.widthMeters * (450f / template.widthPixels).coerceAtMost(1f)
    val visibleHeight = template.heightMeters * (330f / template.heightPixels).coerceAtMost(1f)
    val maxX = ((parent.widthMeters - visibleWidth) / 2f).coerceAtLeast(0f)
    val maxY = ((parent.heightMeters - visibleHeight) / 2f).coerceAtLeast(0f)
    val localX = if (anchor.panelId == "extension")
        (anchor.x - 0.5f) * parent.widthMeters + template.widthMeters / 2f + 0.015f
    else ((anchor.x - 0.5f) * parent.widthMeters - visibleWidth / 2f - 0.025f)
        .coerceIn(-maxX, maxX)
    val localY = if (anchor.panelId == "extension")
        (0.5f - anchor.y) * parent.heightMeters
    else ((0.5f - anchor.y) * parent.heightMeters).coerceIn(-maxY, maxY)
    val local = Vec3(localX, localY, 0.12f)
    return template.copy(pose = parent.pose.copy(position = parent.pose.position +
        parent.pose.orientation.rotate(local)), facing = SpatialFacing.FaceHead, parentId = parent.id)
}

internal object ParameterHelpCatalog {
    val descriptionResources = mapOf(
        "模型 MSAA" to R.string.help_model_msaa,
        "面板位置" to R.string.help_panel_placement,
        "模型渲染倍率" to R.string.help_model_render_scale,
        "运行时锐化" to R.string.help_runtime_sharpening,
        "基本" to R.string.help_basic,
        "多显示器" to R.string.help_multi_display,
        "AI 3D" to R.string.help_ai3d,
        "画面" to R.string.help_picture,
        "控制" to R.string.help_controls,
        "高级" to R.string.help_advanced,
        "透视" to R.string.help_passthrough,
        "背景" to R.string.help_background,
        "SBS" to R.string.help_sbs,
        "曲面" to R.string.help_curve,
        "屏幕预设" to R.string.help_screen_preset,
        "屏幕尺寸" to R.string.help_screen_size,
        "启用 AI 3D" to R.string.help_enable_ai3d,
        "类型" to R.string.help_type,
        "模型" to R.string.help_model,
        "算法" to R.string.help_algorithm,
        "左右眼顺序" to R.string.help_eye_order,
        "分离度" to R.string.help_separation,
        "会聚" to R.string.help_convergence,
        "深度强度" to R.string.help_depth_strength,
        "深度平滑" to R.string.help_depth_smoothing,
        "查看深度图" to R.string.help_depth_preview,
        "深度同步" to R.string.help_depth_sync,
        "AI 优先级" to R.string.help_ai_priority,
        "生成频率上限" to R.string.help_depth_rate_limit,
        "光标类型" to R.string.help_cursor_type,
        "光标稳定" to R.string.help_cursor_smoothing,
        "按键映射" to R.string.help_button_mapping,
        "设备模式" to R.string.help_device_mode,
        "替代操作" to R.string.help_alternate_input,
        "主手" to R.string.help_primary_hand,
        "手柄振动" to R.string.help_controller_haptics,
        "提示音" to R.string.help_audio_feedback,
        "音量" to R.string.help_volume,
        "锐化" to R.string.help_sharpness,
        "模糊" to R.string.help_blur,
        "亮度" to R.string.help_brightness,
        "对比度" to R.string.help_contrast,
        "伽马" to R.string.help_gamma,
        "颜色" to R.string.help_saturation,
        "界面主题" to R.string.help_theme,
        "面板背景" to R.string.help_panel_background,
        "菜单布局" to R.string.help_menu_layout,
        "串流设置" to R.string.help_stream_settings,
        "目标帧率" to R.string.help_stream_fps,
        "分辨率" to R.string.help_stream_resolution,
        "串流码率 Mbps" to R.string.help_bitrate,
        "音频声道" to R.string.help_audio_channels,
        "编码偏好" to R.string.help_codec_preference,
        "空闲断开" to R.string.help_idle_disconnect,
        "自动重连" to R.string.help_auto_reconnect,
        "快速启动" to R.string.help_quick_start
    )
}

class SpatialSettingsGroupScope {
    internal val rows = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) { rows += content }
}

/** A whole group owns one card; even a one-row group retains top/bottom breathing room. */
@Composable
fun SpatialSettingsGroup(title: String, content: SpatialSettingsGroupScope.() -> Unit) {
    val rows = SpatialSettingsGroupScope().apply(content).rows
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        SpatialText(title, Modifier.padding(start = 4.dp), color = SpatialTokens.muted,
            fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
        val shape = RoundedCornerShape(12.dp)
        val light = SpatialTokens.panelBottom.luminance() > 0.5f
        Column(Modifier.fillMaxWidth().clip(shape)
            .background(if (light) SpatialTokens.panelTop
                else SpatialTokens.field.copy(alpha = 0.46f))
            .border(0.7.dp, SpatialTokens.outline.copy(alpha = if (light) 0.22f else 0.38f), shape)
            .padding(vertical = 6.dp)) {
            CompositionLocalProvider(LocalGroupedParameters provides true) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) SpatialDivider(Modifier.padding(horizontal = 10.dp))
                    row()
                }
            }
        }
    }
}

@Composable
internal fun SpatialParameterHelp(label: String) {
    val host = LocalParameterHelpHost.current ?: return
    val description = LocalAdvancedDescriptions.current[label]
        ?: stringResource(ParameterHelpCatalog.descriptionResources[label] ?: return)
    if (description.isBlank()) return
    val helpLabel = stringResource(R.string.ui_parameter_help, spatialString(label))
    val panelId = LocalParameterPanelId.current
    val controlKey = LocalParameterIdentity.current ?: label
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    var anchor by remember(panelId) { mutableStateOf(ParameterHelpAnchor(panelId, 0.5f, 0.5f)) }
    val owner = "$panelId:$controlKey"
    LaunchedEffect(hovered, owner, description) {
        delay(if (hovered) 160 else 280)
        if (hovered) host.showParameterHelp(label, description, anchor, controlKey)
        else host.hideParameterHelp(owner)
    }
    DisposableEffect(owner) { onDispose {
        host.parameterHelpAnchors.remove(owner)
        host.hideParameterHelp(owner)
    } }
    Box(Modifier.size(28.dp).onGloballyPositioned { coordinates ->
        val root = coordinates.findRootCoordinates().size
        val bounds = coordinates.boundsInRoot()
        if (root.width > 0 && root.height > 0) {
            anchor = ParameterHelpAnchor(panelId, (bounds.center.x / root.width).coerceIn(0f, 1f),
                (bounds.center.y / root.height).coerceIn(0f, 1f))
            host.parameterHelpAnchors[owner] = anchor
        }
    }.hoverable(interactions)
        .semantics { contentDescription = helpLabel }
        .clickable(interactionSource = interactions, indication = null, role = Role.Button) {
            host.showParameterHelp(label, description, anchor, controlKey)
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(20.dp).clip(CircleShape)
            .background(SpatialTokens.field.copy(alpha = if (hovered) 1f else 0.5f))
            .border(if (hovered) 1.5.dp else 0.7.dp,
                SpatialTokens.outline.copy(alpha = if (hovered) 1f else 0.55f), CircleShape),
            contentAlignment = Alignment.Center) {
            SpatialText("!", color = SpatialTokens.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun SpatialSettingsDestination(label: String, summary: String, selected: Boolean = false,
                                        enabled: Boolean = true, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val fill = when {
        !enabled -> Color.Transparent
        pressed || selected -> SpatialTokens.accentStrong
        hovered -> SpatialTokens.accent.copy(alpha = 0.22f)
        else -> Color.Transparent
    }
    val emphasized = enabled && (pressed || selected)
    Row(Modifier.fillMaxWidth().recordParameterControl(label).background(fill)
        .hoverable(interactions, enabled = enabled)
        .clickable(interactionSource = interactions, indication = null,
            enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { this.selected = selected }
        .heightIn(min = 44.dp).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SpatialText(label, Modifier.weight(1f), color = if (!enabled) SpatialTokens.disabled
            else if (emphasized) SpatialTokens.onAccent else SpatialTokens.text,
            fontWeight = FontWeight.Medium, maxLines = 1)
        if (summary.isNotEmpty()) SpatialText(summary, color = if (emphasized) SpatialTokens.onAccent
            else SpatialTokens.muted, fontSize = 14.sp, maxLines = 1)
        SpatialText("›", color = if (emphasized) SpatialTokens.onAccent else SpatialTokens.muted, fontSize = 22.sp)
        SpatialParameterHelp(label)
    }
}

@Composable
internal fun ExtensionParameterHelpPanel(help: FloatingPopup.Help) {
    val cardWidth = with(LocalDensity.current) { 450.toDp() }
    val lineColor = SpatialTokens.accent
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2f
            val startX = 8f
            val endX = size.width * (80f / 530f)
            drawLine(lineColor, Offset(startX, y), Offset(endX, y),
                strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(startX, y))
        }
        ParameterHelpPanel(help, Modifier.align(Alignment.CenterEnd).width(cardWidth))
    }
}

@Composable
internal fun ParameterHelpPanel(help: FloatingPopup.Help, modifier: Modifier = Modifier.width(300.dp)) {
    SpatialPanel(modifier, modal = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialText(help.title, color = SpatialTokens.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            SpatialText(help.description, color = SpatialTokens.muted, fontSize = 16.sp, lineHeight = 23.sp)
        }
    }
}
