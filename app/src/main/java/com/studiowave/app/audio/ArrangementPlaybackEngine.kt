package com.studiowave.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Process
import com.studiowave.app.data.MidiNote
import com.studiowave.app.data.StudioProject
import com.studiowave.app.data.StudioTrack
import com.studiowave.app.data.WaveClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
class ArrangementPlaybackEngine(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile private var output: AudioTrack? = null

    private data class AudioAsset(val sampleRate: Int, val pcmStereo: ShortArray) {
        val frames: Int get() = pcmStereo.size / 2
    }

    private data class AudioEvent(
        val startFrame: Long,
        val endFrame: Long,
        val track: StudioTrack,
        val clip: WaveClip,
        val asset: AudioAsset
    )

    private data class NoteEvent(
        val startFrame: Long,
        val endFrame: Long,
        val pitch: Int,
        val velocity: Int,
        val track: StudioTrack
    )

    private data class DrumEvent(
        val startFrame: Long,
        val track: StudioTrack
    )

    private data class Plan(
        val sampleRate: Int,
        val totalFrames: Long,
        val masterGain: Float,
        val audio: List<AudioEvent>,
        val notes: List<NoteEvent>,
        val drums: List<DrumEvent>
    )

    suspend fun start(
        project: StudioProject,
        masterGain: Float = 0.72f,
        onCompleted: () -> Unit = {}
    ) {
        check(!closed.get()) { "Playback engine is closed" }
        stop()
        val plan = withContext(Dispatchers.IO) {
            buildPlan(project, masterGain.coerceIn(0f, 1f))
        }
        check(!closed.get()) { "Playback engine is closed" }
        val minBuffer = AudioTrack.getMinBufferSize(
            plan.sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBuffer > 0) { "Audio output buffer query failed: $minBuffer" }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(plan.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, BLOCK_FRAMES * 4 * 4))
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            error("AudioTrack initialization failed")
        }

        try {
            output = track
            running.set(true)
            track.play()
            worker = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                renderPlan(track, plan, onCompleted)
            }, "StudioWave-Arrangement").also { it.start() }
        } catch (failure: Throwable) {
            running.set(false)
            output = null
            runCatching { track.release() }
            throw failure
        }
    }

    private fun buildPlan(project: StudioProject, masterGain: Float): Plan {
        val bpm = project.bpm.coerceIn(40, 240)
        val ppq = project.ppq.coerceIn(24, 3840)
        val sampleRate = OUTPUT_SAMPLE_RATE
        fun ticksToFrames(ticks: Long): Long =
            (ticks.coerceAtLeast(0L).toDouble() * 60.0 * sampleRate / (bpm.toDouble() * ppq))
                .let { if (it.isFinite()) it.toLong().coerceAtLeast(0L) else 0L }

        val tracks = project.tracks
        val soloActive = tracks.any { it.solo && !it.muted }
        fun audible(track: StudioTrack) = !track.muted && (!soloActive || track.solo)
        val maxTick = tracks.flatMap { it.clips }.maxOfOrNull { it.startTick.coerceAtLeast(0L) + it.lengthTicks.coerceAtLeast(1L) }
            ?: (ppq.toLong() * project.numerator.coerceAtLeast(1) * 4L / project.denominator.coerceAtLeast(1))
        val totalFrames = ticksToFrames(maxTick).coerceIn(1L, sampleRate.toLong() * MAX_ARRANGEMENT_SECONDS)

        val requestedTicksByUri = linkedMapOf<String, Long>()
        tracks.filter(::audible).filter { it.kind == "audio" }.forEach { track ->
            track.clips.forEach clipLoop@{ clip ->
                val uri = clip.sourceUri?.takeIf { it.isNotBlank() } ?: return@clipLoop
                requestedTicksByUri[uri] = max(requestedTicksByUri[uri] ?: 0L, clip.lengthTicks.coerceAtLeast(1L))
            }
        }
        val assets = HashMap<String, AudioAsset>()
        var estimatedBytes = 0L
        requestedTicksByUri.forEach { (uriString, ticks) ->
            val seconds = (ticks.toDouble() * 60.0 / (bpm.toDouble() * ppq))
                .coerceIn(0.05, MAX_SOURCE_SECONDS.toDouble())
            val estimated = ceil(seconds * sampleRate).toLong() * 4L
            estimatedBytes += estimated
            require(estimatedBytes <= MAX_TOTAL_PCM_BYTES) {
                "Arrangement audio cache exceeds the safe memory budget; shorten clips or reduce the number of source files"
            }
            assets[uriString] = decodeAudioAsset(Uri.parse(uriString), seconds)
        }

        val audioEvents = ArrayList<AudioEvent>()
        val notes = ArrayList<NoteEvent>()
        val drums = ArrayList<DrumEvent>()
        tracks.filter(::audible).forEach { track ->
            track.clips.forEach { clip ->
                val clipStart = ticksToFrames(clip.startTick)
                val clipEnd = min(totalFrames, clipStart + ticksToFrames(clip.lengthTicks))
                if (clipEnd <= clipStart) return@forEach
                when (track.kind) {
                    "audio" -> {
                        val source = clip.sourceUri ?: return@clipLoop
                        val asset = assets[source] ?: return@clipLoop
                        audioEvents.add(AudioEvent(clipStart, clipEnd, track, clip, asset))
                    }
                    "midi" -> clip.midiNotes.forEach noteLoop@{ note ->
                        val noteStart = clipStart + ticksToFrames(note.startTick)
                        val noteEnd = min(clipEnd, noteStart + ticksToFrames(note.durationTicks.coerceAtLeast(1L)))
                        if (noteEnd > noteStart) {
                            notes.add(NoteEvent(noteStart, noteEnd, note.pitch.coerceIn(0, 127), note.velocity.coerceIn(1, 127), track))
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
        return Plan(sampleRate, totalFrames, masterGain, audioEvents, notes, drums)
    }

    private fun decodeAudioAsset(uri: Uri, durationSeconds: Double): AudioAsset {
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
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceIn(8000, 192000)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
            val maxFrames = ceil(durationSeconds * sampleRate).toInt().coerceIn(1, sampleRate * MAX_SOURCE_SECONDS)
            val pcmOut = ShortArray(maxFrames * 2)
            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio MIME"))
            decoder.configure(format, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var outputChannels = channels
            var outputRate = sampleRate
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var framesWritten = 0
            var stagnant = 0

            while (!outputDone && framesWritten < maxFrames) {
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
                                val baseFrame = (info.presentationTimeUs * outputRate / 1_000_000L).coerceAtLeast(0L)
                                for (frame in 0 until frames) {
                                    val targetFrame = baseFrame + frame.toLong() * sampleRate / outputRate
                                    if (targetFrame < 0 || targetFrame >= maxFrames) continue
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
            return AudioAsset(sampleRate, pcmOut.copyOf(framesWritten * 2))
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

    private fun renderPlan(track: AudioTrack, plan: Plan, onCompleted: () -> Unit) {
        val mix = FloatArray(BLOCK_FRAMES * 2)
        val pcm = ShortArray(BLOCK_FRAMES * 2)
        val sampleRate = plan.sampleRate
        val activeAudio = plan.audio
        val activeNotes = plan.notes
        val activeDrums = plan.drums
        var position = 0L
        try {
            while (running.get() && position < plan.totalFrames) {
                val frames = min(BLOCK_FRAMES.toLong(), plan.totalFrames - position).toInt()
                val sampleCount = frames * 2
                java.util.Arrays.fill(mix, 0, sampleCount, 0f)
                val blockEnd = position + frames

                activeAudio.forEach { event ->
                    if (event.endFrame <= position || event.startFrame >= blockEnd) return@forEach
                    val from = max(position, event.startFrame)
                    val to = min(blockEnd, event.endFrame)
                    val trackGain = event.track.volume.coerceIn(0f, 1.5f) * plan.masterGain
                    val pan = event.track.pan.coerceIn(-1f, 1f)
                    val leftPan = if (pan > 0f) 1f - pan else 1f
                    val rightPan = if (pan < 0f) 1f + pan else 1f
                    for (absolute in from until to) {
                        val relative = absolute - event.startFrame
                        val sourceFrame = (relative.toDouble() * event.asset.sampleRate / sampleRate).toInt()
                        if (sourceFrame >= event.asset.frames) continue
                        val outIndex = ((absolute - position) * 2).toInt()
                        val sourceIndex = sourceFrame * 2
                        mix[outIndex] += event.asset.pcmStereo[sourceIndex] / 32768f * trackGain * leftPan
                        mix[outIndex + 1] += event.asset.pcmStereo[sourceIndex + 1] / 32768f * trackGain * rightPan
                    }
                }

                activeNotes.forEach { note ->
                    if (note.endFrame <= position || note.startFrame >= blockEnd) return@forEach
                    val from = max(position, note.startFrame)
                    val to = min(blockEnd, note.endFrame)
                    val frequency = 440.0 * 2.0.pow((note.pitch - 69) / 12.0)
                    val gain = note.track.volume.coerceIn(0f, 1.5f) * plan.masterGain * (note.velocity / 127f) * 0.19f
                    val pan = note.track.pan.coerceIn(-1f, 1f)
                    val leftPan = if (pan > 0f) 1f - pan else 1f
                    val rightPan = if (pan < 0f) 1f + pan else 1f
                    for (absolute in from until to) {
                        val elapsed = absolute - note.startFrame
                        val noteLength = (note.endFrame - note.startFrame).coerceAtLeast(1L)
                        val attack = min(1f, elapsed.toFloat() / (sampleRate * 0.008f))
                        val release = min(1f, (noteLength - elapsed).toFloat() / (sampleRate * 0.035f))
                        val envelope = min(attack, release)
                        val wave = sin(2.0 * PI * frequency * elapsed / sampleRate).toFloat() * gain * envelope
                        val outIndex = ((absolute - position) * 2).toInt()
                        mix[outIndex] += wave * leftPan
                        mix[outIndex + 1] += wave * rightPan
                    }
                }

                activeDrums.forEach drumLoop@{ drum ->
                    val hitEnd = drum.startFrame + (sampleRate * 0.20).toLong()
                    if (drum.startFrame >= blockEnd || hitEnd <= position) return@drumLoop
                    val from = max(position, drum.startFrame)
                    val to = min(blockEnd, hitEnd)
                    val gain = drum.track.volume.coerceIn(0f, 1.5f) * plan.masterGain * 0.38f
                    val pan = drum.track.pan.coerceIn(-1f, 1f)
                    val leftPan = if (pan > 0f) 1f - pan else 1f
                    val rightPan = if (pan < 0f) 1f + pan else 1f
                    for (absolute in from until to) {
                        val t = (absolute - drum.startFrame).toDouble() / sampleRate
                        val frequency = 48.0 + 110.0 * exp(-t * 22.0)
                        val wave = sin(2.0 * PI * frequency * t).toFloat() * exp(-t * 20.0).toFloat() * gain
                        val outIndex = ((absolute - position) * 2).toInt()
                        mix[outIndex] += wave * leftPan
                        mix[outIndex + 1] += wave * rightPan
                    }
                }

                for (i in 0 until sampleCount) {
                    pcm[i] = (mix[i].coerceIn(-0.98f, 0.98f) * 32767f).toInt().toShort()
                }
                var offset = 0
                while (offset < sampleCount && running.get()) {
                    val written = track.write(pcm, offset, sampleCount - offset, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) error("Audio output write failed: $written")
                    if (written == 0) Thread.yield() else offset += written
                }
                position = blockEnd
            }
            if (running.compareAndSet(true, false)) onCompleted()
        } catch (_: InterruptedException) {
            running.set(false)
        } catch (_: IllegalStateException) {
            running.set(false)
        } catch (_: Exception) {
            running.set(false)
        } finally {
            synchronized(this) {
                if (output === track) {
                    output = null
                    runCatching { track.pause(); track.flush() }
                    runCatching { track.release() }
                }
                worker = null
            }
        }
    }

    fun stop() {
        running.set(false)
        val thread = worker
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join(1000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        synchronized(this) {
            worker = null
            output?.let { current ->
                runCatching { current.pause(); current.flush() }
                runCatching { current.release() }
            }
            output = null
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) stop()
    }

    companion object {
        const val OUTPUT_SAMPLE_RATE = 44100
        const val BLOCK_FRAMES = 256
        const val MAX_SOURCE_SECONDS = 60
        const val MAX_ARRANGEMENT_SECONDS = 60 * 15
        const val MAX_TOTAL_PCM_BYTES = 64L * 1024L * 1024L
    }
}
