package com.studiowave.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Original, Live-inspired controls for the StudioWave mobile device surface.
 * It deliberately does not claim that unsupported effects are processing audio.
 */
internal object LivePalette {
    val room = Color(0xFF202225)
    val canvas = Color(0xFF292C30)
    val panel = Color(0xFF3B3E42)
    val raised = Color(0xFF484B50)
    val control = Color(0xFF292B2F)
    val border = Color(0xFF5C6066)
    val text = Color(0xFFF1F2F3)
    val muted = Color(0xFFB5B8BD)
    val aqua = Color(0xFF57D7D0)
    val orange = Color(0xFFFFA15C)
    val yellow = Color(0xFFE9CF3D)
    val pink = Color(0xFFD989B2)
    val green = Color(0xFF91DF70)
    val cyan = Color(0xFF64DAD2)
}

@Composable
internal fun LiveDeviceFrame(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    headerActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .background(LivePalette.canvas, RoundedCornerShape(4.dp))
            .border(1.dp, LivePalette.border, RoundedCornerShape(4.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 28.dp)
                .background(LivePalette.panel, RoundedCornerShape(3.dp))
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = {
                Box(Modifier.size(8.dp).background(LivePalette.orange, RoundedCornerShape(50)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(title.uppercase(), color = LivePalette.text, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle.uppercase(), color = LivePalette.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                headerActions()
            }
        )
        content()
    }
}

@Composable
internal fun LiveStateButton(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = LivePalette.aqua,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.heightIn(min = 28.dp).clickable(enabled = enabled, onClick = onClick),
        color = if (active) accent.copy(alpha = 0.2f) else LivePalette.control,
        contentColor = if (!enabled) LivePalette.muted.copy(alpha = 0.45f) else if (active) accent else LivePalette.text,
        shape = RoundedCornerShape(3.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) accent.copy(alpha = 0.85f) else LivePalette.border)
    ) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
            Text(label.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

/** Touch-first rotary control with a 270-degree value arc, pointer and readout. */
@Composable
internal fun LiveKnob(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 56.dp,
    accent: Color = LivePalette.aqua,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    onDoubleTapReset: (() -> Unit)? = null
) {
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onValueChange)
    val span = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(
            Modifier.size(diameter).pointerInput(range.start, range.endInclusive, enabled) {
                if (!enabled) return@pointerInput
                var start = latestValue
                var dyTotal = 0f
                detectDragGestures(
                    onDragStart = { start = latestValue; dyTotal = 0f },
                    onDrag = { change, drag ->
                        change.consume()
                        dyTotal += drag.y
                        latestChange((start - dyTotal / 135f * span).coerceIn(range.start, range.endInclusive))
                    }
                )
            }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * 0.32f
            val arcR = size.minDimension * 0.405f
            val arcTL = Offset(center.x - arcR, center.y - arcR)
            val arcSize = Size(arcR * 2f, arcR * 2f)
            drawCircle(LivePalette.control, r, center)
            drawCircle(LivePalette.border, r, center, style = Stroke(size.minDimension * 0.025f))
            drawArc(LivePalette.room, 135f, 270f, false, arcTL, arcSize, style = Stroke(size.minDimension * 0.055f))
            drawArc(accent, 135f, 270f * fraction, false, arcTL, arcSize,
                style = Stroke(size.minDimension * 0.065f, cap = StrokeCap.Round))
            for (i in 0..10) {
                val a = Math.toRadians(135.0 + i * 27.0)
                val inner = arcR + size.minDimension * 0.008f
                val outer = inner + if (i % 5 == 0) size.minDimension * 0.045f else size.minDimension * 0.022f
                drawLine(
                    LivePalette.muted.copy(alpha = if (i % 5 == 0) 0.9f else 0.45f),
                    Offset(center.x + cos(a).toFloat() * inner, center.y + sin(a).toFloat() * inner),
                    Offset(center.x + cos(a).toFloat() * outer, center.y + sin(a).toFloat() * outer),
                    strokeWidth = size.minDimension * 0.015f
                )
            }
            val pointer = Math.toRadians(135.0 + 270.0 * fraction)
            drawLine(
                if (enabled) LivePalette.text else LivePalette.muted,
                center,
                Offset(center.x + cos(pointer).toFloat() * r * 0.78f, center.y + sin(pointer).toFloat() * r * 0.78f),
                strokeWidth = size.minDimension * 0.045f,
                cap = StrokeCap.Round
            )
            drawCircle(accent.copy(alpha = if (enabled) 0.95f else 0.25f), size.minDimension * 0.035f, center)
        }
        Text(label.uppercase(), color = LivePalette.muted, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(readout, color = LivePalette.text, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
internal fun LiveSectionLabel(label: String, modifier: Modifier = Modifier, color: Color = LivePalette.orange) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.width(3.dp).height(11.dp).background(color, RoundedCornerShape(1.dp)))
        Text(label.uppercase(), color = color, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun LiveMiniMeter(value: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val clamped = value.coerceIn(0f, 1f)
        drawRoundRect(LivePalette.room, cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        val filled = size.width * clamped
        if (filled > 0f) {
            drawRoundRect(
                when {
                    clamped > 0.92f -> Color(0xFFFF5F69)
                    clamped > 0.72f -> Color(0xFFFFC95E)
                    else -> LivePalette.green
                },
                topLeft = Offset.Zero,
                size = Size(filled, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
    }
}

internal fun liveFormatFloat(value: Float, digits: Int = 1): String =
    "%.${digits}f".format(java.util.Locale.US, value)

internal fun liveDbLabel(linearGain: Float): String {
    val gain = linearGain.coerceAtLeast(0.000001f)
    val db = 20.0 * kotlin.math.log10(gain.toDouble())
    return if (db <= -59.9) "-inf dB" else "${db.roundToInt()} dB"
}
