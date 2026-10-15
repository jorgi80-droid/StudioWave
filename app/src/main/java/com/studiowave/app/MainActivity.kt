package com.studiowave.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.studiowave.app.audio.NativeAudioEngine
import com.studiowave.app.audio.ArrangementPlaybackEngine
import com.studiowave.app.data.StudioProject
import com.studiowave.app.data.StudioProjectFile
import com.studiowave.app.data.StudioTrack
import androidx.compose.foundation.BorderStroke
import com.studiowave.app.data.INSTRUMENT_DEVICE_TYPES
import com.studiowave.app.data.EFFECT_DEVICE_TYPES
import com.studiowave.app.data.defaultDeviceParameters
import com.studiowave.app.data.TrackDevice
import android.widget.Toast

private val Room = Color(0xFF141517)
private val CanvasSurface = Color(0xFF1E2024)
private val Raised = Color(0xFF282B30)
private val MutedText = Color(0xFFA4A8B0)
private val Mint = Color(0xFF00D2B4)
private val Green = Color(0xFF22C55E)
private val Red = Color(0xFFEF4444)

private data class TrackRowLayout(val topDp: Float, val heightDp: Float)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideDawSystemBars()
        window.navigationBarColor = android.graphics.Color.rgb(20, 21, 23)
        setContent { StudioWaveApp() }
    }

    private fun hideDawSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideDawSystemBars()
    }

    override fun onResume() {
        super.onResume()
        hideDawSystemBars()
    }
}

private fun musicalPositionLabel(tick: Long, ppq: Int, numerator: Int, denominator: Int): String {
    val safePpq = ppq.coerceIn(24, 3840)
    val safeNumerator = numerator.coerceIn(1, 32)
    val safeDenominator = denominator.coerceIn(1, 32)
    val ticksPerBeat = (safePpq.toLong() * 4L / safeDenominator).coerceAtLeast(1L)
    val ticksPerBar = (ticksPerBeat * safeNumerator).coerceAtLeast(1L)
    val safeTick = tick.coerceAtLeast(0L)
    val bar = safeTick / ticksPerBar + 1L
    val beat = safeTick % ticksPerBar / ticksPerBeat + 1L
    return "BAR ${bar.toString().padStart(3, '0')}:${beat.toString().padStart(2, '0')}"
}

private fun suggestedSdawFileName(projectName: String): String {
    val safeName = projectName.trim()
        .replace(Regex("[^A-Za-z0-9 _.-]"), "")
        .trim()
        .trimEnd('.')
        .take(64)
        .ifBlank { "StudioWave Project" }
    return if (safeName.endsWith(".sdaw", ignoreCase = true)) safeName else "$safeName.sdaw"
}

@Composable
private fun StudioWaveApp() {
    val context = LocalContext.current
    val engine = remember { NativeAudioEngine() }
    val arrangementPlayback = remember(context) { com.studiowave.app.audio.ArrangementPlaybackEngine(context) }
    val playbackScope = rememberCoroutineScope()
    val preferences = remember(context) { context.getSharedPreferences("studiowave_project", android.content.Context.MODE_PRIVATE) }
    var project by remember {
        mutableStateOf(
            runCatching {
                preferences.getString("project_json", null)?.let { StudioProject.fromJson(JSONObject(it)) }
            }.getOrNull() ?: StudioProject()
        )
    }
    var playing by remember { mutableStateOf(false) }
    var playbackLoading by remember { mutableStateOf(false) }
    var playbackJob by remember { mutableStateOf<Job?>(null) }
    var playheadTick by remember { mutableLongStateOf(0L) }
    var playbackSession by remember { mutableLongStateOf(0L) }
    var bpm by remember { mutableIntStateOf(project.bpm) }
    var audioError by remember { mutableStateOf<String?>(null) }
    var master by remember { mutableFloatStateOf(0.72f) }
    var masterPeakDbfs by remember { mutableFloatStateOf(-120f) }
    var masterRmsDbfs by remember { mutableFloatStateOf(-120f) }
    var masterMomentaryLufs by remember { mutableFloatStateOf(-120f) }
    var masterPeakHistory by remember { mutableStateOf(List(48) { 0f }) }
    var selectedWorkspace by remember { mutableStateOf("ARRANGEMENT") }
    // Start with the device drawer collapsed so Arrangement gets the full vertical workspace.
    var showDock by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var scrollTicks by remember { mutableFloatStateOf(0f) }
    var scrollTracks by remember { mutableFloatStateOf(0f) }
    var verticalZoom by remember { mutableFloatStateOf(1f) }
    val trackHeightsDp = remember { mutableStateMapOf<String, Float>() }
    var editorTrackId by remember { mutableStateOf<String?>(null) }
    var scratchOffset by remember { mutableFloatStateOf(0f) }
    var undoHistory by remember { mutableStateOf<List<StudioProject>>(emptyList()) }
    var redoHistory by remember { mutableStateOf<List<StudioProject>>(emptyList()) }
    var pendingExportProject by remember { mutableStateOf<StudioProject?>(null) }
    fun changeProject(next: StudioProject) {
        if (next != project) {
            undoHistory = (undoHistory + project).takeLast(100)
            redoHistory = emptyList()
            project = next
            arrangementPlayback.updateMix(next)
        }
    }
    fun undoProject() {
        val previous = undoHistory.lastOrNull() ?: return
        redoHistory = redoHistory + project
        undoHistory = undoHistory.dropLast(1)
        project = previous
        bpm = previous.bpm
        arrangementPlayback.updateMix(previous)
    }
    fun redoProject() {
        val next = redoHistory.lastOrNull() ?: return
        undoHistory = undoHistory + project
        redoHistory = redoHistory.dropLast(1)
        project = next
        bpm = next.bpm
        arrangementPlayback.updateMix(next)
    }
    fun stopPlayback() {
        playbackSession += 1L
        playbackJob?.cancel()
        playbackJob = null
        runCatching { arrangementPlayback.stop() }
        runCatching { engine.stop() }
        playing = false
        playbackLoading = false
        audioError = null
    }
    fun seekToTick(tick: Long) {
        val target = tick.coerceAtLeast(0L)
        if (playing || playbackLoading) stopPlayback()
        playheadTick = target
    }
    fun startPlaybackAt(tick: Long, projectSnapshot: StudioProject = project) {
        val session = playbackSession + 1L
        playbackSession = session
        // Cancel stale preparation when an edited arrangement requests a new render plan.
        playbackJob?.cancel()
        playbackJob = null
        val snapshot = projectSnapshot
        val gain = master.coerceIn(0f, 1f)
        val startTick = tick.coerceAtLeast(0L)
        playheadTick = startTick
        playbackLoading = true
        audioError = null
        playbackJob = playbackScope.launch {
            try {
                arrangementPlayback.start(
                    snapshot,
                    gain,
                    onCompleted = {
                        playbackScope.launch {
                            if (session == playbackSession) {
                                playing = false
                                playbackLoading = false
                                playbackJob = null
                            }
                        }
                    },
                    onError = { message ->
                        playbackScope.launch {
                            if (session == playbackSession) {
                                playing = false
                                playbackLoading = false
                                playbackJob = null
                                audioError = message
                            }
                        }
                    },
                    startAtTick = startTick,
                    onProgress = { positionTicks ->
                        playbackScope.launch {
                            if (session == playbackSession) playheadTick = positionTicks.coerceAtLeast(0L)
                        }
                    }
                )
                if (session == playbackSession) playing = true
            } catch (_: CancellationException) {
                if (session == playbackSession) {
                    playing = false
                    playbackLoading = false
                    playbackJob = null
                }
            } catch (failure: Exception) {
                if (session == playbackSession) {
                    playing = false
                    audioError = failure.message ?: "Arrangement playback failed"
                }
            } finally {
                if (session == playbackSession) {
                    playbackLoading = false
                    playbackJob = null
                }
            }
        }
    }
    val latestProject by rememberUpdatedState(project)
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            playbackScope.launch {
                try {
                    val durationMs = withContext(Dispatchers.IO) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, uri)
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                        } finally {
                            runCatching { retriever.release() }
                        }
                    }
                    val currentProject = latestProject
                    val audioTrack = currentProject.tracks.firstOrNull { it.kind == "audio" }
                    val target = audioTrack ?: StudioTrack(name = "AUDIO 1", colorArgb = 0xFF3B82F6L, kind = "audio")
                    when {
                        audioTrack == null && currentProject.tracks.size >= StudioProject.MAX_TRACKS -> {
                            audioError = "Track limit reached (${StudioProject.MAX_TRACKS}); remove a track before importing."
                        }
                        target.clips.size >= StudioProject.MAX_CLIPS_PER_TRACK -> {
                            audioError = "Clip limit reached for the audio track."
                        }
                        durationMs > ArrangementPlaybackEngine.MAX_SOURCE_SECONDS * 1000L -> {
                            audioError = "Audio is longer than the current 5-minute source limit. Trim it before importing."
                        }
                        else -> {
                            val timebase = TimelineTimebase(
                                currentProject.bpm, currentProject.numerator,
                                currentProject.denominator, currentProject.ppq
                            )
                            val exactTicks = if (durationMs > 0L) {
                                kotlin.math.ceil(durationMs.toDouble() * currentProject.bpm *
                                    currentProject.ppq / 60_000.0).toLong().coerceAtLeast(1L)
                            } else 15360L
                            val snapStep = SnapDivision.QUARTER_BEAT.ticks(timebase)
                            val lengthTicks = if (durationMs > 0L) {
                                (((exactTicks + snapStep - 1L) / snapStep) * snapStep).coerceAtLeast(snapStep)
                            } else 15360L
                            val clip = com.studiowave.app.data.WaveClip(
                                name = uri.lastPathSegment?.substringAfterLast('/')?.take(32)?.ifBlank { "Audio clip" } ?: "Audio clip",
                                startTick = target.clips.maxOfOrNull { it.startTick + it.lengthTicks } ?: 0L,
                                lengthTicks = lengthTicks,
                                sourceUri = uri.toString()
                            )
                            val updatedTrack = target.copy(clips = target.clips + clip)
                            changeProject(currentProject.copy(tracks = if (audioTrack == null) {
                                currentProject.tracks + updatedTrack
                            } else currentProject.tracks.map { if (it.id == target.id) updatedTrack else it }))
                            selectedWorkspace = "ARRANGEMENT"
                            audioError = null
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    audioError = failure.message ?: "Could not inspect the audio file."
                }
            }
        }
    }
    val exportProjectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val snapshot = pendingExportProject ?: latestProject
        pendingExportProject = null
        if (uri != null) {
            playbackScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri, "wt")
                            ?: error("Tidak dapat membuka tujuan penyimpanan proyek")
                        output.use { StudioProjectFile.write(snapshot, it) }
                    }
                    Toast.makeText(context, "Proyek .sdaw berhasil disimpan", Toast.LENGTH_SHORT).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    audioError = failure.message ?: "Ekspor proyek gagal"
                }
            }
        }
    }
    val importProjectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            playbackScope.launch {
                try {
                    val imported = withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("Tidak dapat membuka file proyek")
                        input.use { StudioProjectFile.read(it) }
                    }
                    playbackJob?.cancel()
                    playbackJob = null
                    arrangementPlayback.stop()
                    engine.stop()
                    playing = false
                    playbackLoading = false
                    changeProject(imported)
                    bpm = imported.bpm
                    scrollTicks = 0f
                    scrollTracks = 0f
                    zoom = 1f
                    verticalZoom = 1f
                    editorTrackId = null
                    selectedWorkspace = "ARRANGEMENT"
                    audioError = null
                    Toast.makeText(context, "Proyek .sdaw berhasil dibuka", Toast.LENGTH_SHORT).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    audioError = failure.message ?: "Impor proyek gagal"
                }
            }
        }
    }
    LaunchedEffect(arrangementPlayback) {
        while (true) {
            val meter = arrangementPlayback.masterMeterSnapshot()
            masterPeakDbfs = meter.peakDbfs
            masterRmsDbfs = meter.rmsDbfs
            masterMomentaryLufs = meter.momentaryLufs
            masterPeakHistory = masterPeakHistory.drop(1) + meter.peakLinear.coerceIn(0f, 1f)
            delay(50)
        }
    }
    LaunchedEffect(project) {
        delay(350)
        val snapshot = project
        try {
            val serialized = withContext(Dispatchers.Default) { snapshot.toJson().toString() }
            if (latestProject === snapshot) {
                withContext(Dispatchers.IO) {
                    preferences.edit().putString("project_json", serialized).apply()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Autosave must not crash editing; the next project change will retry it.
        }
    }
    DisposableEffect(engine, arrangementPlayback) {
        onDispose {
            playbackJob?.cancel()
            arrangementPlayback.close()
            engine.close()
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Mint, secondary = Color(0xFFFF7644), background = Room,
        surface = Raised, onPrimary = Room, onBackground = Color(0xFFF8FAFC),
        onSurface = Color(0xFFF8FAFC)
    )) {
        Surface(Modifier.fillMaxSize(), color = Room) {
            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp)) {
                if (audioError != null) {
                    Surface(color = Color(0xFF4B2424), shape = RoundedCornerShape(5.dp), modifier = Modifier.fillMaxWidth().clickable { audioError = null }) {
                        Text(audioError!!, color = Color(0xFFFFD4D4), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 2)
                    }
                }
                TransportBar(
                    playing || playbackLoading,
                    bpm,
                    master,
                    masterPeakDbfs,
                    masterRmsDbfs,
                    masterMomentaryLufs,
                    masterPeakHistory,
                    musicalPositionLabel(playheadTick, project.ppq, project.numerator, project.denominator),
                    "${project.numerator}/${project.denominator}",
                    { value ->
                    bpm = value.coerceIn(40, 240)
                    changeProject(project.copy(bpm = bpm))
                }, { value -> master = value; arrangementPlayback.setMasterGain(value) }, onUndo = { undoProject() }, onRedo = { redoProject() },
                    onSaveProject = {
                        pendingExportProject = project
                        exportProjectLauncher.launch(suggestedSdawFileName(project.name))
                    },
                    onOpenProject = { importProjectLauncher.launch(arrayOf("*/*")) },
                    onPlay = {
                        if (playing || playbackLoading) stopPlayback() else startPlaybackAt(playheadTick)
                    },
                    onStop = { stopPlayback() })
                Spacer(Modifier.height(3.dp))
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    WorkspaceRail(selectedWorkspace) { selectedWorkspace = it }
                    Spacer(Modifier.width(4.dp))
                    when (selectedWorkspace) {
                        "DEVICE" -> DirectWaveWorkspace(
                            onOpenBrowser = { selectedWorkspace = "BROWSER" },
                            onBack = { selectedWorkspace = "ARRANGEMENT" }
                        )
                        "MIXER" -> MixerWorkspace(
                            project,
                            master,
                            onMasterChange = { value ->
                                master = value.coerceIn(0f, 1f)
                                arrangementPlayback.setMasterGain(master)
                            },
                            onProjectChange = { changeProject(it) }
                        )
                        "PIANO ROLL" -> PianoRollWorkspace(project.tracks.firstOrNull { it.id == editorTrackId }, project, { changeProject(it) }, onBack = { selectedWorkspace = "ARRANGEMENT" })
                        "BROWSER" -> BrowserWorkspace(onImportAudio = { audioPicker.launch(arrayOf("audio/*")) })
                        "DRUM SEQUENCER" -> DrumSequencerWorkspace(project.tracks.firstOrNull { it.id == editorTrackId }, { changeProject(it) }, project, onBack = { selectedWorkspace = "ARRANGEMENT" })
                        "AUDIO EDITOR" -> AudioEditorWorkspace(project.tracks.firstOrNull { it.id == editorTrackId }, onBack = { selectedWorkspace = "ARRANGEMENT" })
                        else -> ArrangementWorkspace(project, { changeProject(it) }, zoom, { zoom = it.coerceIn(0.1f, 4f) }, scrollTicks, { scrollTicks = it.coerceAtLeast(0f) }, verticalZoom, { verticalZoom = it.coerceIn(0.65f, 1.8f) }, scrollTracks, { scrollTracks = it.coerceAtLeast(0f) }, { workspace, trackId -> editorTrackId = trackId; selectedWorkspace = workspace },
                            onPlaybackRestart = { updatedProject ->
                                if (playing || playbackLoading) startPlaybackAt(playheadTick, updatedProject)
                            },
                            showDevices = showDock,
                            onToggleDevices = { showDock = !showDock },
                            trackHeights = trackHeightsDp,
                            onScratchTrack = { trackId, deltaMs -> arrangementPlayback.scratchTrack(trackId, deltaMs) },
                            playheadTick = playheadTick,
                            isPlaying = playing,
                            onSeekTick = { tick -> seekToTick(tick) })
                    }
                }
                if (showDock && selectedWorkspace == "ARRANGEMENT") {
                    Box(Modifier.fillMaxWidth().height(104.dp)) {
                        DeviceDock(
                            project = project,
                            selectedTrackId = editorTrackId,
                            onSelectTrack = { editorTrackId = it },
                            onProjectChange = { changeProject(it) },
                            onToggle = { showDock = false },
                            fullScreen = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportBar(
    playing: Boolean, bpm: Int, master: Float,
    peakDbfs: Float, rmsDbfs: Float, momentaryLufs: Float, peakHistory: List<Float>,
    positionLabel: String, timeSignatureLabel: String,
    onBpm: (Int) -> Unit, onMaster: (Float) -> Unit, onPlay: () -> Unit, onStop: () -> Unit,
    onUndo: () -> Unit, onRedo: () -> Unit,
    onSaveProject: () -> Unit, onOpenProject: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(Raised, RoundedCornerShape(7.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("StudioWave", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Mint, maxLines = 1)
        Text("BPM", color = MutedText, fontSize = 8.sp)
        SmallAction("−") { onBpm(bpm - 1) }
        Text(bpm.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        SmallAction("+") { onBpm(bpm + 1) }
        Text(timeSignatureLabel, fontSize = 9.sp, color = MutedText, maxLines = 1)
        Text(positionLabel, fontSize = 8.sp, color = Color(0xFFDADEE5), maxLines = 1)
        SmallIconButton(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) Green else Mint, onPlay)
        SmallIconButton(Icons.Default.Stop, MutedText, onStop)
        SmallIconButton(Icons.Default.Save, MutedText, onSaveProject)
        SmallIconButton(Icons.Default.FolderOpen, MutedText, onOpenProject)
        SmallIconButton(Icons.Default.Undo, MutedText, onUndo)
        SmallIconButton(Icons.Default.Redo, MutedText, onRedo)
        Spacer(Modifier.weight(1f))
        Text("MST", color = MutedText, fontSize = 7.sp)
        Slider(value = master, onValueChange = onMaster, modifier = Modifier.width(92.dp).height(32.dp), valueRange = 0f..1f)
        Column(Modifier.width(166.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("P ${peakDbfs.toInt()} dBFS", color = Color(0xFFE4E8EF), fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("M ${momentaryLufs.toInt()} LUFS", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Canvas(Modifier.fillMaxWidth().height(11.dp)) {
                drawLine(Color(0xFF343A40), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), size.height)
                peakHistory.forEachIndexed { index, level ->
                    val bar = (level.coerceIn(0f, 1f) * size.height).coerceAtLeast(1f)
                    val x = index * size.width / peakHistory.size.coerceAtLeast(1)
                    drawLine(Mint.copy(alpha = 0.85f), Offset(x, size.height), Offset(x, size.height - bar),
                        (size.width / peakHistory.size.coerceAtLeast(1)).coerceAtLeast(1f))
                }
            }
            Text("RMS ${rmsDbfs.toInt()} dBFS", color = MutedText, fontSize = 8.sp, maxLines = 1)
        }
    }
}

@Composable
private fun WorkspaceRail(selected: String, onSelect: (String) -> Unit) {
    Column(
        Modifier.width(48.dp).fillMaxHeight().background(Raised, RoundedCornerShape(7.dp)).padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        RailButton("ARR", selected == "ARRANGEMENT", Icons.Default.GraphicEq) { onSelect("ARRANGEMENT") }
        RailButton("KEYS", selected == "PIANO ROLL", Icons.Default.Keyboard) { onSelect("PIANO ROLL") }
        RailButton("MIX", selected == "MIXER", Icons.Default.Tune) { onSelect("MIXER") }
        RailButton("DEV", selected == "DEVICE", Icons.Default.Tune) { onSelect("DEVICE") }
        RailButton("FILES", selected == "BROWSER", Icons.Default.LibraryMusic) { onSelect("BROWSER") }
    }
}

@Composable
private fun RailButton(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(1.dp),
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
    scrollTicks: Float, onScroll: (Float) -> Unit,
    verticalZoom: Float, onVerticalZoom: (Float) -> Unit,
    scrollTracks: Float, onScrollTracks: (Float) -> Unit,
    onOpenEditor: (String, String?) -> Unit,
    trackHeights: MutableMap<String, Float>,
    onPlaybackRestart: (StudioProject) -> Unit = {},
    onScratchTrack: (String, Float) -> Unit = { _, _ -> },
    playheadTick: Long = 0L,
    isPlaying: Boolean = false,
    showDevices: Boolean = false,
    onToggleDevices: () -> Unit = {},
    onSeekTick: (Long) -> Unit = {}
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val densityScale = density.density
    val trackIds = project.tracks.map { it.id }
    val trackRowLayout = remember(trackIds, trackHeights.toMap()) {
        var top = 0f
        trackIds.map { id ->
            val height = (trackHeights[id] ?: 67f).coerceIn(44f, 160f)
            TrackRowLayout(topDp = top, heightDp = height).also { top += height }
        }
    }
    var selectedClip by remember { mutableStateOf<String?>(null) }
    var clipMenu by remember { mutableStateOf<String?>(null) }
    var showTimelineMenu by remember { mutableStateOf(false) }
    var showAddTrack by remember { mutableStateOf(false) }
    var snapEnabled by remember { mutableStateOf(true) }
    // "1/16" means a sixteenth note: one quarter of a quarter-note beat in 4/4.
    var snapDivision by remember { mutableStateOf(SnapDivision.QUARTER_BEAT) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var renameClipId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var copiedClip by remember { mutableStateOf<Pair<StudioTrack, com.studiowave.app.data.WaveClip>?>(null) }

    fun addTrack(kind: String, openEditor: Boolean = false) {
        if (project.tracks.size >= StudioProject.MAX_TRACKS) {
            statusMessage = "Track limit reached (${StudioProject.MAX_TRACKS})"
            showAddTrack = false
            showTimelineMenu = false
            return
        }
        val number = project.tracks.count { it.kind == kind } + 1
        val name: String
        val color: Long
        when (kind) {
            "audio" -> { name = "AUDIO $number"; color = 0xFF3B82F6L }
            "drum" -> { name = "DRUM STEP $number"; color = 0xFFFF7644L }
            else -> { name = "VIRTUAL INSTRUMENT $number"; color = 0xFF00D2B4L }
        }
        val starterClip = when (kind) {
            "midi" -> com.studiowave.app.data.WaveClip(
                name = "MIDI Pattern 1", startTick = 0L, lengthTicks = 15360L
            )
            "drum" -> com.studiowave.app.data.WaveClip(
                name = "Drum Pattern 1", startTick = 0L, lengthTicks = 15360L,
                drumSteps = List(16) { false }
            )
            else -> null
        }
        val newTrack = StudioTrack(
            name = name, colorArgb = color, kind = kind,
            clips = starterClip?.let { listOf(it) } ?: emptyList()
        )
        onProjectChange(project.copy(tracks = project.tracks + newTrack))
        statusMessage = "$name added"
        showAddTrack = false
        showTimelineMenu = false
        if (openEditor) onOpenEditor(if (kind == "drum") "DRUM SEQUENCER" else "PIANO ROLL", newTrack.id)
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
                if (track.clips.size >= StudioProject.MAX_CLIPS_PER_TRACK) {
                    statusMessage = "Clip limit reached for this track"
                } else {
                    val nextStart = if (clip.startTick > Long.MAX_VALUE - clip.lengthTicks.coerceAtLeast(1L))
                        Long.MAX_VALUE - clip.lengthTicks.coerceAtLeast(1L) else clip.startTick + clip.lengthTicks.coerceAtLeast(1L)
                    val duplicate = clip.copy(id = java.util.UUID.randomUUID().toString(), name = clip.name + " copy", startTick = nextStart)
                    onProjectChange(project.copy(tracks = project.tracks.map { item -> if (item.id == track.id) item.copy(clips = item.clips + duplicate) else item }))
                    statusMessage = "Clip duplicated"
                }
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
                renameClipId = clipId
                renameText = clip.name
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
        if (target.clips.size >= StudioProject.MAX_CLIPS_PER_TRACK) {
            statusMessage = "Clip limit reached for this track"
            showTimelineMenu = false
            return
        }
        val source = copied.second
        if (target.kind != copied.first.kind) {
            statusMessage = "Paste requires a track with matching type"
            showTimelineMenu = false
            return
        }
        if (target.clips.size >= StudioProject.MAX_CLIPS_PER_TRACK) {
            statusMessage = "Clip limit reached for this track"
            showTimelineMenu = false
            return
        }
        val nextStart = target.clips.maxOfOrNull {
            if (it.startTick > Long.MAX_VALUE - it.lengthTicks.coerceAtLeast(1L)) Long.MAX_VALUE
            else it.startTick + it.lengthTicks.coerceAtLeast(1L)
        } ?: 0L
        val safeStart = nextStart.coerceAtMost(Long.MAX_VALUE - source.lengthTicks.coerceAtLeast(1L))
        val pasted = source.copy(id = java.util.UUID.randomUUID().toString(), name = source.name + " copy", startTick = safeStart)
        onProjectChange(project.copy(tracks = project.tracks.mapIndexed { index, item -> if (index == targetIndex) item.copy(clips = item.clips + pasted) else item }))
        statusMessage = "Clip pasted"
        showTimelineMenu = false
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fitTimebase = remember(project.bpm, project.numerator, project.denominator, project.ppq) {
            TimelineTimebase(project.bpm, project.numerator, project.denominator, project.ppq)
        }
        val maxEndTick = remember(project.tracks) {
            project.tracks.asSequence().flatMap { it.clips.asSequence() }.maxOfOrNull { clip ->
                val start = clip.startTick.coerceAtLeast(0L).coerceAtMost(Long.MAX_VALUE / 2L)
                val length = clip.lengthTicks.coerceAtLeast(1L).coerceAtMost(Long.MAX_VALUE / 2L)
                start + length
            } ?: 1L
        }.coerceAtLeast(1L)
        val fitZoom = remember(maxWidth, maxEndTick, fitTimebase, density) {
            val timelineWidthPx = with(density) { (maxWidth - 8.dp - 104.dp).coerceAtLeast(1.dp).toPx() }
            val baseBarWidthPx = with(density) { 74.dp.toPx() }.coerceAtLeast(1f)
            (timelineWidthPx.toDouble() * fitTimebase.ticksPerBar.toDouble() /
                (baseBarWidthPx.toDouble() * maxEndTick.toDouble()))
                .toFloat().coerceIn(0.1f, 4f)
        }
        Column(Modifier.fillMaxSize().background(CanvasSurface, RoundedCornerShape(7.dp)).padding(1.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(104.dp)) {
                Row(Modifier.fillMaxWidth().height(22.dp).background(Raised), verticalAlignment = Alignment.CenterVertically) {
                    Text("TRACKS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MutedText, modifier = Modifier.padding(start = 5.dp))
                }
                var addTrackMenu by remember { mutableStateOf(false) }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                    val headerViewportHeightDp = maxHeight.value
                    project.tracks.forEachIndexed { index, track ->
                        val row = trackRowLayout.getOrNull(index) ?: TrackRowLayout(index * 67f, 67f)
                        val viewTop = (row.topDp - scrollTracks) * verticalZoom
                        val viewHeight = row.heightDp * verticalZoom
                        if (viewTop + viewHeight >= -viewHeight && viewTop <= headerViewportHeightDp) {
                            TrackHeader(
                                track, index, verticalZoom, scrollTracks,
                                rowTopDp = row.topDp, rowHeightDp = row.heightDp,
                                onDoubleTap = { onOpenEditor(when (track.kind) { "drum" -> "DRUM SEQUENCER"; "audio" -> "AUDIO EDITOR"; else -> "PIANO ROLL" }, track.id) },
                                onColorTap = {
                                    val palette = listOf(0xFFFF7644L, 0xFF00D2B4L, 0xFFA855F7L, 0xFF3B82F6L, 0xFFFF4F9AL, 0xFFFFC857L, 0xFF8BC34AL, 0xFFEEEEEE)
                                    val current = palette.indexOf(track.colorArgb)
                                    val next = palette[((current + 1).coerceAtLeast(0)) % palette.size]
                                    onProjectChange(project.copy(tracks = project.tracks.map { if (it.id == track.id) it.copy(colorArgb = next) else it }))
                                },
                                onMuteToggle = { onProjectChange(project.copy(tracks = project.tracks.map { if (it.id == track.id) it.copy(muted = !it.muted) else it })) },
                                onSoloToggle = { onProjectChange(project.copy(tracks = project.tracks.map { if (it.id == track.id) it.copy(solo = !it.solo) else it })) },
                                onResizeTrack = { delta ->
                                    trackHeights[track.id] = ((trackHeights[track.id] ?: 67f) + delta / (densityScale * verticalZoom).coerceAtLeast(0.1f)).coerceIn(44f, 160f)
                                },
                                onScratch = { delta -> if (track.kind == "audio") onScratchTrack(track.id, delta / densityScale * 8f) },
                                                onReorderTrack = { deltaPx ->
                                    val from = project.tracks.indexOfFirst { it.id == track.id }
                                    val fromRow = trackRowLayout.getOrNull(from)
                                    if (from >= 0 && fromRow != null && project.tracks.size > 1 && deltaPx.isFinite()) {
                                        val rowStepPx = with(density) {
                                            (fromRow.heightDp * verticalZoom).dp.toPx()
                                        }.coerceAtLeast(1f)
                                        val steps = kotlin.math.round(deltaPx / rowStepPx).toInt()
                                        val target = (from + steps).coerceIn(0, project.tracks.lastIndex)
                                        if (target != from) {
                                            val reordered = project.tracks.toMutableList()
                                            reordered.add(target, reordered.removeAt(from))
                                            onProjectChange(project.copy(tracks = reordered))
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Anchor the add-track control to the actual end of the final track row,
                    // rather than pinning it to the viewport bottom while the tracks scroll.
                    val lastTrackRow = trackRowLayout.lastOrNull()
                    val addAnchorY = lastTrackRow?.let {
                        (it.topDp + it.heightDp - scrollTracks) * verticalZoom
                    } ?: 8f
                    val showBottomAddTrack = if (lastTrackRow == null) true else
                        addAnchorY >= 0f && addAnchorY + 44f <= headerViewportHeightDp
                    if (showBottomAddTrack) {
                        Surface(
                            color = Mint,
                            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp),
                            shadowElevation = 5.dp,
                            modifier = Modifier.align(Alignment.TopEnd)
                                .offset(y = (addAnchorY + 8f).coerceAtLeast(0f).dp)
                                .padding(end = 5.dp)
                                .size(34.dp).clickable { addTrackMenu = !addTrackMenu }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(if (addTrackMenu) "×" else "✦", color = Room, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    if (showBottomAddTrack && addTrackMenu) {
                        Surface(
                            color = Raised,
                            shape = RoundedCornerShape(10.dp),
                            shadowElevation = 8.dp,
                            modifier = Modifier.align(Alignment.TopEnd)
                                .offset(y = (addAnchorY - 174f).coerceAtLeast(0f).dp)
                                .padding(end = 40.dp)
                        ) {
                            Column(Modifier.width(174.dp).padding(5.dp)) {
                                Text("ADD TRACK", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
                                listOf(
                                    "MIDI track" to "midi",
                                    "Audio track" to "audio",
                                    "Drum track" to "drum",
                                    "Virtual instrument" to "midi"
                                ).forEach { pair ->
                                    Text(
                                        pair.first, color = Color.White, fontSize = 11.sp,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            addTrack(pair.second, openEditor = pair.second != "audio")
                                            addTrackMenu = false
                                        }.padding(horizontal = 9.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            TimelineGrid(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                project = project,
                playheadTick = playheadTick,
                isPlaying = isPlaying,
                onSeekTick = onSeekTick,
                zoom = zoom, onZoom = onZoom,
                scrollTicks = scrollTicks, onScroll = onScroll, verticalZoom = verticalZoom, onVerticalZoom = onVerticalZoom,
                snapDivision = snapDivision, scrollTracks = scrollTracks, onScrollTracks = onScrollTracks, trackRowLayout = trackRowLayout, snapEnabled = snapEnabled, selectedClip = selectedClip,
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
                onResizeClip = { clipId, newStartTick, lengthTicks ->
                    val safeLength = lengthTicks.coerceIn(1L, Long.MAX_VALUE / 2L)
                    val resizedProject = project.copy(tracks = project.tracks.map { track ->
                        track.copy(clips = track.clips.map { clip ->
                            if (clip.id != clipId) clip else {
                                val delta = newStartTick - clip.startTick
                                val oldLength = clip.lengthTicks.coerceAtLeast(1L)
                                val safeStart = newStartTick.coerceAtLeast(0L)
                                val safeDelta = safeStart - clip.startTick
                                val nextNotes = if (safeDelta > 0L) {
                                    clip.midiNotes.mapNotNull { note ->
                                        val noteEnd = note.startTick + note.durationTicks.coerceAtLeast(1L)
                                        if (noteEnd <= safeDelta) null else {
                                            val clippedStart = note.startTick.coerceAtLeast(safeDelta)
                                            note.copy(
                                                startTick = (clippedStart - safeDelta).coerceAtLeast(0L),
                                                durationTicks = (noteEnd - clippedStart).coerceAtLeast(1L)
                                            )
                                        }
                                    }
                                } else if (safeDelta < 0L) {
                                    clip.midiNotes.map { note -> note.copy(startTick = note.startTick - safeDelta) }
                                } else clip.midiNotes
                                val nextDrumSteps = if (clip.drumSteps.isEmpty() || safeDelta == 0L) clip.drumSteps else {
                                    val stepTicks = (oldLength.toDouble() / clip.drumSteps.size).coerceAtLeast(1.0)
                                    if (safeDelta > 0L) {
                                        val drop = kotlin.math.floor(safeDelta / stepTicks).toInt().coerceIn(0, clip.drumSteps.size)
                                        clip.drumSteps.drop(drop)
                                    } else {
                                        val prepend = kotlin.math.ceil(-safeDelta / stepTicks).toInt().coerceIn(0, StudioProject.MAX_DRUM_STEPS_PER_CLIP - clip.drumSteps.size)
                                        List(prepend) { false } + clip.drumSteps
                                    }
                                }
                                clip.copy(
                                    startTick = safeStart,
                                    lengthTicks = safeLength,
                                    sourceOffsetTicks = (clip.sourceOffsetTicks + safeDelta).coerceAtLeast(0L),
                                    midiNotes = nextNotes,
                                    drumSteps = nextDrumSteps
                                )
                            }
                        })
                    })
                    onProjectChange(resizedProject)
                    // Playback uses a prepared arrangement snapshot. Rebuild it at the current
                    // playhead after an edge-resize commit so the new clip end is honored.
                    onPlaybackRestart(resizedProject)
                },
                onMoveClip = { clipId, targetTrackId, startTick ->
                    val movedProject = project.moveClipToTrack(clipId, targetTrackId, startTick)
                    if (movedProject != null) {
                        onProjectChange(movedProject)
                        onPlaybackRestart(movedProject)
                        statusMessage = "Clip moved"
                    } else {
                        val source = project.tracks.firstOrNull { track -> track.clips.any { it.id == clipId } }
                        val target = project.tracks.firstOrNull { it.id == targetTrackId }
                        statusMessage = when {
                            source == null -> "Clip no longer exists"
                            target == null -> "Target track no longer exists"
                            source.kind != target.kind -> "Clip must stay on a matching track type"
                            else -> "Target track is full"
                        }
                    }
                }
            )
        }
        // Floating controls overlay the weighted Arrangement workspace without
        // becoming a separate Column child that could steal timeline height.
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp)
                .background(Raised.copy(alpha = 0.94f), RoundedCornerShape(28.dp))
                .padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                FloatingTimelineControl("FIT", "Fit clips to view") {
                    onZoom(fitZoom)
                    onVerticalZoom(1f)
                    onScroll(0f)
                    onScrollTracks(0f)
                }
                FloatingTimelineControl(if (snapEnabled) "SN" else "SN×", if (snapEnabled) "Snap on" else "Snap off") {
                    snapEnabled = !snapEnabled
                }
                FloatingTimelineControl(snapDivision.label, "Change snap division") {
                    snapDivision = snapDivision.next().let { if (it == SnapDivision.OFF) SnapDivision.BAR else it }
                }
                FloatingTimelineControl(if (showDevices) "DEV×" else "DEV", if (showDevices) "Hide devices" else "Show devices") {
                    onToggleDevices()
                }
            }
        Box(Modifier.align(Alignment.BottomEnd).fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            // Keep routine edit feedback out of the timeline; only validation errors appear inline.
            if (statusMessage != null) {
                val feedback = statusMessage!!
                val isError = feedback.contains("must", ignoreCase = true) ||
                    feedback.contains("limit", ignoreCase = true) ||
                    feedback.contains("no longer", ignoreCase = true) ||
                    feedback.contains("matching", ignoreCase = true) ||
                    feedback.contains("Nothing copied", ignoreCase = true)
                LaunchedEffect(feedback) {
                    delay(if (isError) 1800 else 650)
                    if (statusMessage == feedback) statusMessage = null
                }
                if (isError) {
                    Surface(
                        color = Color(0xFF3D2929).copy(alpha = 0.94f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(4.dp).clickable { statusMessage = null }
                    ) {
                        Text(feedback, color = Color(0xFFFFD4D4), fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), maxLines = 2)
                    }
                }
            }
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
                    listOf("Virtual instrument / MIDI" to "midi", "Audio track" to "audio", "Drum Step Sequencer" to "drum").forEach { pair ->
                        Text(pair.first, color = Color.White, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable { addTrack(pair.second, openEditor = pair.second != "audio") }.padding(9.dp))
                    }
                }
            }
        }
        if (renameClipId != null) {
            AlertDialog(
                onDismissRequest = { renameClipId = null },
                title = { Text("Rename clip") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("Clip name") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val id = renameClipId
                        val cleanName = renameText.trim()
                        if (id != null && cleanName.isNotEmpty()) {
                            onProjectChange(project.copy(tracks = project.tracks.map { item ->
                                item.copy(clips = item.clips.map { clip -> if (clip.id == id) clip.copy(name = cleanName) else clip })
                            }))
                            statusMessage = "Clip renamed"
                        }
                        renameClipId = null
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { renameClipId = null }) { Text("Cancel") } }
            )
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
    val context = LocalContext.current
    val clip = track?.clips?.firstOrNull { it.sourceUri != null }
    var waveform by remember(clip?.sourceUri) { mutableStateOf<List<Float>>(emptyList()) }
    var loading by remember(clip?.sourceUri) { mutableStateOf(false) }
    var loadError by remember(clip?.sourceUri) { mutableStateOf<String?>(null) }

    LaunchedEffect(clip?.sourceUri) {
        val source = clip?.sourceUri
        waveform = emptyList()
        loadError = null
        if (source.isNullOrBlank()) {
            loadError = "Pilih atau impor file audio untuk melihat waveform."
            return@LaunchedEffect
        }
        loading = true
        try {
            waveform = withContext(Dispatchers.IO) {
                val decodeJob = currentCoroutineContext()[Job]
                WaveformPeakCache.get(source, 240) ?: decodeWaveformPeaks(context, Uri.parse(source), 240) {
                    decodeJob?.ensureActive()
                }.also { WaveformPeakCache.put(source, 240, it) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loadError = failure.message ?: "Waveform tidak dapat dibaca."
        } finally {
            loading = false
        }
    }

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
            if (waveform.isNotEmpty()) {
                Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                    val mid = size.height / 2f
                    val dx = size.width / waveform.size.coerceAtLeast(1)
                    waveform.forEachIndexed { i, peak ->
                        val amplitude = peak.coerceIn(0f, 1f) * size.height * 0.48f
                        val x = (i + 0.5f) * dx
                        drawLine(Mint.copy(alpha = 0.9f), Offset(x, mid - amplitude), Offset(x, mid + amplitude), dx.coerceIn(1f, 3f))
                    }
                }
            } else {
                Text(
                    when {
                        loading -> "Membaca audio…"
                        loadError != null -> loadError!!
                        else -> "Waveform tidak tersedia"
                    },
                    color = MutedText, fontSize = 10.sp, modifier = Modifier.padding(12.dp)
                )
            }
        }
        Text(
            if (waveform.isNotEmpty()) "Waveform sumber • " + waveform.size + " peak" else "Waveform sumber diproses di background",
            color = MutedText, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun TrackHeader(
    track: StudioTrack, index: Int, verticalZoom: Float, scrollTracks: Float,
    rowTopDp: Float = index * 67f, rowHeightDp: Float = 67f,
    onDoubleTap: () -> Unit, onColorTap: () -> Unit, onMuteToggle: () -> Unit, onSoloToggle: () -> Unit,
    onResizeTrack: (Float) -> Unit = {}, onScratch: (Float) -> Unit = {}, onReorderTrack: (Float) -> Unit = {}
) {
    val accent = Color(track.colorArgb.toInt())
    var resizeDelta by remember(track.id) { mutableFloatStateOf(0f) }
    var reorderDelta by remember(track.id) { mutableFloatStateOf(0f) }
    var scratchDelta by remember(track.id) { mutableFloatStateOf(0f) }
    Row(
        Modifier.fillMaxWidth().offset(y = ((rowTopDp - scrollTracks) * verticalZoom).dp)
            .height((rowHeightDp * verticalZoom).dp).padding(bottom = 2.dp)
            .background(Color(0xFF26282D), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.width(6.dp).height(45.dp).background(accent, RoundedCornerShape(3.dp)).clickable { onColorTap() })
        Column(Modifier.weight(1f).combinedClickable(onClick = onDoubleTap, onDoubleClick = onDoubleTap, onLongClick = onDoubleTap)) {
            Text(track.name, fontSize = 9.sp, color = Color(0xFFF8FAFC), fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.kind.uppercase(), fontSize = 7.sp, color = MutedText)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("M", fontSize = 8.sp, color = if (track.muted) Red else MutedText, modifier = Modifier.clickable { onMuteToggle() })
                Text("S", fontSize = 8.sp, color = if (track.solo) Mint else MutedText, modifier = Modifier.clickable { onSoloToggle() })
                if (track.kind == "audio") {
                    Box(
                        Modifier.size(width = 24.dp, height = 19.dp)
                            .background(Color(0xFF17443D), RoundedCornerShape(5.dp))
                            .pointerInput(track.id) {
                                detectHorizontalDragGestures(
                                    onDragStart = { scratchDelta = 0f },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        scratchDelta += amount
                                        onScratch(amount)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("DJ", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Black) }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(
                Modifier.size(width = 23.dp, height = 18.dp)
                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
                    .pointerInput(track.id, rowTopDp, rowHeightDp) {
                        detectDragGestures(
                            onDragStart = { reorderDelta = 0f },
                            onDrag = { change, amount -> change.consume(); reorderDelta += amount.y },
                            onDragEnd = { onReorderTrack(reorderDelta); reorderDelta = 0f },
                            onDragCancel = { reorderDelta = 0f }
                        )
                    },
                contentAlignment = Alignment.Center
            ) { Text("⠿", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier.size(width = 23.dp, height = 18.dp)
                    .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                    .pointerInput(track.id, verticalZoom, rowHeightDp) {
                        detectDragGestures(
                            onDragStart = { resizeDelta = 0f },
                            onDrag = { change, amount -> change.consume(); resizeDelta += amount.y },
                            onDragEnd = { onResizeTrack(resizeDelta); resizeDelta = 0f },
                            onDragCancel = { resizeDelta = 0f }
                        )
                    },
                contentAlignment = Alignment.Center
            ) { Text("↕", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TimelineGrid(
    modifier: Modifier = Modifier,
    project: StudioProject,
    playheadTick: Long,
    isPlaying: Boolean,
    onSeekTick: (Long) -> Unit,
    zoom: Float,
    onZoom: (Float) -> Unit,
    scrollTicks: Float,
    onScroll: (Float) -> Unit,
    verticalZoom: Float,
    onVerticalZoom: (Float) -> Unit,
    snapEnabled: Boolean,
    snapDivision: SnapDivision,
    scrollTracks: Float,
    onScrollTracks: (Float) -> Unit,
    trackRowLayout: List<TrackRowLayout>,
    selectedClip: String?,
    onSelectClip: (String) -> Unit,
    onOpenEditor: (String) -> Unit,
    onClipLongPress: (String) -> Unit,
    onTimelineLongPress: () -> Unit,
    clipMenuClipId: String?,
    onClipAction: (String, String) -> Unit,
    onDismissClipMenu: () -> Unit,
    onResizeClip: (String, Long, Long) -> Unit,
    onMoveClip: (String, String, Long) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val baseBarWidthPx = with(density) { 74.dp.toPx() }
    val rulerHeightPx = with(density) { 24.dp.toPx() }
    val trackHeaderWidthPx = with(density) { 104.dp.toPx() }
    val timebase = remember(project.bpm, project.numerator, project.denominator, project.ppq) {
        TimelineTimebase(project.bpm, project.numerator, project.denominator, project.ppq)
    }
    val requestedPixelsPerBar = baseBarWidthPx * zoom
    val pixelsPerBar = TimelineViewport(timebase, requestedPixelsPerBar, scrollTicks.toDouble(), 0f)
        .effectivePixelsPerBar
    val viewport = remember(timebase, pixelsPerBar, scrollTicks) {
        arrangementPaneViewport(timebase, pixelsPerBar, scrollTicks.toDouble(), 0f)
    }
    val trackHeightPx = with(density) { 67.dp.toPx() } * verticalZoom
    val latestProject by rememberUpdatedState(project)
    val moveCommit by rememberUpdatedState(onMoveClip)
    val snapToGrid by rememberUpdatedState(snapEnabled)
    val trackCount = project.tracks.size
    val rulerPaint = remember(density) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = with(density) { 9.sp.toPx() } } }
    val currentZoom by rememberUpdatedState(zoom)
    val currentScroll by rememberUpdatedState(scrollTicks)
    val currentScrollTracks by rememberUpdatedState(scrollTracks)
    val currentVerticalZoom by rememberUpdatedState(verticalZoom)
    val currentOnVerticalZoom by rememberUpdatedState(onVerticalZoom)
    val currentOnScrollTracks by rememberUpdatedState(onScrollTracks)
    val currentOnZoom by rememberUpdatedState(onZoom)
    val currentOnScroll by rememberUpdatedState(onScroll)
    val currentOnSeekTick by rememberUpdatedState(onSeekTick)
    val currentTrackRows by rememberUpdatedState(trackRowLayout)
    var draggingClip by remember { mutableStateOf<String?>(null) }
    var dragFromTrack by remember { mutableIntStateOf(-1) }
    var dragOriginalTick by remember { mutableLongStateOf(0L) }
    var dragDx by remember { mutableFloatStateOf(0f) }
    var dragDy by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier
            .background(Color(0xFF17191C))
            .clipToBounds()
            .pointerInput(baseBarWidthPx) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    // A one-finger drag that starts on a clip belongs to the clip, never
                    // to viewport navigation. Empty arrangement space may still pan.
                    val startsOnClip = run {
                        val hitProject = latestProject
                        val rowY = firstDown.position.y - rulerHeightPx
                        val rowIndex = hitProject.tracks.indices.firstOrNull { index ->
                            val row = currentTrackRows.getOrNull(index) ?: TrackRowLayout(index * 67f, 67f)
                            val topPx = with(density) { ((row.topDp - currentScrollTracks) * currentVerticalZoom).dp.toPx() }
                            val heightPx = with(density) { (row.heightDp * currentVerticalZoom).dp.toPx() }
                            rowY >= topPx && rowY <= topPx + heightPx
                        }
                        val hitTrack = rowIndex?.let { hitProject.tracks.getOrNull(it) }
                        val barWidthPx = TimelineViewport(timebase, baseBarWidthPx * currentZoom, currentScroll.toDouble(), size.width.toFloat()).effectivePixelsPerBar
                        hitTrack?.clips?.any { clip ->
                            val hitViewport = arrangementPaneViewport(timebase, barWidthPx, currentScroll.toDouble(), size.width.toFloat())
                             val left = hitViewport.timeToViewportX(clip.startTick)
                            val width = hitViewport.tickDeltaToPixels(clip.lengthTicks)
                            val right = left + width
                            val pointerX = firstDown.position.x
                            // A pointer to the right of the final clip is empty canvas.
                            // This is what lets the user begin viewport panning after clips.
                            pointerX >= left && pointerX <= right
                        } == true
                    }
                    var previousSinglePosition = firstDown.position
                    var previousCentroid = Offset.Zero
                    var previousSpan = 0f
                    var initialized = false
                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.filter { it.pressed }
                        if (pointers.size >= 2) {
                            val centroid = Offset(
                                pointers.map { it.position.x }.average().toFloat(),
                                pointers.map { it.position.y }.average().toFloat()
                            )
                            val span = pointers.map { (it.position - centroid).getDistance() }.average().toFloat() * 2f
                            if (initialized) {
                                val zoomFactor = if (previousSpan > 1f && span > 1f) (span / previousSpan).coerceIn(0.85f, 1.18f) else 1f
                                val oldZoom = currentZoom.coerceIn(0.1f, 4f)
                                val newZoom = (oldZoom * zoomFactor).coerceIn(0.1f, 4f)
                                val oldBarWidth = TimelineViewport(timebase, baseBarWidthPx * oldZoom, currentScroll.toDouble(), size.width.toFloat()).effectivePixelsPerBar
                                val newBarWidth = TimelineViewport(timebase, baseBarWidthPx * newZoom, currentScroll.toDouble(), size.width.toFloat()).effectivePixelsPerBar
                                val pinchViewport = TimelineViewport(
                                    timebase, oldBarWidth, currentScroll.toDouble(), size.width.toFloat()
                                )
                                val anchoredViewport = pinchViewport.zoomKeepingAnchor(
                                    newBarWidth, previousCentroid.x
                                )
                                val anchorTick = pinchViewport.viewportXToTime(previousCentroid.x).toDouble()
                                val newScroll = (anchoredViewport.scrollTimeTicks +
                                    (previousCentroid.x - centroid.x).toDouble() *
                                    timebase.ticksPerBar.toDouble() / newBarWidth.toDouble()
                                ).coerceIn(0.0, Long.MAX_VALUE.toDouble()).toFloat()
                                 val oldVerticalZoom = currentVerticalZoom.coerceIn(0.65f, 1.8f)
                                 val newVerticalZoom = (oldVerticalZoom * zoomFactor).coerceIn(0.65f, 1.8f)
                                 val anchorTrackDp = currentScrollTracks + (previousCentroid.y - rulerHeightPx) / (density.density * oldVerticalZoom).coerceAtLeast(0.1f)
                                 val newTrackScroll = (anchorTrackDp - (centroid.y - rulerHeightPx) / (density.density * newVerticalZoom).coerceAtLeast(0.1f)).coerceAtLeast(0f)
                                if (kotlin.math.abs(newZoom - oldZoom) > 0.0005f) currentOnZoom(newZoom)
                                if (kotlin.math.abs(newScroll - currentScroll) > 0.0005f) currentOnScroll(newScroll)
                                if (kotlin.math.abs(newVerticalZoom - oldVerticalZoom) > 0.0005f) currentOnVerticalZoom(newVerticalZoom)
                                if (kotlin.math.abs(newTrackScroll - currentScrollTracks) > 0.0005f) currentOnScrollTracks(newTrackScroll)
                            }
                            previousCentroid = centroid
                            previousSpan = span
                            initialized = true
                            event.changes.forEach { change -> if (change.position != change.previousPosition) change.consume() }
                        } else {
                            initialized = false
                            previousSpan = 0f
                            val pointer = pointers.firstOrNull()
                            if (pointer != null) {
                                val delta = pointer.position - previousSinglePosition
                                if (!startsOnClip && (kotlin.math.abs(delta.x) > 0.01f || kotlin.math.abs(delta.y) > 0.01f)) {
                                    val timebaseNow = TimelineTimebase(latestProject.bpm, latestProject.numerator, latestProject.denominator, latestProject.ppq)
                                       val barWidthPx = TimelineViewport(timebaseNow, baseBarWidthPx * currentZoom, currentScroll.toDouble(), size.width.toFloat()).effectivePixelsPerBar
                                    val newScroll = arrangementPaneViewport(timebaseNow, barWidthPx, currentScroll.toDouble(), size.width.toFloat()).panByScreenPixels(delta.x).scrollTimeTicks.toFloat()
                                    val newTrackScroll = (currentScrollTracks - delta.y / (density.density * currentVerticalZoom).coerceAtLeast(0.1f)).coerceAtLeast(0f)
                                    if (kotlin.math.abs(newScroll - currentScroll) > 0.0005f) currentOnScroll(newScroll)
                                    if (kotlin.math.abs(newTrackScroll - currentScrollTracks) > 0.0005f) currentOnScrollTracks(newTrackScroll)
                                    pointer.consume()
                                }
                                previousSinglePosition = pointer.position
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val timelineWidthPx = with(density) { maxWidth.toPx() }
        LaunchedEffect(isPlaying, playheadTick, zoom, scrollTicks, timelineWidthPx, timebase) {
            if (isPlaying && timelineWidthPx > 0f) {
                val barWidthPx = viewport.effectivePixelsPerBar
                val currentViewport = TimelineViewport(
                    timebase, barWidthPx, scrollTicks.toDouble(), timelineWidthPx
                )
                val currentX = currentViewport.timeToViewportX(playheadTick)
                val leftGuard = timelineWidthPx * 0.08f
                // Start following sooner so playback from later bars scrolls the ruler
                // underneath a steady playhead instead of appearing stalled.
                val rightGuard = timelineWidthPx * 0.70f
                // Re-anchor at the guard line that was crossed, not back at 70% of
                // the viewport. This prevents the visible ruler/playhead "jump".
                val targetX = when {
                    currentX < leftGuard -> leftGuard
                    currentX > rightGuard -> rightGuard
                    else -> return@LaunchedEffect
                }
                val ticksPerPixel = timebase.ticksPerBar.toDouble() / barWidthPx
                val nextScroll = (playheadTick.toDouble() - targetX.toDouble() * ticksPerPixel)
                    .coerceIn(0.0, Long.MAX_VALUE.toDouble()).toFloat()
                if (kotlin.math.abs(nextScroll - scrollTicks) > 0.5f) currentOnScroll(nextScroll)
            }
        }
        // Keep long-press handling on the background layer so it cannot steal a
        // drag that starts on a clip or its resize handle.
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { onTimelineLongPress() })
        }) {
            val canvasViewport = viewport.copy(viewportWidthPx = size.width)
             val firstVisibleBar = canvasViewport.firstVisibleBar().coerceAtMost(4096L).toInt()
             val lastVisibleBar = canvasViewport.lastVisibleBar().coerceAtMost(4096L).toInt()
             val barWidth = canvasViewport.effectivePixelsPerBar
             for (bar in firstVisibleBar..lastVisibleBar) {
                 val x = canvasViewport.timeToViewportX(bar.toLong() * timebase.ticksPerBar)
                 if (x >= -barWidth && x <= size.width + barWidth) {
                     drawLine(if (bar % 4 == 0) Color(0xFF51555D) else Color(0xFF32363D),
                         Offset(x, rulerHeightPx), Offset(x, size.height),
                         if (bar % 4 == 0) 1.5f else 0.7f)
                     val beatTicks = timebase.ticksPerBeat
                     val beatPixels = beatTicks * canvasViewport.pixelsPerTick
                     // Use a progressive musical grid like FL Studio Mobile: the
                     // horizontal zoom decides which subdivisions are worth drawing.
                     val subdivision = when {
                         beatPixels >= 80.0 -> beatTicks / 16L
                         beatPixels >= 52.0 -> beatTicks / 8L
                         beatPixels >= 30.0 -> beatTicks / 4L
                         beatPixels >= 16.0 -> beatTicks / 2L
                         else -> 0L
                     }
                     for (beat in 0 until timebase.safeNumerator) {
                         val beatTick = bar.toLong() * timebase.ticksPerBar + beat.toLong() * beatTicks
                         val beatX = canvasViewport.timeToViewportX(beatTick)
                         if (beat > 0) drawLine(Color(0xFF292D33), Offset(beatX, rulerHeightPx), Offset(beatX, size.height), 0.8f)
                         if (subdivision > 0L) {
                             var tickOffset = subdivision
                             while (tickOffset < beatTicks) {
                                 val subX = canvasViewport.timeToViewportX(beatTick + tickOffset)
                                 val alpha = when (tickOffset) {
                                     beatTicks / 2L -> 0.48f
                                     beatTicks / 4L -> 0.38f
                                     else -> 0.24f
                                 }
                                 drawLine(Color(0xFF30343B).copy(alpha = alpha),
                                     Offset(subX, rulerHeightPx), Offset(subX, size.height), 0.55f)
                                 tickOffset += subdivision
                             }
                         }
                     }
                 }
             }
            trackRowLayout.forEach { row ->
                val rowTopPx = rulerHeightPx + with(density) {
                    ((row.topDp - scrollTracks) * verticalZoom).dp.toPx()
                }
                if (rowTopPx >= rulerHeightPx && rowTopPx <= size.height) {
                    drawLine(Color(0xFF3A3D44), Offset(0f, rowTopPx), Offset(size.width, rowTopPx), 1f)
                }
            }
        }

        // Draw ruler labels only for bars that are currently visible.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(scrollTicks, zoom, timebase) {
                    detectTapGestures(onTap = { offset ->
                        currentOnSeekTick(viewport.copy(viewportWidthPx = size.width.toFloat()).viewportXToTime(offset.x))
                    })
                }
        ) {
            val rulerViewport = viewport.copy(viewportWidthPx = size.width)
             val barWidth = rulerViewport.effectivePixelsPerBar
             val firstVisibleBar = rulerViewport.firstVisibleBar().coerceAtMost(4096L).toInt()
             val lastVisibleBar = rulerViewport.lastVisibleBar().coerceAtMost(4096L).toInt()
             val labelEveryBar = barWidth >= with(density) { 94.dp.toPx() }
             for (bar in firstVisibleBar..lastVisibleBar) {
                 val x = rulerViewport.timeToViewportX(bar.toLong() * timebase.ticksPerBar)
                 if (bar % 4 == 0 || labelEveryBar) {
                    drawContext.canvas.nativeCanvas.drawText(
                        (bar + 1).toString(),
                        x + with(density) { 5.dp.toPx() },
                        size.height * 0.68f,
                        rulerPaint
                    )
                } else if (bar % 4 != 0) {
                     drawCircle(color = MutedText, radius = with(density) { 1.dp.toPx() },
                         center = Offset(x, size.height * 0.55f))
                 }
            }
            drawRect(Color(0xFF2B2E34), size = Size(size.width, with(density) { 2.dp.toPx() }))
            val playheadX = rulerViewport.timeToViewportX(playheadTick)
            if (playheadX >= 0f && playheadX <= size.width) {
                val markerRadius = with(density) { 3.dp.toPx() }
                drawLine(Color(0xFFFF4F64), Offset(playheadX, 0f), Offset(playheadX, size.height), 1.6f)
                drawCircle(Color(0xFFFF4F64), markerRadius, Offset(playheadX, markerRadius))
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(top = 24.dp)) {
            val timelineViewportHeightDp = maxHeight.value
            Box(Modifier.fillMaxSize()) {
                project.tracks.forEachIndexed { trackIndex, track ->
                    val row = trackRowLayout.getOrNull(trackIndex) ?: TrackRowLayout(trackIndex * 67f, 67f)
                    val rowTop = (row.topDp - scrollTracks) * verticalZoom
                    val rowHeight = row.heightDp * verticalZoom
                    if (rowTop + rowHeight >= -rowHeight && rowTop <= timelineViewportHeightDp) {
                BoxWithConstraints(Modifier.fillMaxWidth().offset(y = rowTop.dp).height(rowHeight.dp)) {
                    val viewportWidthPx = with(density) { maxWidth.toPx() }
                    val barWidth = viewport.effectivePixelsPerBar
                    track.clips.forEach { clip ->
                        // Clip geometry is derived only from musical duration and the shared
                        // viewport scale; a fixed visual minimum makes clip lengths jump on zoom.
                        val clipWidthPx = viewport.tickDeltaToPixels(clip.lengthTicks).coerceIn(0.01f, 1_000_000f)
                        val clipWidthDp = with(density) { clipWidthPx.toDp().value }.coerceAtLeast(0.01f)
                        val resizeGripWidthDp = minOf(18f, clipWidthDp * 0.38f).coerceAtLeast(0.01f)
                        val resizeGripWidthPx = with(density) { resizeGripWidthDp.dp.toPx() }
                        val displayLeftPx = viewport.timeToViewportX(clip.startTick)
                        // Avoid composing or drawing clips fully outside the viewport.
                        if (displayLeftPx + clipWidthPx >= 0f && displayLeftPx <= viewportWidthPx) {
                        val accent = Color((clip.colorArgb ?: track.colorArgb).toInt())
                        Column(
                            Modifier
                                .offset(x = with(density) { displayLeftPx.toDp() }, y = 0.dp)
                                .graphicsLayer {
                                    translationX = if (draggingClip == clip.id) dragDx else 0f
                                    translationY = if (draggingClip == clip.id) dragDy else 0f
                                }
                                .padding(top = 7.dp, bottom = 7.dp)
                                .width(clipWidthDp.dp)
                                .fillMaxHeight()
                                .background(accent.copy(alpha = if (selectedClip == clip.id) 0.78f else 0.42f), RoundedCornerShape(5.dp))
                                .then(if (selectedClip == clip.id) Modifier.padding(1.dp) else Modifier)
                                .pointerInput(clip.id, trackIndex, zoom, trackHeightPx) {
                                    // The trailing grip owns resize gestures. Do not let the clip-body
                                    // drag handler consume those pointer changes first.
                                    var startedOnResizeGrip = false
                                    detectDragGestures(
                                        onDragStart = { down ->
                                            startedOnResizeGrip = down.x <= resizeGripWidthPx || down.x >= size.width - resizeGripWidthPx
                                            if (!startedOnResizeGrip) {
                                                draggingClip = clip.id
                                                dragFromTrack = trackIndex
                                                dragOriginalTick = clip.startTick
                                                dragDx = 0f
                                                dragDy = 0f
                                                onSelectClip(clip.id)
                                            }
                                        },
                                        onDrag = { change, amount ->
                                            if (!startedOnResizeGrip) {
                                                change.consume()
                                                dragDx += amount.x
                                                dragDy += amount.y
                                            }
                                        },
                                        onDragEnd = {
                                            if (!startedOnResizeGrip) {
                                            val currentTracks = latestProject.tracks
                                            val startIndex = currentTracks.getOrNull(dragFromTrack)
                                                ?.takeIf { track -> track.clips.any { it.id == clip.id } }
                                                ?.let { dragFromTrack }
                                                ?: currentTracks.indexOfFirst { track -> track.clips.any { it.id == clip.id } }
                                            val source = currentTracks.getOrNull(startIndex)
                                            val rowLayout = currentTrackRows
                                            val startRow = rowLayout.getOrNull(startIndex)
                                            val desiredCenter = if (startRow == null) 0f else
                                                startRow.topDp + startRow.heightDp / 2f +
                                                    dragDy / (density.density * currentVerticalZoom.coerceAtLeast(0.1f))
                                            val targetIndex = if (startRow == null || rowLayout.isEmpty()) {
                                                (startIndex + kotlin.math.round(dragDy / trackHeightPx.coerceAtLeast(1f)).toInt())
                                                    .coerceIn(0, currentTracks.lastIndex.coerceAtLeast(0))
                                            } else {
                                                rowLayout.indices.minByOrNull { index ->
                                                    kotlin.math.abs(rowLayout[index].topDp + rowLayout[index].heightDp / 2f - desiredCenter)
                                                } ?: startIndex
                                            }
                                            val target = currentTracks.getOrNull(targetIndex)
                                            val barWidthPx = viewport.effectivePixelsPerBar
                                            val dragViewport = TimelineViewport(
                                                timebase, barWidthPx, currentScroll.toDouble(),
                                                timelineWidthPx
                                            )
                                            // Convert the total gesture delta to ticks from its original anchor.
                                            // This avoids drift if playback autoscroll changes the viewport mid-drag.
                                            val deltaTicks = dragViewport.pixelsToTickDelta(dragDx)
                                            val rawStartTick = (dragOriginalTick.toDouble() + deltaTicks.toDouble())
                                                .coerceIn(0.0, Long.MAX_VALUE.toDouble()).toLong()
                                            val newTick = dragViewport.snapTick(rawStartTick, snapDivision, snapToGrid)
                                            if (startIndex >= 0 && source != null && target != null && source.kind == target.kind) {
                                                moveCommit(clip.id, target.id, newTick)
                                            }
                                            draggingClip = null
                                            dragFromTrack = -1
                                            dragDx = 0f
                                            dragDy = 0f
                                            }
                                            startedOnResizeGrip = false
                                        },
                                        onDragCancel = {
                                            if (!startedOnResizeGrip) {
                                                draggingClip = null
                                                dragFromTrack = -1
                                                dragDx = 0f
                                                dragDy = 0f
                                            }
                                            startedOnResizeGrip = false
                                        }
                                    )
                                }
                                .combinedClickable(
                                    onClick = { onSelectClip(clip.id) },
                                    onDoubleClick = { onSelectClip(clip.id); onOpenEditor(clip.id) },
                                    onLongClick = { onSelectClip(clip.id); onClipLongPress(clip.id) }
                                )
                                .padding(4.dp)
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                Column(Modifier.fillMaxSize().padding(4.dp)) {
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
                                AudioClipWaveform(clip.sourceUri, accent)
                            }
                                }
                                var leftResizeDeltaPx by remember(clip.id) { mutableFloatStateOf(0f) }
                                Box(
                                    Modifier.align(Alignment.CenterStart)
                                        .width(resizeGripWidthDp.dp)
                                        .height(34.dp)
                                        .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                        .pointerInput(clip.id, zoom, clip.startTick, clip.lengthTicks, clip.sourceOffsetTicks) {
                                            detectDragGestures(
                                                onDragStart = { leftResizeDeltaPx = 0f; onSelectClip(clip.id) },
                                                onDrag = { change, amount -> change.consume(); leftResizeDeltaPx += amount.x },
                                                onDragEnd = {
                                                    val originalEnd = clip.startTick + clip.lengthTicks.coerceAtLeast(1L)
                                                    val snapStep = snapDivision.ticks(timebase)
                                                    val minimumLength = if (snapToGrid && snapStep > 0L) snapStep else 1L
                                                    val earliestStart = (clip.startTick - clip.sourceOffsetTicks.coerceAtLeast(0L)).coerceAtLeast(0L)
                                                    val latestStart = (originalEnd - minimumLength).coerceAtLeast(earliestStart)
                                                    val rawStart = (clip.startTick + viewport.pixelsToTickDelta(leftResizeDeltaPx)).coerceAtLeast(0L)
                                                    val snappedStart = if (snapToGrid && snapStep > 0L) timebase.snap(rawStart, snapDivision) else rawStart
                                                    val newStart = snappedStart.coerceIn(earliestStart, latestStart)
                                                    val newLength = (originalEnd - newStart).coerceAtLeast(minimumLength)
                                                    if (newStart != clip.startTick && newLength > 0L) {
                                                        onResizeClip(clip.id, newStart, newLength)
                                                    }
                                                    leftResizeDeltaPx = 0f
                                                },
                                                onDragCancel = { leftResizeDeltaPx = 0f }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("↔", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                var resizeDeltaPx by remember(clip.id) { mutableFloatStateOf(0f) }
                                Box(
                                    Modifier.align(Alignment.CenterEnd)
                                        .width(resizeGripWidthDp.dp).height(34.dp)
                                        .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                        .pointerInput(clip.id, zoom, clip.lengthTicks) {
                                            detectDragGestures(
                                                onDragStart = { resizeDeltaPx = 0f },
                                                onDrag = { change, amount -> change.consume(); resizeDeltaPx += amount.x },
                                                onDragEnd = {
                                                    val originalEndTick = clip.startTick + clip.lengthTicks.coerceAtLeast(1L)
                                                    val rawEndTick = (originalEndTick.toDouble() +
                                                        viewport.pixelsToTickDelta(resizeDeltaPx).toDouble())
                                                        .coerceIn((clip.startTick + 1L).toDouble(), Long.MAX_VALUE.toDouble()).toLong()
                                                     val snapStep = snapDivision.ticks(timebase)
                                                     val snappedEndTick = if (snapToGrid && snapStep > 0L) timebase.snap(rawEndTick, snapDivision) else rawEndTick
                                                     val newLength = (snappedEndTick - clip.startTick).coerceAtLeast(if (snapToGrid && snapStep > 0L) snapStep else 1L)
                                                    onResizeClip(clip.id, clip.startTick, newLength)
                                                    resizeDeltaPx = 0f
                                                },
                                                onDragCancel = { resizeDeltaPx = 0f }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("↔", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        // Overlay the cursor after clips so it stays visible over audio, MIDI and drum patterns.
        Canvas(Modifier.fillMaxSize().padding(top = 24.dp).clipToBounds()) {
            val playheadViewport = TimelineViewport(
                timebase, viewport.effectivePixelsPerBar, scrollTicks.toDouble(), size.width
            )
            val x = playheadViewport.timeToViewportX(playheadTick)
            if (x >= 0f && x <= size.width) {
                drawLine(Color(0xFFFF4F64), Offset(x, 0f), Offset(x, size.height), 1.5f)
            }
        }
        val menuTrack = project.tracks.firstOrNull { t -> t.clips.any { it.id == clipMenuClipId } }
        val menuClip = menuTrack?.clips?.firstOrNull { it.id == clipMenuClipId }
        if (menuTrack != null && menuClip != null && clipMenuClipId != null) {
            val trackIndex = project.tracks.indexOfFirst { it.id == menuTrack.id }
            val barWidth = viewport.effectivePixelsPerBar
            val menuViewport = TimelineViewport(
                timebase, barWidth, scrollTicks.toDouble(), with(density) { maxWidth.toPx() },
                with(density) { 104.dp.toPx() }
            )
            val clipLeft = with(density) { menuViewport.timeToViewportX(menuClip.startTick).toDp() }
            val menuWidth = 258.dp
            val menuHeight = 29.dp * 4f + 3.dp * 3f + 8.dp
            val maxX = (maxWidth - menuWidth - 4.dp).coerceAtLeast(0.dp)
            val x = clipLeft.coerceIn(0.dp, maxX)
            val menuRow = trackRowLayout.getOrNull(trackIndex) ?: TrackRowLayout(trackIndex * 67f, 67f)
            val rawY = 24.dp + ((menuRow.topDp - scrollTracks) * verticalZoom).dp - 35.dp
            val maxY = (maxHeight - menuHeight - 4.dp).coerceAtLeast(24.dp)
            val y = rawY.coerceIn(24.dp, maxY)
            val actions = listOf("Open editor", "Cut", "Copy", "Duplicate", "Color", "Rename", "Snap", "Delete", "×")
            Column(
                Modifier.offset(x = x, y = y)
                    .width(menuWidth)
                    .background(Color(0xFF30343A), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                actions.chunked(3).forEach { rowActions ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowActions.forEach { action ->
                            val label = when (action) { "Open editor" -> "Edit"; "Duplicate" -> "Dup"; "Rename" -> "Name"; "Delete" -> "Del"; else -> action }
                            Surface(
                                color = if (action == "Open editor") Mint.copy(alpha = 0.18f) else Color(0xFF23262B),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(29.dp).clickable {
                                    if (action == "×") onDismissClipMenu() else onClipAction(action, clipMenuClipId)
                                }
                            ) {
                                Box(Modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                    Text(label, color = if (action == "Open editor") Mint else Color.White,
                                        fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                        repeat(3 - rowActions.size) {
                            Spacer(Modifier.weight(1f).height(29.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PianoRollWorkspace(
    track: StudioTrack?,
    project: StudioProject,
    onProjectChange: (StudioProject) -> Unit,
    onBack: () -> Unit
) {
    val clip = track?.clips?.firstOrNull()
    val notes = clip?.midiNotes ?: emptyList()
    Column(Modifier.fillMaxSize().background(CanvasSurface, RoundedCornerShape(8.dp)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("PIANO ROLL", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Text(track?.name ?: "Select a MIDI track", color = MutedText, fontSize = 9.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text("TAP GRID: ADD / REMOVE NOTE", color = MutedText, fontSize = 9.sp)
            Spacer(Modifier.width(8.dp))
            Surface(color = Raised, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(27.dp).clickable { onBack() }) {
                Box(contentAlignment = Alignment.Center) { Text("×", color = Color.White, fontSize = 20.sp) }
            }
        }
        Canvas(Modifier.fillMaxSize().padding(top = 4.dp).pointerInput(track?.id, clip?.id, notes) {
            detectTapGestures { point ->
                if (track == null || clip == null) return@detectTapGestures
                val rowH = size.height / 24f
                val colW = size.width / 32f
                val row = (point.y / rowH).toInt().coerceIn(0, 23)
                val col = (point.x / colW).toInt().coerceIn(0, 31)
                val pitch = (84 - row * 3).coerceIn(0, 127)
                val startTick = col * 240L
                val existing = notes.firstOrNull { it.pitch == pitch && it.startTick == startTick }
                val updatedNotes = if (existing != null) notes.filterNot { it.id == existing.id }
                    else notes + com.studiowave.app.data.MidiNote(pitch = pitch, startTick = startTick, durationTicks = 240)
                onProjectChange(project.copy(tracks = project.tracks.map { item ->
                    if (item.id == track.id) item.copy(clips = item.clips.map { c -> if (c.id == clip.id) c.copy(midiNotes = updatedNotes) else c }) else item
                }))
            }
        }) {
            val rowH = size.height / 24f
            val colW = size.width / 32f
            for (r in 0..24) drawLine(if (r % 12 == 0) Color(0xFF5D626B) else Color(0xFF343840),
                Offset(0f, r * rowH), Offset(size.width, r * rowH), if (r % 12 == 0) 1.5f else 0.6f)
            for (col in 0..32) drawLine(if (col % 4 == 0) Color(0xFF5D626B) else Color(0xFF343840),
                Offset(col * colW, 0f), Offset(col * colW, size.height), if (col % 4 == 0) 1.2f else 0.6f)
            notes.forEach { note ->
                val x = note.startTick.toFloat() / 240f * colW
                val y = (84 - note.pitch).toFloat() / 3f * rowH
                val width = note.durationTicks.toFloat() / 240f * colW
                drawRoundRect(Mint, Offset(x, y.coerceIn(0f, size.height - rowH)), Size(width.coerceAtLeast(3f), rowH * 0.72f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
            }
        }
    }
}

@Composable
private fun MixerWorkspace(
    project: StudioProject,
    master: Float,
    onMasterChange: (Float) -> Unit,
    onProjectChange: (StudioProject) -> Unit
) {
    Row(
        Modifier.fillMaxSize().background(CanvasSurface, RoundedCornerShape(8.dp)).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        project.tracks.forEach { track ->
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .background(Color(0xFF25282D), RoundedCornerShape(6.dp)).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(track.name, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    SmallAction(if (track.muted) "M✓" else "M") {
                        onProjectChange(project.copy(tracks = project.tracks.map {
                            if (it.id == track.id) it.copy(muted = !it.muted) else it
                        }))
                    }
                    SmallAction(if (track.solo) "S✓" else "S") {
                        onProjectChange(project.copy(tracks = project.tracks.map {
                            if (it.id == track.id) it.copy(solo = !it.solo) else it
                        }))
                    }
                }
                Spacer(Modifier.height(4.dp))
                val panLabel = when {
                    track.pan <= -0.95f -> "L"
                    track.pan >= 0.95f -> "R"
                    else -> (track.pan * 100).toInt().toString()
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    AbletonKnob(
                        "VOL",
                        track.volume.coerceIn(0f, 1.5f) / 1.5f,
                        { amount ->
                            val mapped = amount * 1.5f
                            onProjectChange(project.copy(tracks = project.tracks.map {
                                if (it.id == track.id) it.copy(volume = mapped) else it
                            }))
                        },
                        ((track.volume.coerceIn(0f, 1.5f) / 1.5f) * 100).toInt().toString() + "%",
                        Modifier.weight(1f).height(52.dp),
                        dialSize = 24.dp
                    )
                    AbletonKnob(
                        "PAN",
                        (track.pan + 1f) / 2f,
                        { amount ->
                            val mapped = amount * 2f - 1f
                            onProjectChange(project.copy(tracks = project.tracks.map {
                                if (it.id == track.id) it.copy(pan = mapped) else it
                            }))
                        },
                        panLabel,
                        Modifier.weight(1f).height(52.dp),
                        accent = Color(0xFFFFB347),
                        dialSize = 24.dp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                    drawRoundRect(Color(0xFF131518), Offset(size.width * 0.34f, 0f), Size(size.width * 0.32f, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                    val h = size.height * track.volume.coerceIn(0f, 1.5f) / 1.5f
                    drawRoundRect(
                        if (track.muted) Red else Mint,
                        Offset(size.width * 0.34f, size.height - h),
                        Size(size.width * 0.32f, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                    )
                }
                Text(
                    if (track.muted) "MUTED" else if (track.solo) "SOLO" else track.kind.uppercase(),
                    color = if (track.muted) Red else MutedText,
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight()
                .background(Color(0xFF25282D), RoundedCornerShape(6.dp)).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("MASTER", fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                drawRoundRect(Color(0xFF131518), Offset(size.width * 0.34f, 0f), Size(size.width * 0.32f, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                val h = size.height * master.coerceIn(0f, 1f)
                drawRoundRect(Mint, Offset(size.width * 0.34f, size.height - h), Size(size.width * 0.32f, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
            }
            Text("GAIN " + (master * 100).toInt() + "%", color = MutedText, fontSize = 8.sp)
            Slider(value = master, onValueChange = onMasterChange, valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().height(24.dp))
        }
    }
}

@Composable
private fun BrowserWorkspace(onImportAudio: () -> Unit) {
    val entries = listOf(
        Triple("hard-kick-01.wav", "KICK  /  HARD TRAP", "WAV"),
        Triple("hard-snare-01.wav", "SNARE  /  HARD TRAP", "WAV"),
        Triple("hi-hat-closed-01.wav", "CLOSED HAT", "WAV"),
        Triple("808-bass-sub.wav", "808 SUB BASS", "WAV"),
        Triple("bounce-kick-01.wav", "KICK  /  BOUNCE", "WAV"),
        Triple("vintage-kick-01.wav", "KICK  /  VINTAGE", "WAV")
    )
    Column(Modifier.fillMaxSize().background(CanvasSurface, RoundedCornerShape(7.dp)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("BROWSER", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Text("FACTORY LIBRARY", color = Color(0xFFAEB4BD), fontSize = 8.sp)
            Spacer(Modifier.weight(1f))
            Text("CC0  •  OFFLINE AFTER BUILD", color = Color(0xFF80DFD2), fontSize = 7.sp)
            Spacer(Modifier.width(8.dp))
            Button(onClick = onImportAudio, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), modifier = Modifier.height(27.dp), shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444A50))) {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color(0xFF80DFD2), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("IMPORT AUDIO", fontSize = 8.sp, color = Color.White)
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.width(142.dp).fillMaxHeight().background(Color(0xFF26292D), RoundedCornerShape(4.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("PLACES", color = Color(0xFFAEB4BD), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                listOf("Factory", "DirectWave", "Drums", "User Library").forEachIndexed { index, label ->
                    Row(Modifier.fillMaxWidth().height(27.dp).background(if (index == 1) Color(0xFF444950) else Color.Transparent, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).background(if (index == 1) Color(0xFFFFB347) else Color(0xFF737A84), RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = if (index == 1) Color.White else Color(0xFFD2D5DA), fontSize = 9.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("STUDIOWAVE", color = Color(0xFF7DD9CB), fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF26292D), RoundedCornerShape(4.dp)).padding(7.dp)) {
                Text("DIRECTWAVE / FACTORY ONE-SHOTS", color = Color(0xFFE7E9EC), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text("Boochi44 free-drum-samples  •  CC0 1.0", color = MutedText, fontSize = 7.sp)
                Spacer(Modifier.height(5.dp))
                entries.forEachIndexed { index, entry ->
                    Row(Modifier.fillMaxWidth().height(34.dp).background(if (index == 0) Color(0xFF3B4247) else Color(0xFF2D3035), RoundedCornerShape(3.dp)).padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(20.dp).background(if (index == 0) Color(0xFF80DFD2) else Color(0xFF59616A), RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.first, color = Color(0xFFF1F2F4), fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(entry.second, color = MutedText, fontSize = 6.sp, maxLines = 1)
                        }
                        Text(entry.third, color = Color(0xFF9DA3AD), fontSize = 7.sp)
                    }
                    Spacer(Modifier.height(3.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("Import audio remains available. Sample audition/PCM decoding needs sampler-engine integration.", color = MutedText, fontSize = 7.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun DirectWaveWorkspace(onOpenBrowser: () -> Unit, onBack: () -> Unit) {
    var selectedSample by remember { mutableIntStateOf(0) }
    var cutoff by remember { mutableFloatStateOf(0.72f) }
    var resonance by remember { mutableFloatStateOf(0.22f) }
    var attack by remember { mutableFloatStateOf(0.05f) }
    var decay by remember { mutableFloatStateOf(0.28f) }
    var sustain by remember { mutableFloatStateOf(0.70f) }
    var release by remember { mutableFloatStateOf(0.35f) }
    var pitch by remember { mutableFloatStateOf(0.50f) }
    val samples = listOf(
        Triple("HARD KICK 01", "hard-kick-01.wav", "KICK / HARD TRAP"),
        Triple("HARD SNARE 01", "hard-snare-01.wav", "SNARE / HARD TRAP"),
        Triple("CLOSED HAT 01", "hi-hat-closed-01.wav", "HI-HAT / HARD TRAP"),
        Triple("808 SUB BASS", "808-bass-sub.wav", "BASS / HARD TRAP"),
        Triple("BOUNCE KICK 01", "bounce-kick-01.wav", "KICK / BOUNCE"),
        Triple("VINTAGE KICK 01", "vintage-kick-01.wav", "KICK / VINTAGE")
    )
    Column(Modifier.fillMaxSize().background(Color(0xFF202225), RoundedCornerShape(7.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(30.dp).background(Color(0xFF2A2D31), RoundedCornerShape(4.dp)).padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(Color(0xFFFFA52E), RoundedCornerShape(50.dp)))
            Text("DIRECTWAVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text("SAMPLER  /  INSTRUMENT DEVICE", color = Color(0xFFB8BDC5), fontSize = 8.sp)
            Spacer(Modifier.weight(1f))
            Text("DSP NOT CONNECTED", color = Color(0xFFFFB347), fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(5.dp))
            SmallAction("BROWSER") { onOpenBrowser() }
            SmallAction("BACK") { onBack() }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.width(176.dp).fillMaxHeight().background(Color(0xFF2D3034), RoundedCornerShape(5.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("FACTORY SAMPLES", color = Color(0xFFC0C5CC), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                samples.forEachIndexed { index, sample ->
                    Row(Modifier.fillMaxWidth().height(34.dp).background(if (selectedSample == index) Color(0xFF454A50) else Color(0xFF25272B), RoundedCornerShape(3.dp)).clickable { selectedSample = index }.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(21.dp).background(if (selectedSample == index) Color(0xFFFFB347) else Color(0xFF5F6670), RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sample.first, color = Color(0xFFF0F1F3), fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(sample.second, color = Color(0xFFA5ABB4), fontSize = 6.sp, maxLines = 1)
                        }
                        if (selectedSample == index) Text("●", color = Color(0xFF80DFD2), fontSize = 8.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).background(Color(0xFF80DFD2), RoundedCornerShape(50.dp)))
                    Spacer(Modifier.width(5.dp))
                    Text("6 FACTORY ONE-SHOTS", color = Color(0xFFBFC3CB), fontSize = 7.sp)
                }
                Text("CC0 source recorded with assets", color = Color(0xFF89919B), fontSize = 6.sp)
            }
            Column(Modifier.weight(1.05f).fillMaxHeight().background(Color(0xFF2D3034), RoundedCornerShape(5.dp)).padding(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("SAMPLE VIEW", color = Color(0xFFC0C5CC), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("START 0%    END 100%", color = Color(0xFF9299A3), fontSize = 7.sp)
                }
                Canvas(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF1B1D20), RoundedCornerShape(4.dp)).padding(8.dp)) {
                    val center = size.height / 2f
                    val count = 172
                    val profile = when (selectedSample) { 0 -> 0.96f; 1 -> 0.74f; 2 -> 0.32f; 3 -> 0.82f; 4 -> 0.54f; else -> 0.42f }
                    for (i in 0 until count) {
                        val t = i.toFloat() / count
                        val releaseShape = if (selectedSample == 2) 0.28f + 0.72f * kotlin.math.abs(kotlin.math.sin((t * 82f).toDouble())).toFloat() else (1f - t).coerceAtLeast(0f)
                        val pulse = kotlin.math.abs(kotlin.math.sin((t * (42f + selectedSample * 8f) * 3.14159f).toDouble())).toFloat()
                        val amplitude = size.height * 0.43f * profile * releaseShape * pulse
                        val x = (i + 0.5f) * size.width / count
                        drawLine(if (selectedSample == 3) Color(0xFFFFB347) else Color(0xFF75DCCC), Offset(x, center - amplitude), Offset(x, center + amplitude), (size.width / count * 0.7f).coerceAtLeast(1f))
                    }
                    drawLine(Color(0xFF48505A), Offset(0f, center), Offset(size.width, center), 0.6f)
                }
                Text(samples[selectedSample].second + "  •  WAVEFORM PREVIEW SHAPE", color = Color(0xFFB2B8C0), fontSize = 7.sp, maxLines = 1)
                Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("PCM DECODING / SAMPLE AUDITION NOT CONNECTED", color = Color(0xFF9299A3), fontSize = 6.sp)
                    Spacer(Modifier.weight(1f))
                    SmallAction("LIBRARY") { onOpenBrowser() }
                }
            }
            Column(Modifier.weight(1.35f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DeviceSection("FILTER", Modifier.weight(0.9f)) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        AbletonKnob("CUTOFF", cutoff, { cutoff = it }, (cutoff * 20000).toInt().toString() + " Hz", Modifier.weight(1f), dialSize = 31.dp)
                        AbletonKnob("RESONANCE", resonance, { resonance = it }, (resonance * 100).toInt().toString() + " %", Modifier.weight(1f), Color(0xFFFFB347), dialSize = 31.dp)
                    }
                }
                DeviceSection("AMP ENVELOPE", Modifier.weight(1.12f)) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        AbletonKnob("ATTACK", attack, { attack = it }, (attack * 1000).toInt().toString() + " ms", Modifier.weight(1f), dialSize = 29.dp)
                        AbletonKnob("DECAY", decay, { decay = it }, (decay * 3000).toInt().toString() + " ms", Modifier.weight(1f), dialSize = 29.dp)
                        AbletonKnob("SUSTAIN", sustain, { sustain = it }, (sustain * 100).toInt().toString() + " %", Modifier.weight(1f), dialSize = 29.dp)
                        AbletonKnob("RELEASE", release, { release = it }, (release * 4000).toInt().toString() + " ms", Modifier.weight(1f), dialSize = 29.dp)
                    }
                }
                DeviceSection("PITCH / TONE", Modifier.weight(0.75f)) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        AbletonKnob("PITCH", pitch, { pitch = it }, (pitch * 24 - 12).toInt().toString() + " st", Modifier.weight(1f), Color(0xFFFFB347), dialSize = 29.dp)
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("TRANSPOSE", color = Color(0xFFBFC3CB), fontSize = 6.sp, fontWeight = FontWeight.Bold)
                            Text("±12 st", color = Color(0xFFE8EAED), fontSize = 8.sp)
                        }
                    }
                }
                DeviceSection("DEVICE CHAIN", Modifier.weight(0.73f)) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("EQ EIGHT", "SATURATOR", "REVERB").forEachIndexed { index, name ->
                            Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF24262A), RoundedCornerShape(3.dp)).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Box(Modifier.size(6.dp).background(if (index == 0) Color(0xFFFFB347) else Color(0xFF747B85), RoundedCornerShape(50.dp)))
                                Spacer(Modifier.height(3.dp))
                                Text(name, color = Color(0xFFBFC3CB), fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("UI ONLY", color = Color(0xFF858B95), fontSize = 5.sp)
                            }
                        }
                    }
                }
            }
        }
        Text("DirectWave controls are visual foundations; sampler voice, filter, ADSR and FX DSP are not connected yet.", color = Color(0xFF9299A3), fontSize = 6.sp, maxLines = 1)
    }
}

@Composable
private fun DeviceSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxHeight().background(Color(0xFF35383D), RoundedCornerShape(4.dp)).padding(5.dp)) {
        Text(title, color = Color(0xFFC6CBD2), fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        content()
    }
}

@Composable
private fun AbletonKnob(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    readout: String,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF75DCCC),
    enabled: Boolean = true,
    dialSize: androidx.compose.ui.unit.Dp = 30.dp
) {
    val latestValue = rememberUpdatedState(value)
    val latestOnValueChange = rememberUpdatedState(onValueChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(label, fontSize = 6.sp, color = Color(0xFFB9BFC7), fontWeight = FontWeight.Bold, maxLines = 1)
        Canvas(Modifier.size(dialSize).then(if (enabled) Modifier.pointerInput(Unit) {
            var current = latestValue.value
            detectDragGestures(
                onDragStart = { current = latestValue.value },
                onDrag = { change, drag ->
                    change.consume()
                    current = (current - drag.y / 110f + drag.x / 650f).coerceIn(0f, 1f)
                    latestOnValueChange.value(current)
                }
            )
        } else Modifier)) {
            val d = size.minDimension * 0.88f
            val left = (size.width - d) / 2f
            val top = (size.height - d) / 2f
            drawCircle(Color(0xFF1A1B1E), d / 2f, Offset(size.width / 2f, size.height / 2f))
            drawCircle(Color(0xFF555A62), d / 2f, Offset(size.width / 2f, size.height / 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            val sweep = 270f * value.coerceIn(0f, 1f)
            drawArc(accent, 135f, sweep, false, Offset(left, top), Size(d, d), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            val angle = Math.toRadians((135f + sweep).toDouble())
            val radius = d * 0.29f
            drawLine(Color(0xFFF1F2F4), Offset(size.width / 2f, size.height / 2f), Offset(size.width / 2f + kotlin.math.cos(angle).toFloat() * radius, size.height / 2f + kotlin.math.sin(angle).toFloat() * radius), 2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        Text(readout, fontSize = 7.sp, color = Color(0xFFE0E3E7), fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun DeviceDock(isPlaying: Boolean, onToggle: () -> Unit, onTone: (Float) -> Unit) {
    var pitch by remember { mutableFloatStateOf(0.3f) }
    var cutoff by remember { mutableFloatStateOf(0.72f) }
    var resonance by remember { mutableFloatStateOf(0.22f) }
    var attack by remember { mutableFloatStateOf(0.05f) }
    var decay by remember { mutableFloatStateOf(0.28f) }
    var sustain by remember { mutableFloatStateOf(0.7f) }
    var release by remember { mutableFloatStateOf(0.35f) }
    Column(Modifier.fillMaxWidth().height(102.dp).background(Color(0xFF2D3034), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).background(Color(0xFFFFA52E), RoundedCornerShape(50.dp)))
            Text("DIRECTWAVE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("TONE AUDITION / UI PREVIEW", fontSize = 6.sp, color = MutedText)
            Spacer(Modifier.weight(1f))
            Text(if (isPlaying) "PLAYING" else "READY", color = if (isPlaying) Green else MutedText, fontSize = 6.sp)
            SmallAction("HIDE") { onToggle() }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DeviceSection("FILTER", Modifier.weight(1f)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    AbletonKnob("CUTOFF", cutoff, { cutoff = it }, "7.2k", Modifier.weight(1f), dialSize = 23.dp)
                    AbletonKnob("RES", resonance, { resonance = it }, "22%", Modifier.weight(1f), Color(0xFFFFB347), dialSize = 23.dp)
                }
            }
            DeviceSection("AMP ENV", Modifier.weight(1.6f)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    AbletonKnob("A", attack, { attack = it }, (attack * 1000).toInt().toString(), Modifier.weight(1f), dialSize = 22.dp)
                    AbletonKnob("D", decay, { decay = it }, (decay * 2500).toInt().toString(), Modifier.weight(1f), dialSize = 22.dp)
                    AbletonKnob("S", sustain, { sustain = it }, (sustain * 100).toInt().toString(), Modifier.weight(1f), dialSize = 22.dp)
                    AbletonKnob("R", release, { release = it }, (release * 3000).toInt().toString(), Modifier.weight(1f), dialSize = 22.dp)
                }
            }
            DeviceSection("TONE", Modifier.weight(0.9f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AbletonKnob("PITCH", pitch, { pitch = it; onTone(110f + it * 880f) }, (110 + pitch * 880).toInt().toString(), Modifier.fillMaxWidth(), Color(0xFFFFB347), dialSize = 23.dp)
                }
            }
            DeviceSection("FX CHAIN", Modifier.weight(1.2f)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("EQ", "SAT", "REV").forEachIndexed { index, label ->
                        Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF24262A), RoundedCornerShape(3.dp)).padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Box(Modifier.size(5.dp).background(if (index == 0) Color(0xFF75DCCC) else Color(0xFF646B75), RoundedCornerShape(50.dp)))
                            Spacer(Modifier.height(2.dp))
                            Text(label, color = Color(0xFFBFC3CB), fontSize = 6.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingTimelineControl(label: String, description: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF353A42),
        contentColor = Color(0xFFE4E8EF),
        shape = RoundedCornerShape(50),
        modifier = Modifier.sizeIn(minWidth = 34.dp, minHeight = 34.dp)
            .defaultMinSize(minWidth = 34.dp, minHeight = 34.dp)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
 * Generates a stable visual envelope from available thumbnails. This is a UI preview,
 * not PCM decoding, and runs on Dispatchers.IO.
 */
/**
 * Decode actual PCM samples for display. Extractor and decoder run on Dispatchers.IO,
 * output is reduced to a bounded peak array, and native resources are always released.
 */
private fun decodeWaveformPeaks(
    context: android.content.Context,
    uri: Uri,
    peakCount: Int,
    ensureActive: () -> Unit = {}
): List<Float> {
    require(peakCount in 1..2048) { "Invalid waveform resolution" }
    val extractor = MediaExtractor()
    var decoder: android.media.MediaCodec? = null
    try {
        extractor.setDataSource(context, uri, null)
        var selectedTrack = -1
        var sourceFormat: MediaFormat? = null
        for (index in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(index)
            if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                selectedTrack = index
                sourceFormat = candidate
                break
            }
        }
        val inputFormat = sourceFormat ?: error("File tidak memiliki stream audio")
        extractor.selectTrack(selectedTrack)
        val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) inputFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L) else 1L
        decoder = android.media.MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME) ?: error("Codec audio tidak dikenal"))
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()

        val peaks = FloatArray(peakCount)
        val info = android.media.MediaCodec.BufferInfo()
        var inputFinished = false
        var outputFinished = false
        var gotPcm = false
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
        var pcmEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT
        var decodedFrameCount = 0L
        var noProgressPolls = 0

        while (!outputFinished) {
            ensureActive()
            var progressed = false
            if (!inputFinished) {
                val inputIndex = decoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val input = decoder.getInputBuffer(inputIndex) ?: error("Input buffer audio tidak tersedia")
                    input.clear()
                    val size = extractor.readSampleData(input, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0L, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputFinished = true
                    } else {
                        val pts = extractor.sampleTime.coerceAtLeast(0L)
                        decoder.queueInputBuffer(inputIndex, 0, size, pts, 0)
                        extractor.advance()
                    }
                    progressed = true
                }
            }

            when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val decodedFormat = decoder.outputFormat
                    if (decodedFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = decodedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
                    if (decodedFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = decodedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
                    if (decodedFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) pcmEncoding = decodedFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    progressed = true
                }
                android.media.MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                else -> if (outputIndex >= 0) {
                    try {
                        val output = decoder.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            val bytesPerSample = when (pcmEncoding) {
                                android.media.AudioFormat.ENCODING_PCM_FLOAT,
                                android.media.AudioFormat.ENCODING_PCM_32BIT -> 4
                                android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                                android.media.AudioFormat.ENCODING_PCM_8BIT -> 1
                                else -> 2
                            }
                            val buffer = output.duplicate().order(ByteOrder.nativeOrder())
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val pcm = buffer.slice().order(ByteOrder.nativeOrder())
                            val frames = (info.size / bytesPerSample) / channels
                            for (frame in 0 until frames) {
                                // A single decoded buffer can contain many frames. Check cancellation
                                // periodically so switching clips never leaves a long PCM scan running.
                                if ((frame and 0x0FFF) == 0) ensureActive()
                                var peak = 0f
                                val firstSample = frame * channels
                                for (channel in 0 until channels) {
                                    val sampleIndex = firstSample + channel
                                    val value = when (pcmEncoding) {
                                        android.media.AudioFormat.ENCODING_PCM_FLOAT -> pcm.getFloat(sampleIndex * 4).coerceIn(-1f, 1f)
                                        android.media.AudioFormat.ENCODING_PCM_8BIT -> ((pcm.get(sampleIndex).toInt() and 0xFF) - 128) / 128f
                                        android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                                            val offset = sampleIndex * 3
                                            var value24 = (pcm.get(offset).toInt() and 0xFF) or ((pcm.get(offset + 1).toInt() and 0xFF) shl 8) or (pcm.get(offset + 2).toInt() shl 16)
                                            if (value24 and 0x800000 != 0) value24 = value24 or -0x1000000
                                            value24 / 8388608f
                                        }
                                        android.media.AudioFormat.ENCODING_PCM_32BIT -> pcm.getInt(sampleIndex * 4) / 2147483648f
                                        else -> pcm.getShort(sampleIndex * 2) / 32768f
                                    }
                                    peak = maxOf(peak, kotlin.math.abs(value))
                                }
                                val timestampUs = info.presentationTimeUs + frame.toLong() * 1_000_000L / sampleRate
                                val bin = ((timestampUs.toDouble() / durationUs) * peakCount).toInt().coerceIn(0, peakCount - 1)
                                peaks[bin] = maxOf(peaks[bin], peak)
                            }
                            if (frames > 0) {
                                decodedFrameCount += frames.toLong()
                                gotPcm = true
                            }
                        }
                        if (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputFinished = true
                    } finally {
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                    progressed = true
                }
            }
            if (decodedFrameCount > sampleRate.toLong() * 60L * 30L) error("Audio lebih dari 30 menit; pratinjau dibatasi demi stabilitas")
            if (progressed) noProgressPolls = 0 else noProgressPolls++
            if (noProgressPolls > 500) error("Decoder audio tidak menghasilkan kemajuan")
        }
        check(gotPcm) { "PCM audio tidak dapat didekode" }
        return peaks.map { it.coerceIn(0f, 1f) }
    } finally {
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        runCatching { extractor.release() }
    }
}

private object WaveformPeakCache {
    private val cache = android.util.LruCache<String, List<Float>>(12)
    private fun key(uri: String, peakCount: Int) = "$uri#$peakCount"
    @Synchronized fun get(uri: String, peakCount: Int): List<Float>? = cache.get(key(uri, peakCount))
    @Synchronized fun put(uri: String, peakCount: Int, peaks: List<Float>) {
        cache.put(key(uri, peakCount), peaks)
    }
}

@Composable
private fun AudioClipWaveform(sourceUri: String?, accent: Color) {
    val context = LocalContext.current
    var peaks by remember(sourceUri) { mutableStateOf<List<Float>>(emptyList()) }
    LaunchedEffect(sourceUri) {
        peaks = emptyList()
        if (sourceUri.isNullOrBlank()) return@LaunchedEffect
        try {
            peaks = withContext(Dispatchers.IO) {
                val decodeJob = currentCoroutineContext()[Job]
                WaveformPeakCache.get(sourceUri, 48) ?: decodeWaveformPeaks(context, Uri.parse(sourceUri), 48) {
                    decodeJob?.ensureActive()
                }.also { WaveformPeakCache.put(sourceUri, 48, it) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            peaks = emptyList()
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        if (peaks.isEmpty()) {
            drawLine(accent.copy(alpha = 0.25f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1f)
        } else {
            val mid = size.height / 2f
            val dx = size.width / peaks.size.coerceAtLeast(1)
            peaks.forEachIndexed { index, peak ->
                val amplitude = peak.coerceIn(0f, 1f) * size.height * 0.48f
                val x = (index + 0.5f) * dx
                drawLine(accent, Offset(x, mid - amplitude), Offset(x, mid + amplitude), dx.coerceIn(1f, 2f))
            }
        }
    }
}


@Composable
private fun DeviceDock(
    project: StudioProject,
    selectedTrackId: String?,
    onSelectTrack: (String) -> Unit,
    onProjectChange: (StudioProject) -> Unit,
    onToggle: () -> Unit,
    fullScreen: Boolean = false
) {
    val selectedTrack = project.tracks.firstOrNull { it.id == selectedTrackId } ?: project.tracks.firstOrNull()
    val palette = LocalStudioWavePalette.current
    var expanded by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun updateDevices(devices: List<TrackDevice>) {
        val track = selectedTrack ?: return
        onProjectChange(project.copy(tracks = project.tracks.map { if (it.id == track.id) it.copy(devices = devices) else it }))
    }
    fun addDevice(type: String, name: String) {
        val old = selectedTrack?.devices ?: return
        val newDevice = TrackDevice(type = type, name = name, parameters = defaultDeviceParameters(type))
        val next = if (type in INSTRUMENT_DEVICE_TYPES) old.filterNot { it.type in INSTRUMENT_DEVICE_TYPES } + newDevice else old + newDevice
        updateDevices(next)
        showAddDialog = false
    }

    Column(
        (if (fullScreen) Modifier.fillMaxWidth().fillMaxHeight() else Modifier.fillMaxWidth().height(if (expanded) 328.dp else 42.dp))
            .background(palette.room, RoundedCornerShape(3.dp))
            .border(1.dp, palette.border, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(30.dp).background(palette.panel, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.size(6.dp).background(palette.orange, RoundedCornerShape(1.dp)))
            Text("DEVICE VIEW", color = palette.text, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                project.tracks.forEach { track ->
                    val active = selectedTrack?.id == track.id
                    Surface(
                        color = if (active) palette.accent.copy(alpha = 0.16f) else palette.control,
                        border = BorderStroke(1.dp, if (active) palette.accent else palette.border),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.height(23.dp).clickable { onSelectTrack(track.id) }
                    ) {
                        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(6.dp).background(Color(track.colorArgb.toInt()), RoundedCornerShape(1.dp)))
                            Text(track.name.uppercase(), color = if (active) palette.accent else palette.text, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
            DirectWaveStateButton(if (expanded) "FOLD" else "UNFOLD", expanded, palette) { expanded = !expanded }
            DirectWaveStateButton("×", false, palette, onToggle)
        }

        if (expanded) {
            if (selectedTrack == null) {
                DeviceModuleCard("NO DEVICE", "TRACK DEVICE CHAIN", palette, "Create a track in Arrangement to open its instrument or audio device.")
            } else {
                val track = selectedTrack
                val instrument = track.devices.firstOrNull { it.type in INSTRUMENT_DEVICE_TYPES && it.type != "DRUM_RACK" }
                val effects = track.devices.filter { it.type in EFFECT_DEVICE_TYPES }
                Row(Modifier.fillMaxWidth().weight(1f).clipToBounds(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        when {
                            track.kind == "drum" -> DeviceModuleCard("DRUM RACK", "INSTRUMENT", palette, "Edit its 16-step pattern in Drum Sequencer.")
                            track.kind == "midi" && (instrument == null || instrument.type == "DIRECTWAVE") -> DirectWaveDevicePanel(track.id, track.name, track.kind, palette, fullScreen)
                            track.kind == "midi" && instrument != null -> BasicSynthDevicePanel(track, instrument, palette) { updated ->
                                updateDevices(track.devices.map { if (it.id == updated.id) updated else it })
                            }
                            else -> DeviceModuleCard("AUDIO TRACK", "SOURCE / INSERTS", palette, "Edit source trim and waveform in Clip View. Insert effects process during playback.")
                        }
                        effects.forEach { device ->
                            TrackEffectDeviceCard(
                                device, palette,
                                onUpdate = { updated -> updateDevices(track.devices.map { if (it.id == updated.id) updated else it }) },
                                onRemove = { updateDevices(track.devices.filterNot { it.id == device.id }) }
                            )
                        }
                    }
                    Column(Modifier.width(70.dp).fillMaxHeight().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            color = palette.orange.copy(alpha = 0.18f), border = BorderStroke(1.dp, palette.orange),
                            shape = RoundedCornerShape(2.dp), modifier = Modifier.fillMaxWidth().weight(1f).clickable { showAddDialog = true }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("+", color = palette.orange, fontSize = 22.sp, fontWeight = FontWeight.Light)
                                Text("ADD FX /", color = palette.text, fontSize = 6.sp, fontWeight = FontWeight.Black)
                                Text("DEVICE", color = palette.text, fontSize = 6.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Text("${effects.size} FX", color = palette.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val midiTrack = selectedTrack?.kind == "midi"
        val drumTrack = selectedTrack?.kind == "drum"
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = palette.panel,
            title = { Text("ADD DEVICE", color = palette.text, fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("INSTRUMENTS", color = palette.orange, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    if (midiTrack) {
                        listOf(
                            Triple("DIRECTWAVE", "DirectWave Sampler", "Sample instrument · ADSR / pitch / loop"),
                            Triple("SYNTH_SINE", "Analog Sine", "Basic oscillator synth"),
                            Triple("SYNTH_SAW", "Analog Saw", "Saw oscillator synth"),
                            Triple("SYNTH_SQUARE", "Square Synth", "Pulse oscillator synth"),
                            Triple("SYNTH_TRIANGLE", "Triangle Synth", "Triangle oscillator synth"),
                            Triple("FM_SYNTH", "FM Synth", "Two-operator FM tone generator")
                        ).forEach { item -> DeviceCatalogRow(item.second, item.third, palette) { addDevice(item.first, item.second) } }
                    } else if (drumTrack) {
                        DeviceCatalogRow("Drum Rack", "Native drum step sequencer", palette) { addDevice("DRUM_RACK", "Drum Rack") }
                    } else {
                        Text("Audio tracks accept mixing and mastering effects.", color = palette.muted, fontSize = 9.sp)
                    }
                    HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 4.dp))
                    Text("MIXING / MASTERING FX", color = palette.orange, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    listOf(
                        Triple("EQ", "EQ Three", "3-band low / mid / high tone shaping"),
                        Triple("PARAM_EQ_8", "Parametric EQ Eight", "Eight-band parametric equalizer"),
                        Triple("FILTER", "Auto Filter", "Low-pass, high-pass or band-pass"),
                        Triple("COMPRESSOR", "Compressor", "Threshold, ratio, attack, release, makeup"),
                        Triple("SATURATOR", "Saturator", "Drive, wet/dry and output"),
                        Triple("DELAY", "Delay", "Feedback delay line"),
                        Triple("REVERB", "Reverb", "Room size, decay and wet level"),
                        Triple("LIMITER", "Limiter", "Output ceiling and release")
                    ).forEach { item -> DeviceCatalogRow(item.second, item.third, palette) { addDevice(item.first, item.second) } }
                }
            },
            confirmButton = { TextButton(onClick = { showAddDialog = false }) { Text("DONE", color = palette.orange) } }
        )
    }
}


@Composable
private fun DeviceModuleCard(
    title: String,
    subtitle: String,
    palette: StudioWavePalette,
    body: String
) {
    Column(
        Modifier.width(320.dp).height(160.dp)
            .background(palette.panel, RoundedCornerShape(2.dp))
            .border(1.dp, palette.border, RoundedCornerShape(2.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(palette.orange, RoundedCornerShape(1.dp)))
            Text(title, color = palette.text, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        Text(subtitle, color = palette.accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
        Text(body, color = palette.muted, fontSize = 9.sp, lineHeight = 13.sp)
    }
}

@Composable
private fun DirectWaveDevicePanel(
    trackId: String,
    trackName: String,
    trackKind: String,
    palette: StudioWavePalette,
    fullHeight: Boolean = false
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("studiowave_directwave", android.content.Context.MODE_PRIVATE)
    }
    if (trackKind != "midi") {
        DeviceModuleCard(
            title = "TRACK DEVICE CHAIN",
            subtitle = "DIRECTWAVE IS AN INSTRUMENT",
            palette = palette,
            body = "DirectWave is available on MIDI tracks. Audio processors are not faked as active devices."
        )
        return
    }

    val sampler = remember(trackId) { DirectWaveSampler(context) }
    var sampleLabel by remember(trackId) { mutableStateOf("ONE SHOT.WAV") }
    var sampleUri by remember(trackId) { mutableStateOf<String?>(null) }
    var sampleReady by remember(trackId) { mutableStateOf(false) }
    var pitch by remember(trackId) { mutableFloatStateOf(0f) }
    var fineTune by remember(trackId) { mutableFloatStateOf(0f) }
    var attack by remember(trackId) { mutableFloatStateOf(5f) }
    var decay by remember(trackId) { mutableFloatStateOf(120f) }
    var sustain by remember(trackId) { mutableFloatStateOf(0.8f) }
    var release by remember(trackId) { mutableFloatStateOf(180f) }
    var loop by remember(trackId) { mutableStateOf(false) }
    var outputGain by remember(trackId) { mutableFloatStateOf(0.85f) }
    var category by remember(trackId) { mutableStateOf("ALL") }
    var showSamples by remember(trackId) { mutableStateOf(false) }
    val samples = remember {
        listOf(
            BuiltInStudioSample("SAMPLES", "One Shoot.wav", "one_shot", "StudioWave original one-shot · bundled offline")
        ) + com.studiowave.app.audio.FactorySampleLibrary.presets.map { preset ->
            BuiltInStudioSample(
                preset.category.uppercase(),
                preset.name,
                preset.fileName.substringBeforeLast('.'),
                preset.description
            )
        }
    }
    fun loadBundledOneShot() {
        val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.one_shot}")
        sampleUri = uri.toString()
        sampleLabel = "One Shoot.wav"
        sampleReady = false
        preferences.edit().putString("sample.$trackId", uri.toString())
            .putString("sampleName.$trackId", "One Shoot.wav").apply()
        sampler.load(uri) { ready -> sampleReady = ready }
    }
    fun loadFactoryPreset(sample: BuiltInStudioSample) {
        if (sample.assetName == "one_shot") {
            loadBundledOneShot()
            return
        }
        val presetIndex = com.studiowave.app.audio.FactorySampleLibrary.presets.indexOfFirst {
            it.fileName.substringBeforeLast('.') == sample.assetName
        }
        if (presetIndex < 0) {
            Toast.makeText(context, "Preset belum tersedia.", Toast.LENGTH_SHORT).show()
            return
        }
        val target = java.io.File(context.cacheDir, sample.assetName + ".wav")
        runCatching { target.writeBytes(com.studiowave.app.audio.FactorySampleLibrary.wavBytes(presetIndex)) }
            .onFailure {
                Toast.makeText(context, "Gagal membuat sampel bawaan", Toast.LENGTH_SHORT).show()
                return
            }
        val uri = Uri.fromFile(target)
        sampleUri = uri.toString()
        sampleLabel = sample.title
        sampleReady = false
        preferences.edit().putString("sample.$trackId", uri.toString())
            .putString("sampleName.$trackId", sample.title).apply()
        sampler.load(uri) { ready -> sampleReady = ready }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            sampleUri = uri.toString()
            sampleLabel = uri.lastPathSegment?.substringAfterLast('/')?.takeLast(36) ?: "Imported sample"
            sampleReady = false
            preferences.edit().putString("sample.$trackId", uri.toString())
                .putString("sampleName.$trackId", sampleLabel).apply()
            sampler.load(uri) { ready -> sampleReady = ready }
        }
    }
    DisposableEffect(sampler) { onDispose { sampler.close() } }
    LaunchedEffect(trackId, sampler) {
        pitch = preferences.getInt("pitch.$trackId", 0).toFloat().coerceIn(-12f, 12f)
        fineTune = preferences.getInt("fineTune.$trackId", 0).toFloat().coerceIn(-100f, 100f)
        attack = preferences.getInt("attack.$trackId", 5).toFloat().coerceIn(0f, 2000f)
        decay = preferences.getInt("decay.$trackId", 120).toFloat().coerceIn(0f, 2000f)
        sustain = (preferences.getInt("sustain.$trackId", 80) / 100f).coerceIn(0f, 1f)
        release = preferences.getInt("release.$trackId", 180).toFloat().coerceIn(0f, 2000f)
        loop = preferences.getBoolean("loop.$trackId", false)
        outputGain = preferences.getFloat("outputGain.$trackId", 0.85f).coerceIn(0f, 1f)
        sampler.outputGain = outputGain
        sampler.pitchSemitones = pitch.toInt()
        sampler.fineTuneCents = fineTune.toInt()
        sampler.attackMs = attack.toInt()
        sampler.decayMs = decay.toInt()
        sampler.sustain = sustain
        sampler.releaseMs = release.toInt()
        sampler.looping = loop
        val saved = preferences.getString("sample.$trackId", null)
        if (!saved.isNullOrBlank()) {
            sampleUri = saved
            sampleLabel = preferences.getString("sampleName.$trackId", null)
                ?: Uri.parse(saved).lastPathSegment?.substringAfterLast('/')?.takeLast(36)
                ?: "Saved sample"
            sampler.load(Uri.parse(saved)) { ready -> sampleReady = ready }
        } else {
            loadBundledOneShot()
        }
    }

    val compact = !fullHeight
    val knobSize = if (compact) 48.dp else 62.dp
    val waveformHeight = if (compact) 45.dp else 76.dp
    val panelHeight = if (compact) 287.dp else 410.dp
    val isBuiltIn = sampleUri?.startsWith("android.resource://${context.packageName}/") == true
    val statusText = when {
        sampleReady && isBuiltIn -> "FACTORY SAMPLE · READY"
        sampleReady -> "USER SAMPLE · READY"
        sampleUri != null -> "LOADING SAMPLE"
        else -> "NO SAMPLE"
    }

    Column(
        Modifier.width(if (fullHeight) 860.dp else 610.dp)
            .height(if (fullHeight) panelHeight else 287.dp)
            .background(palette.canvas, RoundedCornerShape(4.dp))
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Ableton-inspired device shell: narrow title strip, activated state, grouped macro controls.
        Row(
            Modifier.fillMaxWidth().height(30.dp)
                .background(palette.panel, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.size(7.dp).background(if (sampleReady) palette.accent else palette.orange, RoundedCornerShape(1.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("DIRECTWAVE", color = palette.text, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text("SAMPLER  ·  ${trackName.uppercase()}", color = palette.muted, fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(statusText, color = if (sampleReady) palette.accent else palette.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            DirectWaveStateButton("SAMPLE ▾", showSamples, palette) { showSamples = !showSamples }
            DirectWaveStateButton(if (loop) "LOOP ON" else "LOOP OFF", loop, palette) {
                loop = !loop
                sampler.looping = loop
                preferences.edit().putBoolean("loop.$trackId", loop).apply()
            }
            DirectWaveStateButton("LOAD WAV", false, palette) {
                launcher.launch(arrayOf("audio/wav", "audio/x-wav", "audio/mpeg", "audio/*"))
            }
        }

        if (showSamples) {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 65.dp)
                    .verticalScroll(rememberScrollState())
                    .background(palette.panel, RoundedCornerShape(3.dp))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("ALL", "SAMPLES", "ARP", "BASS", "LEAD", "KEYBOARD", "ORCHESTRA", "SYNTH", "PAD").forEach { item ->
                        DirectWaveStateButton(item, category == item, palette) { category = item }
                    }
                }
                val filtered = samples.filter { category == "ALL" || it.category == category }
                filtered.forEach { sample ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (sampleUri?.contains(sample.assetName) == true) palette.raised else palette.room, RoundedCornerShape(2.dp))
                            .clickable { loadFactoryPreset(sample) }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(3.dp).height(23.dp).background(palette.orange, RoundedCornerShape(1.dp)))
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(sample.title.uppercase(), color = palette.text, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            Text(sample.description, color = palette.muted, fontSize = 7.sp, maxLines = 1)
                        }
                        Text(if (sampleUri?.contains(sample.assetName) == true) "LOADED" else "LOAD", color = palette.accent, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().height(if (compact) 55.dp else 89.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1.1f).fillMaxHeight()
                    .background(palette.panel, RoundedCornerShape(3.dp)).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("SAMPLE", color = palette.muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text(if (sampleReady) "READY" else "SOURCE", color = palette.accent, fontSize = 6.sp, fontWeight = FontWeight.Bold)
                }
                Text(sampleLabel.uppercase(), color = palette.text, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                DirectWaveDeviceWaveform(
                    sampleUri = sampleUri,
                    sampleReady = sampleReady,
                    palette = palette,
                    compact = compact,
                    onImport = { launcher.launch(arrayOf("audio/wav", "audio/x-wav", "audio/mpeg", "audio/*")) }
                )
            }
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .background(palette.panel, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("TUNE / OUTPUT", color = palette.orange, fontSize = 7.sp, fontWeight = FontWeight.Black)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    DirectWaveKnob("PITCH", pitch, -12f..12f, "${pitch.toInt()} st", palette, Modifier.weight(1f), knobSize) {
                        pitch = it; sampler.pitchSemitones = it.toInt()
                        preferences.edit().putInt("pitch.$trackId", it.toInt()).apply()
                    }
                    DirectWaveKnob("FINE", fineTune, -100f..100f, "${fineTune.toInt()} ct", palette, Modifier.weight(1f), knobSize) {
                        fineTune = it; sampler.fineTuneCents = it.toInt()
                        preferences.edit().putInt("fineTune.$trackId", it.toInt()).apply()
                    }
                    DirectWaveKnob("LEVEL", outputGain, 0f..1f, "${(outputGain * 100).toInt()}%", palette, Modifier.weight(1f), knobSize) {
                        outputGain = it; sampler.outputGain = it
                        preferences.edit().putFloat("outputGain.$trackId", it).apply()
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().weight(1f)
                .background(palette.panel, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("AMP ENVELOPE", color = palette.orange, fontSize = 7.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text("ADSR", color = palette.muted, fontSize = 6.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                DirectWaveKnob("ATTACK", attack, 0f..2000f, "${attack.toInt()} ms", palette, Modifier.weight(1f), knobSize) {
                    attack = it; sampler.attackMs = it.toInt()
                    preferences.edit().putInt("attack.$trackId", it.toInt()).apply()
                }
                DirectWaveKnob("DECAY", decay, 0f..2000f, "${decay.toInt()} ms", palette, Modifier.weight(1f), knobSize) {
                    decay = it; sampler.decayMs = it.toInt()
                    preferences.edit().putInt("decay.$trackId", it.toInt()).apply()
                }
                DirectWaveKnob("SUSTAIN", sustain, 0f..1f, "${(sustain * 100).toInt()}%", palette, Modifier.weight(1f), knobSize) {
                    sustain = it; sampler.sustain = it
                    preferences.edit().putInt("sustain.$trackId", (it * 100).toInt()).apply()
                }
                DirectWaveKnob("RELEASE", release, 0f..2000f, "${release.toInt()} ms", palette, Modifier.weight(1f), knobSize) {
                    release = it; sampler.releaseMs = it.toInt()
                    preferences.edit().putInt("release.$trackId", it.toInt()).apply()
                }
            }
        }

        Row(Modifier.fillMaxWidth().height(23.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("KEYBOARD", color = palette.muted, fontSize = 6.sp, fontWeight = FontWeight.Black)
            listOf(48, 50, 52, 53, 55, 57, 59, 60, 62, 64, 65, 67).forEach { note ->
                val label = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")[note % 12]
                Surface(
                    color = if (note % 12 in setOf(1, 3, 6, 8, 10)) palette.control else palette.raised,
                    contentColor = palette.text, shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.weight(1f).fillMaxHeight().pointerInput(note, sampleReady) {
                        detectTapGestures(onPress = {
                            if (sampleReady) sampler.noteOn(note)
                            tryAwaitRelease()
                            sampler.noteOff(note)
                        })
                    }
                ) { Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 7.sp, fontWeight = FontWeight.Bold) } }
            }
        }
    }
}

private data class BuiltInStudioSample(
    val category: String,
    val title: String,
    val assetName: String,
    val description: String
)


@Composable
private fun DeviceParameterSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, palette: StudioWavePalette, unit: String = "", onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = palette.muted, fontSize = 6.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val shown = when {
                value.absoluteValue >= 1000f -> "%.1fk".format(value / 1000f)
                value.absoluteValue >= 10f -> "%.1f".format(value)
                else -> "%.2f".format(value)
            }
            Text(shown + unit, color = palette.text, fontSize = 6.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, modifier = Modifier.fillMaxWidth().height(20.dp),
            colors = SliderDefaults.colors(thumbColor = palette.orange, activeTrackColor = palette.accent, inactiveTrackColor = palette.control, activeTickColor = palette.accent))
    }
}


@Composable
private fun DirectWaveStateButton(
    label: String,
    active: Boolean,
    palette: StudioWavePalette,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) palette.accent.copy(alpha = 0.18f) else palette.control,
        contentColor = if (active) palette.accent else palette.text,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, if (active) palette.accent else palette.border),
        modifier = Modifier.height(23.dp).clickable(onClick = onClick)
    ) {
        Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}


@Composable
private fun TrackEffectDeviceCard(device: TrackDevice, palette: StudioWavePalette, onUpdate: (TrackDevice) -> Unit, onRemove: () -> Unit) {
    val p = device.parameters
    Column(Modifier.width(238.dp).fillMaxHeight().background(palette.canvas, RoundedCornerShape(2.dp)).border(1.dp, palette.border, RoundedCornerShape(2.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(6.dp).background(if (device.enabled) palette.orange else palette.muted, RoundedCornerShape(1.dp)))
            Text(device.name.uppercase(), color = palette.text, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1, modifier = Modifier.weight(1f))
            DirectWaveStateButton(if (device.enabled) "ON" else "OFF", device.enabled, palette) { onUpdate(device.copy(enabled = !device.enabled)) }
            DirectWaveStateButton("×", false, palette) { onRemove() }
        }
        HorizontalDivider(color = palette.border)
        when (device.type) {
            "EQ" -> {
                DeviceParameterSlider("LOW", p["lowDb"] ?: 0f, -12f..12f, palette, " dB") { onUpdate(device.copy(parameters = p + ("lowDb" to it))) }
                DeviceParameterSlider("MID", p["midDb"] ?: 0f, -12f..12f, palette, " dB") { onUpdate(device.copy(parameters = p + ("midDb" to it))) }
                DeviceParameterSlider("HIGH", p["highDb"] ?: 0f, -12f..12f, palette, " dB") { onUpdate(device.copy(parameters = p + ("highDb" to it))) }
            }
            "PARAM_EQ_8" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom) {
                (1..8).forEach { band ->
                    val key = "band$band"
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(listOf("60","120","250","500","1k","2k","5k","10k")[band-1], color = palette.muted, fontSize = 5.sp)
                        Slider(value = p[key] ?: 0f, onValueChange = { onUpdate(device.copy(parameters = p + (key to it))) }, valueRange = -12f..12f, modifier = Modifier.height(112.dp).graphicsLayer { rotationZ = 180f },
                            colors = SliderDefaults.colors(thumbColor = palette.orange, activeTrackColor = palette.accent, inactiveTrackColor = palette.control, activeTickColor = palette.accent))
                    }
                }
            }
            "FILTER" -> {
                DeviceParameterSlider("CUTOFF", p["cutoffHz"] ?: 16000f, 20f..20000f, palette, " Hz") { onUpdate(device.copy(parameters = p + ("cutoffHz" to it))) }
                DeviceParameterSlider("RESONANCE", p["resonance"] ?: 0.15f, 0f..0.95f, palette) { onUpdate(device.copy(parameters = p + ("resonance" to it))) }
                val mode = (p["mode"] ?: 0f).toInt().coerceIn(0,2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MODE", color = palette.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    listOf("LP","HP","BP").forEachIndexed { index, label -> DirectWaveStateButton(label, mode == index, palette) { onUpdate(device.copy(parameters = p + ("mode" to index.toFloat()))) } }
                }
            }
            "DELAY" -> {
                DeviceParameterSlider("TIME", p["timeMs"] ?: 320f, 10f..900f, palette, " ms") { onUpdate(device.copy(parameters = p + ("timeMs" to it))) }
                DeviceParameterSlider("FEEDBACK", p["feedback"] ?: 0.32f, 0f..0.85f, palette) { onUpdate(device.copy(parameters = p + ("feedback" to it))) }
                DeviceParameterSlider("WET", p["wet"] ?: 0.2f, 0f..1f, palette) { onUpdate(device.copy(parameters = p + ("wet" to it))) }
            }
            "REVERB" -> {
                DeviceParameterSlider("ROOM", p["room"] ?: 0.45f, 0.05f..0.95f, palette) { onUpdate(device.copy(parameters = p + ("room" to it))) }
                DeviceParameterSlider("DECAY", p["decay"] ?: 1.6f, 0.2f..6f, palette, " s") { onUpdate(device.copy(parameters = p + ("decay" to it))) }
                DeviceParameterSlider("WET", p["wet"] ?: 0.22f, 0f..1f, palette) { onUpdate(device.copy(parameters = p + ("wet" to it))) }
            }
            "COMPRESSOR" -> {
                DeviceParameterSlider("THRESHOLD", p["thresholdDb"] ?: -18f, -60f..0f, palette, " dB") { onUpdate(device.copy(parameters = p + ("thresholdDb" to it))) }
                DeviceParameterSlider("RATIO", p["ratio"] ?: 3f, 1f..20f, palette, ":1") { onUpdate(device.copy(parameters = p + ("ratio" to it))) }
                DeviceParameterSlider("ATTACK", p["attackMs"] ?: 10f, 0.1f..250f, palette, " ms") { onUpdate(device.copy(parameters = p + ("attackMs" to it))) }
                DeviceParameterSlider("RELEASE", p["releaseMs"] ?: 120f, 1f..1500f, palette, " ms") { onUpdate(device.copy(parameters = p + ("releaseMs" to it))) }
                DeviceParameterSlider("MAKEUP", p["makeupDb"] ?: 0f, -12f..12f, palette, " dB") { onUpdate(device.copy(parameters = p + ("makeupDb" to it))) }
            }
            "SATURATOR" -> {
                DeviceParameterSlider("DRIVE", p["drive"] ?: 2.5f, 0.2f..12f, palette) { onUpdate(device.copy(parameters = p + ("drive" to it))) }
                DeviceParameterSlider("WET", p["wet"] ?: 0.35f, 0f..1f, palette) { onUpdate(device.copy(parameters = p + ("wet" to it))) }
                DeviceParameterSlider("OUTPUT", p["outputDb"] ?: 0f, -12f..12f, palette, " dB") { onUpdate(device.copy(parameters = p + ("outputDb" to it))) }
            }
            "LIMITER" -> {
                DeviceParameterSlider("CEILING", p["ceilingDb"] ?: -1f, -12f..0f, palette, " dB") { onUpdate(device.copy(parameters = p + ("ceilingDb" to it))) }
                DeviceParameterSlider("RELEASE", p["releaseMs"] ?: 80f, 10f..500f, palette, " ms") { onUpdate(device.copy(parameters = p + ("releaseMs" to it))) }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(when (device.type) {
            "PARAM_EQ_8" -> "8-band parametric EQ"
            "EQ" -> "3-band tone control"
            "FILTER" -> "Low / high / band-pass"
            "DELAY" -> "Feedback delay"
            "REVERB" -> "Multi-tap room reverb"
            "COMPRESSOR" -> "Dynamics / gain reduction"
            "SATURATOR" -> "Soft-clipping saturation"
            else -> "Peak output protection"
        }, color = palette.accent, fontSize = 6.sp, fontWeight = FontWeight.Bold)
    }
}


@Composable
private fun BasicSynthDevicePanel(track: StudioTrack, device: TrackDevice, palette: StudioWavePalette, onUpdate: (TrackDevice) -> Unit) {
    val params = device.parameters
    Column(Modifier.width(242.dp).fillMaxHeight().background(palette.panel, RoundedCornerShape(2.dp)).border(1.dp, palette.border, RoundedCornerShape(2.dp)).padding(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(device.name.uppercase(), color = palette.text, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            DirectWaveStateButton(if (device.enabled) "ON" else "OFF", device.enabled, palette) { onUpdate(device.copy(enabled = !device.enabled)) }
        }
        Text("BASIC SYNTH / ${track.name.uppercase()}", color = palette.orange, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        DeviceParameterSlider("LEVEL", params["level"] ?: 0.8f, 0f..1f, palette) { onUpdate(device.copy(parameters = params + ("level" to it))) }
        DeviceParameterSlider("ATTACK", params["attackMs"] ?: 8f, 0f..1000f, palette, " ms") { onUpdate(device.copy(parameters = params + ("attackMs" to it))) }
        DeviceParameterSlider("RELEASE", params["releaseMs"] ?: 90f, 0f..1500f, palette, " ms") { onUpdate(device.copy(parameters = params + ("releaseMs" to it))) }
        Spacer(Modifier.weight(1f))
        Text("Polyphonic · audio engine", color = palette.accent, fontSize = 7.sp, fontWeight = FontWeight.Bold)
    }
}


@Composable
private fun DeviceCatalogRow(title: String, detail: String, palette: StudioWavePalette, onClick: () -> Unit) {
    Surface(color = palette.control, shape = RoundedCornerShape(2.dp), border = BorderStroke(1.dp, palette.border), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = palette.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = palette.muted, fontSize = 7.sp)
            }
            Text("+", color = palette.orange, fontSize = 17.sp, fontWeight = FontWeight.Light)
        }
    }
}
