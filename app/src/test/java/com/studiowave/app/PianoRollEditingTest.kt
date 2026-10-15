package com.studiowave.app

import com.studiowave.app.data.MidiNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PianoRollEditingTest {
    private val maxTick = 1800L * 3840L

    @Test fun visiblePitchWindowMapsAllRowsWithoutClipping() {
        assertEquals(71, pianoPitchForRow(48, 0))
        assertEquals(48, pianoPitchForRow(48, 23))
        assertEquals(127, pianoPitchForRow(104, 0))
        assertEquals(0, pianoPitchForRow(0, 23))
        assertEquals(60, pianoPitchForRow(48, 11))
    }

    @Test fun movingNoteSnapsTimeClampsPitchAndPreservesDuration() {
        val source = MidiNote(id = "n", pitch = 60, startTick = 960, durationTicks = 480, velocity = 84)
        val moved = updateMidiNoteFromDrag(source, MidiNoteDragMode.MOVE, 730, 80, 240, maxTick)
        assertEquals(1680L, moved.startTick)
        assertEquals(480L, moved.durationTicks)
        assertEquals(127, moved.pitch)
        assertEquals(84, moved.velocity)
    }

    @Test fun leadingEdgeResizeKeepsEndAnchored() {
        val source = MidiNote(id = "n", pitch = 64, startTick = 960, durationTicks = 960, velocity = 90)
        val resized = updateMidiNoteFromDrag(source, MidiNoteDragMode.RESIZE_LEFT, 240, 0, 240, maxTick)
        assertEquals(1920L, midiNoteEndTick(resized))
        assertEquals(1200L, resized.startTick)
        assertEquals(720L, resized.durationTicks)
    }

    @Test fun trailingEdgeResizeKeepsStartAnchored() {
        val source = MidiNote(id = "n", pitch = 64, startTick = 960, durationTicks = 480, velocity = 90)
        val resized = updateMidiNoteFromDrag(source, MidiNoteDragMode.RESIZE_RIGHT, 720, 0, 240, maxTick)
        assertEquals(960L, resized.startTick)
        assertEquals(1200L, resized.durationTicks)
    }

    @Test fun resizeAndMoveCannotCrossArrangementEnd() {
        val source = MidiNote(id = "n", pitch = 60, startTick = maxTick - 480L, durationTicks = 480L)
        val moved = updateMidiNoteFromDrag(source, MidiNoteDragMode.MOVE, 999999L, 0, 240, maxTick)
        assertEquals(maxTick - 480L, moved.startTick)
        assertTrue(midiNoteEndTick(moved) <= maxTick)
        val resized = updateMidiNoteFromDrag(source, MidiNoteDragMode.RESIZE_RIGHT, 999999L, 0, 240, maxTick)
        assertTrue(midiNoteEndTick(resized) <= maxTick)
    }

    @Test fun noteCreationCannotExtendPastArrangementEnd() {
        assertEquals(maxTick - 240L, snapMidiStartTick(maxTick, 240L, maxTick))
        assertEquals(0L, snapMidiStartTick(-99L, 240L, maxTick))
    }

    @Test fun normalizationClampsInvalidNoteDataAndBoundsCount() {
        val result = normalizeMidiNotes(
            listOf(
                MidiNote(id = "bad", pitch = 140, startTick = maxTick, durationTicks = 1000, velocity = 200),
                MidiNote(id = "bad", pitch = -2, startTick = -30, durationTicks = 0, velocity = 0)
            ),
            maxTick,
            8
        )
        assertEquals(1, result.size)
        assertEquals(127, result.single().pitch)
        assertEquals(maxTick - 1000L, result.single().startTick)
        assertEquals(1000L, result.single().durationTicks)
        assertEquals(127, result.single().velocity)
    }

    @Test fun growingPatternRoundsToBarsAndNeverShrinks() {
        val note = MidiNote(id = "n", pitch = 64, startTick = 3900, durationTicks = 480)
        assertEquals(7680L, clipLengthForNotes(3840L, listOf(note), 3840L, maxTick))
        assertEquals(7680L, clipLengthForNotes(7680L, emptyList(), 3840L, maxTick))
    }
}
