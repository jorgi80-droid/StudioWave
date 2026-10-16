package com.studiowave.app

import android.os.Bundle
import java.io.File
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Add
import android.content.Intent
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
import kotlin.math.pow
import kotlin.math.absoluteValue
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
import androidx.compose.ui.graphics.toArgb
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
import com.studiowave.app.audio.DirectWaveSampler
import com.studiowave.app.audio.ArrangementPlaybackEngine
import com.studiowave.app.audio.AudioPerformanceProfile
import com.studiowave.app.audio.ArrangementTiming
import com.studiowave.app.data.StudioProject
import com.studiowave.app.data.defaultDeviceParameters
import com.studiowave.app.data.EFFECT_DEVICE_TYPES
import com.studiowave.app.data.TrackDevice
import com.studiowave.app.data.StudioProjectFile
import com.studiowave.app.data.StudioTrack
import com.studiowave.app.data.WaveClip
import android.widget.Toast

private val Room: Color @Composable get() = LocalStudioWavePalette.current.room
private val CanvasSurface: Color @Composable get() = LocalStudioWavePalette.current.canvas
private val Raised: Color @Composable get() = LocalStudioWavePalette.current.panel
private val MutedText: Color @Composable get() = LocalStudioWavePalette.current.muted
private val Mint: Color @Composable get() = LocalStudioWavePalette.current.accent
private val AbletonOrange: Color @Composable get() = LocalStudioWavePalette.current.orange
private val Green: Color @Composable get() = LocalStudioWavePalette.current.success
private val Red: Color @Composable get() = LocalStudioWavePalette.current.danger

internal fun arrangementClipSummary(kind: String, clip: WaveClip, project: StudioProject): String {
    val safePpq = project.ppq.coerceIn(24, 3840).toLong()
    val beatTicks = (safePpq * 4L / project.denominator.coerceIn(1, 32).toLong()).coerceAtLeast(1L)
    val ticksPerBar = (beatTicks * project.numerator.coerceIn(1, 32).toLong()).coerceAtLeast(1L)
    val safeStart = clip.startTick.coerceAtLeast(0L)
    val safeLength = clip.lengthTicks.coerceAtLeast(1L)
    val startBar = safeStart / ticksPerBar + 1L
    val bars = kotlin.math.ceil(safeLength.toDouble() / ticksPerBar.toDouble())
        .toInt().coerceAtLeast(1)
    val barLabel = "$bars BAR" + if (bars == 1) "" else "S"
    val positionLabel = "BAR $startBar"
    return when (kind.lowercase()) {
        "midi" -> {
            val pitches = clip.midiNotes.map { it.pitch.coerceIn(0, 127) }
            val pitchLabel = if (pitches.isEmpty()) "EMPTY" else {
                val names = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
                fun noteName(pitch: Int) = "${names[pitch % 12]}${pitch / 12 - 1}"
                if (pitches.minOrNull() == pitches.maxOrNull()) noteName(pitches.first())
                else "${noteName(pitches.minOrNull()!!)}–${noteName(pitches.maxOrNull()!!)}"
            }
            "MIDI · ${clip.midiNotes.size} NOTES · $pitchLabel · $positionLabel · $barLabel"
        }
        "drum" -> {
            val active = clip.drumSteps.count { it }
            "DRUM · $active/${clip.drumSteps.size} ON · $positionLabel · $barLabel"
        }
        "audio" -> {
            val source = clip.sourceUri?.substringAfterLast('/')?.substringBefore('?')
                ?.take(14)?.ifBlank { "AUDIO" } ?: "NO SOURCE"
            val sourceState = if (clip.sourceUri.isNullOrBlank()) "MISSING" else "WAVE"
            "AUDIO · $source · $sourceState · $positionLabel · $barLabel"
        }
        else -> "${kind.uppercase()} · $positionLabel · $barLabel"
    }
}

internal fun choosePreferredRefreshRate(supportedRates: Iterable<Float>): Float? =
    supportedRates.asSequence()
        .filter { it.isFinite() && it >= 90f && it <= 120f }
        .maxOrNull()


/**
 * Convert a normalized vertical fader position to a linear gain using an audio-style
 * logarithmic taper. A zero fraction is a true mute; the top of each fader is that
 * control's supported maximum. The dB domain below the maximum is -60 dB.
 */
internal fun mixerFaderGainAtFraction(fraction: Float, maximumGain: Float): Float {
    if (!maximumGain.isFinite() || maximumGain <= 0f) return 0f
    val maxGain = maximumGain
    val normalized = fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    if (normalized <= 0f) return 0f
    val maxDb = 20.0 * kotlin.math.log10(maxGain.toDouble())
    val minDb = minOf(-60.0, maxDb - 60.0)
    val db = minDb + (maxDb - minDb) * normalized.toDouble()
    return (10.0.pow(db / 20.0)).toFloat().coerceIn(0f, maxGain)
}

/** Inverse of [mixerFaderGainAtFraction], used to render the fader cap at its real gain. */
internal fun mixerFaderFractionForGain(gain: Float, maximumGain: Float): Float {
    if (!maximumGain.isFinite() || maximumGain <= 0f) return 0f
    val maxGain = maximumGain
    val value = gain.takeIf { it.isFinite() }?.coerceIn(0f, maxGain) ?: 0f
    if (value <= 0f) return 0f
    val maxDb = 20.0 * kotlin.math.log10(maxGain.toDouble())
    val minDb = minOf(-60.0, maxDb - 60.0)
    val valueDb = 20.0 * kotlin.math.log10(value.toDouble())
    return ((valueDb - minDb) / (maxDb - minDb)).toFloat().coerceIn(0f, 1f)
}


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
    return (safeBarSteps.toFloat() * 2f / safeZoom).roundToInt().coerceIn(4, 4096)
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
    val safeClipLength = clipLengthTicks.coerceAtLeast(1L)
    // Preserve clip bounds even when callers supply a clip shorter than the current snap step.
    val effectiveStep = minOf(safeStep, safeClipLength)
    val noteStart = note.startTick.coerceIn(0L, (safeClipLength - effectiveStep).coerceAtLeast(0L))
    val originalDuration = note.durationTicks.coerceIn(
        effectiveStep,
        (safeClipLength - noteStart).coerceAtLeast(effectiveStep)
    )
    val safeDeltaSteps = deltaSteps.coerceIn(-100_000, 100_000)
    if (resize) {
        val maxDuration = (safeClipLength - noteStart).coerceAtLeast(effectiveStep)
        val requested = originalDuration.toDouble() + safeDeltaSteps.toDouble() * effectiveStep.toDouble()
        val duration = requested.coerceIn(effectiveStep.toDouble(), maxDuration.toDouble()).toLong()
        return note.copy(startTick = noteStart, durationTicks = duration.coerceIn(effectiveStep, maxDuration))
    }
    val maxStart = (safeClipLength - originalDuration).coerceAtLeast(0L)
    val rawStart = (noteStart.toDouble() + safeDeltaSteps.toDouble() * effectiveStep.toDouble())
        .coerceIn(0.0, maxStart.toDouble()).toLong()
    val quotient = (rawStart.toDouble() / effectiveStep.toDouble()).roundToLong()
    val snappedStart = (quotient.toDouble() * effectiveStep.toDouble())
        .coerceIn(0.0, maxStart.toDouble()).toLong()
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
    val sourceOffsetMicros = clip.sourceOffsetMicros?.coerceAtLeast(0L)
    val sourceLengthMicros = clip.sourceLengthMicros?.coerceAtLeast(1L)
    val leftSourceLengthMicros = sourceLengthMicros?.let { sourceLength ->
        if (sourceLength < 2L) sourceLength else
            (sourceLength.toDouble() * leftLength.toDouble() / length.toDouble()).toLong()
                .coerceIn(1L, sourceLength - 1L)
    }
    val rightSourceOffsetMicros = if (sourceOffsetMicros != null && leftSourceLengthMicros != null) {
        if (sourceOffsetMicros > Long.MAX_VALUE - leftSourceLengthMicros) Long.MAX_VALUE
        else sourceOffsetMicros + leftSourceLengthMicros
    } else null
    val rightSourceLengthMicros = if (sourceLengthMicros != null && leftSourceLengthMicros != null)
        (sourceLengthMicros - leftSourceLengthMicros).coerceAtLeast(1L) else null
    fun clippedNotes(offset: Long, windowLength: Long, newIds: Boolean) = clip.midiNotes.mapNotNull { note ->
        val noteStart = note.startTick.coerceAtLeast(0L)
        val noteEnd = if (noteStart > Long.MAX_VALUE - note.durationTicks.coerceAtLeast(1L)) Long.MAX_VALUE
            else noteStart + note.durationTicks.coerceAtLeast(1L)
        val windowEnd = if (offset > Long.MAX_VALUE - windowLength) Long.MAX_VALUE else offset + windowLength
        val from = noteStart.coerceAtLeast(offset)
        val to = noteEnd.coerceAtMost(windowEnd)
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
    val left = clip.copy(
        lengthTicks = leftLength,
        sourceLengthMicros = leftSourceLengthMicros,
        midiNotes = clippedNotes(0L, leftLength, false),
        drumSteps = clip.drumSteps.take(splitIndex)
    )
    val right = clip.copy(
        id = java.util.UUID.randomUUID().toString(), name = clip.name + " (2)", startTick = splitTick,
        lengthTicks = rightLength, sourceOffsetTicks = rightSourceOffset,
        sourceOffsetMicros = rightSourceOffsetMicros,
        sourceLengthMicros = rightSourceLengthMicros,
        midiNotes = clippedNotes(leftLength, rightLength, true), drumSteps = clip.drumSteps.drop(splitIndex)
    )
    return left to right
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        preferAdaptiveHighRefreshRate()
        StudioWaveDiagnostics.initialize(applicationContext)
        StudioWaveDiagnostics.installCrashHandler()
        StudioWaveDiagnostics.log("INFO", "LIFECYCLE", "MainActivity created")
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
    // Restore persisted projects off the main thread. Large sessions can contain dozens
    // of tracks and devices; parsing JSON during initial composition freezes the UI.
    var project by remember { mutableStateOf(StudioProject()) }
    var projectReady by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var recordingAudio by remember { mutableStateOf(false) }
    var liveAudioRecorder by remember { mutableStateOf<com.studiowave.app.audio.LiveAudioRecorder?>(null) }
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
                // Session IDs reject stale callbacks after seek/stop/project-open. Within
                // one session, always accept the latest render position: a live arrangement
                // edit can shorten the schedule, so a monotonic-only guard can freeze the cursor.
                playheadTick = boundedTick
            }
        }
    }
    var bpm by remember { mutableIntStateOf(project.bpm) }
    LaunchedEffect(preferences) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val restored = try {
            withContext(Dispatchers.IO) {
                preferences.getString("project_json", null)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { StudioProjectFile.decode(it.toByteArray(Charsets.UTF_8)) }
            }
        } catch (failure: Exception) {
            StudioWaveDiagnostics.log("ERROR", "PROJECT_LOAD", "Autosaved project restore failed: ${failure.javaClass.simpleName}: ${failure.message}")
            null
        }
        if (restored != null) {
            project = restored
            bpm = restored.bpm
        }
        projectReady = true
        val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt
        val active = restored ?: project
        StudioWaveDiagnostics.log(
            "INFO", "PROJECT_LOAD",
            "Autosaved session restored in ${elapsedMs} ms · ${active.tracks.size} tracks · ${active.tracks.sumOf { it.clips.size }} clips · ${active.tracks.sumOf { it.devices.size }} devices"
        )
    }
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
    var audioProfile by remember {
        mutableStateOf(preferences.getString("audio_profile", "AUTO")?.takeIf {
            it in setOf("AUTO", "LOW_LATENCY", "BALANCED", "STABLE")
        } ?: "AUTO")
    }
    var metronomeEnabled by remember { mutableStateOf(preferences.getBoolean("metronome_enabled", false)) }
    var metronomeLevel by remember { mutableFloatStateOf(preferences.getFloat("metronome_level", 0.25f).coerceIn(0f, 1f)) }
    var parallelPreparation by remember { mutableStateOf(preferences.getBoolean("parallel_preparation", true)) }
    var diagnosticLogLines by remember { mutableStateOf(StudioWaveDiagnostics.snapshot()) }
    var audioDiagnosticsLines by remember { mutableStateOf(listOf("ENGINE STATUS::Collecting runtime diagnostics…")) }
    var themeName by remember {
        mutableStateOf(
            if (preferences.getBoolean("ui_theme_selected_manually", false)) {
                preferences.getString("ui_theme", "Live Light") ?: "Live Light"
            } else {
                // Match the supplied Ableton Live references by default; graphite themes remain in Settings.
                "Live Light"
            }
        )
    }
    var paletteTone by remember { mutableStateOf(preferences.getString("ui_tone", "Neutral") ?: "Neutral") }
    var highContrast by remember { mutableStateOf(preferences.getBoolean("ui_high_contrast", false)) }
    var gridOpacity by remember { mutableFloatStateOf(preferences.getFloat("ui_grid_opacity", 0.72f)) }
    var uiScale by remember { mutableFloatStateOf(preferences.getFloat("ui_scale", 1f).coerceIn(0.6f, 1.2f)) }
    // Start with the device drawer collapsed so Arrangement gets the full vertical workspace.
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
    val compactTracks = remember { mutableStateMapOf<String, Boolean>() }
    var editorTrackId by remember { mutableStateOf<String?>(null) }
    var editorClipId by remember { mutableStateOf<String?>(null) }
    var pendingAudioTrackId by remember { mutableStateOf<String?>(null) }
    var pendingAudioStartTick by remember { mutableStateOf<Long?>(null) }
    var returnToAudioEditorAfterImport by remember { mutableStateOf(false) }
    var scratchOffset by remember { mutableFloatStateOf(0f) }
    var undoHistory by remember { mutableStateOf<List<StudioProject>>(emptyList()) }
    var redoHistory by remember { mutableStateOf<List<StudioProject>>(emptyList()) }
    var pendingExportProject by remember { mutableStateOf<StudioProject?>(null) }
    var restartPendingPlayback: ((StudioProject) -> Unit)? = null
    fun arrangementScheduleChanged(previous: StudioProject, next: StudioProject): Boolean {
        if (previous.bpm != next.bpm || previous.ppq != next.ppq ||
            previous.numerator != next.numerator || previous.denominator != next.denominator ||
            previous.tracks.size != next.tracks.size
        ) return true
        for (trackIndex in previous.tracks.indices) {
            val oldTrack = previous.tracks[trackIndex]
            val newTrack = next.tracks[trackIndex]
            if (oldTrack.id != newTrack.id || oldTrack.kind != newTrack.kind ||
                oldTrack.clips != newTrack.clips ||
                oldTrack.devices.size != newTrack.devices.size
            ) return true
            for (deviceIndex in oldTrack.devices.indices) {
                val oldDevice = oldTrack.devices[deviceIndex]
                val newDevice = newTrack.devices[deviceIndex]
                if (oldDevice.id != newDevice.id || oldDevice.type != newDevice.type ||
                    oldDevice.enabled != newDevice.enabled
                ) return true
            }
        }
        return false
    }
    fun changeProject(next: StudioProject) {
        if (next != project) {
            val previous = project
            val arrangementChanged = arrangementScheduleChanged(previous, next)
            undoHistory = (undoHistory + previous).takeLast(100)
            redoHistory = emptyList()
            project = next
            arrangementPlayback.updateMix(next)
            if (arrangementChanged && playing && !playbackLoading) {
                // Clip/sample/note edits publish a new render plan in the background.
                // Do not stop transport or reopen AAudio while the new plan is prepared.
                playbackScope.launch {
                    runCatching { arrangementPlayback.updateArrangement(next) }
                        .onFailure { failure ->
                            audioError = failure.message ?: "Arrangement update failed"
                            StudioWaveDiagnostics.log(
                                "ERROR", "AUDIO_HOT_UPDATE", audioError ?: "Arrangement update failed"
                            )
                        }
                }
            } else if (arrangementChanged && playbackLoading) {
                // Keep the pending initial playback snapshot in sync with edits made while loading.
                restartPendingPlayback?.invoke(next)
            }
            StudioWaveDiagnostics.logThrottled("project-edit", 300L, "INFO", "PROJECT",
                "Project edit committed: '${next.name}', ${next.tracks.size} tracks, ${next.tracks.sumOf { it.clips.size }} clips")
        }
    }
    fun undoProject() {
        val previous = undoHistory.lastOrNull() ?: return
        redoHistory = redoHistory + project
        undoHistory = undoHistory.dropLast(1)
        StudioWaveDiagnostics.log("INFO", "EDIT_HISTORY", "Undo project change")
        project = previous
        bpm = previous.bpm
        arrangementPlayback.updateMix(previous)
        if (playing && !playbackLoading) {
            playbackScope.launch {
                runCatching { arrangementPlayback.updateArrangement(previous) }
                    .onFailure { audioError = it.message ?: "Undo arrangement update failed" }
            }
        } else if (playbackLoading) {
            restartPendingPlayback?.invoke(previous)
        }
    }
    fun redoProject() {
        val next = redoHistory.lastOrNull() ?: return
        undoHistory = undoHistory + project
        redoHistory = redoHistory.dropLast(1)
        StudioWaveDiagnostics.log("INFO", "EDIT_HISTORY", "Redo project change")
        project = next
        bpm = next.bpm
        arrangementPlayback.updateMix(next)
        if (playing && !playbackLoading) {
            playbackScope.launch {
                runCatching { arrangementPlayback.updateArrangement(next) }
                    .onFailure { audioError = it.message ?: "Redo arrangement update failed" }
            }
        } else if (playbackLoading) {
            restartPendingPlayback?.invoke(next)
        }
    }
    fun stopPlayback() {
        playbackSession += 1L
        playbackJob?.cancel()
        playbackJob = null
        runCatching { arrangementPlayback.stop() }
        StudioWaveDiagnostics.log("INFO", "TRANSPORT", "Playback stopped at tick $playheadTick")
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
        StudioWaveDiagnostics.log("INFO", "TRANSPORT", "Playback preparation requested at tick $tick for project '${snapshot.name}'")
        val gain = master.coerceIn(0f, 1f)
        val startTimebase = TimelineTimebase(snapshot.bpm, snapshot.numerator, snapshot.denominator, snapshot.ppq)
        val startTick = clampArrangementTick(tick, startTimebase)
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
                                // Live edits may extend the arrangement while playback runs.
                                // Resolve the completion point from the latest project, not the
                                // stale snapshot that originally started transport.
                                val completedProject = project
                                val completedTimebase = TimelineTimebase(
                                    completedProject.bpm, completedProject.numerator,
                                    completedProject.denominator, completedProject.ppq
                                )
                                val arrangementLimit = maxArrangementTick(completedTimebase)
                                val projectEnd = completedProject.tracks.asSequence()
                                    .flatMap { it.clips.asSequence() }
                                    .maxOfOrNull { clip ->
                                        val start = clip.startTick.coerceAtLeast(0L)
                                        val length = clip.lengthTicks.coerceAtLeast(1L)
                                        if (start > Long.MAX_VALUE - length) Long.MAX_VALUE else start + length
                                    }
                                playheadTick = clampArrangementTick(projectEnd ?: playheadTick, completedTimebase)
                                    .coerceAtMost(arrangementLimit)
                                playing = false
                                playbackLoading = false
                                playbackJob = null
                            }
                        }
                    },
                    onError = { message ->
                        StudioWaveDiagnostics.log("ERROR", "AUDIO", "Playback failed: $message")
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
                    },
                    parallelPreparation = parallelPreparation,
                    metronomeEnabled = metronomeEnabled,
                    metronomeLevel = metronomeLevel,
                    queueTargetFrames = AudioPerformanceProfile.queueTargetFrames(context, audioProfile).also {
                        arrangementPlayback.setDecoderParallelism(
                            AudioPerformanceProfile.decoderParallelism(context, audioProfile, parallelPreparation)
                        )
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
    restartPendingPlayback = { updated -> startPlaybackAt(playheadTick, updated) }
    fun seekToTick(tick: Long) {
        val timebase = TimelineTimebase(project.bpm, project.numerator, project.denominator, project.ppq)
        val target = clampArrangementTick(tick, timebase)
        // Match DAW transport semantics: seeking while the transport is active is a
        // discontinuity, not an implicit STOP. Rebuild playback from the requested
        // musical position so the audio and playhead remain together.
        if (playing || playbackLoading) {
            startPlaybackAt(target)
        } else {
            playheadTick = target
        }
    }
    fun startLiveRecording() {
        if (recordingAudio) return
        stopPlayback()
        val recorder = com.studiowave.app.audio.LiveAudioRecorder(File(context.filesDir, "recordings"))
        try {
            recorder.start()
            liveAudioRecorder = recorder
            recordingAudio = true
            audioError = null
            android.widget.Toast.makeText(context, "Perekaman audio berjalan — tekan REC lagi untuk berhenti", android.widget.Toast.LENGTH_LONG).show()
            StudioWaveDiagnostics.log("INFO", "RECORD", "Microphone recording started")
        } catch (failure: Exception) {
            runCatching { recorder.close() }
            liveAudioRecorder = null
            recordingAudio = false
            audioError = failure.message ?: "Tidak dapat memulai perekaman mikrofon"
            StudioWaveDiagnostics.log("ERROR", "RECORD", "Microphone recording failed to start: ${failure.message}")
        }
    }

    fun finishLiveRecording() {
        val recorder = liveAudioRecorder ?: return
        liveAudioRecorder = null
        recordingAudio = false
        playbackScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { recorder.stop() }
                if (project.tracks.size >= StudioProject.MAX_TRACKS) {
                    result.file.delete()
                    error("Batas track tercapai; rekaman tidak dapat ditambahkan")
                }
                val timebase = TimelineTimebase(project.bpm, project.numerator, project.denominator, project.ppq)
                val timelineLimit = maxArrangementTick(timebase)
                val startTick = clampArrangementTick(playheadTick, timebase)
                    .coerceAtMost((timelineLimit - 1L).coerceAtLeast(0L))
                val rawLength = kotlin.math.ceil(
                    result.durationMillis.toDouble() * project.bpm * project.ppq / 60_000.0
                ).toLong().coerceAtLeast(1L)
                val lengthTicks = rawLength.coerceAtMost((timelineLimit - startTick).coerceAtLeast(1L))
                val durationMicros = (lengthTicks.toDouble() * 60_000_000.0 /
                    (project.bpm * project.ppq)).toLong().coerceAtLeast(1L)
                val number = project.tracks.count { it.kind == "audio" } + 1
                val clip = com.studiowave.app.data.WaveClip(
                    name = "Recording $number",
                    startTick = startTick,
                    lengthTicks = lengthTicks,
                    sourceUri = android.net.Uri.fromFile(result.file).toString(),
                    sourceOffsetMicros = 0L,
                    sourceLengthMicros = durationMicros
                )
                val track = StudioTrack(
                    name = "AUDIO $number",
                    colorArgb = 0xFF3B82F6L,
                    kind = "audio",
                    clips = listOf(clip)
                )
                changeProject(project.copy(tracks = project.tracks + track))
                audioError = null
                android.widget.Toast.makeText(context, "Rekaman ditambahkan ke Arrangement", android.widget.Toast.LENGTH_LONG).show()
                StudioWaveDiagnostics.log("INFO", "RECORD", "Microphone recording finalized: ${result.durationMillis} ms, ${result.file.length()} bytes")
            } catch (failure: Exception) {
                audioError = failure.message ?: "Perekaman audio gagal disimpan"
                StudioWaveDiagnostics.log("ERROR", "RECORD", "Microphone recording finalization failed: ${failure.message}")
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startLiveRecording()
        else audioError = "Izin mikrofon ditolak. Izinkan mikrofon untuk merekam audio."
    }
    fun toggleLiveRecording() {
        if (recordingAudio) {
            finishLiveRecording()
        } else if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startLiveRecording()
        } else {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    val latestProject by rememberUpdatedState(project)
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            pendingAudioTrackId = null
            pendingAudioStartTick = null
            returnToAudioEditorAfterImport = false
        }
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            playbackScope.launch {
                try {
                    val durationMs = withContext(Dispatchers.IO) {
                        // Prefer container/track metadata. MediaMetadataRetriever may spin up
                        // expensive extractors on some content providers and delay clip creation.
                        val extractor = MediaExtractor()
                        val extractedDurationMs = try {
                            extractor.setDataSource(context, uri, null)
                            var durationUs = 0L
                            for (trackIndex in 0 until extractor.trackCount) {
                                val format = extractor.getTrackFormat(trackIndex)
                                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true &&
                                    format.containsKey(MediaFormat.KEY_DURATION)) {
                                    durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(0L))
                                }
                            }
                            durationUs / 1000L
                        } finally {
                            runCatching { extractor.release() }
                        }
                        if (extractedDurationMs > 0L) extractedDurationMs else {
                            val retriever = MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(context, uri)
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                            } finally {
                                runCatching { retriever.release() }
                            }
                        }
                    }
                    var currentProject = latestProject
                    val requestedTrackId = pendingAudioTrackId ?: editorTrackId
                    var audioTrack = currentProject.tracks.firstOrNull { it.id == requestedTrackId && it.kind == "audio" }
                        ?: currentProject.tracks.firstOrNull { it.kind == "audio" }
                    var target = audioTrack ?: StudioTrack(name = "AUDIO 1", colorArgb = 0xFF3B82F6L, kind = "audio")
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
                            // Commit the clip immediately after metadata inspection. Do not wait for
                            // MediaCodec/PCM preparation before showing it in Arrangement: the active
                            // transport publishes a partial plan and prepares this source on its IO
                            // workers, then joins it at a render-block boundary.
                            // Merge with the latest snapshot so edits made during import are retained.
                            currentProject = latestProject
                            audioTrack = currentProject.tracks.firstOrNull { it.id == target.id && it.kind == "audio" }
                            if (audioTrack == null && currentProject.tracks.size >= StudioProject.MAX_TRACKS) {
                                error("Track limit reached (${StudioProject.MAX_TRACKS}); remove a track before importing.")
                            }
                            target = audioTrack ?: target.copy(clips = emptyList())
                            if (target.clips.size >= StudioProject.MAX_CLIPS_PER_TRACK) {
                                error("Clip limit reached for the audio track.")
                            }
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
                            val appendStart = target.clips.maxOfOrNull { clip ->
                                val end = clip.startTick.coerceAtLeast(0L)
                                val length = clip.lengthTicks.coerceAtLeast(0L)
                                if (end > Long.MAX_VALUE - length) Long.MAX_VALUE else end + length
                            } ?: 0L
                            val requestedStart = if (pendingAudioTrackId == target.id) {
                                pendingAudioStartTick ?: appendStart
                            } else appendStart
                            val clipStart = requestedStart.coerceIn(0L, arrangementEndTick - 1L)
                            val lengthTicks = requestedLengthTicks.coerceIn(1L, arrangementEndTick - clipStart)
                            val clip = com.studiowave.app.data.WaveClip(
                                name = uri.lastPathSegment?.substringAfterLast('/')?.take(32)?.ifBlank { "Audio clip" } ?: "Audio clip",
                                startTick = clipStart,
                                lengthTicks = lengthTicks,
                                sourceUri = uri.toString(),
                                sourceOffsetMicros = 0L,
                                sourceLengthMicros = if (durationMs > 0L && durationMs <= Long.MAX_VALUE / 1000L)
                                    (durationMs * 1000L).coerceAtLeast(1L) else null
                            )
                            val updatedTrack = target.copy(clips = target.clips + clip)
                            changeProject(currentProject.copy(tracks = if (audioTrack == null) {
                                currentProject.tracks + updatedTrack
                            } else currentProject.tracks.map { if (it.id == target.id) updatedTrack else it }))
                            editorTrackId = target.id
                            editorClipId = clip.id
                            pendingAudioTrackId = null
                            pendingAudioStartTick = null
                            selectedWorkspace = if (returnToAudioEditorAfterImport) "AUDIO EDITOR" else "ARRANGEMENT"
                            returnToAudioEditorAfterImport = false
                            audioError = null
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    audioError = failure.message ?: "Could not inspect the audio file."
                    StudioWaveDiagnostics.log("ERROR", "AUDIO_IMPORT", "Audio import/metadata inspection failed: ${failure.message}")
                    selectedWorkspace = "ARRANGEMENT"
                } finally {
                    // Clear every deferred import target on success or failure so a retry
                    // cannot accidentally reuse an old track or playhead position.
                    pendingAudioTrackId = null
                    pendingAudioStartTick = null
                    returnToAudioEditorAfterImport = false
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
                    StudioWaveDiagnostics.log("INFO", "PROJECT", "Project '${snapshot.name}' exported successfully")
                    Toast.makeText(context, "Proyek .sdaw berhasil disimpan", Toast.LENGTH_SHORT).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    audioError = failure.message ?: "Ekspor proyek gagal"
                    StudioWaveDiagnostics.log("ERROR", "PROJECT", "Project export failed: ${failure.message}")
                }
            }
        }
    }
    val exportWavLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri ->
        if (uri != null) {
            playbackScope.launch {
                try {
                    val destination = withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")
                            ?: error("Tidak dapat membuka tujuan ekspor WAV")
                    }
                    stopPlayback()
                    arrangementPlayback.beginWavCapture(destination)
                    startPlaybackAt(playheadTick)
                    Toast.makeText(context, "Bounce WAV dimulai — tekan Stop untuk menyelesaikan file", Toast.LENGTH_LONG).show()
                    StudioWaveDiagnostics.log("INFO", "EXPORT", "WAV capture started for project")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    arrangementPlayback.endWavCapture()
                    audioError = failure.message ?: "Ekspor WAV gagal"
                    StudioWaveDiagnostics.log("ERROR", "EXPORT", "WAV capture failed: ${failure.message}")
                }
            }
        }
    }
    val importProjectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            playbackScope.launch {
                val loadStartedAt = android.os.SystemClock.elapsedRealtime()
                try {
                    val imported = withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("Tidak dapat membuka file proyek")
                        input.use { StudioProjectFile.read(it) }
                    }
                    // Invalidate queued progress/completion callbacks from the previous
                    // project before stopping its output; otherwise an old tick can overwrite
                    // the newly opened session's cursor.
                    playbackSession += 1L
                    playbackJob?.cancel()
                    playbackJob = null
                    arrangementPlayback.stop()
                    playing = false
                    playbackLoading = false
                    changeProject(imported)
                    bpm = imported.bpm
                    playheadTick = 0L
                    scrollTicks = 0.0
                    scrollTracks = 0f
                    zoom = 1f
                    verticalZoom = 1f
                    editorTrackId = null
                    editorClipId = null
                    selectedWorkspace = "ARRANGEMENT"
                    audioError = null
                    val loadElapsedMs = android.os.SystemClock.elapsedRealtime() - loadStartedAt
                    StudioWaveDiagnostics.log(
                        "INFO", "PROJECT_LOAD",
                        "Imported '${imported.name}' in ${loadElapsedMs} ms · ${imported.tracks.size} tracks · ${imported.tracks.sumOf { it.clips.size }} clips · ${imported.tracks.sumOf { it.devices.size }} devices"
                    )
                    Toast.makeText(context, "Proyek .sdaw berhasil dibuka", Toast.LENGTH_SHORT).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    audioError = failure.message ?: "Impor proyek gagal"
                    StudioWaveDiagnostics.log("ERROR", "PROJECT", "Project import failed: ${failure.message}")
                }
            }
        }
    }
    LaunchedEffect(arrangementPlayback, showSettings, settingsSection) {
        while (true) {
            val output = arrangementPlayback.outputDiagnostics()
            StudioWaveDiagnostics.recordUnderruns(output.underruns)
            if (showSettings && settingsSection == "LOG") {
                val runtime = StudioWaveDiagnostics.runtimeSnapshot()
                val javaUsedMb = runtime.javaHeapUsedBytes / (1024.0 * 1024.0)
                val javaMaxMb = runtime.javaHeapMaxBytes / (1024.0 * 1024.0)
                val nativeMb = runtime.nativeHeapUsedBytes / (1024.0 * 1024.0)
                val pssMb = runtime.processPssBytes / (1024.0 * 1024.0)
                val cpuLabel = runtime.cpuPercent?.let { "%.1f%%".format(java.util.Locale.US, it) } ?: "sampling…"
                val fpsLabel = runtime.uiFps?.let { "%.1f fps".format(java.util.Locale.US, it) } ?: "sampling…"
                val frameLabel = runtime.uiFrameIntervalMs?.let { "%.2f ms".format(java.util.Locale.US, it) } ?: "sampling…"
                val queueTarget = AudioPerformanceProfile.queueTargetFrames(context, audioProfile)
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                val deviceSampleRate = audioManager?.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "unknown"
                val deviceBurstFrames = audioManager?.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) ?: "unknown"
                audioDiagnosticsLines = listOf(
                    "DEVICE AUDIO::Android suggested output rate $deviceSampleRate Hz · output burst $deviceBurstFrames frames",
                    "OUTPUT PROFILE::${audioProfile.replace('_', ' ')} · target producer queue $queueTarget frames",
                    "ENGINE STATE::${if (playing) "PLAYING" else if (playbackLoading) "PREPARING" else "STOPPED"} · stereo PCM16 · ${ArrangementPlaybackEngine.OUTPUT_SAMPLE_RATE} Hz",
                    "UNDERRUN MONITOR::${output.underruns} native underruns observed · ${StudioWaveDiagnostics.underrunEvents()} increases logged since session start",
                    "OUTPUT QUEUE::${output.queuedFrames} frames queued · ${output.playedFrames} frames consumed",
                    "PROCESS CPU::$cpuLabel · ${runtime.availableProcessors} logical processors reported by Android",
                    "MEMORY::Process PSS %.1f MB · Java heap %.1f / %.1f MB · native heap %.1f MB".format(java.util.Locale.US, pssMb, javaUsedMb, javaMaxMb, nativeMb),
                    "RENDER PACING::${fpsLabel} estimated UI frame rate · ${frameLabel} smoothed UI frame interval",
                    "AUDIO RENDER::last ${"%.2f".format(java.util.Locale.US, output.lastRenderMillis)} ms · max ${"%.2f".format(java.util.Locale.US, output.maxRenderMillis)} ms · budget ${"%.2f".format(java.util.Locale.US, output.renderBudgetMillis)} ms · ${output.renderOverrunBlocks} over-budget blocks",
                    "PROJECT LOAD::${project.tracks.size} tracks · ${project.tracks.sumOf { it.clips.size }} clips · BPM ${project.bpm}",
                    "MIXER CONFIG::256-frame render blocks · ${if (parallelPreparation) "parallel source preparation ON" else "sequential source preparation"} · realtime mix worker single-threaded",
                    "METRONOME::${if (metronomeEnabled) "ON" else "OFF"} · level ${(metronomeLevel * 100).toInt()}%"
                )
                diagnosticLogLines = StudioWaveDiagnostics.snapshot()
            }
            delay(1000)
        }
    }
    LaunchedEffect(showSettings, settingsSection) {
        if (showSettings && settingsSection == "LOG") {
            var lastFrame = 0L
            while (true) {
                val frame = withFrameNanos { it }
                if (lastFrame != 0L) StudioWaveDiagnostics.recordUiFrameInterval(frame - lastFrame)
                lastFrame = frame
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
    LaunchedEffect(project, projectReady) {
        if (!projectReady) return@LaunchedEffect
        delay(350)
        val snapshot = project
        try {
            val serialized = withContext(Dispatchers.Default) {
                String(StudioProjectFile.encode(snapshot), Charsets.UTF_8)
            }
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
            .putString("audio_profile", audioProfile)
            .putBoolean("metronome_enabled", metronomeEnabled)
            .putFloat("metronome_level", metronomeLevel)
            .putBoolean("parallel_preparation", parallelPreparation)
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
    // Keep Material defaults (dialogs/cards/menus) inside the same crisp Live-style geometry.
    val liveShapes = Shapes(
        extraSmall = RoundedCornerShape(1.dp),
        small = RoundedCornerShape(1.dp),
        medium = RoundedCornerShape(1.dp),
        large = RoundedCornerShape(1.dp),
        extraLarge = RoundedCornerShape(1.dp)
    )
    MaterialTheme(colorScheme = studioColorScheme, shapes = liveShapes) {
        CompositionLocalProvider(
            LocalStudioWavePalette provides swatch,
            LocalStudioWaveGridOpacity provides gridOpacity,
            LocalDensity provides scaledDensity
        ) {
        Surface(Modifier.fillMaxSize(), color = swatch.room) {
            if (!projectReady) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(color = swatch.accent)
                    Spacer(Modifier.height(12.dp))
                    Text("RESTORING PROJECT", color = swatch.text, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("Loading session off the UI thread…", color = swatch.muted, fontSize = 10.sp)
                }
            } else if (showSettings) {
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
                    audioStatus = AudioPerformanceProfile.describeDevice(context, audioProfile, parallelPreparation) +
                        " · Native AAudio stereo PCM16 · 256-frame render blocks; Android audio policy may override requested stream properties.",
                    audioProfile = audioProfile,
                    onAudioProfileChange = { value ->
                        audioProfile = value
                        preferences.edit().putString("audio_profile", value).apply()
                        StudioWaveDiagnostics.log("INFO", "AUDIO_SETTINGS", "Output profile changed to $value; effective queue=${AudioPerformanceProfile.queueTargetFrames(context, value)} frames; decoder parallelism=${AudioPerformanceProfile.decoderParallelism(context, value, parallelPreparation)}")
                    },
                    metronomeEnabled = metronomeEnabled,
                    onMetronomeEnabledChange = { value ->
                        metronomeEnabled = value
                        preferences.edit().putBoolean("metronome_enabled", value).apply()
                        arrangementPlayback.setMetronome(value, metronomeLevel)
                        StudioWaveDiagnostics.log("INFO", "AUDIO_SETTINGS", "Metronome ${if (value) "enabled" else "disabled"}")
                    },
                    metronomeLevel = metronomeLevel,
                    onMetronomeLevelChange = { value ->
                        metronomeLevel = value.coerceIn(0f, 1f)
                        preferences.edit().putFloat("metronome_level", metronomeLevel).apply()
                        arrangementPlayback.setMetronome(metronomeEnabled, metronomeLevel)
                    },
                    parallelPreparation = parallelPreparation,
                    onParallelPreparationChange = { value ->
                        parallelPreparation = value
                        preferences.edit().putBoolean("parallel_preparation", value).apply()
                        StudioWaveDiagnostics.log("INFO", "AUDIO_SETTINGS", "Parallel sample preparation ${if (value) "enabled" else "disabled"}")
                    },
                    audioDiagnostics = audioDiagnosticsLines,
                    appLog = diagnosticLogLines,
                    onShareLogs = {
                        val report = StudioWaveDiagnostics.formattedReport(audioDiagnosticsLines)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "StudioWave diagnostics")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, "Share StudioWave diagnostics")) }
                            .onFailure {
                                StudioWaveDiagnostics.log("ERROR", "DIAGNOSTICS", "Could not open share sheet: ${it.message}")
                                Toast.makeText(context, "Could not share diagnostics: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                    },
                    onClearLogs = {
                        StudioWaveDiagnostics.clear()
                        diagnosticLogLines = StudioWaveDiagnostics.snapshot()
                    },
                    currentProjectName = project.name,
                    onNewProject = { name ->
                        stopPlayback()
                        val fresh = StudioProject(name = name.take(64).ifBlank { "Untitled Set" },
                            tracks = StudioProject.defaultTracks().map { it.copy(clips = emptyList()) })
                        undoHistory = emptyList()
                        redoHistory = emptyList()
                        project = fresh
                        bpm = fresh.bpm
                        playheadTick = 0L
                        scrollTicks = 0.0
                        scrollTracks = 0f
                        zoom = 1f
                        verticalZoom = 1f
                        editorTrackId = fresh.tracks.firstOrNull()?.id
                        editorClipId = null
                        selectedWorkspace = "ARRANGEMENT"
                        arrangementPlayback.updateMix(fresh)
                        StudioWaveDiagnostics.log("INFO", "PROJECT", "Created new empty project '${fresh.name}'")
                        preferences.edit().putString("project_json", fresh.toJson().toString()).apply()
                        showSettings = false
                        Toast.makeText(context, "Proyek baru: ${fresh.name}", Toast.LENGTH_SHORT).show()
                    },
                    onSaveProject = {
                        pendingExportProject = project
                        showSettings = false
                        StudioWaveDiagnostics.log("INFO", "PROJECT", "Save As requested for '${project.name}'")
                        exportProjectLauncher.launch(suggestedSdawFileName(project.name))
                    },
                    onOpenProject = {
                        showSettings = false
                        StudioWaveDiagnostics.log("INFO", "PROJECT", "Open project picker requested")
                        importProjectLauncher.launch(arrayOf("*/*"))
                    }
                )
            } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp)) {
                if (audioError != null) {
                    Surface(color = LocalStudioWavePalette.current.danger.copy(alpha = 0.22f), shape = RoundedCornerShape(2.dp), modifier = Modifier.fillMaxWidth().clickable { audioError = null }) {
                        Text(audioError!!, color = LocalStudioWavePalette.current.text, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 2)
                    }
                }
                TransportBar(
                    compact = selectedWorkspace == "ARRANGEMENT" || selectedWorkspace == "PIANO ROLL",
                    playing = playing || playbackLoading,
                    recording = recordingAudio,
                    bpm = bpm,
                    master = master,
                    peakDbfs = masterPeakDbfs,
                    rmsDbfs = masterRmsDbfs,
                    momentaryLufs = masterMomentaryLufs,
                    peakHistory = masterPeakHistory,
                    positionLabel = musicalPositionLabel(playheadTick, project.ppq, project.numerator, project.denominator),
                    timeSignatureLabel = "${project.numerator}/${project.denominator}",
                    onSettings = { settingsSection = "INTERFACE"; showSettings = true },
                    onNewProject = { settingsSection = "PROJECT"; showSettings = true },
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
                    onRecord = { toggleLiveRecording() },
                    onExportWav = { exportWavLauncher.launch(project.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80) + ".wav") },
                    onUndo = { undoProject() },
                    onRedo = { redoProject() },
                    onSaveProject = {
                        pendingExportProject = project
                        StudioWaveDiagnostics.log("INFO", "PROJECT", "Save As requested for '${project.name}'")
                        exportProjectLauncher.launch(suggestedSdawFileName(project.name))
                    },
                    onOpenProject = {
                        StudioWaveDiagnostics.log("INFO", "PROJECT", "Open project picker requested")
                        importProjectLauncher.launch(arrayOf("*/*"))
                    }
                )
                Spacer(Modifier.height(3.dp))
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    WorkspaceRail(
                        selected = selectedWorkspace,
                        onSelect = {
                            selectedWorkspace = it
                            StudioWaveDiagnostics.log("INFO", "WORKSPACE", "Workspace changed to $it")
                        }
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (selectedWorkspace != "ARRANGEMENT" && selectedWorkspace !in setOf("PIANO ROLL", "AUDIO EDITOR", "DRUM SEQUENCER")) {
                            Row(
                                Modifier.fillMaxWidth().height(22.dp)
                                    .background(LocalStudioWavePalette.current.panel, RoundedCornerShape(2.dp))
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
                                        "TUTORIAL" -> "STUDIOWAVE  /  MUSIC GUIDE"
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
                        "DEVICE VIEW" -> DeviceDock(
                            project = project,
                            selectedTrackId = editorTrackId,
                            onSelectTrack = { editorTrackId = it },
                            onProjectChange = { changeProject(it) },
                            onToggle = { selectedWorkspace = "ARRANGEMENT" },
                            fullScreen = true
                        )
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
                        "TUTORIAL" -> StudioWaveTutorialWorkspace(onBack = { selectedWorkspace = "ARRANGEMENT" })
                        "AUDIO EDITOR" -> AudioEditorWorkspace(
                            track = project.tracks.firstOrNull { it.id == editorTrackId }?.takeIf { it.kind == "audio" }
                                ?: project.tracks.firstOrNull { it.kind == "audio" },
                            project = project,
                            onProjectChange = { changeProject(it) },
                            onBack = { selectedWorkspace = "ARRANGEMENT" },
                            selectedClipId = editorClipId,
                            onImportAudio = {
                                pendingAudioTrackId = editorTrackId
                                pendingAudioStartTick = playheadTick
                                returnToAudioEditorAfterImport = true
                                audioPicker.launch(arrayOf("audio/*"))
                            }
                        )
                        else -> ArrangementWorkspace(project, { changeProject(it) }, zoom, { zoom = it.coerceIn(0.1f, 16f) }, scrollTicks, { scrollTicks = it.coerceAtLeast(0.0) }, verticalZoom, { verticalZoom = it.coerceIn(0.65f, 1.8f) }, scrollTracks, { scrollTracks = it.coerceAtLeast(0f) }, { workspace, trackId, clipId ->
                                editorTrackId = trackId
                                editorClipId = clipId
                                selectedWorkspace = workspace
                            },
                            onPlaybackRestart = { updatedProject ->
                                // changeProject() owns hot plan publication for every editor,
                                // including audio import, Piano Roll and Arrangement gestures.
                                // Only replace an initial plan that has not started yet.
                                if (playbackLoading && project != updatedProject) {
                                    restartPendingPlayback?.invoke(updatedProject)
                                }
                            },
                            showDevices = false,
                            onToggleDevices = {},
                            onRequestAudioAtTick = { trackId, tick ->
                                pendingAudioTrackId = trackId
                                pendingAudioStartTick = tick
                            },
                            trackHeights = trackHeightsDp,
                            compactTracks = compactTracks,
                            onScratchTrack = { trackId, deltaMs -> arrangementPlayback.scratchTrack(trackId, deltaMs) },
                            playheadTick = playheadTick,
                            isPlaying = playing || playbackLoading,
                            onSeekTick = { tick -> seekToTick(tick) })
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
private fun TransportBar(
    compact: Boolean = false,
    playing: Boolean, recording: Boolean = false, bpm: Int, master: Float,
    peakDbfs: Float, rmsDbfs: Float, momentaryLufs: Float, peakHistory: List<Float>,
    positionLabel: String, timeSignatureLabel: String, onSettings: () -> Unit, onNewProject: () -> Unit,
    onBpm: (Int) -> Unit, onMaster: (Float) -> Unit, onPlay: () -> Unit, onStop: () -> Unit,
    onUndo: () -> Unit, onRedo: () -> Unit,
    onSaveProject: () -> Unit, onOpenProject: () -> Unit, onRecord: () -> Unit, onExportWav: () -> Unit
) {
    val p = LocalStudioWavePalette.current
    val edge = RoundedCornerShape(2.dp)
    Row(
        Modifier.fillMaxWidth().height(36.dp)
            .background(p.room, edge)
            .border(1.dp, p.border, edge)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Live-style top control bar: compact brand, transport, tempo and position readout.
        Surface(color = Color.Transparent,
            modifier = Modifier.clickable(onClickLabel = "Open Settings", onClick = onSettings)) {
            Text("StudioWave", fontSize = 11.sp, fontWeight = FontWeight.Black, color = p.orange,
                maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp))
        }
        Box(Modifier.width(1.dp).height(19.dp).background(p.border))
        Surface(
            color = if (playing) p.danger.copy(alpha = 0.16f) else p.control,
            shape = edge,
            border = BorderStroke(1.dp, if (playing) p.danger else p.border),
            modifier = Modifier.size(25.dp).clickable(onClick = onStop)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Stop, contentDescription = "Stop playback",
                    tint = if (playing) p.danger else p.muted, modifier = Modifier.size(14.dp))
            }
        }
        Surface(
            color = if (playing) p.accent else p.control,
            shape = edge,
            border = BorderStroke(1.dp, if (playing) p.accent else p.border),
            modifier = Modifier.size(width = 29.dp, height = 25.dp).clickable(onClick = onPlay)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause playback" else "Start playback",
                    tint = if (playing) p.room else p.text, modifier = Modifier.size(16.dp))
            }
        }
        Text("BPM", color = p.muted, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1)
        TransportAction("−") { onBpm(bpm - 1) }
        Surface(color = p.control, shape = edge, border = BorderStroke(1.dp, p.border)) {
            Text("%d".format(bpm), fontSize = 10.sp, fontWeight = FontWeight.Black, color = p.text,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
        }
        TransportAction("+") { onBpm(bpm + 1) }
        Surface(color = p.control, shape = edge, border = BorderStroke(1.dp, p.border)) {
            Text(timeSignatureLabel, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = p.text,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp))
        }
        Surface(color = p.canvas, shape = edge, border = BorderStroke(1.dp, p.border)) {
            Text(positionLabel, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = p.text,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp), maxLines = 1)
        }
        Surface(
            color = if (recording) p.danger else p.control,
            shape = edge,
            border = BorderStroke(1.dp, if (recording) p.danger else p.border),
            modifier = Modifier.size(width = 29.dp, height = 25.dp).clickable(onClick = onRecord)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("REC", color = if (recording) Color.White else p.danger,
                    fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        SmallIconButton(Icons.Default.Undo, p.muted, onUndo)
        SmallIconButton(Icons.Default.Redo, p.muted, onRedo)
        SmallIconButton(Icons.Default.Add, p.accent, onNewProject)
        SmallIconButton(Icons.Default.FolderOpen, p.muted, onOpenProject)
        SmallIconButton(Icons.Default.Save, p.muted, onSaveProject)
        TransportAction("WAV", heightDp = 25, onClick = onExportWav)
        Box(Modifier.width(1.dp).height(19.dp).background(p.border))
        Text("M", color = p.muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
        Slider(value = master, onValueChange = onMaster, modifier = Modifier.width(48.dp).height(24.dp),
            valueRange = 0f..1f, colors = SliderDefaults.colors(
                thumbColor = p.text, activeTrackColor = p.accent,
                inactiveTrackColor = p.control, activeTickColor = p.accent
            ))
        Column(Modifier.width(if (compact) 64.dp else 78.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("PK ${peakDbfs.toInt()}", color = p.text, fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${momentaryLufs.toInt()} LUFS", color = p.accent, fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                drawRect(p.control, size = size)
                val peak = (peakHistory.lastOrNull() ?: 0f).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
                drawRect(p.accent, size = Size(size.width * peak, size.height))
            }
        }
    }
}
@Composable
private fun WorkspaceRail(
    selected: String,
    onSelect: (String) -> Unit
) {
    val p = LocalStudioWavePalette.current
    Column(
        Modifier.width(42.dp).fillMaxHeight().background(p.panel, RoundedCornerShape(2.dp)).padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RailButton("ARR", selected == "ARRANGEMENT", Icons.Default.GraphicEq) { onSelect("ARRANGEMENT") }
        RailButton("MIX", selected == "MIXER", Icons.Default.Tune) { onSelect("MIXER") }
        RailButton("HELP", selected == "TUTORIAL", Icons.Default.LibraryMusic) { onSelect("TUTORIAL") }
        // Browser is intentionally available from the unified bottom action dock instead of a permanent rail slot.
    }
}

@Composable
private fun RailButton(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val p = LocalStudioWavePalette.current
    Surface(
        color = if (selected) p.accent.copy(alpha = 0.14f) else p.panel,
        contentColor = if (selected) p.accent else p.muted,
        shape = RoundedCornerShape(2.dp),
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
        Modifier.fillMaxWidth().height(29.dp).background(p.panel, RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 9.sp, color = LocalStudioWavePalette.current.orange, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text(subtitle, fontSize = 8.sp, color = p.muted, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Surface(color = p.control, shape = RoundedCornerShape(2.dp),
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
    compactTracks: MutableMap<String, Boolean>,
    onPlaybackRestart: (StudioProject) -> Unit = {},
    onScratchTrack: (String, Float) -> Unit = { _, _ -> },
    playheadTick: Long = 0L,
    isPlaying: Boolean = false,
    onRequestAudioAtTick: (String, Long) -> Unit = { _, _ -> },
    showDevices: Boolean = false,
    onToggleDevices: () -> Unit = {},
    onSeekTick: (Long) -> Unit = {}
) {
    val arrangementPalette = LocalStudioWavePalette.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val densityScale = density.density
    val trackIds = project.tracks.map { it.id }
    val trackRowLayout = remember(trackIds, trackHeights.toMap(), compactTracks.toMap()) {
        var top = 0f
        trackIds.map { id ->
            val height = if (compactTracks[id] == true) 30f else (trackHeights[id] ?: 67f).coerceIn(44f, 160f)
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
        if (openEditor || kind == "audio") onOpenEditor(if (kind == "audio") "BROWSER" else if (kind == "drum") "DRUM SEQUENCER" else "PIANO ROLL", newTrack.id, starterClip?.id)
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
        Column(Modifier.fillMaxSize().background(LocalStudioWavePalette.current.room, RoundedCornerShape(2.dp)).border(1.dp, LocalStudioWavePalette.current.border, RoundedCornerShape(2.dp)).padding(1.dp)) {
        Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(94.dp)) {
                Row(Modifier.fillMaxWidth().height(18.dp).background(LocalStudioWavePalette.current.panel), verticalAlignment = Alignment.CenterVertically) {
                    Text("OVERVIEW", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = LocalStudioWavePalette.current.muted, modifier = Modifier.padding(start = 5.dp))
                }
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
                                    onOpenEditor("DEVICE VIEW", track.id, track.clips.minByOrNull { it.startTick }?.id)
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
                                compact = compactTracks[track.id] == true,
                                onToggleCompact = {
                                    compactTracks[track.id] = compactTracks[track.id] != true
                                },
                                onDuplicateTrack = {
                                    val source = project.tracks.firstOrNull { it.id == track.id }
                                    if (source != null) {
                                        val copy = source.copy(
                                            id = java.util.UUID.randomUUID().toString(),
                                            name = (source.name + " Copy").take(48),
                                            clips = source.clips.map { clip -> clip.copy(id = java.util.UUID.randomUUID().toString()) },
                                            muted = false,
                                            solo = false
                                        )
                                        onProjectChange(project.copy(tracks = project.tracks.toMutableList().apply {
                                            add(project.tracks.indexOfFirst { it.id == track.id } + 1, copy)
                                        }))
                                        StudioWaveDiagnostics.log("INFO", "ARRANGEMENT", "Duplicated track '${source.name}' with clips")
                                    }
                                },
                                onDeleteTrack = {
                                    if (project.tracks.size > 1) {
                                        onProjectChange(project.copy(tracks = project.tracks.filterNot { it.id == track.id }))
                                        trackHeights.remove(track.id)
                                        compactTracks.remove(track.id)
                                        StudioWaveDiagnostics.log("INFO", "ARRANGEMENT", "Deleted track '${track.name}'")
                                    } else {
                                        statusMessage = "At least one track must remain in the project."
                                    }
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
                            color = LocalStudioWavePalette.current.control,
                            contentColor = LocalStudioWavePalette.current.text,
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, LocalStudioWavePalette.current.border),
                            shadowElevation = 3.dp,
                            modifier = Modifier.align(Alignment.TopEnd)
                                .offset(y = (addAnchorY + 6f).coerceAtLeast(0f).dp)
                                .padding(end = 5.dp)
                                .height(30.dp).widthIn(min = 66.dp)
                                .clickable {
                                    addTrackMenu = !addTrackMenu
                                    if (addTrackMenu) {
                                        showTimelineMenu = false
                                        showAddTrack = false
                                        clipMenu = null
                                    }
                                }
                        ) {
                            Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                Text(if (addTrackMenu) "× CLOSE" else "Track +", color = LocalStudioWavePalette.current.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (showBottomAddTrack && addTrackMenu) {
                        Surface(
                            color = LocalStudioWavePalette.current.raised,
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, LocalStudioWavePalette.current.border),
                            shadowElevation = 8.dp,
                            modifier = Modifier.align(Alignment.TopEnd)
                                .offset(y = (addAnchorY - 174f).coerceAtLeast(0f).dp)
                                .padding(end = 40.dp)
                        ) {
                            Column(Modifier.width(174.dp).padding(5.dp)) {
                                Text("TRACK +", color = LocalStudioWavePalette.current.orange, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
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
                onTimelineLongPress = { trackId, tick ->
                    val targetTrack = project.tracks.firstOrNull { it.id == trackId }
                    if (targetTrack == null) {
                        showTimelineMenu = true
                        showAddTrack = false
                        addTrackMenu = false
                        clipMenu = null
                    } else if (targetTrack.kind == "audio") {
                        onRequestAudioAtTick(targetTrack.id, tick)
                        onOpenEditor("BROWSER", targetTrack.id, null)
                    } else {
                        val clipTimebase = TimelineTimebase(project.bpm, project.numerator, project.denominator, project.ppq)
                        val rawStart = if (snapEnabled) clipTimebase.snap(tick, snapDivision) else tick.coerceAtLeast(0L)
                        val arrangementEnd = maxArrangementTick(clipTimebase)
                        val start = rawStart.coerceIn(0L, arrangementEnd - 1L)
                        val requestedLength = (clipTimebase.ticksPerBar * 4L).coerceAtLeast(1L)
                        val length = requestedLength.coerceAtMost(arrangementEnd - start)
                        if (targetTrack.clips.size >= StudioProject.MAX_CLIPS_PER_TRACK) {
                            statusMessage = "Clip limit reached for this track"
                        } else {
                            val newClip = if (targetTrack.kind == "drum") {
                                WaveClip(name = "Drum Pattern", startTick = start, lengthTicks = length,
                                    drumSteps = List(16) { false })
                            } else {
                                WaveClip(name = "MIDI Pattern", startTick = start, lengthTicks = length)
                            }
                            onProjectChange(project.copy(tracks = project.tracks.map { track ->
                                if (track.id == targetTrack.id) track.copy(clips = track.clips + newClip) else track
                            }))
                            selectedClip = newClip.id
                            statusMessage = "${targetTrack.kind.uppercase()} clip created"
                        }
                        showTimelineMenu = false
                    }
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
                                    sourceOffsetMicros = if (clip.sourceUri != null) {
                                        if (safeDelta == 0L) clip.sourceOffsetMicros
                                            ?: ArrangementTiming.ticksToMicros(clip.sourceOffsetTicks, project.bpm, project.ppq)
                                        else ArrangementTiming.ticksToMicros(
                                            if (safeDelta > 0L) {
                                                val oldOffset = clip.sourceOffsetTicks.coerceAtLeast(0L)
                                                if (oldOffset > Long.MAX_VALUE - safeDelta) Long.MAX_VALUE else oldOffset + safeDelta
                                            } else {
                                                (clip.sourceOffsetTicks.coerceAtLeast(0L) + safeDelta).coerceAtLeast(0L)
                                            }, project.bpm, project.ppq
                                        )
                                    } else clip.sourceOffsetMicros,
                                    sourceLengthMicros = if (clip.sourceUri != null)
                                        ArrangementTiming.ticksToMicros(safeLength, project.bpm, project.ppq).coerceAtLeast(1L)
                                    else clip.sourceLengthMicros,
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
                .background(LocalStudioWavePalette.current.panel.copy(alpha = 0.98f), RoundedCornerShape(2.dp))
                .border(1.dp, LocalStudioWavePalette.current.border, RoundedCornerShape(2.dp))
                .padding(horizontal = 3.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                FloatingTimelineControl("BROWSER", "Open sample and project browser") {
                    onOpenEditor("BROWSER", null, null)
                }
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
                    .background(arrangementPalette.panel.copy(alpha = 0.98f), RoundedCornerShape(2.dp))
                    .border(1.dp, arrangementPalette.border, RoundedCornerShape(2.dp))
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
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("CLIP CONTENT", color = arrangementPalette.muted, fontSize = 7.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f))
                    Text(when (inspectorTrack.kind) { "audio" -> "SAMPLE"; "drum" -> "STEP GRID"; else -> "MIDI NOTES" },
                        color = arrangementPalette.orange, fontSize = 7.sp, fontWeight = FontWeight.Black)
                }
                if (inspectorTrack.kind == "midi" || inspectorTrack.kind == "drum") {
                    Canvas(Modifier.fillMaxWidth().height(40.dp)
                        .background(arrangementPalette.room, RoundedCornerShape(1.dp))
                        .border(1.dp, arrangementPalette.border.copy(alpha = 0.75f), RoundedCornerShape(1.dp))) {
                        val laneColor = arrangementPalette.border.copy(alpha = 0.5f)
                        for (division in 1..7) {
                            val x = size.width * division / 8f
                            drawLine(laneColor, Offset(x, 0f), Offset(x, size.height), 0.5f)
                        }
                        if (inspectorTrack.kind == "midi") {
                            val duration = inspectorClip.lengthTicks.coerceAtLeast(1L).toDouble()
                            val noteHeight = (size.height / 12f).coerceIn(2f, 5f)
                            inspectorClip.midiNotes.forEach { note ->
                                val start = note.startTick.coerceAtLeast(0L).toDouble()
                                val end = (start + note.durationTicks.coerceAtLeast(1L)).coerceAtMost(duration)
                                if (start < duration && end > start) {
                                    val left = (start / duration * size.width).toFloat().coerceIn(0f, size.width)
                                    val right = (end / duration * size.width).toFloat().coerceIn(left, size.width)
                                    val y = (1f - note.pitch.coerceIn(0, 127) / 127f) * (size.height - noteHeight)
                                    drawRect(arrangementPalette.accent.copy(alpha = 0.92f),
                                        Offset(left, y), androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(1f), noteHeight))
                                }
                            }
                        } else {
                            val steps = inspectorClip.drumSteps
                            if (steps.isNotEmpty()) {
                                val cellWidth = size.width / steps.size
                                steps.forEachIndexed { index, active ->
                                    if (active) {
                                        val cellHeight = size.height * 0.62f
                                        drawRect(arrangementPalette.accent.copy(alpha = 0.92f),
                                            Offset(index * cellWidth + 1f, (size.height - cellHeight) / 2f),
                                            androidx.compose.ui.geometry.Size((cellWidth - 2f).coerceAtLeast(1f), cellHeight))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().background(arrangementPalette.room, RoundedCornerShape(1.dp))
                        .border(1.dp, arrangementPalette.border.copy(alpha = 0.75f), RoundedCornerShape(1.dp))
                        .padding(horizontal = 6.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (inspectorClip.sourceUri != null) "●" else "○",
                            color = if (inspectorClip.sourceUri != null) arrangementPalette.accent else arrangementPalette.muted,
                            fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(5.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(if (inspectorClip.sourceUri != null) "AUDIO SOURCE ATTACHED" else "NO AUDIO SOURCE",
                                color = arrangementPalette.text, fontSize = 7.sp, fontWeight = FontWeight.Black)
                            Text(inspectorClip.sourceUri?.substringAfterLast('/')?.take(30) ?: "Open Sample Editor to import audio",
                                color = arrangementPalette.muted, fontSize = 7.sp, maxLines = 1)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.weight(1f).background(arrangementPalette.room, RoundedCornerShape(2.dp)).padding(5.dp)) {
                        Text("START", color = arrangementPalette.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        Text("BAR $barNumber", color = arrangementPalette.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f).background(arrangementPalette.room, RoundedCornerShape(2.dp)).padding(5.dp)) {
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
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.padding(4.dp).clickable { statusMessage = null }
                    ) {
                        Text(feedback, color = LocalStudioWavePalette.current.text, fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), maxLines = 2)
                    }
                }
            }
            if (showTimelineMenu) Surface(color = LocalStudioWavePalette.current.raised, shape = RoundedCornerShape(2.dp), border = BorderStroke(1.dp, LocalStudioWavePalette.current.border), shadowElevation = 8.dp, modifier = Modifier.padding(6.dp)) {
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
            if (showAddTrack && !addTrackMenu) Surface(color = LocalStudioWavePalette.current.raised, shape = RoundedCornerShape(2.dp), shadowElevation = 8.dp, modifier = Modifier.padding(6.dp)) {
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
            Text("DRAG HORIZONTALLY", color = palette.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(steps) { step, isActive ->
                val isBeat = step % 4 == 0
                val stepInk = if (isActive) palette.room else palette.text
                Surface(
                    color = if (isActive) palette.accent else if (isBeat) palette.control else palette.room,
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(
                        if (isBeat || isActive) 1.5.dp else 1.dp,
                        if (isActive) palette.orange else if (isBeat) palette.muted.copy(alpha = 0.8f)
                        else palette.border.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.width(42.dp).fillMaxHeight().clickable {
                        if (track != null && clip != null) {
                            updateSteps(steps.toMutableList().also { it[step] = !it[step] })
                        }
                    }
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(3.dp)
                                .background(if (isActive) palette.room.copy(alpha = 0.8f)
                                else if (isBeat) palette.accent else palette.border)
                        )
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                (step + 1).toString().padStart(2, '0'),
                                color = stepInk,
                                fontSize = 9.sp,
                                fontWeight = if (isBeat || isActive) FontWeight.Black else FontWeight.Medium
                            )
                        }
                        Text(
                            if (isActive) "ON" else if (isBeat) "BEAT" else "·",
                            color = if (isActive) stepInk else palette.muted,
                            fontSize = 6.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap to toggle a trigger. Beat steps have a stronger marker; swipe horizontally to reach later steps. CLEAR and ALL keep the current pattern length.",
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
    selectedClipId: String? = null,
    onImportAudio: () -> Unit
) {
    val audioPalette = LocalStudioWavePalette.current
    val context = LocalContext.current
    val clip = track?.clips?.firstOrNull { it.id == selectedClipId }
        ?: track?.clips?.firstOrNull { it.sourceUri != null }
    var sourceDurationTicks by remember(clip?.id, clip?.sourceUri, project.bpm, project.ppq) {
        mutableLongStateOf(0L)
    }
    var sourceDurationMicros by remember(clip?.id, clip?.sourceUri) { mutableLongStateOf(0L) }
    var startFraction by remember(clip?.id) { mutableFloatStateOf(0f) }
    var endFraction by remember(clip?.id) { mutableFloatStateOf(1f) }

    // Reconstruct trim handles from persisted source coordinates whenever a clip is opened.
    // Fractions are relative to the original source, not to the already-trimmed clip length.
    LaunchedEffect(clip?.id, clip?.sourceUri, project.bpm, project.ppq) {
        val source = clip?.sourceUri
        sourceDurationTicks = 0L
        sourceDurationMicros = 0L
        if (source.isNullOrBlank()) {
            startFraction = 0f
            endFraction = 1f
            return@LaunchedEffect
        }
        try {
            val durationMs = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(source))
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                } finally {
                    runCatching { retriever.release() }
                }
            }
            sourceDurationMicros = if (durationMs > 0L && durationMs <= Long.MAX_VALUE / 1000L)
                (durationMs * 1000L).coerceAtLeast(0L) else 0L
            val durationTicks = if (durationMs > 0L) {
                kotlin.math.ceil(durationMs.toDouble() * project.bpm.coerceAtLeast(1) *
                    project.ppq.coerceAtLeast(1) / 60_000.0).toLong().coerceAtLeast(1L)
            } else 0L
            sourceDurationTicks = durationTicks
            if (durationTicks > 0L) {
                val restored = audioTrimFractionsFromTicks(
                    sourceOffsetTicks = clip?.sourceOffsetMicros?.let {
                        ArrangementTiming.microsToTicks(it, project.bpm, project.ppq)
                    } ?: (clip?.sourceOffsetTicks ?: 0L),
                    clipLengthTicks = clip?.sourceLengthMicros?.let {
                        ArrangementTiming.microsToTicks(it, project.bpm, project.ppq).coerceAtLeast(1L)
                    } ?: (clip?.lengthTicks ?: 1L),
                    sourceDurationTicks = durationTicks
                )
                startFraction = restored.first
                endFraction = restored.second
            }
        } catch (_: Exception) {
            sourceDurationTicks = 0L
            sourceDurationMicros = 0L
        }
    }

    fun applyTrim(startFractionValue: Float, endFractionValue: Float) {
        val activeTrack = track ?: return
        val activeClip = clip ?: return
        val durationTicks = sourceDurationTicks
        if (durationTicks < 2L) return
        val range = audioTrimRangeFromFractions(
            startFraction = startFractionValue,
            endFraction = endFractionValue,
            sourceDurationTicks = durationTicks
        ) ?: return
        val startTicks = range.startTicks
        val nextLength = range.endTicksExclusive - range.startTicks
        onProjectChange(project.copy(tracks = project.tracks.map { item ->
            if (item.id != activeTrack.id) item else item.copy(clips = item.clips.map { current ->
                if (current.id == activeClip.id) current.copy(
                    // Keep legacy tick fields for old readers, but persist source trim in absolute time.
                    sourceOffsetTicks = startTicks,
                    lengthTicks = nextLength,
                    sourceOffsetMicros = audioTrimMicrosRangeFromFractions(
                        startFractionValue, endFractionValue, sourceDurationMicros
                    )?.startMicros ?: ArrangementTiming.ticksToMicros(startTicks, project.bpm, project.ppq),
                    sourceLengthMicros = audioTrimMicrosRangeFromFractions(
                        startFractionValue, endFractionValue, sourceDurationMicros
                    )?.let { (it.endMicrosExclusive - it.startMicros).coerceAtLeast(1L) }
                        ?: ArrangementTiming.ticksToMicros(nextLength, project.bpm, project.ppq).coerceAtLeast(1L)
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
                WaveformPeakCache.getOrDecode(context, Uri.parse(source), 240) {
                    decodeJob?.ensureActive()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loadError = failure.message ?: "Waveform tidak dapat dibaca."
        } finally {
            loading = false
        }
    }

    // Reveal the decoded source waveform on Compose vsync instead of rebuilding it with a timer.
    // This is presentation-only: the peak values remain the actual decoded source data.
    val waveformReveal by animateFloatAsState(
        targetValue = if (waveform.isNotEmpty()) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "source_waveform_reveal"
    )
    val renderedTrimStart by animateFloatAsState(
        targetValue = startFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 70),
        label = "source_trim_start"
    )
    val renderedTrimEnd by animateFloatAsState(
        targetValue = endFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 70),
        label = "source_trim_end"
    )

    Column(Modifier.fillMaxSize().background(audioPalette.room).padding(7.dp)) {
        WorkspaceTitleBar("CLIP VIEW  /  SAMPLE EDITOR", track?.name ?: "No audio track", onBack)
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (clip?.sourceUri != null) "AUDIO SOURCE" else "NO AUDIO CLIP", color = LocalStudioWavePalette.current.orange, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            SmallAction("IMPORT AUDIO") { onImportAudio() }
            SmallAction("RESET RANGE") {
                if (sourceDurationTicks >= 2L && clip?.sourceUri != null) {
                    startFraction = 0f
                    endFraction = 1f
                    applyTrim(0f, 1f)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("START", color = LocalStudioWavePalette.current.muted, fontSize = 7.sp)
            Slider(value = startFraction, enabled = clip?.sourceUri != null && sourceDurationTicks >= 2L, onValueChange = { value ->
                startFraction = value.coerceIn(0f, (endFraction - 0.01f).coerceAtLeast(0f))
                applyTrim(startFraction, endFraction)
            }, modifier = Modifier.weight(1f).height(20.dp))
            Text("END", color = LocalStudioWavePalette.current.muted, fontSize = 7.sp)
            Slider(value = endFraction, enabled = clip?.sourceUri != null && sourceDurationTicks >= 2L, onValueChange = { value ->
                endFraction = value.coerceIn((startFraction + 0.01f).coerceAtMost(1f), 1f)
                applyTrim(startFraction, endFraction)
            }, modifier = Modifier.weight(1f).height(20.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(audioPalette.canvas, RoundedCornerShape(1.dp))
            .border(1.dp, audioPalette.border.copy(alpha = 0.9f), RoundedCornerShape(1.dp)), contentAlignment = Alignment.Center) {
            if (waveform.isNotEmpty()) {
                Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                    val mid = size.height / 2f
                    val dx = size.width / waveform.size.coerceAtLeast(1)
                    val trimStartX = size.width * renderedTrimStart.coerceIn(0f, 1f)
                    val trimEndX = size.width * renderedTrimEnd.coerceIn(renderedTrimStart, 1f)
                    val revealWidth = size.width * waveformReveal.coerceIn(0f, 1f)
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
                        val x = (i + 0.5f) * dx
                        // Reveal only decoded peaks; never synthesize or pulse fake audio activity.
                        if (x <= revealWidth) {
                            val amplitude = peak.coerceIn(0f, 1f) * size.height * 0.48f
                            val insideRange = x >= trimStartX && x <= trimEndX
                            val waveColor = if (insideRange) audioPalette.accent.copy(alpha = 0.96f)
                                else audioPalette.muted.copy(alpha = 0.62f)
                            drawLine(waveColor, Offset(x, mid - amplitude), Offset(x, mid + amplitude), dx.coerceIn(1f, 3f))
                        }
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
    onResizeTrack: (Float) -> Unit = {}, onScratch: (Float) -> Unit = {}, onReorderTrack: (Float) -> Unit = {},
    compact: Boolean = false, onToggleCompact: () -> Unit = {},
    onDuplicateTrack: () -> Unit = {}, onDeleteTrack: () -> Unit = {}
) {
    val p = LocalStudioWavePalette.current
    var showTrackActions by remember(track.id) { mutableStateOf(false) }
    val accent = Color(track.colorArgb.toInt())
    val labelInk = if (accent.luminance() > 0.52f) Color(0xFF171717) else Color.White
    var resizeDelta by remember(track.id) { mutableFloatStateOf(0f) }
    var reorderDelta by remember(track.id) { mutableFloatStateOf(0f) }
    var scratchDelta by remember(track.id) { mutableFloatStateOf(0f) }

    Row(
        Modifier.fillMaxWidth()
            .offset(y = ((rowTopDp - scrollTracks) * verticalZoom).dp)
            .height((rowHeightDp * verticalZoom).dp)
            .padding(bottom = 1.dp)
            .background(p.panel)
            .border(1.dp, p.border.copy(alpha = 0.92f))
            .padding(end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Compact Live-inspired track-color rail and name strip; no oversized rounded cards.
        Box(
            Modifier.fillMaxHeight().width(3.dp)
                .background(accent)
                .clickable(onClickLabel = "Change track color", onClick = onColorTap)
        )
        Column(
            Modifier.weight(1f).fillMaxHeight()
                .combinedClickable(
                    onClick = onDoubleTap,
                    onDoubleClick = onDoubleTap,
                    onLongClick = onDoubleTap
                )
                .padding(vertical = 3.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .background(p.raised, RoundedCornerShape(1.dp))
                    .border(1.dp, p.border, RoundedCornerShape(1.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("${(index + 1).toString().padStart(2, '0')}  ${track.name.uppercase()}", fontSize = 8.sp, color = p.text, fontWeight = FontWeight.Black, maxLines = 1)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(track.kind.uppercase(), fontSize = 6.sp, color = p.muted, fontWeight = FontWeight.Bold, maxLines = 1)
                if (track.kind == "audio") {
                    Box(
                        Modifier.size(width = 22.dp, height = 16.dp).background(p.control).border(1.dp, p.border)
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
                    ) { Text("SCR", color = p.accent, fontSize = 6.sp, fontWeight = FontWeight.Black) }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (track.muted) p.warning else p.control,
                    shape = RoundedCornerShape(1.dp),
                    border = BorderStroke(1.dp, if (track.muted) p.warning else p.border),
                    modifier = Modifier.size(width = 18.dp, height = 15.dp).clickable(onClickLabel = "Mute track", onClick = onMuteToggle)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("M", color = if (track.muted) Color.Black else p.muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    }
                }
                Surface(
                    color = if (track.solo) p.accent else p.control,
                    shape = RoundedCornerShape(1.dp),
                    border = BorderStroke(1.dp, if (track.solo) p.accent else p.border),
                    modifier = Modifier.size(width = 18.dp, height = 15.dp).clickable(onClickLabel = "Solo track", onClick = onSoloToggle)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("S", color = if (track.solo) labelInk else p.muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box {
                Box(
                    Modifier.size(width = 20.dp, height = 14.dp).background(p.control).border(1.dp, p.border)
                        .clickable { showTrackActions = true },
                    contentAlignment = Alignment.Center
                ) { Text("⋮", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Black) }
                DropdownMenu(expanded = showTrackActions, onDismissRequest = { showTrackActions = false }) {
                    DropdownMenuItem(text = { Text(if (compact) "Maximize track" else "Minimize track") }, onClick = {
                        showTrackActions = false
                        onToggleCompact()
                    })
                    DropdownMenuItem(text = { Text("Duplicate track + clips") }, onClick = {
                        showTrackActions = false
                        onDuplicateTrack()
                    })
                    DropdownMenuItem(text = { Text("Delete track", color = MaterialTheme.colorScheme.error) }, onClick = {
                        showTrackActions = false
                        onDeleteTrack()
                    })
                }
            }
            Box(
                Modifier.size(width = 20.dp, height = 16.dp).background(p.control).border(1.dp, p.border)
                    .pointerInput(track.id, rowTopDp, rowHeightDp) {
                        detectDragGestures(
                            onDragStart = { reorderDelta = 0f },
                            onDrag = { change, amount -> change.consume(); reorderDelta += amount.y },
                            onDragEnd = { onReorderTrack(reorderDelta); reorderDelta = 0f },
                            onDragCancel = { reorderDelta = 0f }
                        )
                    },
                contentAlignment = Alignment.Center
            ) { Text("⠿", color = p.text, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier.size(width = 20.dp, height = 16.dp).background(p.control).border(1.dp, p.border)
                    .pointerInput(track.id, verticalZoom, rowHeightDp) {
                        detectDragGestures(
                            onDragStart = { resizeDelta = 0f },
                            onDrag = { change, amount -> change.consume(); resizeDelta += amount.y },
                            onDragEnd = { onResizeTrack(resizeDelta); resizeDelta = 0f },
                            onDragCancel = { resizeDelta = 0f }
                        )
                    },
                contentAlignment = Alignment.Center
            ) { Text("↕", color = p.muted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
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
    onTimelineLongPress: (String?, Long) -> Unit,
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
    // Compact Live-style Arrangement Overview above the beat ruler.
    val overviewHeightPx = with(density) { 18.dp.toPx() }
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
    val currentOnTimelineLongPress by rememberUpdatedState(onTimelineLongPress)
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
        Canvas(Modifier.fillMaxSize().padding(top = 18.dp).pointerInput(Unit) {
            detectTapGestures(onLongPress = { offset ->
                val yDp = ((offset.y - rulerHeightPx).coerceAtLeast(0f) / density.density /
                    currentVerticalZoom.coerceAtLeast(0.1f)) + currentScrollTracks
                val hitIndex = currentTrackRows.indices.firstOrNull { index ->
                    val row = currentTrackRows[index]
                    yDp >= row.topDp && yDp <= row.topDp + row.heightDp
                }
                val hitTrackId = hitIndex?.let { latestProject.tracks.getOrNull(it)?.id }
                val hitTimebase = TimelineTimebase(latestProject.bpm, latestProject.numerator, latestProject.denominator, latestProject.ppq)
                val hitViewport = TimelineViewport(
                    hitTimebase, baseBarWidthPx * currentZoom, currentScroll, size.width.toFloat(), 0f
                )
                val tick = hitViewport.viewportXToTime(offset.x)
                currentOnTimelineLongPress(hitTrackId, tick)
            })
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

        // Overview navigator: actual clip locations and visible timeline window.
        Canvas(
            Modifier.fillMaxWidth().height(18.dp).background(timelinePalette.panel)
                .pointerInput(project.tracks, scrollTicks, zoom, timebase) {
                    detectTapGestures(onTap = { point ->
                        val maxTick = maxArrangementTick(timebase).coerceAtLeast(1L)
                        val targetTick = (point.x / size.width.coerceAtLeast(1).toDouble() *
                            maxTick.toDouble()).toLong().coerceIn(0L, maxTick)
                        val visible = TimelineViewport(timebase, viewport.effectivePixelsPerBar,
                            scrollTicks, size.width.toFloat(), trackHeaderWidthPx)
                        currentOnScroll(visible.placeTickAtViewportX(targetTick, size.width * 0.5f).scrollTimeTicks)
                    })
                }
        ) {
            val maxTick = maxArrangementTick(timebase).coerceAtLeast(1L)
            val trackCountSafe = project.tracks.size.coerceAtLeast(1)
            project.tracks.forEachIndexed { index, track ->
                val y = size.height * (index + 0.5f) / trackCountSafe
                track.clips.forEach { clip ->
                    val rawLeft = clip.startTick.coerceAtLeast(0L).toDouble() / maxTick * size.width
                    val rawWidth = clip.lengthTicks.coerceAtLeast(1L).toDouble() / maxTick * size.width
                    val left = rawLeft.toFloat().coerceIn(0f, size.width)
                    val width = rawWidth.toFloat().coerceIn(1f, (size.width - left).coerceAtLeast(1f))
                    drawRoundRect(Color((clip.colorArgb ?: track.colorArgb).toInt()).copy(alpha = 0.95f),
                        Offset(left, y - 2f), Size(width, 4f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f))
                }
            }
            val totalBars = (maxTick.toDouble() / timebase.ticksPerBar.toDouble()).toFloat().coerceAtLeast(1f)
            val visibleBars = (size.width / viewport.effectivePixelsPerBar).coerceAtLeast(1f)
            val frameWidth = (visibleBars / totalBars * size.width).coerceIn(8f, size.width)
            val frameLeft = (scrollTicks / timebase.ticksPerBar.toDouble() / totalBars * size.width)
                .toFloat().coerceIn(0f, (size.width - frameWidth).coerceAtLeast(0f))
            drawRect(timelinePalette.accent.copy(alpha = 0.08f), Offset(frameLeft, 0f), Size(frameWidth, size.height))
            drawRect(timelinePalette.accent.copy(alpha = 0.85f), Offset(frameLeft, 0f), Size(frameWidth, size.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
        }

        // Draw ruler labels only for bars that are currently visible.
        Canvas(
            Modifier
                .fillMaxWidth()
                .offset(y = 18.dp)
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
             val labelEveryBar = barWidth >= with(density) { 52.dp.toPx() }
             val playheadBar = playheadTick.coerceAtLeast(0L) / timebase.ticksPerBar.coerceAtLeast(1L)
             for (bar in firstVisibleBar..lastVisibleBar) {
                 val x = rulerViewport.timeToViewportX(bar.toLong() * timebase.ticksPerBar)
                 if (bar % 4 == 0 || labelEveryBar) {
                    rulerPaint.color = when {
                        bar.toLong() == playheadBar -> timelinePalette.orange.toArgb()
                        bar % 4 == 0 -> timelinePalette.text.toArgb()
                        else -> timelinePalette.muted.toArgb()
                    }
                    rulerPaint.typeface = if (bar.toLong() == playheadBar || bar % 4 == 0)
                        android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
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
                drawLine(timelinePalette.orange, Offset(playheadX, 0f), Offset(playheadX, size.height), 1.6f)
                drawCircle(timelinePalette.orange, markerRadius, Offset(playheadX, markerRadius))
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(top = 36.dp)) {
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
                            targetValue = accent.copy(alpha = if (selectedClip == clip.id) 0.30f else 0.18f),
                            animationSpec = tween(durationMillis = 140),
                            label = "clipSelectionFill"
                        )
                        val clipBorderColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (selectedClip == clip.id) timelinePalette.orange
                                else accent.copy(alpha = if (isLightTheme) 0.72f else 0.9f),
                            animationSpec = tween(durationMillis = 120),
                            label = "clipSelectionBorder"
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
                                .background(clipFill, RoundedCornerShape(1.dp))
                                .border(
                                    width = if (selectedClip == clip.id) 1.25.dp else 0.5.dp,
                                    color = clipBorderColor,
                                    shape = RoundedCornerShape(1.dp)
                                )
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
                                    onDoubleClick = {
                                        onSelectClip(clip.id)
                                        onOpenEditor(clip.id)
                                    },
                                    onLongClick = { onSelectClip(clip.id); onClipLongPress(clip.id) }
                                )
                                .padding(4.dp)
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                Column(Modifier.fillMaxSize().padding(4.dp)) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                // Flat title strip and hairline border echo Live's clip headers without obscuring the note/wave content.
                                Row(
                                    Modifier.fillMaxWidth().height(16.dp)
                                        .background(accent.copy(alpha = if (selectedClip == clip.id) 1f else 0.88f))
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        clip.name.uppercase(),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Black,
                                        color = clipInk,
                                        maxLines = 1
                                    )
                                }
                                val clipDetail = arrangementClipSummary(track.kind, clip, project)
                                Text(
                                    clipDetail,
                                    fontSize = 6.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = clipInk.copy(alpha = 0.78f),
                                    maxLines = 1
                                )
                            }
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
                                    val beatCount = kotlin.math.ceil(duration / beatTicks.toDouble()).toInt().coerceIn(1, 256)
                                    // Include the clip's zero point as a real bar line; subsequent
                                    // beat and bar guides use the same clip-relative tick transform.
                                    for (beat in 0 until beatCount) {
                                        val tick = beatTicks * beat.toLong()
                                        val x = (tick.toDouble() / duration * size.width).toFloat()
                                        val isBarLine = beat == 0 || tick % barTicks == 0L
                                        drawLine(
                                            if (isBarLine) accent.copy(alpha = if (isLightTheme) 0.72f else 0.62f)
                                            else clipInk.copy(alpha = if (isLightTheme) 0.20f else 0.18f),
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
                                    ppq = project.ppq,
                                    sourceOffsetMicros = clip.sourceOffsetMicros,
                                    visibleLengthMicros = clip.sourceLengthMicros,
                                    playbackBusy = isPlaying
                                )
                            }
                                }
                                var leftResizeDeltaPx by remember(clip.id) { mutableFloatStateOf(0f) }
                                Box(
                                    Modifier.align(Alignment.CenterStart)
                                        .width(resizeGripWidthDp.dp)
                                        .height(34.dp)
                                        .background(timelinePalette.orange.copy(alpha = 0.28f), RoundedCornerShape(1.dp))
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
                                        .background(timelinePalette.orange.copy(alpha = 0.28f), RoundedCornerShape(1.dp))
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
        Canvas(Modifier.fillMaxSize().padding(top = 36.dp).clipToBounds()) {
            val playheadViewport = TimelineViewport(
                timebase, viewport.effectivePixelsPerBar, scrollTicks.toDouble(), size.width, trackHeaderWidthPx
            )
            val x = playheadViewport.timeToViewportX(playheadTick)
            if (x >= 0f && x <= size.width) {
                drawLine(playheadPalette.orange, Offset(x, 0f), Offset(x, size.height), 1.5f)
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
                    .background(timelinePalette.panel, RoundedCornerShape(1.dp))
                    .border(1.dp, timelinePalette.border, RoundedCornerShape(1.dp))
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
                                shape = RoundedCornerShape(1.dp),
                                border = BorderStroke(1.dp, timelinePalette.border.copy(alpha = 0.75f)),
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
    var selectedNoteIds by remember(track?.id, clip?.id) { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember(track?.id, clip?.id) { mutableStateOf(false) }
    var velocityMode by remember(track?.id, clip?.id) { mutableStateOf(false) }
    var panMode by remember(track?.id, clip?.id) { mutableStateOf(false) }
    var clipboard by remember(track?.id, clip?.id) { mutableStateOf<List<com.studiowave.app.data.MidiNote>>(emptyList()) }
    var selectionDrag by remember(track?.id, clip?.id) { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var editMessage by remember(track?.id, clip?.id) { mutableStateOf("Pinch to zoom · drag empty grid to scroll") }
    var noteContextMenuExpanded by remember(track?.id, clip?.id) { mutableStateOf(false) }

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
    fun quantizeSelectedNotes() {
        val ids = selectedNoteIds + listOfNotNull(selectedNoteId)
        if (ids.isEmpty()) { editMessage = "Select notes first"; return }
        val quantized = notes.map { note ->
            if (note.id !in ids) note else {
                val snappedStart = ((note.startTick + stepTicks / 2L) / stepTicks) * stepTicks
                val snappedEnd = ((note.startTick + note.durationTicks + stepTicks / 2L) / stepTicks) * stepTicks
                val safeStart = snappedStart.coerceAtLeast(0L)
                val safeEnd = maxOf(safeStart + stepTicks, snappedEnd)
                note.copy(startTick = safeStart, durationTicks = (safeEnd - safeStart).coerceAtLeast(stepTicks))
            }
        }
        updateNotes(quantized)
        editMessage = "Quantized ${ids.size} note(s) to 1/$snapDenominator"
    }

    fun snapStep(raw: Int): Int = (raw / 1).coerceAtLeast(0)
    fun pitchLabel(pitch: Int): String {
        val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
        return names[Math.floorMod(pitch, 12)] + (pitch / 12 - 1)
    }

    Column(Modifier.fillMaxSize().background(pianoPalette.room).border(1.dp, pianoPalette.border, RoundedCornerShape(1.dp)).padding(4.dp)) {
        // Clip identity and editing actions share one shallow strip so the note grid wins height.
        Row(
            Modifier.fillMaxWidth().height(42.dp).horizontalScroll(rememberScrollState())
                .background(pianoPalette.panel, RoundedCornerShape(1.dp))
                .border(1.dp, pianoPalette.border, RoundedCornerShape(1.dp))
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MIDI", color = pianoPalette.orange, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Text(clip?.name ?: "NO CLIP", color = pianoPalette.text, fontSize = 8.sp, maxLines = 1,
                modifier = Modifier.widthIn(max = 90.dp))
            Spacer(Modifier.width(4.dp))
            SmallAction("×", minHeight = 32) { onBack() }
            SmallAction(if (keyboardVisible) "NOTES" else "VEL LANE", minHeight = 32) {
                keyboardVisible = !keyboardVisible
                velocityMode = !keyboardVisible
                panMode = false
            }
            SmallAction(if (virtualKeyboardVisible) "INPUT ON" else "INPUT", minHeight = 32) { virtualKeyboardVisible = !virtualKeyboardVisible }
            SmallAction("1/${snapDenominator}", minHeight = 32) {
                snapDenominator = when (snapDenominator) { 32 -> 16; 16 -> 8; 8 -> 4; 4 -> 2; else -> 32 }
            }
            TransportAction("−", heightDp = 32) {
                val nextZoom = (zoomX / 1.25f).coerceIn(0.35f, 8f)
                val oldVisible = pianoRollVisibleSteps(stepsPerBar, zoomX)
                val newVisible = pianoRollVisibleSteps(stepsPerBar, nextZoom)
                val anchor = pageStartStep + oldVisible / 2
                zoomX = nextZoom
                startStep = (anchor - newVisible / 2).coerceIn(0, (clipLengthSteps - newVisible).coerceAtLeast(0))
            }
            Text("BAR ${pageStartStep / stepsPerBar + 1}", color = pianoPalette.accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            TransportAction("+", heightDp = 32) {
                val nextZoom = (zoomX * 1.25f).coerceIn(0.35f, 8f)
                val oldVisible = pianoRollVisibleSteps(stepsPerBar, zoomX)
                val newVisible = pianoRollVisibleSteps(stepsPerBar, nextZoom)
                val anchor = pageStartStep + oldVisible / 2
                zoomX = nextZoom
                startStep = (anchor - newVisible / 2).coerceIn(0, (clipLengthSteps - newVisible).coerceAtLeast(0))
            }
            TransportAction("P−", heightDp = 32) { zoomY = (zoomY / 1.2f).coerceIn(0.45f, 3.5f) }
            TransportAction("P+", heightDp = 32) { zoomY = (zoomY * 1.2f).coerceIn(0.45f, 3.5f) }
            Box {
                SmallAction("⋮", minHeight = 32) { noteContextMenuExpanded = true }
                DropdownMenu(
                    expanded = noteContextMenuExpanded,
                    onDismissRequest = { noteContextMenuExpanded = false },
                    modifier = Modifier.background(pianoPalette.panel)
                ) {
                    DropdownMenuItem(text = { Text("Edit tools", color = pianoPalette.text) }, onClick = {
                        editToolsExpanded = !editToolsExpanded; noteContextMenuExpanded = false
                    })
                    DropdownMenuItem(text = { Text(if (selectionMode) "Exit selection" else "Select notes", color = pianoPalette.text) }, onClick = {
                        selectionMode = !selectionMode; noteContextMenuExpanded = false
                    })
                    DropdownMenuItem(text = { Text(if (panMode) "Exit pan lane" else "Pan lane", color = pianoPalette.text) }, onClick = {
                        panMode = !panMode; if (panMode) velocityMode = false; noteContextMenuExpanded = false
                    })
                    DropdownMenuItem(text = { Text("Octave down", color = pianoPalette.text) }, onClick = {
                        val ids = selectedNoteIds + listOfNotNull(selectedNoteId)
                        if (ids.isNotEmpty()) updateNotes(notes.map { n -> if (n.id in ids) n.copy(pitch = (n.pitch - 12).coerceAtLeast(0)) else n })
                        else editMessage = "Select notes first"
                        noteContextMenuExpanded = false
                    })
                    DropdownMenuItem(text = { Text("Octave up", color = pianoPalette.text) }, onClick = {
                        val ids = selectedNoteIds + listOfNotNull(selectedNoteId)
                        if (ids.isNotEmpty()) updateNotes(notes.map { n -> if (n.id in ids) n.copy(pitch = (n.pitch + 12).coerceAtMost(127)) else n })
                        else editMessage = "Select notes first"
                        noteContextMenuExpanded = false
                    })
                    val selectedIds = selectedNoteIds + listOfNotNull(selectedNoteId)
                    val selectedNotes = notes.filter { it.id in selectedIds }
                    DropdownMenuItem(text = { Text("Copy selected notes", color = pianoPalette.text) }, enabled = selectedNotes.isNotEmpty(), onClick = {
                        val anchorTick = selectedNotes.minOfOrNull { it.startTick } ?: 0L
                        val anchorPitch = selectedNotes.minOfOrNull { it.pitch } ?: 0
                        clipboard = selectedNotes.map { note -> note.copy(startTick = (note.startTick - anchorTick).coerceAtLeast(0L), pitch = note.pitch - anchorPitch) }
                        editMessage = "${selectedNotes.size} note(s) copied"; noteContextMenuExpanded = false
                    })
                    DropdownMenuItem(text = { Text("Duplicate selected notes", color = pianoPalette.text) }, enabled = selectedNotes.isNotEmpty() && clip != null, onClick = {
                        val activeClip = clip
                        if (activeClip != null && selectedNotes.isNotEmpty()) {
                            val anchorTick = selectedNotes.minOf { it.startTick }
                            val endTick = selectedNotes.maxOf { it.startTick + it.durationTicks }
                            val span = (endTick - anchorTick).coerceAtLeast(stepTicks)
                            val duplicates = if (endTick <= activeClip.lengthTicks - span) selectedNotes.map { it.copy(id = java.util.UUID.randomUUID().toString(), startTick = it.startTick + span) } else emptyList()
                            if (duplicates.isNotEmpty()) {
                                updateNotes(notes + duplicates); selectedNoteIds = duplicates.map { it.id }.toSet()
                                selectedNoteId = duplicates.first().id; editMessage = "${duplicates.size} note(s) duplicated"
                            } else editMessage = "Not enough clip space to duplicate selection"
                        }
                        noteContextMenuExpanded = false
                    })
                    DropdownMenuItem(text = { Text("Delete selected notes", color = pianoPalette.text) }, enabled = selectedIds.isNotEmpty(), onClick = {
                        updateNotes(notes.filterNot { it.id in selectedIds }); selectedNoteId = null; selectedNoteIds = emptySet()
                        editMessage = "${selectedIds.size} note(s) deleted"; noteContextMenuExpanded = false
                    })
                    DropdownMenuItem(text = { Text("Select all notes", color = pianoPalette.text) }, enabled = notes.isNotEmpty(), onClick = {
                        selectedNoteIds = notes.map { it.id }.toSet(); selectedNoteId = notes.firstOrNull()?.id
                        editMessage = "${notes.size} note(s) selected"; noteContextMenuExpanded = false
                    })
                    DropdownMenuItem(text = { Text("Clear selection", color = pianoPalette.text) }, enabled = selectedIds.isNotEmpty(), onClick = {
                        selectedNoteId = null; selectedNoteIds = emptySet(); editMessage = "Selection cleared"; noteContextMenuExpanded = false
                    })
                }
            }

        }
        if (editToolsExpanded) {
            Row(
                Modifier.fillMaxWidth().height(26.dp)
                    .background(pianoPalette.panel, RoundedCornerShape(1.dp))
                    .border(1.dp, pianoPalette.border.copy(alpha = 0.8f), RoundedCornerShape(1.dp))
                    .padding(horizontal = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallAction("COPY") {
                    val selected = notes.filter { it.id in selectedNoteIds || it.id == selectedNoteId }
                    if (selected.isNotEmpty()) {
                        val anchorTick = selected.minOf { it.startTick }
                        val anchorPitch = selected.minOf { it.pitch }
                        clipboard = selected.map { note ->
                            note.copy(startTick = (note.startTick - anchorTick).coerceAtLeast(0L), pitch = note.pitch - anchorPitch)
                        }
                        editMessage = "${selected.size} note(s) copied"
                    } else {
                        clipboard = emptyList()
                        editMessage = "Select notes first"
                    }
                }
                SmallAction("DEL") {
                    val ids = selectedNoteIds + listOfNotNull(selectedNoteId)
                    if (ids.isNotEmpty()) {
                        updateNotes(notes.filterNot { it.id in ids })
                        selectedNoteId = null
                        selectedNoteIds = emptySet()
                        editMessage = "${ids.size} note(s) deleted"
                    } else editMessage = "Select a note first"
                }
                SmallAction("DUP") {
                    val selected = notes.filter { it.id in selectedNoteIds || it.id == selectedNoteId }
                    if (selected.isEmpty()) {
                        editMessage = "Select notes first"
                    } else {
                        val anchorTick = selected.minOf { it.startTick }
                        val endTick = selected.maxOf { it.startTick + it.durationTicks }
                        val span = (endTick - anchorTick).coerceAtLeast(stepTicks)
                        if (endTick + span > (clip?.lengthTicks ?: 0L)) {
                            editMessage = "Not enough clip space to duplicate selection"
                        } else {
                            val duplicates = selected.map { note ->
                                note.copy(id = java.util.UUID.randomUUID().toString(), startTick = note.startTick + span)
                            }
                            updateNotes(notes + duplicates)
                            selectedNoteIds = duplicates.map { it.id }.toSet()
                            selectedNoteId = duplicates.firstOrNull()?.id
                            editMessage = "${duplicates.size} note(s) duplicated"
                        }
                    }
                }
                SmallAction("SNAP") {
                    quantizeSelectedNotes()
                }
                SmallAction("LEGATO") {
                    val selected = notes.filter { it.id in selectedNoteIds || it.id == selectedNoteId }
                    if (selected.size >= 2) {
                        val ordered = selected.sortedBy { it.startTick }
                        val legato = ordered.mapIndexed { index, note ->
                            val nextStart = ordered.getOrNull(index + 1)?.startTick
                                ?: (note.startTick + note.durationTicks)
                            note.copy(durationTicks = (nextStart - note.startTick).coerceAtLeast(stepTicks))
                        }
                        val byId = legato.associateBy { it.id }
                        updateNotes(notes.map { byId[it.id] ?: it })
                        editMessage = "Legato applied to ${legato.size} notes"
                    } else editMessage = "Select at least two notes"
                }
                SmallAction("OCT−") {
                    val ids = selectedNoteIds + listOfNotNull(selectedNoteId)
                    if (ids.isNotEmpty()) {
                        updateNotes(notes.map { note -> if (note.id in ids) note.copy(pitch = (note.pitch - 12).coerceAtLeast(0)) else note })
                        editMessage = "Selection down one octave"
                    } else editMessage = "Select notes first"
                }
                SmallAction("OCT+") {
                    val ids = selectedNoteIds + listOfNotNull(selectedNoteId)
                    if (ids.isNotEmpty()) {
                        updateNotes(notes.map { note -> if (note.id in ids) note.copy(pitch = (note.pitch + 12).coerceAtMost(127)) else note })
                        editMessage = "Selection up one octave"
                    } else editMessage = "Select notes first"
                }
                SmallAction("SCALE") {
                    val ids = selectedNoteIds + listOfNotNull(selectedNoteId)
                    if (ids.isEmpty()) {
                        editMessage = "Select notes first"
                    } else {
                        val allowed = setOf(0, 2, 4, 5, 7, 9, 11)
                        val changes = notes.filter { it.id in ids }.associate { note ->
                            val nearest = (0..127).filter { Math.floorMod(it, 12) in allowed }
                                .minByOrNull { kotlin.math.abs(it - note.pitch) } ?: note.pitch
                            note.id to note.copy(pitch = nearest)
                        }
                        updateNotes(notes.map { changes[it.id] ?: it })
                        editMessage = "Selection snapped to C major"
                    }
                }
                SmallAction("−1") {
                    val ids = selectedNoteIds + listOfNotNull(selectedNoteId)
                    if (ids.isEmpty()) editMessage = "Select notes first"
                    else {
                        updateNotes(notes.map { note -> if (note.id in ids) note.copy(pitch = (note.pitch - 1).coerceAtLeast(0)) else note })
                        editMessage = "Selection −1 semitone"
                    }
                }
                SmallAction("+1") {
                    val ids = selectedNoteIds + listOfNotNull(selectedNoteId)
                    if (ids.isEmpty()) editMessage = "Select notes first"
                    else {
                        updateNotes(notes.map { note -> if (note.id in ids) note.copy(pitch = (note.pitch + 1).coerceAtMost(127)) else note })
                        editMessage = "Selection +1 semitone"
                    }
                }
                SmallAction("PASTE") {
                    val source = clipboard
                    if (source.isEmpty()) {
                        editMessage = "Copy notes first"
                    } else if (clip == null || track == null) {
                        editMessage = "Create a MIDI clip first"
                    } else {
                        val anchor = notes.firstOrNull { it.id == selectedNoteId }
                        val pasteOrigin = ((anchor?.startTick ?: pageStartStep.toLong() * stepTicks) + stepTicks)
                            .coerceIn(0L, (clip.lengthTicks - 1L).coerceAtLeast(0L))
                        val basePitch = anchor?.pitch ?: 60
                        val candidates = source.map { note ->
                            note.copy(
                                id = java.util.UUID.randomUUID().toString(),
                                startTick = (pasteOrigin + note.startTick).coerceAtMost(clip.lengthTicks),
                                pitch = (basePitch + note.pitch).coerceIn(0, 127)
                            )
                        }
                        val pasted = fitMidiNotesToClip(candidates, clip.lengthTicks)
                        if (pasted.isEmpty()) {
                            editMessage = "Notes do not fit in this clip"
                        } else {
                            updateNotes(notes + pasted)
                            selectedNoteIds = pasted.map { it.id }.toSet()
                            selectedNoteId = pasted.first().id
                            editMessage = "${pasted.size} note(s) pasted"
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
                                            val newSteps = pianoRollVisibleSteps(stepsPerBar, newZoomX)
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
                                    val currentStepsForGesture = pianoRollVisibleSteps(stepsPerBar, gestureZoomX.value)
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
                                    val noteHitSlop = with(pianoDensity) { 8.dp.toPx() }
                                    val touchedNote = if (keyboardVisible && !velocityMode && !panMode) notesAtDown.firstOrNull { note ->
                                        val noteLeftPx = ((note.startTick.toDouble() / stepTicks.toDouble()) - visibleStartStep) * gestureColWidth
                                        val noteRightPx = (((note.startTick + note.durationTicks.coerceAtLeast(1L)).toDouble() / stepTicks.toDouble()) - visibleStartStep) * gestureColWidth
                                        val noteCenterY = (currentTopPitch - note.pitch + 0.5f) * gestureRowHeight
                                        down.position.x >= noteLeftPx - noteHitSlop &&
                                            down.position.x <= noteRightPx + noteHitSlop &&
                                            kotlin.math.abs(down.position.y - noteCenterY) <= gestureRowHeight * 0.65f
                                    } else null
                                    val noteRightEdgePx = touchedNote?.let { note ->
                                        (((note.startTick + note.durationTicks.coerceAtLeast(1L)).toDouble() /
                                            stepTicks.toDouble()) - visibleStartStep.toDouble()) * gestureColWidth
                                    }
                                    // Prefer the note's trailing edge; use a generous but bounded touch zone
                                    // so resizing works on phone screens while ordinary note drags remain moves.
                                    val resizeSlopPx = maxOf(with(pianoDensity) { 12.dp.toPx() }, (gestureColWidth * 0.34f).coerceAtMost(with(pianoDensity) { 30.dp.toPx() }))
                                    val resizeNote = touchedNote != null && noteRightEdgePx != null &&
                                        noteRightEdgePx >= 0f &&
                                        down.position.x >= noteRightEdgePx - resizeSlopPx &&
                                        down.position.x <= noteRightEdgePx + resizeSlopPx
                                    if (selectionMode && keyboardVisible) {
                                        val origin = down.position
                                        var current = origin
                                        do {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (change.pressed) {
                                                current = change.position
                                                if ((current - origin).getDistance() > with(pianoDensity) { 8.dp.toPx() }) {
                                                    selectionDrag = origin to current
                                                    val left = minOf(origin.x, current.x)
                                                    val right = maxOf(origin.x, current.x)
                                                    val top = minOf(origin.y, current.y)
                                                    val bottom = maxOf(origin.y, current.y)
                                                    val leftStep = visibleStartStep + (left / gestureColWidth).toInt()
                                                    val rightStep = visibleStartStep + kotlin.math.ceil(right / gestureColWidth).toInt()
                                                    val highPitch = (currentTopPitch - (top / gestureRowHeight).toInt()).coerceIn(0,127)
                                                    val lowPitch = (currentTopPitch - (bottom / gestureRowHeight).toInt()).coerceIn(0,127)
                                                    selectedNoteIds = notesAtDown.filter { note ->
                                                        val noteStart = note.startTick / stepTicks
                                                        val noteEnd = kotlin.math.ceil((note.startTick + note.durationTicks).toDouble() / stepTicks).toLong()
                                                        noteEnd > leftStep && noteStart < rightStep && note.pitch in lowPitch..highPitch
                                                    }.map { it.id }.toSet()
                                                    change.consume()
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })
                                        selectionDrag = null
                                        if (selectedNoteIds.isNotEmpty()) {
                                            selectedNoteId = selectedNoteIds.first()
                                            editMessage = "${selectedNoteIds.size} notes selected"
                                        } else editMessage = "No notes inside selection"
                                        return@awaitEachGesture
                                    }
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
                                                if (keyboardVisible && touchedNote == null && !velocityMode && !panMode) {
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
                                    val meaningfulDrag = accumulatedDelta.getDistance() > with(pianoDensity) { 3.dp.toPx() }
                                    if ((velocityMode || panMode) && keyboardVisible && track != null && clip != null && meaningfulDrag) {
                                        val targetTick = clickedStep.toDouble() * stepTicks.toDouble()
                                        val nearest = latestNotes.minByOrNull { note -> kotlin.math.abs(note.startTick.toDouble() - targetTick) }
                                        if (nearest != null) {
                                            selectedNoteId = nearest.id
                                            if (panMode) {
                                                val pan = ((finalPosition.x / viewportWidthPx.coerceAtLeast(1f)) * 2f - 1f)
                                                    .coerceIn(-1f, 1f)
                                                gestureNoteWriter.value(latestNotes.map { note ->
                                                    if (note.id == nearest.id) note.copy(pan = pan) else note
                                                })
                                                editMessage = "Note pan " + (if (pan < -0.01f) "L" else if (pan > 0.01f) "R" else "C") + " · " + String.format(java.util.Locale.US, "%.2f", pan)
                                            } else {
                                                val velocity = (127f * (1f - finalPosition.y / viewportHeightPx.coerceAtLeast(1f))).roundToInt().coerceIn(1,127)
                                                gestureNoteWriter.value(latestNotes.map { note -> if (note.id == nearest.id) note.copy(velocity = velocity) else note })
                                                editMessage = "Velocity $velocity"
                                            }
                                        }
                                    } else if (touchedNote != null && keyboardVisible && track != null && clip != null) {
                                        selectedNoteId = touchedNote.id
                                        selectedNoteIds = if (selectionMode) selectedNoteIds + touchedNote.id else setOf(touchedNote.id)
                                        if (dragged && !selectionMode) {
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
                                    } else if (!dragged && track != null && clip != null && keyboardVisible && !selectionMode) {
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
                            // Alternate octave/semitone lanes like a piano-keybed editor: black-key
                            // pitches receive a recessed graphite wash, while each C row gets a
                            // faint octave landmark. These are paint-only overlays; note hit-testing
                            // and musical coordinate mapping remain unchanged.
                            for (row in 0..rowsVisible) {
                                val pitch = currentTopPitch - row
                                val pitchClass = Math.floorMod(pitch, 12)
                                val isBlackKey = pitchClass == 1 || pitchClass == 3 || pitchClass == 6 || pitchClass == 8 || pitchClass == 10
                                val isC = pitch >= 0 && pitchClass == 0
                                val top = row * localRowHeight
                                if (row < rowsVisible) {
                                    if (isBlackKey) {
                                        drawRect(
                                            pianoPalette.room.copy(alpha = 0.42f * pianoGridOpacity),
                                            Offset(0f, top),
                                            Size(size.width, localRowHeight)
                                        )
                                    } else if ((pitchClass == 2 || pitchClass == 4 || pitchClass == 7 || pitchClass == 9 || pitchClass == 11)) {
                                        drawRect(
                                            pianoPalette.canvas.copy(alpha = 0.18f * pianoGridOpacity),
                                            Offset(0f, top),
                                            Size(size.width, localRowHeight)
                                        )
                                    }
                                    if (isC) {
                                        drawRect(
                                            pianoPalette.orange.copy(alpha = 0.035f * pianoGridOpacity),
                                            Offset(0f, top),
                                            Size(size.width, localRowHeight)
                                        )
                                    }
                                }
                                drawLine(
                                    if (isC) pianoPalette.border.copy(alpha = 0.88f * pianoGridOpacity)
                                    else pianoPalette.grid.copy(alpha = pianoGridOpacity),
                                    Offset(0f, top), Offset(size.width, top),
                                    if (isC) 1.25f else 0.5f
                                )
                            }
                            // Slight alternating bar tint establishes phrase grouping without
                            // competing with note bodies or beat/subdivision lines.
                            for (barIndex in 0..(currentStepsOnScreen / stepsPerBar + 1)) {
                                val barStartStep = barIndex * stepsPerBar
                                val absoluteBar = pageStartStep / stepsPerBar + barIndex
                                if (absoluteBar % 2 == 1) {
                                    val visibleStart = (barStartStep - (pageStartStep % stepsPerBar)).toFloat() * localColWidth
                                    val width = stepsPerBar * localColWidth
                                    if (visibleStart < size.width && visibleStart + width > 0f) {
                                        drawRect(
                                            pianoPalette.room.copy(alpha = 0.055f * pianoGridOpacity),
                                            Offset(visibleStart.coerceAtLeast(0f), 0f),
                                            Size((minOf(size.width, visibleStart + width) - visibleStart.coerceAtLeast(0f)).coerceAtLeast(0f), size.height)
                                        )
                                    }
                                }
                            }
                            for (col in 0..currentStepsOnScreen) {
                                val stepAbsolute = pageStartStep + col
                                val bar = stepAbsolute % stepsPerBar == 0
                                val beat = stepAbsolute % stepsPerBeat == 0
                                drawLine(
                                    when {
                                        bar -> pianoPalette.accent.copy(alpha = 0.72f * pianoGridOpacity)
                                        beat -> pianoPalette.border.copy(alpha = 0.88f * pianoGridOpacity)
                                        else -> pianoPalette.grid.copy(alpha = pianoGridOpacity * 0.72f)
                                    },
                                    Offset(col * localColWidth, 0f), Offset(col * localColWidth, size.height),
                                    when { bar -> 1.35f; beat -> 0.9f; else -> 0.45f }
                                )
                            }
                            selectionDrag?.let { (origin, current) ->
                                val left = minOf(origin.x, current.x).coerceIn(0f, size.width)
                                val top = minOf(origin.y, current.y).coerceIn(0f, size.height)
                                val right = maxOf(origin.x, current.x).coerceIn(0f, size.width)
                                val bottom = maxOf(origin.y, current.y).coerceIn(0f, size.height)
                                val marqueeSize = Size((right-left).coerceAtLeast(1f), (bottom-top).coerceAtLeast(1f))
                                drawRect(pianoPalette.orange.copy(alpha=0.16f), Offset(left,top), marqueeSize)
                                drawRect(pianoPalette.orange, Offset(left,top), marqueeSize,
                                    style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.5f))
                            }
                            notes.forEach { note ->
                                val x = (note.startTick.toDouble() / stepTicks.toDouble() - pageStartStep) * localColWidth
                                val row = currentTopPitch - note.pitch
                                val y = row * localRowHeight
                                val width = (note.durationTicks.toDouble() / stepTicks.toDouble() * localColWidth).toFloat().coerceAtLeast(3f)
                                if (note.pitch in 0..127 && x < size.width && x + width > 0 && y + localRowHeight > 0 && y < size.height) {
                                    val noteHeight = (localRowHeight - 2f).coerceAtLeast(2f)
                                    val selected = note.id == selectedNoteId || note.id in selectedNoteIds
                                    val velocityAlpha = (0.52f + note.velocity.coerceIn(1, 127) / 270f).coerceIn(0.52f, 0.98f)
                                    val noteColor = if (selected) pianoPalette.orange else pianoPalette.accent.copy(alpha = velocityAlpha)
                                    // Live-inspired MIDI notes use a flat rectangular body. Velocity
                                    // changes opacity, while selection remains clearly orange.
                                    drawRect(noteColor, Offset(x.toFloat(), y + 1f), Size(width, noteHeight))
                                    drawLine(Color.White.copy(alpha = if (selected) 0.95f else 0.62f),
                                        Offset(x.toFloat(), y + 1f), Offset(x.toFloat() + width, y + 1f), 1f)
                                    drawRect(Color.White.copy(alpha = if (selected) 0.95f else 0.72f),
                                        Offset(x.toFloat() + width - 2f, y + 1f), Size(2f, noteHeight))
                                    // Selected notes expose a trailing grip; the touch hit-zone remains wider.
                                    if (selected && width >= 8f && noteHeight >= 7f) {
                                        drawLine(Color.White.copy(alpha = 0.95f),
                                            Offset(x.toFloat() + width - 5f, y + 3f),
                                            Offset(x.toFloat() + width - 5f, y + noteHeight - 3f), 1.2f)
                                    }
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
                Modifier.fillMaxWidth().height(44.dp).background(pianoPalette.panel, RoundedCornerShape(1.dp)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                keyboardNotes.forEach { (label, pitch) ->
                    Surface(
                        color = pianoPalette.control,
                        shape = RoundedCornerShape(1.dp),
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
        Modifier.fillMaxSize().background(mixerPalette.room, RoundedCornerShape(1.dp)).border(1.dp, mixerPalette.border.copy(alpha = 0.85f), RoundedCornerShape(1.dp)).padding(4.dp)
    ) {
        // Stable Live-inspired channel strips; larger sessions scroll horizontally rather than shrinking controls.
        val channelWidth = 184.dp
        Row(
            Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
        project.tracks.forEachIndexed { trackIndex, track ->
            Column(
                Modifier.width(channelWidth).fillMaxHeight()
                    .background(mixerPalette.panel, RoundedCornerShape(1.dp))
                    .border(1.dp, mixerPalette.border.copy(alpha = 0.95f), RoundedCornerShape(1.dp))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth().height(34.dp)
                        .background(mixerPalette.canvas, RoundedCornerShape(1.dp))
                        .border(1.dp, mixerPalette.border.copy(alpha = 0.95f), RoundedCornerShape(1.dp))
                        .padding(horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Keep track identity visible without turning the entire header into a saturated block.
                    // This mirrors the restrained Live-style surface hierarchy while preserving track colors.
                    Box(
                        Modifier.width(3.dp).fillMaxHeight()
                            .background(Color(track.colorArgb.toInt()))
                    )
                    Text("%02d".format(trackIndex + 1), fontSize = 7.sp, fontWeight = FontWeight.Black,
                        color = Color(track.colorArgb.toInt()), maxLines = 1)
                    Text(track.name, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1,
                        color = mixerPalette.text,
                        modifier = Modifier.weight(1f))
                    Text(track.kind.uppercase(), fontSize = 6.sp, fontWeight = FontWeight.Bold,
                        color = mixerPalette.muted,
                        maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
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
                            val meter = trackMeterReadings[track.id]
                            val targetPeak = (if (channel == 0) meter?.peakLeft else meter?.peakRight)
                                ?.coerceIn(0f, 1f) ?: 0f
                            val targetRms = (if (channel == 0) meter?.rmsLeft else meter?.rmsRight)
                                ?.coerceIn(0f, 1f) ?: 0f
                            // Smooth the meter's visual response without touching the audio signal.
                            val animatedPeak by animateFloatAsState(
                                targetValue = targetPeak,
                                animationSpec = tween(durationMillis = 85),
                                label = "mixer_peak_${track.id}_$channel"
                            )
                            val animatedRms by animateFloatAsState(
                                targetValue = targetRms,
                                animationSpec = tween(durationMillis = 100),
                                label = "mixer_rms_${track.id}_$channel"
                            )
                            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                                val lane = (size.width * 0.76f).coerceAtLeast(2f)
                                for (segment in 0 until 24) {
                                    val segmentHeight = size.height / 24f
                                    val y = size.height - (segment + 1) * segmentHeight
                                    val active = (segment + 1) / 24f <= animatedPeak
                                    val color = when {
                                        !active -> mixerPalette.room
                                        segment >= 21 -> mixerPalette.meterHigh
                                        segment >= 17 -> mixerPalette.meterMid
                                        else -> mixerPalette.meterLow
                                    }
                                    drawRoundRect(color, Offset((size.width - lane) / 2f, y + 0.5f),
                                        Size(lane, (segmentHeight - 1f).coerceAtLeast(1f)),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.7f))
                                }
                                drawLine(mixerPalette.text.copy(alpha = 0.84f),
                                    Offset((size.width - lane) / 2f, size.height * (1f - animatedRms).coerceIn(0f, 1f)),
                                    Offset((size.width + lane) / 2f, size.height * (1f - animatedRms).coerceIn(0f, 1f)), 1f)
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
                                        val value = mixerFaderGainAtFraction(1f - start.y / height, 1.5f)
                                        val current = latestProject
                                        onProjectChange(current.copy(tracks = current.tracks.map {
                                            if (it.id == track.id) it.copy(volume = value) else it
                                        }))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val height = size.height.coerceAtLeast(1).toFloat()
                                        val value = mixerFaderGainAtFraction(1f - change.position.y / height, 1.5f)
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
                                    val value = mixerFaderGainAtFraction(1f - position.y / height, 1.5f)
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
                        val channelMaxDb = 20.0 * kotlin.math.log10(1.5)
                val channelMinDb = minOf(-60.0, channelMaxDb - 60.0)
                listOf(3.5, 0.0, -6.0, -12.0, -24.0, -48.0).forEach { db ->
                    if (db in channelMinDb..channelMaxDb) {
                        val fraction = ((db - channelMinDb) / (channelMaxDb - channelMinDb)).toFloat()
                        val y = railBottom - fraction * (railBottom - railTop)
                        val major = db == 0.0 || db == -12.0 || db == -24.0
                        drawLine(mixerPalette.grid.copy(alpha = if (major) 0.95f else 0.65f),
                            Offset(railLeft - if (major) 8f else 5f, y), Offset(railLeft - 1f, y),
                            if (major) 1.3f else 0.8f)
                    }
                }
                        val fraction = mixerFaderFractionForGain(track.volume, 1.5f)
                        val thumbY = railBottom - fraction * (railBottom - railTop)
                        val capLeft = size.width * 0.08f
                        val capWidth = size.width * 0.84f
                        drawRoundRect(mixerPalette.control, Offset(capLeft, thumbY - 9f),
                            Size(capWidth, 18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f))
                        drawRoundRect(mixerPalette.border, Offset(capLeft, thumbY - 9f),
                            Size(capWidth, 18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f),
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
                    modifier = Modifier.size(76.dp)
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
            Modifier.width(164.dp).fillMaxHeight()
                .background(mixerPalette.panel, RoundedCornerShape(1.dp))
                .border(1.dp, mixerPalette.border.copy(alpha = 0.95f), RoundedCornerShape(1.dp)).padding(7.dp),
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
                                latestMasterChange(mixerFaderGainAtFraction(1f - start.y / height, 1f))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val height = size.height.coerceAtLeast(1).toFloat()
                                latestMasterChange(mixerFaderGainAtFraction(1f - change.position.y / height, 1f))
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { offset ->
                            val height = size.height.coerceAtLeast(1).toFloat()
                            latestMasterChange(mixerFaderGainAtFraction(1f - offset.y / height, 1f))
                        })
                    }
            ) {
                val railWidth = size.width * 0.24f
                val railLeft = (size.width - railWidth) / 2f
                drawRoundRect(mixerPalette.room, Offset(railLeft, 0f), Size(railWidth, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f))
                listOf(0.0, -6.0, -12.0, -24.0, -48.0).forEach { db ->
                    val fraction = ((db + 60.0) / 60.0).toFloat()
                    val y = size.height * (1f - fraction)
                    val major = db == 0.0 || db == -12.0 || db == -24.0
                    drawLine(mixerPalette.grid.copy(alpha = if (major) 0.95f else 0.65f),
                        Offset(railLeft - if (major) 8f else 5f, y), Offset(railLeft - 1f, y),
                        if (major) 1.3f else 0.8f)
                }
                val gain = mixerFaderFractionForGain(master, 1f)
                val thumbY = size.height * (1f - gain)
                val capLeft = size.width * 0.08f
                val capWidth = size.width * 0.84f
                drawRoundRect(mixerPalette.control, Offset(capLeft, thumbY - 9f),
                    Size(capWidth, 18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f))
                drawRoundRect(mixerPalette.border, Offset(capLeft, thumbY - 9f),
                    Size(capWidth, 18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
                drawLine(mixerPalette.text, Offset(capLeft + 3f, thumbY),
                    Offset(capLeft + capWidth - 3f, thumbY), 2f)
            }
            val masterDbLabel = if (master <= 0.001f) "−∞ dB" else
                String.format(java.util.Locale.US, "%.1f dB", 20f * kotlin.math.log10(master))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("${(master * 100).toInt()}%", color = mixerPalette.text, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Text(masterDbLabel, color = mixerPalette.muted, fontSize = 8.sp, maxLines = 1)
            }
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
        normalizedQuery.isEmpty() || track.name.lowercase().contains(normalizedQuery) ||
            track.kind.lowercase().contains(normalizedQuery) ||
            track.clips.any { it.name.lowercase().contains(normalizedQuery) }
    }
    val allClips = project.tracks.flatMap { track -> track.clips.map { track to it } }
        .filter { (track, clip) ->
            (category != "SAMPLES" || (track.kind == "audio" && clip.sourceUri != null)) &&
                (normalizedQuery.isEmpty() || clip.name.lowercase().contains(normalizedQuery) ||
                    track.name.lowercase().contains(normalizedQuery) ||
                    clip.sourceUri?.substringAfterLast('/')?.lowercase()?.contains(normalizedQuery) == true)
        }
    Column(Modifier.fillMaxSize().background(p.room).border(1.dp, p.border.copy(alpha = 0.8f)).padding(4.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Column(
                Modifier.width(184.dp).fillMaxHeight()
                    .background(p.canvas, RoundedCornerShape(1.dp))
                    .border(1.dp, p.border, RoundedCornerShape(1.dp)).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(Modifier.fillMaxWidth().height(27.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("BROWSER", color = p.orange, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text("⌕", color = p.muted, fontSize = 17.sp)
                }
                HorizontalDivider(color = p.border)
                Text("LIBRARY", color = p.muted, fontSize = 7.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                categories.forEach { item ->
                    val active = category == item
                    Surface(
                        color = if (active) p.accent.copy(alpha = 0.16f) else Color.Transparent,
                        shape = RoundedCornerShape(1.dp),
                        border = BorderStroke(1.dp, if (active) p.accent.copy(alpha = 0.65f) else Color.Transparent),
                        modifier = Modifier.fillMaxWidth().height(38.dp).clickable { category = item }
                    ) {
                        Row(Modifier.padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.width(3.dp).height(19.dp)
                                .background(if (active) p.accent else p.border, RoundedCornerShape(1.dp)))
                            Text(
                                when (item) {
                                    "ALL" -> "All Items"
                                    "SAMPLES" -> "Samples"
                                    "CLIPS" -> "Clips"
                                    "TRACKS" -> "Tracks"
                                    else -> "Devices"
                                },
                                color = if (active) p.accent else p.text,
                                fontSize = 9.sp,
                                fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text("PLACES", color = p.muted, fontSize = 7.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 4.dp))
                Row(Modifier.fillMaxWidth().height(29.dp)
                    .background(p.panel, RoundedCornerShape(1.dp)).padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("▣", color = p.accent, fontSize = 11.sp)
                    Text("Current Project", color = p.text, fontSize = 8.sp, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                HorizontalDivider(color = p.border)
                Text("STUDIOWAVE", color = p.orange, fontSize = 7.sp, fontWeight = FontWeight.Black)
                Text("Offline Library", color = p.muted, fontSize = 7.sp)
            }

            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .background(p.canvas, RoundedCornerShape(2.dp))
                    .border(1.dp, p.border, RoundedCornerShape(2.dp)).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(Modifier.fillMaxWidth().height(28.dp)
                    .background(p.panel, RoundedCornerShape(2.dp)).padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (category) {
                            "SAMPLES" -> "SAMPLES"
                            "CLIPS" -> "CLIP LIBRARY"
                            "TRACKS" -> "TRACK LIST"
                            "DEVICES" -> "DEVICE LIBRARY"
                            else -> "PROJECT CONTENT"
                        },
                        color = p.text, fontSize = 9.sp, fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.weight(1f))
                    Text(project.tracks.size.toString() + " TRACKS   /   " +
                        project.tracks.sumOf { it.clips.size }.toString() + " CLIPS",
                        color = p.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().height(45.dp), singleLine = true,
                    shape = RoundedCornerShape(1.dp),
                    placeholder = { Text("Search library…", fontSize = 10.sp, color = p.muted) },
                    leadingIcon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Search library", modifier = Modifier.size(15.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = p.accent, unfocusedBorderColor = p.border,
                        focusedTextColor = p.text, unfocusedTextColor = p.text, cursorColor = p.accent,
                        focusedContainerColor = p.control, unfocusedContainerColor = p.control
                    )
                )
                if (category == "ALL" || category == "SAMPLES" || category == "CLIPS") {
                    Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = onImportAudio, modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = p.accent.copy(alpha = 0.18f), contentColor = p.accent),
                            shape = RoundedCornerShape(1.dp)
                        ) {
                            Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(5.dp)); Text("IMPORT AUDIO", fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                        Button(
                            onClick = { onOpenEditor("ARRANGEMENT", null, null) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = p.control, contentColor = p.text),
                            shape = RoundedCornerShape(1.dp)
                        ) { Text("OPEN ARRANGEMENT", fontSize = 8.sp, fontWeight = FontWeight.Black) }
                    }
                }
                when (category) {
                    "DEVICES" -> DeviceModuleCard(
                        title = "DEVICE LIBRARY", subtitle = "AVAILABLE INSTRUMENTS", palette = p,
                        body = "DirectWave is available on MIDI tracks from Device View. Active effect inserts are listed only when an engine-backed processor exists.",
                        fillAvailableWidth = true
                    )
                    "TRACKS" -> Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        tracks.forEach { track ->
                            val selected = track.id == selectedTrackId
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (selected) p.raised else p.panel, RoundedCornerShape(1.dp))
                                    .border(1.dp, if (selected) p.accent else p.border.copy(alpha = 0.55f), RoundedCornerShape(1.dp))
                                    .clickable { onSelectTrack(track.id) }
                                    .padding(horizontal = 7.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.width(4.dp).height(30.dp).background(Color(track.colorArgb.toInt()), RoundedCornerShape(1.dp)))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(track.name.uppercase(), color = p.text, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
                                    Text(track.kind.uppercase() + "  ·  " + track.clips.size + " CLIPS", color = p.muted, fontSize = 7.sp)
                                }
                                SmallAction("SELECT") { onSelectTrack(track.id) }
                                SmallAction("EDIT") {
                                    onSelectTrack(track.id)
                                    onOpenEditor(
                                        when (track.kind) { "audio" -> "AUDIO EDITOR"; "drum" -> "DRUM SEQUENCER"; else -> "PIANO ROLL" },
                                        track.id, track.clips.minByOrNull { it.startTick }?.id
                                    )
                                }
                            }
                        }
                        if (tracks.isEmpty()) Text("No matching tracks.", color = p.muted, fontSize = 9.sp, modifier = Modifier.padding(8.dp))
                    }
                    else -> Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().height(23.dp).padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("NAME", color = p.muted, fontSize = 7.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            Text("TYPE / LENGTH", color = p.muted, fontSize = 7.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(170.dp))
                            Text("ACTION", color = p.muted, fontSize = 7.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(58.dp))
                        }
                        HorizontalDivider(color = p.border)
                        allClips.forEachIndexed { index, entry ->
                            val (track, clip) = entry
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (index % 2 == 0) p.panel else p.control.copy(alpha = 0.55f), RoundedCornerShape(1.dp))
                                    .border(1.dp, p.border.copy(alpha = 0.42f), RoundedCornerShape(1.dp))
                                    .clickable { onSelectTrack(track.id) }
                                    .padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(Modifier.width(4.dp).height(29.dp).background(Color((clip.colorArgb ?: track.colorArgb).toInt()), RoundedCornerShape(1.dp)))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(clip.name.uppercase(), color = p.text, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
                                    Text(track.name, color = p.muted, fontSize = 7.sp, maxLines = 1)
                                }
                                Column(Modifier.width(164.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(track.kind.uppercase(), color = p.accent, fontSize = 7.sp, fontWeight = FontWeight.Black)
                                    Text((kotlin.math.ceil(clip.lengthTicks.toDouble() / project.ppq.coerceAtLeast(1) / 4.0).toInt().coerceAtLeast(1)).toString() + " BARS",
                                        color = p.muted, fontSize = 7.sp)
                                }
                                SmallAction("OPEN") {
                                    onSelectTrack(track.id)
                                    onOpenEditor(
                                        when (track.kind) { "audio" -> "AUDIO EDITOR"; "drum" -> "DRUM SEQUENCER"; else -> "PIANO ROLL" },
                                        track.id, clip.id
                                    )
                                }
                            }
                        }
                        if (allClips.isEmpty()) Text(
                            if (category == "SAMPLES") "No imported audio sources match this search." else "No matching clips.",
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
    onToggle: () -> Unit,
    fullScreen: Boolean = false
) {
    val selectedTrack = project.tracks.firstOrNull { it.id == selectedTrackId } ?: project.tracks.firstOrNull()
    val palette = LocalStudioWavePalette.current
    var expanded by remember { mutableStateOf(true) }
    var showAddFx by remember { mutableStateOf(false) }

    fun applyDeviceList(updatedDevices: List<TrackDevice>) {
        val track = selectedTrack ?: return
        onProjectChange(project.copy(tracks = project.tracks.map { old ->
            if (old.id == track.id) old.copy(devices = updatedDevices) else old
        }))
    }

    fun addEffect(type: String, label: String) {
        val track = selectedTrack ?: return
        if (track.devices.size >= StudioProject.MAX_DEVICES_PER_TRACK) return
        applyDeviceList(track.devices + TrackDevice(type = type, name = label, parameters = defaultDeviceParameters(type)))
        showAddFx = false
    }

    Column(
        (if (fullScreen) Modifier.fillMaxWidth().fillMaxHeight()
        else Modifier.fillMaxWidth().height(if (expanded) 328.dp else 42.dp))
            .background(palette.room, RoundedCornerShape(2.dp))
            .border(1.dp, palette.border, RoundedCornerShape(2.dp))
            .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(30.dp)
                .background(palette.panel, RoundedCornerShape(1.dp)).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.size(6.dp).background(palette.orange, RoundedCornerShape(1.dp)))
            Text("DEVICE VIEW", color = palette.text, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                project.tracks.forEach { track ->
                    val isSelected = selectedTrack?.id == track.id
                    Surface(
                        color = if (isSelected) palette.accent.copy(alpha = 0.16f) else palette.control,
                        border = BorderStroke(1.dp, if (isSelected) palette.accent else palette.border),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.height(23.dp).clickable { onSelectTrack(track.id) }
                    ) {
                        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(6.dp).background(Color(track.colorArgb.toInt()), RoundedCornerShape(1.dp)))
                            Text(track.name.uppercase(), color = if (isSelected) palette.accent else palette.text,
                                fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
            DirectWaveStateButton(if (expanded) "FOLD" else "UNFOLD", expanded, palette) { expanded = !expanded }
            DirectWaveStateButton("×", false, palette, onToggle)
        }

        if (expanded) {
            if (selectedTrack == null) {
                DeviceModuleCard("NO TRACK", "DEVICE CHAIN", palette, "Create or select a track in Arrangement.")
            } else {
                val track = selectedTrack
                Row(Modifier.fillMaxWidth().height(27.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(track.name.uppercase(), color = palette.orange, fontSize = 8.sp, fontWeight = FontWeight.Black,
                        maxLines = 1, modifier = Modifier.weight(1f))
                    Text("${track.devices.size} INSERTS", color = palette.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    DirectWaveStateButton("+ ADD FX", false, palette) { showAddFx = true }
                }
                Row(Modifier.fillMaxWidth().weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Top) {
                    DeviceChainSlotMarker(number = 1, palette = palette, label = "SRC")
                    when (track.kind) {
                        "midi" -> DirectWaveDevicePanel(
                            trackId = track.id, trackName = track.name, trackKind = track.kind,
                            palette = palette, fullHeight = fullScreen
                        )
                        "drum" -> DeviceModuleCard("DRUM RACK", "STEP SEQUENCER", palette,
                            "This track keeps its drum-step workflow. Enabled effect inserts below process its output.")
                        else -> DeviceModuleCard("AUDIO SOURCE", "CLIP / SAMPLE", palette,
                            "Audio source editing remains in Clip View. Enabled effects below process this track during playback.")
                    }
                    track.devices.filter { it.type in EFFECT_DEVICE_TYPES }.forEachIndexed { effectIndex, device ->
                        // Make the serial device chain legible at a glance: each insert gets a
                        // numbered Live-style slot marker without changing device order or routing.
                        DeviceChainSlotMarker(
                            number = effectIndex + 2,
                            palette = palette
                        )
                        TrackEffectDeviceCard(
                            device = device,
                            palette = palette,
                            onUpdate = { changed -> applyDeviceList(track.devices.map { if (it.id == changed.id) changed else it }) },
                            onRemove = { applyDeviceList(track.devices.filterNot { it.id == device.id }) }
                        )
                    }
                    if (track.devices.none { it.type in EFFECT_DEVICE_TYPES }) {
                        DeviceModuleCard("NO INSERT FX", "EMPTY EFFECT CHAIN", palette,
                            "Tap + ADD FX to insert an audio processor. Values are saved with this project.")
                    }
                }
            }
        }
    }

    if (showAddFx && selectedTrack != null) {
        AlertDialog(
            onDismissRequest = { showAddFx = false },
            containerColor = palette.panel,
            title = { Text("ADD AUDIO EFFECT", color = palette.orange, fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(
                        "EQ" to "Equalizer",
                        "PARAM_EQ_8" to "Parametric EQ Eight",
                        "REVERB" to "Reverb",
                        "DELAY" to "Delay",
                        "COMPRESSOR" to "Compressor",
                        "SATURATOR" to "Saturator",
                        "FILTER" to "Filter",
                        "LIMITER" to "Limiter"
                    ).forEach { (type, label) ->
                        Surface(
                            color = palette.canvas, shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.fillMaxWidth().height(35.dp).clickable { addEffect(type, label) }
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, color = palette.text, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f))
                                Text("ADD +", color = palette.accent, fontSize = 7.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Text("Full Analyzer and Multi FX remain unavailable until their dedicated processors are implemented and tested.",
                        color = palette.muted, fontSize = 7.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showAddFx = false }) { Text("DONE", color = palette.accent) } }
        )
    }
}

@Composable
private fun DeviceChainSlotMarker(number: Int, palette: StudioWavePalette, label: String = "FX") {
    Column(
        Modifier.width(28.dp).height(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(number.toString().padStart(2, '0'), color = palette.orange,
            fontSize = 8.sp, fontWeight = FontWeight.Black)
        Box(Modifier.width(1.dp).height(9.dp).background(palette.border))
        Text(label, color = palette.muted, fontSize = 6.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.width(1.dp).height(9.dp).background(palette.border))
        Text("›", color = palette.orange, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DeviceModuleCard(
    title: String,
    subtitle: String,
    palette: StudioWavePalette,
    body: String,
    fillAvailableWidth: Boolean = false
) {
    Column(
        (if (fillAvailableWidth) Modifier.fillMaxWidth() else Modifier.width(300.dp)).height(204.dp)
            .background(palette.panel, RoundedCornerShape(1.dp))
            .border(1.dp, palette.border, RoundedCornerShape(1.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(palette.orange, RoundedCornerShape(1.dp)))
            Text(title, color = palette.text, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        Text(subtitle, color = palette.accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
        HorizontalDivider(color = palette.border)
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
    var isFactorySample by remember(trackId) { mutableStateOf(false) }
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
        // Generate the built-in one-shot locally so this sample works offline and
        // doesn't rely on an optional binary resource being packaged by CI.
        val presetIndex = com.studiowave.app.audio.FactorySampleLibrary.presets.indexOfFirst {
            it.fileName.substringBeforeLast('.') == "one_shot"
        }.takeIf { it >= 0 } ?: 0
        val target = java.io.File(context.cacheDir, "studiowave_one_shot.wav")
        runCatching {
            target.writeBytes(com.studiowave.app.audio.FactorySampleLibrary.wavBytes(presetIndex))
        }.onFailure {
            sampleUri = null
            sampleReady = false
            Toast.makeText(context, "Gagal membuat sample bawaan", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.fromFile(target)
        sampleUri = uri.toString()
        sampleLabel = "One Shoot.wav"
        sampleReady = false
        isFactorySample = true
        preferences.edit().putString("sample.$trackId", uri.toString())
            .putString("sampleName.$trackId", "One Shoot.wav")
            .putBoolean("factory.$trackId", true).apply()
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
        isFactorySample = true
        preferences.edit().putString("sample.$trackId", uri.toString())
            .putString("sampleName.$trackId", sample.title)
            .putBoolean("factory.$trackId", true).apply()
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
            isFactorySample = false
            preferences.edit().putString("sample.$trackId", uri.toString())
                .putString("sampleName.$trackId", sampleLabel)
                .putBoolean("factory.$trackId", false).apply()
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
            isFactorySample = preferences.getBoolean("factory.$trackId", false)
            sampleLabel = preferences.getString("sampleName.$trackId", null)
                ?: Uri.parse(saved).lastPathSegment?.substringAfterLast('/')?.takeLast(36)
                ?: "Saved sample"
            sampler.load(Uri.parse(saved)) { ready -> sampleReady = ready }
        } else {
            loadBundledOneShot()
        }
    }

    val compact = !fullHeight
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val knobSize = if (compact) 54.dp else 72.dp
    val waveformHeight = if (compact) 52.dp else 96.dp
    val panelWidth = if (fullHeight) {
        (screenWidth - 88.dp).coerceIn(480.dp, 1120.dp)
    } else {
        (screenWidth - 144.dp).coerceIn(400.dp, 780.dp)
    }
    val panelHeight = if (compact) 287.dp else (configuration.screenHeightDp.dp - 76.dp).coerceIn(320.dp, 560.dp)
    val statusText = when {
        sampleReady && isFactorySample -> "FACTORY SAMPLE · READY"
        sampleReady -> "USER SAMPLE · READY"
        sampleUri != null -> "LOADING SAMPLE"
        else -> "NO SAMPLE"
    }

    Column(
        Modifier.width(panelWidth)
            .height(panelHeight)
            .background(palette.canvas, RoundedCornerShape(1.dp))
            .border(1.dp, palette.border, RoundedCornerShape(1.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Ableton-inspired device shell: narrow title strip, activated state, grouped macro controls.
        Row(
            Modifier.fillMaxWidth().height(36.dp)
                .background(palette.panel, RoundedCornerShape(1.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(Modifier.size(9.dp).background(if (sampleReady) palette.accent else palette.orange, RoundedCornerShape(1.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("DIRECTWAVE", color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("SAMPLER  ·  ${trackName.uppercase()}", color = palette.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
                    .background(palette.panel, RoundedCornerShape(1.dp))
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
                            .background(if (sampleUri?.contains(sample.assetName) == true) palette.raised else palette.room, RoundedCornerShape(1.dp))
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
                    .background(palette.panel, RoundedCornerShape(1.dp)).padding(4.dp),
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
                    .background(palette.panel, RoundedCornerShape(1.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
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
                .background(palette.panel, RoundedCornerShape(1.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
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
                    contentColor = palette.text, shape = RoundedCornerShape(1.dp),
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
private fun TrackEffectDeviceCard(
    device: TrackDevice,
    palette: StudioWavePalette,
    onUpdate: (TrackDevice) -> Unit,
    onRemove: () -> Unit
) {
    val p = device.parameters
    val enabledAccent by androidx.compose.animation.animateColorAsState(
        targetValue = if (device.enabled) palette.orange else palette.border,
        animationSpec = tween(durationMillis = 120),
        label = "deviceEnabledAccent_" + device.id
    )
    val enabledIndicator by androidx.compose.animation.animateColorAsState(
        targetValue = if (device.enabled) palette.orange else palette.muted,
        animationSpec = tween(durationMillis = 120),
        label = "deviceEnabledIndicator_" + device.id
    )
    Column(
        Modifier.width(344.dp).height(360.dp).verticalScroll(rememberScrollState())
            .background(palette.panel, RoundedCornerShape(1.dp))
            .border(1.dp, palette.border, RoundedCornerShape(1.dp))
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Device-rack faceplate: active strip and indicator ease between enabled/disabled states.
        Box(Modifier.fillMaxWidth().height(3.dp).background(enabledAccent))
        Row(
            Modifier.fillMaxWidth().height(25.dp)
                .background(palette.raised, RoundedCornerShape(1.dp))
                .border(1.dp, palette.border, RoundedCornerShape(1.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).background(enabledIndicator, RoundedCornerShape(1.dp)))
            Text(device.name.uppercase(), color = palette.text, fontSize = 8.sp, fontWeight = FontWeight.Black,
                maxLines = 1, modifier = Modifier.weight(1f))
            DirectWaveStateButton(if (device.enabled) "ON" else "OFF", device.enabled, palette) {
                onUpdate(device.copy(enabled = !device.enabled))
            }
            DirectWaveStateButton("×", false, palette, onRemove)
        }
        HorizontalDivider(color = palette.border)
        when (device.type) {
            "EQ" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    DirectWaveKnob("LOW", p["lowDb"] ?: 0f, -12f..12f, String.format(java.util.Locale.US, "%.1f dB", p["lowDb"] ?: 0f), palette, Modifier.weight(1f), 52.dp) {
                        onUpdate(device.copy(parameters = p + ("lowDb" to it)))
                    }
                    DirectWaveKnob("MID", p["midDb"] ?: 0f, -12f..12f, String.format(java.util.Locale.US, "%.1f dB", p["midDb"] ?: 0f), palette, Modifier.weight(1f), 52.dp) {
                        onUpdate(device.copy(parameters = p + ("midDb" to it)))
                    }
                    DirectWaveKnob("HIGH", p["highDb"] ?: 0f, -12f..12f, String.format(java.util.Locale.US, "%.1f dB", p["highDb"] ?: 0f), palette, Modifier.weight(1f), 52.dp) {
                        onUpdate(device.copy(parameters = p + ("highDb" to it)))
                    }
                }
            }
            "PARAM_EQ_8" -> {
                var selectedBand by remember(device.id) { mutableIntStateOf(1) }
                val defaultFreqNorms = listOf(0.159f, 0.259f, 0.366f, 0.466f, 0.566f, 0.681f, 0.799f, 0.900f)
                val gain = (p["band$selectedBand"] ?: 0f).coerceIn(-18f, 18f)
                val freqKey = "band${selectedBand}FreqNorm"
                val qKey = "band${selectedBand}Q"
                val freqNorm = (p[freqKey] ?: defaultFreqNorms[selectedBand - 1]).coerceIn(0f, 1f)
                val frequencyHz = (20.0 * Math.pow(1000.0, freqNorm.toDouble())).toInt()
                val q = (p[qKey] ?: 1.1f).coerceIn(0.25f, 8f)
                val gains = (1..8).map { band -> (p["band$band"] ?: 0f).coerceIn(-18f, 18f) }
                // Place each band at its actual logarithmic frequency position. Equally spaced
                // band indices distort the response overview when users move a band's frequency.
                val bandFreqNorms = (1..8).map { band ->
                    (p["band${band}FreqNorm"] ?: defaultFreqNorms[band - 1]).coerceIn(0f, 1f)
                }
                Canvas(
                    Modifier.fillMaxWidth().height(58.dp)
                        .background(palette.canvas, RoundedCornerShape(1.dp))
                        .border(1.dp, palette.border, RoundedCornerShape(1.dp))
                ) {
                    val midY = size.height / 2f
                    for (line in 0..4) {
                        val y = size.height * line / 4f
                        drawLine(
                            palette.border.copy(alpha = if (line == 2) 0.95f else 0.48f),
                            Offset(0f, y), Offset(size.width, y),
                            if (line == 2) 1.dp.toPx() else 0.6.dp.toPx()
                        )
                    }
                    // Log-frequency grid divisions (20 Hz to 20 kHz).
                    for (division in 0..6) {
                        val x = size.width * division / 6f
                        drawLine(palette.border.copy(alpha = 0.42f), Offset(x, 0f), Offset(x, size.height), 0.6.dp.toPx())
                    }
                    // This is a band-gain overview, not a measured FFT or summed filter response.
                    // Sort by frequency before connecting points so moving bands cannot make the
                    // overview double back across the graph.
                    val curveOrder = (0..7).sortedBy { bandFreqNorms[it] }
                    val curve = androidx.compose.ui.graphics.Path()
                    curveOrder.forEachIndexed { orderIndex, bandIndex ->
                        val x = size.width * bandFreqNorms[bandIndex]
                        val y = midY - (gains[bandIndex] / 18f) * (size.height * 0.44f)
                        if (orderIndex == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
                    }
                    drawPath(curve, palette.orange, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                    gains.forEachIndexed { index, bandGain ->
                        val x = size.width * bandFreqNorms[index]
                        val y = midY - (bandGain / 18f) * (size.height * 0.44f)
                        drawCircle(if (index + 1 == selectedBand) palette.orange else palette.text,
                            radius = if (index + 1 == selectedBand) 3.dp.toPx() else 2.dp.toPx(), center = Offset(x, y))
                    }
                }
                Text("PARAMETRIC EQ · SELECT BAND TO EDIT", color = palette.orange, fontSize = 6.sp, fontWeight = FontWeight.Black)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..8).forEach { band ->
                        DirectWaveStateButton("B$band", selectedBand == band, palette) { selectedBand = band }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DirectWaveKnob(
                        "FREQ", freqNorm, 0f..1f, if (frequencyHz >= 1000) String.format(java.util.Locale.US, "%.1f kHz", frequencyHz / 1000f) else "$frequencyHz Hz",
                        palette, Modifier.weight(1f), 43.dp
                    ) { next ->
                        onUpdate(device.copy(parameters = p + (freqKey to next)))
                    }
                    DirectWaveKnob(
                        "Q", q, 0.25f..8f, String.format(java.util.Locale.US, "%.2f", q),
                        palette, Modifier.weight(1f), 43.dp
                    ) { next ->
                        onUpdate(device.copy(parameters = p + (qKey to next)))
                    }
                    DirectWaveKnob(
                        "GAIN", gain, -18f..18f, String.format(java.util.Locale.US, "%+.1f dB", gain),
                        palette, Modifier.weight(1f), 43.dp
                    ) { next ->
                        onUpdate(device.copy(parameters = p + ("band$selectedBand" to next)))
                    }
                }
            }
            "REVERB" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    DirectWaveKnob("ROOM", p["room"] ?: 0.45f, 0.05f..0.95f, String.format(java.util.Locale.US, "%.2f", p["room"] ?: 0.45f), palette, Modifier.weight(1f), 52.dp) {
                        onUpdate(device.copy(parameters = p + ("room" to it)))
                    }
                    DirectWaveKnob("DECAY", p["decay"] ?: 1.6f, 0.2f..6f, String.format(java.util.Locale.US, "%.1f s", p["decay"] ?: 1.6f), palette, Modifier.weight(1f), 52.dp) {
                        onUpdate(device.copy(parameters = p + ("decay" to it)))
                    }
                    DirectWaveKnob("WET", p["wet"] ?: 0.22f, 0f..1f, String.format(java.util.Locale.US, "%.0f%%", (p["wet"] ?: 0.22f) * 100f), palette, Modifier.weight(1f), 52.dp) {
                        onUpdate(device.copy(parameters = p + ("wet" to it)))
                    }
                }
            }
            "DELAY" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    DirectWaveKnob("TIME", p["timeMs"] ?: 320f, 10f..900f, "${(p["timeMs"] ?: 320f).toInt()} ms", palette, Modifier.weight(1f), 52.dp) {
                        onUpdate(device.copy(parameters = p + ("timeMs" to it)))
                    }
                    DirectWaveKnob("FEEDBACK", p["feedback"] ?: 0.32f, 0f..0.88f, String.format(java.util.Locale.US, "%.0f%%", (p["feedback"] ?: 0.32f) * 100f), palette, Modifier.weight(1f), 52.dp) {
                        onUpdate(device.copy(parameters = p + ("feedback" to it)))
                    }
                    DirectWaveKnob("WET", p["wet"] ?: 0.2f, 0f..1f, String.format(java.util.Locale.US, "%.0f%%", (p["wet"] ?: 0.2f) * 100f), palette, Modifier.weight(1f), 52.dp) {
                        onUpdate(device.copy(parameters = p + ("wet" to it)))
                    }
                }
            }
            "COMPRESSOR" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    DirectWaveKnob("THRESH", p["thresholdDb"] ?: -18f, -60f..0f,
                        String.format(java.util.Locale.US, "%.1f dB", p["thresholdDb"] ?: -18f), palette, Modifier.weight(1f), 39.dp) {
                        onUpdate(device.copy(parameters = p + ("thresholdDb" to it)))
                    }
                    DirectWaveKnob("RATIO", p["ratio"] ?: 3f, 1f..20f,
                        String.format(java.util.Locale.US, "%.1f:1", p["ratio"] ?: 3f), palette, Modifier.weight(1f), 39.dp) {
                        onUpdate(device.copy(parameters = p + ("ratio" to it)))
                    }
                    DirectWaveKnob("ATTACK", p["attackMs"] ?: 10f, 0.1f..250f,
                        String.format(java.util.Locale.US, "%.1f ms", p["attackMs"] ?: 10f), palette, Modifier.weight(1f), 39.dp) {
                        onUpdate(device.copy(parameters = p + ("attackMs" to it)))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    DirectWaveKnob("RELEASE", p["releaseMs"] ?: 120f, 1f..1500f,
                        String.format(java.util.Locale.US, "%.0f ms", p["releaseMs"] ?: 120f), palette, Modifier.weight(1f), 39.dp) {
                        onUpdate(device.copy(parameters = p + ("releaseMs" to it)))
                    }
                    DirectWaveKnob("MAKEUP", p["makeupDb"] ?: 0f, -12f..12f,
                        String.format(java.util.Locale.US, "%.1f dB", p["makeupDb"] ?: 0f), palette, Modifier.weight(1f), 39.dp) {
                        onUpdate(device.copy(parameters = p + ("makeupDb" to it)))
                    }
                }
            }
            "SATURATOR" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    DirectWaveKnob("DRIVE", p["drive"] ?: 2.5f, 0.2f..12f,
                        String.format(java.util.Locale.US, "%.1f", p["drive"] ?: 2.5f), palette, Modifier.weight(1f), 41.dp) {
                        onUpdate(device.copy(parameters = p + ("drive" to it)))
                    }
                    DirectWaveKnob("WET", p["wet"] ?: 0.35f, 0f..1f,
                        String.format(java.util.Locale.US, "%.0f%%", (p["wet"] ?: 0.35f) * 100f), palette, Modifier.weight(1f), 41.dp) {
                        onUpdate(device.copy(parameters = p + ("wet" to it)))
                    }
                    DirectWaveKnob("OUTPUT", p["outputDb"] ?: 0f, -12f..12f,
                        String.format(java.util.Locale.US, "%.1f dB", p["outputDb"] ?: 0f), palette, Modifier.weight(1f), 41.dp) {
                        onUpdate(device.copy(parameters = p + ("outputDb" to it)))
                    }
                }
            }
            "FILTER" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    DirectWaveKnob("CUTOFF", p["cutoffHz"] ?: 16000f, 20f..20000f,
                        "${(p["cutoffHz"] ?: 16000f).toInt()} Hz", palette, Modifier.weight(1f), 41.dp) {
                        onUpdate(device.copy(parameters = p + ("cutoffHz" to it)))
                    }
                    DirectWaveKnob("RESONANCE", p["resonance"] ?: 0.15f, 0f..0.95f,
                        String.format(java.util.Locale.US, "%.2f", p["resonance"] ?: 0.15f), palette, Modifier.weight(1f), 41.dp) {
                        onUpdate(device.copy(parameters = p + ("resonance" to it)))
                    }
                }
                val mode = (p["mode"] ?: 0f).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MODE", color = palette.muted, fontSize = 6.sp, modifier = Modifier.weight(1f))
                    listOf("LP", "HP", "BP").forEachIndexed { index, label ->
                        DirectWaveStateButton(label, mode == index, palette) {
                            onUpdate(device.copy(parameters = p + ("mode" to index.toFloat())))
                        }
                    }
                }
            }
            "LIMITER" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    DirectWaveKnob("CEILING", p["ceilingDb"] ?: -1f, -12f..0f,
                        String.format(java.util.Locale.US, "%.1f dB", p["ceilingDb"] ?: -1f), palette, Modifier.weight(1f), 41.dp) {
                        onUpdate(device.copy(parameters = p + ("ceilingDb" to it)))
                    }
                    DirectWaveKnob("RELEASE", p["releaseMs"] ?: 80f, 10f..500f,
                        String.format(java.util.Locale.US, "%.0f ms", p["releaseMs"] ?: 80f), palette, Modifier.weight(1f), 41.dp) {
                        onUpdate(device.copy(parameters = p + ("releaseMs" to it)))
                    }
                }
            }
        }
        HorizontalDivider(color = palette.border, thickness = 1.dp)
        Text(
            if (device.enabled) "INSERT  ·  ENABLED  ·  PROJECT PARAMETERS" else "INSERT  ·  BYPASSED  ·  PROJECT PARAMETERS",
            color = if (device.enabled) palette.orange else palette.muted,
            fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1
        )
    }
}

@Composable
private fun DeviceParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    palette: StudioWavePalette,
    unit: String = "",
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = palette.muted, fontSize = 6.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(String.format(java.util.Locale.US, "%.1f%s", value, unit), color = palette.text, fontSize = 6.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange,
            valueRange = range, modifier = Modifier.fillMaxWidth().height(21.dp),
            colors = SliderDefaults.colors(thumbColor = palette.orange, activeTrackColor = palette.accent,
                inactiveTrackColor = palette.control)
        )
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
        shape = RoundedCornerShape(1.dp),
        border = BorderStroke(1.dp, if (active) palette.orange else palette.border.copy(alpha = 0.95f)),
        modifier = Modifier.height(23.dp).clickable(onClick = onClick)
    ) {
        Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun DirectWaveKnob(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    palette: StudioWavePalette,
    modifier: Modifier = Modifier,
    knobSize: androidx.compose.ui.unit.Dp = 56.dp,
    onValueChange: (Float) -> Unit
) {
    val latestValue = rememberUpdatedState(value)
    val latestOnChange = rememberUpdatedState(onValueChange)
    val fraction = ((value - range.start) / (range.endInclusive - range.start).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(
            Modifier.size(knobSize)
                .pointerInput(range.start, range.endInclusive) {
                    var accumulated = 0f
                    var startValue = latestValue.value
                    detectDragGestures(
                        onDragStart = { accumulated = 0f; startValue = latestValue.value },
                        onDrag = { change, drag ->
                            change.consume()
                            accumulated += drag.y
                            val span = range.endInclusive - range.start
                            // A vertical drag is reliable on compact mobile device controls and keeps the knob touch-sized.
                            val next = (startValue - accumulated / 105f * span).coerceIn(range.start, range.endInclusive)
                            latestOnChange.value(next)
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.34f
            val ringRadius = radius + size.minDimension * 0.105f
            val rectTop = Offset(center.x - ringRadius, center.y - ringRadius)
            val rectSize = Size(ringRadius * 2f, ringRadius * 2f)
            drawCircle(palette.room.copy(alpha = 0.72f), radius * 1.15f, center)
            drawCircle(palette.control, radius, center)
            drawCircle(palette.border, radius, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawArc(
                palette.border.copy(alpha = 0.92f), 135f, 270f, false, rectTop, rectSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            drawArc(
                palette.orange, 135f, 270f * fraction, false, rectTop, rectSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            for (tick in 0..10) {
                val angle = Math.toRadians((135.0 + tick / 10.0 * 270.0))
                val inner = ringRadius + size.minDimension * 0.015f
                val outer = inner + if (tick % 5 == 0) size.minDimension * 0.045f else size.minDimension * 0.025f
                drawLine(
                    palette.muted.copy(alpha = if (tick % 5 == 0) 0.85f else 0.48f),
                    Offset(center.x + kotlin.math.cos(angle).toFloat() * inner, center.y + kotlin.math.sin(angle).toFloat() * inner),
                    Offset(center.x + kotlin.math.cos(angle).toFloat() * outer, center.y + kotlin.math.sin(angle).toFloat() * outer),
                    if (tick % 5 == 0) 1.dp.toPx() else 0.65.dp.toPx()
                )
            }
            val angle = Math.toRadians((135.0 + fraction * 270.0))
            drawLine(
                palette.text, center,
                Offset(center.x + kotlin.math.cos(angle).toFloat() * radius * 0.7f, center.y + kotlin.math.sin(angle).toFloat() * radius * 0.7f),
                2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawCircle(palette.muted.copy(alpha = 0.55f), radius * 0.09f, center)
        }
        Text(label, color = palette.muted, fontSize = 6.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Box(
            Modifier.background(palette.canvas, RoundedCornerShape(1.dp))
                .border(1.dp, palette.border, RoundedCornerShape(1.dp))
                .padding(horizontal = 3.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(valueLabel, color = palette.text, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun DirectWaveDeviceWaveform(
    sampleUri: String?,
    sampleReady: Boolean,
    palette: StudioWavePalette,
    compact: Boolean = false,
    onImport: () -> Unit
) {
    val context = LocalContext.current
    var peaks by remember(sampleUri) { mutableStateOf<List<Float>>(emptyList()) }
    LaunchedEffect(sampleUri) {
        peaks = emptyList()
        if (sampleUri.isNullOrBlank()) return@LaunchedEffect
        try {
            peaks = withContext(Dispatchers.IO) {
                WaveformPeakCache.getOrDecode(context, Uri.parse(sampleUri), if (compact) 80 else 180)
            }
        } catch (_: Exception) { peaks = emptyList() }
    }
    Column(
        Modifier.fillMaxWidth().height(if (compact) 26.dp else 55.dp)
            .background(palette.canvas, RoundedCornerShape(1.dp)).padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("WAVEFORM", color = palette.muted, fontSize = 6.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text(if (sampleReady) "PCM READY" else "LOADING", color = if (sampleReady) palette.accent else palette.muted, fontSize = 5.sp)
            SmallAction("IMPORT") { onImport() }
        }
        Canvas(Modifier.fillMaxSize()) {
            val mid = size.height / 2f
            drawLine(palette.grid, Offset.Zero.copy(y = mid), Offset(size.width, mid), 0.7f)
            if (peaks.isNotEmpty()) {
                val dx = size.width / peaks.size
                peaks.forEachIndexed { index, peak ->
                    val amp = peak.coerceIn(0.015f, 1f) * size.height * 0.46f
                    val x = (index + 0.5f) * dx
                    drawLine(palette.accent, Offset(x, mid - amp), Offset(x, mid + amp), (dx * 0.7f).coerceAtLeast(0.7f))
                }
            } else {
                for (index in 0 until 48) {
                    val x = size.width * index / 48f
                    val h = size.height * (0.07f + (index % 7) / 20f)
                    drawLine(palette.grid, Offset(x, mid - h), Offset(x, mid + h), 1f)
                }
            }
        }
    }
}

@Composable
private fun DirectWaveReadout(label: String, value: String, palette: StudioWavePalette, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(label, color = palette.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Text(value, color = palette.text, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun DirectWaveMiniKnob(label: String, value: Float, valueLabel: String, palette: StudioWavePalette, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(32.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.38f
            drawCircle(palette.control, radius, center)
            drawArc(palette.border, 135f, 270f, false, Offset(center.x - radius, center.y - radius),
                Size(radius * 2f, radius * 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            val angle = Math.toRadians(135.0 + value.coerceIn(0f, 100f) / 100f * 270.0)
            drawLine(palette.accent, center, Offset(center.x + kotlin.math.cos(angle).toFloat() * radius * 0.8f,
                center.y + kotlin.math.sin(angle).toFloat() * radius * 0.8f), 2.dp.toPx())
        }
        Text(label, color = palette.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Text(valueLabel, color = palette.text, fontSize = 8.sp)
    }
}

@Composable
private fun DirectWaveSampleWaveform(
    sampleUri: String?,
    sampleReady: Boolean,
    palette: StudioWavePalette,
    onImport: () -> Unit
) {
    val context = LocalContext.current
    var peaks by remember(sampleUri) { mutableStateOf<List<Float>>(emptyList()) }
    LaunchedEffect(sampleUri) {
        peaks = emptyList()
        if (sampleUri.isNullOrBlank()) return@LaunchedEffect
        try {
            peaks = withContext(Dispatchers.IO) {
                val uri = Uri.parse(sampleUri)
                if (uri.scheme == "android.resource") {
                    // Built-in presets are synthesized offline from their preset ID.
                    studioPresetWaveform(uri.lastPathSegment.orEmpty(), 128)
                } else {
                    decodeWaveformPeaks(context, uri, 128)
                }
            }
        } catch (_: Exception) { peaks = emptyList() }
    }
    Column(Modifier.fillMaxWidth().height(if (sampleReady) 60.dp else 56.dp)
        .background(palette.panel, RoundedCornerShape(1.dp)).padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("SAMPLE WAVEFORM", color = palette.muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(if (sampleReady) "READY" else "LOADING / EMPTY", color = if (sampleReady) palette.accent else palette.muted, fontSize = 7.sp)
            SmallAction("IMPORT") { onImport() }
        }
        Canvas(Modifier.fillMaxSize()) {
            val mid = size.height / 2f
            drawLine(palette.grid, Offset(0f, mid), Offset(size.width, mid), 1f)
            if (peaks.isNotEmpty()) {
                val dx = size.width / peaks.size
                peaks.forEachIndexed { index, peak ->
                    val amp = peak.coerceIn(0.02f, 1f) * size.height * 0.45f
                    val x = (index + 0.5f) * dx
                    drawLine(palette.accent, Offset(x, mid - amp), Offset(x, mid + amp), (dx * 0.68f).coerceAtLeast(1f))
                }
            }
        }
    }
}

/** Deterministic per-preset waveform for the compact waveform preview. */
private fun studioPresetWaveform(preset: String, count: Int): List<Float> {
    val seed = preset.hashCode().toLong().absoluteValue.coerceAtLeast(1L)
    return List(count.coerceIn(1, 1024)) { index ->
        val t = index.toDouble() / count.coerceAtLeast(1)
        val harmonic = kotlin.math.abs(
            kotlin.math.sin((2.0 * Math.PI * (1.0 + seed % 7) * t) +
                0.35 * kotlin.math.sin(2.0 * Math.PI * (3.0 + seed % 11) * t))
        )
        (0.08 + 0.82 * harmonic).toFloat().coerceIn(0f, 1f)
    }
}

@Composable
private fun DirectWaveEnvelopeControl(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    palette: StudioWavePalette,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when (label) {
                "S" -> "$label ${(value * 100).toInt()}%"
                else -> "$label ${value.toInt()}ms"
            },
            color = palette.text, fontSize = 8.sp, fontWeight = FontWeight.Bold
        )
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun FloatingTimelineControl(label: String, description: String, onClick: () -> Unit) {
    Surface(
        color = LocalStudioWavePalette.current.control,
        contentColor = LocalStudioWavePalette.current.text,
        shape = RoundedCornerShape(1.dp),
        border = BorderStroke(1.dp, LocalStudioWavePalette.current.border.copy(alpha = 0.9f)),
        modifier = Modifier.height(34.dp).defaultMinSize(minWidth = 42.dp)
            .clickable(onClickLabel = description, onClick = onClick)
    ) {
        Box(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}


@Composable
private fun SmallAction(label: String, minHeight: Int = 25, onClick: () -> Unit) {
    val p = LocalStudioWavePalette.current
    Button(onClick = onClick, modifier = Modifier.height(minHeight.dp), contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
        shape = RoundedCornerShape(1.dp), border = BorderStroke(1.dp, p.border.copy(alpha = 0.9f)),
        colors = ButtonDefaults.buttonColors(containerColor = p.control, contentColor = p.text)) {
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
        shape = RoundedCornerShape(1.dp), border = BorderStroke(1.dp, p.border.copy(alpha = 0.9f)),
        colors = ButtonDefaults.buttonColors(containerColor = p.control, contentColor = tint)) {
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
    // One canonical source envelope is shared by Arrangement, Sample Editor and DirectWave.
    // The old per-resolution cache let all three screens decode the same URI simultaneously.
    private const val CANONICAL_PEAK_COUNT = 240
    // Waveform envelopes are tiny (240 floats each). Keep enough entries for a large
    // arrangement so scrolling between 50+ tracks does not evict and re-decode sources.
    private const val CACHE_ENTRIES = 128
    private val cache = android.util.LruCache<String, List<Float>>(CACHE_ENTRIES)
    private val durationCache = android.util.LruCache<String, Double>(CACHE_ENTRIES)
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
    // MediaCodec instances compete heavily for CPU/memory on phones. Bound simultaneous
    // first-time decodes instead of starting dozens of codec pipelines during project open.
    private val decodePermits = java.util.concurrent.Semaphore(2, true)

    suspend fun getOrDecode(
        context: android.content.Context,
        uri: Uri,
        requestedPeakCount: Int,
        ensureActive: () -> Unit = {}
    ): List<Float> {
        require(requestedPeakCount in 1..2048) { "Invalid waveform resolution" }
        val key = uri.toString()
        val mutex = inFlight.computeIfAbsent(key) { kotlinx.coroutines.sync.Mutex() }
        mutex.lock()
        try {
            val canonical = synchronized(this) { cache.get(key) }
                ?: run {
                    decodePermits.acquire()
                    try {
                        ensureActive()
                        decodeWaveformPeaks(context, uri, CANONICAL_PEAK_COUNT, ensureActive)
                    } finally {
                        decodePermits.release()
                    }
                }.also { decoded ->
                    synchronized(this) { cache.put(key, decoded) }
                }
            if (requestedPeakCount == canonical.size) return canonical
            if (requestedPeakCount >= canonical.size) {
                return List(requestedPeakCount) { index ->
                    canonical[((index.toLong() * canonical.size) / requestedPeakCount)
                        .toInt().coerceIn(0, canonical.lastIndex)]
                }
            }
            // Preserve transients while reducing resolution: each output bin keeps the
            // maximum peak from the canonical bins that fall inside it.
            return List(requestedPeakCount) { index ->
                val first = (index.toLong() * canonical.size / requestedPeakCount).toInt()
                val end = (((index + 1L) * canonical.size + requestedPeakCount - 1L) /
                    requestedPeakCount).toInt().coerceIn(first + 1, canonical.size)
                var peak = 0f
                for (sourceIndex in first until end) peak = maxOf(peak, canonical[sourceIndex])
                peak
            }
        } finally {
            mutex.unlock()
        }
    }

    @Synchronized fun getDurationSeconds(uri: String): Double? = durationCache.get(uri)

    /** Deduplicate metadata reads too: many clips can point at the same source URI. */
    suspend fun getOrReadDurationSeconds(context: android.content.Context, uri: Uri): Double? {
        val key = uri.toString()
        val mutex = inFlight.computeIfAbsent(key) { kotlinx.coroutines.sync.Mutex() }
        mutex.lock()
        try {
            synchronized(this) { durationCache.get(key) }?.let { return it }
            val duration = readAudioSourceDurationSeconds(context, uri)
            if (duration != null && duration.isFinite() && duration > 0.0) {
                synchronized(this) { durationCache.put(key, duration) }
            }
            return duration
        } finally {
            mutex.unlock()
        }
    }

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

internal data class AudioTrimMicrosRange(val startMicros: Long, val endMicrosExclusive: Long)

/** Convert editor fractions directly against source duration, without BPM/PPQ quantization. */
internal fun audioTrimMicrosRangeFromFractions(
    startFraction: Float,
    endFraction: Float,
    sourceDurationMicros: Long
): AudioTrimMicrosRange? {
    if (sourceDurationMicros < 2L) return null
    val maximumStartFraction = (1.0 - 1.0 / sourceDurationMicros.toDouble()).coerceAtLeast(0.0)
    val start = if (startFraction.isFinite()) startFraction.toDouble().coerceIn(0.0, maximumStartFraction) else 0.0
    val minimumEnd = (start + 1.0 / sourceDurationMicros.toDouble()).coerceAtMost(1.0)
    val end = if (endFraction.isFinite()) endFraction.toDouble().coerceIn(minimumEnd, 1.0) else 1.0
    val startMicros = (sourceDurationMicros.toDouble() * start).toLong().coerceIn(0L, sourceDurationMicros - 1L)
    val endMicros = (sourceDurationMicros.toDouble() * end).toLong()
        .coerceIn(startMicros + 1L, sourceDurationMicros)
    return AudioTrimMicrosRange(startMicros, endMicros)
}

internal data class AudioTrimTickRange(val startTicks: Long, val endTicksExclusive: Long)

/** Restores editor slider fractions from persisted musical source coordinates without overflowing. */
internal fun audioTrimFractionsFromTicks(
    sourceOffsetTicks: Long,
    clipLengthTicks: Long,
    sourceDurationTicks: Long
): Pair<Float, Float> {
    if (sourceDurationTicks < 2L) return 0f to 1f
    val start = sourceOffsetTicks.coerceIn(0L, sourceDurationTicks - 1L)
    val length = clipLengthTicks.coerceAtLeast(1L)
    val remaining = sourceDurationTicks - start
    val end = start + length.coerceAtMost(remaining)
    val maximumStartFraction = (1f - 1f / sourceDurationTicks.toFloat()).coerceAtLeast(0f)
    val startFraction = (start.toDouble() / sourceDurationTicks.toDouble()).toFloat()
        .coerceIn(0f, maximumStartFraction)
    val endFraction = (end.toDouble() / sourceDurationTicks.toDouble()).toFloat()
        .coerceIn(startFraction + 1f / sourceDurationTicks.toFloat(), 1f)
    return startFraction to endFraction
}

/** Converts slider fractions to a valid non-empty source range using safe tick arithmetic. */
internal fun audioTrimRangeFromFractions(
    startFraction: Float,
    endFraction: Float,
    sourceDurationTicks: Long
): AudioTrimTickRange? {
    if (sourceDurationTicks < 2L) return null
    val maximumStartFraction = (1f - 1f / sourceDurationTicks.toFloat()).coerceAtLeast(0f)
    val safeStart = if (startFraction.isFinite()) startFraction.coerceIn(0f, maximumStartFraction) else 0f
    val minimumEndFraction = (safeStart + 1f / sourceDurationTicks.toFloat()).coerceAtMost(1f)
    val requestedEnd = if (endFraction.isFinite()) endFraction else 1f
    val safeEnd = requestedEnd.coerceIn(minimumEndFraction, 1f)
    val start = (sourceDurationTicks.toDouble() * safeStart.toDouble()).toLong()
        .coerceIn(0L, sourceDurationTicks - 1L)
    val end = (sourceDurationTicks.toDouble() * safeEnd.toDouble()).toLong()
        .coerceIn(start + 1L, sourceDurationTicks)
    return AudioTrimTickRange(start, end)
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
    peakCount: Int,
    sourceOffsetMicros: Long? = null,
    sourceLengthMicros: Long? = null
): AudioWaveformClipRange? {
    if (!sourceDurationSeconds.isFinite() || sourceDurationSeconds <= 0.0 || peakCount <= 0) return null
    val ticksPerSecond = bpm.coerceAtLeast(1).toDouble() * ppq.coerceAtLeast(1).toDouble() / 60.0
    val offsetSeconds = sourceOffsetMicros?.coerceAtLeast(0L)?.toDouble()?.div(1_000_000.0)
        ?: (sourceOffsetTicks.coerceAtLeast(0L).toDouble() / ticksPerSecond)
    val clipSeconds = sourceLengthMicros?.coerceAtLeast(1L)?.toDouble()?.div(1_000_000.0)
        ?: (clipLengthTicks.coerceAtLeast(1L).toDouble() / ticksPerSecond)
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
    ppq: Int = 960,
    sourceOffsetMicros: Long? = null,
    visibleLengthMicros: Long? = null,
    playbackBusy: Boolean = false
) {
    val context = LocalContext.current
    var peaks by remember(sourceUri) { mutableStateOf<List<Float>>(emptyList()) }
    var sourceDurationSeconds by remember(sourceUri) { mutableStateOf<Double?>(null) }
    var waveformLoading by remember(sourceUri) { mutableStateOf(false) }
    LaunchedEffect(sourceUri, playbackBusy) {
        // Audio output has priority over visual analysis. Cancel an in-flight full-file
        // waveform decode as soon as the transport is preparing/playing; retry after stop.
        if (playbackBusy) {
            waveformLoading = false
            return@LaunchedEffect
        }
        sourceDurationSeconds = null
        waveformLoading = !sourceUri.isNullOrBlank()
        if (sourceUri.isNullOrBlank()) {
            waveformLoading = false
            return@LaunchedEffect
        }
        try {
            // Give transport a short head start after import. If the user presses Play,
            // the playbackBusy key changes and cancels this pending visual-only decode.
            delay(750L)
            val loaded = withContext(Dispatchers.IO) {
                val decodeJob = currentCoroutineContext()[Job]
                val source = Uri.parse(sourceUri)
                val duration = WaveformPeakCache.getOrReadDurationSeconds(context, source)
                val cachedPeaks = WaveformPeakCache.getOrDecode(context, source, 240) {
                    decodeJob?.ensureActive()
                }
                duration to cachedPeaks
            }
            sourceDurationSeconds = loaded.first
            peaks = loaded.second
            if (loaded.second.isEmpty()) {
                StudioWaveDiagnostics.log("WARN", "WAVEFORM", "Decoder returned no peaks for audio source: ${Uri.parse(sourceUri).lastPathSegment ?: "audio"}")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            peaks = emptyList()
            sourceDurationSeconds = null
            StudioWaveDiagnostics.log("ERROR", "WAVEFORM", "Arrangement waveform decode failed: ${failure.javaClass.simpleName}: ${failure.message}")
        } finally {
            waveformLoading = false
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val accentColor = accent
        val mid = size.height / 2f
        if (peaks.isEmpty()) {
            drawLine(accentColor.copy(alpha = 0.3f), Offset(0f, mid), Offset(size.width, mid), 1f)
            val markerColor = if (waveformLoading) accentColor else Color(0xFFFF7655)
            val markerWidth = (size.width * 0.025f).coerceIn(4f, 18f)
            drawRoundRect(markerColor.copy(alpha = 0.85f), Offset(size.width * 0.04f, mid - size.height * 0.24f),
                Size(markerWidth, size.height * 0.48f), androidx.compose.ui.geometry.CornerRadius(1f))
        } else {
            val durationSeconds = sourceDurationSeconds
            val range = durationSeconds?.let {
                audioWaveformClipRange(
                    sourceOffsetTicks = sourceOffsetTicks,
                    clipLengthTicks = visibleLengthTicks,
                    bpm = bpm,
                    ppq = ppq,
                    sourceDurationSeconds = it,
                    peakCount = peaks.size,
                    sourceOffsetMicros = sourceOffsetMicros,
                    sourceLengthMicros = visibleLengthMicros
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
        shape = RoundedCornerShape(1.dp),
        border = BorderStroke(1.dp, if (active) palette.orange.copy(alpha = 0.95f) else palette.border.copy(alpha = 0.9f)),
        modifier = Modifier.size(width = 29.dp, height = 23.dp).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (active) "$label✓" else label, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
internal fun TransportAction(label: String, heightDp: Int = 23, onClick: () -> Unit) {
    val p = LocalStudioWavePalette.current
    Surface(
        color = p.control,
        contentColor = p.text,
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, p.border.copy(alpha = 0.65f)),
        modifier = Modifier.height(heightDp.dp).clickable(onClick = onClick)
    ) {
        Box(Modifier.padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

