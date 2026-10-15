package com.studiowave.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Process
import com.studiowave.app.data.MidiNote
import com.studiowave.app.data.StudioProject
import com.studiowave.app.data.StudioTrack
import com.studiowave.app.data.TrackDevice
import com.studiowave.app.data.EFFECT_DEVICE_TYPES
import com.studiowave.app.data.WaveClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.pow

/**
 * A bounded, block-mixing arrangement playback path. Media is decoded before playback
 * on Dispatchers.IO; the audio worker only mixes prepared PCM and synthesized notes.
 * This keeps content-provider/codec calls away from the realtime output thread.
 */
/** Linear stereo peak/RMS for an interleaved L,R sample buffer. */
internal data class StereoMeterLevels(
    val peakLeft: Float,
    val peakRight: Float,
    val rmsLeft: Float,
    val rmsRight: Float
)

/** Pure helper used by the realtime mixer and unit tests; it does not allocate sample buffers. */
internal fun measureStereoMeterLevels(samples: FloatArray, sampleCount: Int = samples.size): StereoMeterLevels {
    val count = sampleCount.coerceIn(0, samples.size)
    var peakLeft = 0f
    var peakRight = 0f
    var squareLeft = 0.0
    var squareRight = 0.0
    var leftFrames = 0
    var rightFrames = 0
    var i = 0
    while (i + 1 < count) {
        val left = samples[i].takeIf { it.isFinite() } ?: 0f
        val right = samples[i + 1].takeIf { it.isFinite() } ?: 0f
        peakLeft = maxOf(peakLeft, kotlin.math.abs(left))
        peakRight = maxOf(peakRight, kotlin.math.abs(right))
        squareLeft += left.toDouble() * left.toDouble()
        squareRight += right.toDouble() * right.toDouble()
        leftFrames++
        rightFrames++
        i += 2
    }
    if (i < count) {
        val left = samples[i].takeIf { it.isFinite() } ?: 0f
        peakLeft = maxOf(peakLeft, kotlin.math.abs(left))
        squareLeft += left.toDouble() * left.toDouble()
        leftFrames++
    }
    val leftDenominator = leftFrames.coerceAtLeast(1).toDouble()
    val rightDenominator = rightFrames.coerceAtLeast(1).toDouble()
    return StereoMeterLevels(
        peakLeft.coerceIn(0f, 1f), peakRight.coerceIn(0f, 1f),
        kotlin.math.sqrt(squareLeft / leftDenominator).toFloat().coerceIn(0f, 1f),
        kotlin.math.sqrt(squareRight / rightDenominator).toFloat().coerceIn(0f, 1f)
    )
}

class ArrangementPlaybackEngine(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val generation = java.util.concurrent.atomic.AtomicLong(0L)
    private val scratchOffsetsMs = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()
    @Volatile private var liveMasterGain = 0.72f
    // Peak/RMS snapshot from the final master PCM block, before 16-bit quantization.
    private val masterPeakBits = java.util.concurrent.atomic.AtomicInteger(java.lang.Float.floatToRawIntBits(0f))
    private val masterRmsBits = java.util.concurrent.atomic.AtomicInteger(java.lang.Float.floatToRawIntBits(0f))
    private val masterLufsBits = java.util.concurrent.atomic.AtomicInteger(java.lang.Float.floatToRawIntBits(MasterLoudnessMeter.SILENCE_LUFS))
    private val loudnessMeter = MasterLoudnessMeter(OUTPUT_SAMPLE_RATE)
    private data class TrackMeterBits(
        val peakLeft: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(java.lang.Float.floatToRawIntBits(0f)),
        val peakRight: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(java.lang.Float.floatToRawIntBits(0f)),
        val rmsLeft: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(java.lang.Float.floatToRawIntBits(0f)),
        val rmsRight: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(java.lang.Float.floatToRawIntBits(0f))
    )
    private val trackMeterBits = java.util.concurrent.ConcurrentHashMap<String, TrackMeterBits>()
    @Volatile private var liveMix = MixSnapshot(emptyMap(), false)

    data class MasterMeterSnapshot(val peakDbfs: Float, val rmsDbfs: Float, val momentaryLufs: Float) {
        // Convert meter values to linear amplitude for the scrolling level trace.
        val peakLinear: Float get() = Math.pow(10.0, peakDbfs.coerceIn(-120f, 0f).toDouble() / 20.0).toFloat()
        val rmsLinear: Float get() = Math.pow(10.0, rmsDbfs.coerceIn(-120f, 0f).toDouble() / 20.0).toFloat()
    }

    fun masterMeterSnapshot(): MasterMeterSnapshot {
        val peak = java.lang.Float.intBitsToFloat(masterPeakBits.get()).coerceIn(0f, 1f)
        val rms = java.lang.Float.intBitsToFloat(masterRmsBits.get()).coerceIn(0f, 1f)
        val lufs = java.lang.Float.intBitsToFloat(masterLufsBits.get()).coerceIn(-120f, 6f)
        return MasterMeterSnapshot(linearToDbfs(peak), linearToDbfs(rms), lufs)
    }

    data class TrackMeterSnapshot(
        val peakLeft: Float,
        val peakRight: Float,
        val rmsLeft: Float,
        val rmsRight: Float
    ) {
        val peak: Float get() = maxOf(peakLeft, peakRight)
        val rms: Float get() = maxOf(rmsLeft, rmsRight)
    }

    fun trackMeterSnapshot(trackId: String): TrackMeterSnapshot {
        val meter = trackMeterBits[trackId]
            ?: return TrackMeterSnapshot(0f, 0f, 0f, 0f)
        return TrackMeterSnapshot(
            java.lang.Float.intBitsToFloat(meter.peakLeft.get()).coerceIn(0f, 1f),
            java.lang.Float.intBitsToFloat(meter.peakRight.get()).coerceIn(0f, 1f),
            java.lang.Float.intBitsToFloat(meter.rmsLeft.get()).coerceIn(0f, 1f),
            java.lang.Float.intBitsToFloat(meter.rmsRight.get()).coerceIn(0f, 1f)
        )
    }

    private fun publishTrackMeter(
        trackId: String, peakLeft: Float, peakRight: Float, rmsLeft: Float, rmsRight: Float
    ) {
        val meter = trackMeterBits.computeIfAbsent(trackId) { TrackMeterBits() }
        meter.peakLeft.set(java.lang.Float.floatToRawIntBits(peakLeft.coerceIn(0f, 1f)))
        meter.peakRight.set(java.lang.Float.floatToRawIntBits(peakRight.coerceIn(0f, 1f)))
        meter.rmsLeft.set(java.lang.Float.floatToRawIntBits(rmsLeft.coerceIn(0f, 1f)))
        meter.rmsRight.set(java.lang.Float.floatToRawIntBits(rmsRight.coerceIn(0f, 1f)))
    }

    private fun publishMasterMeter(peak: Float, rms: Float, momentaryLufs: Float) {
        masterPeakBits.set(java.lang.Float.floatToRawIntBits(peak.coerceIn(0f, 1f)))
        masterRmsBits.set(java.lang.Float.floatToRawIntBits(rms.coerceIn(0f, 1f)))
        masterLufsBits.set(java.lang.Float.floatToRawIntBits(momentaryLufs.coerceIn(-120f, 6f)))
    }

    private fun linearToDbfs(value: Float): Float =
        if (value <= 0.000001f) -120f else (20f * kotlin.math.log10(value)).coerceIn(-120f, 0f)

    private fun dbfsToLinear(value: Float): Float =
        Math.pow(10.0, value.coerceIn(-120f, 0f).toDouble() / 20.0).toFloat()
    private val audioAssetCache = object : android.util.LruCache<String, AudioAsset>(MAX_ASSET_CACHE_KIB) {
        override fun sizeOf(key: String, value: AudioAsset): Int =
            (value.pcmStereo.size.toLong() * 2L / 1024L).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
    @Volatile private var worker: Thread? = null
    @Volatile private var output: NativeAudioEngine? = null

    private data class AudioAsset(
        val sampleRate: Int,
        val pcmStereo: ShortArray,
        val frames: Int
    )

    private data class AudioEvent(
        val startFrame: Long,
        val endFrame: Long,
        val sourceOffsetFrame: Long,
        val sourceWindowFrames: Long,
        val track: StudioTrack,
        val clip: WaveClip,
        val asset: AudioAsset
    )

    private data class SamplerInstrument(
        val asset: AudioAsset,
        val looping: Boolean,
        val pitchSemitones: Int,
        val fineTuneCents: Int,
        val attackMs: Int,
        val decayMs: Int,
        val sustain: Float,
        val releaseMs: Int
    )

    private data class NoteEvent(
        val startFrame: Long,
        val endFrame: Long,
        val pitch: Int,
        val velocity: Int,
        val pan: Float,
        val track: StudioTrack,
        val instrument: SamplerInstrument? = null
    )

    private data class DrumEvent(
        val startFrame: Long,
        val track: StudioTrack
    )

    private data class Plan(
        val sampleRate: Int,
        val bpm: Int,
        val ppq: Int,
        val numerator: Int,
        val denominator: Int,
        val totalFrames: Long,
        val audio: List<AudioEvent>,
        val notes: List<NoteEvent>,
        val drums: List<DrumEvent>
    )

    private data class TrackMix(
        val volume: Float,
        val pan: Float,
        val muted: Boolean,
        val solo: Boolean,
        val devices: List<TrackDevice>
    )

    private data class MixSnapshot(
        val tracks: Map<String, TrackMix>,
        val soloActive: Boolean
    )

    suspend fun start(
        project: StudioProject,
        masterGain: Float = 0.72f,
        onCompleted: () -> Unit = {},
        onError: (String) -> Unit = {},
        startAtTick: Long = 0L,
        onProgress: (Long) -> Unit = {},
        parallelPreparation: Boolean = false,
        metronomeEnabled: Boolean = false,
        metronomeLevel: Float = 0.25f,
        queueTargetFrames: Int = 1024
    ) {
        check(!closed.get()) { "Playback engine is closed" }
        val token = generation.incrementAndGet()
        scratchOffsetsMs.clear()
        stopOutputOnly()
        liveMasterGain = masterGain.coerceIn(0f, 1f)
        loudnessMeter.reset()
        publishMasterMeter(0f, 0f, MasterLoudnessMeter.SILENCE_LUFS)
        project.tracks.forEach { track ->
            publishTrackMeter(track.id, 0f, 0f, 0f, 0f)
        }
        updateMix(project)
        val plan = withContext(Dispatchers.IO) { buildPlan(project, token, parallelPreparation) }
        check(!closed.get()) { "Playback engine is closed" }
        check(generation.get() == token) { "Playback start was cancelled" }
        val startFrame = ArrangementTiming.ticksToFrames(
            startAtTick.coerceAtLeast(0L), plan.bpm, plan.ppq, plan.sampleRate
        ).coerceIn(0L, plan.totalFrames)
        runCatching {
            onProgress(ArrangementTiming.framesToTicks(startFrame, plan.bpm, plan.ppq, plan.sampleRate))
        }
        val nativeOutput = NativeAudioEngine()
        nativeOutput.start(plan.sampleRate, queueTargetFrames.coerceIn(256, 4096))
        synchronized(this) {
            if (closed.get() || generation.get() != token) {
                nativeOutput.close()
                error("Playback start was cancelled")
            }
            output = nativeOutput
            running.set(true)
            try {
                worker = Thread({
                    Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                    renderPlan(
                        nativeOutput, plan, token, startFrame, onProgress, onCompleted, onError,
                        metronomeEnabled, metronomeLevel.coerceIn(0f, 1f)
                    )
                }, "StudioWave-Arrangement").also { it.start() }
            } catch (failure: Throwable) {
                running.set(false)
                output = null
                runCatching { nativeOutput.close() }
                throw failure
            }
        }
    }

    private suspend fun buildPlan(project: StudioProject, token: Long, parallelPreparation: Boolean): Plan {
        fun ensureCurrent() {
            check(!closed.get() && generation.get() == token) { "Playback preparation cancelled" }
        }
        val bpm = project.bpm.coerceIn(40, 240)
        val ppq = project.ppq.coerceIn(24, 3840)
        val sampleRate = OUTPUT_SAMPLE_RATE
        fun ticksToFrames(ticks: Long): Long =
            ArrangementTiming.ticksToFrames(ticks, bpm, ppq, sampleRate)

        val tracks = project.tracks
        val maxAllowedTick = ArrangementTiming.maxArrangementTick(
            project.numerator, project.denominator, ppq, MAX_ARRANGEMENT_BARS
        )
        val defaultBarTicks = maxAllowedTick / MAX_ARRANGEMENT_BARS
        val maxTick = tracks.asSequence().flatMap { it.clips.asSequence() }.maxOfOrNull {
            val start = it.startTick.coerceAtLeast(0L).coerceAtMost(Long.MAX_VALUE / 2)
            val length = it.lengthTicks.coerceAtLeast(1L).coerceAtMost(Long.MAX_VALUE / 2)
            if (start > Long.MAX_VALUE - length) Long.MAX_VALUE else start + length
        } ?: defaultBarTicks
        // Keep playback aligned with the same 1800-bar musical limit as Arrangement.
        // A fixed seconds cap would truncate valid projects at slower tempos or different meters.
        val boundedMaxTick = maxTick.coerceIn(1L, maxAllowedTick)
        val totalFrames = ticksToFrames(boundedMaxTick).coerceAtLeast(1L)

        val requestedTicksByUri = linkedMapOf<String, Long>()
        tracks.filter { it.kind == "audio" }.forEach { track ->
            track.clips.forEach clipLoop@{ clip ->
                val uri = clip.sourceUri?.takeIf { it.isNotBlank() } ?: return@clipLoop
                // Absolute source coordinates are tempo-independent. Convert only for the
                // legacy "unknown source duration" fallback used while preparing the PCM cache.
                val offsetMicros = clip.sourceOffsetMicros
                    ?: ArrangementTiming.ticksToMicros(clip.sourceOffsetTicks, bpm, ppq)
                val lengthMicros = clip.sourceLengthMicros
                    ?: ArrangementTiming.ticksToMicros(clip.lengthTicks.coerceAtLeast(1L), bpm, ppq)
                val sourceEndMicros = ArrangementTiming.addTicksSaturated(offsetMicros, lengthMicros)
                val sourceEndTicks = ArrangementTiming.microsToTicks(sourceEndMicros, bpm, ppq)
                requestedTicksByUri[uri] = max(requestedTicksByUri[uri] ?: 0L, sourceEndTicks)
            }
        }
        data class SourceRequest(
            val uriString: String,
            val sourceSeconds: Double,
            val requestedFrames: Int,
            val cacheKey: String
        )
        val sourceRequests = ArrayList<SourceRequest>(requestedTicksByUri.size)
        var estimatedBytes = 0L
        requestedTicksByUri.forEach { (uriString, ticks) ->
            ensureCurrent()
            // Metadata inspection and the memory-budget check happen before any parallel
            // decode starts, so concurrent preparation cannot oversubscribe the PCM budget.
            val requiredSeconds = (ticks.toDouble() * 60.0 / (bpm.toDouble() * ppq))
                .coerceIn(0.05, MAX_SOURCE_SECONDS.toDouble())
            val sourceSeconds = readSourceDurationSeconds(Uri.parse(uriString))
                ?.coerceIn(0.05, MAX_SOURCE_SECONDS.toDouble()) ?: requiredSeconds
            val requestedFrames = ceil(sourceSeconds * sampleRate).toLong()
                .coerceIn(1L, sampleRate.toLong() * MAX_SOURCE_SECONDS).toInt()
            estimatedBytes += requestedFrames.toLong() * 4L
            require(estimatedBytes <= MAX_TOTAL_PCM_BYTES) {
                "Arrangement audio cache exceeds the safe memory budget; shorten clips or reduce the number of source files"
            }
            sourceRequests += SourceRequest(uriString, sourceSeconds, requestedFrames,
                "$uriString|$sampleRate|$requestedFrames")
        }

        suspend fun prepareSource(request: SourceRequest): Pair<String, AudioAsset> {
            ensureCurrent()
            val cached = synchronized(audioAssetCache) { audioAssetCache.get(request.cacheKey) }
            if (cached != null) return request.uriString to cached
            val decoded = decodeAudioAsset(Uri.parse(request.uriString), request.sourceSeconds, token)
            ensureCurrent()
            // LruCache is not thread-safe; decoding happens outside the monitor, only cache
            // lookup/insert is synchronized. Each distinct source URI has only one request.
            synchronized(audioAssetCache) { audioAssetCache.put(request.cacheKey, decoded) }
            return request.uriString to decoded
        }

        val assets = HashMap<String, AudioAsset>()
        val preparedSources = if (parallelPreparation && sourceRequests.size > 1) {
            coroutineScope {
                sourceRequests.map { request -> async(Dispatchers.IO) { prepareSource(request) } }.awaitAll()
            }
        } else {
            sourceRequests.map { request -> prepareSource(request) }
        }
        preparedSources.forEach { (uri, asset) -> assets[uri] = asset }

        val audioEvents = ArrayList<AudioEvent>()
        val notes = ArrayList<NoteEvent>()
        val drums = ArrayList<DrumEvent>()
        val samplerPreferences = appContext.getSharedPreferences("studiowave_directwave", Context.MODE_PRIVATE)
        val instrumentsByTrack = HashMap<String, SamplerInstrument>()
        tracks.asSequence().filter { it.kind == "midi" }.forEach { track ->
            ensureCurrent()
            val uriText = samplerPreferences.getString("sample.${track.id}", null)?.takeIf { it.isNotBlank() }
                ?: return@forEach
            val uri = Uri.parse(uriText)
            // Bound instrument decoding to 30 seconds and include it in the shared PCM budget.
            val duration = (readSourceDurationSeconds(uri) ?: 10.0).coerceIn(0.05, 30.0)
            val frameCount = ceil(duration * sampleRate).toLong()
                .coerceIn(1L, sampleRate.toLong() * 30L).toInt()
            estimatedBytes += frameCount.toLong() * 4L
            require(estimatedBytes <= MAX_TOTAL_PCM_BYTES) {
                "Instrument samples exceed the safe PCM memory budget; shorten samples or use fewer instrument tracks"
            }
            val cacheKey = "directwave:$uriText|$sampleRate|$frameCount"
            val asset = audioAssetCache.get(cacheKey) ?: decodeAudioAsset(uri, duration, token).also { decoded ->
                ensureCurrent()
                audioAssetCache.put(cacheKey, decoded)
            }
            instrumentsByTrack[track.id] = SamplerInstrument(
                asset = asset,
                looping = samplerPreferences.getBoolean("loop.${track.id}", false),
                pitchSemitones = samplerPreferences.getInt("pitch.${track.id}", 0).coerceIn(-24, 24),
                fineTuneCents = samplerPreferences.getInt("fineTune.${track.id}", 0).coerceIn(-100, 100),
                attackMs = samplerPreferences.getInt("attack.${track.id}", 5).coerceIn(0, 5000),
                decayMs = samplerPreferences.getInt("decay.${track.id}", 120).coerceIn(0, 5000),
                sustain = (samplerPreferences.getInt("sustain.${track.id}", 80) / 100f).coerceIn(0f, 1f),
                releaseMs = samplerPreferences.getInt("release.${track.id}", 180).coerceIn(0, 5000)
            )
        }
        tracks.forEach { track ->
            ensureCurrent()
            track.clips.forEach clipLoop@{ clip ->
                ensureCurrent()
                val clipStart = ticksToFrames(clip.startTick)
                val clipLength = ticksToFrames(clip.lengthTicks.coerceAtLeast(1L))
                val clipEnd = min(totalFrames, if (clipStart > Long.MAX_VALUE - clipLength) Long.MAX_VALUE else clipStart + clipLength)
                if (clipEnd <= clipStart) return@clipLoop
                when (track.kind) {
                    "audio" -> {
                        val source = clip.sourceUri ?: return@clipLoop
                        val asset = assets[source] ?: return@clipLoop
                        // Source trim is stored in absolute microseconds so changing BPM
                        // changes arrangement timing, not the source sample selected by the trim.
                        val sourceOffsetMicros = clip.sourceOffsetMicros
                            ?: ArrangementTiming.ticksToMicros(clip.sourceOffsetTicks, bpm, ppq)
                        val sourceLengthMicros = clip.sourceLengthMicros
                            ?: ArrangementTiming.ticksToMicros(clip.lengthTicks.coerceAtLeast(1L), bpm, ppq)
                        val sourceOffsetFrame = ArrangementTiming.microsToFrames(
                            sourceOffsetMicros, asset.sampleRate
                        ).coerceAtLeast(0L)
                        // Invalid persisted trims must not silently clamp to and repeat the
                        // final source sample. Ignore clips whose start is outside the source.
                        if (sourceOffsetFrame >= asset.frames.toLong()) return@clipLoop
                        val sourceWindowFrames = ArrangementTiming.microsToFrames(
                            sourceLengthMicros, asset.sampleRate
                        ).coerceAtLeast(1L)
                        audioEvents.add(AudioEvent(
                            clipStart, clipEnd, sourceOffsetFrame, sourceWindowFrames,
                            track, clip, asset
                        ))
                    }
                    "midi" -> clip.midiNotes.forEach noteLoop@{ note ->
                        val noteOffset = ticksToFrames(note.startTick)
                        val noteStart = if (clipStart > Long.MAX_VALUE - noteOffset) Long.MAX_VALUE else clipStart + noteOffset
                        val noteDuration = ticksToFrames(note.durationTicks.coerceAtLeast(1L))
                        val noteEnd = min(clipEnd, if (noteStart > Long.MAX_VALUE - noteDuration) Long.MAX_VALUE else noteStart + noteDuration)
                        if (noteEnd > noteStart) {
                            notes.add(NoteEvent(
                                noteStart, noteEnd, note.pitch.coerceIn(0, 127),
                                note.velocity.coerceIn(1, 127), note.pan.coerceIn(-1f, 1f), track,
                                instrumentsByTrack[track.id]
                            ))
                        }
                    }
                    "drum" -> {
                        val steps = clip.drumSteps
                        steps.forEachIndexed { step, active ->
                            if (active) {
                                val tick = clip.startTick + (clip.lengthTicks.coerceAtLeast(1L) * step / steps.size.coerceAtLeast(1))
                                val frame = ticksToFrames(tick)
                                if (frame < totalFrames) drums.add(DrumEvent(frame, track))
                            }
                        }
                    }
                }
            }
        }
        return Plan(
            sampleRate,
            bpm,
            ppq,
            project.numerator.coerceIn(1, 32),
            project.denominator.coerceIn(1, 32),
            totalFrames,
            audioEvents.sortedBy { it.startFrame },
            notes.sortedBy { it.startFrame },
            drums.sortedBy { it.startFrame }
        )
    }

    /** Reads original media duration on the IO preparation dispatcher, never the render thread. */
    private fun readSourceDurationSeconds(uri: Uri): Double? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?.div(1000.0)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeAudioAsset(uri: Uri, durationSeconds: Double, token: Long): AudioAsset {
        fun ensureCurrent() {
            check(!closed.get() && generation.get() == token) { "Playback preparation cancelled" }
        }
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(appContext, uri, null)
            var selected = -1
            var sourceFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    selected = i
                    sourceFormat = candidate
                    break
                }
            }
            val format = sourceFormat ?: error("Audio source has no decodable audio stream: $uri")
            extractor.selectTrack(selected)
            val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceIn(8000, 192000)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
            val sampleRate = OUTPUT_SAMPLE_RATE
            val maxFrames = ceil(durationSeconds * sampleRate).toInt().coerceIn(1, sampleRate * MAX_SOURCE_SECONDS)
            val pcmOut = ShortArray(maxFrames * 2)
            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio MIME"))
            decoder.configure(format, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var outputChannels = channels
            var outputRate = sourceRate
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var framesWritten = 0
            var stagnant = 0

            while (!outputDone && framesWritten < maxFrames) {
                ensureCurrent()
                var progress = false
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex) ?: error("Audio input buffer unavailable")
                        input.clear()
                        val bytes = extractor.readSampleData(input, 0)
                        if (bytes < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, bytes, extractor.sampleTime.coerceAtLeast(0L), 0)
                            extractor.advance()
                        }
                        progress = true
                    }
                }

                when (val outIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val decodedFormat = decoder.outputFormat
                        if (decodedFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) outputRate = decodedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceIn(8000, 192000)
                        if (decodedFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) outputChannels = decodedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
                        if (decodedFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = decodedFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        progress = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outIndex >= 0) {
                        try {
                            val outputBuffer = decoder.getOutputBuffer(outIndex)
                            if (outputBuffer != null && info.size > 0) {
                                val bytesPerSample = when (encoding) {
                                    AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
                                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                                    AudioFormat.ENCODING_PCM_8BIT -> 1
                                    else -> 2
                                }
                                val bytes = outputBuffer.duplicate().order(ByteOrder.nativeOrder())
                                bytes.position(info.offset)
                                bytes.limit(info.offset + info.size)
                                val pcm = bytes.slice().order(ByteOrder.nativeOrder())
                                val frames = (info.size / bytesPerSample) / outputChannels
                                val baseFrame = (info.presentationTimeUs * sampleRate / 1_000_000L).coerceAtLeast(0L)
                                for (frame in 0 until frames) {
                                    if ((baseFrame + frame.toLong() * sampleRate / outputRate) >= maxFrames) break
                                    val targetFrame = baseFrame + frame.toLong() * sampleRate / outputRate
                                    if (targetFrame < 0) continue
                                    val index = frame * outputChannels
                                    val left = readPcmSample(pcm, index, encoding)
                                    val right = if (outputChannels > 1) readPcmSample(pcm, index + 1, encoding) else left
                                    val out = targetFrame.toInt() * 2
                                    pcmOut[out] = (left * 32767f).toInt().coerceIn(-32767, 32767).toShort()
                                    pcmOut[out + 1] = (right * 32767f).toInt().coerceIn(-32767, 32767).toShort()
                                    framesWritten = max(framesWritten, targetFrame.toInt() + 1)
                                }
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        } finally {
                            decoder.releaseOutputBuffer(outIndex, false)
                        }
                        progress = true
                    }
                }
                if (progress) stagnant = 0 else stagnant++
                if (stagnant > 500) error("Audio decoder stopped making progress")
            }
            check(framesWritten > 0) { "No PCM audio samples could be decoded from $uri" }
            return AudioAsset(OUTPUT_SAMPLE_RATE, pcmOut, framesWritten)
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun readPcmSample(buffer: java.nio.ByteBuffer, sampleIndex: Int, encoding: Int): Float =
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> buffer.getFloat(sampleIndex * 4).coerceIn(-1f, 1f)
            AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get(sampleIndex).toInt() and 0xFF) - 128) / 128f
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                val offset = sampleIndex * 3
                var value = (buffer.get(offset).toInt() and 0xFF) or
                    ((buffer.get(offset + 1).toInt() and 0xFF) shl 8) or
                    (buffer.get(offset + 2).toInt() shl 16)
                if (value and 0x800000 != 0) value = value or -0x1000000
                value / 8388608f
            }
            AudioFormat.ENCODING_PCM_32BIT -> buffer.getInt(sampleIndex * 4) / 2147483648f
            else -> buffer.getShort(sampleIndex * 2) / 32768f
        }

    /** Latest native output health snapshot; useful for diagnostics and device stress tests. */
    data class OutputDiagnostics(val queuedFrames: Int, val playedFrames: Long, val underruns: Long)

    fun outputDiagnostics(): OutputDiagnostics {
        val current = output
        return if (current == null) OutputDiagnostics(0, 0L, 0L)
        else OutputDiagnostics(current.queuedFrames(), current.playedFrames(), current.underrunCount())
    }

    private fun renderPlan(
        track: NativeAudioEngine,
        plan: Plan,
        token: Long,
        startFrame: Long,
        onProgress: (Long) -> Unit,
        onCompleted: () -> Unit,
        onError: (String) -> Unit,
        metronomeEnabled: Boolean,
        metronomeLevel: Float
    ) {
        val mix = FloatArray(BLOCK_FRAMES * 2)
        val pcm = ShortArray(BLOCK_FRAMES * 2)
        val perTrackMix = HashMap<String, FloatArray>()
        val deviceDsp = HashMap<String, TrackDeviceDsp>()
        val sampleRate = plan.sampleRate
        val scheduledAudio = plan.audio
        val scheduledNotes = plan.notes
        val scheduledDrums = plan.drums
        val playingAudio = ArrayList<AudioEvent>()
        val playingNotes = ArrayList<NoteEvent>()
        val playingDrums = ArrayList<DrumEvent>()
        var audioCursor = 0
        var noteCursor = 0
        var drumCursor = 0
        var position = startFrame.coerceIn(0L, plan.totalFrames)

        // When seeking into an active clip/note/drum hit, start with that event
        // already active so its source/envelope offset remains musically correct.
        while (audioCursor < scheduledAudio.size && scheduledAudio[audioCursor].startFrame < position) {
            val event = scheduledAudio[audioCursor++]
            if (event.endFrame > position) playingAudio.add(event)
        }
        while (noteCursor < scheduledNotes.size && scheduledNotes[noteCursor].startFrame < position) {
            val event = scheduledNotes[noteCursor++]
            if (event.endFrame > position) playingNotes.add(event)
        }
        while (drumCursor < scheduledDrums.size && scheduledDrums[drumCursor].startFrame < position) {
            val event = scheduledDrums[drumCursor++]
            if (event.startFrame + DRUM_DURATION_FRAMES > position) playingDrums.add(event)
        }

        var lastReportedFrame = -1L
        fun reportPlaybackPosition(force: Boolean = false) {
            // Native callback consumption tracks frames read from the PCM queue.
            val playedFrames = track.playedFrames()
            val absoluteFrame = (startFrame + playedFrames).coerceAtMost(plan.totalFrames)
            val reportInterval = (sampleRate / 30).toLong().coerceAtLeast(1L)
            if (force || lastReportedFrame < 0L || absoluteFrame - lastReportedFrame >= reportInterval) {
                lastReportedFrame = absoluteFrame
                val tick = ArrangementTiming.framesToTicks(absoluteFrame, plan.bpm, plan.ppq, sampleRate)
                runCatching { onProgress(tick) }
            }
        }
        try {
            while (running.get() && generation.get() == token && position < plan.totalFrames) {
                val frames = min(BLOCK_FRAMES.toLong(), plan.totalFrames - position).toInt()
                val sampleCount = frames * 2
                java.util.Arrays.fill(mix, 0, sampleCount, 0f)
                perTrackMix.values.forEach { java.util.Arrays.fill(it, 0, sampleCount, 0f) }
                val blockEnd = position + frames
                val mixState = liveMix

                playingAudio.removeAll { it.endFrame <= position }
                while (audioCursor < scheduledAudio.size && scheduledAudio[audioCursor].startFrame < blockEnd) {
                    playingAudio.add(scheduledAudio[audioCursor++])
                }
                playingNotes.removeAll { it.endFrame <= position }
                while (noteCursor < scheduledNotes.size && scheduledNotes[noteCursor].startFrame < blockEnd) {
                    playingNotes.add(scheduledNotes[noteCursor++])
                }
                playingDrums.removeAll { it.startFrame + DRUM_DURATION_FRAMES <= position }
                while (drumCursor < scheduledDrums.size && scheduledDrums[drumCursor].startFrame < blockEnd) {
                    playingDrums.add(scheduledDrums[drumCursor++])
                }

                for (event in playingAudio) {
                    val channelMix = mixState.tracks[event.track.id] ?: continue
                    if (!ArrangementTiming.trackIsAudible(channelMix.muted, channelMix.solo, mixState.soloActive)) continue
                    val from = max(position, event.startFrame)
                    val to = min(blockEnd, event.endFrame)
                    val trackGain = channelMix.volume * liveMasterGain
                    val pan = channelMix.pan
                    val leftPan = if (pan > 0f) 1f - pan else 1f
                    val rightPan = if (pan < 0f) 1f + pan else 1f
                    val channelBuffer = perTrackMix.getOrPut(event.track.id) { FloatArray(BLOCK_FRAMES * 2) }
                    // Read the atomic scrub offset once per audio event/block, not per sample.
                    val scratchMs = scratchOffsetsMs[event.track.id]?.get() ?: 0L
                    val scratchFrames = scratchMs * event.asset.sampleRate / 1_000L
                    for (absolute in from until to) {
                        val relative = absolute - event.startFrame
                        val relativeAssetFrame = (relative.toDouble() *
                            event.asset.sampleRate.toDouble() / sampleRate.toDouble()).toLong()
                        val rawSourceFrame = event.sourceOffsetFrame + relativeAssetFrame + scratchFrames
                        if (event.asset.frames <= 0) continue
                        // Loop only inside the clip's selected source window. Wrapping across
                        // the whole file would make playback leak into audio removed by trim.
                        val sourceFrame = ArrangementTiming.sourceFrameWithinTrim(
                            rawSourceFrame = rawSourceFrame,
                            sourceOffsetFrame = event.sourceOffsetFrame,
                            requestedWindowFrames = event.sourceWindowFrames,
                            assetFrames = event.asset.frames.toLong()
                        )?.toInt() ?: continue
                        val outIndex = ((absolute - position) * 2).toInt()
                        val sourceIndex = sourceFrame * 2
                        val sourceLeft = event.asset.pcmStereo[sourceIndex] / 32768f * channelMix.volume * leftPan
                        val sourceRight = event.asset.pcmStereo[sourceIndex + 1] / 32768f * channelMix.volume * rightPan
                        val leftSample = sourceLeft * liveMasterGain
                        val rightSample = sourceRight * liveMasterGain
                        mix[outIndex] += leftSample
                        mix[outIndex + 1] += rightSample
                        channelBuffer[outIndex] += sourceLeft
                        channelBuffer[outIndex + 1] += sourceRight
                    }
                }

                for (note in playingNotes) {
                    val channelMix = mixState.tracks[note.track.id] ?: continue
                    if (!ArrangementTiming.trackIsAudible(channelMix.muted, channelMix.solo, mixState.soloActive)) continue
                    val from = max(position, note.startFrame)
                    val to = min(blockEnd, note.endFrame)
                    val instrument = note.instrument
                    val frequency = 440.0 * 2.0.pow((note.pitch - 69) / 12.0)
                    val channelGain = channelMix.volume * (note.velocity / 127f) * 0.19f
                    val pan = (channelMix.pan + note.pan).coerceIn(-1f, 1f)
                    val leftPan = if (pan > 0f) 1f - pan else 1f
                    val rightPan = if (pan < 0f) 1f + pan else 1f
                    val channelBuffer = perTrackMix.getOrPut(note.track.id) { FloatArray(BLOCK_FRAMES * 2) }
                    val noteLength = (note.endFrame - note.startFrame).coerceAtLeast(1L)
                    val instrumentRate = instrument?.let {
                        2.0.pow(((note.pitch - 60 + it.pitchSemitones).toDouble() + it.fineTuneCents / 100.0) / 12.0)
                    } ?: 1.0
                    val attackFrames = instrument?.let { it.attackMs.toLong() * sampleRate / 1000L } ?: 0L
                    val decayFrames = instrument?.let { it.decayMs.toLong() * sampleRate / 1000L } ?: 0L
                    val releaseFrames = instrument?.let { it.releaseMs.toLong() * sampleRate / 1000L } ?: 0L
                    for (absolute in from until to) {
                        val elapsed = absolute - note.startFrame
                        val outIndex = ((absolute - position) * 2).toInt()
                        if (instrument != null) {
                            var sourceFrame = (elapsed.toDouble() * instrumentRate).toLong()
                            if (instrument.looping) {
                                sourceFrame %= instrument.asset.frames.toLong().coerceAtLeast(1L)
                            } else if (sourceFrame >= instrument.asset.frames) {
                                continue
                            }
                            val attackGain = if (attackFrames <= 0L) 1f
                                else (elapsed.toFloat() / attackFrames.toFloat()).coerceIn(0f, 1f)
                            val decayProgress = if (decayFrames <= 0L) 1f
                                else ((elapsed - attackFrames).toFloat() / decayFrames.toFloat()).coerceIn(0f, 1f)
                            val sustainGain = if (elapsed <= attackFrames) attackGain
                                else 1f - (1f - instrument.sustain) * decayProgress
                            val releaseGain = if (releaseFrames <= 0L) 1f
                                else ((note.endFrame - absolute).toFloat() / releaseFrames.toFloat()).coerceIn(0f, 1f)
                            val envelope = min(sustainGain, releaseGain)
                            val sampleIndex = sourceFrame.toInt().coerceIn(0, instrument.asset.frames - 1) * 2
                            val velocityGain = channelMix.volume * (note.velocity / 127f) * envelope
                            val sourceLeft = instrument.asset.pcmStereo[sampleIndex] / 32768f * velocityGain * leftPan
                            val sourceRight = instrument.asset.pcmStereo[sampleIndex + 1] / 32768f * velocityGain * rightPan
                            mix[outIndex] += sourceLeft * liveMasterGain
                            mix[outIndex + 1] += sourceRight * liveMasterGain
                            channelBuffer[outIndex] += sourceLeft
                            channelBuffer[outIndex + 1] += sourceRight
                        } else {
                            val attack = min(1f, elapsed.toFloat() / (sampleRate * 0.008f))
                            val release = min(1f, (noteLength - elapsed).toFloat() / (sampleRate * 0.035f))
                            val envelope = min(attack, release)
                            val channelWave = sin(2.0 * PI * frequency * elapsed / sampleRate).toFloat() * channelGain * envelope
                            val wave = channelWave * liveMasterGain
                            val sourceLeft = channelWave * leftPan
                            val sourceRight = channelWave * rightPan
                            mix[outIndex] += wave * leftPan
                            mix[outIndex + 1] += wave * rightPan
                            channelBuffer[outIndex] += sourceLeft
                            channelBuffer[outIndex + 1] += sourceRight
                        }
                    }
                }

                for (drum in playingDrums) {
                    val channelMix = mixState.tracks[drum.track.id] ?: continue
                    if (!ArrangementTiming.trackIsAudible(channelMix.muted, channelMix.solo, mixState.soloActive)) continue
                    val hitEnd = drum.startFrame + DRUM_DURATION_FRAMES
                    val from = max(position, drum.startFrame)
                    val to = min(blockEnd, hitEnd)
                    val channelGain = channelMix.volume * 0.38f
                    val gain = channelGain * liveMasterGain
                    val pan = channelMix.pan
                    val leftPan = if (pan > 0f) 1f - pan else 1f
                    val rightPan = if (pan < 0f) 1f + pan else 1f
                    val channelBuffer = perTrackMix.getOrPut(drum.track.id) { FloatArray(BLOCK_FRAMES * 2) }
                    for (absolute in from until to) {
                        val t = (absolute - drum.startFrame).toDouble() / sampleRate
                        val frequency = 48.0 + 110.0 * exp(-t * 22.0)
                        val channelWave = sin(2.0 * PI * frequency * t).toFloat() * exp(-t * 20.0).toFloat() * channelGain
                        val wave = channelWave * liveMasterGain
                        val outIndex = ((absolute - position) * 2).toInt()
                        val sourceLeft = channelWave * leftPan
                        val sourceRight = channelWave * rightPan
                        mix[outIndex] += wave * leftPan
                        mix[outIndex + 1] += wave * rightPan
                        channelBuffer[outIndex] += sourceLeft
                        channelBuffer[outIndex + 1] += sourceRight
                    }
                }

                // The metronome is a deterministic master-bus click. It is generated in the
                // render worker, not by a UI timer, so it stays aligned to the musical timebase.
                if (metronomeEnabled && metronomeLevel > 0f) {
                    val beatTicks = (plan.ppq.toLong() * 4L / plan.denominator.coerceIn(1, 32).toLong()).coerceAtLeast(1L)
                    val beatFrames = ArrangementTiming.ticksToFrames(beatTicks, plan.bpm, plan.ppq, sampleRate).coerceAtLeast(1L)
                    val barFrames = (beatFrames * plan.numerator.coerceIn(1, 32).toLong()).coerceAtLeast(1L)
                    val clickDuration = (sampleRate * 0.018f).toInt().coerceAtLeast(1)
                    for (frameOffset in 0 until frames) {
                        val absoluteFrame = position + frameOffset
                        val inBeat = absoluteFrame % beatFrames
                        if (inBeat >= clickDuration) continue
                        val beatIndex = absoluteFrame / beatFrames
                        val downbeat = (absoluteFrame % barFrames) < beatFrames
                        val frequency = if (downbeat) 2200.0 else 1650.0
                        val envelope = 1.0 - inBeat.toDouble() / clickDuration.toDouble()
                        val click = (sin(2.0 * PI * frequency * inBeat / sampleRate) *
                            envelope * metronomeLevel * if (downbeat) 0.30 else 0.18).toFloat()
                        val outIndex = frameOffset * 2
                        mix[outIndex] += click
                        mix[outIndex + 1] += click
                    }
                }

                // Apply enabled track inserts in chain order to the per-track bus.
                perTrackMix.forEach { (trackId, channel) ->
                    val trackState = mixState.tracks[trackId] ?: return@forEach
                    if (trackState.devices.any { it.enabled && it.type in EFFECT_DEVICE_TYPES }) {
                        deviceDsp.getOrPut(trackId) { TrackDeviceDsp(sampleRate) }
                            .process(channel, mix, sampleCount, liveMasterGain, trackState.devices)
                    }
                }
                // Per-channel meters are calculated from each track's summed pre-master stereo bus.
                // Buffers are reused across blocks; only one meter snapshot is published per track/block.
                var blockPeak = 0f
                var blockSquares = 0.0
                for (i in 0 until sampleCount) {
                    val sample = mix[i].coerceIn(-0.98f, 0.98f)
                    mix[i] = sample // Meter the same clipped master signal queued for native output.
                    val absolute = kotlin.math.abs(sample)
                    if (absolute > blockPeak) blockPeak = absolute
                    blockSquares += sample.toDouble() * sample.toDouble()
                    pcm[i] = (sample * 32767f).toInt().toShort()
                }
                val momentaryLufs = loudnessMeter.processBlock(mix, sampleCount)
                publishMasterMeter(blockPeak, kotlin.math.sqrt(blockSquares / sampleCount.coerceAtLeast(1)).toFloat(), momentaryLufs)
                perTrackMix.forEach { (id, channel) ->
                    val levels = measureStereoMeterLevels(channel, sampleCount)
                    publishTrackMeter(id, levels.peakLeft, levels.peakRight, levels.rmsLeft, levels.rmsRight)
                }
                // A silent/muted channel should not leave an old level stuck on screen.
                mixState.tracks.keys.forEach { id ->
                    if (id !in perTrackMix) publishTrackMeter(id, 0f, 0f, 0f, 0f)
                }
                if (!track.queuePcm(pcm, sampleCount) { running.get() && generation.get() == token }) break
                position = blockEnd
                reportPlaybackPosition()
            }
            if (position >= plan.totalFrames && generation.get() == token && running.get()) {
                val expectedPlayedFrames = (plan.totalFrames - startFrame).coerceAtLeast(0L)
                val drainDeadlineNanos = System.nanoTime() + 5_000_000_000L
                // Wait until Oboe has consumed queued PCM before reporting completion.
                while (running.get() && generation.get() == token) {
                    reportPlaybackPosition()
                    val playedFrames = track.playedFrames()
                    if (playedFrames >= expectedPlayedFrames) break
                    if (System.nanoTime() >= drainDeadlineNanos) {
                        error("Audio output did not drain before timeout")
                    }
                    Thread.sleep(4L)
                }
                val playedFrames = track.playedFrames()
                if (running.get() && generation.get() == token &&
                    playedFrames >= expectedPlayedFrames && running.compareAndSet(true, false)
                ) {
                    runCatching {
                        onProgress(ArrangementTiming.framesToTicks(plan.totalFrames, plan.bpm, plan.ppq, sampleRate))
                    }
                    runCatching { onCompleted() }
                }
            }
        } catch (_: InterruptedException) {
            running.set(false)
            Thread.currentThread().interrupt()
        } catch (failure: Exception) {
            running.set(false)
            runCatching { onError(failure.message ?: "Arrangement audio output failed") }
        } finally {
            synchronized(this) {
                if (output === track) {
                    output = null
                    runCatching { track.close() }
                }
                worker = null
            }
        }
    }

    fun updateMix(project: StudioProject) {
        val trackValues = project.tracks.map { track ->
            TrackMix(
                volume = track.volume.coerceIn(0f, 1.5f),
                pan = track.pan.coerceIn(-1f, 1f),
                muted = track.muted,
                solo = track.solo,
                devices = track.devices
            )
        }
        liveMix = MixSnapshot(
            tracks = trackValues.mapIndexed { index, mix -> project.tracks[index].id to mix }.toMap(),
            soloActive = trackValues.any { it.solo && !it.muted }
        )
    }

    fun setMasterGain(gain: Float) {
        require(gain.isFinite()) { "Master gain must be finite" }
        liveMasterGain = gain.coerceIn(0f, 1f)
    }

    /**
     * Scrub an audio track while it is playing. The gesture changes the source read
     * position by a bounded millisecond offset; the realtime worker reads only atomics.
     */
    fun scratchTrack(trackId: String, deltaMilliseconds: Float) {
        if (closed.get() || !running.get() || trackId.isBlank() || !deltaMilliseconds.isFinite()) return
        val delta = deltaMilliseconds.toLong()
        if (delta == 0L) return
        val offset = scratchOffsetsMs.computeIfAbsent(trackId) { java.util.concurrent.atomic.AtomicLong(0L) }
        while (true) {
            val old = offset.get()
            val next = (old + delta).coerceIn(-MAX_SCRATCH_OFFSET_MS, MAX_SCRATCH_OFFSET_MS)
            if (offset.compareAndSet(old, next)) break
        }
    }

    fun stop() {
        generation.incrementAndGet()
        scratchOffsetsMs.clear()
        stopOutputOnly()
        trackMeterBits.keys.forEach { id -> publishTrackMeter(id, 0f, 0f, 0f, 0f) }
    }

    private fun stopOutputOnly() {
        running.set(false)
        val current = synchronized(this) { output }
        // Closing Oboe unblocks native queue operations before joining the producer.
        current?.let { track -> runCatching { track.stop() } }
        val thread = worker
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join(1_000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        synchronized(this) {
            if (output === current) {
                output = null
                current?.let { track ->
                    runCatching { track.close() }
                }
            }
            if (worker === thread && (thread == null || !thread.isAlive)) worker = null
            check(thread == null || thread === Thread.currentThread() || !thread.isAlive) {
                "Audio render thread did not stop; playback restart was blocked for safety"
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                stop()
            } finally {
                audioAssetCache.evictAll()
            }
        }
    }

    companion object {
        const val OUTPUT_SAMPLE_RATE = 44100
        const val MAX_SCRATCH_OFFSET_MS = 10_000L
        const val BLOCK_FRAMES = 256
        val DRUM_DURATION_FRAMES = (OUTPUT_SAMPLE_RATE * 0.20).toLong()
        const val MAX_SOURCE_SECONDS = 300
        const val MAX_ARRANGEMENT_BARS = 1800
        const val MAX_TOTAL_PCM_BYTES = 64L * 1024L * 1024L
        // 64 MiB accommodates one five-minute stereo PCM source (~50.5 MiB), avoiding
        // cache eviction and expensive source decoding every time a long clip is resized.
        const val MAX_ASSET_CACHE_KIB = 64 * 1024
    }
}
