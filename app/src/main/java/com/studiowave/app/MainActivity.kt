package com.studiowave.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.studiowave.app.audio.NativeAudioEngine
import com.studiowave.app.data.StudioProject
import com.studiowave.app.data.StudioTrack

private val Room = Color(0xFF141517)
private val CanvasSurface = Color(0xFF1E2024)
private val Raised = Color(0xFF282B30)
private val MutedText = Color(0xFFA4A8B0)
private val Mint = Color(0xFF00D2B4)
private val Green = Color(0xFF22C55E)
private val Red = Color(0xFFEF4444)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.navigationBarColor = android.graphics.Color.rgb(20, 21, 23)
        setContent { StudioWaveApp() }
    }
}

@Composable
private fun StudioWaveApp() {
    val engine = remember { NativeAudioEngine() }
    var playing by remember { mutableStateOf(false) }
    var bpm by remember { mutableIntStateOf(120) }
    var master by remember { mutableFloatStateOf(0.72f) }
    var selectedWorkspace by remember { mutableStateOf("ARRANGEMENT") }
    var showDock by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var scrollBars by remember { mutableFloatStateOf(0f) }
    var scrollTracks by remember { mutableFloatStateOf(0f) }
    var verticalZoom by remember { mutableFloatStateOf(1f) }
    var project by remember { mutableStateOf(StudioProject()) }
    var editorTrackId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(engine) { onDispose { engine.close() } }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Mint, secondary = Color(0xFFFF7644), background = Room,
        surface = Raised, onPrimary = Room, onBackground = Color(0xFFF8FAFC),
        onSurface = Color(0xFFF8FAFC)
    )) {
        Surface(Modifier.fillMaxSize(), color = Room) {
            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp)) {
                TransportBar(playing, bpm, master, { bpm = it.coerceIn(40, 240) }, { master = it },
                    onPlay = {
                        if (!playing) {
                            try { engine.setTone(55f, 0.025f, 1); engine.start(); playing = true }
                            catch (_: Exception) { playing = false }
                        } else { engine.stop(); playing = false }
                    },
                    onStop = { engine.stop(); playing = false })
                Spacer(Modifier.height(3.dp))
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    WorkspaceRail(selectedWorkspace) { selectedWorkspace = it }
                    Spacer(Modifier.width(4.dp))
                    when (selectedWorkspace) {
                        "MIXER" -> MixerWorkspace(project.tracks)
                        "PIANO ROLL" -> PianoRollWorkspace()
                        "BROWSER" -> BrowserWorkspace()
                        "DRUM SEQUENCER" -> DrumSequencerWorkspace(project.tracks.firstOrNull { it.id == editorTrackId }, onBack = { selectedWorkspace = "ARRANGEMENT" })
                        "AUDIO EDITOR" -> AudioEditorWorkspace(project.tracks.firstOrNull { it.id == editorTrackId }, onBack = { selectedWorkspace = "ARRANGEMENT" })
                        else -> ArrangementWorkspace(project, { project = it }, zoom, { zoom = it.coerceIn(0.55f, 2.8f) }, scrollBars, { scrollBars = it.coerceAtLeast(0f) }, verticalZoom, { verticalZoom = it.coerceIn(0.65f, 1.8f) }, scrollTracks, { scrollTracks = it.coerceAtLeast(0f) }, { workspace, trackId -> editorTrackId = trackId; selectedWorkspace = workspace })
                    }
                }
                if (showDock) {
                    Spacer(Modifier.height(3.dp))
                    DeviceDock(playing, { showDock = false }, { freq -> engine.setTone(freq, 0.07f, 0) })
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SmallAction("SHOW DEVICES") { showDock = true } }
                }
            }
        }
    }
}

@Composable
private fun TransportBar(
    playing: Boolean, bpm: Int, master: Float,
    onBpm: (Int) -> Unit, onMaster: (Float) -> Unit, onPlay: () -> Unit, onStop: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(Raised, RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("StudioWave", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Mint, maxLines = 1)
        Text("NEON", fontSize = 9.sp, color = MutedText, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("BPM", color = MutedText, fontSize = 9.sp)
        SmallAction("−") { onBpm(bpm - 1) }
        Text(bpm.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        SmallAction("+") { onBpm(bpm + 1) }
        Text("4/4", fontSize = 10.sp, color = MutedText)
        Text("BAR 001:01", fontSize = 9.sp, color = Color(0xFFDADEE5), maxLines = 1)
        SmallIconButton(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) Green else Mint, onPlay)
        SmallIconButton(Icons.Default.Stop, MutedText, onStop)
        SmallIconButton(Icons.Default.Mic, Red) { }
        SmallIconButton(Icons.Default.Undo, MutedText) { }
        SmallIconButton(Icons.Default.Redo, MutedText) { }
        Spacer(Modifier.weight(1f))
        Text("MASTER", color = MutedText, fontSize = 9.sp)
        Slider(value = master, onValueChange = onMaster, modifier = Modifier.width(75.dp).height(24.dp), valueRange = 0f..1f)
    }
}

@Composable
private fun WorkspaceRail(selected: String, onSelect: (String) -> Unit) {
    Column(
        Modifier.width(57.dp).fillMaxHeight().background(Raised, RoundedCornerShape(7.dp)).padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RailButton("ARR", selected == "ARRANGEMENT", Icons.Default.GraphicEq) { onSelect("ARRANGEMENT") }
        RailButton("KEYS", selected == "PIANO ROLL", Icons.Default.Keyboard) { onSelect("PIANO ROLL") }
        RailButton("MIX", selected == "MIXER", Icons.Default.Tune) { onSelect("MIXER") }
        RailButton("FILES", selected == "BROWSER", Icons.Default.LibraryMusic) { onSelect("BROWSER") }
    }
}

@Composable
private fun RailButton(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(45.dp), shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(2.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) Color(0xFF18443F) else Color(0xFF202226),
            contentColor = if (selected) Mint else MutedText)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(17.dp))
            Text(label, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun ArrangementWorkspace(project: StudioProject, onProjectChange: (StudioProject) -> Unit, zoom: Float, onZoom: (Float) -> Unit, scrollBars: Float, onScroll: (Float) -> Unit, verticalZoom: Float, onVerticalZoom: (Float) -> Unit, scrollTracks: Float, onScrollTracks: (Float) -> Unit, onOpenEditor: (String, String?) -> Unit) {
    var selectedClip by remember { mutableStateOf<String?>(null) }
    var showAddTrack by remember { mutableStateOf(false) }
    var clipMenu by remember { mutableStateOf<String?>(null) }
    var showTimelineMenu by remember { mutableStateOf(false) }
    var snapEnabled by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(CanvasSurface, RoundedCornerShape(7.dp)).padding(4.dp)) {
        Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ARRANGEMENT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE4E8EF))
            Spacer(Modifier.weight(1f))
            Text("PINCH TO ZOOM • DRAG TO SCROLL", color = MutedText, fontSize = 8.sp, maxLines = 1)
            Text("${(zoom * 100).toInt()}%", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            SmallAction("FIT") { onZoom(1f); onVerticalZoom(1f); onScroll(0f); onScrollTracks(0f) }
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.width(104.dp)) {
                Row(Modifier.fillMaxWidth().height(22.dp).background(Raised), verticalAlignment = Alignment.CenterVertically) {
                    Text("TRACKS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MutedText, modifier = Modifier.padding(start = 5.dp))
                }
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                    project.tracks.forEachIndexed { index, track -> TrackHeader(track, index, verticalZoom, scrollTracks) }
                }
            }
            TimelineGrid(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                project = project,
                zoom = zoom,
                onZoom = onZoom,
                scrollBars = scrollBars,
                onScroll = onScroll,
                verticalZoom = verticalZoom,
                onVerticalZoom = onVerticalZoom,
                scrollTracks = scrollTracks,
                onScrollTracks = onScrollTracks,
                selectedClip = selectedClip,
                onSelectClip = { selectedClip = it },
                onOpenEditor = { clipId ->
                    val track = project.tracks.firstOrNull { t -> t.clips.any { it.id == clipId } }
                    onOpenEditor(when (track?.kind) { "drum" -> "DRUM SEQUENCER"; "audio" -> "AUDIO EDITOR"; else -> "PIANO ROLL" }, track?.id)
                },
                onClipLongPress = { clipMenu = it },
                onTimelineLongPress = { showTimelineMenu = true }
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                if (statusMessage != null) {
                    Surface(color = Raised, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(8.dp)) {
                        Text(statusMessage!!, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(10.dp))
                    }
                }
                if (showTimelineMenu) {
                    Surface(color = Raised, shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp, modifier = Modifier.padding(8.dp)) {
                        Column(Modifier.width(150.dp).padding(5.dp)) {
                            Text("TIMELINE ACTIONS", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
                            listOf("Add track", "Create clip", "Paste", "Import audio").forEach { action ->
                                Text(action, color = Color.White, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable { statusMessage = when(action) { "Add track" -> "Add track: track creation hook needed"; "Create clip" -> "Choose a track to create a clip"; "Paste" -> "Nothing copied yet"; else -> "Open Browser to import audio" }; if(action == "Import audio") onOpenEditor("BROWSER"); showTimelineMenu = false }.padding(horizontal = 9.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
                if (clipMenu != null) {
                    Surface(color = Raised, shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp, modifier = Modifier.padding(8.dp)) {
                        Column(Modifier.width(150.dp).padding(5.dp)) {
                            Text("CLIP ACTIONS", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
                            listOf("Open editor", "Cut", "Copy", "Snap: " + if (snapEnabled) "On" else "Off").forEach { action ->
                                Text(action, color = Color.White, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable { when { action == "Open editor" -> onOpenEditor("PIANO ROLL"); action == "Cut" -> statusMessage = "Cut selected clip"; action == "Copy" -> statusMessage = "Clip copied"; else -> snapEnabled = !snapEnabled }; clipMenu = null }.padding(horizontal = 9.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackHeader(track: StudioTrack, index: Int, verticalZoom: Float, scrollTracks: Float, onDoubleTap: () -> Unit) {
    val accent = Color(track.colorArgb.toInt())
    Row(Modifier.fillMaxWidth().offset(y = ((index - scrollTracks) * 67f * verticalZoom).dp).height((67f * verticalZoom).dp).padding(bottom = 2.dp).background(Color(0xFF26282D), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.width(4.dp).height(47.dp).background(accent, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f)) {
            Text(track.name, fontSize = 9.sp, color = Color(0xFFF8FAFC), fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.kind.uppercase(), fontSize = 7.sp, color = MutedText)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("M", fontSize = 8.sp, color = MutedText)
                Text("S", fontSize = 8.sp, color = Mint)
                Text("ARM", fontSize = 7.sp, color = if (index == 0) Red else MutedText)
            }
        }
        Canvas(Modifier.size(21.dp)) {
            drawCircle(color = accent.copy(alpha = 0.22f))
            drawArc(color = accent, startAngle = -90f, sweepAngle = 220f, useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
private fun TimelineGrid(
    modifier: Modifier = Modifier,
    project: StudioProject,
    zoom: Float,
    onZoom: (Float) -> Unit,
    scrollBars: Float,
    onScroll: (Float) -> Unit,
    verticalZoom: Float,
    onVerticalZoom: (Float) -> Unit,
    scrollTracks: Float,
    onScrollTracks: (Float) -> Unit,
    selectedClip: String?,
    onSelectClip: (String) -> Unit,
    onOpenEditor: (String) -> Unit,
    onClipLongPress: (String) -> Unit,
    onTimelineLongPress: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val baseBarWidthPx = with(density) { 74.dp.toPx() }
    val rulerHeightPx = with(density) { 24.dp.toPx() }
    val trackHeightPx = with(density) { 67.dp.toPx() } * verticalZoom
    val accentColors = remember(project.tracks) { project.tracks.map { Color(it.colorArgb.toInt()) } }
    val rulerPaint = remember(density) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = with(density) { 9.sp.toPx() } } }
    val currentZoom by rememberUpdatedState(zoom)
    val currentScroll by rememberUpdatedState(scrollBars)
    val currentScrollTracks by rememberUpdatedState(scrollTracks)
    val currentVerticalZoom by rememberUpdatedState(verticalZoom)
    val currentOnVerticalZoom by rememberUpdatedState(onVerticalZoom)
    val currentOnScrollTracks by rememberUpdatedState(onScrollTracks)
    val currentOnZoom by rememberUpdatedState(onZoom)
    val currentOnScroll by rememberUpdatedState(onScroll)

    Box(
        modifier
            .background(Color(0xFF17191C))
            .clipToBounds()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onTimelineLongPress() }) }
            .pointerInput(baseBarWidthPx) {
                detectTransformGestures(panZoomLock = true) { centroid, pan, gestureZoom, _ ->
                    val oldZoom = currentZoom.coerceIn(0.55f, 2.8f)
                    val newZoom = (oldZoom * gestureZoom).coerceIn(0.55f, 2.8f)
                    val oldBarWidthPx = (baseBarWidthPx * oldZoom).coerceAtLeast(1f)
                    val newBarWidthPx = (baseBarWidthPx * newZoom).coerceAtLeast(1f)

                    // Keep the bar under the gesture centroid anchored while zooming.
                    val contentUnderCentroid = currentScroll * oldBarWidthPx + centroid.x
                    val zoomAdjustedScroll = (contentUnderCentroid - centroid.x) / newBarWidthPx
                    val panAdjustedScroll = -pan.x / newBarWidthPx
                    val newScroll = (zoomAdjustedScroll + panAdjustedScroll).coerceAtLeast(0f)
                    val oldVerticalZoom = currentVerticalZoom.coerceIn(0.65f, 1.8f)
                    val newVerticalZoom = (oldVerticalZoom * gestureZoom).coerceIn(0.65f, 1.8f)
                    val trackHeight = with(density) { 67.dp.toPx() }
                    val verticalScroll = (currentScrollTracks - pan.y / (trackHeight * newVerticalZoom)).coerceAtLeast(0f)

                    if (kotlin.math.abs(newZoom - oldZoom) > 0.0005f) currentOnZoom(newZoom)
                    if (kotlin.math.abs(newScroll - currentScroll) > 0.0005f) currentOnScroll(newScroll)
                    if (kotlin.math.abs(newVerticalZoom - oldVerticalZoom) > 0.0005f) currentOnVerticalZoom(newVerticalZoom)
                    if (kotlin.math.abs(verticalScroll - currentScrollTracks) > 0.0005f) currentOnScrollTracks(verticalScroll)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val barWidth = baseBarWidthPx * zoom
            val firstVisibleBar = (scrollBars - 1f / zoom).toInt().coerceAtLeast(0)
            val lastVisibleBar = (scrollBars + size.width / barWidth + 2f).toInt().coerceAtMost(4096)
            for (bar in firstVisibleBar..lastVisibleBar) {
                val x = bar * barWidth - scrollBars * barWidth
                if (x >= -barWidth && x <= size.width + barWidth) {
                    drawLine(
                        if (bar % 4 == 0) Color(0xFF51555D) else Color(0xFF32363D),
                        Offset(x, rulerHeightPx), Offset(x, size.height),
                        if (bar % 4 == 0) 1.5f else 0.7f
                    )
                }
            }
            val firstVisibleTrack = (scrollTracks - 1f / verticalZoom).toInt().coerceAtLeast(0)
            val lastVisibleTrack = (scrollTracks + (size.height - rulerHeightPx) / trackHeightPx + 2f).toInt().coerceAtMost(project.tracks.size)
            for (i in firstVisibleTrack..lastVisibleTrack) {
                val y = rulerHeightPx + (i - scrollTracks) * trackHeightPx
                if (y >= rulerHeightPx && y <= size.height) {
                    drawLine(Color(0xFF3A3D44), Offset(0f, y), Offset(size.width, y), 1f)
                }
            }
        }

        // Draw ruler labels only for bars that are currently visible.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            val barWidth = baseBarWidthPx * zoom
            val firstVisibleBar = (scrollBars - 1f / zoom).toInt().coerceAtLeast(0)
            val lastVisibleBar = (scrollBars + size.width / barWidth + 2f).toInt().coerceAtMost(4096)
            for (bar in firstVisibleBar..lastVisibleBar) {
                val x = bar * barWidth - scrollBars * barWidth
                if (bar % 4 == 0) {
                    drawContext.canvas.nativeCanvas.drawText(
                        (bar + 1).toString(),
                        x + with(density) { 5.dp.toPx() },
                        size.height * 0.68f,
                        rulerPaint
                    )
                } else {
                    drawCircle(
                        color = MutedText,
                        radius = with(density) { 1.dp.toPx() },
                        center = Offset(x, size.height * 0.55f)
                    )
                }
            }
            drawRect(Color(0xFF2B2E34), size = Size(size.width, with(density) { 2.dp.toPx() }))
        }

        Box(Modifier.fillMaxSize().padding(top = 24.dp)) {
            project.tracks.forEachIndexed { trackIndex, track ->
                BoxWithConstraints(Modifier.fillMaxWidth().offset(y = ((trackIndex - scrollTracks) * 67f * verticalZoom).dp).height((67f * verticalZoom).dp)) {
                    val viewportWidthPx = with(density) { maxWidth.toPx() }
                    val barWidth = baseBarWidthPx * zoom
                    track.clips.forEach { clip ->
                        val clipStartPx = clip.startTick.toFloat() / 3840f * barWidth
                        val clipWidthPx = clip.lengthTicks.toFloat() / 3840f * barWidth
                        val displayLeftPx = clipStartPx - scrollBars * barWidth
                        // Avoid composing or drawing clips fully outside the viewport.
                        if (displayLeftPx + clipWidthPx >= 0f && displayLeftPx <= viewportWidthPx) {
                        val accent = accentColors.getOrElse(trackIndex) { Mint }
                        Column(
                            Modifier
                                .offset(x = with(density) { displayLeftPx.toDp() })
                                .padding(top = 7.dp, bottom = 7.dp)
                                .width(with(density) { clipWidthPx.toDp() }.coerceAtLeast(24.dp))
                                .fillMaxHeight()
                                .background(
                                    accent.copy(alpha = if (selectedClip == clip.id) 0.65f else 0.34f),
                                    RoundedCornerShape(4.dp)
                                )
                                .combinedClickable(
                                    onClick = { onSelectClip(clip.id) },
                                    onDoubleClick = { onSelectClip(clip.id); onOpenEditor(clip.id) },
                                    onLongClick = { onSelectClip(clip.id); onClipLongPress(clip.id) }
                                )
                                .padding(4.dp)
                        ) {
                            Text(clip.name, fontSize = 8.sp, color = Color.White, maxLines = 1)
                            if (track.kind == "midi") {
                                Canvas(Modifier.fillMaxSize()) {
                                    clip.midiNotes.forEach { note ->
                                        val noteX = note.startTick.toFloat() / clip.lengthTicks.coerceAtLeast(1) * size.width
                                        val noteW = note.durationTicks.toFloat() / clip.lengthTicks.coerceAtLeast(1) * size.width
                                        val y = (127 - note.pitch).toFloat() / 127f * size.height
                                        drawRoundRect(
                                            Color.White.copy(alpha = 0.8f),
                                            Offset(noteX, y),
                                            Size(noteW.coerceAtLeast(2f), 3.dp.toPx()),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
                                        )
                                    }
                                }
                            } else if (track.kind == "audio") {
                                Canvas(Modifier.fillMaxSize()) {
                                    for (i in 0 until 48) {
                                        val h = ((i * 37 % 19) + 3).toFloat() / 22f * size.height
                                        drawLine(
                                            accent,
                                            Offset(i * size.width / 48f, size.height / 2 - h / 2),
                                            Offset(i * size.width / 48f, size.height / 2 + h / 2),
                                            1.4f
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PianoRollWorkspace() {
    Column(Modifier.fillMaxSize().background(CanvasSurface, RoundedCornerShape(8.dp)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("PIANO ROLL", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("SNAP 1/16 • VELOCITY", color = MutedText, fontSize = 9.sp)
        }
        Canvas(Modifier.fillMaxSize().padding(top = 4.dp)) {
            val rowH = size.height / 24f
            val colW = size.width / 32f
            for (r in 0..24) drawLine(if (r % 12 == 0) Color(0xFF5D626B) else Color(0xFF343840),
                Offset(0f, r * rowH), Offset(size.width, r * rowH), if (r % 12 == 0) 1.5f else 0.6f)
            for (c in 0..32) drawLine(if (c % 4 == 0) Color(0xFF5D626B) else Color(0xFF343840),
                Offset(c * colW, 0f), Offset(c * colW, size.height), if (c % 4 == 0) 1.2f else 0.6f)
            listOf(3, 6, 10, 13, 17, 20).forEachIndexed { i, row ->
                drawRoundRect(Mint, Offset(i * colW * 4, row * rowH), Size(colW * 3.2f, rowH * 0.7f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
            }
        }
    }
}

@Composable
private fun MixerWorkspace(tracks: List<StudioTrack>) {
    Row(Modifier.fillMaxSize().background(CanvasSurface, RoundedCornerShape(8.dp)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (tracks + StudioTrack(name = "MASTER", colorArgb = 0xFFEEEEEE, kind = "master")).forEach { ch ->
            Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF25282D), RoundedCornerShape(6.dp)).padding(7.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ch.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                    drawRoundRect(Color(0xFF131518), Offset(size.width * 0.2f, 0f), Size(size.width * 0.6f, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                    val h = size.height * 0.22f
                    drawRoundRect(Mint, Offset(size.width * 0.2f, size.height - h), Size(size.width * 0.6f, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                }
                Text("${(ch.volume * 100).toInt()}%", color = MutedText, fontSize = 9.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { SmallAction("M") {}; SmallAction("S") {} }
            }
        }
    }
}

@Composable
private fun BrowserWorkspace() {
    Column(Modifier.fillMaxSize().background(CanvasSurface, RoundedCornerShape(8.dp)).padding(10.dp)) {
        Text("BROWSER", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        listOf("Samples", "Drum Kits", "Instruments", "Presets", "Effects", "Recordings", "Projects").forEach { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color(0xFF272A30), RoundedCornerShape(4.dp)).padding(9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Mint, modifier = Modifier.size(15.dp))
                Text(item, modifier = Modifier.padding(start = 8.dp), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun DeviceDock(isPlaying: Boolean, onToggle: () -> Unit, onTone: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().height(105.dp).background(Raised, RoundedCornerShape(8.dp)).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DEVICE CHAIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE4E8EF))
            Spacer(Modifier.weight(1f))
            Text(if (isPlaying) "AUDIO ACTIVE" else "READY", fontSize = 8.sp, color = if (isPlaying) Green else MutedText)
            SmallAction("HIDE") { onToggle() }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("OSC", "FILTER", "DELAY", "REVERB", "DRIVE").forEachIndexed { index, name ->
                Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF202328), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                    Text(name, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (index == 0) Mint else Color(0xFFD5D9E0))
                    var amount by remember { mutableFloatStateOf(0.3f + index * 0.1f) }
                    Slider(value = amount, onValueChange = { amount = it; if (index == 0) onTone(110f + it * 880f) },
                        valueRange = 0f..1f, modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
        shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34383F), contentColor = Color(0xFFE4E8EF))) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.size(29.dp), contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(5.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF353940), contentColor = tint)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}