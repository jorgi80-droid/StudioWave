package com.studiowave.app.audio

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bounded, non-blocking PCM capture for a live playback bounce.
 * Audio-thread calls only copy into a bounded queue; file and document I/O run on a writer thread.
 * The final RIFF header is written after capture completes so the exported WAV has correct lengths.
 */
internal class PlaybackWavRecorder(
    private val tempFile: File,
    private val destination: OutputStream,
    private val sampleRate: Int = 44_100,
    queueBlocks: Int = 24,
    private val onOverflow: () -> Unit = {}
) : AutoCloseable {
    private val queue = ArrayBlockingQueue<ShortArray>(queueBlocks.coerceIn(2, 128))
    private val accepting = AtomicBoolean(true)
    private val closed = AtomicBoolean(false)
    private val writer = Thread(::writeCapture, "StudioWave-WAV-writer").apply { isDaemon = true; start() }

    init {
        require(sampleRate in 8_000..192_000)
    }

    /** Called from the playback producer. Never waits for storage or a slow document provider. */
    fun offer(samples: ShortArray, sampleCount: Int) {
        if (!accepting.get() || sampleCount <= 0) return
        val count = sampleCount.coerceAtMost(samples.size).coerceAtMost(262_144)
        if (count == 0) return
        val copy = samples.copyOf(count)
        if (!queue.offer(copy)) onOverflow()
    }

    private fun writeCapture() {
        var rawBytes = 0L
        try {
            tempFile.parentFile?.mkdirs()
            tempFile.outputStream().buffered().use { raw ->
                while (accepting.get() || queue.isNotEmpty()) {
                    val block = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    val bytes = ByteBuffer.allocate(block.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    block.forEach(bytes::putShort)
                    raw.write(bytes.array())
                    rawBytes += bytes.capacity()
                }
            }
            destination.use { output ->
                output.write(wavHeader(rawBytes, sampleRate))
                FileInputStream(tempFile).use { input -> input.copyTo(output, 64 * 1024) }
                output.flush()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            runCatching { destination.close() }
        } catch (_: Exception) {
            runCatching { destination.close() }
        } finally {
            runCatching { tempFile.delete() }
            closed.set(true)
        }
    }

    override fun close() {
        if (accepting.compareAndSet(true, false)) {
            // The worker drains queued blocks before writing the finalized WAV header.
            writer.join(15_000L)
            if (writer.isAlive) {
                writer.interrupt()
                writer.join(1_000L)
            }
        }
    }

    fun isClosed(): Boolean = closed.get()

    companion object {
        internal fun wavHeader(dataBytes: Long, sampleRate: Int): ByteArray {
            require(dataBytes in 0..(0xFFFFFFFFL - 36L))
            require(sampleRate in 8_000..192_000)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
            header.putInt((36L + dataBytes).toInt())
            header.put(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))
            header.put(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))
            header.putInt(16)
            header.putShort(1)
            header.putShort(2)
            header.putInt(sampleRate)
            header.putInt(sampleRate * 4)
            header.putShort(4)
            header.putShort(16)
            header.put(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
            header.putInt(dataBytes.toInt())
            return header.array()
        }
    }
}
