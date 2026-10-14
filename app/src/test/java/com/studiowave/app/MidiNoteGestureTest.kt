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
        assertEquals(127, movedLeft.pitch)

        val movedRight = transformMidiNoteFromGesture(original.copy(pitch = 0), 3840L, 240L, 100, -12, false)
        assertEquals(2880L, movedRight.startTick)
        assertEquals(0, movedRight.pitch)
    }


    @Test
    fun fullStandardMidiRangeCanReachPitch127ButCannotExceedIt() {
        val note = MidiNote(pitch = 120, startTick = 0L, durationTicks = 240L)
        val high = transformMidiNoteFromGesture(note, 3840L, 240L, 0, 20, false)
        assertEquals(127, high.pitch)

        val alreadyAtTop = transformMidiNoteFromGesture(high, 3840L, 240L, 0, 1, false)
        assertEquals(127, alreadyAtTop.pitch)

        val low = transformMidiNoteFromGesture(note.copy(pitch = 7), 3840L, 240L, 0, -20, false)
        assertEquals(0, low.pitch)
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

    @Test
    fun malformedNoteStartIsClampedBeforeResize() {
        val original = MidiNote(pitch = 67, startTick = 2390L, durationTicks = 240L)
        val result = transformMidiNoteFromGesture(original, 2400L, 240L, 100, 0, true)
        assertEquals(2160L, result.startTick)
        assertEquals(240L, result.durationTicks)
        assertEquals(2400L, result.startTick + result.durationTicks)
    }

    @Test
    fun shrinkingClipDropsNotesPastEndAndShortensCrossingNotes() {
        val crossing = MidiNote(pitch = 60, startTick = 800L, durationTicks = 500L)
        val outside = MidiNote(pitch = 64, startTick = 1200L, durationTicks = 240L)
        val safe = fitMidiNotesToClip(listOf(crossing, outside), clipLengthTicks = 1000L)
        assertEquals(1, safe.size)
        assertEquals(800L, safe.single().startTick)
        assertEquals(200L, safe.single().durationTicks)
    }

    @Test
    fun noteFitPreservesNotesWithinClipAndClampsNegativeStart() {
        val normal = MidiNote(pitch = 64, startTick = 300L, durationTicks = 240L)
        val malformed = MidiNote(pitch = 65, startTick = -30L, durationTicks = 100L)
        val safe = fitMidiNotesToClip(listOf(normal, malformed), clipLengthTicks = 960L)
        assertEquals(2, safe.size)
        assertEquals(300L, safe[0].startTick)
        assertEquals(240L, safe[0].durationTicks)
        assertEquals(0L, safe[1].startTick)
        assertEquals(100L, safe[1].durationTicks)
    }

    @Test
    fun pianoRollVisibleStepMappingUsesOneStableRoundingRule() {
        assertEquals(32, pianoRollVisibleSteps(16, 1f))
        assertEquals(26, pianoRollVisibleSteps(16, 1.25f))
        assertEquals(40, pianoRollVisibleSteps(16, 0.8f))
        assertEquals(4, pianoRollVisibleSteps(16, 8f))
        // At the minimum zoom, the shared mapping rounds 32 / 0.35 to 91 steps;
        // the 96-step ceiling is a safety bound, not the expected value at every minimum zoom.
        assertEquals(91, pianoRollVisibleSteps(16, 0.35f))
        assertEquals(4, pianoRollVisibleSteps(0, 8f))
    }
}
