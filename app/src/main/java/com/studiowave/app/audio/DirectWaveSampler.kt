package com.studiowave.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * Polyphonic single-zone WAV sampler for the StudioWave DirectWave-style device surface.
 * This uses Android SoundPool; it is not a reader for proprietary .dwp/.instr patch formats.
 */
internal class DirectWaveSampler(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_POLYPHONY)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        ).build()

    private data class Voice(val streamId: Int, val generation: Long)
    private val voices = ConcurrentHashMap<Int, Voice>()
    private var generation = 0L
    @Volatile private var sampleId = 0
    @Volatile private var loaded = false

    @Volatile var pitchSemitones = 0
        set(value) { field = value.coerceIn(-48, 48) }
    @Volatile var fineTuneCents = 0
        set(value) { field = value.coerceIn(-100, 100) }
    @Volatile var outputGain = 0.8f
        set(value) { field = value.coerceIn(0f, 1f) }
    @Volatile var attackMs = 5
        set(value) { field = value.coerceIn(0, 5000) }
    @Volatile var decayMs = 120
        set(value) { field = value.coerceIn(0, 5000) }
    @Volatile var sustain = 0.8f
        set(value) { field = value.coerceIn(0f, 1f) }
    @Volatile var releaseMs = 180
        set(value) { field = value.coerceIn(0, 5000) }
    @Volatile var looping = false

    fun load(uri: Uri, onLoaded: (Boolean) -> Unit = {}) {
        loaded = false
        val id = try {
            if (uri.scheme == "android.resource" || uri.scheme == "content") {
                val fd = appContext.contentResolver.openAssetFileDescriptor(uri, "r")
                if (fd == null) 0 else fd.use { pool.load(it.fileDescriptor, 0L, it.length, 1) }
            } else if (uri.scheme == "file") pool.load(uri.path ?: return onLoaded(false), 1) else 0
        } catch (_: Exception) { 0 }
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
        generation += 1
        val token = generation
        val rate = 2.0.pow((midi - 60 + pitchSemitones + fineTuneCents / 100.0) / 12.0)
            .toFloat().coerceIn(0.25f, 4f)
        val stream = pool.play(sampleId, 0f, 0f, 1, if (looping) -1 else 0, rate)
        if (stream == 0) return
        val voice = Voice(stream, token)
        voices[midi] = voice
        val peak = (velocity.coerceIn(1, 127) / 127f) * outputGain
        if (attackMs <= 0) pool.setVolume(stream, peak, peak) else {
            pool.setVolume(stream, 0f, 0f)
            val steps = (attackMs / 16).coerceIn(1, 48)
            repeat(steps) { i ->
                mainHandler.postDelayed({
                    if (voices[midi] == voice) {
                        val env = peak * (i + 1).toFloat() / steps
                        pool.setVolume(stream, env, env)
                        if (i == steps - 1) {
                            val sustainLevel = peak * sustain
                            mainHandler.postDelayed({
                                if (voices[midi] == voice) pool.setVolume(stream, sustainLevel, sustainLevel)
                            }, decayMs.toLong())
                        }
                    }
                }, attackMs.toLong() * (i + 1) / steps)
            }
        }
    }

    fun noteOff(note: Int) {
        val voice = voices.remove(note.coerceIn(0, 127)) ?: return
        val steps = (releaseMs / 16).coerceIn(1, 48)
        repeat(steps) { i ->
            mainHandler.postDelayed({
                val level = 1f - (i + 1).toFloat() / steps
                pool.setVolume(voice.streamId, level.coerceIn(0f, 1f), level.coerceIn(0f, 1f))
                if (i == steps - 1) pool.stop(voice.streamId)
            }, releaseMs.toLong() * (i + 1) / steps)
        }
    }

    fun allNotesOff() = voices.keys.toList().forEach(::noteOff)

    override fun close() {
        allNotesOff()
        loaded = false
        pool.setOnLoadCompleteListener(null)
        pool.release()
    }

    companion object {
        private const val MAX_POLYPHONY = 24
    }
}
