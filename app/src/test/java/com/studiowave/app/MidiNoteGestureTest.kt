package com.studiowave.app

import com.studiowave.app.data.MidiNote
import org.junit.Assert.assertEquals
import org.junit.Test

class MidiNoteGestureTest {
    @Test
    fun movingNoteSnapsToGridAndChangesPitch() {
        val original = MidiNote(pitch = 60, startTick = 960L, durationTicks = 480L)
        val moved = transformMidiNoteFromGesture(
            note = original,
            clipLengthTicks = 7680L,
            stepTicks = 240L,
            deltaSteps = 2,
            deltaPitch = 3,
            resize = false
        )
        assertEquals(1440L, moved.startTick)
        assertEquals(480L, moved.durationTicks)
        assertEquals(63, moved.pitch)
        assertEquals(original.id, moved.id)
    }

    @Test
    fun movingNoteCannotLeaveClipOrPianoPitchRange() {
        val original = MidiNote(pitch = 119, startTick = 0L, durationTicks = 960L)
        val movedLeft = transformMidiNoteFromGesture(original, 3840L, 240L, -100, 12, false)
        assertEquals(0L, movedLeft.startTick)
        assertEquals(119, movedLeft.pitch)

        val movedRight = transformMidiNoteFromGesture(original.copy(pitch = 0), 3840L, 240L, 100, -12, false)
        assertEquals(2880L, movedRight.startTick)
        assertEquals(0, movedRight.pitch)
    }

    @Test
    fun resizeHonorsMinimumGridAndClipEnd() {
        val original = MidiNote(pitch = 64, startTick = 720L, durationTicks = 480L)
        val grown = transformMidiNoteFromGesture(original, 2400L, 240L, 3, 0, true)
        assertEquals(1200L, grown.durationTicks)
        assertEquals(720L, grown.startTick)
        val shrunk = transformMidiNoteFromGesture(original, 2400L, 240L, -10, 0, true)
        assertEquals(240L, shrunk.durationTicks)
        assertEquals(720L, shrunk.startTick)
    }

    @Test
    fun resizeNearClipEndCannotCreateOutOfRangeDuration() {
        val original = MidiNote(pitch = 67, startTick = 2100L, durationTicks = 240L)
        val result = transformMidiNoteFromGesture(original, 2400L, 240L, 100, 0, true)
        assertEquals(2100L, result.startTick)
        assertEquals(300L, result.durationTicks)
    }
}
