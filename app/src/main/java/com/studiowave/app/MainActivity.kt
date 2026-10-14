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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.roundToLong
import org.json.JSONObject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

private val Room: Color @Composable get() = LocalStudioWavePalette.current.room
private val CanvasSurface: Color @Composable get() = LocalStudioWavePalette.current.canvas
private val Raised: Color @Composable get() = LocalStudioWavePalette.current.panel
private val MutedText: Color @Composable get() = LocalStudioWavePalette.current.muted
private val Mint: Color @Composable get() = LocalStudioWavePalette.current.accent
private val AbletonOrange: Color @Composable get() = LocalStudioWavePalette.current.orange
private val Green: Color @Composable get() = LocalStudioWavePalette.current.success
private val Red: Color @Composable get() = LocalStudioWavePalette.current.danger

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
/**
 * Shared Piano Roll viewport mapping. Every toolbar, pinch, grid and scroll calculation
 * must use the same rounding rule so zoom cannot change the visible step count by one.
 */
internal fun pianoRollVisibleSteps(stepsPerBar: Int, zoomX: Float): Int {
    val safeBarSteps = stepsPerBar.coerceAtLeast(1)
    val safeZoom = zoomX.coerceIn(0.35f, 8f)
    return (safeBarSteps.toFloat() * 2f / safeZoom).roundToInt().coerceIn(4, 96)
}

/**
 * Keep note events inside a clip's relative musical duration.
 * Notes fully beyond the new end are removed; crossing notes are shortened.
 */
/** Clamp Piano Roll's top pitch so every row fits the 0..127 MIDI range. */
internal fun clampPianoRollTopPitch(topPitch: Int, visiblePitchRows: Int): Int {
    val visibleRows = visiblePitchRows.coerceIn(1, 128)
    return topPitch.coerceIn(visibleRows - 1, 127)
}

internal fun pianoRollPitchScrollIndex(topPitch: Int): Int =
    127 - topPitch.coerceIn(0, 127)

/**
 * Keep note events inside a clip's relative musical duration.
 * Notes fully beyond the new end are removed; crossing notes are shortened.
 */
internal fun fitMidiNotesToClip(
    notes: List<com.studiowave.app.data.MidiNote>,
    clipLengthTicks: Long
): List<com.studiowave.app.data.MidiNote> {
    val safeLength = clipLengthTicks.coerceAtLeast(1L)
    return notes.mapNotNull { note ->
        val start = note.startTick.coerceAtLeast(0L)
        if (start >= safeLength) {
            null
        } else {
            val duration = note.durationTicks.coerceAtLeast(1L)
                .coerceAtMost(safeLength - start)
            note.copy(startTick = start, durationTicks = duration)
        }
    }
}

/**
 * Apply a horizontal/vertical Piano Roll gesture to an existing MIDI note.
 * Timeline positions are clip-relative ticks; resizing always keeps a note inside the clip.
 */
internal fun transformMidiNoteFromGesture(
    note: com.studiowave.app.data.MidiNote,
    clipLengthTicks: Long,
    stepTicks: Long,
    deltaSteps: Int,
    deltaPitch: Int,
    resize: Boolean
): com.studiowave.app.data.MidiNote {
    val safeStep = stepTicks.coerceAtLeast(1L)
    val safeClipLength = clipLengthTicks.coerceAtLeast(safeStep)
    val originalDuration = note.durationTicks.coerceIn(safeStep, safeClipLength)
    val safeDeltaSteps = deltaSteps.coerceIn(-100_000, 100_000)
    if (resize) {
        val latestAllowedStart = (safeClipLength - safeStep).coerceAtLeast(0L)
        val start = note.startTick.coerceIn(0L, latestAllowedStart)
        val maxDuration = (safeClipLength - start).coerceAtLeast(safeStep)
        val requested = originalDuration.toDouble() + safeDeltaSteps.toDouble() * safeStep.toDouble()
        val duration = requested.coerceIn(safeStep.toDouble(), maxDuration.toDouble()).toLong()
        return note.copy(startTick = start, durationTicks = duration.coerceIn(safeStep, maxDuration))
    }
    val maxStart = (safeClipLength - originalDuration).coerceAtLeast(0L)
    val start = note.startTick.coerceIn(0L, maxStart)
    val rawStart = (start.toDouble() + safeDeltaSteps.toDouble() * safeStep.toDouble())
        .coerceIn(0.0, maxStart.toDouble()).toLong()
    val quotient = (rawStart.toDouble() / safeStep.toDouble()).roundToLong()
    val snappedStart = (quotient * safeStep).coerceIn(0L, maxStart)
    return note.copy(
        startTick = snappedStart,
        durationTicks = originalDuration,
        pitch = (note.pitch.coerceIn(0, 127) + deltaPitch.coerceIn(-127, 127)).coerceIn(0, 127)
    )
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
    var trackMeterReadings by remember {
        mutableStateOf<Map<String, ArrangementPlaybackEngine.TrackMeterSnapshot>>(emptyMap())
    }
    var selectedWorkspace by remember { mutableStateOf("ARRANGEMENT") }
    var showSettings by remember { mutableStateOf(false) }
    var settingsSection by remember { mutableStateOf("INTERFACE") }
    var themeName by remember {
        mutableStateOf(
            if (preferences.getBoolean("ui_theme_selected_manually", false)) {
                preferences.getString("ui_theme", "Live Light") ?: "Live Light"
            } else {
                // Migrate sessions that previously saved the old default dark palette to the requested light workspace.
                "Live Light"
            }
        )
    }
    var paletteTone by remember { mutableStateOf(preferences.getString("ui_tone", "Neutral") ?: "Neutral") }
    var highContrast by remember { mutableStateOf(preferences.getBoolean("ui_high_contrast", false)) }
    var gridOpacity by remember { mutableFloatStateOf(preferences.getFloat("ui_grid_opacity", 0.72f)) }
    var uiScale by remember { mutableFloatStateOf(preferences.getFloat("ui_scale", 1f).coerceIn(0.6f, 1.2f)) }
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
    var editorClipId by remember { mutableStateOf<String?>(null) }
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
                    editorClipId = null
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
                track.id to arrangementPlayback.trackMeterSnapshot(track.id)
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

    val swatch = studioWavePalette(themeName, paletteTone, highContrast)
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(baseDensity.density * uiScale, baseDensity.fontScale)
    SideEffect {
        preferences.edit()
            .putString("ui_theme", themeName)
            .putString("ui_tone", paletteTone)
            .putBoolean("ui_high_contrast", highContrast)
            .putFloat("ui_grid_opacity", gridOpacity)
            .putFloat("ui_scale", uiScale)
            .apply()
    }
    val studioColorScheme = if (themeName == "Live Light") {
        lightColorScheme(
            primary = swatch.accent, secondary = swatch.orange, background = swatch.room,
            surface = swatch.panel, onPrimary = swatch.room, onBackground = swatch.text,
            onSurface = swatch.text
        )
    } else {
        darkColorScheme(
            primary = swatch.accent, secondary = swatch.orange, background = swatch.room,
            surface = swatch.panel, onPrimary = swatch.room, onBackground = swatch.text,
            onSurface = swatch.text
        )
    }
    MaterialTheme(colorScheme = studioColorScheme) {
        CompositionLocalProvider(
            LocalStudioWavePalette provides swatch,
            LocalStudioWaveGridOpacity provides gridOpacity,
            LocalDensity provides scaledDensity
        ) {
        Surface(Modifier.fillMaxSize(), color = swatch.room) {
            if (showSettings) {
                StudioWaveSettingsScreen(
                    section = settingsSection, onSectionChange = { settingsSection = it },
                    onClose = { showSettings = false },
                    themeName = themeName, onThemeChange = {
                        themeName = it
                        preferences.edit().putBoolean("ui_theme_selected_manually", true).apply()
                    },
                    paletteTone = paletteTone, onPaletteToneChange = { paletteTone = it },
                    highContrast = highContrast, onHighContrastChange = { highContrast = it },
                    gridOpacity = gridOpacity, onGridOpacityChange = { gridOpacity = it },
                    uiScale = uiScale, onUiScaleChange = { uiScale = it.coerceIn(0.6f, 1.2f) },
                    audioStatus = "Android AudioTrack system output; custom routing and DSP device chains are not connected.",
                    interfaceLog = listOf(
                        "Theme: $themeName / $paletteTone",
                        "UI scale: ${(uiScale * 100).toInt()}%",
                        "Transport: ${if (playing) "PLAYING" else "STOPPED"}",
                        "Audio output: stereo PCM16 at 44.1 kHz / 256-frame mixer blocks",
                        "Peak: ${masterPeakDbfs.toInt()} dBFS; RMS: ${masterRmsDbfs.toInt()} dBFS",
                        "Workspace: $selectedWorkspace",
                        "Project autosave: enabled"
                    )
                )
            } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp)) {
                if (audioError != null) {
                    Surface(color = LocalStudioWavePalette.current.danger.copy(alpha = 0.22f), shape = RoundedCornerShape(5.dp), modifier = Modifier.fillMaxWidth().clickable { audioError = null }) {
                        Text(audioError!!, color = LocalStudioWavePalette.current.text, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 2)
                    }
                }
                TransportBar(
                    compact = selectedWorkspace == "ARRANGEMENT" || selectedWorkspace == "PIANO ROLL",
                    playing = playing || playbackLoading,
                    bpm = bpm,
                    master = master,
                    peakDbfs = masterPeakDbfs,
                    rmsDbfs = masterRmsDbfs,
                    momentaryLufs = masterMomentaryLufs,
                    peakHistory = masterPeakHistory,
                    positionLabel = musicalPositionLabel(playheadTick, project.ppq, project.numerator, project.denominator),
                    timeSignatureLabel = "${project.numerator}/${project.denominator}",
                    onSettings = { settingsSection = "INTERFACE"; showSettings = true },
                    onBpm = { value ->
                        bpm = value.coerceIn(40, 240)
                        changeProject(project.copy(bpm = bpm))
                    },
                    onMaster = { value ->
                        master = value
                        arrangementPlayback.setMasterGain(value)
                    },
                    onPlay = {
                        if (playing || playbackLoading) stopPlayback() else startPlaybackAt(playheadTick)
                    },
                    onStop = { stopPlayback() },
                    onUndo = { undoProject() },
                    onRedo = { redoProject() },
                    onSaveProject = {
                        pendingExportProject = project
                        exportProjectLauncher.launch(suggestedSdawFileName(project.name))
                    },
                    onOpenProject = { importProjectLauncher.launch(arrayOf("*/*")) }
                )
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
                        if (selectedWorkspace != "ARRANGEMENT" && selectedWorkspace !in setOf("PIANO ROLL", "AUDIO EDITOR", "DRUM SEQUENCER")) {
                            Row(
                                Modifier.fillMaxWidth().height(22.dp)
                                    .background(LocalStudioWavePalette.current.panel, RoundedCornerShape(5.dp))
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
                                    color = LocalStudioWavePalette.current.orange, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1
                                )
                                Spacer(Modifier.weight(1f))
                                Text(project.name.take(18), color = LocalStudioWavePalette.current.muted, fontSize = 8.sp, maxLines = 1)
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
                            project, { changeProject(it) }, onBack = { selectedWorkspace = "ARRANGEMENT" },
                            selectedClipId = editorClipId
                        )
                        "BROWSER" -> BrowserWorkspace(
                            project = project,
                            selectedTrackId = editorTrackId,
                            onSelectTrack = { editorTrackId = it },
                            onOpenEditor = { workspace, trackId, clipId ->
                                if (trackId != null) editorTrackId = trackId
                                editorClipId = clipId
                                selectedWorkspace = workspace
                            },
                            onImportAudio = { audioPicker.launch(arrayOf("audio/*")) }
                        )
                        "DRUM SEQUENCER" -> DrumSequencerWorkspace(
                            project.tracks.firstOrNull { it.id == editorTrackId }?.takeIf { it.kind == "drum" }
                                ?: project.tracks.firstOrNull { it.kind == "drum" },
                            { changeProject(it) }, project, onBack = { selectedWorkspace = "ARRANGEMENT" },
                            selectedClipId = editorClipId
                        )
                        "AUDIO EDITOR" -> AudioEditorWorkspace(
                            track = project.tracks.firstOrNull { it.id == editorTrackId }?.takeIf { it.kind == "audio" }
                                ?: project.tracks.firstOrNull { it.kind == "audio" },
                            project = project,
                            onProjectChange = { changeProject(it) },
                            onBack = { selectedWorkspace = "ARRANGEMENT" },
                            selectedClipId = editorClipId
                        )
                        else -> ArrangementWorkspace(project, { changeProject(it) }, zoom, { zoom = it.coerceIn(0.1f, 16f) }, scrollTicks, { scrollTicks = it.coerceAtLeast(0.0) }, verticalZoom, { verticalZoom = it.coerceIn(0.65f, 1.8f) }, scrollTracks, { scrollTracks = it.coerceAtLeast(0f) }, { workspace, trackId, clipId ->
                                editorTrackId = trackId
                                editorClipId = clipId
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
    }
}

@Composable
private fun TransportBar(
    compact: Boolean = false,
    playing: Boolean, bpm: Int, master: Float,
    peakDbfs: Float, rmsDbfs: Float, momentaryLufs: Float, peakHistory: List<Float>,
    positionLabel: String, timeSignatureLabel: String, onSettings: () -> Unit,
    onBpm: (Int) -> Unit, onMaster: (Float) -> Unit, onPlay: () -> Unit, onStop: () -> Unit,
    onUndo: () -> Unit, onRedo: () -> Unit,
    onSaveProject: () -> Unit, onOpenProject: () -> Unit
) {
    val p = LocalStudioWavePalette.current
    Row(
        Modifier.fillMaxWidth().height(34.dp)
            .background(p.panel, RoundedCornerShape(5.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
            Surface(
                color = Color.Transparent, shape = RoundedCornerShape(4.dp),
                modifier = Modifier.clickable(onClickLabel = "Open Settings", onClick = onSettings)
            ) {
                Text("StudioWave", fontSize = 13.sp, fontWeight = FontWeight.Black, color = p.orange,
                    maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp, vertical = 3.dp))
            }
            if (!compact) Text("LIVE", color = p.muted.copy(alpha = 0.72f), fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("BPM", color = p.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            TransportAction("−") { onBpm(bpm - 1) }
            Surface(color = p.room, shape = RoundedCornerShape(4.dp)) {
                Text(bpm.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = p.text,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp))
            }
            TransportAction("+") { onBpm(bpm + 1) }
            Surface(color = p.room, shape = RoundedCornerShape(4.dp)) {
                Text(timeSignatureLabel, fontSize = 8.sp, color = p.muted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
            }
            Spacer(Modifier.weight(1f))
            SmallIconButton(Icons.Default.FolderOpen, p.muted, onOpenProject)
            SmallIconButton(Icons.Default.Save, p.muted, onSaveProject)
            SmallIconButton(Icons.Default.Undo, p.muted, onUndo)
            SmallIconButton(Icons.Default.Redo, p.muted, onRedo)
            SmallIconButton(Icons.Default.Stop, p.muted, onStop)
            Surface(
                color = if (playing) p.accent.copy(alpha = 0.24f) else p.raised,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.size(width = 26.dp, height = 26.dp).clickable(onClick = onPlay)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause playback" else "Start playback",
                        tint = if (playing) p.accent else p.orange, modifier = Modifier.size(16.dp))
                }
            }
            Surface(color = p.room, shape = RoundedCornerShape(4.dp)) {
                Text(positionLabel, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = p.text,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp), maxLines = 1)
            }
            Text("M", color = p.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Slider(value = master, onValueChange = onMaster, modifier = Modifier.width(48.dp).height(24.dp),
                valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = p.text, activeTrackColor = p.accent, inactiveTrackColor = p.control))
            Column(Modifier.width(64.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("P ${peakDbfs.toInt()}", color = p.text, fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${momentaryLufs.toInt()} LUFS", color = p.accent, fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                    drawRect(p.control, size = size)
                    drawRect(p.accent, size = Size(size.width * (peakHistory.lastOrNull() ?: 0f).coerceIn(0f, 1f), size.height))
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
    val p = LocalStudioWavePalette.current
    Column(
        Modifier.width(48.dp).fillMaxHeight().background(p.panel, RoundedCornerShape(7.dp)).padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RailButton("ARR", selected == "ARRANGEMENT", Icons.Default.GraphicEq) { onSelect("ARRANGEMENT") }
        RailButton("EDIT", selected == "PIANO ROLL" || selected == "AUDIO EDITOR" || selected == "DRUM SEQUENCER", Icons.Default.Keyboard) {
            onSelect(when (selected) {
                "PIANO ROLL", "AUDIO EDITOR", "DRUM SEQUENCER" -> "ARRANGEMENT"
                "ARRANGEMENT" -> "PIANO ROLL"
                else -> selected
            })
        }
        RailButton("MIX", selected == "MIXER", Icons.Default.Tune) { onSelect("MIXER") }
        RailButton("DEV", devicesOpen, Icons.Default.GraphicEq, onToggleDevices)
        RailButton("FILES", selected == "BROWSER", Icons.Default.LibraryMusic) { onSelect("BROWSER") }
    }
}

@Composable
private fun RailButton(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val p = LocalStudioWavePalette.current
    Surface(
        color = if (selected) p.accent.copy(alpha = 0.18f) else p.room,
        contentColor = if (selected) p.accent else p.muted,
        shape = RoundedCornerShape(6.dp),
        border = if (selected) BorderStroke(1.dp, p.accent.copy(alpha = 0.65f)) else null,
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
    val p = LocalStudioWavePalette.current
    Row(
        Modifier.fillMaxWidth().height(29.dp).background(p.panel, RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 9.sp, color = LocalStudioWavePalette.current.orange, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text(subtitle, fontSize = 8.sp, color = p.muted, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Surface(color = p.control, shape = RoundedCornerShape(5.dp),
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
    onOpenEditor: (String, String?, String?) -> Unit,
    trackHeights: MutableMap<String, Float>,
    onPlaybackRestart: (StudioProject) -> Unit = {},
    onScratchTrack: (String, Float) -> Unit = { _, _ -> },
    playheadTick: Long = 0L,
    isPlaying: Boolean = false,
    showDevices: Boolean = false,
    onToggleDevices: () -> Unit = {},
    onSeekTick: (Long) -> Unit = {}
) {
    val arrangementPalette = LocalStudioWavePalette.current
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
        if (openEditor) onOpenEditor(if (kind == "drum") "DRUM SEQUENCER" else "PIANO ROLL", newTrack.id, starterClip?.id)
    }

    fun handleClipAction(action: String, clipId: String) {
        val trackIndex = project.tracks.indexOfFirst { track -> track.clips.any { it.id == clipId } }
        if (trackIndex < 0) { clipMenu = null; return }
        val track = project.tracks[trackIndex]
        val clip = track.clips.first { it.id == clipId }
        when (action) {
            "Open editor" -> {
                showClipView = true
                onOpenEditor(when (track.kind) { "drum" -> "DRUM SEQUENCER"; "audio" -> "AUDIO EDITOR"; else -> "PIANO ROLL" }, track.id, clip.id)
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
            val timelineWidthPx = with(density) { (maxWidth - 8.dp - 94.dp).coerceAtLeast(1.dp).toPx() }
            val baseBarWidthPx = with(density) { 74.dp.toPx() }.coerceAtLeast(1f)
            (timelineWidthPx.toDouble() * fitTimebase.ticksPerBar.toDouble() /
                (baseBarWidthPx.toDouble() * maxEndTick.toDouble()))
                .toFloat().coerceIn(0.1f, 16f)
        }
        Column(Modifier.fillMaxSize().background(LocalStudioWavePalette.current.room, RoundedCornerShape(7.dp)).padding(1.dp)) {
        Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(94.dp)) {
                Row(Modifier.fillMaxWidth().height(19.dp).background(LocalStudioWavePalette.current.raised), verticalAlignment = Alignment.CenterVertically) {
                    Text("TRACKS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = LocalStudioWavePalette.current.muted, modifier = Modifier.padding(start = 5.dp))
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
                                onDoubleTap = {
                                    val editorClip = track.clips.minByOrNull { it.startTick }
                                    onOpenEditor(when (track.kind) { "drum" -> "DRUM SEQUENCER"; "audio" -> "AUDIO EDITOR"; else -> "PIANO ROLL" }, track.id, editorClip?.id)
                                },
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
                            color = LocalStudioWavePalette.current.accent,
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
                            color = LocalStudioWavePalette.current.raised,
                            shape = RoundedCornerShape(10.dp),
                            shadowElevation = 8.dp,
                            modifier = Modifier.align(Alignment.TopEnd)
                                .offset(y = (addAnchorY - 174f).coerceAtLeast(0f).dp)
                                .padding(end = 40.dp)
                        ) {
                            Column(Modifier.width(174.dp).padding(5.dp)) {
                                Text("ADD TRACK", color = LocalStudioWavePalette.current.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
                                listOf(
                                    "MIDI / Instrument track" to "midi",
                                    "Audio track" to "audio",
                                    "Drum track" to "drum"
                                ).forEach { pair ->
                                    Text(
                                        pair.first, color = LocalStudioWavePalette.current.text, fontSize = 11.sp,
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
                        onOpenEditor("BROWSER", null, null)
                    } else {
                        val track = project.tracks.firstOrNull { item -> item.clips.any { it.id == clipId } }
                        onOpenEditor(when (track?.kind) { "drum" -> "DRUM SEQUENCER"; "audio" -> "AUDIO EDITOR"; else -> "PIANO ROLL" }, track?.id, clipId)
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
                                    midiNotes = fitMidiNotesToClip(nextNotes, safeLength),
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
                if (selectedClipModel != null) {
                    FloatingTimelineControl(if (showClipView) "CLIP ✓" else "CLIP", "Show selected clip inspector") {
                        showClipView = !showClipView
                    }
                }
                FloatingTimelineControl(if (snapEnabled) "SN" else "SN×", if (snapEnabled) "Snap on" else "Snap off") {
                    snapEnabled = !snapEnabled
                }
                FloatingTimelineControl(snapDivision.label, "Change snap division") {
                    snapDivision = snapDivision.next().let { if (it == SnapDivision.OFF) SnapDivision.BAR else it }
                }
            }
        if (showClipView && selectedClipTrack != null && selectedClipModel != null) {
            val inspectorTrack = selectedClipTrack
            val inspectorClip = selectedClipModel
            val inspectorTimebase = TimelineTimebase(project.bpm, project.numerator, project.denominator, project.ppq)
            val barNumber = inspectorClip.startTick / inspectorTimebase.ticksPerBar + 1L
            val endTick = (inspectorClip.startTick.coerceAtLeast(0L) + inspectorClip.lengthTicks.coerceAtLeast(1L))
            val endBarNumber = (endTick - 1L).coerceAtLeast(0L) / inspectorTimebase.ticksPerBar + 1L
            val typeLabel = when (inspectorTrack.kind) {
                "audio" -> "AUDIO CLIP"
                "drum" -> "DRUM CLIP"
                else -> "MIDI CLIP"
            }
            Column(
                Modifier.align(Alignment.TopEnd).padding(6.dp).width(228.dp)
                    .background(arrangementPalette.panel.copy(alpha = 0.98f), RoundedCornerShape(6.dp))
                    .border(1.dp, arrangementPalette.border, RoundedCornerShape(6.dp))
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("CLIP VIEW", color = arrangementPalette.orange, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        Text(typeLabel, color = arrangementPalette.accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    SmallAction("×") { showClipView = false }
                }
                Text(inspectorClip.name, color = arrangementPalette.text, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1)
                Text(inspectorTrack.name, color = arrangementPalette.muted, fontSize = 8.sp, maxLines = 1)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.weight(1f).background(arrangementPalette.room, RoundedCornerShape(4.dp)).padding(5.dp)) {
                        Text("START", color = arrangementPalette.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        Text("BAR $barNumber", color = arrangementPalette.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f).background(arrangementPalette.room, RoundedCornerShape(4.dp)).padding(5.dp)) {
                        Text("END", color = arrangementPalette.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        Text("BAR $endBarNumber", color = arrangementPalette.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                val isPatternClip = inspectorTrack.kind == "midi" || inspectorTrack.kind == "drum"
                val oneBarTicks = inspectorTimebase.ticksPerBar.coerceAtLeast(1L)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    SmallAction("START −") {
                        val latest = inspectorTrack.clips.firstOrNull { it.id == inspectorClip.id } ?: return@SmallAction
                        val newStart = (latest.startTick - oneBarTicks).coerceAtLeast(0L)
                        onProjectChange(project.copy(tracks = project.tracks.map { track ->
                            if (track.id == inspectorTrack.id) track.copy(clips = track.clips.map { clip ->
                                if (clip.id == inspectorClip.id) clip.copy(startTick = newStart) else clip
                            }) else track
                        }))
                    }
                    SmallAction("START +") {
                        val latest = inspectorTrack.clips.firstOrNull { it.id == inspectorClip.id } ?: return@SmallAction
                        val maxStart = (maxArrangementTick(inspectorTimebase) - latest.lengthTicks.coerceAtLeast(1L)).coerceAtLeast(0L)
                        val newStart = (latest.startTick + oneBarTicks).coerceAtMost(maxStart)
                        onProjectChange(project.copy(tracks = project.tracks.map { track ->
                            if (track.id == inspectorTrack.id) track.copy(clips = track.clips.map { clip ->
                                if (clip.id == inspectorClip.id) clip.copy(startTick = newStart) else clip
                            }) else track
                        }))
                    }
                    if (isPatternClip) {
                        SmallAction("LEN −") {
                            val latest = inspectorTrack.clips.firstOrNull { it.id == inspectorClip.id } ?: return@SmallAction
                            val maxLength = (maxArrangementTick(inspectorTimebase) - latest.startTick.coerceAtLeast(0L)).coerceAtLeast(1L)
                            val newLength = (latest.lengthTicks - oneBarTicks).coerceAtLeast(oneBarTicks).coerceAtMost(maxLength)
                            onProjectChange(project.copy(tracks = project.tracks.map { track ->
                                if (track.id == inspectorTrack.id) track.copy(clips = track.clips.map { clip ->
                                    if (clip.id == inspectorClip.id) clip.copy(
                                        lengthTicks = newLength,
                                        midiNotes = fitMidiNotesToClip(clip.midiNotes, newLength)
                                    ) else clip
                                }) else track
                            }))
                        }
                        SmallAction("LEN +") {
                            val latest = inspectorTrack.clips.firstOrNull { it.id == inspectorClip.id } ?: return@SmallAction
                            val maxLength = (maxArrangementTick(inspectorTimebase) - latest.startTick.coerceAtLeast(0L)).coerceAtLeast(1L)
                            val newLength = (latest.lengthTicks.coerceAtLeast(1L) + oneBarTicks).coerceAtMost(maxLength)
                            onProjectChange(project.copy(tracks = project.tracks.map { track ->
                                if (track.id == inspectorTrack.id) track.copy(clips = track.clips.map { clip ->
                                    if (clip.id == inspectorClip.id) clip.copy(
                                        lengthTicks = newLength,
                                        midiNotes = fitMidiNotesToClip(clip.midiNotes, newLength)
                                    ) else clip
                                }) else track
                            }))
                        }
                    }
                }
                val detailText = when (inspectorTrack.kind) {
                    "audio" -> if (inspectorClip.sourceUri != null) "SOURCE LOADED · trim offset ${inspectorClip.sourceOffsetTicks} ticks"
                        else "NO SOURCE · import audio to attach a waveform"
                    "drum" -> "${inspectorClip.drumSteps.count { it }} ACTIVE STEPS · ${inspectorClip.drumSteps.size} STEPS"
                    else -> "${inspectorClip.midiNotes.size} NOTES · ${inspectorClip.midiNotes.sumOf { it.durationTicks.coerceAtLeast(1L) }} NOTE-TICKS"
                }
                Text(detailText, color = arrangementPalette.muted, fontSize = 8.sp, maxLines = 2)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallAction("EDITOR") {
                        showClipView = false
                        onOpenEditor(
                            when (inspectorTrack.kind) { "audio" -> "AUDIO EDITOR"; "drum" -> "DRUM SEQUENCER"; else -> "PIANO ROLL" },
                            inspectorTrack.id,
                            inspectorClip.id
                        )
                    }
                    SmallAction("DUP") { handleClipAction("Duplicate", inspectorClip.id) }
                    SmallAction("NAME") {
                        renameClipId = inspectorClip.id
                        renameText = inspectorClip.name
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
                        color = LocalStudioWavePalette.current.danger.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(4.dp).clickable { statusMessage = null }
                    ) {
                        Text(feedback, color = LocalStudioWavePalette.current.text, fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), maxLines = 2)
                    }
                }
            }
            if (showTimelineMenu) Surface(color = LocalStudioWavePalette.current.raised, shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp, modifier = Modifier.padding(6.dp)) {
                Column(Modifier.width(150.dp).padding(5.dp)) {
                    Text("TIMELINE", color = LocalStudioWavePalette.current.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
                    Text("Add track", color = LocalStudioWavePalette.current.text, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable {
                        showAddTrack = true
                        showTimelineMenu = false
                        addTrackMenu = false
                        clipMenu = null
                    }.padding(9.dp))
                    Text("Paste clip", color = LocalStudioWavePalette.current.text, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable { pasteClip() }.padding(9.dp))
                    Text("Import audio", color = LocalStudioWavePalette.current.text, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable {
                        showTimelineMenu = false
                        showAddTrack = false
                        addTrackMenu = false
                        onOpenEditor("BROWSER", null, null)
                    }.padding(9.dp))
                }
            }
            if (showAddTrack && !addTrackMenu) Surface(color = LocalStudioWavePalette.current.raised, shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp, modifier = Modifier.padding(6.dp)) {
                Column(Modifier.width(155.dp).padding(5.dp)) {
                    Text("ADD TRACK", color = LocalStudioWavePalette.current.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
                    listOf("Virtual instrument / MIDI" to "midi", "Audio track" to "audio", "Drum Step Sequencer" to "drum").forEach { pair ->
                        Text(pair.first, color = LocalStudioWavePalette.current.text, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable { addTrack(pair.second, openEditor = pair.second != "audio") }.padding(9.dp))
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

/**
 * Normalize the editor's drum-step view without discarding long stored patterns.
 * A missing pattern gets one bar of editable empty steps; existing patterns retain
 * their length up to the project model's explicit safety limit.
 */
internal fun normalizeDrumPatternForEditor(
    steps: List<Boolean>,
    minimumSteps: Int = 16,
    maximumSteps: Int = StudioProject.MAX_DRUM_STEPS_PER_CLIP
): List<Boolean> {
    val safeMinimum = minimumSteps.coerceIn(1, maximumSteps.coerceAtLeast(1))
    val safeMaximum = maximumSteps.coerceAtLeast(safeMinimum)
    val length = steps.size.coerceIn(safeMinimum, safeMaximum)
    return steps.take(length) + List((length - steps.size).coerceAtLeast(0)) { false }
}

@Composable
private fun DrumSequencerWorkspace(
    track: StudioTrack?,
    onProjectChange: (StudioProject) -> Unit,
    project: StudioProject,
    onBack: () -> Unit,
    selectedClipId: String? = null
) {
    val palette = LocalStudioWavePalette.current
    val clip = track?.clips?.firstOrNull { it.id == selectedClipId } ?: track?.clips?.firstOrNull()
    val rawSteps = clip?.drumSteps ?: emptyList()
    val steps = normalizeDrumPatternForEditor(rawSteps)

    fun updateSteps(updated: List<Boolean>) {
        val activeTrack = track ?: return
        val activeClip = clip ?: return
        val normalized = normalizeDrumPatternForEditor(updated)
        onProjectChange(project.copy(tracks = project.tracks.map { item ->
            if (item.id != activeTrack.id) item else item.copy(clips = item.clips.map { current ->
                if (current.id == activeClip.id) current.copy(drumSteps = normalized) else current
            })
        }))
    }

    Column(Modifier.fillMaxSize().background(palette.room).padding(7.dp)) {
        WorkspaceTitleBar("DRUM RACK  /  STEP SEQUENCER", track?.name ?: "No drum track", onBack)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${steps.size} STEPS", color = palette.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp))
            SmallAction("CLEAR") {
                if (track != null && clip != null) updateSteps(List(steps.size) { false })
            }
            SmallAction("ALL") {
                if (track != null && clip != null) updateSteps(List(steps.size) { true })
            }
            Spacer(Modifier.weight(1f))
            Text("SCROLL FOR MORE", color = palette.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(46.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(steps) { step, isActive ->
                val isBeat = step % 4 == 0
                Surface(
                    color = if (isActive) palette.accent else if (isBeat) palette.control else palette.room,
                    shape = RoundedCornerShape(if (isBeat) 4.dp else 3.dp),
                    border = BorderStroke(1.dp, if (isActive) palette.accent else palette.border.copy(alpha = 0.8f)),
                    modifier = Modifier.width(34.dp).fillMaxHeight().clickable {
                        if (track != null && clip != null) {
                            updateSteps(steps.toMutableList().also { it[step] = !it[step] })
                        }
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            (step + 1).toString(),
                            color = if (isActive) palette.room else palette.text,
                            fontSize = 8.sp,
                            fontWeight = if (isBeat || isActive) FontWeight.Black else FontWeight.Medium
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap a step to toggle its trigger. CLEAR and ALL preserve the current pattern length.",
            color = palette.muted, fontSize = 8.sp
        )
    }
}
@Composable
private fun AudioEditorWorkspace(
    track: StudioTrack?,
    project: StudioProject,
    onProjectChange: (StudioProject) -> Unit,
    onBack: () -> Unit,
    selectedClipId: String? = null
) {
    val audioPalette = LocalStudioWavePalette.current
    val context = LocalContext.current
    val clip = track?.clips?.firstOrNull { it.id == selectedClipId }
        ?: track?.clips?.firstOrNull { it.sourceUri != null }
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

    Column(Modifier.fillMaxSize().background(audioPalette.room).padding(7.dp)) {
        WorkspaceTitleBar("CLIP VIEW  /  SAMPLE EDITOR", track?.name ?: "No audio track", onBack)
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (clip?.sourceUri != null) "AUDIO SOURCE" else "NO AUDIO CLIP", color = LocalStudioWavePalette.current.orange, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            SmallAction("RESET RANGE") {
                startFraction = 0f
                endFraction = 1f
                applyTrim(0f, 1f)
            }
        }
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("START", color = LocalStudioWavePalette.current.muted, fontSize = 7.sp)
            Slider(value = startFraction, onValueChange = { value ->
                startFraction = value.coerceIn(0f, (endFraction - 0.01f).coerceAtLeast(0f))
                applyTrim(startFraction, endFraction)
            }, modifier = Modifier.weight(1f).height(20.dp))
            Text("END", color = LocalStudioWavePalette.current.muted, fontSize = 7.sp)
            Slider(value = endFraction, onValueChange = { value ->
                endFraction = value.coerceIn((startFraction + 0.01f).coerceAtMost(1f), 1f)
                applyTrim(startFraction, endFraction)
            }, modifier = Modifier.weight(1f).height(20.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(audioPalette.canvas, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
            if (waveform.isNotEmpty()) {
                Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                    val mid = size.height / 2f
                    val dx = size.width / waveform.size.coerceAtLeast(1)
                    val trimStartX = size.width * startFraction.coerceIn(0f, 1f)
                    val trimEndX = size.width * endFraction.coerceIn(startFraction, 1f)
                    // Shade excluded source regions so the saved trim range is visible on the waveform itself.
                    if (trimStartX > 0f) {
                        drawRect(audioPalette.border.copy(alpha = 0.34f), Offset.Zero,
                            Size(trimStartX, size.height))
                    }
                    if (trimEndX < size.width) {
                        drawRect(audioPalette.border.copy(alpha = 0.34f), Offset(trimEndX, 0f),
                            Size((size.width - trimEndX).coerceAtLeast(0f), size.height))
                    }
                    waveform.forEachIndexed { i, peak ->
                        val amplitude = peak.coerceIn(0f, 1f) * size.height * 0.48f
                        val x = (i + 0.5f) * dx
                        val insideRange = x >= trimStartX && x <= trimEndX
                        val waveColor = if (insideRange) audioPalette.accent.copy(alpha = 0.96f)
                            else audioPalette.muted.copy(alpha = 0.62f)
                        drawLine(waveColor, Offset(x, mid - amplitude), Offset(x, mid + amplitude), dx.coerceIn(1f, 3f))
                    }
                    drawLine(audioPalette.orange, Offset(trimStartX, 0f), Offset(trimStartX, size.height), 1.8f)
                    drawLine(audioPalette.orange, Offset(trimEndX, 0f), Offset(trimEndX, size.height), 1.8f)
                }
            } else {
                Text(
                    when {
                        loading -> "Membaca audio…"
                        loadError != null -> loadError!!
                        else -> "Waveform tidak tersedia"
                    },
                    color = LocalStudioWavePalette.current.muted, fontSize = 10.sp, modifier = Modifier.padding(12.dp)
                )
            }
        }
        Text(
            when {
                waveform.isNotEmpty() -> "SOURCE WAVEFORM  ·  " + waveform.size + " peaks  ·  range " + (startFraction * 100).toInt() + "–" + (endFraction * 100).toInt() + "%"
                loading -> "READING SOURCE WAVEFORM…"
                else -> loadError ?: "IMPORT AN AUDIO CLIP TO BEGIN"
            },
            color = LocalStudioWavePalette.current.muted, fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp)
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
            .background(LocalStudioWavePalette.current.panel, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.width(6.dp).height(45.dp).background(accent, RoundedCornerShape(3.dp)).clickable { onColorTap() })
        Column(Modifier.weight(1f).combinedClickable(onClick = {}, onDoubleClick = onDoubleTap, onLongClick = onDoubleTap)) {
            Text(track.name, fontSize = 9.sp, color = LocalStudioWavePalette.current.text, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.kind.uppercase(), fontSize = 7.sp, color = MutedText)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("M", fontSize = 8.sp, color = if (track.muted) Red else MutedText, modifier = Modifier.clickable { onMuteToggle() })
                Text("S", fontSize = 8.sp, color = if (track.solo) Mint else MutedText, modifier = Modifier.clickable { onSoloToggle() })
                if (track.kind == "audio") {
                    Box(
                        Modifier.size(width = 24.dp, height = 19.dp)
                            .background(LocalStudioWavePalette.current.accent.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
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
                    ) { Text("DJ", color = LocalStudioWavePalette.current.accent, fontSize = 8.sp, fontWeight = FontWeight.Black) }
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
            ) { Text("⠿", color = LocalStudioWavePalette.current.text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
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
            ) { Text("↕", color = LocalStudioWavePalette.current.text, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
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
    val timelinePalette = LocalStudioWavePalette.current
    val timelineGridOpacity = LocalStudioWaveGridOpacity.current.coerceIn(0f, 1f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val baseBarWidthPx = with(density) { 74.dp.toPx() }
    // One compact time ruler; no separate overview/mini-map consumes workspace height.
    val rulerHeightPx = with(density) { 18.dp.toPx() }
    val trackHeaderWidthPx = with(density) { 94.dp.toPx() }
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
            .background(timelinePalette.room)
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
                     drawLine(timelinePalette.grid.copy(alpha = timelineGridOpacity * (if (bar % 4 == 0) 1f else 0.65f)),
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
                         if (beat > 0) drawLine(timelinePalette.grid.copy(alpha = timelineGridOpacity * 0.55f), Offset(beatX, rulerHeightPx), Offset(beatX, size.height), 0.8f)
                         if (subdivision > 0L) {
                             var tickOffset = subdivision
                             while (tickOffset < beatTicks) {
                                 val subX = canvasViewport.timeToViewportX(beatTick + tickOffset)
                                 val alpha = when (tickOffset) {
                                     beatTicks / 2L -> 0.48f
                                     beatTicks / 4L -> 0.38f
                                     else -> 0.24f
                                 }
                                 drawLine(timelinePalette.grid.copy(alpha = timelineGridOpacity * alpha),
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
                    drawLine(timelinePalette.grid.copy(alpha = timelineGridOpacity * 0.8f), Offset(0f, rowTopPx), Offset(size.width, rowTopPx), 1f)
                }
            }
        }

        // Draw ruler labels only for bars that are currently visible.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
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
                     drawCircle(color = timelinePalette.muted, radius = with(density) { 1.dp.toPx() },
                         center = Offset(x, size.height * 0.55f))
                 }
            }
            drawRect(timelinePalette.grid.copy(alpha = timelineGridOpacity), size = Size(size.width, with(density) { 2.dp.toPx() }))
            val playheadX = rulerViewport.timeToViewportX(playheadTick)
            if (playheadX >= 0f && playheadX <= size.width) {
                val markerRadius = with(density) { 3.dp.toPx() }
                drawLine(timelinePalette.danger, Offset(playheadX, 0f), Offset(playheadX, size.height), 1.6f)
                drawCircle(timelinePalette.danger, markerRadius, Offset(playheadX, markerRadius))
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(top = 18.dp)) {
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
                        val rawAccent = Color((clip.colorArgb ?: track.colorArgb).toInt())
                        // Keep user-selected track colors readable against both Live Light and Live Dark surfaces.
                        val isLightTheme = timelinePalette.room.luminance() > 0.45f
                        val accent = if (isLightTheme) rawAccent.copy(
                            red = rawAccent.red * 0.76f,
                            green = rawAccent.green * 0.76f,
                            blue = rawAccent.blue * 0.76f
                        ) else rawAccent
                        val clipInk = if (isLightTheme) Color(0xFF17191C) else Color(0xFFF4F6FA)
                        val clipFill by androidx.compose.animation.animateColorAsState(
                            targetValue = accent.copy(alpha = if (selectedClip == clip.id) 0.94f else 0.76f),
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
                            Text(
                                clip.name,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = timelinePalette.text,
                                maxLines = 1
                            )
                            if (track.kind == "drum") {
                                // Step-sequencer thumbnail: 16 equal steps, stronger beat accents, active hits clearly visible.
                                Row(
                                    Modifier.fillMaxSize().padding(top = 14.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    val visibleStepCount = clip.drumSteps.size.coerceIn(16, 128)
                                    (0 until visibleStepCount).forEach { step ->
                                        val active = clip.drumSteps.getOrNull(step) == true
                                        val isBeat = step % 4 == 0
                                        Box(
                                            Modifier.weight(1f).fillMaxHeight()
                                                .padding(top = if (active) 0.dp else 3.dp)
                                                .background(
                                                    if (active) accent.copy(alpha = if (isBeat) 1f else 0.82f)
                                                    else timelinePalette.text.copy(alpha = if (isLightTheme) 0.16f else 0.24f),
                                                    RoundedCornerShape(if (isBeat) 2.dp else 1.dp)
                                                )
                                        )
                                    }
                                }
                            } else if (track.kind == "midi") {
                                Canvas(Modifier.fillMaxSize().padding(top = 13.dp, bottom = 2.dp)) {
                                    // Miniature piano roll: pitch is vertically ordered and each
                                    // note is placed from the same clip-relative musical tick ratio.
                                    val duration = clip.lengthTicks.coerceAtLeast(1L).toDouble()
                                    val rowHeight = (size.height / 16f).coerceAtLeast(2f)
                                    val beatTicks = (project.ppq.toLong() * 4L /
                                        project.denominator.coerceAtLeast(1).toLong()).coerceAtLeast(1L)
                                    val barTicks = beatTicks * project.numerator.coerceAtLeast(1).toLong()
                                    val beatCount = (duration / beatTicks.toDouble()).toInt().coerceIn(1, 256)
                                    for (beat in 1 until beatCount) {
                                        val x = (beat.toDouble() * beatTicks.toDouble() / duration * size.width).toFloat()
                                        val isBarLine = beatTicks * beat.toLong() % barTicks == 0L
                                        drawLine(
                                            clipInk.copy(alpha = if (isLightTheme) 0.20f else 0.18f),
                                            Offset(x, 0f), Offset(x, size.height),
                                            if (isBarLine) 1.2f else 0.55f
                                        )
                                    }
                                    // Subtle octave rows make the note pattern readable at thumbnail size.
                                    for (octave in 1..7) {
                                        val y = size.height * octave / 8f
                                        drawLine(clipInk.copy(alpha = if (isLightTheme) 0.12f else 0.10f),
                                            Offset(0f, y), Offset(size.width, y), 0.45f)
                                    }
                                    clip.midiNotes.forEach { note ->
                                        val safeStart = note.startTick.coerceAtLeast(0L).toDouble()
                                        val safeDuration = note.durationTicks.coerceAtLeast(1L).toDouble()
                                        val rawLeft = (safeStart / duration * size.width).toFloat()
                                        val rawRight = ((safeStart + safeDuration) / duration * size.width).toFloat()
                                        // A clip thumbnail must never draw note tails outside its own bounds.
                                        val left = rawLeft.coerceIn(0f, size.width)
                                        val right = rawRight.coerceIn(left, size.width)
                                        val noteWidth = right - left
                                        val normalizedPitch = (note.pitch.coerceIn(0, 127).toFloat() / 127f)
                                        val y = (1f - normalizedPitch) * (size.height - rowHeight)
                                        if (rawLeft < size.width && rawRight > 0f && noteWidth > 0f) {
                                            drawRoundRect(
                                                color = accent.copy(alpha = (0.48f + note.velocity.coerceIn(1, 127) / 254f).coerceIn(0.5f, 0.98f)),
                                                topLeft = Offset(left, y),
                                                size = Size(noteWidth.coerceAtLeast(1f), rowHeight.coerceAtMost(size.height)),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f)
                                            )
                                        }
                                    }
                                }
                            } else if (track.kind == "audio") {
                                AudioClipWaveform(
                                    sourceUri = clip.sourceUri,
                                    accent = clipInk,
                                    sourceOffsetTicks = clip.sourceOffsetTicks,
                                    visibleLengthTicks = clip.lengthTicks,
                                    bpm = project.bpm,
                                    ppq = project.ppq
                                )
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
                                    Text("↔", color = LocalStudioWavePalette.current.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                                    Text("↔", color = LocalStudioWavePalette.current.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        val playheadPalette = timelinePalette
        Canvas(Modifier.fillMaxSize().padding(top = 24.dp).clipToBounds()) {
            val playheadViewport = TimelineViewport(
                timebase, viewport.effectivePixelsPerBar, scrollTicks.toDouble(), size.width, trackHeaderWidthPx
            )
            val x = playheadViewport.timeToViewportX(playheadTick)
            if (x >= 0f && x <= size.width) {
                drawLine(playheadPalette.danger, Offset(x, 0f), Offset(x, size.height), 1.5f)
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
                    .background(timelinePalette.panel, RoundedCornerShape(12.dp))
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
                                color = if (action == "Open editor") timelinePalette.accent.copy(alpha = 0.18f) else timelinePalette.room,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(29.dp).clickable {
                                    if (action == "×") onDismissClipMenu() else onClipAction(action, clipMenuClipId)
                                }
                            ) {
                                Box(Modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                    Text(label, color = if (action == "Open editor") timelinePalette.accent else timelinePalette.text,
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
    onBack: () -> Unit,
    selectedClipId: String? = null
) {
    val pianoPalette = LocalStudioWavePalette.current
    val pianoGridOpacity = LocalStudioWaveGridOpacity.current.coerceIn(0f, 1f)
    val clip = track?.clips?.firstOrNull { it.id == selectedClipId } ?: track?.clips?.firstOrNull()
    val notes = clip?.midiNotes ?: emptyList()
    var snapDenominator by remember(track?.id, clip?.id) { mutableIntStateOf(16) }
    var keyboardVisible by remember(track?.id) { mutableStateOf(true) }
    var editToolsExpanded by remember(track?.id, clip?.id) { mutableStateOf(false) }
    var virtualKeyboardVisible by remember(track?.id, clip?.id) { mutableStateOf(false) }
    var zoomX by remember(track?.id, clip?.id) { mutableFloatStateOf(1.0f) }
    var zoomY by remember(track?.id) { mutableFloatStateOf(1.0f) }
    var startStep by remember(track?.id, clip?.id) { mutableIntStateOf(0) }
    var lastViewportWidthPx by remember(track?.id, clip?.id) { mutableFloatStateOf(0f) }
    var topPitch by remember(track?.id) { mutableIntStateOf(108) } // Start near C8; all 128 standard MIDI pitches remain reachable
    var selectedNoteId by remember(track?.id, clip?.id) { mutableStateOf<String?>(null) }
    var clipboard by remember(track?.id, clip?.id) { mutableStateOf<com.studiowave.app.data.MidiNote?>(null) }
    var editMessage by remember(track?.id, clip?.id) { mutableStateOf("Pinch to zoom · drag empty grid to scroll") }

    val safePpq = project.ppq.coerceIn(24, 3840).toLong()
    val ticksPerQuarter = safePpq
    // The editor grid divisions are note values relative to a quarter note, independent
    // of the displayed meter denominator; the beat ruler itself follows the time signature.
    val stepTicks = (ticksPerQuarter * 4L / snapDenominator.toLong()).coerceAtLeast(1L)
    val beatTicks = (ticksPerQuarter * 4L / project.denominator.coerceAtLeast(1).toLong()).coerceAtLeast(1L)
    val stepsPerBeat = kotlin.math.ceil(beatTicks.toDouble() / stepTicks.toDouble()).toInt().coerceAtLeast(1)
    val ticksPerBar = (beatTicks * project.numerator.coerceAtLeast(1).toLong()).coerceAtLeast(1L)
    val stepsPerBar = kotlin.math.ceil(ticksPerBar.toDouble() / stepTicks.toDouble()).toInt().coerceAtLeast(1)
    val rowHeight = (18f * zoomY).coerceIn(10f, 44f)
    val rawLengthTicks = (clip?.lengthTicks ?: stepTicks * 32L).coerceAtLeast(stepTicks)
    // Base the visible musical span on the selected clip duration, like Arrangement zoom:
    // zoom changes pixels-per-step, not the duration represented by the viewport.
    val clipLengthSteps = kotlin.math.ceil(rawLengthTicks.toDouble() / stepTicks.toDouble())
        .coerceAtMost(1_000_000.0).toInt().coerceAtLeast(1)
    // Keep zoom controls and gestures on one shared steps-per-screen mapping.
    val stepsOnScreen = pianoRollVisibleSteps(stepsPerBar, zoomX)
    val maxStartStep = (clipLengthSteps - stepsOnScreen).coerceAtLeast(0)
    val pageStartStep = startStep.coerceIn(0, maxStartStep)
    val gestureZoomX = rememberUpdatedState(zoomX)
    val gestureZoomY = rememberUpdatedState(zoomY)
    val gestureStartStep = rememberUpdatedState(pageStartStep)
    val gestureTopPitch = rememberUpdatedState(topPitch)
    val pianoDensity = LocalDensity.current

    fun updateNotes(updatedNotes: List<com.studiowave.app.data.MidiNote>) {
        val activeTrack = track ?: return
        val activeClip = clip ?: return
        val safeNotes = fitMidiNotesToClip(updatedNotes, activeClip.lengthTicks)
        onProjectChange(project.copy(tracks = project.tracks.map { item ->
            if (item.id != activeTrack.id) item else item.copy(clips = item.clips.map { current ->
                if (current.id == activeClip.id) current.copy(midiNotes = safeNotes) else current
            })
        }))
    }
    val gestureNotes = rememberUpdatedState(notes)
    val gestureNoteWriter = rememberUpdatedState<(List<com.studiowave.app.data.MidiNote>) -> Unit> { updated ->
        updateNotes(updated)
    }
    fun snapStep(raw: Int): Int = (raw / 1).coerceAtLeast(0)
    fun pitchLabel(pitch: Int): String {
        val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
        return names[Math.floorMod(pitch, 12)] + (pitch / 12 - 1)
    }

    Column(Modifier.fillMaxSize().background(pianoPalette.room).padding(2.dp)) {
        // Clip identity and editing actions share one shallow strip so the note grid wins height.
        Row(
            Modifier.fillMaxWidth().height(28.dp)
                .background(pianoPalette.panel, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MIDI", color = pianoPalette.orange, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Text(clip?.name ?: "NO CLIP", color = pianoPalette.text, fontSize = 8.sp, maxLines = 1,
                modifier = Modifier.widthIn(max = 100.dp))
            Spacer(Modifier.weight(1f))
            SmallAction(if (keyboardVisible) "KEYS" else "KEYS OFF") { keyboardVisible = !keyboardVisible }
            SmallAction(if (virtualKeyboardVisible) "NOTE INPUT ON" else "NOTE INPUT") { virtualKeyboardVisible = !virtualKeyboardVisible }
            SmallAction(if (editToolsExpanded) "TOOLS" else "TOOLS +") { editToolsExpanded = !editToolsExpanded }
            TransportAction("−") {
                val nextZoom = (zoomX / 1.25f).coerceIn(0.35f, 8f)
                val oldVisible = pianoRollVisibleSteps(stepsPerBar, zoomX)
                val newVisible = (stepsPerBar.toFloat() * 2f / nextZoom).roundToInt().coerceIn(4, 96)
                val anchor = pageStartStep + oldVisible / 2
                zoomX = nextZoom
                startStep = (anchor - newVisible / 2).coerceIn(0, (clipLengthSteps - newVisible).coerceAtLeast(0))
            }
            Text("BAR ${pageStartStep / stepsPerBar + 1}", color = pianoPalette.accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            TransportAction("+") {
                val nextZoom = (zoomX * 1.25f).coerceIn(0.35f, 8f)
                val oldVisible = pianoRollVisibleSteps(stepsPerBar, zoomX)
                val newVisible = (stepsPerBar.toFloat() * 2f / nextZoom).roundToInt().coerceIn(4, 96)
                val anchor = pageStartStep + oldVisible / 2
                zoomX = nextZoom
                startStep = (anchor - newVisible / 2).coerceIn(0, (clipLengthSteps - newVisible).coerceAtLeast(0))
            }
            TransportAction("P−") { zoomY = (zoomY / 1.2f).coerceIn(0.45f, 3.5f) }
            TransportAction("P+") { zoomY = (zoomY * 1.2f).coerceIn(0.45f, 3.5f) }
            TransportAction("1/${snapDenominator}") {
                snapDenominator = when (snapDenominator) { 16 -> 8; 8 -> 4; 4 -> 32; else -> 16 }
            }
            SmallAction("BACK", onBack)
        }
        if (editToolsExpanded) {
            Row(
                Modifier.fillMaxWidth().height(26.dp)
                    .background(pianoPalette.panel, RoundedCornerShape(4.dp)).padding(horizontal = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallAction("COPY") {
                    clipboard = notes.firstOrNull { it.id == selectedNoteId }
                    editMessage = if (clipboard != null) "Note copied" else "Select a note first"
                }
                SmallAction("DEL") {
                    val selected = notes.firstOrNull { it.id == selectedNoteId }
                    if (selected != null) {
                        updateNotes(notes.filterNot { it.id == selected.id })
                        selectedNoteId = null
                        editMessage = "Note deleted"
                    } else editMessage = "Select a note first"
                }
                SmallAction("PASTE") {
                    val source = clipboard
                    if (source == null) {
                        editMessage = "Copy a note first"
                    } else if (clip == null || track == null) {
                        editMessage = "Create a MIDI clip first"
                    } else {
                        val requestedStart = if (source.startTick > Long.MAX_VALUE - stepTicks) Long.MAX_VALUE
                            else source.startTick.coerceAtLeast(0L) + stepTicks
                        if (requestedStart >= clip.lengthTicks.coerceAtLeast(1L)) {
                            editMessage = "No room to paste after this note"
                        } else {
                            val candidate = source.copy(
                                id = java.util.UUID.randomUUID().toString(),
                                startTick = requestedStart
                            )
                            val pasted = fitMidiNotesToClip(listOf(candidate), clip.lengthTicks).firstOrNull()
                            if (pasted == null) {
                                editMessage = "Note does not fit in this clip"
                            } else {
                                updateNotes(notes + pasted)
                                selectedNoteId = pasted.id
                                editMessage = "Note pasted"
                            }
                        }
                    }
                }
                SmallAction("− LEN") {
                    val selected = notes.firstOrNull { it.id == selectedNoteId }
                    if (selected != null) {
                        updateNotes(notes.map { if (it.id == selected.id) it.copy(durationTicks = (it.durationTicks - stepTicks).coerceAtLeast(stepTicks)) else it })
                    } else editMessage = "Select a note first"
                }
                SmallAction("+ LEN") {
                    val selected = notes.firstOrNull { it.id == selectedNoteId }
                    if (selected != null) {
                        updateNotes(notes.map { if (it.id == selected.id) it.copy(durationTicks = (it.durationTicks + stepTicks).coerceAtMost(project.ppq.toLong() * 4L * 4096L)) else it })
                    } else editMessage = "Select a note first"
                }
                Spacer(Modifier.weight(1f))
                Text(if (keyboardVisible) "NOTE EDIT" else "VELOCITY", color = pianoPalette.muted, fontSize = 7.sp, maxLines = 1)
            }
        }

        Row(Modifier.fillMaxWidth().weight(1f)) {
            if (keyboardVisible) {
                Column(Modifier.width(54.dp).fillMaxHeight().background(pianoPalette.panel)) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val keyRowHeightPx = with(pianoDensity) { rowHeight.dp.toPx() }
                        val keyVisibleRows = (constraints.maxHeight.toFloat() / keyRowHeightPx.coerceAtLeast(1f)).roundToInt().coerceIn(12, 128)
                        val keyTopPitch = clampPianoRollTopPitch(topPitch, keyVisibleRows)
                        val keyScrollPx = rememberScrollState(
                            (pianoRollPitchScrollIndex(keyTopPitch) * keyRowHeightPx).roundToInt().coerceAtLeast(0)
                        )
                        LaunchedEffect(keyTopPitch, keyRowHeightPx, constraints.maxHeight) {
                            keyScrollPx.scrollTo(
                                (pianoRollPitchScrollIndex(keyTopPitch) * keyRowHeightPx).roundToInt().coerceAtLeast(0)
                            )
                        }
                        Column(Modifier.verticalScroll(keyScrollPx)
                            .height((rowHeight * 128f).dp)) {
                            for (pitch in 127 downTo 0) {
                                val isBlack = pitch % 12 in setOf(1, 3, 6, 8, 10)
                                val isC = pitch % 12 == 0
                                Box(
                                    Modifier.fillMaxWidth().height(rowHeight.dp)
                                        .background(if (isBlack) LocalStudioWavePalette.current.room else LocalStudioWavePalette.current.control)
                                        .clickable {
                                            topPitch = clampPianoRollTopPitch(
                                                (pitch + keyVisibleRows - 1).coerceAtMost(127),
                                                keyVisibleRows
                                            )
                                        },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (isC || pitch % 12 == 4 || pitch % 12 == 7) {
                                        Text(pitchLabel(pitch), color = if (isC) pianoPalette.accent else pianoPalette.text,
                                            fontSize = 7.sp, modifier = Modifier.padding(start = 3.dp), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                BoxWithConstraints(Modifier.fillMaxSize().background(pianoPalette.canvas)) {
                    val viewportWidthPx = with(pianoDensity) { maxWidth.toPx() }
                    val viewportHeightPx = with(pianoDensity) { maxHeight.toPx() }
                    LaunchedEffect(viewportWidthPx, clipLengthSteps, stepsPerBar, zoomX) {
                        if (lastViewportWidthPx > 0f && viewportWidthPx > 0f &&
                            kotlin.math.abs(viewportWidthPx - lastViewportWidthPx) > 1f) {
                            val stepsVisible = pianoRollVisibleSteps(stepsPerBar, zoomX)
                            val anchor = pageStartStep + stepsVisible / 2
                            startStep = (anchor - stepsVisible / 2)
                                .coerceIn(0, (clipLengthSteps - stepsVisible).coerceAtLeast(0))
                        }
                        lastViewportWidthPx = viewportWidthPx
                    }
                    val gridStepsPerBar = stepsPerBar.coerceAtLeast(1)
                    val currentStepsOnScreen = pianoRollVisibleSteps(gridStepsPerBar, gestureZoomX.value)
                    val currentRowHeight = (18f * gestureZoomY.value).coerceIn(10f, 44f)
                    val rowHeightPx = with(pianoDensity) { currentRowHeight.dp.toPx() }
                    val visiblePitchRows = (viewportHeightPx / rowHeightPx.coerceAtLeast(1f)).roundToInt().coerceIn(12, 128)
                    val currentTopPitch = clampPianoRollTopPitch(gestureTopPitch.value, visiblePitchRows)
                    val colWidthPx = (viewportWidthPx / currentStepsOnScreen.toFloat()).coerceAtLeast(2f)
                    val currentStart = gestureStartStep.value.coerceIn(0, (clipLengthSteps - currentStepsOnScreen).coerceAtLeast(0))
                    val maxHorizontalScroll = (clipLengthSteps - currentStepsOnScreen).coerceAtLeast(0)
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .pointerInput(track?.id, clip?.id, snapDenominator, keyboardVisible, viewportWidthPx, viewportHeightPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val second = awaitPointerEvent()
                                if (second.changes.count { it.pressed } >= 2) {
                                    val initialPointers = second.changes.filter { it.pressed }.take(2)
                                    var initialMid = (initialPointers[0].position + initialPointers[1].position) / 2f
                                    val initialSpan = (initialPointers[0].position - initialPointers[1].position).getDistance().coerceAtLeast(1f)
                                    val baseZoomX = gestureZoomX.value.coerceIn(0.35f, 8f)
                                    val baseZoomY = gestureZoomY.value.coerceIn(0.45f, 3.5f)
                                    val baseStart = gestureStartStep.value
                                    val baseTop = gestureTopPitch.value
                                    val baseStepsOnScreen = pianoRollVisibleSteps(stepsPerBar, baseZoomX)
                                    val baseColWidth = (viewportWidthPx / baseStepsOnScreen.toFloat()).coerceAtLeast(2f)
                                    val baseRowHeight = with(pianoDensity) {
                                        (18f * baseZoomY).coerceIn(10f, 44f).dp.toPx()
                                    }.coerceAtLeast(1f)
                                    val anchorStep = baseStart + initialMid.x / baseColWidth
                                    val anchorPitch = baseTop - initialMid.y / baseRowHeight
                                    do {
                                        val event = awaitPointerEvent()
                                        val pts = event.changes.filter { it.pressed }.take(2)
                                        if (pts.size >= 2) {
                                            val span = (pts[0].position - pts[1].position).getDistance().coerceAtLeast(1f)
                                            val midpoint = (pts[0].position + pts[1].position) / 2f
                                            val scale = span / initialSpan
                                            val newZoomX = (baseZoomX * scale).coerceIn(0.35f, 8f)
                                            val newZoomY = (baseZoomY * scale).coerceIn(0.45f, 3.5f)
                                            val newSteps = (stepsPerBar.toFloat() * 2f / newZoomX).roundToInt().coerceIn(4, 96)
                                            val newColWidth = (viewportWidthPx / newSteps.toFloat()).coerceAtLeast(2f)
                                            val newRowHeight = with(pianoDensity) {
                                                (18f * newZoomY).coerceIn(10f, 44f).dp.toPx()
                                            }.coerceAtLeast(1f)
                                            val newClipSteps = kotlin.math.ceil(rawLengthTicks.toDouble() / stepTicks.toDouble())
                                                .coerceAtMost(1_000_000.0).toInt().coerceAtLeast(newSteps)
                                            val newStart = (anchorStep - midpoint.x / newColWidth).roundToInt()
                                                .coerceIn(0, (newClipSteps - newSteps).coerceAtLeast(0))
                                            val visibleRowsAfterZoom = (viewportHeightPx / newRowHeight).roundToInt().coerceIn(12, 128)
                                            val newTop = clampPianoRollTopPitch(
                                                (anchorPitch + midpoint.y / newRowHeight).roundToInt(),
                                                visibleRowsAfterZoom
                                            )
                                            if (kotlin.math.abs(newZoomX - gestureZoomX.value) > 0.0005f) zoomX = newZoomX
                                            if (kotlin.math.abs(newZoomY - gestureZoomY.value) > 0.0005f) zoomY = newZoomY
                                            if (newStart != gestureStartStep.value) startStep = newStart
                                            if (newTop != gestureTopPitch.value) topPitch = newTop
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                } else {
                                    val currentStepsForGesture = (stepsPerBar.toFloat() * 2f / gestureZoomX.value)
                                        .roundToInt().coerceIn(4, 96)
                                    val gestureColWidth = (viewportWidthPx / currentStepsForGesture.toFloat()).coerceAtLeast(2f)
                                    val gestureRowHeight = with(pianoDensity) {
                                        (18f * gestureZoomY.value).coerceIn(10f, 44f).dp.toPx()
                                    }.coerceAtLeast(1f)
                                    val visibleStartStep = gestureStartStep.value.coerceIn(
                                        0, (clipLengthSteps - currentStepsForGesture).coerceAtLeast(0)
                                    )
                                    val clickedStep = (visibleStartStep +
                                        (down.position.x / gestureColWidth).toInt().coerceIn(0, currentStepsForGesture - 1))
                                        .coerceIn(0, (clipLengthSteps - 1).coerceAtLeast(0))
                                    val clickedPitch = (currentTopPitch -
                                        (down.position.y / gestureRowHeight).toInt()).coerceIn(0, 127)
                                    val notesAtDown = gestureNotes.value
                                    val touchedNote = if (keyboardVisible) notesAtDown.firstOrNull { note ->
                                        val noteStartStep = (note.startTick / stepTicks).toInt()
                                        val noteEndStep = noteStartStep +
                                            kotlin.math.ceil(note.durationTicks.coerceAtLeast(1L).toDouble() / stepTicks.toDouble())
                                                .toInt().coerceAtLeast(1)
                                        note.pitch == clickedPitch && clickedStep >= noteStartStep && clickedStep < noteEndStep
                                    } else null
                                    val noteRightEdgePx = touchedNote?.let { note ->
                                        ((note.startTick.toDouble() + note.durationTicks.coerceAtLeast(1L).toDouble()) /
                                            stepTicks.toDouble() - visibleStartStep.toDouble()) * gestureColWidth.toDouble()
                                    }?.toFloat()
                                    val resizeNote = touchedNote != null && noteRightEdgePx != null &&
                                        noteRightEdgePx >= 0f && kotlin.math.abs(down.position.x - noteRightEdgePx) <=
                                        maxOf(with(pianoDensity) { 12.dp.toPx() }, gestureColWidth * 0.18f)
                                    var previous = down.position
                                    var finalPosition = down.position
                                    var dragged = false
                                    var horizontalRemainder = 0f
                                    var verticalRemainder = 0f
                                    var dragStart = gestureStartStep.value
                                    var dragPitch = gestureTopPitch.value
                                    var accumulatedDelta = Offset.Zero
                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        finalPosition = change.position
                                        if (change.pressed) {
                                            val delta = change.position - previous
                                            if (kotlin.math.abs(delta.x) > 1f || kotlin.math.abs(delta.y) > 1f) {
                                                dragged = true
                                                accumulatedDelta += delta
                                                // Empty grid pans; note gestures edit the note; velocity mode edits only velocity.
                                                if (keyboardVisible && touchedNote == null) {
                                                    horizontalRemainder += -delta.x / gestureColWidth
                                                    verticalRemainder += delta.y / gestureRowHeight
                                                    val stepDelta = horizontalRemainder.roundToInt()
                                                    val pitchDelta = verticalRemainder.roundToInt()
                                                    if (stepDelta != 0) {
                                                        val maxScroll = (clipLengthSteps - currentStepsForGesture).coerceAtLeast(0)
                                                        dragStart = (dragStart + stepDelta).coerceIn(0, maxScroll)
                                                        startStep = dragStart
                                                        horizontalRemainder -= stepDelta
                                                    }
                                                    if (pitchDelta != 0) {
                                                        dragPitch = (dragPitch + pitchDelta).coerceIn(0, 127)
                                                        topPitch = dragPitch
                                                        verticalRemainder -= pitchDelta
                                                    }
                                                }
                                                change.consume()
                                            }
                                            previous = change.position
                                        }
                                    } while (event.changes.any { it.pressed })

                                    val latestNotes = gestureNotes.value
                                    if (touchedNote != null && keyboardVisible && track != null && clip != null) {
                                        selectedNoteId = touchedNote.id
                                        if (dragged) {
                                            val stepDelta = (accumulatedDelta.x / gestureColWidth).roundToInt().coerceIn(-100_000, 100_000)
                                            val pitchDelta = (-accumulatedDelta.y / gestureRowHeight).roundToInt().coerceIn(-127, 127)
                                            val updatedNote = transformMidiNoteFromGesture(
                                                note = touchedNote,
                                                clipLengthTicks = clip.lengthTicks,
                                                stepTicks = stepTicks,
                                                deltaSteps = stepDelta,
                                                deltaPitch = pitchDelta,
                                                resize = resizeNote
                                            )
                                            gestureNoteWriter.value(latestNotes.map { note ->
                                                if (note.id == touchedNote.id) updatedNote else note
                                            })
                                            editMessage = if (resizeNote) "Note length changed" else "Note moved"
                                        } else {
                                            editMessage = "Note selected"
                                        }
                                    } else if (!keyboardVisible && track != null && clip != null) {
                                        val targetTick = clickedStep.toDouble() * stepTicks.toDouble()
                                        val nearest = latestNotes.minByOrNull { note ->
                                            kotlin.math.abs(note.startTick.toDouble() - targetTick)
                                        }
                                        if (nearest != null) {
                                            selectedNoteId = nearest.id
                                            val velocity = (127f * (1f - finalPosition.y /
                                                viewportHeightPx.coerceAtLeast(1f))).roundToInt().coerceIn(1, 127)
                                            gestureNoteWriter.value(latestNotes.map { note ->
                                                if (note.id == nearest.id) note.copy(velocity = velocity) else note
                                            })
                                            editMessage = "Velocity $velocity"
                                        } else {
                                            editMessage = "Add notes before editing velocity"
                                        }
                                    } else if (!dragged && track != null && clip != null && keyboardVisible) {
                                        val existing = latestNotes.firstOrNull { note ->
                                            note.pitch == clickedPitch &&
                                                (note.startTick / stepTicks).toInt() == clickedStep
                                        }
                                        if (existing != null) {
                                            selectedNoteId = existing.id
                                            editMessage = "Note selected"
                                        } else {
                                            val created = com.studiowave.app.data.MidiNote(
                                                pitch = clickedPitch,
                                                startTick = snapStep(clickedStep).toLong() * stepTicks,
                                                durationTicks = stepTicks,
                                                velocity = 100
                                            )
                                            gestureNoteWriter.value(latestNotes + created)
                                            selectedNoteId = created.id
                                            editMessage = "Note added"
                                        }
                                    }                                }
                            }
                        }
                    Box(contentModifier) {
                        Canvas(Modifier.fillMaxSize()) {
                            val localRowHeight = rowHeightPx
                            val localColWidth = size.width / currentStepsOnScreen.toFloat()
                            val rowsVisible = (size.height / localRowHeight).toInt().coerceAtLeast(1)
                            for (row in 0..rowsVisible) {
                                val pitch = currentTopPitch - row
                                val isC = pitch >= 0 && pitch % 12 == 0
                                drawLine(if (isC) pianoPalette.border.copy(alpha = pianoGridOpacity) else pianoPalette.grid.copy(alpha = pianoGridOpacity),
                                    Offset(0f, row * localRowHeight), Offset(size.width, row * localRowHeight),
                                    if (isC) 1.2f else 0.45f)
                            }
                            for (col in 0..currentStepsOnScreen) {
                                val stepAbsolute = pageStartStep + col
                                val bar = stepAbsolute % stepsPerBar == 0
                                val beat = stepAbsolute % stepsPerBeat == 0
                                drawLine(when { bar -> pianoPalette.border.copy(alpha = pianoGridOpacity); beat -> pianoPalette.grid.copy(alpha = pianoGridOpacity); else -> pianoPalette.grid.copy(alpha = pianoGridOpacity * 0.65f) },
                                    Offset(col * localColWidth, 0f), Offset(col * localColWidth, size.height),
                                    when { bar -> 1.4f; beat -> 0.9f; else -> 0.45f })
                            }
                            notes.forEach { note ->
                                val x = (note.startTick.toDouble() / stepTicks.toDouble() - pageStartStep) * localColWidth
                                val row = currentTopPitch - note.pitch
                                val y = row * localRowHeight
                                val width = (note.durationTicks.toDouble() / stepTicks.toDouble() * localColWidth).toFloat().coerceAtLeast(3f)
                                if (note.pitch in 0..127 && x < size.width && x + width > 0 && y + localRowHeight > 0 && y < size.height) {
                                    drawRoundRect(
                                        color = if (note.id == selectedNoteId) pianoPalette.orange else pianoPalette.accent,
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
        if (virtualKeyboardVisible) {
            val keyboardNotes = listOf(
                "C" to 48, "D" to 50, "E" to 52, "F" to 53,
                "G" to 55, "A" to 57, "B" to 59, "C5" to 60
            )
            Row(
                Modifier.fillMaxWidth().height(44.dp).background(pianoPalette.panel, RoundedCornerShape(4.dp)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                keyboardNotes.forEach { (label, pitch) ->
                    Surface(
                        color = pianoPalette.control,
                        shape = RoundedCornerShape(3.dp),
                        border = BorderStroke(1.dp, pianoPalette.border),
                        modifier = Modifier.weight(1f).fillMaxHeight().clickable {
                            val activeTrack = track
                            val activeClip = clip
                            if (activeTrack != null && activeClip != null) {
                                val noteStartTick = (pageStartStep.toLong() * stepTicks)
                                    .coerceIn(0L, (activeClip.lengthTicks - 1L).coerceAtLeast(0L))
                                val noteDuration = stepTicks.coerceAtMost(
                                    (activeClip.lengthTicks - noteStartTick).coerceAtLeast(1L)
                                ).coerceAtLeast(1L)
                                val note = com.studiowave.app.data.MidiNote(
                                    pitch = pitch, startTick = noteStartTick,
                                    durationTicks = noteDuration, velocity = 100
                                )
                                updateNotes(notes + note)
                                selectedNoteId = note.id
                                editMessage = "Added ${label} to clip"
                            } else {
                                editMessage = "Create a MIDI track and clip first"
                            }
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(label, color = pianoPalette.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                SmallAction("HIDE") { virtualKeyboardVisible = false }
            }
        }
        Row(Modifier.fillMaxWidth().height(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(editMessage, color = pianoPalette.muted, fontSize = 7.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text("H ${zoomX.format(2)}×  V ${zoomY.format(2)}×", color = pianoPalette.accent, fontSize = 7.sp)
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
    trackMeterReadings: Map<String, ArrangementPlaybackEngine.TrackMeterSnapshot>
) {
    val mixerPalette = LocalStudioWavePalette.current
    BoxWithConstraints(
        Modifier.fillMaxSize().background(mixerPalette.room, RoundedCornerShape(7.dp)).padding(4.dp)
    ) {
        val trackCount = project.tracks.size.coerceAtLeast(1)
        val availableWidth = (maxWidth - 100.dp).coerceAtLeast(1.dp)
        val gapWidthDp = ((trackCount - 1).coerceAtLeast(0) * 5).toFloat()
        val preferredChannelWidth = ((availableWidth - gapWidthDp.dp) / trackCount.toFloat())
            .coerceIn(96.dp, 160.dp)
        val channelWidth = preferredChannelWidth
        Row(
            Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
        project.tracks.forEach { track ->
            Column(
                Modifier.width(channelWidth).fillMaxHeight()
                    .background(mixerPalette.panel, RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(track.name, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = mixerPalette.text)
                Spacer(Modifier.height(3.dp))
                val volumeDbLabel = if (track.volume <= 0.001f) "−∞ dB" else
                    String.format(java.util.Locale.US, "%.1f dB", 20f * kotlin.math.log10(track.volume))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("VOL", color = mixerPalette.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    Text(volumeDbLabel, color = mixerPalette.text, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    MixerStateButton("M", track.muted, mixerPalette) {
                        onProjectChange(project.copy(tracks = project.tracks.map {
                            if (it.id == track.id) it.copy(muted = !it.muted) else it
                        }))
                    }
                    MixerStateButton("S", track.solo, mixerPalette) {
                        onProjectChange(project.copy(tracks = project.tracks.map {
                            if (it.id == track.id) it.copy(solo = !it.solo) else it
                        }))
                    }
                }
                Spacer(Modifier.height(4.dp))
                val latestProject by rememberUpdatedState(project)
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Narrow, discrete stereo meter with clear peak and RMS indicators.
                    Row(
                        Modifier.weight(0.72f).fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(2) { channel ->
                            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                                val meter = trackMeterReadings[track.id]
                                val channelPeak = (if (channel == 0) meter?.peakLeft else meter?.peakRight)
                                    ?.coerceIn(0f, 1f) ?: 0f
                                val channelRms = (if (channel == 0) meter?.rmsLeft else meter?.rmsRight)
                                    ?.coerceIn(0f, 1f) ?: 0f
                                val lane = (size.width * 0.76f).coerceAtLeast(2f)
                                for (segment in 0 until 24) {
                                    val segmentHeight = size.height / 24f
                                    val y = size.height - (segment + 1) * segmentHeight
                                    val active = (segment + 1) / 24f <= channelPeak
                                    val color = when {
                                        !active -> mixerPalette.room
                                        segment >= 21 -> mixerPalette.danger
                                        segment >= 17 -> mixerPalette.warning
                                        else -> mixerPalette.accent
                                    }
                                    drawRoundRect(color, Offset((size.width - lane) / 2f, y + 0.5f),
                                        Size(lane, (segmentHeight - 1f).coerceAtLeast(1f)),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(lane / 3f))
                                }
                                drawLine(mixerPalette.text.copy(alpha = 0.84f),
                                    Offset((size.width - lane) / 2f, size.height * (1f - channelRms).coerceIn(0f, 1f)),
                                    Offset((size.width + lane) / 2f, size.height * (1f - channelRms).coerceIn(0f, 1f)), 1f)
                            }
                        }
                    }
                    // A clear vertical mixer fader with a wide touch target, ticks, and an Ableton-like cap.
                    Canvas(
                        Modifier.weight(1.6f).fillMaxHeight()
                            .pointerInput(track.id) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        val height = size.height.coerceAtLeast(1).toFloat()
                                        val value = (1f - start.y / height).coerceIn(0f, 1f) * 1.5f
                                        val current = latestProject
                                        onProjectChange(current.copy(tracks = current.tracks.map {
                                            if (it.id == track.id) it.copy(volume = value) else it
                                        }))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val height = size.height.coerceAtLeast(1).toFloat()
                                        val value = (1f - change.position.y / height).coerceIn(0f, 1f) * 1.5f
                                        val current = latestProject
                                        onProjectChange(current.copy(tracks = current.tracks.map {
                                            if (it.id == track.id) it.copy(volume = value) else it
                                        }))
                                    }
                                )
                            }
                            .pointerInput(track.id) {
                                detectTapGestures(onTap = { position ->
                                    val height = size.height.coerceAtLeast(1).toFloat()
                                    val value = (1f - position.y / height).coerceIn(0f, 1f) * 1.5f
                                    val current = latestProject
                                    onProjectChange(current.copy(tracks = current.tracks.map {
                                        if (it.id == track.id) it.copy(volume = value) else it
                                    }))
                                })
                            }
                    ) {
                        val railWidth = (size.width * 0.18f).coerceAtLeast(5f)
                        val railLeft = (size.width - railWidth) / 2f
                        val railTop = size.height * 0.045f
                        val railBottom = size.height * 0.955f
                        drawRoundRect(mixerPalette.room, Offset(railLeft, railTop),
                            Size(railWidth, (railBottom - railTop).coerceAtLeast(1f)),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(railWidth / 2f))
                        for (tick in 0..12) {
                            val y = railTop + (railBottom - railTop) * tick / 12f
                            drawLine(mixerPalette.grid.copy(alpha = 0.72f),
                                Offset(railLeft - 5f, y), Offset(railLeft - 1f, y), 1f)
                        }
                        val fraction = track.volume.coerceIn(0f, 1.5f) / 1.5f
                        val thumbY = railBottom - fraction * (railBottom - railTop)
                        val capLeft = size.width * 0.08f
                        val capWidth = size.width * 0.84f
                        drawRoundRect(mixerPalette.control, Offset(capLeft, thumbY - 9f),
                            Size(capWidth, 18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f))
                        drawRoundRect(mixerPalette.border, Offset(capLeft, thumbY - 9f),
                            Size(capWidth, 18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
                        drawLine(mixerPalette.text, Offset(capLeft + 3f, thumbY),
                            Offset(capLeft + capWidth - 3f, thumbY), 2f)
                    }
                }
                Spacer(Modifier.height(3.dp))
                val panLabel = when {
                    track.pan <= -0.95f -> "L"
                    track.pan >= 0.95f -> "R"
                    else -> (track.pan * 100).toInt().toString()
                }
                Text("PAN  " + panLabel, color = mixerPalette.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                StudioWavePanKnob(
                    value = track.pan.coerceIn(-1f, 1f),
                    palette = mixerPalette,
                    onValueChange = { value ->
                        onProjectChange(project.copy(tracks = project.tracks.map {
                            if (it.id == track.id) it.copy(pan = value) else it
                        }))
                    },
                    modifier = Modifier.size(54.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (track.muted) "MUTED" else if (track.solo) "SOLO" else track.kind.uppercase(),
                    color = if (track.muted) mixerPalette.danger else if (track.solo) mixerPalette.accent else mixerPalette.muted,
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
        }
        Column(
            Modifier.width(90.dp).fillMaxHeight()
                .background(mixerPalette.panel, RoundedCornerShape(5.dp)).padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("MASTER", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = mixerPalette.text)
            val latestMasterChange by rememberUpdatedState(onMasterChange)
            Canvas(
                Modifier.fillMaxWidth().weight(1f)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val height = size.height.coerceAtLeast(1).toFloat()
                                latestMasterChange((1f - start.y / height).coerceIn(0f, 1f))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val height = size.height.coerceAtLeast(1).toFloat()
                                latestMasterChange((1f - change.position.y / height).coerceIn(0f, 1f))
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { offset ->
                            val height = size.height.coerceAtLeast(1).toFloat()
                            latestMasterChange((1f - offset.y / height).coerceIn(0f, 1f))
                        })
                    }
            ) {
                val railWidth = size.width * 0.24f
                val railLeft = (size.width - railWidth) / 2f
                drawRoundRect(mixerPalette.room, Offset(railLeft, 0f), Size(railWidth, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                for (tick in 0..12) {
                    val y = size.height * tick / 12f
                    drawLine(mixerPalette.grid.copy(alpha = 0.68f),
                        Offset(railLeft - 5f, y), Offset(railLeft - 1f, y), 1f)
                }
                val gain = master.coerceIn(0f, 1f)
                val thumbY = size.height * (1f - gain)
                val capLeft = size.width * 0.08f
                val capWidth = size.width * 0.84f
                drawRoundRect(mixerPalette.control, Offset(capLeft, thumbY - 9f),
                    Size(capWidth, 18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f))
                drawRoundRect(mixerPalette.border, Offset(capLeft, thumbY - 9f),
                    Size(capWidth, 18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
                drawLine(mixerPalette.text, Offset(capLeft + 3f, thumbY),
                    Offset(capLeft + capWidth - 3f, thumbY), 2f)
            }
            val masterDbLabel = if (master <= 0.001f) "-∞ dB" else
                String.format(java.util.Locale.US, "%.1f dB", 20f * kotlin.math.log10(master))
            Text("${(master * 100).toInt()}%  $masterDbLabel", color = mixerPalette.muted, fontSize = 8.sp, maxLines = 1)
        }
        }
    }
}

@Composable
private fun BrowserWorkspace(
    project: StudioProject,
    selectedTrackId: String?,
    onSelectTrack: (String) -> Unit,
    onOpenEditor: (String, String?, String?) -> Unit,
    onImportAudio: () -> Unit
) {
    val p = LocalStudioWavePalette.current
    var category by remember { mutableStateOf("ALL") }
    var query by remember { mutableStateOf("") }
    val categories = listOf("ALL", "SAMPLES", "CLIPS", "TRACKS", "DEVICES")
    val normalizedQuery = query.trim().lowercase()
    val tracks = project.tracks.filter { track ->
        (normalizedQuery.isEmpty() || track.name.lowercase().contains(normalizedQuery) ||
            track.kind.lowercase().contains(normalizedQuery) ||
            track.clips.any { it.name.lowercase().contains(normalizedQuery) })
    }
    val clipEntries = project.tracks.flatMap { track -> track.clips.map { track to it } }
        .filter { (track, clip) ->
            val matchesCategory = when (category) {
                "SAMPLES" -> track.kind == "audio" && clip.sourceUri != null
                "CLIPS" -> true
                else -> true
            }
            matchesCategory && (normalizedQuery.isEmpty() ||
                clip.name.lowercase().contains(normalizedQuery) ||
                track.name.lowercase().contains(normalizedQuery))
        }
    val visibleClipEntries = when (category) {
        "SAMPLES" -> clipEntries.filter { (track, clip) -> track.kind == "audio" && clip.sourceUri != null }
        "CLIPS" -> clipEntries
        else -> clipEntries
    }
    Column(Modifier.fillMaxSize().background(p.room).padding(5.dp)) {
        Row(
            Modifier.fillMaxWidth().height(30.dp).background(p.panel, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BROWSER", color = p.orange, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text("LIBRARY", color = p.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${project.tracks.size} TRACKS  ·  ${project.tracks.sumOf { it.clips.size }} CLIPS",
                color = p.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().height(47.dp),
            singleLine = true,
            placeholder = { Text("Search current project…", fontSize = 10.sp) },
            leadingIcon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Search project", modifier = Modifier.size(15.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = p.accent, unfocusedBorderColor = p.border,
                focusedTextColor = p.text, unfocusedTextColor = p.text
            )
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            categories.forEach { item ->
                val active = category == item
                Surface(
                    color = if (active) p.accent.copy(alpha = 0.18f) else p.panel,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, if (active) p.accent else p.border),
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { category = item }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(item, color = if (active) p.accent else p.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        when (category) {
            "ALL", "SAMPLES" -> Row(Modifier.fillMaxWidth().height(31.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onImportAudio, modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent.copy(alpha = 0.18f), contentColor = p.accent),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp)); Text("IMPORT AUDIO", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onOpenEditor("ARRANGEMENT", null, null) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.control, contentColor = p.text),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("OPEN ARRANGEMENT", fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            }
            "CLIPS" -> Row(Modifier.fillMaxWidth().height(31.dp), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { onOpenEditor("ARRANGEMENT", null, null) },
                    modifier = Modifier.fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.control, contentColor = p.text),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("OPEN ARRANGEMENT", fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            }
            "DEVICES" -> Surface(color = p.panel, shape = RoundedCornerShape(5.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("DEVICE LIBRARY", color = p.accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    Text("No built-in instrument or FX processor registry is connected yet. This view intentionally does not offer fake device entries.",
                        color = p.muted, fontSize = 9.sp)
                }
            }
            else -> {}
        }
        Spacer(Modifier.height(3.dp))
        when (category) {
            "TRACKS" -> {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    tracks.forEach { track ->
                        val selected = track.id == selectedTrackId
                        Row(
                            Modifier.fillMaxWidth().background(if (selected) p.raised else p.panel, RoundedCornerShape(4.dp))
                                .clickable { onSelectTrack(track.id) }.padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(3.dp).height(25.dp).background(Color(track.colorArgb.toInt()), RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.name, color = p.text, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("${track.kind.uppercase()}  ·  ${track.clips.size} CLIPS", color = p.muted, fontSize = 7.sp)
                            }
                            SmallAction("SELECT") { onSelectTrack(track.id) }
                            SmallAction("EDIT") {
                                onSelectTrack(track.id)
                                onOpenEditor(
                                    when (track.kind) { "audio" -> "AUDIO EDITOR"; "drum" -> "DRUM SEQUENCER"; else -> "PIANO ROLL" },
                                    track.id,
                                    track.clips.minByOrNull { it.startTick }?.id
                                )
                            }
                        }
                    }
                    if (tracks.isEmpty()) {
                        Text("No tracks match this search.", color = p.muted, fontSize = 9.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }
            "DEVICES" -> {}
            else -> {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    visibleClipEntries.forEach { (track, clip) ->
                        Row(
                            Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(3.dp).height(31.dp).background(Color((clip.colorArgb ?: track.colorArgb).toInt()), RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(clip.name, color = p.text, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("${track.name}  ·  ${track.kind.uppercase()}  ·  ${clip.lengthTicks} TICKS",
                                    color = p.muted, fontSize = 7.sp, maxLines = 1)
                            }
                            SmallAction("OPEN") {
                                onSelectTrack(track.id)
                                onOpenEditor(
                                    when (track.kind) { "audio" -> "AUDIO EDITOR"; "drum" -> "DRUM SEQUENCER"; else -> "PIANO ROLL" },
                                    track.id,
                                    clip.id
                                )
                            }
                        }
                    }
                    if (visibleClipEntries.isEmpty()) {
                        Text(
                            when (category) {
                                "SAMPLES" -> "No imported audio sources match this search."
                                "CLIPS" -> "No clips match this search."
                                else -> "No matching project items."
                            },
                            color = p.muted, fontSize = 9.sp, modifier = Modifier.padding(8.dp)
                        )
                    }
                }
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
    val devicePalette = LocalStudioWavePalette.current
    var expanded by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf("MIX") }
    val gainDb = selectedTrack?.volume?.takeIf { it > 0.001f }?.let {
        String.format(java.util.Locale.US, "%.1f dB", 20f * kotlin.math.log10(it))
    } ?: "−∞ dB"
    Column(
        Modifier.fillMaxWidth().height(if (expanded) 220.dp else 42.dp)
            .background(devicePalette.panel, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(27.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (page == "MIX") "TRACK CONTROLS" else "DEVICE VIEW", fontSize = 9.sp,
                fontWeight = FontWeight.Black, color = devicePalette.orange)
            Spacer(Modifier.width(8.dp))
            Text(selectedTrack?.name ?: "NO TRACK", fontSize = 9.sp, color = devicePalette.text, maxLines = 1)
            Spacer(Modifier.weight(1f))
            SmallAction(if (page == "MIX") "DEVICE CHAIN" else "TRACK MIX") { page = if (page == "MIX") "DEVICES" else "MIX" }
            SmallAction(if (expanded) "−" else "+") { expanded = !expanded }
            SmallAction("× CLOSE") { onToggle() }
        }
        if (expanded) {
            if (selectedTrack == null) {
                Text("Add a track in Arrangement to see its controls.", color = devicePalette.muted, fontSize = 9.sp)
            } else {
                Row(
                    Modifier.fillMaxWidth().height(28.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    project.tracks.forEach { track ->
                        val isSelected = selectedTrack.id == track.id
                        Surface(
                            color = if (isSelected) devicePalette.accent.copy(alpha = 0.18f) else devicePalette.room,
                            shape = RoundedCornerShape(5.dp),
                            border = if (isSelected) BorderStroke(1.dp, devicePalette.accent) else null,
                            modifier = Modifier.clickable { onSelectTrack(track.id) }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(Modifier.size(5.dp).background(Color(track.colorArgb.toInt()), RoundedCornerShape(2.dp)))
                                Text(track.name.take(18), color = if (isSelected) devicePalette.accent else devicePalette.text,
                                    fontSize = 9.sp, maxLines = 1)
                            }
                        }
                    }
                }
                if (page == "MIX") {
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(0.9f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("VOLUME  ·  $gainDb", color = devicePalette.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            val latestProject by rememberUpdatedState(project)
                            Canvas(
                                Modifier.fillMaxWidth().weight(1f)
                                    .pointerInput(selectedTrack.id) {
                                        detectDragGestures(
                                            onDragStart = { start ->
                                                val height = size.height.coerceAtLeast(1).toFloat()
                                                val value = (1f - start.y / height).coerceIn(0f, 1f) * 1.5f
                                                val current = latestProject
                                                onProjectChange(current.copy(tracks = current.tracks.map {
                                                    if (it.id == selectedTrack.id) it.copy(volume = value) else it
                                                }))
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val height = size.height.coerceAtLeast(1).toFloat()
                                                val value = (1f - change.position.y / height).coerceIn(0f, 1f) * 1.5f
                                                val current = latestProject
                                                onProjectChange(current.copy(tracks = current.tracks.map {
                                                    if (it.id == selectedTrack.id) it.copy(volume = value) else it
                                                }))
                                            }
                                        )
                                    }
                                    .pointerInput(selectedTrack.id) {
                                        detectTapGestures(onTap = { offset ->
                                            val height = size.height.coerceAtLeast(1).toFloat()
                                            val value = (1f - offset.y / height).coerceIn(0f, 1f) * 1.5f
                                            val current = latestProject
                                            onProjectChange(current.copy(tracks = current.tracks.map {
                                                if (it.id == selectedTrack.id) it.copy(volume = value) else it
                                            }))
                                        })
                                    }
                            ) {
                                val railWidth = (size.width * 0.18f).coerceAtLeast(5f)
                                val railLeft = (size.width - railWidth) / 2f
                                val railTop = size.height * 0.06f
                                val railBottom = size.height * 0.94f
                                for (tick in 0..10) {
                                    val y = railTop + (railBottom - railTop) * tick / 10f
                                    drawLine(devicePalette.grid.copy(alpha = 0.65f),
                                        Offset(size.width * 0.08f, y), Offset(size.width * 0.25f, y), 1f)
                                }
                                drawRoundRect(devicePalette.room, Offset(railLeft, railTop),
                                    Size(railWidth, (railBottom - railTop).coerceAtLeast(1f)),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(railWidth / 2f))
                                val fraction = selectedTrack.volume.coerceIn(0f, 1.5f) / 1.5f
                                val thumbY = railBottom - fraction * (railBottom - railTop)
                                drawRoundRect(devicePalette.accent.copy(alpha = 0.78f), Offset(railLeft, railTop),
                                    Size(railWidth, (thumbY - railTop).coerceAtLeast(0f)),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(railWidth / 2f))
                                drawRoundRect(devicePalette.text, Offset(size.width * 0.16f, thumbY - 3f),
                                    Size(size.width * 0.68f, 6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f))
                            }
                        }
                        Column(Modifier.weight(0.9f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("PAN  ·  " + when {
                                selectedTrack.pan <= -0.95f -> "L"
                                selectedTrack.pan >= 0.95f -> "R"
                                else -> (selectedTrack.pan * 100).toInt().toString()
                            }, color = devicePalette.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            StudioWavePanKnob(
                                value = selectedTrack.pan.coerceIn(-1f, 1f),
                                palette = devicePalette,
                                onValueChange = { value ->
                                    onProjectChange(project.copy(tracks = project.tracks.map {
                                        if (it.id == selectedTrack.id) it.copy(pan = value) else it
                                    }))
                                },
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            SmallAction(if (selectedTrack.muted) "MUTED" else "MUTE") {
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
                } else {
                    val slotInfo = when (selectedTrack.kind) {
                        "audio" -> listOf(
                            Triple("AUDIO SOURCE", "Available", "Imported clip source; trim is persisted"),
                            Triple("WARP / TIME STRETCH", "Unavailable", "Requires a tempo-aware time-stretch processor"),
                            Triple("AUDIO EFFECTS", "Unavailable", "No serializable DSP processor chain")
                        )
                        "drum" -> listOf(
                            Triple("STEP SEQUENCER", "Available", "Edits the selected drum clip"),
                            Triple("DRUM INSTRUMENT", "Unavailable", "Drum sample/instrument voices are not wired"),
                            Triple("AUDIO EFFECTS", "Unavailable", "No serializable DSP processor chain")
                        )
                        else -> listOf(
                            Triple("MIDI CLIP", "Available", "Note events and velocity are persisted"),
                            Triple("INSTRUMENT", "Limited", "Current playback is basic oscillator synthesis"),
                            Triple("MIDI / AUDIO FX", "Unavailable", "No serializable processor chain")
                        )
                    }
                    Row(Modifier.fillMaxWidth().weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        slotInfo.forEach { item ->
                            val available = item.second == "Available"
                            val limited = item.second == "Limited"
                            Column(
                                Modifier.width(164.dp).fillMaxHeight()
                                    .background(devicePalette.room, RoundedCornerShape(5.dp))
                                    .then(if (available) Modifier.border(1.dp, devicePalette.accent, RoundedCornerShape(5.dp)) else Modifier)
                                    .padding(7.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(6.dp).background(
                                        when { available -> devicePalette.success; limited -> devicePalette.warning; else -> devicePalette.muted },
                                        RoundedCornerShape(50.dp)
                                    ))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.first, color = if (available) devicePalette.accent else devicePalette.text,
                                            fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 2)
                                        Text(item.second.uppercase(), color = if (limited) devicePalette.warning else devicePalette.muted,
                                            fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(item.third, color = devicePalette.muted, fontSize = 8.sp)
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
        color = LocalStudioWavePalette.current.control,
        contentColor = LocalStudioWavePalette.current.text,
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
    val p = LocalStudioWavePalette.current
    Button(onClick = onClick, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
        shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(containerColor = p.control, contentColor = p.text)) {
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
    val p = LocalStudioWavePalette.current
    Button(onClick = onClick, modifier = Modifier.size(26.dp), contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(5.dp), colors = ButtonDefaults.buttonColors(containerColor = p.control, contentColor = tint)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(14.dp))
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
    private val durationCache = android.util.LruCache<String, Double>(12)
    private fun key(uri: String, peakCount: Int) = "$uri#$peakCount"
    @Synchronized fun get(uri: String, peakCount: Int): List<Float>? = cache.get(key(uri, peakCount))
    @Synchronized fun put(uri: String, peakCount: Int, peaks: List<Float>) {
        cache.put(key(uri, peakCount), peaks)
    }
    @Synchronized fun getDurationSeconds(uri: String): Double? = durationCache.get(uri)
    @Synchronized fun putDurationSeconds(uri: String, durationSeconds: Double) {
        if (durationSeconds.isFinite() && durationSeconds > 0.0) {
            durationCache.put(uri, durationSeconds)
        }
    }
}

/** Read the real audio-track duration without decoding PCM a second time. */
private fun readAudioSourceDurationSeconds(context: android.content.Context, uri: Uri): Double? {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(context, uri, null)
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true &&
                format.containsKey(MediaFormat.KEY_DURATION)) {
                val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                if (durationUs > 0L) return durationUs.toDouble() / 1_000_000.0
            }
        }
        null
    } catch (_: Exception) {
        null
    } finally {
        extractor.release()
    }
}

internal data class AudioWaveformClipRange(
    val firstPeakIndex: Int,
    val endPeakIndexExclusive: Int,
    val sourceOffsetSeconds: Double,
    val clipDurationSeconds: Double,
    val sourceDurationSeconds: Double
)

/** Use the same musical tick-to-time conversion as the playback engine. */
internal fun audioWaveformClipRange(
    sourceOffsetTicks: Long,
    clipLengthTicks: Long,
    bpm: Int,
    ppq: Int,
    sourceDurationSeconds: Double,
    peakCount: Int
): AudioWaveformClipRange? {
    if (!sourceDurationSeconds.isFinite() || sourceDurationSeconds <= 0.0 || peakCount <= 0) return null
    val ticksPerSecond = bpm.coerceAtLeast(1).toDouble() * ppq.coerceAtLeast(1).toDouble() / 60.0
    val offsetSeconds = sourceOffsetTicks.coerceAtLeast(0L).toDouble() / ticksPerSecond
    val clipSeconds = clipLengthTicks.coerceAtLeast(1L).toDouble() / ticksPerSecond
    if (!offsetSeconds.isFinite() || !clipSeconds.isFinite() || offsetSeconds >= sourceDurationSeconds) return null
    val endSeconds = (offsetSeconds + clipSeconds).coerceAtMost(sourceDurationSeconds)
    if (endSeconds <= offsetSeconds) return null
    val first = kotlin.math.floor(offsetSeconds / sourceDurationSeconds * peakCount)
        .toInt().coerceIn(0, peakCount - 1)
    val end = kotlin.math.ceil(endSeconds / sourceDurationSeconds * peakCount)
        .toInt().coerceIn(first + 1, peakCount)
    return AudioWaveformClipRange(first, end, offsetSeconds, clipSeconds, sourceDurationSeconds)
}

@Composable
private fun AudioClipWaveform(
    sourceUri: String?,
    accent: Color,
    sourceOffsetTicks: Long = 0L,
    visibleLengthTicks: Long = 1L,
    bpm: Int = 120,
    ppq: Int = 960
) {
    val context = LocalContext.current
    var peaks by remember(sourceUri) { mutableStateOf<List<Float>>(emptyList()) }
    var sourceDurationSeconds by remember(sourceUri) { mutableStateOf<Double?>(null) }
    LaunchedEffect(sourceUri) {
        peaks = emptyList()
        sourceDurationSeconds = null
        if (sourceUri.isNullOrBlank()) return@LaunchedEffect
        try {
            val loaded = withContext(Dispatchers.IO) {
                val decodeJob = currentCoroutineContext()[Job]
                val duration = WaveformPeakCache.getDurationSeconds(sourceUri)
                    ?: readAudioSourceDurationSeconds(context, Uri.parse(sourceUri))?.also {
                        WaveformPeakCache.putDurationSeconds(sourceUri, it)
                    }
                val cachedPeaks = WaveformPeakCache.get(sourceUri, 240)
                    ?: decodeWaveformPeaks(context, Uri.parse(sourceUri), 240) {
                        decodeJob?.ensureActive()
                    }.also { WaveformPeakCache.put(sourceUri, 240, it) }
                duration to cachedPeaks
            }
            sourceDurationSeconds = loaded.first
            peaks = loaded.second
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            peaks = emptyList()
            sourceDurationSeconds = null
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val accentColor = accent
        val mid = size.height / 2f
        if (peaks.isEmpty()) {
            drawLine(accentColor.copy(alpha = 0.3f), Offset(0f, mid), Offset(size.width, mid), 1f)
        } else {
            val durationSeconds = sourceDurationSeconds
            val range = durationSeconds?.let {
                audioWaveformClipRange(
                    sourceOffsetTicks = sourceOffsetTicks,
                    clipLengthTicks = visibleLengthTicks,
                    bpm = bpm,
                    ppq = ppq,
                    sourceDurationSeconds = it,
                    peakCount = peaks.size
                )
            }
            if (range == null) {
                // Metadata could not be read, or the clip starts beyond the source. Don't invent
                // a tick-to-frame conversion: show the whole source only for unknown duration.
                if (durationSeconds == null) {
                    val dx = size.width / peaks.size.coerceAtLeast(1)
                    peaks.forEachIndexed { index, peak ->
                        val amplitude = peak.coerceIn(0f, 1f) * size.height * 0.46f
                        val x = (index + 0.5f) * dx
                        drawLine(accentColor.copy(alpha = 0.88f), Offset(x, mid - amplitude), Offset(x, mid + amplitude), dx.coerceIn(1f, 3f))
                    }
                } else {
                    drawLine(accentColor.copy(alpha = 0.25f), Offset(0f, mid), Offset(size.width, mid), 1f)
                }
            } else {
                val dx = size.width * (range.sourceDurationSeconds / peaks.size.toDouble()) /
                    range.clipDurationSeconds
                for (index in range.firstPeakIndex until range.endPeakIndexExclusive) {
                    val peak = peaks[index].coerceIn(0f, 1f)
                    val sampleTime = (index + 0.5) * range.sourceDurationSeconds / peaks.size
                    val x = ((sampleTime - range.sourceOffsetSeconds) / range.clipDurationSeconds * size.width).toFloat()
                    if (x >= 0f && x <= size.width) {
                        val amplitude = peak * size.height * 0.46f
                        drawLine(accentColor.copy(alpha = 0.95f), Offset(x, mid - amplitude), Offset(x, mid + amplitude),
                            dx.toFloat().coerceIn(1f, 3f))
                    }
                }
            }
        }
    }
}


@Composable
private fun StudioWavePanKnob(
    value: Float,
    palette: StudioWavePalette,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sensitivity = with(density) { 1f / 38.dp.toPx() }
    val latestValue by rememberUpdatedState(value)
    Canvas(
        modifier
            .pointerInput(Unit) {
                var dragStartValue = 0f
                var accumulatedDrag = 0f
                detectDragGestures(
                    onDragStart = {
                        dragStartValue = latestValue
                        accumulatedDrag = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDrag -= dragAmount.y * sensitivity
                        onValueChange((dragStartValue + accumulatedDrag).coerceIn(-1f, 1f))
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onValueChange(0f) })
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.34f
        val arcRadius = radius + size.minDimension * 0.09f
        val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
        val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
        drawCircle(palette.room, radius, center)
        drawCircle(palette.control, radius, center, style = androidx.compose.ui.graphics.drawscope.Stroke(size.minDimension * 0.055f))
        drawArc(
            color = palette.border,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(size.minDimension * 0.045f)
        )
        val sweep = ((value + 1f) / 2f) * 270f
        drawArc(
            color = palette.accent,
            startAngle = 135f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = size.minDimension * 0.055f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
        val angle = Math.toRadians((135f + sweep).toDouble())
        drawLine(
            palette.text,
            center,
            Offset(
                center.x + kotlin.math.cos(angle).toFloat() * radius * 0.72f,
                center.y + kotlin.math.sin(angle).toFloat() * radius * 0.72f
            ),
            strokeWidth = size.minDimension * 0.045f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawCircle(palette.text.copy(alpha = 0.25f), size.minDimension * 0.025f, center)
    }
}



@Composable
private fun MixerStateButton(
    label: String,
    active: Boolean,
    palette: StudioWavePalette,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) palette.accent.copy(alpha = 0.2f) else palette.control,
        contentColor = if (active) palette.accent else palette.muted,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, if (active) palette.accent.copy(alpha = 0.8f) else palette.border.copy(alpha = 0.7f)),
        modifier = Modifier.size(width = 29.dp, height = 23.dp).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (active) "$label✓" else label, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
internal fun TransportAction(label: String, onClick: () -> Unit) {
    val p = LocalStudioWavePalette.current
    Surface(
        color = p.control,
        contentColor = p.text,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, p.border.copy(alpha = 0.65f)),
        modifier = Modifier.height(23.dp).clickable(onClick = onClick)
    ) {
        Box(Modifier.padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

