package com.studiowave.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PianoRollPitchRangeTest {
    @Test
    fun topPitchMapsToCorrectScrollIndexForFullMidiRange() {
        assertEquals(0, pianoRollPitchScrollIndex(127))
        assertEquals(19, pianoRollPitchScrollIndex(108))
        assertEquals(127, pianoRollPitchScrollIndex(0))
        assertEquals(0, pianoRollPitchScrollIndex(500))
        assertEquals(127, pianoRollPitchScrollIndex(-10))
    }

    @Test
    fun lowerVisiblePitchDependsOnViewportRowsNotAnInvertedUpperBound() {
        assertEquals(11, clampPianoRollTopPitch(-100, 12))
        assertEquals(108, clampPianoRollTopPitch(108, 12))
        assertEquals(127, clampPianoRollTopPitch(500, 12))
        assertEquals(127, clampPianoRollTopPitch(0, 128))
        assertEquals(0, clampPianoRollTopPitch(-100, 1))
    }

    @Test
    fun everyMidiPitchCanReachTheVisibleRangeAtDifferentViewportHeights() {
        for (visibleRows in listOf(12, 24, 48, 96, 128)) {
            val minTopPitch = visibleRows - 1
            assertEquals(visibleRows - 1, clampPianoRollTopPitch(-1, visibleRows))
            assertEquals(127, clampPianoRollTopPitch(127, visibleRows))
            assertEquals(127 - minTopPitch, pianoRollPitchScrollIndex(minTopPitch))
        }
    }
}
