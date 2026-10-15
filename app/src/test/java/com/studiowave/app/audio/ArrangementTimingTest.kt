package com.studiowave.app

import com.studiowave.app.audio.ArrangementTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementTimingTest {
    @Test fun oneQuarterNoteAt120BpmIsHalfSecond() {
        assertEquals(22_050L, ArrangementTiming.ticksToFrames(960L, 120, 960))
    }

    @Test fun fourFourBarAt120BpmIsTwoSeconds() {
        assertEquals(88_200L, ArrangementTiming.ticksToFrames(3_840L, 120, 960))
    }

    @Test fun fasterTempoProducesFewerFrames() {
        val slow = ArrangementTiming.ticksToFrames(3_840L, 90, 960)
        val fast = ArrangementTiming.ticksToFrames(3_840L, 180, 960)
        assertEquals(slow / 2, fast)
    }

    @Test fun negativeTicksAndOutOfRangeTempoAreHandledSafely() {
        assertEquals(0L, ArrangementTiming.ticksToFrames(-100L, 120, 960))
        assertEquals(11_025L, ArrangementTiming.ticksToFrames(960L, 240, 960))
        assertTrue(ArrangementTiming.ticksToFrames(Long.MAX_VALUE, 120, 960) > 0L)
    }

    @Test fun playbackFramesConvertBackToMusicalTicks() {
        assertEquals(0L, ArrangementTiming.framesToTicks(-1L, 120, 960))
        assertEquals(1_920L, ArrangementTiming.framesToTicks(44_100L, 120, 960))
        assertEquals(3_840L, ArrangementTiming.framesToTicks(88_200L, 120, 960))
        val sixQuarterFrames = ArrangementTiming.ticksToFrames(5_760L, 96, 960)
        assertEquals(5_760L, ArrangementTiming.framesToTicks(sixQuarterFrames, 96, 960))
        assertTrue(ArrangementTiming.framesToTicks(Long.MAX_VALUE, 40, 3840) > 0L)
    }

    @Test fun tickAdditionSaturatesInsteadOfOverflowing() {
        assertEquals(0L, ArrangementTiming.addTicksSaturated(-10L, -2L))
        assertEquals(15L, ArrangementTiming.addTicksSaturated(10L, 5L))
        assertEquals(Long.MAX_VALUE, ArrangementTiming.addTicksSaturated(Long.MAX_VALUE - 2L, 10L))
        assertEquals(Long.MAX_VALUE, ArrangementTiming.addTicksSaturated(Long.MAX_VALUE, 1L))
    }

    @Test fun soloAndMuteRulesAreDeterministic() {
        assertTrue(ArrangementTiming.trackIsAudible(false, false, false))
        assertFalse(ArrangementTiming.trackIsAudible(true, true, false))
        assertTrue(ArrangementTiming.trackIsAudible(false, true, true))
        assertFalse(ArrangementTiming.trackIsAudible(false, false, true))
    }

    @Test fun absoluteSourceMicrosecondsDoNotDependOnTempo() {
        val halfSecondAtLowTempo = ArrangementTiming.ticksToMicros(960L, 120, 960)
        val sameSourceTimeAtHighTempo = ArrangementTiming.ticksToMicros(1920L, 240, 960)
        assertEquals(500_000L, halfSecondAtLowTempo)
        assertEquals(halfSecondAtLowTempo, sameSourceTimeAtHighTempo)
        assertEquals(22_050L, ArrangementTiming.microsToFrames(500_000L, 44_100))
        assertEquals(500_000L, ArrangementTiming.ticksToMicros(
            ArrangementTiming.microsToTicks(500_000L, 120, 960), 120, 960
        ))
    }

    @Test fun absoluteSourceConversionsClampInvalidAndOverflowInputs() {
        assertEquals(0L, ArrangementTiming.microsToFrames(-1L, 44_100))
        assertEquals(0L, ArrangementTiming.ticksToMicros(-1L, 120, 960))
        assertEquals(Long.MAX_VALUE, ArrangementTiming.microsToFrames(Long.MAX_VALUE, 192_000))
        assertEquals(Long.MAX_VALUE, ArrangementTiming.ticksToMicros(Long.MAX_VALUE, 40, 24))
    }

    @Test fun sourceFrameWrappingStaysInsideTrimmedWindowForMalformedAndOverflowInputs() {
        assertEquals(10L, ArrangementTiming.sourceFrameWithinTrim(10L, 10L, 4L, 100L))
        assertEquals(12L, ArrangementTiming.sourceFrameWithinTrim(-1L, 10L, 4L, 100L))
        assertEquals(13L, ArrangementTiming.sourceFrameWithinTrim(17L, 10L, 4L, 100L))
        assertEquals(99L, ArrangementTiming.sourceFrameWithinTrim(Long.MAX_VALUE, 98L, 8L, 100L))
        assertEquals(null, ArrangementTiming.sourceFrameWithinTrim(0L, 0L, 8L, 0L))
        assertEquals(99L, ArrangementTiming.sourceFrameWithinTrim(Long.MIN_VALUE, Long.MAX_VALUE, 8L, 100L))
    }

}
