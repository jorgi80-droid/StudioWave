package com.studiowave.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.ParcelFileDescriptor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * Lightweight sample instrument voice layer. SoundPool decodes WAV/MP3 on Android's
 * media stack; pitch, loop, polyphonic voice IDs and ADSR gain ramps are controlled here.
 * This is a single-zone sampler; it does not claim to parse Image-Line .dwp/.instr patches.
 */
class DirectWaveSampler(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_POLYPHONY)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build())
        .build()
    private val voices = ConcurrentHashMap<Int, Voice>()
    @Volatile private var sampleId: Int = 0
    @Volatile private var loaded: Boolean = false
    @Volatile var pitchSemitones: Int = 0
        set(value) { field = value.coerceIn(-12, 12) }
    @Volatile var attackMs: Int = 5
        set(value) { field = value.coerceIn(0, 5000) }
    @Volatile var decayMs: Int = 120
        set(value) { field = value.coerceIn(0, 5000) }
    @Volatile var sustain: Float = 0.8f
        set(value) { field = value.coerceIn(0f, 1f) }
    @Volatile var releaseMs: Int = 180
        set(value) { field = value.coerceIn(0, 5000) }
    @Volatile var looping: Boolean = false
    @Volatile var outputGain: Float = 1f
        set(value) { field = value.coerceIn(0f, 1f) }
    @Volatile var fineTuneCents: Int = 0
        set(value) { field = value.coerceIn(-100, 100) }

    private data class Voice(val streamId: Int, val note: Int, val velocity: Int, val generation: Long)
    private val generations = ConcurrentHashMap<Int, Long>()

    fun load(uri: Uri, onLoaded: (Boolean) -> Unit = {}) {
        loaded = false
        val id = if (uri.scheme == "file") {
            runCatching { pool.load(uri.path ?: return onLoaded(false), 1) }.getOrDefault(0)
        } else {
            val descriptor = runCatching { appContext.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
            if (descriptor == null) 0 else runCatching {
                descriptor.use { pool.load(it.fileDescriptor, 0L, it.statSize.coerceAtLeast(0L), 1) }
            }.getOrDefault(0)
        }
        if (id == 0) { onLoaded(false); return }
        sampleId = id
        pool.setOnLoadCompleteListener { _, loadedId, status ->
            if (loadedId == id) {
                loaded = status == 0
                onLoaded(loaded)
            }
        }
    }

    fun hasSample(): Boolean = loaded

    fun noteOn(note: Int, velocity: Int = 100) {
        if (!loaded) return
        val midi = note.coerceIn(0, 127)
        noteOff(midi)
        val generation = (generations[midi] ?: 0L) + 1L
        generations[midi] = generation
        val rate = 2.0.pow(((midi - 60 + pitchSemitones).toDouble() + fineTuneCents / 100.0) / 12.0)
            .toFloat().coerceIn(0.5f, 2f)
        val gain = (velocity.coerceIn(1, 127) / 127f) * outputGain
        val stream = pool.play(sampleId, 0f, 0f, 1, if (looping) -1 else 0, rate)
        if (stream == 0) return
        val voice = Voice(stream, midi, velocity.coerceIn(1, 127), generation)
        voices[midi] = voice
        val attack = attackMs
        val decay = decayMs
        if (attack <= 0) {
            pool.setVolume(stream, gain, gain)
            if (decay > 0) {
                handler.postDelayed({
                    if (voices[midi] == voice) pool.setVolume(stream, gain * sustain, gain * sustain)
                }, decay.toLong())
            }
        } else {
            val steps = (attack / RAMP_INTERVAL_MS).coerceIn(1, 64)
            for (step in 1..steps) {
                handler.postDelayed({
                    if (voices[midi] == voice) {
                        val envelope = step.toFloat() / steps
                        pool.setVolume(stream, gain * envelope, gain * envelope)
                        if (step == steps && decay > 0) {
                            handler.postDelayed({
                                if (voices[midi] == voice) pool.setVolume(stream, gain * sustain, gain * sustain)
                            }, decay.toLong())
                        }
                    }
                }, (attack.toLong() * step / steps))
            }
        }
    }

    fun noteOff(note: Int) {
        val midi = note.coerceIn(0, 127)
        val voice = voices.remove(midi) ?: return
        val release = releaseMs
        if (release <= 0) {
            pool.stop(voice.streamId)
            return
        }
        val steps = (release / RAMP_INTERVAL_MS).coerceIn(1, 64)
        for (step in 1..steps) {
            handler.postDelayed({
                val level = 1f - step.toFloat() / steps
                pool.setVolume(voice.streamId, level, level)
                if (step == steps) pool.stop(voice.streamId)
            }, release.toLong() * step / steps)
        }
    }

    fun allNotesOff() {
        voices.keys.toList().forEach(::noteOff)
    }

    override fun close() {
        allNotesOff()
        pool.release()
        loaded = false
    }

    companion object {
        const val MAX_POLYPHONY = 16
        private const val RAMP_INTERVAL_MS = 16
    }
}
