package com.studiowave.app.audio

/**
 * Pure arrangement timing helpers. Kept separate from Android audio APIs so tempo and
 * tick calculations can be covered by fast local unit tests.
 */
internal object ArrangementTiming {
    const val DEFAULT_SAMPLE_RATE = 44100

    fun ticksToFrames(
        ticks: Long,
        bpm: Int,
        ppq: Int,
        sampleRate: Int = DEFAULT_SAMPLE_RATE
    ): Long {
        val safeTicks = ticks.coerceAtLeast(0L)
        val safeBpm = bpm.coerceIn(40, 240)
        val safePpq = ppq.coerceIn(24, 3840)
        val safeRate = sampleRate.coerceIn(8000, 192000)
        val frames = safeTicks.toDouble() * 60.0 * safeRate / (safeBpm.toDouble() * safePpq)
        return if (!frames.isFinite() || frames >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE
        else frames.toLong().coerceAtLeast(0L)
    }

    /** End tick of an arrangement whose visible musical extent is limited to [bars] bars. */
    fun maxArrangementTick(numerator: Int, denominator: Int, ppq: Int, bars: Int): Long {
        val safePpq = ppq.coerceIn(24, 3840)
        val safeNumerator = numerator.coerceIn(1, 32)
        val safeDenominator = denominator.coerceIn(1, 32)
        val safeBars = bars.coerceIn(1, 1800)
        val ticksPerBar = (safePpq.toLong() * 4L / safeDenominator).coerceAtLeast(1L) * safeNumerator
        return if (ticksPerBar > Long.MAX_VALUE / safeBars) Long.MAX_VALUE
        else ticksPerBar * safeBars
    }

    fun framesToTicks(
        frames: Long,
        bpm: Int,
        ppq: Int,
        sampleRate: Int = DEFAULT_SAMPLE_RATE
    ): Long {
        val safeFrames = frames.coerceAtLeast(0L)
        val safeBpm = bpm.coerceIn(40, 240)
        val safePpq = ppq.coerceIn(24, 3840)
        val safeRate = sampleRate.coerceIn(8000, 192000)
        val ticks = safeFrames.toDouble() * safeBpm.toDouble() * safePpq.toDouble() /
            (60.0 * safeRate.toDouble())
        return if (!ticks.isFinite() || ticks >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE
        else ticks.toLong().coerceAtLeast(0L)
    }

    fun trackIsAudible(trackMuted: Boolean, trackSolo: Boolean, anySoloActive: Boolean): Boolean =
        !trackMuted && (!anySoloActive || trackSolo)
}
