package com.studiowave.app

import com.studiowave.app.data.StudioProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrumPatternUiTest {
    @Test
    fun normalizationPreservesExistingPatternLengthBeyondOneBar() {
        val original = (0 until 64).map { it % 3 == 0 }
        val normalized = normalizeDrumPatternForEditor(original)
        assertEquals(64, normalized.size)
        assertEquals(original, normalized)
    }

    @Test
    fun emptyPatternReceivesSixteenStepsForEditing() {
        assertEquals(List(16) { false }, normalizeDrumPatternForEditor(emptyList()))
    }

    @Test
    fun oversizedPatternIsCappedAtProjectMaximum() {
        val oversized = List(StudioProject.MAX_DRUM_STEPS_PER_CLIP + 10) { true }
        val normalized = normalizeDrumPatternForEditor(oversized)
        assertEquals(StudioProject.MAX_DRUM_STEPS_PER_CLIP, normalized.size)
        assertTrue(normalized.all { it })
    }

    @Test
    fun stepEditClearAndAllPreservePatternLength() {
        val current = normalizeDrumPatternForEditor(List(64) { it % 2 == 0 })
        val toggled = current.toMutableList().also { it[31] = !it[31] }
        val cleared = List(toggled.size) { false }
        val filled = List(toggled.size) { true }
        assertEquals(64, toggled.size)
        assertEquals(64, cleared.size)
        assertEquals(64, filled.size)
        assertTrue(cleared.none { it })
        assertTrue(filled.all { it })
    }
}
