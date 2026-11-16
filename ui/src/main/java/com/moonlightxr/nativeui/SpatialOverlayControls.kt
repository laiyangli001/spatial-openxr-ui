package com.moonlightxr.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.coroutines.delay

/** These draw transparent Compose content; the caller decides whether to place it on a front Quad. */
@Composable
fun SpatialAlertDialog(
    visible: Boolean, title: String, message: String, onConfirm: () -> Unit,
    onDismiss: () -> Unit, modifier: Modifier = Modifier,
    confirmLabel: String = "确定", dismissLabel: String? = "取消",
) {
    if (!visible) return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SpatialPanel(Modifier.widthIn(min = 300.dp, max = 480.dp), modal = true) {
            Column(Modifier.padding(SpatialTokens.panelPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SpatialText(title, color = SpatialTokens.text, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold)
                SpatialText(message, color = SpatialTokens.muted, fontSize = 16.sp)
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (dismissLabel != null) SpatialButton(dismissLabel, onDismiss,
                        Modifier.width(104.dp).recordParameterControl(dismissLabel),
                        size = SpatialButtonSize.Small)
                    SpatialButton(confirmLabel, onConfirm,
                        Modifier.width(if (dismissLabel == null) 104.dp else 132.dp)
                            .recordParameterControl(confirmLabel),
                        size = SpatialButtonSize.Small, variant = SpatialButtonVariant.Primary)
                }
            }
        }
    }
}

@Composable
fun SpatialToolbar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    SpatialPanel(modifier) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
fun SpatialSheet(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier,
                 content: @Composable () -> Unit) {
    if (!visible) return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SpatialPanel(Modifier.fillMaxWidth(), modal = true) {
            Column(Modifier.padding(SpatialTokens.panelPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SpatialButton("关闭", onDismiss, compact = true)
                }
                content()
            }
        }
    }
}

@Composable
fun SpatialSnack(visible: Boolean, message: String, modifier: Modifier = Modifier,
                 actionLabel: String? = null, onAction: (() -> Unit)? = null,
                 durationMs: Long? = null, onDismiss: (() -> Unit)? = null) {
    if (!visible) return
    LaunchedEffect(visible, message, durationMs) {
        if (durationMs != null && onDismiss != null) {
            delay(durationMs.coerceAtLeast(0))
            onDismiss()
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SpatialPanel(modal = true) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SpatialText(message, color = SpatialTokens.text)
                if (actionLabel != null && onAction != null) SpatialButton(actionLabel, onAction, compact = true)
            }
        }
    }
}

@Composable
fun SpatialMenu(visible: Boolean, options: List<String>, selected: String?,
                onSelect: (String) -> Unit, onDismiss: () -> Unit,
                modifier: Modifier = Modifier) {
    if (!visible) return
    SpatialPanel(modifier.widthIn(min = 180.dp, max = 320.dp), modal = true) {
        Column(Modifier.heightIn(max = 400.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                SpatialOption(option, selected == option, { onSelect(option); onDismiss() }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun SpatialCoachmark(visible: Boolean, message: String, modifier: Modifier = Modifier,
                     onDismiss: (() -> Unit)? = null) {
    if (!visible) return
    SpatialPanel(modifier.widthIn(max = 320.dp), modal = true) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialText(message, color = SpatialTokens.text)
            if (onDismiss != null) SpatialButton("知道了", onDismiss, compact = true)
        }
    }
}

@Composable
fun SpatialTabBar(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
                  modifier: Modifier = Modifier) {
    SpatialSegmentControl(tabs, selectedIndex, onSelect, modifier)
}

/** App-owned calendar picker, without PICO's system picker runtime. */
@Composable
fun SpatialDatePicker(value: LocalDate, onValueChange: (LocalDate) -> Unit,
                      modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = DateTimeFormatter.ofPattern(stringResource(R.string.ui_calendar_month_pattern), locale)
    var shownMonth by remember(value.year, value.monthValue) {
        mutableStateOf(YearMonth.from(value))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialButton("‹", { shownMonth = shownMonth.minusMonths(1) }, compact = true)
            SpatialText(shownMonth.format(monthFormat), color = SpatialTokens.text,
                fontWeight = FontWeight.Bold, modifier = Modifier.width(144.dp))
            SpatialButton("›", { shownMonth = shownMonth.plusMonths(1) }, compact = true)
        }
        val weekNames = DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, locale) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            weekNames.forEach { SpatialText(it, Modifier.width(40.dp), color = SpatialTokens.muted) }
        }
        val startOffset = shownMonth.atDay(1).dayOfWeek.value - 1
        repeat(6) { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { weekday ->
                    val day = week * 7 + weekday - startOffset + 1
                    if (day in 1..shownMonth.lengthOfMonth()) {
                        SpatialButton(day.toString(), { onValueChange(shownMonth.atDay(day)) },
                            Modifier.width(40.dp), compact = true,
                            selected = shownMonth.atDay(day) == value)
                    } else Box(Modifier.width(40.dp).heightIn(min = 40.dp))
                }
            }
        }
    }
}

@Composable
fun SpatialTimePicker(value: LocalTime, onValueChange: (LocalTime) -> Unit,
                      modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SpatialText("时间", color = SpatialTokens.text, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpatialWheelValue(value.hour, 0..23, { onValueChange(value.withHour(it)) }, "小时")
            SpatialText(":", color = SpatialTokens.text)
            SpatialWheelValue(value.minute, 0..59, { onValueChange(value.withMinute(it)) }, "分钟")
            SpatialText(":", color = SpatialTokens.text)
            SpatialWheelValue(value.second, 0..59, { onValueChange(value.withSecond(it)) }, "秒")
        }
    }
}

@Composable
private fun SpatialWheelValue(value: Int, range: IntRange, onValueChange: (Int) -> Unit,
                              label: String) {
    fun wrap(n: Int): Int = when {
        n < range.first -> range.last
        n > range.last -> range.first
        else -> n
    }
    val currentValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    Column(Modifier.width(72.dp).pointerInput(range) {
        var deltaPx = 0f
        var dragValue = currentValue
        detectVerticalDragGestures(
            onDragStart = { deltaPx = 0f; dragValue = currentValue },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                deltaPx += dragAmount
                val stepPx = 28.dp.toPx()
                while (kotlin.math.abs(deltaPx) >= stepPx) {
                    val direction = if (deltaPx < 0f) 1 else -1
                    dragValue = wrap(dragValue + direction)
                    latestOnValueChange(dragValue)
                    deltaPx += if (direction == 1) stepPx else -stepPx
                }
            },
        )
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        SpatialText(label, color = SpatialTokens.muted)
        SpatialButton(wrap(value - 1).toString().padStart(2, '0'),
            { onValueChange(wrap(value - 1)) }, Modifier.fillMaxWidth(), compact = true)
        SpatialButton(value.toString().padStart(2, '0'), {}, Modifier.fillMaxWidth(),
            selected = true, compact = true)
        SpatialButton(wrap(value + 1).toString().padStart(2, '0'),
            { onValueChange(wrap(value + 1)) }, Modifier.fillMaxWidth(), compact = true)
    }
}
