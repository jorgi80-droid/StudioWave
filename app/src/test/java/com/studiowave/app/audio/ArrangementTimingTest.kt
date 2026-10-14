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

    @Test fun 1800BarsAt120BpmFourFourFitExactlyOneHour() {
        val endTick = ArrangementTiming.maxArrangementTick(4, 4, 960, 1_800)
        val frames = ArrangementTiming.ticksToFrames(endTick, 120, 960)
        assertEquals(44_100L * 60L * 60L, frames)
    }

    @Test fun 1800BarPlaybackLimitFollowsTempoAndTimeSignature() {
        val slowFourFourTicks = ArrangementTiming.maxArrangementTick(4, 4, 960, 1_800)
        val slowFourFourFrames = ArrangementTiming.ticksToFrames(slowFourFourTicks, 40, 960)
        assertEquals(44_100L * 60L * 60L * 3L, slowFourFourFrames)

        val sevenEightTicks = ArrangementTiming.maxArrangementTick(7, 8, 960, 1_800)
        assertEquals(6_048_000L, sevenEightTicks)
        assertEquals(44_100L * 60L * 60L * 1.3125, ArrangementTiming.ticksToFrames(sevenEightTicks, 80, 960).toDouble(), 1.0)
    }

    @Test fun arrangementEndTickSanitizesInvalidTimebaseAndBarCount() {
        assertEquals(
            ArrangementTiming.maxArrangementTick(32, 1, 3_840, 1_800),
            ArrangementTiming.maxArrangementTick(40, 0, 8_000, 2_000)
        )
    }

    @Test fun soloAndMuteRulesAreDeterministic() {
        assertTrue(ArrangementTiming.trackIsAudible(false, false, false))
        assertFalse(ArrangementTiming.trackIsAudible(true, true, false))
        assertTrue(ArrangementTiming.trackIsAudible(false, true, true))
        assertFalse(ArrangementTiming.trackIsAudible(false, false, true))
    }
}
