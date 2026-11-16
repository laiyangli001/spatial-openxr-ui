package com.moonlightxr.nativeui

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/** Stable menu keys are persisted and used by QA. Only their presentation is localized. */
internal object SpatialStringCatalog {
    private val resources = mapOf(
        "面板位置" to R.string.ui_panel_placement,
        "固定" to R.string.ui_panel_fixed,
        "跟随头显" to R.string.ui_panel_follow,
        "模型显示" to R.string.ui_model_display,
        "模型 MSAA" to R.string.ui_model_msaa,
        "模型渲染倍率" to R.string.ui_model_render_scale,
        "运行时锐化" to R.string.ui_runtime_sharpening,
        "运行时支持锐化" to R.string.ui_runtime_sharpening_supported,
        "运行时不支持锐化" to R.string.ui_runtime_sharpening_unsupported,

        "操作指南" to R.string.ui_controller_guide,
        "显示操作指南" to R.string.ui_show_controller_guide,
        "首页" to R.string.ui_home,
        "主页" to R.string.ui_return_home,
        "退出应用" to R.string.ui_exit_app,
        "浏览器" to R.string.ui_browser,
        "视频" to R.string.ui_video,
        "图片" to R.string.ui_gallery,
        "串流" to R.string.ui_streaming,
        "设置" to R.string.ui_settings,
        "Moonlight XR Home" to R.string.ui_home_title,
        "选择要打开的空间内容" to R.string.ui_home_subtitle,
        "Moonlight Streaming for PICO" to R.string.ui_stream_subtitle,
        "连接" to R.string.ui_connect,
        "应用 · 桌面" to R.string.ui_apps_desktop,
        "选择服务器" to R.string.ui_select_server,
        "打开串流" to R.string.ui_open_streaming,
        "基本" to R.string.ui_basic,
        "多显示器" to R.string.ui_multi_display,
        "AI 3D" to R.string.ui_ai3d,
        "画面" to R.string.ui_picture,
        "控制" to R.string.ui_controls,
        "高级" to R.string.ui_advanced,
        "显示与空间" to R.string.ui_display_space,
        "处理与性能" to R.string.ui_processing_performance,
        "输入" to R.string.ui_input,
        "背景、曲面、尺寸" to R.string.ui_screen_summary,
        "背景、串流与外观" to R.string.ui_basic_summary,
        "布局与预设" to R.string.ui_layout_summary,
        "亮度、锐化、模糊" to R.string.ui_picture_summary,
        "模型与立体视图" to R.string.ui_ai_summary,
        "高级参数与性能" to R.string.ui_advanced_summary,
        "手柄与光标" to R.string.ui_controls_summary,
        "显示模式" to R.string.ui_display_mode,
        "透视" to R.string.ui_passthrough,
        "SBS" to R.string.ui_sbs,
        "曲面" to R.string.ui_curve,
        "环境" to R.string.ui_environment,
        "背景" to R.string.ui_background,
        "屏幕布局" to R.string.ui_screen_layout,
        "屏幕预设" to R.string.ui_screen_preset,
        "屏幕尺寸" to R.string.ui_screen_size,
        "显示布局" to R.string.ui_display_layout,
        "未连接" to R.string.ui_not_connected,
        "预设" to R.string.ui_presets,
        "保存预设" to R.string.ui_save_preset,
        "删除预设" to R.string.ui_delete_preset,
        "生成与模型" to R.string.ui_generation_models,
        "启用 AI 3D" to R.string.ui_enable_ai3d,
        "类型" to R.string.ui_type,
        "模型" to R.string.ui_model,
        "算法" to R.string.ui_algorithm,
        "立体视图" to R.string.ui_stereo_view,
        "左右眼顺序" to R.string.ui_eye_order,
        "分离度" to R.string.ui_separation,
        "会聚" to R.string.ui_convergence,
        "深度处理" to R.string.ui_depth_processing,
        "深度强度" to R.string.ui_depth_strength,
        "深度平滑" to R.string.ui_depth_smoothing,
        "查看深度图" to R.string.ui_depth_preview,
        "性能与时序" to R.string.ui_performance_timing,
        "深度同步" to R.string.ui_depth_sync,
        "AI 优先级" to R.string.ui_ai_priority,
        "生成频率上限" to R.string.ui_depth_rate_limit,
        "重置 AI 参数" to R.string.ui_reset_ai,
        "光标" to R.string.ui_cursor,
        "光标类型" to R.string.ui_cursor_type,
        "光标稳定" to R.string.ui_cursor_smoothing,
        "输入方式" to R.string.ui_input_mode,
        "按键映射" to R.string.ui_button_mapping,
        "设备模式" to R.string.ui_device_mode,
        "替代操作" to R.string.ui_alternate_input,
        "主手" to R.string.ui_primary_hand,
        "反馈与音频" to R.string.ui_feedback_audio,
        "手柄振动" to R.string.ui_controller_haptics,
        "提示音" to R.string.ui_audio_feedback,
        "音量" to R.string.ui_volume,
        "图像细节" to R.string.ui_image_detail,
        "锐化" to R.string.ui_sharpness,
        "模糊" to R.string.ui_blur,
        "色彩与明暗" to R.string.ui_color_tone,
        "亮度" to R.string.ui_brightness,
        "对比度" to R.string.ui_contrast,
        "伽马" to R.string.ui_gamma,
        "颜色" to R.string.ui_saturation,
        "重置画面参数" to R.string.ui_reset_picture,
        "外观" to R.string.ui_appearance,
        "界面主题" to R.string.ui_theme,
        "面板背景" to R.string.ui_panel_background,
        "菜单布局" to R.string.ui_menu_layout,
        "月光蓝" to R.string.ui_moonlight_blue,
        "透明 · 雾面" to R.string.ui_frosted,
        "不透明" to R.string.ui_opaque,
        "性能" to R.string.ui_performance,
        "串流设置" to R.string.ui_stream_settings,
        "目标帧率" to R.string.ui_stream_fps,
        "分辨率" to R.string.ui_stream_resolution,
        "音频声道" to R.string.ui_audio_channels,
        "编码偏好" to R.string.ui_codec_preference,
        "空闲断开" to R.string.ui_idle_disconnect,
        "自动重连" to R.string.ui_auto_reconnect,
        "快速启动" to R.string.ui_quick_start,
        "串流码率 Mbps" to R.string.ui_bitrate,
        "重置参数" to R.string.ui_reset_settings,
        "Black" to R.string.ui_black,
        "HDR 极光" to R.string.ui_hdr_aurora,
        "HDR 云海" to R.string.ui_hdr_cloud,
        "HDR 卡通" to R.string.ui_hdr_cartoon,
        "HDR 湖畔" to R.string.ui_hdr_lakeside,
        "HDR 天空" to R.string.ui_hdr_skycloud,
        "HDR 太空" to R.string.ui_hdr_space,
        "六边形菜单" to R.string.ui_hex_menu,
        "平面菜单" to R.string.ui_flat_menu,
        "近距离" to R.string.ui_near_distance,
        "标准距离" to R.string.ui_standard_distance,
        "影院距离" to R.string.ui_cinema_distance,
        "远距离" to R.string.ui_far_distance,
        "自动" to R.string.ui_automatic,
        "平移算法" to R.string.ui_direct_shift,
        "视差算法" to R.string.ui_parallax_algorithm,
        "Distill-224" to R.string.ui_ai_distill_224,
        "ZipDepth-384" to R.string.ui_ai_zipdepth_384,
        "ZipDepth-512" to R.string.ui_ai_zipdepth_512,
        "Off" to R.string.ui_off,
        "Half" to R.string.ui_half,
        "Full" to R.string.ui_full,
        "Half-SBS" to R.string.ui_half_sbs,
        "Full-SBS" to R.string.ui_full_sbs,
        "Flat" to R.string.ui_flat,
        "Slight" to R.string.ui_slight,
        "Curved" to R.string.ui_curved,
        "Still" to R.string.ui_still,
        "Movie" to R.string.ui_movie,
        "Normal" to R.string.ui_normal,
        "Crossed" to R.string.ui_crossed,
        "Gaussian" to R.string.ui_gaussian,
        "Color Guided" to R.string.ui_color_guided,
        "Boost" to R.string.ui_boost,
        "Auto" to R.string.ui_auto,
        "Stereo" to R.string.ui_stereo,
        "5.1" to R.string.ui_channels_51,
        "7.1" to R.string.ui_channels_71,
        "H.264 preferred" to R.string.ui_codec_h264,
        "HEVC preferred (if supported)" to R.string.ui_codec_hevc,
        "Circle" to R.string.ui_circle,
        "Pointer" to R.string.ui_pointer,
        "Low" to R.string.ui_low,
        "High" to R.string.ui_high,
        "PAD-HAND" to R.string.ui_pad_hand,
        "PAD-ABXY" to R.string.ui_pad_abxy,
        "KBM" to R.string.ui_kbm,
        "Head" to R.string.ui_head,
        "Tilt" to R.string.ui_tilt,
        "None" to R.string.ui_none,
        "Right" to R.string.ui_right,
        "Left" to R.string.ui_left,
        "取消" to R.string.ui_cancel,
        "确定" to R.string.ui_confirm,
        "关闭" to R.string.ui_close,
        "知道了" to R.string.ui_got_it,
        "确认操作" to R.string.ui_confirm_action,
        "请选择要执行的操作。" to R.string.ui_confirm_message,
        "退出 Moonlight XR？" to R.string.ui_exit_title,
        "确认后将关闭应用，返回头显主页。" to R.string.ui_exit_message,
        "确认重置" to R.string.ui_confirm_reset,
        "全部参数" to R.string.ui_all_settings,
        "AI 3D 参数" to R.string.ui_ai_settings,
        "画面参数" to R.string.ui_picture_settings,
        "关" to R.string.ui_disabled,
        "半幅" to R.string.ui_half_label,
        "全幅" to R.string.ui_full_label,
        "平面" to R.string.ui_flat_label,
        "轻曲" to R.string.ui_slight_label,
        "空间弹窗" to R.string.ui_spatial_popup,
        "独立 Quad · 可点击" to R.string.ui_independent_quad,
        "空间侧窗" to R.string.ui_spatial_subwindow,
        "同一 XR 帧提交" to R.string.ui_same_xr_frame,
        "空间附着信息" to R.string.ui_spatial_augment,
        "搜索" to R.string.ui_search,
        "复制" to R.string.ui_copy,
        "剪切" to R.string.ui_cut,
        "粘贴" to R.string.ui_paste,
        "全选" to R.string.ui_select_all,
        "时间" to R.string.ui_time,
        "小时" to R.string.ui_hours,
        "分钟" to R.string.ui_minutes,
        "秒" to R.string.ui_seconds
    )

    @StringRes fun resourceFor(key: String): Int? = resources[key]
}

@Composable
internal fun spatialString(key: String): String =
    SpatialStringCatalog.resourceFor(key)?.let { stringResource(it) } ?: key

/** Resource-backed text shared by app-owned Spatial UI controls. Caller content remains valid. */
@Composable
internal fun SpatialText(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, maxLines: Int = Int.MAX_VALUE,
    textDecoration: TextDecoration? = null, style: TextStyle = LocalTextStyle.current,
) {
    Text(spatialString(text), modifier, color = color, fontSize = fontSize,
        fontWeight = fontWeight, textAlign = textAlign, lineHeight = lineHeight,
        overflow = overflow, maxLines = maxLines, textDecoration = textDecoration, style = style)
}

/** QA may select a locale for every offscreen surface without changing the headset language. */
@Composable
internal fun SpatialLocaleOverride(languageTag: String?, content: @Composable () -> Unit) {
    if (languageTag == null) {
        content()
        return
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(context, configuration, languageTag) {
        context.createConfigurationContext(Configuration(configuration).apply {
            setLocale(Locale.forLanguageTag(languageTag))
        })
    }
    CompositionLocalProvider(LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration, content = content)
}
