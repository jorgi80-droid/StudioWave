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

    @Test fun soloAndMuteRulesAreDeterministic() {
        assertTrue(ArrangementTiming.trackIsAudible(false, false, false))
        assertFalse(ArrangementTiming.trackIsAudible(true, true, false))
        assertTrue(ArrangementTiming.trackIsAudible(false, true, true))
        assertFalse(ArrangementTiming.trackIsAudible(false, false, true))
    }
}
