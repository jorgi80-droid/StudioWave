package com.studiowave.app.audio

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Lock-free producer path for post-master PCM and a worker-owned 1024-point FFT. offer() only
 * copies at most one output block into a preallocated slot; FFT/windows never run on audio render.
 */
internal class PcmSpectrumAnalyzer : AutoCloseable {
    companion object {
        const val FFT_SIZE = 1024
        const val BAND_COUNT = 64
        private const val BLOCK_FRAMES = 256
        private const val SLOT_COUNT = 8

        internal fun binForFrequency(frequencyHz: Double, sampleRate: Int, fftSize: Int = FFT_SIZE): Int {
            if (!frequencyHz.isFinite() || sampleRate <= 0 || fftSize < 2) return 0
            return (frequencyHz.coerceIn(0.0, sampleRate / 2.0) * fftSize / sampleRate)
                .roundToInt().coerceIn(0, fftSize / 2)
        }

        /** In-place radix-2 Cooley-Tukey FFT, exposed for deterministic mathematical tests. */
        internal fun fftInPlace(real: DoubleArray, imaginary: DoubleArray) {
            require(real.size == imaginary.size && real.size >= 2 && real.size.countOneBits() == 1) {
                "FFT input must be equal-length power-of-two arrays"
            }
            val n = real.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (i < j) {
                    val tr = real[i]; real[i] = real[j]; real[j] = tr
                    val ti = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = ti
                }
            }
            var length = 2
            while (length <= n) {
                val angle = -2.0 * Math.PI / length
                val stepReal = kotlin.math.cos(angle)
                val stepImaginary = kotlin.math.sin(angle)
                val half = length shr 1
                var base = 0
                while (base < n) {
                    var wr = 1.0
                    var wi = 0.0
                    for (offset in 0 until half) {
                        val even = base + offset
                        val odd = even + half
                        val tr = wr * real[odd] - wi * imaginary[odd]
                        val ti = wr * imaginary[odd] + wi * real[odd]
                        real[odd] = real[even] - tr
                        imaginary[odd] = imaginary[even] - ti
                        real[even] += tr
                        imaginary[even] += ti
                        val nextWr = wr * stepReal - wi * stepImaginary
                        wi = wr * stepImaginary + wi * stepReal
                        wr = nextWr
                    }
                    base += length
                }
                if (length == n) break
                length = length shl 1
            }
        }
    }

    private val closed = AtomicBoolean(false)
    private val writeCursor = AtomicLong(0L)
    private val readCursor = AtomicLong(0L)
    private val slots = Array(SLOT_COUNT) { FloatArray(BLOCK_FRAMES) }
    private val slotFrames = IntArray(SLOT_COUNT)
    @Volatile private var currentSampleRate = 44_100
    @Volatile private var published = FloatArray(BAND_COUNT)
    private val timeWindow = DoubleArray(FFT_SIZE)
    private val real = DoubleArray(FFT_SIZE)
    private val imaginary = DoubleArray(FFT_SIZE)
    private val hann = DoubleArray(FFT_SIZE) { index ->
        0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * index / (FFT_SIZE - 1))
    }
    private val worker = Thread({ runAnalyzer() }, "StudioWave-FFT").apply {
        isDaemon = true
        priority = Thread.NORM_PRIORITY - 1
        start()
    }

    /** The renderer calls this after master limiting and before PCM conversion. Never blocks. */
    fun offer(interleavedStereo: FloatArray, sampleCount: Int, sampleRate: Int) {
        if (closed.get() || sampleCount < 2 || sampleRate <= 0) return
        currentSampleRate = sampleRate.coerceIn(8_000, 192_000)
        val frames = (sampleCount / 2).coerceIn(0, BLOCK_FRAMES)
        if (frames == 0) return
        val write = writeCursor.get()
        if (write - readCursor.get() >= SLOT_COUNT) return
        val slot = (write % SLOT_COUNT).toInt()
        val target = slots[slot]
        for (frame in 0 until frames) {
            val left = interleavedStereo[frame * 2]
            val right = interleavedStereo[frame * 2 + 1]
            target[frame] = if (left.isFinite() && right.isFinite()) (left + right) * 0.5f else 0f
        }
        slotFrames[slot] = frames
        writeCursor.lazySet(write + 1L)
        LockSupport.unpark(worker)
    }

    fun snapshot(): FloatArray = published.copyOf()

    private fun runAnalyzer() {
        var collected = 0
        while (!closed.get()) {
            val read = readCursor.get()
            if (read >= writeCursor.get()) {
                LockSupport.parkNanos(3_000_000L)
                continue
            }
            val slot = (read % SLOT_COUNT).toInt()
            val count = slotFrames[slot].coerceIn(0, BLOCK_FRAMES)
            val data = slots[slot]
            for (i in 0 until count) {
                if (collected < FFT_SIZE) timeWindow[collected++] = data[i].toDouble()
            }
            readCursor.lazySet(read + 1L)
            if (collected >= FFT_SIZE) {
                for (i in 0 until FFT_SIZE) {
                    real[i] = timeWindow[i] * hann[i]
                    imaginary[i] = 0.0
                }
                fftInPlace(real, imaginary)
                publishSpectrum(currentSampleRate)
                collected = 0
            }
        }
    }

    private fun publishSpectrum(sampleRate: Int) {
        val bands = FloatArray(BAND_COUNT)
        val minHz = 20.0
        val maxHz = min(20_000.0, sampleRate / 2.0)
        val ratio = maxHz / minHz
        for (band in 0 until BAND_COUNT) {
            val lowHz = minHz * ratio.pow(band.toDouble() / BAND_COUNT)
            val highHz = minHz * ratio.pow((band + 1.0) / BAND_COUNT)
            val first = max(1, ceil(lowHz * FFT_SIZE / sampleRate).toInt())
            val last = min(FFT_SIZE / 2, max(first, floor(highHz * FFT_SIZE / sampleRate).toInt()))
            var magnitude = 0.0
            for (bin in first..last) {
                magnitude = max(magnitude, kotlin.math.sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin]))
            }
            // Hann window coherent gain is ~0.5; map -72..0 dBFS into a stable 0..1 display.
            val amplitude = (magnitude / (FFT_SIZE * 0.25)).coerceAtLeast(1e-8)
            val dbfs = 20.0 * log10(amplitude)
            bands[band] = ((dbfs + 72.0) / 72.0).coerceIn(0.0, 1.0).toFloat()
        }
        published = bands
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            LockSupport.unpark(worker)
            if (Thread.currentThread() !== worker) {
                try {
                    worker.join(250L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }
}
