package com.studiowave.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Original allocation-free StudioWave voice DSP. MIDI scheduling creates one VoiceState per
 * note off the render thread; the render path only updates primitive state and sample values.
 */
internal object StudioWaveVoiceDsp {
    internal data class Parameters(
        val oscillatorA: Int = 0,
        val oscillatorB: Int = 1,
        val mix: Float = 0.28f,
        val gain: Float = 0.8f,
        val detuneCents: Float = 7f,
        val cutoffHz: Float = 18_000f,
        val resonance: Float = 0.12f,
        val attackMs: Int = 8,
        val decayMs: Int = 120,
        val sustain: Float = 0.75f,
        val releaseMs: Int = 140,
        /** 0 = 12-TET, 1 = just intonation, 2 = Pythagorean. */
        val tuning: Int = 0,
        val rootMidi: Int = 60,
        val modulationRateHz: Float = 0.35f,
        val modulationDepth: Float = 0f,
        /** 0 = filter cutoff, 1 = pitch. */
        val modulationTarget: Int = 0
    ) {
        companion object {
            val DEFAULT = Parameters()

            fun fromMap(values: Map<String, Float>): Parameters {
                fun f(key: String, fallback: Float, min: Float, max: Float): Float {
                    val value = values[key]?.takeIf { it.isFinite() } ?: fallback
                    return value.coerceIn(min, max)
                }
                fun i(key: String, fallback: Int, min: Int, max: Int): Int =
                    (values[key]?.takeIf { it.isFinite() }?.roundToInt() ?: fallback).coerceIn(min, max)
                return Parameters(
                    oscillatorA = i("oscillatorA", 0, 0, 4),
                    oscillatorB = i("oscillatorB", 1, 0, 4),
                    mix = f("mix", 0.28f, 0f, 1f),
                    gain = f("gain", f("level", 0.8f, 0f, 1.5f), 0f, 1.5f),
                    detuneCents = f("detuneCents", 7f, -50f, 50f),
                    cutoffHz = f("cutoffHz", 18_000f, 20f, 20_000f),
                    resonance = f("resonance", 0.12f, 0f, 1f),
                    attackMs = i("attackMs", 8, 0, 5_000),
                    decayMs = i("decayMs", 120, 0, 5_000),
                    sustain = f("sustain", 0.75f, 0f, 1f),
                    releaseMs = i("releaseMs", 140, 0, 5_000),
                    tuning = i("tuning", 0, 0, 2),
                    rootMidi = i("rootMidi", 60, 0, 127),
                    modulationRateHz = f("modRateHz", 0.35f, 0.01f, 20f),
                    modulationDepth = f("modDepth", 0f, 0f, 1f),
                    modulationTarget = i("modTarget", 0, 0, 1)
                )
            }

            fun forDevice(type: String, values: Map<String, Float>): Parameters {
                val shape = when (type) {
                    "SYNTH_SAW" -> 1
                    "SYNTH_SQUARE" -> 2
                    "SYNTH_TRIANGLE" -> 3
                    "FM_SYNTH" -> 4
                    else -> 0
                }
                val defaults = mapOf(
                    "oscillatorA" to shape.toFloat(),
                    "oscillatorB" to 1f,
                    "mix" to 0.28f
                )
                return fromMap(defaults + values)
            }
        }
    }

    /** One mutable state object per scheduled note; never constructed inside the sample loop. */
    internal class VoiceState {
        var lastCoefficientFrame: Long = Long.MIN_VALUE
        var b0 = 1.0
        var b1 = 0.0
        var b2 = 0.0
        var a1 = 0.0
        var a2 = 0.0
        var x1L = 0.0
        var x2L = 0.0
        var y1L = 0.0
        var y2L = 0.0
        var x1R = 0.0
        var x2R = 0.0
        var y1R = 0.0
        var y2R = 0.0
    }

    private val justRatios = doubleArrayOf(
        1.0, 16.0 / 15.0, 9.0 / 8.0, 6.0 / 5.0, 5.0 / 4.0, 4.0 / 3.0,
        45.0 / 32.0, 3.0 / 2.0, 8.0 / 5.0, 5.0 / 3.0, 9.0 / 5.0, 15.0 / 8.0
    )
    private val pythagoreanRatios = doubleArrayOf(
        1.0, 256.0 / 243.0, 9.0 / 8.0, 32.0 / 27.0, 81.0 / 64.0, 4.0 / 3.0,
        729.0 / 512.0, 3.0 / 2.0, 128.0 / 81.0, 27.0 / 16.0, 16.0 / 9.0, 243.0 / 128.0
    )

    fun midiFrequency(midiPitch: Int, referenceHz: Double = 440.0): Double {
        val pitch = midiPitch.coerceIn(0, 127)
        val reference = referenceHz.takeIf { it.isFinite() && it in 400.0..480.0 } ?: 440.0
        return reference * 2.0.pow((pitch - 69) / 12.0)
    }

    /** Root note keeps its equal-tempered reference frequency; intervals use the chosen ratios. */
    fun tunedFrequency(midiPitch: Int, tuning: Int, rootMidi: Int = 60): Double {
        val pitch = midiPitch.coerceIn(0, 127)
        val root = rootMidi.coerceIn(0, 127)
        val offset = pitch - root
        val octave = Math.floorDiv(offset, 12)
        val step = Math.floorMod(offset, 12)
        val ratio = when (tuning.coerceIn(0, 2)) {
            1 -> justRatios[step]
            2 -> pythagoreanRatios[step]
            else -> 2.0.pow(step / 12.0)
        }
        return midiFrequency(root) * 2.0.pow(octave.toDouble()) * ratio
    }

    /** Same five oscillator shapes are used by the renderer and the device's waveform preview. */
    fun oscillatorWaveform(shape: Int, phase: Double, fmAmount: Float = 0.3f): Float {
        if (!phase.isFinite()) return 0f
        var cycles = phase / (2.0 * PI)
        cycles -= floor(cycles)
        return when (shape.coerceIn(0, 4)) {
            0 -> sin(2.0 * PI * cycles).toFloat()
            1 -> (2.0 * cycles - 1.0).toFloat()
            2 -> if (cycles < 0.5) 1f else -1f
            3 -> (1.0 - 4.0 * abs(cycles - 0.5)).toFloat()
            else -> sin(2.0 * PI * cycles + sin(4.0 * PI * cycles) * fmAmount.coerceIn(0f, 1f)).toFloat()
        }
    }

    /**
     * Render one configured mono synth voice. noteLengthFrames includes the release tail;
     * heldFrames is the scheduled MIDI note-off position. All voice/filter state is preallocated.
     */
    fun sampleConfigured(
        midiPitch: Int,
        elapsedFrames: Long,
        noteLengthFrames: Long,
        sampleRate: Int,
        amplitude: Float,
        parameters: Parameters = Parameters.DEFAULT,
        state: VoiceState,
        heldFrames: Long = noteLengthFrames
    ): Float {
        if (elapsedFrames < 0L || noteLengthFrames <= 0L || elapsedFrames >= noteLengthFrames ||
            sampleRate <= 0 || !amplitude.isFinite() || amplitude <= 0f) return 0f
        val safeRate = sampleRate.coerceIn(8_000, 192_000)
        val safeHeld = heldFrames.coerceIn(1L, noteLengthFrames)
        val modPhase = 2.0 * PI * parameters.modulationRateHz * elapsedFrames.toDouble() / safeRate
        val lfo = if (parameters.modulationDepth <= 0f) 0.0 else sin(modPhase) * parameters.modulationDepth
        var frequency = tunedFrequency(midiPitch, parameters.tuning, parameters.rootMidi)
        if (parameters.modulationTarget == 1) {
            frequency *= 2.0.pow(lfo * 3.0 / 12.0)
        }
        frequency = frequency.coerceIn(1.0, safeRate * 0.45)
        val frequencyB = (frequency * 2.0.pow(parameters.detuneCents / 1200.0)).coerceAtMost(safeRate * 0.45)
        val phaseA = 2.0 * PI * frequency * elapsedFrames.toDouble() / safeRate
        val phaseB = 2.0 * PI * frequencyB * elapsedFrames.toDouble() / safeRate
        val blend = parameters.mix.coerceIn(0f, 1f)
        val a = oscillatorWaveform(parameters.oscillatorA, phaseA)
        val b = oscillatorWaveform(parameters.oscillatorB, phaseB)
        val raw = a * (1f - blend) + b * blend
        val cutoff = if (parameters.modulationTarget == 0) {
            (parameters.cutoffHz * 2.0.pow(lfo * 2.0)).toFloat()
        } else parameters.cutoffHz
        val filtered = processFilter(raw, elapsedFrames, safeRate, parameters, cutoff, state, 0)
        val attackFrames = parameters.attackMs.toLong() * safeRate / 1_000L
        val decayFrames = parameters.decayMs.toLong() * safeRate / 1_000L
        val releaseFrames = parameters.releaseMs.toLong() * safeRate / 1_000L
        val sustain = parameters.sustain.coerceIn(0f, 1f)
        val envelope = when {
            attackFrames > 0L && elapsedFrames < attackFrames ->
                elapsedFrames.toFloat() / attackFrames.toFloat()
            decayFrames > 0L && elapsedFrames < attackFrames + decayFrames -> {
                val progress = (elapsedFrames - attackFrames).toFloat() / decayFrames.toFloat()
                1f - (1f - sustain) * progress.coerceIn(0f, 1f)
            }
            else -> sustain
        }.coerceIn(0f, 1f)
        val release = when {
            elapsedFrames < safeHeld -> 1f
            releaseFrames <= 0L -> if (elapsedFrames < noteLengthFrames) 0f else 0f
            else -> ((noteLengthFrames - elapsedFrames).toDouble() / releaseFrames.toDouble()).toFloat().coerceIn(0f, 1f)
        }
        val result = filtered * envelope * release * amplitude * parameters.gain.coerceIn(0f, 1.5f)
        return if (result.isFinite()) result else 0f
    }

    /** Filter a source PCM sample using the same actual per-voice filter as the synth. */
    fun processSample(
        input: Float,
        elapsedFrames: Long,
        sampleRate: Int,
        parameters: Parameters,
        state: VoiceState,
        channel: Int
    ): Float {
        if (!input.isFinite() || elapsedFrames < 0L || sampleRate <= 0) return 0f
        val safeRate = sampleRate.coerceIn(8_000, 192_000)
        val modPhase = 2.0 * PI * parameters.modulationRateHz * elapsedFrames.toDouble() / safeRate
        val lfo = if (parameters.modulationDepth <= 0f) 0.0 else sin(modPhase) * parameters.modulationDepth
        val cutoff = if (parameters.modulationTarget == 0) {
            (parameters.cutoffHz * 2.0.pow(lfo * 2.0)).toFloat()
        } else parameters.cutoffHz
        return processFilter(input, elapsedFrames, safeRate, parameters, cutoff, state, channel.coerceIn(0, 1))
    }

    /** Frequency response of the exact low-pass biquad coefficients used by each voice. */
    fun filterResponseDb(frequencyHz: Double, sampleRate: Int, parameters: Parameters): Double {
        val sr = sampleRate.coerceIn(8_000, 192_000).toDouble()
        val cutoff = parameters.cutoffHz.toDouble().coerceIn(20.0, sr * 0.45)
        val q = 0.5 + parameters.resonance.coerceIn(0f, 1f) * 7.5
        val w0 = 2.0 * PI * cutoff / sr
        val c = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        val b0 = ((1.0 - c) / 2.0) / a0
        val b1 = (1.0 - c) / a0
        val b2 = b0
        val a1 = (-2.0 * c) / a0
        val a2 = (1.0 - alpha) / a0
        val w = 2.0 * PI * frequencyHz.coerceIn(1.0, sr * 0.499) / sr
        val nr = b0 + b1 * cos(w) + b2 * cos(2.0 * w)
        val ni = -b1 * sin(w) - b2 * sin(2.0 * w)
        val dr = 1.0 + a1 * cos(w) + a2 * cos(2.0 * w)
        val di = -a1 * sin(w) - a2 * sin(2.0 * w)
        val denominator = dr * dr + di * di
        val magnitudeSquared = (nr * nr + ni * ni) / denominator.coerceAtLeast(1e-24)
        return 10.0 * kotlin.math.log10(magnitudeSquared.coerceAtLeast(1e-12))
    }

    /** Backward-compatible legacy entry point, retaining a short note-edge fade. */
    fun sample(
        midiPitch: Int,
        elapsedFrames: Long,
        noteLengthFrames: Long,
        sampleRate: Int,
        amplitude: Float,
        detuneCents: Float = 7f,
        blend: Float = 0.22f
    ): Float {
        val safeRate = sampleRate.coerceAtLeast(8_000)
        val release = (safeRate * 0.035).toLong().coerceAtLeast(1L)
        return sampleConfigured(
            midiPitch, elapsedFrames, noteLengthFrames, safeRate, amplitude,
            Parameters.DEFAULT.copy(detuneCents = detuneCents, mix = blend),
            VoiceState(),
            (noteLengthFrames - release).coerceAtLeast(1L)
        )
    }

    fun peak(samples: FloatArray, length: Int = samples.size): Float {
        var result = 0f
        for (i in 0 until length.coerceIn(0, samples.size)) {
            val value = samples[i]
            if (value.isFinite()) result = maxOf(result, abs(value))
        }
        return result
    }

    private fun processFilter(
        input: Float,
        elapsedFrames: Long,
        sampleRate: Int,
        parameters: Parameters,
        cutoffHz: Float,
        state: VoiceState,
        channel: Int
    ): Float {
        if (elapsedFrames and 7L == 0L && state.lastCoefficientFrame != elapsedFrames) {
            val cutoff = cutoffHz.toDouble().coerceIn(20.0, sampleRate * 0.45)
            val q = 0.5 + parameters.resonance.coerceIn(0f, 1f) * 7.5
            val w = 2.0 * PI * cutoff / sampleRate
            val c = cos(w)
            val alpha = sin(w) / (2.0 * q)
            val a0 = 1.0 + alpha
            state.b0 = ((1.0 - c) / 2.0) / a0
            state.b1 = (1.0 - c) / a0
            state.b2 = state.b0
            state.a1 = (-2.0 * c) / a0
            state.a2 = (1.0 - alpha) / a0
            state.lastCoefficientFrame = elapsedFrames
        }
        val x = input.toDouble()
        val result: Double
        if (channel == 0) {
            result = state.b0 * x + state.b1 * state.x1L + state.b2 * state.x2L -
                state.a1 * state.y1L - state.a2 * state.y2L
            state.x2L = state.x1L
            state.x1L = x
            state.y2L = state.y1L
            state.y1L = result
        } else {
            result = state.b0 * x + state.b1 * state.x1R + state.b2 * state.x2R -
                state.a1 * state.y1R - state.a2 * state.y2R
            state.x2R = state.x1R
            state.x1R = x
            state.y2R = state.y1R
            state.y1R = result
        }
        return if (result.isFinite()) result.toFloat() else 0f
    }
}
