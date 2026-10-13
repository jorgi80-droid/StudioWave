package com.studiowave.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * EBU R128 / ITU-R BS.1770 style momentary loudness meter.
 *
 * Applies K-weighting to the stereo master and reports a 400 ms momentary
 * loudness value in LUFS. This is deliberately a momentary meter, not integrated
 * loudness for a completed export; integrated loudness also needs programme-level
 * gating and a defined measurement interval.
 *
 * All buffers and filter state are allocated up front. processBlock() is intended
 * for the existing audio render worker and performs no per-block allocation.
 */
internal class MasterLoudnessMeter(
    private val sampleRate: Int,
    windowMilliseconds: Int = 400
) {
    private val windowFrames = (sampleRate * windowMilliseconds / 1000).coerceAtLeast(1)
    private val energyWindow = DoubleArray(windowFrames)
    private var writeIndex = 0
    private var filledFrames = 0
    private var energySum = 0.0
    private val leftShelf = Biquad.highShelf(sampleRate, 1681.974450955533, 3.999843853973347, 0.7071752369554196)
    private val rightShelf = Biquad.highShelf(sampleRate, 1681.974450955533, 3.999843853973347, 0.7071752369554196)
    private val leftHighPass = Biquad.highPass(sampleRate, 38.13547087602444, 0.5003270373238773)
    private val rightHighPass = Biquad.highPass(sampleRate, 38.13547087602444, 0.5003270373238773)

    fun reset() {
        energyWindow.fill(0.0)
        writeIndex = 0
        filledFrames = 0
        energySum = 0.0
        leftShelf.reset()
        rightShelf.reset()
        leftHighPass.reset()
        rightHighPass.reset()
    }

    /** Process interleaved stereo samples and return the current 400 ms momentary LUFS. */
    fun processBlock(interleavedStereo: FloatArray, sampleCount: Int): Float {
        val safeCount = sampleCount.coerceIn(0, interleavedStereo.size - interleavedStereo.size % 2)
        var i = 0
        while (i < safeCount) {
            val left = leftHighPass.process(leftShelf.process(interleavedStereo[i].toDouble()))
            val right = rightHighPass.process(rightShelf.process(interleavedStereo[i + 1].toDouble()))
            val energy = left * left + right * right
            if (filledFrames >= windowFrames) energySum -= energyWindow[writeIndex]
            else filledFrames++
            energyWindow[writeIndex] = energy
            energySum += energy
            writeIndex++
            if (writeIndex == windowFrames) writeIndex = 0
            i += 2
        }
        val meanEnergy = energySum / filledFrames.coerceAtLeast(1)
        if (!meanEnergy.isFinite() || meanEnergy <= ABSOLUTE_GATE_POWER) return SILENCE_LUFS
        return (-0.691 + 10.0 * log10(meanEnergy)).toFloat().coerceIn(SILENCE_LUFS, 6f)
    }

    private class Biquad(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double
    ) {
        private var z1 = 0.0
        private var z2 = 0.0

        fun process(input: Double): Double {
            val output = b0 * input + z1
            z1 = b1 * input - a1 * output + z2
            z2 = b2 * input - a2 * output
            return if (output.isFinite()) output else 0.0
        }

        fun reset() {
            z1 = 0.0
            z2 = 0.0
        }

        companion object {
            fun highShelf(sampleRate: Int, frequency: Double, gainDb: Double, q: Double): Biquad {
                val a = 10.0.pow(gainDb / 40.0)
                val w = 2.0 * PI * frequency / sampleRate.coerceAtLeast(1)
                val c = cos(w)
                val s = sin(w)
                val alpha = s / 2.0 * sqrt((a + 1.0 / a) * (1.0 / q - 1.0) + 2.0)
                val rootA = sqrt(a)
                val b0 = a * ((a + 1.0) + (a - 1.0) * c + 2.0 * rootA * alpha)
                val b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * c)
                val b2 = a * ((a + 1.0) + (a - 1.0) * c - 2.0 * rootA * alpha)
                val a0 = (a + 1.0) - (a - 1.0) * c + 2.0 * rootA * alpha
                val a1 = 2.0 * ((a - 1.0) - (a + 1.0) * c)
                val a2 = (a + 1.0) - (a - 1.0) * c - 2.0 * rootA * alpha
                return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }

            fun highPass(sampleRate: Int, frequency: Double, q: Double): Biquad {
                val w = 2.0 * PI * frequency / sampleRate.coerceAtLeast(1)
                val c = cos(w)
                val alpha = sin(w) / (2.0 * q)
                val b0 = (1.0 + c) / 2.0
                val b1 = -(1.0 + c)
                val b2 = (1.0 + c) / 2.0
                val a0 = 1.0 + alpha
                val a1 = -2.0 * c
                val a2 = 1.0 - alpha
                return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }
        }
    }

    companion object {
        private const val ABSOLUTE_GATE_POWER = 1.0e-7
        const val SILENCE_LUFS = -120f
    }
}
