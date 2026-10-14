package com.studiowave.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlinx.coroutines.awaitCancellation
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt
import org.json.JSONObject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
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
import com.studiowave.app.audio.ArrangementPlaybackEngine
import com.studiowave.app.data.StudioProject
import com.studiowave.app.data.StudioProjectFile
import com.studiowave.app.data.StudioTrack
import android.widget.Toast

private val Room = Color(0xFF141517)
private val CanvasSurface = Color(0xFF1E2024)
private val Raised = Color(0xFF282B30)
private val MutedText = Color(0xFFA4A8B0)
private val Mint = Color(0xFF00D2B4)
private val AbletonOrange = Color(0xFFFF764D)
private val Green = Color(0xFF22C55E)
private val Red = Color(0xFFEF4444)

internal fun choosePreferredRefreshRate(supportedRates: Iterable<Float>): Float? =
    supportedRates.asSequence()
        .filter { it.isFinite() && it >= 90f && it <= 120f }
        .maxOrNull()


/**
 * Give an Arrangement clip its real timeline width even when it is wider than the
 * visible lane. Modifier.width() respects the lane's maxWidth and silently truncates
 * long clips to the viewport, making their apparent duration change as the viewport zooms.
 *
 * The parent reports a constrained width to preserve layout, but the child is measured
 * at its requested width and placed at x=0. TimelineGrid's clipToBounds() clips only the
 * off-screen portion, not the clip's musical extent.
 */
private fun Modifier.timelineClipWidthPx(widthPx: Float): Modifier = layout { measurable, constraints ->
    // Compose packs dimensions into Constraints; keep a generous finite ceiling while
    // never using the viewport width as the clip's preferred width.
    val desiredWidthPx = if (widthPx.isFinite()) {
        widthPx.roundToInt().coerceIn(1, 262_143)
    } else {
        1
    }
    val childConstraints = constraints.copy(minWidth = desiredWidthPx, maxWidth = desiredWidthPx)
    val placeable = measurable.measure(childConstraints)
    val reportedWidth = desiredWidthPx.coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(reportedWidth, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}

private data class TrackRowLayout(val topDp: Float, val heightDp: Float)

internal data class PinchZoomFrame(val zoom: Float, val scrollTicks: Double)

/** Compute a pinch from an immutable gesture-start baseline and fractional tick anchor. */
internal fun calculatePinchZoomFrame(
    timebase: TimelineTimebase,
    baseZoom: Float,
    baseScrollTicks: Double,
    baseSpanPx: Float,
    currentSpanPx: Float,
    anchorTick: Double,
    centroidX: Float,
    baseBarWidthPx: Float,
    viewportWidthPx: Float = 0f
): PinchZoomFrame {
    val ratio = if (baseSpanPx > 1f && currentSpanPx.isFinite() && currentSpanPx > 1f)
        currentSpanPx / baseSpanPx else 1f
    val requestedZoom = (baseZoom * ratio).coerceIn(0.1f, 16f)
    val viewportScale = TimelineViewport(
        timebase, baseBarWidthPx * requestedZoom, baseScrollTicks, 0f
    ).effectivePixelsPerBar
    val effectiveZoom = viewportScale / baseBarWidthPx
    // Pinch anchor is stored as a fractional tick, so returning to the start scale must
    // return the start scroll without an integer-tick snap.
    val scroll = TimelineViewport(timebase, viewportScale, baseScrollTicks, viewportWidthPx)
        .placeTickAtViewportX(anchorTick, centroidX).scrollTimeTicks
    return PinchZoomFrame(effectiveZoom, scroll)
}
/** Split a clip at an absolute arrangement tick while keeping both halves tied to source time. */
internal fun splitWaveClip(clip: com.studiowave.app.data.WaveClip, splitTick: Long): Pair<com.studiowave.app.data.WaveClip, com.studiowave.app.data.WaveClip>? {
    val start = clip.startTick.coerceAtLeast(0L)
    val length = clip.lengthTicks.coerceAtLeast(1L)
    val end = if (start > Long.MAX_VALUE - length) Long.MAX_VALUE else start + length
    if (splitTick <= start || splitTick >= end) return null
    val leftLength = splitTick - start
    val rightLength = end - splitTick
    val sourceOffset = clip.sourceOffsetTicks.coerceAtLeast(0L)
    val rightSourceOffset = if (sourceOffset > Long.MAX_VALUE - leftLength) Long.MAX_VALUE else sourceOffset + leftLength
    fun clippedNotes(offset: Long, windowLength: Long, newIds: Boolean) = clip.midiNotes.mapNotNull { note ->
        val noteStart = note.startTick.coerceAtLeast(0L)
        val noteEnd = if (noteStart > Long.MAX_VALUE - note.durationTicks.coerceAtLeast(1L)) Long.MAX_VALUE
            else noteStart + note.durationTicks.coerceAtLeast(1L)
        val from = noteStart.coerceAtLeast(offset)
        val to = noteEnd.coerceAtMost(offset + windowLength)
        if (to <= from) null else note.copy(
            // Keep IDs for notes that remain wholly in one piece; only a note
            // crossing the split boundary needs a new ID for its right-hand segment.
            id = if (newIds && noteStart < offset && noteEnd > offset) java.util.UUID.randomUUID().toString() else note.id,
            startTick = from - offset, durationTicks = to - from
        )
    }
    val splitIndex = if (clip.drumSteps.isEmpty()) 0 else
        kotlin.math.floor(clip.drumSteps.size.toDouble() * leftLength.toDouble() / length.toDouble())
            .toInt().coerceIn(0, clip.drumSteps.size)
    val left = clip.copy(lengthTicks = leftLength, midiNotes = clippedNotes(0L, leftLength, false), drumSteps = clip.drumSteps.take(splitIndex))
    val right = clip.copy(
        id = java.util.UUID.randomUUID().toString(), name = clip.name + " (2)", startTick = splitTick,
        lengthTicks = rightLength, sourceOffsetTicks = rightSourceOffset,
        midiNotes = clippedNotes(leftLength, rightLength, true), drumSteps = clip.drumSteps.drop(splitIndex)
    )
    return left to right
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        preferAdaptiveHighRefreshRate()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideDawSystemBars()
        window.navigationBarColor = android.graphics.Color.rgb(20, 21, 23)
        setContent { StudioWaveApp() }
    }

    /** Ask for 90/120 Hz where available; Android may still adapt for battery/thermal policy. */
    @Suppress("DEPRECATION")
    private fun preferAdaptiveHighRefreshRate() {
        val targetRate = choosePreferredRefreshRate(
            windowManager.defaultDisplay.supportedModes.map { it.refreshRate }.asIterable()
        )
        if (targetRate != null) {
            window.attributes = window.attributes.apply { preferredRefreshRate = targetRate }
        }
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
    val playbackProgress = remember { Channel<Pair<Long, Long>>(Channel.CONFLATED) }
    val latestProjectForProgress by rememberUpdatedState(project)
    LaunchedEffect(playbackProgress) {
        for ((session, tick) in playbackProgress) {
            if (session == playbackSession) {
                val activeProject = latestProjectForProgress
                val limit = maxArrangementTick(TimelineTimebase(
                    activeProject.bpm, activeProject.numerator, activeProject.denominator, activeProject.ppq
                ))
                val boundedTick = tick.coerceIn(0L, limit)
                if (boundedTick >= playheadTick) playheadTick = boundedTick
            }
        }
    }
    var bpm by remember { mutableIntStateOf(project.bpm) }
    var audioError by remember { mutableStateOf<String?>(null) }
    var master by remember { mutableFloatStateOf(0.72f) }
    var masterPeakDbfs by remember { mutableFloatStateOf(-120f) }
    var masterRmsDbfs by remember { mutableFloatStateOf(-120f) }
    var masterMomentaryLufs by remember { mutableFloatStateOf(-120f) }
    var masterPeakHistory by remember { mutableStateOf(List(48) { 0f }) }
    var trackMeterReadings by remember { mutableStateOf<Map<String, Pair<Float, Float>>>(emptyMap()) }
    var selectedWorkspace by remember { mutableStateOf("ARRANGEMENT") }
    // Start with the device drawer collapsed so Arrangement gets the full vertical workspace.
    var showDock by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var scrollTicks by remember { mutableDoubleStateOf(0.0) }
    val configuration = LocalConfiguration.current
    // Follow playback inside the Arrangement viewport, keeping the playhead away from
    // the right edge. Dimensions are in dp here, matching the 74.dp bar scale below.
    LaunchedEffect(playing, playheadTick, zoom, scrollTicks, selectedWorkspace,
        project.bpm, project.numerator, project.denominator, project.ppq, configuration.screenWidthDp) {
        if (playing && selectedWorkspace == "ARRANGEMENT") {
            val timebase = TimelineTimebase(project.bpm, project.numerator, project.denominator, project.ppq)
            val timelineWidthDp = (configuration.screenWidthDp - 44f - 104f - 30f).coerceAtLeast(1f)
            val currentViewport = TimelineViewport(timebase, 74f * zoom, scrollTicks, timelineWidthDp)
            val followed = currentViewport.followPlayhead(playheadTick)
            if (kotlin.math.abs(followed.scrollTimeTicks - scrollTicks) > 0.5) {
                scrollTicks = followed.scrollTimeTicks
            }
        }
    }
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
        playing = false
        playbackLoading = false
        audioError = null
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
                        playbackScope.launch(Dispatchers.Main.immediate) {
                            if (session == playbackSession) {
                                playheadTick = snapshot.tracks.asSequence().flatMap { it.clips.asSequence() }
                                    .maxOfOrNull { clip ->
                                        val start = clip.startTick.coerceAtLeast(0L)
                                        val length = clip.lengthTicks.coerceAtLeast(1L)
                                        if (start > Long.MAX_VALUE - length) Long.MAX_VALUE else start + length
                                    } ?: playheadTick
                                playing = false
                                playbackLoading = false
                                playbackJob = null
                            }
                        }
                    },
                    onError = { message ->
                        playbackScope.launch(Dispatchers.Main.immediate) {
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
                        // Conflate progress from the audio worker to avoid an unbounded main-thread backlog.
                        playbackProgress.trySend(session to positionTicks.coerceAtLeast(0L))
                    }
                )
                if (session == playbackSession) {
                    playing = true
                    playbackLoading = false
                    // Keep transport state until AudioTrack drains; start() only launches its worker.
                    awaitCancellation()
                }
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
    fun seekToTick(tick: Long) {
        val target = tick.coerceAtLeast(0L)
        // Match DAW transport semantics: seeking while the transport is active is a
        // discontinuity, not an implicit STOP. Rebuild playback from the requested
        // musical position so the audio and playhead remain together.
        if (playing || playbackLoading) {
            startPlaybackAt(target)
        } else {
            playheadTick = target
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
                            val requestedLengthTicks = if (durationMs > 0L) {
                                (((exactTicks + snapStep - 1L) / snapStep) * snapStep).coerceAtLeast(snapStep)
                            } else 15360L
                            val arrangementEndTick = maxArrangementTick(timebase)
                            val requestedStart = target.clips.maxOfOrNull { clip ->
                                val end = clip.startTick.coerceAtLeast(0L)
                                val length = clip.lengthTicks.coerceAtLeast(0L)
                                if (end > Long.MAX_VALUE - length) Long.MAX_VALUE else end + length
                            } ?: 0L
                            val clipStart = requestedStart.coerceIn(0L, arrangementEndTick - 1L)
                            val lengthTicks = requestedLengthTicks.coerceIn(1L, arrangementEndTick - clipStart)
                            val clip = com.studiowave.app.data.WaveClip(
                                name = uri.lastPathSegment?.substringAfterLast('/')?.take(32)?.ifBlank { "Audio clip" } ?: "Audio clip",
                                startTick = clipStart,
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
                    playing = false
                    playbackLoading = false
                    changeProject(imported)
                    bpm = imported.bpm
                    scrollTicks = 0.0
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
    LaunchedEffect(arrangementPlayback, project.tracks.map { it.id }) {
        while (true) {
            trackMeterReadings = project.tracks.associate { track ->
                val meter = arrangementPlayback.trackMeterSnapshot(track.id)
                track.id to (meter.peak to meter.rms)
            }
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
    DisposableEffect(arrangementPlayback) {
        onDispose {
            playbackJob?.cancel()
            arrangementPlayback.close()
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
                    WorkspaceRail(
                        selected = selectedWorkspace,
                        devicesOpen = showDock,
                        onSelect = {
                        selectedWorkspace = it
                        showDock = false
                    },
                    onToggleDevices = { showDock = !showDock }
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (selectedWorkspace != "ARRANGEMENT") {
                            Row(
                                Modifier.fillMaxWidth().height(25.dp)
                                    .background(Color(0xFF292B30), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    when (selectedWorkspace) {
                                        "PIANO ROLL" -> "CLIP VIEW  /  MIDI NOTE EDITOR"
                                        "AUDIO EDITOR" -> "CLIP VIEW  /  AUDIO"
                                        "DRUM SEQUENCER" -> "DRUM RACK  /  STEP SEQUENCER"
                                        "MIXER" -> "MIXER"
                                        "BROWSER" -> "BROWSER  /  SAMPLES"
                                        else -> selectedWorkspace
                                    },
                                    color = AbletonOrange, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1
                                )
                                Spacer(Modifier.weight(1f))
                                Text(project.name.take(18), color = MutedText, fontSize = 8.sp, maxLines = 1)
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedWorkspace) {
                        "MIXER" -> MixerWorkspace(
                            project,
                            master,
                            trackMeterReadings = trackMeterReadings,
                            onMasterChange = { value ->
                                master = value.coerceIn(0f, 1f)
                                arrangementPlayback.setMasterGain(master)
                            },
                            onProjectChange = { changeProject(it) }
                        )
                        "PIANO ROLL" -> PianoRollWorkspace(
                            project.tracks.firstOrNull { it.id == editorTrackId }?.takeIf { it.kind == "midi" }
                                ?: project.tracks.firstOrNull { it.kind == "midi" },
                            project, { changeProject(it) }, onBack = { selectedWorkspace = "ARRANGEMENT" }
                        )
                        "BROWSER" -> BrowserWorkspace(onImportAudio = { audioPicker.launch(arrayOf("audio/*")) })
                        "DRUM SEQUENCER" -> DrumSequencerWorkspace(
                            project.tracks.firstOrNull { it.id == editorTrackId }?.takeIf { it.kind == "drum" }
                                ?: project.tracks.firstOrNull { it.kind == "drum" },
                            { changeProject(it) }, project, onBack = { selectedWorkspace = "ARRANGEMENT" }
                        )
                        "AUDIO EDITOR" -> AudioEditorWorkspace(
                            track = project.tracks.firstOrNull { it.id == editorTrackId }?.takeIf { it.kind == "audio" }
                                ?: project.tracks.firstOrNull { it.kind == "audio" },
                            project = project,
                            onProjectChange = { changeProject(it) },
                            onBack = { selectedWorkspace = "ARRANGEMENT" }
                        )
                        else -> ArrangementWorkspace(project, { changeProject(it) }, zoom, { zoom = it.coerceIn(0.1f, 16f) }, scrollTicks, { scrollTicks = it.coerceAtLeast(0.0) }, verticalZoom, { verticalZoom = it.coerceIn(0.65f, 1.8f) }, scrollTracks, { scrollTracks = it.coerceAtLeast(0f) }, { workspace, trackId ->
                                editorTrackId = trackId
                                selectedWorkspace = workspace
                                showDock = false
                            },
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
                    }
                }
                AnimatedVisibility(
                    visible = showDock && selectedWorkspace == "ARRANGEMENT",
                    enter = fadeIn(tween(120)) + slideInVertically(tween(150), initialOffsetY = { it / 4 }),
                    exit = fadeOut(tween(80)) + slideOutVertically(tween(100), targetOffsetY = { it / 4 })
                ) {
                    DeviceDock(
                        project = project,
                        selectedTrackId = editorTrackId,
                        onSelectTrack = { editorTrackId = it },
                        onProjectChange = { changeProject(it) },
                        onToggle = { showDock = false }
                    )
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
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF242528), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("StudioWave", fontSize = 13.sp, fontWeight = FontWeight.Black, color = AbletonOrange, maxLines = 1)
            Text("LIVE SET", color = Color(0xFF777B82), fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("BPM", color = MutedText, fontSize = 8.sp)
            SmallAction("−") { onBpm(bpm - 1) }
            Surface(color = Color(0xFF111315), shape = RoundedCornerShape(4.dp)) {
                Text(bpm.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
            SmallAction("+") { onBpm(bpm + 1) }
            Surface(color = Color(0xFF111315), shape = RoundedCornerShape(4.dp)) {
                Text(timeSignatureLabel, fontSize = 9.sp, color = MutedText,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp))
            }
            Spacer(Modifier.weight(1f))
            SmallIconButton(Icons.Default.FolderOpen, MutedText, onOpenProject)
            SmallIconButton(Icons.Default.Save, MutedText, onSaveProject)
            SmallIconButton(Icons.Default.Undo, MutedText, onUndo)
            SmallIconButton(Icons.Default.Redo, MutedText, onRedo)
        }
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            SmallIconButton(Icons.Default.Stop, MutedText, onStop)
            Surface(
                color = if (playing) Color(0xFF553126) else Color(0xFF352722),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier.size(width = 42.dp, height = 32.dp).clickable(onClick = onPlay)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause playback" else "Start playback",
                        tint = if (playing) AbletonOrange else Color(0xFFFF9A72), modifier = Modifier.size(22.dp))
                }
            }
            Surface(color = Color(0xFF111315), shape = RoundedCornerShape(5.dp)) {
                Text(positionLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFFE4E8EF), modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Text("MASTER", color = MutedText, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Slider(value = master, onValueChange = onMaster,
                modifier = Modifier.width(82.dp).height(30.dp), valueRange = 0f..1f)
            Column(Modifier.width(112.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("P ${peakDbfs.toInt()} dBFS", color = Color(0xFFE4E8EF), fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${momentaryLufs.toInt()} LUFS", color = Mint, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Canvas(Modifier.fillMaxWidth().height(9.dp)) {
                    drawLine(Color(0xFF343A40), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), size.height)
                    peakHistory.forEachIndexed { index, level ->
                        val bar = (level.coerceIn(0f, 1f) * size.height).coerceAtLeast(1f)
                        val x = index * size.width / peakHistory.size.coerceAtLeast(1)
                        drawLine(Mint.copy(alpha = 0.85f), Offset(x, size.height), Offset(x, size.height - bar),
                            (size.width / peakHistory.size.coerceAtLeast(1)).coerceAtLeast(1f))
                    }
                }
                Text("RMS ${rmsDbfs.toInt()} dBFS", color = MutedText, fontSize = 7.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun WorkspaceRail(
    selected: String,
    devicesOpen: Boolean,
    onSelect: (String) -> Unit,
    onToggleDevices: () -> Unit
) {
    Column(
        Modifier.width(48.dp).fillMaxHeight().background(Color(0xFF242528), RoundedCornerShape(7.dp)).padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RailButton("ARR", selected == "ARRANGEMENT", Icons.Default.GraphicEq) { onSelect("ARRANGEMENT") }
        RailButton("EDIT", selected == "PIANO ROLL" || selected == "AUDIO EDITOR" || selected == "DRUM SEQUENCER", Icons.Default.Keyboard) {
            when (selected) {
                "PIANO ROLL", "AUDIO EDITOR", "DRUM SEQUENCER" -> onSelect("ARRANGEMENT")
                else -> onSelect("PIANO ROLL")
            }
        }
        RailButton("MIX", selected == "MIXER", Icons.Default.Tune) { onSelect("MIXER") }
        RailButton("DEV", devicesOpen, Icons.Default.GraphicEq, onToggleDevices)
        RailButton("FILES", selected == "BROWSER", Icons.Default.LibraryMusic) { onSelect("BROWSER") }
    }
}

@Composable
private fun RailButton(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        color = if (selected) Color(0xFF4A2D24) else Color(0xFF191A1D),
        contentColor = if (selected) AbletonOrange else MutedText,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().height(43.dp).clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(17.dp))
            Text(label, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun WorkspaceTitleBar(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(29.dp).background(Color(0xFF292B30), RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 9.sp, color = AbletonOrange, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text(subtitle, fontSize = 8.sp, color = Color(0xFFB0B3BB), maxLines = 1)
        Spacer(Modifier.weight(1f))
        Surface(color = Color(0xFF383B41), shape = RoundedCornerShape(5.dp),
            modifier = Modifier.size(width = 38.dp, height = 23.dp)
                .clickable(onClickLabel = "Return to Arrangement", onClick = onBack)) {
            Box(contentAlignment = Alignment.Center) { Text("BACK", fontSize = 7.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ArrangementWorkspace(
    project: StudioProject,
    onProjectChange: (StudioProject) -> Unit,
    zoom: Float, onZoom: (Float) -> Unit,
    scrollTicks: Double, onScroll: (Double) -> Unit,
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
    var showClipView by remember { mutableStateOf(false) }
    val selectedClipTrack = project.tracks.firstOrNull { track -> track.clips.any { it.id == selectedClip } }
    val selectedClipModel = selectedClipTrack?.clips?.firstOrNull { it.id == selectedClip }
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
    var addTrackMenu by remember { mutableStateOf(false) }

    // Contextual overlays are mutually exclusive on compact screens.
    LaunchedEffect(showTimelineMenu, showAddTrack, addTrackMenu, clipMenu) {
        // A clip context menu, the timeline menu and either Add Track menu may never overlap.
        if (clipMenu != null) {
            if (showTimelineMenu) showTimelineMenu = false
            if (showAddTrack) showAddTrack = false
            if (addTrackMenu) addTrackMenu = false
        } else if (addTrackMenu) {
            if (showTimelineMenu) showTimelineMenu = false
            if (showAddTrack) showAddTrack = false
        } else if (showAddTrack && showTimelineMenu) {
            showTimelineMenu = false
        }
    }
    LaunchedEffect(project.tracks, selectedClip) {
        val stillExists = selectedClip != null && project.tracks.any { track -> track.clips.any { it.id == selectedClip } }
        if (!stillExists) {
            selectedClip = null
            showClipView = false
            clipMenu = null
        }
    }
    LaunchedEffect(selectedClipModel) {
        if (selectedClipModel == null) showClipView = false
    }

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
            "Open editor" -> {
                showClipView = true
                onOpenEditor(when (track.kind) { "drum" -> "DRUM SEQUENCER"; "audio" -> "AUDIO EDITOR"; else -> "PIANO ROLL" }, track.id)
            }
            "Cut" -> {
                // Arrangement Cut splits at the playhead; it must not delete the entire clip.
                val split = splitWaveClip(clip, playheadTick)
                when {
                    split == null -> statusMessage = "Place the playhead inside the clip to split it"
                    track.clips.size >= StudioProject.MAX_CLIPS_PER_TRACK -> statusMessage = "Clip limit reached for this track"
                    else -> {
                        val (left, right) = split
                        onProjectChange(project.copy(tracks = project.tracks.mapIndexed { index, item ->
                            if (index == trackIndex) item.copy(clips = (item.clips.filterNot { it.id == clipId } + left + right).sortedBy { it.startTick }) else item
                        }))
                        statusMessage = "Clip split at playhead"
                    }
                }
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
                .toFloat().coerceIn(0.1f, 16f)
        }
        Column(Modifier.fillMaxSize().background(Color(0xFF17181B), RoundedCornerShape(7.dp)).padding(1.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(104.dp)) {
                Row(Modifier.fillMaxWidth().height(22.dp).background(Raised), verticalAlignment = Alignment.CenterVertically) {
                    Text("TRACKS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MutedText, modifier = Modifier.padding(start = 5.dp))
                }
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
                                .size(34.dp).clickable {
                                    addTrackMenu = !addTrackMenu
                                    if (addTrackMenu) {
                                        showTimelineMenu = false
                                        showAddTrack = false
                                        clipMenu = null
                                    }
                                }
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
                                    "MIDI / Instrument track" to "midi",
                                    "Audio track" to "audio",
                                    "Drum track" to "drum"
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
                onClipLongPress = {
                    clipMenu = it
                    showTimelineMenu = false
                    showAddTrack = false
                    addTrackMenu = false
                },
                onTimelineLongPress = {
                    showTimelineMenu = true
                    showAddTrack = false
                    addTrackMenu = false
                    clipMenu = null
                },
                clipMenuClipId = clipMenu,
                onClipAction = { action, clipId -> handleClipAction(action, clipId) },
                onDismissClipMenu = { clipMenu = null },
                onResizeClip = { clipId, newStartTick, lengthTicks ->
                    val resizeTimebase = TimelineTimebase(project.bpm, project.numerator, project.denominator, project.ppq)
                    val arrangementEndTick = maxArrangementTick(resizeTimebase)
                    val safeStart = newStartTick.coerceIn(0L, arrangementEndTick - 1L)
                    val safeLength = lengthTicks.coerceIn(1L, arrangementEndTick - safeStart)
                    val resizedProject = project.copy(tracks = project.tracks.map { track ->
                        track.copy(clips = track.clips.map { clip ->
                            if (clip.id != clipId) clip else {
                                val delta = newStartTick - clip.startTick
                                val oldLength = clip.lengthTicks.coerceAtLeast(1L)
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
                                    sourceOffsetTicks = if (safeDelta >= 0L) {
                                        val oldOffset = clip.sourceOffsetTicks.coerceAtLeast(0L)
                                        if (oldOffset > Long.MAX_VALUE - safeDelta) Long.MAX_VALUE else oldOffset + safeDelta
                                    } else {
                                        (clip.sourceOffsetTicks.coerceAtLeast(0L) + safeDelta).coerceAtLeast(0L)
                                    },
                                    midiNotes = nextNotes,
                                    drumSteps = nextDrumSteps
                                )
                            }
                        })
                    })
                    onProjectChange(resizedProject)
                    // Do not flush/recreate AudioTrack just to commit an edge edit; this
                    // causes an audible gap. A fresh plan is built on the next explicit Play/seek.
                },
                onMoveClip = { clipId, targetTrackId, startTick ->
                    val moveTimebase = TimelineTimebase(project.bpm, project.numerator, project.denominator, project.ppq)
                    val arrangementEndTick = maxArrangementTick(moveTimebase)
                    val movingClip = project.tracks.asSequence().flatMap { it.clips.asSequence() }
                        .firstOrNull { it.id == clipId }
                    val maxStartTick = (arrangementEndTick - (movingClip?.lengthTicks ?: 1L).coerceAtLeast(1L))
                        .coerceAtLeast(0L)
                    val boundedStartTick = startTick.coerceIn(0L, maxStartTick)
                    val movedProject = project.moveClipToTrack(clipId, targetTrackId, boundedStartTick)
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
                    onScroll(0.0)
                    onScrollTracks(0f)
                }
                FloatingTimelineControl(if (snapEnabled) "SN" else "SN×", if (snapEnabled) "Snap on" else "Snap off") {
                    snapEnabled = !snapEnabled
                }
                FloatingTimelineControl(snapDivision.label, "Change snap division") {
                    snapDivision = snapDivision.next().let { if (it == SnapDivision.OFF) SnapDivision.BAR else it }
                }
                FloatingTimelineControl(if (showClipView) "CLIP×" else "CLIP", "Show selected clip details") {
                    if (selectedClipModel == null) {
                        statusMessage = "Select a clip first"
                    } else {
                        showClipView = !showClipView
                    }
                }
            }
        if (showClipView && selectedClipTrack != null && selectedClipModel != null) {
            val clip = selectedClipModel
            val timebase = TimelineTimebase(project.bpm, project.numerator, project.denominator, project.ppq)
            val firstBar = clip.startTick / timebase.ticksPerBar + 1L
            val lastTick = if (clip.startTick > Long.MAX_VALUE - clip.lengthTicks.coerceAtLeast(1L)) Long.MAX_VALUE
                else clip.startTick + clip.lengthTicks.coerceAtLeast(1L) - 1L
            val lastBar = lastTick / timebase.ticksPerBar + 1L
            Surface(
                color = Color(0xF21B1D21),
                contentColor = Color.White,
                shape = RoundedCornerShape(9.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF454950)),
                shadowElevation = 8.dp,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 110.dp, bottom = 8.dp)
                    .widthIn(min = 220.dp, max = 300.dp)
            ) {
                Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CLIP VIEW", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(7.dp))
                        Text(selectedClipTrack.name, color = MutedText, fontSize = 8.sp, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Text("×", color = MutedText, fontSize = 14.sp, modifier = Modifier.clickable { showClipView = false }.padding(horizontal = 3.dp))
                    }
                    Text(clip.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        "${selectedClipTrack.kind.uppercase()}  •  BAR ${firstBar}–${lastBar}  •  ${String.format(java.util.Locale.US, "%.2f", clip.lengthTicks.toDouble() / timebase.ticksPerBar)} bars",
                        color = MutedText, fontSize = 8.sp, maxLines = 1
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallAction("OPEN EDITOR") {
                            onOpenEditor(when (selectedClipTrack.kind) {
                                "audio" -> "AUDIO EDITOR"
                                "drum" -> "DRUM SEQUENCER"
                                else -> "PIANO ROLL"
                            }, selectedClipTrack.id)
                        }
                        SmallAction("DUPLICATE") { handleClipAction("Duplicate", clip.id) }
                        SmallAction("− BAR") {
                            val nextStart = (clip.startTick - timebase.ticksPerBar).coerceAtLeast(0L)
                            onProjectChange(project.copy(tracks = project.tracks.map { track ->
                                if (track.id == selectedClipTrack.id) track.copy(clips = track.clips.map { item ->
                                    if (item.id == clip.id) item.copy(startTick = nextStart) else item
                                }) else track
                            }))
                        }
                        SmallAction("+ BAR") {
                            val limit = maxArrangementTick(timebase)
                            val nextStart = (clip.startTick + timebase.ticksPerBar).coerceAtMost((limit - clip.lengthTicks.coerceAtLeast(1L)).coerceAtLeast(0L))
                            onProjectChange(project.copy(tracks = project.tracks.map { track ->
                                if (track.id == selectedClipTrack.id) track.copy(clips = track.clips.map { item ->
                                    if (item.id == clip.id) item.copy(startTick = nextStart) else item
                                }) else track
                            }))
                        }
                        SmallAction("SHORTEN") {
                            val nextLength = (clip.lengthTicks - timebase.ticksPerBar).coerceAtLeast(timebase.ticksPerBeat)
                            onProjectChange(project.copy(tracks = project.tracks.map { track ->
                                if (track.id == selectedClipTrack.id) track.copy(clips = track.clips.map { item ->
                                    if (item.id == clip.id) item.copy(lengthTicks = nextLength) else item
                                }) else track
                            }))
                        }
                        SmallAction("LENGTH +") {
                            val limit = maxArrangementTick(timebase)
                            val maxLength = (maxArrangementTick(timebase) - clip.startTick.coerceAtLeast(0L)).coerceAtLeast(timebase.ticksPerBeat)
                            val nextLength = (clip.lengthTicks + timebase.ticksPerBar).coerceAtMost(maxLength)
                            onProjectChange(project.copy(tracks = project.tracks.map { track ->
                                if (track.id == selectedClipTrack.id) track.copy(clips = track.clips.map { item ->
                                    if (item.id == clip.id) item.copy(lengthTicks = nextLength) else item
                                }) else track
                            }))
                        }
                        if (selectedClipTrack.kind == "audio") {
                            SmallAction("TRIM IN −") {
                                val nextOffset = (clip.sourceOffsetTicks - timebase.ticksPerBeat).coerceAtLeast(0L)
                                onProjectChange(project.copy(tracks = project.tracks.map { track ->
                                    if (track.id == selectedClipTrack.id) track.copy(clips = track.clips.map { item ->
                                        if (item.id == clip.id) item.copy(sourceOffsetTicks = nextOffset) else item
                                    }) else track
                                }))
                            }
                            SmallAction("TRIM IN +") {
                                val nextOffset = (clip.sourceOffsetTicks + timebase.ticksPerBeat).coerceAtMost(maxArrangementTick(timebase))
                                onProjectChange(project.copy(tracks = project.tracks.map { track ->
                                    if (track.id == selectedClipTrack.id) track.copy(clips = track.clips.map { item ->
                                        if (item.id == clip.id) item.copy(sourceOffsetTicks = nextOffset) else item
                                    }) else track
                                }))
                            }
                        }
                    }
                }
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
                    Text("Add track", color = Color.White, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable {
                        showAddTrack = true
                        showTimelineMenu = false
                        addTrackMenu = false
                        clipMenu = null
                    }.padding(9.dp))
                    Text("Paste clip", color = Color.White, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable { pasteClip() }.padding(9.dp))
                    Text("Import audio", color = Color.White, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable {
                        showTimelineMenu = false
                        showAddTrack = false
                        addTrackMenu = false
                        onOpenEditor("BROWSER", null)
                    }.padding(9.dp))
                }
            }
            if (showAddTrack && !addTrackMenu) Surface(color = Raised, shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp, modifier = Modifier.padding(6.dp)) {
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
    val rawSteps = clip?.drumSteps ?: emptyList()
    val steps = rawSteps.take(16) + List((16 - rawSteps.size).coerceAtLeast(0)) { false }
    Column(Modifier.fillMaxSize().background(Color(0xFF17191C)).padding(7.dp)) {
        WorkspaceTitleBar("DRUM RACK  /  STEP SEQUENCER", track?.name ?: "No drum track", onBack)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("16 STEPS", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp))
            SmallAction("CLEAR") {
                if (track != null && clip != null) onProjectChange(project.copy(tracks = project.tracks.map { item ->
                    if (item.id == track.id) item.copy(clips = item.clips.map { c -> if (c.id == clip.id) c.copy(drumSteps = List(16) { false }) else c }) else item
                }))
            }
            SmallAction("ALL") {
                if (track != null && clip != null) onProjectChange(project.copy(tracks = project.tracks.map { item ->
                    if (item.id == track.id) item.copy(clips = item.clips.map { c -> if (c.id == clip.id) c.copy(drumSteps = List(16) { true }) else c }) else item
                }))
            }
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..15).forEach { step ->
                val isActive = steps[step]
                val accent = step % 4 == 0
                Surface(
                    color = if (isActive) Mint else if (accent) Color(0xFF343A43) else Color(0xFF222428),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable {
                        if (track != null && clip != null) {
                            val updated = steps.toMutableList().also { it[step] = !it[step] }
                            onProjectChange(project.copy(tracks = project.tracks.map { item ->
                                if (item.id == track.id) item.copy(clips = item.clips.map { c -> if (c.id == clip.id) c.copy(drumSteps = updated) else c }) else item
                            }))
                        }
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) { Text((step + 1).toString(), color = if (isActive) Color(0xFF141619) else Color(0xFFE4E8EF), fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Text("Tap steps to toggle triggers. CLEAR and ALL modify this clip’s 16-step pattern.", color = MutedText, fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AudioEditorWorkspace(
    track: StudioTrack?,
    project: StudioProject,
    onProjectChange: (StudioProject) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clip = track?.clips?.firstOrNull { it.sourceUri != null }
    val baseLengthTicks = remember(clip?.id) { (clip?.lengthTicks ?: 1L).coerceAtLeast(1L) }
    val baseSourceOffsetTicks = remember(clip?.id) { (clip?.sourceOffsetTicks ?: 0L).coerceAtLeast(0L) }
    var startFraction by remember(clip?.id) { mutableFloatStateOf(0f) }
    var endFraction by remember(clip?.id) { mutableFloatStateOf(1f) }

    fun applyTrim(startFractionValue: Float, endFractionValue: Float) {
        val activeTrack = track ?: return
        val activeClip = clip ?: return
        val safeStart = startFractionValue.coerceIn(0f, 0.99f)
        val safeEnd = endFractionValue.coerceIn(safeStart + 0.01f, 1f)
        val startTicks = (baseLengthTicks.toDouble() * safeStart.toDouble()).toLong()
            .coerceIn(0L, (baseLengthTicks - 1L).coerceAtLeast(0L))
        val endTicks = (baseLengthTicks.toDouble() * safeEnd.toDouble()).toLong()
            .coerceIn(startTicks + 1L, baseLengthTicks)
        val sourceOffset = if (baseSourceOffsetTicks > Long.MAX_VALUE - startTicks) Long.MAX_VALUE
            else baseSourceOffsetTicks + startTicks
        val nextLength = (endTicks - startTicks).coerceAtLeast(1L)
        onProjectChange(project.copy(tracks = project.tracks.map { item ->
            if (item.id != activeTrack.id) item else item.copy(clips = item.clips.map { current ->
                if (current.id == activeClip.id) current.copy(
                    sourceOffsetTicks = sourceOffset,
                    lengthTicks = nextLength
                ) else current
            })
        }))
    }
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

    Column(Modifier.fillMaxSize().background(Color(0xFF17191C)).padding(7.dp)) {
        WorkspaceTitleBar("CLIP VIEW  /  SAMPLE EDITOR", track?.name ?: "No audio track", onBack)
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (clip?.sourceUri != null) "AUDIO SOURCE" else "NO AUDIO CLIP", color = AbletonOrange, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            SmallAction("RESET RANGE") {
                startFraction = 0f
                endFraction = 1f
                applyTrim(0f, 1f)
            }
        }
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("START", color = MutedText, fontSize = 7.sp)
            Slider(value = startFraction, onValueChange = { value ->
                startFraction = value.coerceIn(0f, (endFraction - 0.01f).coerceAtLeast(0f))
                applyTrim(startFraction, endFraction)
            }, modifier = Modifier.weight(1f).height(20.dp))
            Text("END", color = MutedText, fontSize = 7.sp)
            Slider(value = endFraction, onValueChange = { value ->
                endFraction = value.coerceIn((startFraction + 0.01f).coerceAtMost(1f), 1f)
                applyTrim(startFraction, endFraction)
            }, modifier = Modifier.weight(1f).height(20.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF27292E), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
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
            when {
                waveform.isNotEmpty() -> "SOURCE WAVEFORM  ·  " + waveform.size + " peaks  ·  range " + (startFraction * 100).toInt() + "–" + (endFraction * 100).toInt() + "%"
                loading -> "READING SOURCE WAVEFORM…"
                else -> loadError ?: "IMPORT AN AUDIO CLIP TO BEGIN"
            },
            color = MutedText, fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp)
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
        Column(Modifier.weight(1f).combinedClickable(onClick = {}, onDoubleClick = onDoubleTap, onLongClick = onDoubleTap)) {
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
    scrollTicks: Double,
    onScroll: (Double) -> Unit,
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
    val pixelsPerBar = TimelineViewport(timebase, requestedPixelsPerBar, scrollTicks, 0f)
        .effectivePixelsPerBar
    val viewport = remember(timebase, pixelsPerBar, scrollTicks, trackHeaderWidthPx) {
        TimelineViewport(timebase, pixelsPerBar, scrollTicks, 0f, trackHeaderWidthPx)
    }
    val trackHeightPx = with(density) { 67.dp.toPx() } * verticalZoom
    val latestProject by rememberUpdatedState(project)
    val moveCommit by rememberUpdatedState(onMoveClip)
    val snapToGrid by rememberUpdatedState(snapEnabled)
    val trackCount = project.tracks.size
    val rulerPaint = remember(density) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = with(density) { 9.sp.toPx() } } }
    val currentZoom by rememberUpdatedState(zoom)
    val currentScroll by rememberUpdatedState(scrollTicks)
    val currentPlayhead by rememberUpdatedState(playheadTick)
    val currentScrollTracks by rememberUpdatedState(scrollTracks)
    val currentVerticalZoom by rememberUpdatedState(verticalZoom)
    val currentOnVerticalZoom by rememberUpdatedState(onVerticalZoom)
    val currentOnScrollTracks by rememberUpdatedState(onScrollTracks)
    val currentViewport by rememberUpdatedState(viewport)
    val currentTrackHeightPx by rememberUpdatedState(trackHeightPx)
    val currentTimebase by rememberUpdatedState(timebase)
    val currentSnapDivision by rememberUpdatedState(snapDivision)
    val currentSnapEnabled by rememberUpdatedState(snapToGrid)
    val currentOnZoom by rememberUpdatedState(onZoom)
    val currentOnScroll by rememberUpdatedState(onScroll)
    val currentOnSeekTick by rememberUpdatedState(onSeekTick)
    val currentTrackRows by rememberUpdatedState(trackRowLayout)
    var draggingClip by remember { mutableStateOf<String?>(null) }
    var resizingClip by remember { mutableStateOf<String?>(null) }
    var liveResizeStart by remember { mutableLongStateOf(0L) }
    var liveResizeLength by remember { mutableLongStateOf(1L) }
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
                            val topPx = (row.topDp - currentScrollTracks) * currentVerticalZoom * density.density
                            val heightPx = row.heightDp * currentVerticalZoom * density.density
                            rowY >= topPx && rowY <= topPx + heightPx
                        }
                        val hitTrack = rowIndex?.let { hitProject.tracks.getOrNull(it) }
                        if (hitTrack == null) {
                            false
                        } else {
                            val hitViewport = TimelineViewport(
                                timebase, baseBarWidthPx * currentZoom,
                                currentScroll, size.width.toFloat(), trackHeaderWidthPx
                            )
                            val pointerX = firstDown.position.x
                            hitTrack.clips.any { clip ->
                                val left = hitViewport.timeToViewportX(clip.startTick)
                                val right = left + hitViewport.tickDeltaToPixels(clip.lengthTicks.coerceAtLeast(1L))
                                pointerX >= left && pointerX <= right
                            }
                        }
                    }
                    var previousSinglePosition = firstDown.position
                    var previousCentroid = Offset.Zero
                    var previousSpan = 0f
                    var initialized = false
                    var multiTouchGestureOccurred = false
                    var gestureBaseSpan = 0f
                    var gesturePointerCount = 0
                    var gestureBaseZoom = currentZoom.coerceIn(0.1f, 16f)
                    var gestureBaseVerticalZoom = currentVerticalZoom.coerceIn(0.65f, 1.8f)
                    var gestureBaseScrollTicks = currentScroll
                    var gestureAnchorTick = 0.0
                    var gestureAnchorTrackDp = 0f
                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.filter { it.pressed }
                        if (pointers.size >= 2) {
                            multiTouchGestureOccurred = true
                            val centroid = Offset(
                                pointers.map { it.position.x }.average().toFloat(),
                                pointers.map { it.position.y }.average().toFloat()
                            )
                            val span = pointers.map { (it.position - centroid).getDistance() }.average().toFloat() * 2f
                            if (initialized && pointers.size == gesturePointerCount) {
                                // Derive every scale/scroll frame from the same baseline.
                                val frame = calculatePinchZoomFrame(
                                    timebase, gestureBaseZoom, gestureBaseScrollTicks, gestureBaseSpan, span,
                                    gestureAnchorTick, centroid.x, baseBarWidthPx, size.width.toFloat()
                                )
                                val spanRatio = if (gestureBaseSpan > 1f && span > 1f) span / gestureBaseSpan else 1f
                                val newZoom = frame.zoom
                                val newScroll = frame.scrollTicks
                                val oldZoom = currentZoom.coerceIn(0.1f, 16f)
                                val oldVerticalZoom = currentVerticalZoom.coerceIn(0.65f, 1.8f)
                                val newVerticalZoom = (gestureBaseVerticalZoom * spanRatio).coerceIn(0.65f, 1.8f)
                                val newTrackScroll = (gestureAnchorTrackDp - (centroid.y - rulerHeightPx) /
                                    (density.density * newVerticalZoom).coerceAtLeast(0.1f)).coerceAtLeast(0f)
                                if (kotlin.math.abs(newZoom - oldZoom) > 0.0005f) currentOnZoom(newZoom)
                                if (kotlin.math.abs(newScroll - currentScroll) > 0.0005) currentOnScroll(newScroll)
                                if (kotlin.math.abs(newVerticalZoom - oldVerticalZoom) > 0.0005f) currentOnVerticalZoom(newVerticalZoom)
                                if (kotlin.math.abs(newTrackScroll - currentScrollTracks) > 0.0005f) currentOnScrollTracks(newTrackScroll)
                            }
                            if (!initialized || pointers.size != gesturePointerCount) {
                                gestureBaseSpan = span.coerceAtLeast(1f)
                                gestureBaseZoom = currentZoom.coerceIn(0.1f, 16f)
                                gesturePointerCount = pointers.size
                                gestureBaseVerticalZoom = currentVerticalZoom.coerceIn(0.65f, 1.8f)
                                gestureBaseScrollTicks = currentScroll
                                val baseViewport = TimelineViewport(
                                    timebase, baseBarWidthPx * gestureBaseZoom, gestureBaseScrollTicks, size.width.toFloat()
                                )
                                // Preserve a fractional tick anchor in the timeline's local coordinate space.
                                gestureAnchorTick = baseViewport.scrollTimeTicks + centroid.x.toDouble() / baseViewport.pixelsPerTick
                                gestureAnchorTrackDp = currentScrollTracks +
                                    (centroid.y - rulerHeightPx) / (density.density * gestureBaseVerticalZoom).coerceAtLeast(0.1f)
                            }
                            previousCentroid = centroid
                            previousSpan = span
                            initialized = true
                            event.changes.forEach { change -> if (change.position != change.previousPosition) change.consume() }
                        } else {
                            initialized = false
                            gestureBaseSpan = 0f
                            gesturePointerCount = 0
                            val pointer = pointers.firstOrNull()
                            if (pointer != null) {
                                val delta = pointer.position - previousSinglePosition
                                if (!multiTouchGestureOccurred && !startsOnClip &&
                                    (kotlin.math.abs(delta.x) > 0.01f || kotlin.math.abs(delta.y) > 0.01f)) {
                                    val timebaseNow = TimelineTimebase(latestProject.bpm, latestProject.numerator, latestProject.denominator, latestProject.ppq)
                                       val barWidthPx = TimelineViewport(timebaseNow, baseBarWidthPx * currentZoom, currentScroll, size.width.toFloat()).effectivePixelsPerBar
                                    val newScroll = TimelineViewport(timebaseNow, barWidthPx, currentScroll, size.width.toFloat(), trackHeaderWidthPx).panByScreenPixels(delta.x).scrollTimeTicks
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
        // Stable follow loop: do not cancel/restart it for each playhead update.
        LaunchedEffect(isPlaying, timelineWidthPx, timebase) {
            if (isPlaying && timelineWidthPx > 0f) {
                var previousFrameNanos = 0L
                while (isPlaying) {
                    val frameNanos = withFrameNanos { it }
                    val dt = if (previousFrameNanos == 0L) 0.016f
                        else ((frameNanos - previousFrameNanos).coerceAtLeast(0L) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    previousFrameNanos = frameNanos
                    val currentViewport = TimelineViewport(
                        timebase, baseBarWidthPx * currentZoom, currentScroll,
                        timelineWidthPx, trackHeaderWidthPx
                    )
                    val currentX = currentViewport.timeToViewportX(currentPlayhead)
                    val leftGuard = timelineWidthPx * 0.08f
                    val rightGuard = timelineWidthPx * 0.70f
                    val targetX = when {
                        currentX < leftGuard -> leftGuard
                        currentX > rightGuard -> rightGuard
                        else -> null
                    }
                    if (targetX != null) {
                        val targetScroll = currentViewport.placeTickAtViewportX(currentPlayhead, targetX).scrollTimeTicks
                        val smoothing = (1.0 - kotlin.math.exp(-dt.toDouble() / 0.065)).toFloat()
                        val nextScroll = currentScroll + (targetScroll - currentScroll) * smoothing
                        if (kotlin.math.abs(nextScroll - currentScroll) > 0.05) currentOnScroll(nextScroll)
                    }
                }
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
                     val subdivision = canvasViewport.gridSubdivisionTicks(beatPixels)
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

        // Arrangement Overview mirrors Live's whole-song map and its current viewport.
        Row(
            Modifier.fillMaxWidth().height(18.dp)
                .background(Color(0xFF232529), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("OVERVIEW", color = MutedText, fontSize = 7.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(50.dp))
            Canvas(
                Modifier.weight(1f).fillMaxHeight()
                    .pointerInput(project.tracks, scrollTicks, zoom) {
                        detectTapGestures { position ->
                            val endTick = maxOf(
                                project.tracks.flatMap { it.clips }.maxOfOrNull { it.startTick + it.lengthTicks.coerceAtLeast(1L) } ?: timebase.ticksPerBar,
                                timebase.ticksPerBar
                            ).coerceAtMost(maxArrangementTick(timebase))
                            val fraction = (position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val visibleTicks = size.width.toDouble() /
                                TimelineViewport(timebase, 74f * zoom, scrollTicks, size.width.toFloat()).pixelsPerTick.toDouble()
                            val maxScroll = (maxArrangementTick(timebase).toDouble() - visibleTicks).coerceAtLeast(0.0)
                            currentOnScroll((endTick.toDouble() * fraction).coerceIn(0.0, maxScroll))
                        }
                    }
            ) {
                val endTick = maxOf(
                    project.tracks.flatMap { it.clips }.maxOfOrNull { it.startTick + it.lengthTicks.coerceAtLeast(1L) } ?: timebase.ticksPerBar,
                    timebase.ticksPerBar
                ).coerceAtMost(maxArrangementTick(timebase)).coerceAtLeast(1L)
                val rows = project.tracks.size.coerceAtLeast(1)
                val rowHeight = size.height / rows
                project.tracks.forEachIndexed { rowIndex, track ->
                    track.clips.forEach { clip ->
                        val left = clip.startTick.toDouble() / endTick.toDouble() * size.width
                        val width = clip.lengthTicks.toDouble() / endTick.toDouble() * size.width
                        drawRoundRect(
                            Color(track.colorArgb.toInt()).copy(alpha = 0.88f),
                            Offset(left.toFloat(), rowIndex * rowHeight),
                            Size(width.toFloat().coerceAtLeast(1f), rowHeight.coerceAtLeast(1f)),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f)
                        )
                    }
                }
                val pxPerTick = viewport.pixelsPerTick
                val viewportStart = (scrollTicks / endTick.toDouble() * size.width).toFloat().coerceIn(0f, size.width)
                val viewportTicks = size.width.toDouble() / pxPerTick.toDouble()
                val visibleWidth = (viewportTicks / endTick.toDouble() * size.width).toFloat().coerceIn(2f, size.width)
                drawRoundRect(
                    Color.White.copy(alpha = 0.2f), Offset(viewportStart, 0f),
                    Size(visibleWidth, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
                )
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
                        val effectiveClipStart = if (resizingClip == clip.id) liveResizeStart else clip.startTick
                        val effectiveClipLength = if (resizingClip == clip.id) liveResizeLength else clip.lengthTicks
                        val arrangementEndTick = maxArrangementTick(timebase)
                        val boundedClipStart = effectiveClipStart.coerceIn(0L, arrangementEndTick)
                        val boundedClipLength = effectiveClipLength.coerceAtLeast(1L)
                            .coerceAtMost((arrangementEndTick - boundedClipStart).coerceAtLeast(1L))
                        val clipWidthPx = viewport.tickDeltaToPixels(boundedClipLength).coerceAtLeast(1f)
                        val clipWidthDp = with(density) { clipWidthPx.toDp().value }
                        val resizeGripWidthDp = minOf(18f, clipWidthDp * 0.38f).coerceAtLeast(0.01f)
                        val resizeGripWidthPx = with(density) { resizeGripWidthDp.dp.toPx() }
                        val displayLeftPx = viewport.timeToViewportX(boundedClipStart)
                        // Avoid composing or drawing clips fully outside the viewport.
                        if (boundedClipStart < arrangementEndTick &&
                            displayLeftPx + clipWidthPx >= 0f && displayLeftPx <= viewportWidthPx) {
                        val accent = Color((clip.colorArgb ?: track.colorArgb).toInt())
                        val clipFill by androidx.compose.animation.animateColorAsState(
                            targetValue = accent.copy(alpha = if (selectedClip == clip.id) 0.78f else 0.42f),
                            animationSpec = tween(durationMillis = 140),
                            label = "clipSelectionFill"
                        )
                        Column(
                            Modifier
                                .offset(x = with(density) { displayLeftPx.toDp() }, y = 0.dp)
                                .graphicsLayer {
                                    translationX = if (draggingClip == clip.id) dragDx else 0f
                                    translationY = if (draggingClip == clip.id) dragDy else 0f
                                }
                                .padding(top = 7.dp, bottom = 7.dp)
                                .timelineClipWidthPx(clipWidthPx)
                                .fillMaxHeight()
                                .background(clipFill, RoundedCornerShape(5.dp))
                                .then(if (selectedClip == clip.id) Modifier.padding(1.dp) else Modifier)
                                .pointerInput(clip.id, trackIndex) {
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
                                                (startIndex + kotlin.math.round(dragDy / currentTrackHeightPx.coerceAtLeast(1f)).toInt())
                                                    .coerceIn(0, currentTracks.lastIndex.coerceAtLeast(0))
                                            } else {
                                                rowLayout.indices.minByOrNull { index ->
                                                    kotlin.math.abs(rowLayout[index].topDp + rowLayout[index].heightDp / 2f - desiredCenter)
                                                } ?: startIndex
                                            }
                                            val target = currentTracks.getOrNull(targetIndex)
                                            val barWidthPx = currentViewport.effectivePixelsPerBar
                                            val dragViewport = TimelineViewport(
                                                timebase, barWidthPx, currentScroll,
                                                timelineWidthPx, trackHeaderWidthPx
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
                                        .pointerInput(clip.id) {
                                            var gestureViewport = currentViewport
                                            var gestureTimebase = currentTimebase
                                            var gestureSnapDivision = currentSnapDivision
                                            var gestureSnapEnabled = currentSnapEnabled
                                            var sourceOffsetAtStart = clip.sourceOffsetTicks.coerceAtLeast(0L)
                                            detectDragGestures(
                                                onDragStart = {
                                                    gestureViewport = currentViewport
                                                    gestureTimebase = currentTimebase
                                                    gestureSnapDivision = currentSnapDivision
                                                    gestureSnapEnabled = currentSnapEnabled
                                                    sourceOffsetAtStart = clip.sourceOffsetTicks.coerceAtLeast(0L)
                                                    leftResizeDeltaPx = 0f
                                                    liveResizeStart = clip.startTick.coerceAtLeast(0L)
                                                    liveResizeLength = clip.lengthTicks.coerceAtLeast(1L)
                                                    resizingClip = clip.id
                                                    onSelectClip(clip.id)
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    leftResizeDeltaPx += amount.x
                                                    val originalStart = clip.startTick.coerceAtLeast(0L)
                                                    val originalLength = clip.lengthTicks.coerceAtLeast(1L)
                                                    val originalEnd = if (originalStart > Long.MAX_VALUE - originalLength) Long.MAX_VALUE else originalStart + originalLength
                                                    val step = gestureSnapDivision.ticks(gestureTimebase)
                                                    val minimumLength = if (gestureSnapEnabled && step > 0L) step else 1L
                                                    val earliestStart = (originalStart - sourceOffsetAtStart).coerceAtLeast(0L)
                                                    val latestStart = (originalEnd - minimumLength).coerceAtLeast(earliestStart)
                                                    val raw = (originalStart.toDouble() + gestureViewport.pixelsToTickDelta(leftResizeDeltaPx).toDouble())
                                                        .coerceIn(0.0, Long.MAX_VALUE.toDouble()).toLong()
                                                    liveResizeStart = gestureViewport.snapTick(raw, gestureSnapDivision, gestureSnapEnabled).coerceIn(earliestStart, latestStart)
                                                    liveResizeLength = (originalEnd - liveResizeStart).coerceAtLeast(minimumLength)
                                                },
                                                onDragEnd = {
                                                    if (resizingClip == clip.id) {
                                                        onResizeClip(clip.id, liveResizeStart, liveResizeLength)
                                                    }
                                                    resizingClip = null
                                                    leftResizeDeltaPx = 0f
                                                },
                                                onDragCancel = {
                                                    resizingClip = null
                                                    leftResizeDeltaPx = 0f
                                                }
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
                                        .pointerInput(clip.id) {
                                            var gestureViewport = currentViewport
                                            var gestureTimebase = currentTimebase
                                            var gestureSnapDivision = currentSnapDivision
                                            var gestureSnapEnabled = currentSnapEnabled
                                            detectDragGestures(
                                                onDragStart = {
                                                    gestureViewport = currentViewport
                                                    gestureTimebase = currentTimebase
                                                    gestureSnapDivision = currentSnapDivision
                                                    gestureSnapEnabled = currentSnapEnabled
                                                    resizeDeltaPx = 0f
                                                    liveResizeStart = clip.startTick.coerceAtLeast(0L)
                                                    liveResizeLength = clip.lengthTicks.coerceAtLeast(1L)
                                                    resizingClip = clip.id
                                                    onSelectClip(clip.id)
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    resizeDeltaPx += amount.x
                                                    val originalStart = clip.startTick.coerceAtLeast(0L)
                                                    val originalLength = clip.lengthTicks.coerceAtLeast(1L)
                                                    val originalEnd = if (originalStart > Long.MAX_VALUE - originalLength) Long.MAX_VALUE else originalStart + originalLength
                                                    val rawEnd = (originalEnd.toDouble() +
                                                        gestureViewport.pixelsToTickDelta(resizeDeltaPx).toDouble())
                                                        .coerceIn((originalStart.toDouble() + 1.0).coerceAtMost(Long.MAX_VALUE.toDouble()), Long.MAX_VALUE.toDouble()).toLong()
                                                    val step = gestureSnapDivision.ticks(gestureTimebase)
                                                    val minimumLength = if (gestureSnapEnabled && step > 0L) step else 1L
                                                    val snappedEnd = gestureViewport.snapTick(rawEnd, gestureSnapDivision, gestureSnapEnabled)
                                                        .coerceAtLeast((originalStart + minimumLength).coerceAtMost(Long.MAX_VALUE))
                                                    liveResizeStart = originalStart
                                                    liveResizeLength = (snappedEnd - originalStart).coerceAtLeast(minimumLength)
                                                },
                                                onDragEnd = {
                                                    if (resizingClip == clip.id) {
                                                        onResizeClip(clip.id, liveResizeStart, liveResizeLength)
                                                    }
                                                    resizingClip = null
                                                    resizeDeltaPx = 0f
                                                },
                                                onDragCancel = {
                                                    resizingClip = null
                                                    resizeDeltaPx = 0f
                                                }
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
                timebase, viewport.effectivePixelsPerBar, scrollTicks.toDouble(), size.width, trackHeaderWidthPx
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
    var snapDenominator by remember(track?.id, clip?.id) { mutableIntStateOf(16) }
    var keyboardVisible by remember(track?.id) { mutableStateOf(true) }
    var zoomX by remember(track?.id, clip?.id) { mutableFloatStateOf(1.0f) }
    var zoomY by remember(track?.id) { mutableFloatStateOf(1.0f) }
    var startStep by remember(track?.id, clip?.id) { mutableIntStateOf(0) }
    var topPitch by remember(track?.id) { mutableIntStateOf(108) } // C8 at the top initially
    var selectedNoteId by remember(track?.id, clip?.id) { mutableStateOf<String?>(null) }
    var clipboard by remember(track?.id, clip?.id) { mutableStateOf<com.studiowave.app.data.MidiNote?>(null) }
    var editMessage by remember(track?.id, clip?.id) { mutableStateOf("Pinch to zoom · drag empty grid to scroll") }

    val stepTicks = (project.ppq.toLong() * 4L / snapDenominator.toLong()).coerceAtLeast(1L)
    val beatTicks = (project.ppq.toLong() * 4L / project.denominator.coerceAtLeast(1).toLong()).coerceAtLeast(1L)
    val stepsPerBeat = (beatTicks / stepTicks).toInt().coerceAtLeast(1)
    val stepsPerBar = ((beatTicks * project.numerator.coerceAtLeast(1).toLong()) / stepTicks).toInt().coerceAtLeast(1)
    val pitchRows = 120
    val rowHeight = (15f * zoomY).coerceIn(7f, 34f)
    val stepsOnScreen = (32f / zoomX.coerceIn(0.45f, 5f)).roundToInt().coerceIn(6, 72)
    val rawLengthTicks = (clip?.lengthTicks ?: stepTicks * 32L).coerceAtLeast(stepTicks)
    val clipLengthSteps = ((rawLengthTicks + stepTicks - 1L) / stepTicks)
        .coerceAtMost(1_000_000L).toInt().coerceAtLeast(stepsOnScreen)
    val maxStartStep = (clipLengthSteps - stepsOnScreen).coerceAtLeast(0)
    val pageStartStep = startStep.coerceIn(0, maxStartStep)
    val currentTopPitch = topPitch.coerceIn(0, 119)
    val highestPitch = currentTopPitch
    val lowestPitch = (highestPitch - 119 + 1).coerceAtLeast(0)
    val visiblePitchRows = (600f / rowHeight).roundToInt().coerceIn(12, 120)
    val maxTopPitch = (119 - visiblePitchRows + 1).coerceAtLeast(0)
    val currentVisibleTop = currentTopPitch.coerceIn(maxTopPitch, 119)
    val verticalScroll = rememberScrollState(((119 - currentVisibleTop) * rowHeight).roundToInt().coerceAtLeast(0))

    fun updateNotes(updatedNotes: List<com.studiowave.app.data.MidiNote>) {
        val activeTrack = track ?: return
        val activeClip = clip ?: return
        onProjectChange(project.copy(tracks = project.tracks.map { item ->
            if (item.id != activeTrack.id) item else item.copy(clips = item.clips.map { current ->
                if (current.id == activeClip.id) current.copy(midiNotes = updatedNotes) else current
            })
        }))
    }
    fun snapStep(raw: Int): Int = (raw / 1).coerceAtLeast(0)
    fun pitchLabel(pitch: Int): String {
        val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
        return names[Math.floorMod(pitch, 12)] + (pitch / 12 - 1)
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF17191C), RoundedCornerShape(7.dp)).padding(5.dp)) {
        WorkspaceTitleBar("CLIP VIEW  /  MIDI NOTE EDITOR", "${track?.name ?: "No MIDI track"}  ·  ${clip?.name ?: "No clip"}", onBack)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                .background(Color(0xFF222428), RoundedCornerShape(5.dp))
                .padding(horizontal = 5.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            SmallAction(if (keyboardVisible) "KEYS" else "VEL") { keyboardVisible = !keyboardVisible }
            SmallAction("−") { zoomX = (zoomX / 1.25f).coerceIn(0.45f, 5f) }
            SmallAction("←") { startStep = (pageStartStep - stepsOnScreen).coerceAtLeast(0) }
            Text("BAR ${pageStartStep / stepsPerBar + 1}", color = Color(0xFFE4E8EF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            SmallAction("→") { startStep = (pageStartStep + stepsOnScreen).coerceAtMost(maxStartStep) }
            SmallAction("+") { zoomX = (zoomX * 1.25f).coerceIn(0.45f, 5f) }
            SmallAction("PITCH −") { zoomY = (zoomY / 1.2f).coerceIn(0.5f, 2.6f) }
            SmallAction("PITCH +") { zoomY = (zoomY * 1.2f).coerceIn(0.5f, 2.6f) }
            SmallAction("1/${snapDenominator}") { snapDenominator = when (snapDenominator) { 16 -> 8; 8 -> 4; 4 -> 32; else -> 16 } }
            SmallAction("COPY") {
                clipboard = notes.firstOrNull { it.id == selectedNoteId }
                editMessage = if (clipboard != null) "Note copied" else "Tap a note, then COPY"
            }
            SmallAction("PASTE") {
                val source = clipboard
                if (source != null && clip != null && track != null) {
                    val pasted = source.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        startTick = source.startTick + stepTicks,
                        pitch = (source.pitch - 1).coerceIn(0, 119)
                    )
                    updateNotes(notes + pasted)
                    selectedNoteId = pasted.id
                    editMessage = "Note pasted"
                }
            }
            SmallAction("− LEN") {
                val selected = notes.firstOrNull { it.id == selectedNoteId }
                if (selected != null) {
                    updateNotes(notes.map { if (it.id == selected.id) it.copy(durationTicks = (it.durationTicks - stepTicks).coerceAtLeast(stepTicks)) else it })
                }
            }
            SmallAction("+ LEN") {
                val selected = notes.firstOrNull { it.id == selectedNoteId }
                if (selected != null) {
                    val updated = notes.map { note ->
                        if (note.id == selected.id) {
                            note.copy(durationTicks = (note.durationTicks + stepTicks).coerceAtMost(project.ppq.toLong() * 4L * 4096L))
                        } else {
                            note
                        }
                    }
                    updateNotes(updated)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(if (keyboardVisible) "SELECT / DRAW" else "VELOCITY", color = MutedText, fontSize = 7.sp, maxLines = 1)
        }

        Row(Modifier.fillMaxWidth().weight(1f).padding(top = 3.dp)) {
            if (keyboardVisible) {
                Column(Modifier.width(55.dp).fillMaxHeight().background(Color(0xFF202328))) {
                    Column(Modifier.verticalScroll(verticalScroll).height((rowHeight * 120f).dp)) {
                        for (pitch in 119 downTo 0) {
                            val isBlack = pitch % 12 in setOf(1, 3, 6, 8, 10)
                            val isC = pitch % 12 == 0
                            Box(
                                Modifier.fillMaxWidth().height(rowHeight.dp)
                                    .background(if (isBlack) Color(0xFF17191C) else Color(0xFF343840))
                                    .clickable { topPitch = (pitch + visiblePitchRows - 1).coerceIn(0, maxTopPitch) },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (isC || pitch % 12 == 4 || pitch % 12 == 7) {
                                    Text(pitchLabel(pitch), color = if (isC) Mint else Color(0xFFE4E8EF),
                                        fontSize = 7.sp, modifier = Modifier.padding(start = 3.dp), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().height(20.dp).background(Color(0xFF24262A)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("BAR / BEAT", color = MutedText, fontSize = 7.sp, modifier = Modifier.padding(start = 5.dp))
                    Spacer(Modifier.weight(1f))
                    Text("C1 — C10", color = Mint, fontSize = 7.sp, modifier = Modifier.padding(end = 6.dp))
                }
                BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF1B1D21))) {
                    val viewportWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
                    val viewportHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
                    val colWidthPx = (viewportWidthPx / stepsOnScreen.toFloat()).coerceAtLeast(5f)
                    val contentWidthPx = (clipLengthSteps.coerceAtLeast(stepsOnScreen) * colWidthPx).coerceAtMost(8_000_000f)
                    val contentHeightDp = 120f * rowHeight
                    val maxHorizontalScroll = (contentWidthPx - viewportWidthPx).coerceAtLeast(0f)
                    val currentStart = if (maxHorizontalScroll <= 0f) 0 else {
                        (pageStartStep / clipLengthSteps.toFloat() * maxHorizontalScroll).roundToInt().coerceIn(0, maxHorizontalScroll.roundToInt())
                    }
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .pointerInput(track?.id, clip?.id, notes, snapDenominator, zoomX, zoomY, currentStart, currentTopPitch, keyboardVisible) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val second = awaitPointerEvent()
                                if (second.changes.count { it.pressed } >= 2) {
                                    val positions = second.changes.filter { it.pressed }.map { it.position }
                                    val span = if (positions.size >= 2) (positions[0] - positions[1]).getDistance() else 1f
                                    var lastDistance = span.coerceAtLeast(1f)
                                    var lastMid = if (positions.size >= 2) (positions[0] + positions[1]) / 2f else down.position
                                    do {
                                        val event = awaitPointerEvent()
                                        val pts = event.changes.filter { it.pressed }
                                        if (pts.size >= 2) {
                                            val newSpan = (pts[0].position - pts[1].position).getDistance().coerceAtLeast(1f)
                                            val mid = (pts[0].position + pts[1].position) / 2f
                                            val ratio = newSpan / lastDistance
                                            if (kotlin.math.abs(ratio - 1f) > 0.015f) {
                                                zoomX = (zoomX * ratio).coerceIn(0.45f, 5f)
                                                zoomY = (zoomY * ratio).coerceIn(0.5f, 2.6f)
                                            }
                                            val delta = mid - lastMid
                                            if (kotlin.math.abs(delta.x) > 1f) {
                                                startStep = (startStep - (delta.x / colWidthPx).roundToInt()).coerceIn(0, maxStartStep)
                                            }
                                            if (kotlin.math.abs(delta.y) > 1f) {
                                                topPitch = (topPitch + (delta.y / rowHeight).roundToInt()).coerceIn(0, maxTopPitch)
                                            }
                                            lastDistance = newSpan
                                            lastMid = mid
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                } else {
                                    var previous = down.position
                                    var dragged = false
                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (change.pressed) {
                                            val delta = change.position - previous
                                            if (kotlin.math.abs(delta.x) > 2f || kotlin.math.abs(delta.y) > 2f) {
                                                dragged = true
                                                startStep = (startStep - (delta.x / colWidthPx).roundToInt()).coerceIn(0, maxStartStep)
                                                topPitch = (topPitch + (delta.y / rowHeight).roundToInt()).coerceIn(0, 119)
                                                change.consume()
                                            }
                                            previous = change.position
                                        }
                                    } while (event.changes.any { it.pressed })
                                    if (!dragged && track != null && clip != null && keyboardVisible) {
                                        val step = (startStep + (down.position.x / colWidthPx).toInt().coerceIn(0, stepsOnScreen - 1))
                                        val row = (down.position.y / rowHeight).toInt()
                                        val pitch = (currentTopPitch - row).coerceIn(0, 119)
                                        val startTick = snapStep(step) * stepTicks
                                        val hitNote = notes.firstOrNull { note ->
                                            val noteStartStep = (note.startTick / stepTicks).toInt()
                                            val noteEndStep = noteStartStep + (note.durationTicks / stepTicks).toInt().coerceAtLeast(1)
                                            note.pitch == pitch && step >= noteStartStep && step < noteEndStep
                                        }
                                        val found = hitNote ?: notes.firstOrNull { it.pitch == pitch && it.startTick == startTick }
                                        if (found != null) {
                                            selectedNoteId = found.id
                                            updateNotes(notes.filterNot { it.id == found.id })
                                            editMessage = "Note removed"
                                        } else {
                                            val created = com.studiowave.app.data.MidiNote(
                                                pitch = pitch, startTick = startTick, durationTicks = stepTicks, velocity = 100
                                            )
                                            selectedNoteId = created.id
                                            updateNotes(notes + created)
                                            editMessage = "Note added"
                                        }
                                    } else if (!dragged && !keyboardVisible && notes.isNotEmpty()) {
                                        val targetTick = (startStep + (down.position.x / colWidthPx).toInt().coerceIn(0, stepsOnScreen - 1)) * stepTicks
                                        val nearest = notes.minByOrNull { kotlin.math.abs(it.startTick - targetTick) }
                                        if (nearest != null) {
                                            selectedNoteId = nearest.id
                                            val velocity = (127f * (1f - down.position.y / viewportHeightPx.coerceAtLeast(1f))).roundToInt().coerceIn(1, 127)
                                            updateNotes(notes.map { if (it.id == nearest.id) it.copy(velocity = velocity) else it })
                                        }
                                    }
                                }
                            }
                        }
                    Box(contentModifier) {
                        Canvas(Modifier.fillMaxSize()) {
                            val localRowHeight = rowHeight
                            val localColWidth = size.width / stepsOnScreen.toFloat()
                            val rowsVisible = (size.height / localRowHeight).toInt().coerceAtLeast(1)
                            for (row in 0..rowsVisible) {
                                val pitch = currentTopPitch - row
                                val isC = pitch >= 0 && pitch % 12 == 0
                                drawLine(if (isC) Color(0xFF69717D) else Color(0xFF343840),
                                    Offset(0f, row * localRowHeight), Offset(size.width, row * localRowHeight),
                                    if (isC) 1.2f else 0.45f)
                            }
                            for (col in 0..stepsOnScreen) {
                                val stepAbsolute = pageStartStep + col
                                val bar = stepAbsolute % stepsPerBar == 0
                                val beat = stepAbsolute % stepsPerBeat == 0
                                drawLine(when { bar -> Color(0xFF89919D); beat -> Color(0xFF69717D); else -> Color(0xFF343840) },
                                    Offset(col * localColWidth, 0f), Offset(col * localColWidth, size.height),
                                    when { bar -> 1.4f; beat -> 0.9f; else -> 0.45f })
                            }
                            notes.forEach { note ->
                                val x = (note.startTick.toDouble() / stepTicks.toDouble() - pageStartStep) * localColWidth
                                val row = currentTopPitch - note.pitch
                                val y = row * localRowHeight
                                val width = (note.durationTicks.toDouble() / stepTicks.toDouble() * localColWidth).toFloat().coerceAtLeast(3f)
                                if (note.pitch in 0..119 && x < size.width && x + width > 0 && y + localRowHeight > 0 && y < size.height) {
                                    drawRoundRect(
                                        color = if (note.id == selectedNoteId) Color(0xFFFF764D) else Mint,
                                        topLeft = Offset(x.toFloat(), y + 1f),
                                        size = Size(width, (localRowHeight - 2f).coerceAtLeast(2f)),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f)
                                    )
                                    drawRect(Color.White.copy(alpha = 0.8f),
                                        Offset(x.toFloat() + width - 3f, y + 1f), Size(2f, (localRowHeight - 2f).coerceAtLeast(2f)))
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(editMessage, color = MutedText, fontSize = 8.sp)
            Spacer(Modifier.weight(1f))
            Text("H ${zoomX.format(2)}×   V ${zoomY.format(2)}×", color = Mint, fontSize = 8.sp)
        }
    }
}

@Composable
private fun Float.format(digits: Int): String = "%.${digits}f".format(java.util.Locale.US, this)

@Composable
private fun MixerWorkspace(
    project: StudioProject,
    master: Float,
    onMasterChange: (Float) -> Unit,
    onProjectChange: (StudioProject) -> Unit,
    trackMeterReadings: Map<String, Pair<Float, Float>>
) {
    Row(
        Modifier.fillMaxSize().background(Color(0xFF17191C), RoundedCornerShape(7.dp))
            .horizontalScroll(rememberScrollState()).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        project.tracks.forEach { track ->
            Column(
                Modifier.width(104.dp).fillMaxHeight()
                    .background(Color(0xFF25272B), RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 6.dp),
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
                Text("VOL  " + (track.volume * 100).toInt() + "%", color = Color(0xFFB6B9C0), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                val latestProject by rememberUpdatedState(project)
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.weight(1f).fillMaxHeight()) {
                        // Dedicated stereo meter lane, visually separate from the channel fader.
                        val peak = trackMeterReadings[track.id]?.first ?: 0f
                        val rms = trackMeterReadings[track.id]?.second ?: 0f
                        val lane = (size.width * 0.72f).coerceAtLeast(3f)
                        for (segment in 0 until 18) {
                            val segmentHeight = size.height / 18f
                            val y = size.height - (segment + 1) * segmentHeight
                            val active = segment / 18f <= peak
                            drawRoundRect(
                                color = if (!active) Color(0xFF131518) else if (segment >= 15) Color(0xFFCC5B55) else if (segment >= 11) Color(0xFFE4B45E) else Mint,
                                topLeft = Offset((size.width - lane) / 2f, y + 1f),
                                size = Size(lane, (segmentHeight - 2f).coerceAtLeast(1f)),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f)
                            )
                        }
                        drawLine(Color.White.copy(alpha = 0.85f),
                            Offset((size.width - lane) / 2f, size.height * (1f - rms)),
                            Offset((size.width + lane) / 2f, size.height * (1f - rms)), 1f)
                    }
                    Canvas(
                        Modifier.weight(1.8f).fillMaxHeight()
                            .pointerInput(track.id) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val height = size.height.coerceAtLeast(1).toFloat()
                                    val value = (1f - change.position.y / height).coerceIn(0f, 1f) * 1.5f
                                    val current = latestProject
                                    onProjectChange(current.copy(tracks = current.tracks.map {
                                        if (it.id == track.id) it.copy(volume = value) else it
                                    }))
                                }
                            }
                    ) {
                        // Touch-drag channel gain fader.

                    val railWidth = (size.width * 0.18f).coerceAtLeast(5f)
                    val railLeft = (size.width - railWidth) / 2f
                    val railTop = size.height * 0.06f
                    val railBottom = size.height * 0.94f
                    drawRoundRect(
                        Color(0xFF111315),
                        Offset(railLeft, railTop),
                        Size(railWidth, (railBottom - railTop).coerceAtLeast(1f)),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(railWidth / 2f)
                    )
                    val fraction = track.volume.coerceIn(0f, 1.5f) / 1.5f
                    val thumbY = railBottom - fraction * (railBottom - railTop)
                    drawRoundRect(
                        Mint.copy(alpha = 0.7f),
                        Offset(railLeft, thumbY),
                        Size(railWidth, (railBottom - thumbY).coerceAtLeast(0f)),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(railWidth / 2f)
                    )
                    drawRoundRect(
                        Color(0xFFE4E8EF),
                        Offset(size.width * 0.18f, thumbY - 3f),
                        Size(size.width * 0.64f, 6f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f)
                    )
                    }
                }
                Spacer(Modifier.height(3.dp))
                val panLabel = when {
                    track.pan <= -0.95f -> "L"
                    track.pan >= 0.95f -> "R"
                    else -> (track.pan * 100).toInt().toString()
                }
                Text("PAN  " + panLabel, color = Color(0xFFB6B9C0), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = track.pan.coerceIn(-1f, 1f),
                    onValueChange = { value ->
                        onProjectChange(project.copy(tracks = project.tracks.map {
                            if (it.id == track.id) it.copy(pan = value) else it
                        }))
                    },
                    valueRange = -1f..1f,
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (track.muted) "MUTED" else if (track.solo) "SOLO" else track.kind.uppercase(),
                    color = if (track.muted) Red else MutedText,
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
        }
        Column(
            Modifier.width(110.dp).fillMaxHeight()
                .background(Color(0xFF292B30), RoundedCornerShape(5.dp)).padding(6.dp),
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
    Column(Modifier.fillMaxSize().background(CanvasSurface, RoundedCornerShape(8.dp)).padding(10.dp)) {
        Text("BROWSER", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onImportAudio, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF18443F))) {
            Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Mint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("IMPORT AUDIO", fontSize = 10.sp, color = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Surface(color = Color(0xFF25282D), shape = RoundedCornerShape(7.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(10.dp)) {
                Text("LOCAL AUDIO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Mint)
                Text("Choose an audio file from Android storage. It will be placed on the audio track in Arrangement.",
                    color = Color(0xFFD5D9E0), fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
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
    onToggle: () -> Unit
) {
    val selectedTrack = project.tracks.firstOrNull { it.id == selectedTrackId } ?: project.tracks.firstOrNull()
    var expanded by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf("MIX") }
    Column(
        Modifier.fillMaxWidth().height(if (expanded) 220.dp else 42.dp)
            .background(Color(0xFF25272B), RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(27.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("DEVICE VIEW", fontSize = 10.sp, fontWeight = FontWeight.Black, color = AbletonOrange)
            Spacer(Modifier.width(8.dp))
            Text(selectedTrack?.name ?: "NO TRACK", fontSize = 9.sp, color = Color(0xFFE4E8EF), maxLines = 1)
            Spacer(Modifier.weight(1f))
            SmallAction(if (page == "MIX") "MIX" else "DEVICES") { page = if (page == "MIX") "DEVICES" else "MIX" }
            SmallAction(if (expanded) "−" else "+") { expanded = !expanded }
            SmallAction("× CLOSE") { onToggle() }
        }
        if (expanded) {
            if (project.tracks.isEmpty()) {
                Text("Tambahkan track di Arrangement untuk melihat perangkat dan kontrol channel.",
                    color = MutedText, fontSize = 9.sp)
            } else {
                Row(
                    Modifier.fillMaxWidth().height(28.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    project.tracks.forEach { track ->
                        val isSelected = selectedTrack?.id == track.id
                        Surface(
                            color = if (isSelected) Color(0xFF18443F) else Color(0xFF202328),
                            shape = RoundedCornerShape(5.dp),
                            modifier = Modifier.clickable { onSelectTrack(track.id) }
                        ) {
                            Text(track.name.take(18), color = if (isSelected) Mint else Color(0xFFD5D9E0),
                                fontSize = 9.sp, maxLines = 1,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                        }
                    }
                }
                if (selectedTrack != null && page == "MIX") {
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("VOLUME  ·  ${(selectedTrack.volume.coerceIn(0f, 1.5f) * 100).toInt()}%",
                                color = MutedText, fontSize = 9.sp)
                            Slider(
                                value = selectedTrack.volume.coerceIn(0f, 1.5f),
                                onValueChange = { value ->
                                    onProjectChange(project.copy(tracks = project.tracks.map {
                                        if (it.id == selectedTrack.id) it.copy(volume = value) else it
                                    }))
                                },
                                valueRange = 0f..1.5f,
                                modifier = Modifier.fillMaxWidth().height(30.dp)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            val pan = selectedTrack.pan.coerceIn(-1f, 1f)
                            Text("PAN  ·  " + when {
                                pan <= -0.95f -> "L"
                                pan >= 0.95f -> "R"
                                else -> (pan * 100).toInt().toString()
                            }, color = MutedText, fontSize = 9.sp)
                            Slider(
                                value = pan,
                                onValueChange = { value ->
                                    onProjectChange(project.copy(tracks = project.tracks.map {
                                        if (it.id == selectedTrack.id) it.copy(pan = value) else it
                                    }))
                                },
                                valueRange = -1f..1f,
                                modifier = Modifier.fillMaxWidth().height(30.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            SmallAction(if (selectedTrack.muted) "MUTE ✓" else "MUTE") {
                                onProjectChange(project.copy(tracks = project.tracks.map {
                                    if (it.id == selectedTrack.id) it.copy(muted = !it.muted) else it
                                }))
                            }
                            SmallAction(if (selectedTrack.solo) "SOLO ✓" else "SOLO") {
                                onProjectChange(project.copy(tracks = project.tracks.map {
                                    if (it.id == selectedTrack.id) it.copy(solo = !it.solo) else it
                                }))
                            }
                        }
                    }
                } else if (selectedTrack != null) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        val devices = when (selectedTrack.kind) {
                            "audio" -> listOf("SAMPLE", "WARP (NOT IMPLEMENTED)", "AUDIO EFFECTS")
                            "drum" -> listOf("DRUM PATTERN", "INSTRUMENT (NOT IMPLEMENTED)", "AUDIO EFFECTS")
                            else -> listOf("MIDI NOTES", "INSTRUMENT (NOT IMPLEMENTED)", "MIDI EFFECTS", "AUDIO EFFECTS")
                        }
                        devices.forEachIndexed { index, label ->
                            Column(
                                Modifier.weight(1f).fillMaxHeight()
                                    .background(Color(0xFF202328), RoundedCornerShape(5.dp)).padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(label, color = if (index == 0) Mint else MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (index == 0) "Track data available" else "Belum ada processor audio terhubung",
                                    color = Color(0xFFD5D9E0), fontSize = 8.sp
                                )
                            }
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
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(40.dp).defaultMinSize(minWidth = 44.dp)
            .clickable(onClickLabel = description, onClick = onClick)
    ) {
        Box(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
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
    val description = when (icon) {
        Icons.Default.Stop -> "Stop playback"
        Icons.Default.FolderOpen -> "Open project"
        Icons.Default.Save -> "Save project"
        Icons.Default.Undo -> "Undo"
        Icons.Default.Redo -> "Redo"
        else -> "Action"
    }
    Button(onClick = onClick, modifier = Modifier.size(32.dp), contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(5.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF353940), contentColor = tint)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(16.dp))
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
