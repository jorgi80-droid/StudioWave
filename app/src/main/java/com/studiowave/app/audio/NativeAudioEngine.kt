package com.studiowave.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean

class NativeAudioEngine : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile private var track: AudioTrack? = null

    companion object {
        init { System.loadLibrary("studiowave_audio") }
    }

    private external fun nativeConfigure(sampleRate: Int)
    private external fun nativeStart()
    private external fun nativeStop()
    private external fun nativeSetTone(frequency: Float, gain: Float, waveform: Int)
    private external fun nativeRender(target: ShortArray, frames: Int)

    @Synchronized
    fun start(sampleRate: Int = 44100) {
        check(!closed.get()) { "Audio engine is closed" }
        require(sampleRate in 8000..192000) { "Unsupported sample rate: $sampleRate" }
        if (running.get()) return
        stop()
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBytes > 0) { "AudioTrack buffer query failed: $minBytes" }
        val output = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBytes, 4096))
            .build()
        if (output.state != AudioTrack.STATE_INITIALIZED) {
            output.release()
            error("AudioTrack initialization failed")
        }

        try {
            nativeConfigure(sampleRate)
            track = output
            running.set(true)
            nativeStart()
            output.play()
            worker = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val pcm = ShortArray(512)
                try {
                    while (running.get()) {
                        nativeRender(pcm, 256)
                        var offset = 0
                        while (offset < pcm.size && running.get()) {
                            val written = output.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
                            if (written < 0) { running.set(false); break }
                            if (written == 0) Thread.yield() else offset += written
                        }
                    }
                } catch (_: IllegalStateException) {
                    running.set(false)
                } finally {
                    running.set(false)
                }
            }, "StudioWave-AudioTrack").also { it.start() }
        } catch (failure: Throwable) {
            running.set(false)
            nativeStop()
            track = null
            runCatching { output.release() }
            throw failure
        }
    }

    fun setTone(frequencyHz: Float, gain: Float = 0.08f, waveform: Int = 0) {
        check(!closed.get()) { "Audio engine is closed" }
        require(frequencyHz.isFinite() && gain.isFinite()) { "Tone parameters must be finite" }
        nativeSetTone(frequencyHz, gain, waveform.coerceIn(0, 3))
    }

    @Synchronized
    fun stop() {
        running.set(false)
        nativeStop()
        val t = worker
        if (t != null && t !== Thread.currentThread()) {
            try { t.join(750) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        worker = null
        track?.let {
            try { it.pause(); it.flush() } catch (_: IllegalStateException) { }
            it.release()
        }
        track = null
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) stop()
    }
}