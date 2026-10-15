package com.studiowave.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioWaveformRangeTest {
    @Test
    fun sourceTicksMapToActualSourceTimeUsingProjectTempoAndPpq() {
        val range = audioWaveformClipRange(
            sourceOffsetTicks = 1920L,
            clipLengthTicks = 3840L,
            bpm = 120,
            ppq = 960,
            sourceDurationSeconds = 10.0,
            peakCount = 240
        )
        requireNotNull(range)
        assertEquals(24, range.firstPeakIndex)
        assertEquals(72, range.endPeakIndexExclusive)
        assertEquals(1.0, range.sourceOffsetSeconds, 1e-9)
        assertEquals(2.0, range.clipDurationSeconds, 1e-9)
    }

    @Test
    fun clipReachingSourceEndUsesAvailablePeaksWithoutStretchingToFullClipWidth() {
        val range = audioWaveformClipRange(
            sourceOffsetTicks = 17280L,
            clipLengthTicks = 3840L,
            bpm = 120,
            ppq = 960,
            sourceDurationSeconds = 10.0,
            peakCount = 200
        )
        requireNotNull(range)
        assertEquals(180, range.firstPeakIndex)
        assertEquals(200, range.endPeakIndexExclusive)
        assertEquals(2.0, range.clipDurationSeconds, 1e-9)
    }

    @Test
    fun offsetBeyondRealSourceDurationDoesNotInventWaveformSamples() {
        assertNull(
            audioWaveformClipRange(
                sourceOffsetTicks = 24000L,
                clipLengthTicks = 3840L,
                bpm = 120,
                ppq = 960,
                sourceDurationSeconds = 10.0,
                peakCount = 240
            )
        )
    }

    @Test
    fun invalidDurationOrPeakCountIsRejected() {
        assertNull(audioWaveformClipRange(0, 3840, 120, 960, 0.0, 240))
        assertNull(audioWaveformClipRange(0, 3840, 120, 960, 10.0, 0))
    }
}
