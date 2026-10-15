package com.studiowave.app
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke

/** A single source of truth for StudioWave's Live-inspired surface and control colors. */
internal data class StudioWavePalette(
    val room: Color, val canvas: Color, val panel: Color, val raised: Color, val control: Color,
    val text: Color, val muted: Color, val accent: Color, val orange: Color, val grid: Color,
    val border: Color, val success: Color, val warning: Color, val danger: Color
)

internal val LocalStudioWavePalette = compositionLocalOf {
    // Shared Live-inspired neutral workstation palette: graphite room, raised mixer strips,
    // soft aqua focus, warm orange selection, and clear green/red transport feedback.
    StudioWavePalette(Color(0xFF202124), Color(0xFF2B2D30), Color(0xFF383A3D), Color(0xFF414347),
        Color(0xFF303236), Color(0xFFE7E7E7), Color(0xFFAAAAAA), Color(0xFF65D9D0), Color(0xFFFF9A76),
        Color(0xFF4A4C50), Color(0xFF55575B), Color(0xFF6EE7A0), Color(0xFFFFD36E), Color(0xFFFF647C))
}

internal val LocalStudioWaveGridOpacity = compositionLocalOf { 0.72f }

internal fun studioWavePalette(theme: String, tone: String, highContrast: Boolean): StudioWavePalette {
    val base = when (theme) {
        // Compact, neutral Live-inspired surface tiers: room > panel > control > canvas.
        "Live Light" -> StudioWavePalette(Color(0xFFBFC0C2), Color(0xFFEEEEEE), Color(0xFFDADBDD), Color(0xFFCFD0D2),
            Color(0xFFC5C6C8), Color(0xFF202124), Color(0xFF55575A), Color(0xFF168F85), Color(0xFFB7654A),
            Color(0xFFB3B4B6), Color(0xFF8C8D90), Color(0xFF207C55), Color(0xFF8C6A27), Color(0xFFB11E37))
        "Live Mid" -> StudioWavePalette(
            Color(0xFF323436), Color(0xFF232426), Color(0xFF36383B), Color(0xFF414347),
            Color(0xFF2B2D30), Color(0xFFF0F1F2), Color(0xFFB7BABD), Color(0xFF6EDBD2),
            Color(0xFFFF936F), Color(0xFF4D5054), Color(0xFF606368), Color(0xFF65D99B),
            Color(0xFFFFCA66), Color(0xFFFF5F75)
        )
        else -> StudioWavePalette(Color(0xFF1B1C1E), Color(0xFF232527), Color(0xFF303235), Color(0xFF3D3F42),
            Color(0xFF292B2E), Color(0xFFE7E8E9), Color(0xFFAAADB1), Color(0xFF65D9D0), Color(0xFFFF9877),
            Color(0xFF484B4F), Color(0xFF575A5E), Color(0xFF6EE7A0), Color(0xFFFFD36E), Color(0xFFFF647C))
    }
    val toned = when (tone) {
        "Warm" -> base.copy(accent = if (theme == "Live Light") Color(0xFF98602C) else Color(0xFFFFB15E), orange = Color(0xFFFF8B52))
        "Cool" -> base.copy(accent = if (theme == "Live Light") Color(0xFF176F99) else Color(0xFF57BDEB), orange = if (theme == "Live Light") Color(0xFFBC5938) else Color(0xFFFF9474))
        else -> base
    }
    return if (highContrast) toned.copy(text = if (theme == "Live Light") Color.Black else Color.White,
        muted = if (theme == "Live Light") Color(0xFF252A31) else Color(0xFFE0E4EA),
        grid = if (theme == "Live Light") Color(0xFF8A9099) else Color(0xFF727984),
        border = if (theme == "Live Light") Color(0xFF777E88) else Color(0xFF737A85)) else toned
}

@Composable
internal fun StudioWaveSettingsScreen(
    section: String, onSectionChange: (String) -> Unit, onClose: () -> Unit,
    themeName: String, onThemeChange: (String) -> Unit, paletteTone: String, onPaletteToneChange: (String) -> Unit,
    highContrast: Boolean, onHighContrastChange: (Boolean) -> Unit, gridOpacity: Float, onGridOpacityChange: (Float) -> Unit,
    uiScale: Float, onUiScaleChange: (Float) -> Unit,
    audioStatus: String, interfaceLog: List<String>
) {
    val p = LocalStudioWavePalette.current
    Row(Modifier.fillMaxSize().background(p.room).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.width(164.dp).fillMaxHeight().background(p.panel, RoundedCornerShape(7.dp)).padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("StudioWave", color = p.orange, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f)); Text("SETTINGS", color = p.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = p.border)
            SettingsNav("Interface", "Theme, controls, typography", section == "INTERFACE", p) { onSectionChange("INTERFACE") }
            SettingsNav("Audio", "Output preferences", section == "AUDIO", p) { onSectionChange("AUDIO") }
            SettingsNav("Interface Log", "Current UI and engine state", section == "LOG", p) { onSectionChange("LOG") }
            Spacer(Modifier.weight(1f))
            Text("STUDIOWAVE • MOBILE DAW", color = p.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(34.dp), contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.control, contentColor = p.text), shape = RoundedCornerShape(5.dp)) {
                Text("DONE", fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(p.canvas, RoundedCornerShape(7.dp)).padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(when (section) { "AUDIO" -> "Audio"; "LOG" -> "Interface Log"; else -> "Interface & Theme" },
                        color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(when (section) { "AUDIO" -> "Engine-aware information and saved output preferences"; "LOG" -> "Diagnostic snapshot for the current session"; else -> "Consistent Live-inspired color, control and text system" },
                        color = p.muted, fontSize = 9.sp)
                }
                Button(onClick = onClose, modifier = Modifier.size(width = 40.dp, height = 32.dp), contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.control, contentColor = p.text), shape = RoundedCornerShape(5.dp)) { Text("✕", fontWeight = FontWeight.Bold) }
            }
            HorizontalDivider(color = p.border)
            when (section) {
                "AUDIO" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsSection("OUTPUT", "Read-only settings from the active playback implementation.", p)
                    SettingsInfoCard(
                        "ACTIVE FORMAT",
                        "Stereo PCM16 · 44.1 kHz output · 256-frame mixer blocks. The Android AudioTrack buffer uses the device minimum or at least 4096 bytes.",
                        p
                    )
                    SettingsInfoCard("ENGINE STATUS", audioStatus, p)
                    SettingsInfoCard(
                        "CONFIGURATION",
                        "Sample rate, output mode and buffer size are not user-configurable in this engine version, so no inactive selection controls are shown.",
                        p
                    )
                }
                "LOG" -> Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SettingsInfoCard("SESSION DIAGNOSTICS", "Read-only snapshot. Values are not uploaded.", p)
                    interfaceLog.forEachIndexed { index, line ->
                        Row(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text((index + 1).toString().padStart(2, '0'), color = p.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp)); Text(line, color = p.text, fontSize = 9.sp, maxLines = 2)
                        }
                    }
                }
                else -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsSection("THEME", "Inspired by Live's Theme & Colors settings.", p)
                    SettingsChoiceRow("Color scheme", "One palette drives panel, text, grid and active controls.", listOf("Live Dark" to "Live Dark", "Live Mid" to "Live Mid", "Live Light" to "Live Light"), themeName, onThemeChange, p)
                    SettingsChoiceRow("Palette tone", "Warm, cool or neutral accent tone.", listOf("Neutral" to "Neutral", "Warm" to "Warm", "Cool" to "Cool"), paletteTone, onPaletteToneChange, p)
                    SettingsToggleRow("High contrast", "Increase contrast for labels, borders and grid.", highContrast, onHighContrastChange, p)
                    SettingsSliderRow("Grid opacity", "Applied to Arrangement and Piano Roll grid lines.", gridOpacity, "${(gridOpacity * 100).toInt()}%", onGridOpacityChange, p)
                    SettingsSliderRow("UI scale", "Scales workspace dimensions and controls.", uiScale, "${(uiScale * 100).toInt()}%", onUiScaleChange, p, 0.6f..1.2f)
                    SettingsSection("CONTROL SURFACE", "Shared palette for knobs, faders and buttons.", p)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).background(p.panel, RoundedCornerShape(6.dp)).padding(8.dp)) { Text("KNOB", color = p.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold); StudioWaveKnobPreview(p) }
                        Column(Modifier.weight(1f).background(p.panel, RoundedCornerShape(6.dp)).padding(8.dp)) { Text("FADER", color = p.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold); StudioWaveFaderPreview(p) }
                        Column(Modifier.weight(1f).background(p.panel, RoundedCornerShape(6.dp)).padding(8.dp)) { Text("BUTTON", color = p.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold); Surface(color = p.control, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, p.border)) { Text("ACTIVE", color = p.accent, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) } }
                    }
                }
            }
        }
    }
}

@Composable internal fun SettingsNav(title: String, subtitle: String, selected: Boolean, p: StudioWavePalette, onClick: () -> Unit) {
    Surface(color = if (selected) p.raised else p.panel, shape = RoundedCornerShape(5.dp),
        border = if (selected) BorderStroke(1.dp, p.accent.copy(alpha = 0.6f)) else BorderStroke(1.dp, p.border.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = if (selected) p.accent else p.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = p.muted, fontSize = 8.sp, maxLines = 2)
        }
    }
}

@Composable internal fun SettingsSection(title: String, subtitle: String, p: StudioWavePalette) {
    Column(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(5.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = p.orange, fontSize = 9.sp, fontWeight = FontWeight.Black); Text(subtitle, color = p.muted, fontSize = 8.sp)
    }
}

@Composable internal fun SettingsInfoCard(title: String, body: String, p: StudioWavePalette) {
    Column(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(5.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = p.accent, fontSize = 8.sp, fontWeight = FontWeight.Black); Text(body, color = p.text, fontSize = 9.sp)
    }
}

@Composable internal fun SettingsChoiceRow(title: String, subtitle: String, choices: List<Pair<String, String>>, selected: String, onSelected: (String) -> Unit, p: StudioWavePalette) {
    Column(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(5.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = p.text, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = p.muted, fontSize = 8.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            choices.forEach { (value, label) ->
                val active = value == selected
                Surface(color = if (active) p.accent.copy(alpha = 0.18f) else p.control, shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, if (active) p.accent else p.border), modifier = Modifier.weight(1f).height(31.dp).clickable { onSelected(value) }) {
                    Box(contentAlignment = Alignment.Center) { Text(label, color = if (active) p.accent else p.text, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                }
            }
        }
    }
}

@Composable internal fun SettingsToggleRow(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit, p: StudioWavePalette) {
    Row(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(5.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, color = p.text, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = p.muted, fontSize = 8.sp) }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable internal fun SettingsSliderRow(title: String, subtitle: String, value: Float, valueText: String, onChange: (Float) -> Unit, p: StudioWavePalette, range: ClosedFloatingPointRange<Float> = 0f..1f) {
    Column(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(5.dp)).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(title, color = p.text, fontSize = 10.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text(valueText, color = p.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        Text(subtitle, color = p.muted, fontSize = 8.sp)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.fillMaxWidth().height(28.dp))
    }
}

@Composable internal fun StudioWaveKnobPreview(p: StudioWavePalette) {
    Canvas(Modifier.size(62.dp)) {
        val radius = size.minDimension * 0.37f; val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(p.control, radius, center); drawCircle(p.border, radius, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.3.dp.toPx()))
        val start = 135f; val sweep = 270f * 0.72f
        val arcTop = Offset(center.x - radius - 3.dp.toPx(), center.y - radius - 3.dp.toPx()); val arcSize = Size((radius + 3.dp.toPx()) * 2f, (radius + 3.dp.toPx()) * 2f)
        drawArc(p.border, start, 270f, false, arcTop, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        drawArc(p.accent, start, sweep, false, arcTop, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        val angle = Math.toRadians((135f + sweep).toDouble())
        drawLine(p.text, center, Offset(center.x + kotlin.math.cos(angle).toFloat() * radius * 0.62f, center.y + kotlin.math.sin(angle).toFloat() * radius * 0.62f), 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable internal fun StudioWaveFaderPreview(p: StudioWavePalette) {
    Canvas(Modifier.fillMaxWidth().height(62.dp)) {
        val leftRail = size.width * 0.40f
        val railWidth = (size.width * 0.20f).coerceAtLeast(5.dp.toPx())
        val railTop = 5.dp.toPx()
        val railBottom = size.height - 5.dp.toPx()
        for (tick in 0..8) {
            val y = railTop + (railBottom - railTop) * tick / 8f
            drawLine(p.grid.copy(alpha = 0.7f), Offset(size.width * 0.08f, y), Offset(size.width * 0.34f, y), 1.dp.toPx())
        }
        drawRoundRect(p.control, Offset(leftRail, railTop), Size(railWidth, railBottom - railTop),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(railWidth / 2f))
        val fraction = 0.62f
        val thumbY = railBottom - fraction * (railBottom - railTop)
        drawRoundRect(p.accent.copy(alpha = 0.8f), Offset(leftRail, thumbY), Size(railWidth, railBottom - thumbY),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(railWidth / 2f))
        drawRoundRect(p.text, Offset(size.width * 0.16f, thumbY - 3.dp.toPx()), Size(size.width * 0.68f, 6.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
    }
}