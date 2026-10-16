package com.studiowave.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * Allocation-free, deterministic voice renderer for MIDI notes without a loaded sample zone.
 * The two continuously phase-related oscillator layers are intentionally simple native DSP;
 * this is not a claim of compatibility with commercial synthesizers or wavetable patches.
 */
internal object StudioWaveVoiceDsp {
    /** Equal-tempered MIDI pitch mapping with A4=440 Hz. */
    fun midiFrequency(midiPitch: Int, referenceHz: Double = 440.0): Double {
        val pitch = midiPitch.coerceIn(0, 127)
        val reference = referenceHz.takeIf { it.isFinite() && it in 400.0..480.0 } ?: 440.0
        return reference * 2.0.pow((pitch - 69) / 12.0)
    }

    /**
     * Render one sample. Oscillator B is a softly detuned octave blend, adding body without
     * allocation or mutable global voice state. Envelope ramps prevent hard note-edge clicks.
     */
    fun sample(
        midiPitch: Int,
        elapsedFrames: Long,
        noteLengthFrames: Long,
        sampleRate: Int,
        amplitude: Float,
        detuneCents: Float = 7f,
        blend: Float = 0.22f
    ): Float {
        if (elapsedFrames < 0L || noteLengthFrames <= 0L || sampleRate <= 0 ||
            !amplitude.isFinite() || amplitude <= 0f) return 0f
        if (elapsedFrames >= noteLengthFrames) return 0f
        val safeRate = sampleRate.coerceIn(8_000, 192_000)
        val frequency = midiFrequency(midiPitch).coerceAtMost(safeRate * 0.45)
        val detune = detuneCents.takeIf { it.isFinite() }?.coerceIn(-30f, 30f) ?: 0f
        val secondaryFrequency = (frequency * 2.0.pow(detune / 1200.0)).coerceAtMost(safeRate * 0.45)
        val phaseA = 2.0 * PI * frequency * elapsedFrames.toDouble() / safeRate
        val phaseB = 2.0 * PI * secondaryFrequency * elapsedFrames.toDouble() / safeRate
        val mix = blend.takeIf { it.isFinite() }?.coerceIn(0f, 0.5f) ?: 0.22f
        val oscillator = sin(phaseA) * (1.0 - mix) + sin(phaseB) * mix
        val attackFrames = (safeRate * 0.008).toLong().coerceAtLeast(1L)
        val releaseFrames = (safeRate * 0.035).toLong().coerceAtLeast(1L)
        val attack = (elapsedFrames.toDouble() / attackFrames).coerceIn(0.0, 1.0)
        val remaining = noteLengthFrames - elapsedFrames
        val release = (remaining.toDouble() / releaseFrames).coerceIn(0.0, 1.0)
        val envelope = minOf(attack, release)
        return (oscillator * amplitude.coerceIn(0f, 1f) * envelope).toFloat()
    }

    /** Lightweight sample-domain peak helper shared by tests and analyzer snapshots. */
    fun peak(samples: FloatArray, length: Int = samples.size): Float {
        var result = 0f
        for (i in 0 until length.coerceIn(0, samples.size)) {
            val value = samples[i]
            if (value.isFinite()) result = maxOf(result, abs(value))
        }
        return result
    }
}
