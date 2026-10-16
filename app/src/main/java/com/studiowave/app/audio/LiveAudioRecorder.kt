package com.studiowave.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/** Native microphone capture, written off the UI thread and finalized as mono PCM16 RIFF/WAV. */
internal class LiveAudioRecorder(
    private val outputDirectory: File,
    private val sampleRate: Int = 44_100
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var rawFile: File? = null
    @Volatile private var recordingFailure: Throwable? = null

    data class Result(val file: File, val durationMillis: Long)

    fun start() {
        check(running.compareAndSet(false, true)) { "Microphone recording is already active" }
        try {
            require(sampleRate in 8_000..96_000) { "Unsupported microphone sample rate" }
            check(outputDirectory.exists() || outputDirectory.mkdirs()) { "Cannot create recording directory" }
            val raw = File(outputDirectory, "take-${System.nanoTime()}.pcm")
            rawFile = raw
            recordingFailure = null
            val minimum = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            check(minimum > 0) { "This device does not support the selected microphone format" }
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, sampleRate / 5 * 2)
            )
            audioRecord = recorder
            check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                "Android could not initialize microphone capture"
            }
            recorder.startRecording()
            worker = Thread({
                try {
                    FileOutputStream(raw).buffered().use { output ->
                        val samples = ShortArray(4096)
                        while (running.get()) {
                            val count = recorder.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                            if (count > 0) {
                                val bytes = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
                                for (index in 0 until count) bytes.putShort(samples[index])
                                output.write(bytes.array())
                            } else if (count < 0 && running.get()) {
                                error("Microphone read failed: $count")
                            }
                        }
                    }
                } catch (failure: Throwable) {
                    recordingFailure = failure
                    running.set(false)
                }
            }, "StudioWave-MicRecorder").apply { isDaemon = true; start() }
        } catch (failure: Throwable) {
            running.set(false)
            audioRecord?.let { runCatching { it.release() } }
            audioRecord = null
            rawFile?.let { runCatching { it.delete() } }
            rawFile = null
            throw failure
        }
    }

    fun stop(): Result {
        running.set(false)
        val recorder = audioRecord
        if (recorder != null) runCatching { recorder.stop() }
        val thread = worker
        if (thread != null && thread !== Thread.currentThread()) {
            thread.join(5_000L)
            check(!thread.isAlive) { "Microphone capture did not stop cleanly" }
        }
        audioRecord = null
        runCatching { recorder?.release() }
        worker = null
        recordingFailure?.let { failure ->
            rawFile?.let { runCatching { it.delete() } }
            rawFile = null
            throw IllegalStateException("Microphone capture failed", failure)
        }
        val raw = rawFile ?: error("No active microphone take")
        val dataBytes = raw.length()
        check(dataBytes >= 2L) { "Recording was empty; check microphone permission and input level" }
        val durationMs = dataBytes * 1000L / (sampleRate.toLong() * 2L)
        val output = File(outputDirectory, "StudioWave-Take-${System.nanoTime()}.wav")
        FileOutputStream(output).buffered().use { destination ->
            destination.write(wavHeader(dataBytes, sampleRate))
            FileInputStream(raw).use { input -> input.copyTo(destination, 64 * 1024) }
            destination.flush()
        }
        raw.delete()
        rawFile = null
        recordingFailure = null
        return Result(output, durationMs)
    }

    override fun close() {
        if (running.get() || worker != null) runCatching { stop() }
        audioRecord = null
        worker = null
    }

    companion object {
        internal fun wavHeader(dataBytes: Long, sampleRate: Int): ByteArray {
            require(dataBytes in 0..(0xFFFFFFFFL - 36L))
            require(sampleRate in 8_000..96_000)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt((36L + dataBytes).toInt())
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)
            header.putShort(1)
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2)
            header.putShort(2)
            header.putShort(16)
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataBytes.toInt())
            return header.array()
        }
    }
}
