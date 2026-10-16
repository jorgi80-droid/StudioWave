package com.studiowave.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * Magnitude response for the same eight peaking-biquad coefficient equations used by
 * TrackDeviceDsp. The graph uses logarithmic frequency coordinates (20 Hz to 20 kHz).
 */
internal object ParametricEqMath {
    fun frequencyNormForHz(frequencyHz: Double): Float {
        val safe = frequencyHz.takeIf { it.isFinite() }?.coerceIn(20.0, 20_000.0) ?: 20.0
        return (kotlin.math.ln(safe / 20.0) / kotlin.math.ln(1_000.0)).toFloat().coerceIn(0f, 1f)
    }

    fun frequencyHzForNorm(normalized: Float): Double =
        20.0 * 1_000.0.pow(normalized.takeIf { it.isFinite() }?.coerceIn(0f, 1f)?.toDouble() ?: 0.0)

    fun responseDbAt(
        frequencyHz: Double,
        sampleRate: Int,
        parameters: Map<String, Float>,
        enabled: Boolean = true
    ): Double {
        if (!enabled || !frequencyHz.isFinite() || sampleRate <= 0) return 0.0
        val sr = sampleRate.coerceIn(8_000, 192_000).toDouble()
        val f = frequencyHz.coerceIn(1.0, sr * 0.499)
        val w = 2.0 * PI * f / sr
        val c1 = cos(w)
        val s1 = sin(w)
        val c2 = cos(2.0 * w)
        val s2 = sin(2.0 * w)
        var totalDb = 0.0
        for (band in 1..8) {
            val gainDb = (parameters["band" + band] ?: 0f).takeIf { it.isFinite() }
                ?.coerceIn(-18f, 18f)?.toDouble() ?: 0.0
            if (kotlin.math.abs(gainDb) < 1e-6) continue
            val norm = (parameters["band" + band + "FreqNorm"] ?: floatArrayOf(
                0.159f, 0.259f, 0.366f, 0.466f, 0.566f, 0.681f, 0.799f, 0.900f
            )[band - 1]).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f
            val q = (parameters["band" + band + "Q"] ?: 1.1f).takeIf { it.isFinite() }
                ?.coerceIn(0.25f, 8f)?.toDouble() ?: 1.1
            val center = frequencyHzForNorm(norm).coerceIn(20.0, sr * 0.45)
            val centerW = 2.0 * PI * center / sr
            val alpha = sin(centerW) / (2.0 * q)
            val a = 10.0.pow(gainDb / 40.0)
            val a0 = 1.0 + alpha / a
            val b0 = (1.0 + alpha * a) / a0
            val b1 = (-2.0 * cos(centerW)) / a0
            val b2 = (1.0 - alpha * a) / a0
            val a1 = (-2.0 * cos(centerW)) / a0
            val a2 = (1.0 - alpha / a) / a0
            val nr = b0 + b1 * c1 + b2 * c2
            val ni = -b1 * s1 - b2 * s2
            val dr = 1.0 + a1 * c1 + a2 * c2
            val di = -a1 * s1 - a2 * s2
            val numerator = nr * nr + ni * ni
            val denominator = (dr * dr + di * di).coerceAtLeast(1e-24)
            totalDb += 10.0 * log10((numerator / denominator).coerceAtLeast(1e-24))
        }
        return totalDb.coerceIn(-144.0, 144.0)
    }
}
