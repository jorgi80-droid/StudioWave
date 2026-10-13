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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import org.json.JSONObject
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
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("studiowave_project", android.content.Context.MODE_PRIVATE) }
    var project by remember {
        mutableStateOf(
            runCatching {
                preferences.getString("project_json", null)?.let { StudioProject.fromJson(JSONObject(it)) }
            }.getOrNull() ?: StudioProject()
        )
    }
    var playing by remember { mutableStateOf(false) }
    var bpm by remember { mutableIntStateOf(project.bpm) }
    var master by remember { mutableFloatStateOf(0.72f) }
    var selectedWorkspace by remember { mutableStateOf("ARRANGEMENT") }
    var showDock by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var scrollBars by remember { mutableFloatStateOf(0f) }
    var scrollTracks by remember { mutableFloatStateOf(0f) }
    var verticalZoom by remember { mutableFloatStateOf(1f) }
    var editorTrackId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(project) {
        delay(350)
        runCatching { preferences.edit().putString("project_json", project.toJson().toString()).apply() }
    }
    DisposableEffect(engine) { onDispose { engine.close() } }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Mint, secondary = Color(0xFFFF7644), background = Room,
        surface = Raised, onPrimary = Room, onBackground = Color(0xFFF8FAFC),
        onSurface = Color(0xFFF8FAFC)
    )) {
        Surface(Modifier.fillMaxSize(), color = Room) {
            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp)) {
                TransportBar(playing, bpm, master, { value ->
                    bpm = value.coerceIn(40, 240)
                    project = project.copy(bpm = bpm)
                }, { master = it },
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
                        "PIANO ROLL" -> PianoRollWorkspace(onBack = { selectedWorkspace = "ARRANGEMENT" })
                        "BROWSER" -> BrowserWorkspace()
                        "DRUM SEQUENCER" -> DrumSequencerWorkspace(project.tracks.firstOrNull { it.id == editorTrackId }, { project = it }, project, onBack = { selectedWorkspace = "ARRANGEMENT" })
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
private fun ArrangementWorkspace(
    project: StudioProject,
    onProjectChange: (StudioProject) -> Unit,
    zoom: Float, onZoom: (Float) -> Unit,
    scrollBars: Float, onScroll: (Float) -> Unit,
    verticalZoom: Float, onVerticalZoom: (Float) -> Unit,
    scrollTracks: Float, onScrollTracks: (Float) -> Unit,
    onOpenEditor: (String, String?) -> Unit
) {
    var selectedClip by remember { mutableStateOf<String?>(null) }
    var clipMenu by remember { mutableStateOf<String?>(null) }
    var showTimelineMenu by remember { mutableStateOf(false) }
    var showAddTrack by remember { mutableStateOf(false) }
    var snapEnabled by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var copiedClip by remember { mutableStateOf<Pair<StudioTrack, com.studiowave.app.data.WaveClip>?>(null) }

    fun addTrack(kind: String) {
        val number = project.tracks.count { it.kind == kind } + 1
        val name: String
        val color: Long
        when (kind) {
            "audio" -> { name = "AUDIO $number"; color = 0xFF3B82F6L }
            "drum" -> { name = "DRUM RACK $number"; color = 0xFFFF7644L }
            else -> { name = "INSTRUMENT $number"; color = 0xFF00D2B4L }
        }
        onProjectChange(project.copy(tracks = project.tracks + StudioTrack(name = name, colorArgb = color, kind = kind)))
        statusMessage = "$name added"
        showAddTrack = false
        showTimelineMenu = false
    }

    fun handleClipAction(action: String, clipId: String) {
        val trackIndex = project.tracks.indexOfFirst { track -> track.clips.any { it.id == clipId } }
        if (trackIndex < 0) { clipMenu = null; return }
        val track = project.tracks[trackIndex]
        val clip = track.clips.first { it.id == clipId }
        when (action) {
            "Open editor" -> onOpenEditor(when (track.kind) { "drum" -> "DRUM SEQUENCER"; "audio" -> "AUDIO EDITOR"; else -> "PIANO ROLL" }, track.id)
            "Cut" -> {
                copiedClip = track to clip
                onProjectChange(project.copy(tracks = project.tracks.mapIndexed { index, item -> if (index == trackIndex) item.copy(clips = item.clips.filterNot { it.id == clipId }) else item }))
                statusMessage = "Clip cut"
            }
            "Copy" -> { copiedClip = track to clip; statusMessage = "Clip copied" }
            "Duplicate" -> {
                val duplicate = clip.copy(id = java.util.UUID.randomUUID().toString(), name = clip.name + " copy", startTick = clip.startTick + clip.lengthTicks)
                onProjectChange(project.copy(tracks = project.tracks.map { item -> if (item.id == track.id) item.copy(clips = item.clips + duplicate) else item }))
                statusMessage = "Clip duplicated"
            }
            "Color" -> {
                val palette = listOf(0xFFFF7644L, 0xFF00D2B4L, 0xFFA855F7L, 0xFF3B82F6L, 0xFFFF4F9AL, 0xFFFFC857L, 0xFF8BC34AL, 0xFFEEEEEEL)
                val current = clip.colorArgb ?: track.colorArgb
                val paletteIndex = palette.indexOf(current)
                val nextColor = palette[((paletteIndex + 1).coerceAtLeast(0)) % palette.size]
                onProjectChange(project.copy(tracks = project.tracks.map { item ->
                    if (item.id == track.id) item.copy(clips = item.clips.map { c -> if (c.id == clipId) c.copy(colorArgb = nextColor) else c }) else item
                }))
                statusMessage = "Clip color changed"
            }
            "Rename" -> {
                val renamed = clip.copy(name = if (clip.name.endsWith(" *")) clip.name.removeSuffix(" *") else clip.name + " *")
                onProjectChange(project.copy(tracks = project.tracks.map { item -> if (item.id == track.id) item.copy(clips = item.clips.map { c -> if (c.id == clipId) renamed else c }) else item }))
                statusMessage = "Clip renamed (tap Rename again to toggle marker)"
            }
            "Snap" -> { snapEnabled = !snapEnabled; statusMessage = if (snapEnabled) "Snap on" else "Snap off" }
            "Delete" -> {
                onProjectChange(project.copy(tracks = project.tracks.mapIndexed { index, item -> if (index == trackIndex) item.copy(clips = item.clips.filterNot { it.id == clipId }) else item }))
                statusMessage = "Clip deleted"
            }
        }
        clipMenu = null
    }

    fun pasteClip() {
        val copied = copiedClip
        if (copied == null) { statusMessage = "Nothing copied yet"; showTimelineMenu = false; return }
        val targetIndex = project.tracks.indexOfFirst { it.id == copied.first.id }.let { if (it >= 0) it else project.tracks.indexOfFirst { track -> track.kind == copied.first.kind } }
        if (targetIndex < 0) { statusMessage = "Add a matching track first"; showTimelineMenu = false; return }
        val target = project.tracks[targetIndex]
        val source = copied.second
        val nextStart = target.clips.maxOfOrNull { it.startTick + it.lengthTicks } ?: 0L
        val pasted = source.copy(id = java.util.UUID.randomUUID().toString(), name = source.name + " copy", startTick = nextStart)
        onProjectChange(project.copy(tracks = project.tracks.mapIndexed { index, item -> if (index == targetIndex) item.copy(clips = item.clips + pasted) else item }))
        statusMessage = "Clip pasted"
        showTimelineMenu = false
    }

    Column(Modifier.fillMaxSize().background(CanvasSurface, RoundedCornerShape(7.dp)).padding(4.dp)) {
        Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ARRANGEMENT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE4E8EF))
            Spacer(Modifier.weight(1f))
            SmallAction("FIT") { onZoom(1f); onVerticalZoom(1f); onScroll(0f); onScrollTracks(0f) }
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.width(104.dp)) {
                Row(Modifier.fillMaxWidth().height(22.dp).background(Raised), verticalAlignment = Alignment.CenterVertically) {
                    Text("TRACKS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MutedText, modifier = Modifier.padding(start = 5.dp))
                }
                var addTrackMenu by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                    project.tracks.forEachIndexed { index, track ->
                        TrackHeader(track, index, verticalZoom, scrollTracks,
                        onDoubleTap = { onOpenEditor(when (track.kind) { "drum" -> "DRUM SEQUENCER"; "audio" -> "AUDIO EDITOR"; else -> "PIANO ROLL" }, track.id) },
                        onColorTap = {
                            val palette = listOf(0xFFFF7644L, 0xFF00D2B4L, 0xFFA855F7L, 0xFF3B82F6L, 0xFFFF4F9AL, 0xFFFFC857L, 0xFF8BC34AL, 0xFFEEEEEE)
                            val current = palette.indexOf(track.colorArgb)
                            val next = palette[((current + 1).coerceAtLeast(0)) % palette.size]
                            onProjectChange(project.copy(tracks = project.tracks.map { if (it.id == track.id) it.copy(colorArgb = next) else it }))
                        })
                    }
                    val showBottomAddTrack = project.tracks.size > 4 && scrollTracks + (4f / verticalZoom) >= project.tracks.size - 0.75f
                    if (showBottomAddTrack) {
                        Surface(color = Mint, shape = RoundedCornerShape(9.dp), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(30.dp).clickable { addTrackMenu = !addTrackMenu }) {
                            Box(contentAlignment = Alignment.Center) { Text("+", color = Room, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    if (showBottomAddTrack && addTrackMenu) {
                        Surface(color = Raised, shape = RoundedCornerShape(10.dp), shadowElevation = 8.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)) {
                            Column(Modifier.width(150.dp).padding(5.dp)) {
                                Text("ADD TRACK", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
                                listOf("Instrument / MIDI" to "midi", "Audio clip" to "audio", "Drum track" to "drum").forEach { pair ->
                                    Text(pair.first, color = Color.White, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable { addTrack(pair.second); addTrackMenu = false }.padding(horizontal = 9.dp, vertical = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
            TimelineGrid(
                modifier = Modifier.weight(1f).fillMaxHeight(), project = project, zoom = zoom, onZoom = onZoom,
                scrollBars = scrollBars, onScroll = onScroll, verticalZoom = verticalZoom, onVerticalZoom = onVerticalZoom,
                scrollTracks = scrollTracks, onScrollTracks = onScrollTracks, selectedClip = selectedClip,
                onSelectClip = { selectedClip = it },
                onOpenEditor = { clipId ->
                    if (clipId == null) {
                        onOpenEditor("BROWSER", null)
                    } else {
                        val track = project.tracks.firstOrNull { item -> item.clips.any { it.id == clipId } }
                        onOpenEditor(when (track?.kind) { "drum" -> "DRUM SEQUENCER"; "audio" -> "AUDIO EDITOR"; else -> "PIANO ROLL" }, track?.id)
                    }
                },
                onClipLongPress = { clipMenu = it },
                onTimelineLongPress = { showTimelineMenu = true },
                clipMenuClipId = clipMenu,
                onClipAction = { action, clipId -> handleClipAction(action, clipId) },
                onDismissClipMenu = { clipMenu = null },
                onResizeClip = { clipId, lengthTicks ->
                    onProjectChange(project.copy(tracks = project.tracks.map { track ->
                        track.copy(clips = track.clips.map { clip -> if (clip.id == clipId) clip.copy(lengthTicks = lengthTicks) else clip })
                    }))
                },
                onMoveClip = { clipId, targetTrackId, startTick ->
                    val source = project.tracks.firstOrNull { item -> item.clips.any { it.id == clipId } }
                    val target = project.tracks.firstOrNull { it.id == targetTrackId }
                    if (source != null && target != null && source.kind == target.kind) {
                        val moving = source.clips.first { it.id == clipId }.copy(startTick = startTick)
                        onProjectChange(project.copy(tracks = project.tracks.map { item ->
                            if (source.id == target.id) item.copy(clips = item.clips.filterNot { it.id == clipId } + moving)
                            else if (item.id == source.id) item.copy(clips = item.clips.filterNot { it.id == clipId })
                            else if (item.id == target.id) item.copy(clips = item.clips + moving)
                            else item
                        }))
                    } else statusMessage = "Clip must stay on a matching track type"
                }
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
            if (statusMessage != null) Surface(color = Raised, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(4.dp).clickable { statusMessage = null }) { Text(statusMessage!!, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(8.dp)) }
            if (showTimelineMenu) Surface(color = Raised, shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp, modifier = Modifier.padding(6.dp)) {
                Column(Modifier.width(150.dp).padding(5.dp)) {
                    Text("TIMELINE", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
                    Text("Add track", color = Color.White, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable { showAddTrack = true; showTimelineMenu = false }.padding(9.dp))
                    Text("Paste clip", color = Color.White, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable { pasteClip() }.padding(9.dp))
                    Text("Import audio", color = Color.White, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable { onOpenEditor("BROWSER", null); showTimelineMenu = false }.padding(9.dp))
                }
            }
            if (showAddTrack) Surface(color = Raised, shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp, modifier = Modifier.padding(6.dp)) {
                Column(Modifier.width(155.dp).padding(5.dp)) {
                    Text("ADD TRACK", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
                    listOf("Instrument / MIDI" to "midi", "Audio clip" to "audio", "Drum track" to "drum").forEach { pair ->
                        Text(pair.first, color = Color.White, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable { addTrack(pair.second) }.padding(9.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DrumSequencerWorkspace(track: StudioTrack?, onProjectChange: (StudioProject) -> Unit, project: StudioProject, onBack: () -> Unit) {
    val clip = track?.clips?.firstOrNull()
    val steps = clip?.drumSteps?.let { it.take(16) + List((16 - it.size).coerceAtLeast(0)) { false } } ?: List(16) { false }
    Column(Modifier.fillMaxSize().background(Color(0xFF17191C)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ BACK", color = Mint, modifier = Modifier.clickable { onBack() }.padding(8.dp))
            Text((track?.name ?: "DRUM").uppercase() + " • STEP SEQUENCER", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("16 STEPS", color = MutedText, fontSize = 9.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..15).forEach { step ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(70.dp)
                        .background(if (steps[step]) Mint else if (step % 4 == 0) Color(0xFF343A43) else Raised, RoundedCornerShape(4.dp))
                        .clickable {
                            if (track != null && clip != null) {
                                val updated = steps.toMutableList().also { it[step] = !it[step] }
                                onProjectChange(project.copy(tracks = project.tracks.map { item ->
                                    if (item.id == track.id) item.copy(clips = item.clips.map { c -> if (c.id == clip.id) c.copy(drumSteps = updated) else c }) else item
                                }))
                            }
                        }.padding(2.dp))
                    Text((step + 1).toString(), color = MutedText, fontSize = 8.sp)
                }
            }
        }
        Text("Tap a step to toggle trigger • autosave follows project state", color = MutedText, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun AudioEditorWorkspace(track: StudioTrack?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF17191C)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text((track?.name ?: "AUDIO").uppercase() + " • AUDIO EDITOR", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Surface(color = Raised, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(28.dp).clickable { onBack() }) {
                Box(contentAlignment = Alignment.Center) { Text("×", color = Color.White, fontSize = 20.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f).background(Raised, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val mid = size.height / 2f
                for (i in 0..120) {
                    val x = size.width * i / 120f
                    val amp = (kotlin.math.abs(kotlin.math.sin(i * 1.71)) * size.height * 0.36f).toFloat()
                    drawLine(Mint.copy(alpha = 0.8f), Offset(x, mid - amp), Offset(x, mid + amp), 1.2f)
                }
            }
        }
        Text("Waveform preview • source-linked editing integration pending", color = MutedText, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun TrackHeader(track: StudioTrack, index: Int, verticalZoom: Float, scrollTracks: Float, onDoubleTap: () -> Unit, onColorTap: () -> Unit) {
    val accent = Color(track.colorArgb.toInt())
    Row(Modifier.fillMaxWidth().offset(y = ((index - scrollTracks) * 67f * verticalZoom).dp).height((67f * verticalZoom).dp).padding(bottom = 2.dp).background(Color(0xFF26282D), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp).combinedClickable(onClick = {}, onDoubleClick = onDoubleTap, onLongClick = {}),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.width(7.dp).height(47.dp).background(accent, RoundedCornerShape(3.dp)).clickable { onColorTap() })
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
    onTimelineLongPress: () -> Unit,
    clipMenuClipId: String?,
    onClipAction: (String, String) -> Unit,
    onDismissClipMenu: () -> Unit,
    onResizeClip: (String, Long) -> Unit,
    onMoveClip: (String, String, Long) -> Unit
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
    var draggingClip by remember { mutableStateOf<String?>(null) }
    var dragFromTrack by remember { mutableIntStateOf(-1) }
    var dragOriginalTick by remember { mutableLongStateOf(0L) }
    var dragDx by remember { mutableFloatStateOf(0f) }
    var dragDy by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
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
                        val accent = Color((clip.colorArgb ?: track.colorArgb).toInt())
                        val drawingLeft = displayLeftPx + if (draggingClip == clip.id) dragDx else 0f
                        val drawingTop = if (draggingClip == clip.id) dragDy else 0f
                        Column(
                            Modifier
                                .offset(x = with(density) { drawingLeft.toDp() }, y = with(density) { drawingTop.toDp() })
                                .padding(top = 7.dp, bottom = 7.dp)
                                .width(with(density) { clipWidthPx.toDp() }.coerceAtLeast(24.dp))
                                .fillMaxHeight()
                                .background(accent.copy(alpha = if (selectedClip == clip.id) 0.78f else 0.42f), RoundedCornerShape(5.dp))
                                .then(if (selectedClip == clip.id) Modifier.padding(1.dp) else Modifier)
                                .pointerInput(clip.id, trackIndex, zoom) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingClip = clip.id
                                            dragFromTrack = trackIndex
                                            dragOriginalTick = clip.startTick
                                            dragDx = 0f
                                            dragDy = 0f
                                            onSelectClip(clip.id)
                                        },
                                        onDrag = { change, amount -> change.consume(); dragDx += amount.x; dragDy += amount.y },
                                        onDragEnd = {
                                            val source = project.tracks.getOrNull(dragFromTrack)
                                            val targetIndex = (dragFromTrack + (dragDy / trackHeightPx).toInt()).coerceIn(0, project.tracks.lastIndex.coerceAtLeast(0))
                                            val target = project.tracks.getOrNull(targetIndex)
                                            val sixteenthWidthPx = (baseBarWidthPx * zoom) / 16f
                                            val movedSixteenths = (dragDx / sixteenthWidthPx.coerceAtLeast(1f)).toLong()
                                            val newTick = (dragOriginalTick + movedSixteenths * 240L).coerceAtLeast(0L)
                                            if (source != null && target != null && source.kind == target.kind) onMoveClip(clip.id, target.id, newTick)
                                            draggingClip = null
                                            dragFromTrack = -1
                                            dragDx = 0f
                                            dragDy = 0f
                                        },
                                        onDragCancel = { draggingClip = null; dragFromTrack = -1; dragDx = 0f; dragDy = 0f }
                                    )
                                }
                                .combinedClickable(
                                    onClick = { onSelectClip(clip.id) },
                                    onDoubleClick = { onSelectClip(clip.id); onOpenEditor(clip.id) },
                                    onLongClick = { onSelectClip(clip.id); onClipLongPress(clip.id) }
                                )
                                .padding(4.dp)
                        ) {
                            var resizeDeltaPx by remember(clip.id) { mutableFloatStateOf(0f) }
                            Box(Modifier.fillMaxWidth().height(7.dp).pointerInput(clip.id, zoom) {
                                detectDragGestures(
                                    onDragStart = { resizeDeltaPx = 0f },
                                    onDrag = { change, amount -> change.consume(); resizeDeltaPx += amount.x },
                                    onDragEnd = {
                                        val deltaTicks = (resizeDeltaPx / (baseBarWidthPx * zoom) * 3840f).toLong()
                                        onResizeClip(clip.id, (clip.lengthTicks + deltaTicks).coerceAtLeast(240L))
                                        resizeDeltaPx = 0f
                                    },
                                    onDragCancel = { resizeDeltaPx = 0f }
                                )
                            }.background(Color.White.copy(alpha = 0.65f), RoundedCornerShape(3.dp)))
                            Text(clip.name, fontSize = 8.sp, color = Color.White, maxLines = 1)
                            if (track.kind == "drum") {
                                Row(Modifier.fillMaxSize().padding(top = 16.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                                    (0 until 16).forEach { step ->
                                        val active = clip.drumSteps.getOrNull(step) ?: false
                                        Box(Modifier.weight(1f).fillMaxHeight(if (active) 0.88f else 0.24f).background(if (active) accent.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.16f), RoundedCornerShape(1.dp)))
                                    }
                                }
                            } else if (track.kind == "midi") {
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
                                    val peaks = waveformPeaksForUnlinkedClip(clip.sourceUri)
                                    for (i in peaks.indices) {
                                        val h = peaks[i] * size.height * 0.48f
                                        val x = (i + 0.5f) * size.width / peaks.size.coerceAtLeast(1)
                                        drawLine(accent, Offset(x, size.height / 2 - h), Offset(x, size.height / 2 + h), 1.4f)
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
        val menuTrack = project.tracks.firstOrNull { t -> t.clips.any { it.id == clipMenuClipId } }
        val menuClip = menuTrack?.clips?.firstOrNull { it.id == clipMenuClipId }
        if (menuTrack != null && menuClip != null && clipMenuClipId != null) {
            val trackIndex = project.tracks.indexOfFirst { it.id == menuTrack.id }
            val barWidth = baseBarWidthPx * zoom
            val clipLeft = with(density) { (menuClip.startTick.toFloat() / 3840f * barWidth - scrollBars * barWidth).toDp() }
            val menuWidth = 220.dp
            val maxX = (maxWidth - menuWidth - 4.dp).coerceAtLeast(0.dp)
            val x = clipLeft.coerceIn(0.dp, maxX)
            val y = (24.dp + ((trackIndex - scrollTracks) * 67f * verticalZoom).dp - 35.dp).coerceAtLeast(24.dp)
            Row(
                Modifier.offset(x = x, y = y)
                    .background(Color(0xFF30343A), RoundedCornerShape(20.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Open editor", "Cut", "Copy", "Duplicate", "Color", "Rename", "Snap", "Delete", "×").forEach { action ->
                    val label = when (action) { "Open editor" -> "Edit"; "Duplicate" -> "Dup"; "Rename" -> "Name"; "Delete" -> "Del"; else -> action }
                    Surface(
                        color = if (action == "Open editor") Mint.copy(alpha = 0.18f) else Color(0xFF23262B),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(29.dp).clickable {
                            if (action == "×") onDismissClipMenu() else onClipAction(action, clipMenuClipId)
                        }
                    ) {
                        Box(Modifier.padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                            Text(label, color = if (action == "Open editor") Mint else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PianoRollWorkspace(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(CanvasSurface, RoundedCornerShape(8.dp)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("PIANO ROLL", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("SNAP 1/16 • VELOCITY", color = MutedText, fontSize = 9.sp)
            Spacer(Modifier.width(8.dp))
            Surface(color = Raised, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(27.dp).clickable { onBack() }) {
                Box(contentAlignment = Alignment.Center) { Text("×", color = Color.White, fontSize = 20.sp) }
            }
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

/**
 * Safe fallback for clips whose audio source has not been decoded yet. A flat
 * zero waveform honestly represents an unavailable waveform instead of fake peaks.
 * Actual decoding belongs off the UI/audio thread and is not implemented here.
 */
private fun waveformPeaksForUnlinkedClip(sourceUri: String?): List<Float> =
    List(48) { 0f }
