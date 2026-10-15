package com.studiowave.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MetronomeClickTest {
    @Test fun clickIsAlignedToBeatAndBarBoundaries() {
        val beat = 11_025L
        val downbeat = metronomeClickSample(1L, beat, 4, 44_100, 1f)
        val nextBeat = metronomeClickSample(beat + 1L, beat, 4, 44_100, 1f)
        val nextBar = metronomeClickSample(beat * 4L + 1L, beat, 4, 44_100, 1f)

        assertTrue(abs(downbeat) > 0f)
        assertTrue(abs(nextBeat) > 0f)
        assertTrue(abs(downbeat) > abs(nextBeat))
        assertEquals(downbeat, nextBar, 0.000001f)
    }

    @Test fun clickSilencesOutsideItsShortWindowAndWhenLevelIsZero() {
        assertEquals(0f, metronomeClickSample(900L, 11_025L, 4, 44_100, 1f), 0f)
        assertEquals(0f, metronomeClickSample(1L, 11_025L, 4, 44_100, 0f), 0f)
        assertEquals(0f, metronomeClickSample(-1L, 11_025L, 4, 44_100, 1f), 0f)
    }

    @Test fun unusuallyShortBeatKeepsClickWithinHalfBeat() {
        val beat = 100L
        assertTrue(abs(metronomeClickSample(1L, beat, 4, 44_100, 1f)) > 0f)
        assertEquals(0f, metronomeClickSample(60L, beat, 4, 44_100, 1f), 0f)
    }
}
