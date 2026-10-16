package com.studiowave.app
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val border: Color, val success: Color, val warning: Color, val danger: Color,
    val meterLow: Color = accent, val meterMid: Color = warning, val meterHigh: Color = danger
)

internal val LocalStudioWavePalette = compositionLocalOf {
    // Ableton Live-inspired graphite workstation defaults. Keep selection/transport accents
    // vivid while the room, device panels, controls and arrangement canvas remain distinct.
    StudioWavePalette(
        Color(0xFF212121), Color(0xFF1F1F1F), Color(0xFF282828), Color(0xFF181818),
        Color(0xFF2D2D2D), Color(0xFFB2B2B2), Color(0xFF707070), Color(0xFFFF7600),
        Color(0xFFFF7600), Color(0xFF2F2F2F), Color(0xFF141414), Color(0xFF48BD83),
        Color(0xFFF3C928), Color(0xFFEF4444), Color(0xFF48BD83), Color(0xFFF3C928), Color(0xFFEF4444)
    )
}

internal val LocalStudioWaveGridOpacity = compositionLocalOf { 0.72f }

internal fun studioWavePalette(theme: String, tone: String, highContrast: Boolean): StudioWavePalette {
    val base = when (theme) {
        // Compact, neutral Live-inspired surface tiers: room > panel > control > canvas.
        "Live Light" -> StudioWavePalette(Color(0xFFBFC0C2), Color(0xFFEEEEEE), Color(0xFFDADBDD), Color(0xFFCFD0D2),
            Color(0xFFC5C6C8), Color(0xFF202124), Color(0xFF55575A), Color(0xFFB85A00), Color(0xFFB85A00),
            Color(0xFFB3B4B6), Color(0xFF8C8D90), Color(0xFF207C55), Color(0xFF8C6A27), Color(0xFFB11E37), Color(0xFF188D78), Color(0xFFB68B32), Color(0xFFBD334A))
        "Live Mid" -> StudioWavePalette(
            Color(0xFF212121), Color(0xFF1F1F1F), Color(0xFF282828), Color(0xFF181818),
            Color(0xFF2D2D2D), Color(0xFFB2B2B2), Color(0xFF707070), Color(0xFFFF7600),
            Color(0xFFFF7600), Color(0xFF2F2F2F), Color(0xFF141414), Color(0xFF48BD83),
            Color(0xFFF3C928), Color(0xFFEF4444), Color(0xFF48BD83), Color(0xFFF3C928), Color(0xFFEF4444)
        )
        else -> StudioWavePalette(Color(0xFF212121), Color(0xFF1F1F1F), Color(0xFF282828), Color(0xFF181818),
            Color(0xFF2D2D2D), Color(0xFFB2B2B2), Color(0xFF707070), Color(0xFFFF7600), Color(0xFFFF7600),
            Color(0xFF2F2F2F), Color(0xFF141414), Color(0xFF48BD83), Color(0xFFF3C928), Color(0xFFEF4444), Color(0xFF48BD83), Color(0xFFF3C928), Color(0xFFEF4444))
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
    audioStatus: String, audioProfile: String, onAudioProfileChange: (String) -> Unit,
    metronomeEnabled: Boolean, onMetronomeEnabledChange: (Boolean) -> Unit,
    metronomeLevel: Float, onMetronomeLevelChange: (Float) -> Unit,
    parallelPreparation: Boolean, onParallelPreparationChange: (Boolean) -> Unit,
    audioDiagnostics: List<String>, appLog: List<String>,
    onShareLogs: () -> Unit, onClearLogs: () -> Unit,
    currentProjectName: String, onNewProject: (String) -> Unit, onSaveProject: () -> Unit, onOpenProject: () -> Unit
) {
    val p = LocalStudioWavePalette.current
    var newProjectName by remember { mutableStateOf("Untitled Set") }
    var confirmNewProject by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().background(p.room).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.width(164.dp).fillMaxHeight().background(p.panel, RoundedCornerShape(1.dp))
            .border(1.dp, p.border.copy(alpha = 0.9f), RoundedCornerShape(1.dp)).padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("StudioWave", color = p.orange, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f)); Text("SETTINGS", color = p.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = p.border)
            SettingsNav("Interface", "Theme, controls, typography", section == "INTERFACE", p) { onSectionChange("INTERFACE") }
            SettingsNav("Audio", "Output profile and metronome", section == "AUDIO", p) { onSectionChange("AUDIO") }
            SettingsNav("Diagnostics & Logs", "Audio debug and app log", section == "LOG", p) { onSectionChange("LOG") }
            SettingsNav("Project", "New, open and save sets", section == "PROJECT", p) { onSectionChange("PROJECT") }
            Spacer(Modifier.weight(1f))
            Text("STUDIOWAVE • MOBILE DAW", color = p.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(34.dp), contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.control, contentColor = p.text), shape = RoundedCornerShape(1.dp), border = BorderStroke(1.dp, p.border)) {
                Text("DONE", fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(p.canvas, RoundedCornerShape(1.dp))
            .border(1.dp, p.border.copy(alpha = 0.9f), RoundedCornerShape(1.dp)).padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(when (section) { "AUDIO" -> "Audio"; "LOG" -> "Diagnostics & Logs"; "PROJECT" -> "Project Manager"; else -> "Interface & Theme" },
                        color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(when (section) { "AUDIO" -> "Device-aware playback profile, metronome and render preparation"; "LOG" -> "Local live telemetry and exportable application events"; "PROJECT" -> "Create a clean set, or import/export a .sdaw project"; else -> "Consistent Live-inspired color, control and text system" },
                        color = p.muted, fontSize = 9.sp)
                }
                Button(onClick = onClose, modifier = Modifier.size(width = 40.dp, height = 32.dp), contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.control, contentColor = p.text), shape = RoundedCornerShape(1.dp)) { Text("✕", fontWeight = FontWeight.Bold) }
            }
            HorizontalDivider(color = p.border)
            when (section) {
                "AUDIO" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsSection("OUTPUT PROFILE", "Choose a queue-depth target for your device. The profile is applied the next time playback starts; output is stereo PCM16 at 44.1 kHz.", p)
                    SettingsChoiceRow(
                        "Playback stability",
                        "Low latency uses a shorter producer queue; Stable buffers more audio against scheduler jitter.",
                        listOf("AUTO" to "AUTO · DEVICE", "LOW_LATENCY" to "LOW LATENCY", "BALANCED" to "BALANCED", "STABLE" to "STABLE"),
                        audioProfile, onAudioProfileChange, p
                    )
                    SettingsInfoCard("ACTIVE OUTPUT", audioStatus, p)
                    SettingsInfoCard(
                        "PROFILE DETAILS",
                        when (audioProfile) {
                            "AUTO" -> "Device-aware mode: selects queue depth and decoder concurrency from available CPU cores, memory class, and Android low-RAM status. The detected device capabilities are shown above."
                            "LOW_LATENCY" -> "Queue target: 512 frames (~11.6 ms at 44.1 kHz), lower buffering with less tolerance for heavy projects."
                            "STABLE" -> "Queue target: 2048 frames (~46.4 ms at 44.1 kHz), more headroom against scheduler jitter."
                            else -> "Queue target: 1024 frames (~23.2 ms at 44.1 kHz), balanced buffering for most devices."
                        }, p
                    )
                    SettingsSection("METRONOME", "Click is mixed into the master output during playback.", p)
                    SettingsToggleRow("Metronome enabled", "Play a beat-aligned click on the master output.", metronomeEnabled, onMetronomeEnabledChange, p)
                    SettingsSliderRow("Metronome level", "0% = silent; 100% = maximum click level.", metronomeLevel,
                        "${(metronomeLevel * 100).toInt()}%", onMetronomeLevelChange, p)
                    SettingsSection("PREPARATION & PROCESSING", "Multicore option parallelizes sample preparation only; realtime mixing remains a dedicated worker.", p)
                    SettingsToggleRow("Parallel sample preparation", "Use bounded IO workers for independent sources. Newly added audio can prepare while transport keeps running; the realtime mixer stays on its dedicated worker.", parallelPreparation, onParallelPreparationChange, p)
                    SettingsInfoCard("REALTIME MIXER", "The current realtime mixer is single-worker. This switch does not falsely claim parallel DSP; it only parallelizes source preparation.", p)
                }
                "LOG" -> Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Button(onClick = onShareLogs, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.room), shape = RoundedCornerShape(1.dp)) {
                            Text("EXPORT / SHARE LOG", fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                        OutlinedButton(onClick = onClearLogs, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                            border = BorderStroke(1.dp, p.border), shape = RoundedCornerShape(1.dp)) {
                            Text("CLEAR", color = p.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    SettingsSection("AUDIO DEBUG", "Live engine health, performance and memory estimates.", p)
                    audioDiagnostics.forEach { SettingsInfoCard(it.substringBefore("::"), it.substringAfter("::", it), p) }
                    SettingsSection("APPLICATION LOG", "Showing last ${appLog.size.coerceAtMost(250)} of ${appLog.size} retained entries · full retained report is exported.", p)
                    appLog.takeLast(250).forEachIndexed { index, line ->
                        Row(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(1.dp))
                            .border(1.dp, p.border.copy(alpha = 0.8f), RoundedCornerShape(1.dp))
                            .padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.Top) {
                            Text((index + 1).toString().padStart(3, '0'), color = p.accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(7.dp)); Text(line, color = p.text, fontSize = 8.sp)
                        }
                    }
                    SettingsInfoCard("IMPORTANT", "This captures StudioWave-owned events and uncaught app exceptions. Android system Logcat is not accessible to ordinary apps; export this report and send it for analysis.", p)
                }
                "PROJECT" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsSection("PROJECT WORKFLOW", "Dedicated project actions, separate from track and clip editing.", p)
                    SettingsInfoCard("CURRENT SET", "Active project: $currentProjectName\nNew project name: $newProjectName\nThe current set autosaves locally; use SAVE AS to create a shareable .sdaw file.", p)
                    OutlinedTextField(
                        value = newProjectName, onValueChange = { newProjectName = it.take(64) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("New project name", fontSize = 10.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = p.accent, unfocusedBorderColor = p.border,
                            focusedTextColor = p.text, unfocusedTextColor = p.text,
                            focusedLabelColor = p.accent, unfocusedLabelColor = p.muted
                        )
                    )
                    Button(onClick = { confirmNewProject = true },
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.room),
                        shape = RoundedCornerShape(1.dp)) {
                        Text("NEW EMPTY PROJECT", fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onOpenProject, modifier = Modifier.weight(1f).height(38.dp),
                            border = BorderStroke(1.dp, p.border), shape = RoundedCornerShape(1.dp)) {
                            Text("OPEN PROJECT", color = p.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = onSaveProject, modifier = Modifier.weight(1f).height(38.dp),
                            border = BorderStroke(1.dp, p.accent), shape = RoundedCornerShape(1.dp)) {
                            Text("SAVE AS .SDAW", color = p.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    SettingsInfoCard("PROJECT FILE", "A .sdaw file contains the project structure, tracks, clips, MIDI notes and device parameters. External audio URIs remain references to files on your device.", p)
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
                        Column(Modifier.weight(1f).background(p.panel, RoundedCornerShape(1.dp)).border(1.dp, p.border, RoundedCornerShape(1.dp)).padding(8.dp)) { Text("KNOB", color = p.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold); StudioWaveKnobPreview(p) }
                        Column(Modifier.weight(1f).background(p.panel, RoundedCornerShape(1.dp)).border(1.dp, p.border, RoundedCornerShape(1.dp)).padding(8.dp)) { Text("FADER", color = p.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold); StudioWaveFaderPreview(p) }
                        Column(Modifier.weight(1f).background(p.panel, RoundedCornerShape(1.dp)).border(1.dp, p.border, RoundedCornerShape(1.dp)).padding(8.dp)) { Text("BUTTON", color = p.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold); Surface(color = p.control, shape = RoundedCornerShape(1.dp), border = BorderStroke(1.dp, p.border)) { Text("ACTIVE", color = p.accent, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) } }
                    }
                }
            }
        }
    }
    if (confirmNewProject) {
        AlertDialog(
            onDismissRequest = { confirmNewProject = false },
            title = { Text("Create new project?", fontWeight = FontWeight.Black) },
            text = { Text("A new empty set named \"${newProjectName.trim().ifBlank { "Untitled Set" }}\" will replace the active arrangement in memory. Save the current set first if you need to keep it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmNewProject = false
                    onNewProject(newProjectName.trim().ifBlank { "Untitled Set" })
                }) { Text("CREATE", color = p.accent, fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { confirmNewProject = false }) { Text("CANCEL", color = p.text) } },
            containerColor = p.panel,
            titleContentColor = p.text,
            textContentColor = p.muted
        )
    }
}

@Composable internal fun SettingsNav(title: String, subtitle: String, selected: Boolean, p: StudioWavePalette, onClick: () -> Unit) {
    Surface(color = if (selected) p.raised else p.panel, shape = RoundedCornerShape(1.dp),
        border = if (selected) BorderStroke(1.dp, p.accent.copy(alpha = 0.6f)) else BorderStroke(1.dp, p.border.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = if (selected) p.accent else p.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = p.muted, fontSize = 8.sp, maxLines = 2)
        }
    }
}

@Composable internal fun SettingsSection(title: String, subtitle: String, p: StudioWavePalette) {
    Column(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(1.dp)).border(1.dp, p.border.copy(alpha = 0.8f), RoundedCornerShape(1.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = p.orange, fontSize = 9.sp, fontWeight = FontWeight.Black); Text(subtitle, color = p.muted, fontSize = 8.sp)
    }
}

@Composable internal fun SettingsInfoCard(title: String, body: String, p: StudioWavePalette) {
    Column(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(1.dp)).border(1.dp, p.border.copy(alpha = 0.8f), RoundedCornerShape(1.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = p.accent, fontSize = 8.sp, fontWeight = FontWeight.Black); Text(body, color = p.text, fontSize = 9.sp)
    }
}

@Composable internal fun SettingsChoiceRow(title: String, subtitle: String, choices: List<Pair<String, String>>, selected: String, onSelected: (String) -> Unit, p: StudioWavePalette) {
    Column(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(1.dp)).border(1.dp, p.border.copy(alpha = 0.8f), RoundedCornerShape(1.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = p.text, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = p.muted, fontSize = 8.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            choices.forEach { (value, label) ->
                val active = value == selected
                Surface(color = if (active) p.accent.copy(alpha = 0.18f) else p.control, shape = RoundedCornerShape(1.dp),
                    border = BorderStroke(1.dp, if (active) p.orange else p.border), modifier = Modifier.weight(1f).height(31.dp).clickable { onSelected(value) }) {
                    Box(contentAlignment = Alignment.Center) { Text(label, color = if (active) p.accent else p.text, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                }
            }
        }
    }
}

@Composable internal fun SettingsToggleRow(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit, p: StudioWavePalette) {
    Row(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(1.dp)).border(1.dp, p.border.copy(alpha = 0.8f), RoundedCornerShape(1.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, color = p.text, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = p.muted, fontSize = 8.sp) }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable internal fun SettingsSliderRow(title: String, subtitle: String, value: Float, valueText: String, onChange: (Float) -> Unit, p: StudioWavePalette, range: ClosedFloatingPointRange<Float> = 0f..1f) {
    Column(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(1.dp)).border(1.dp, p.border.copy(alpha = 0.8f), RoundedCornerShape(1.dp)).padding(8.dp)) {
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