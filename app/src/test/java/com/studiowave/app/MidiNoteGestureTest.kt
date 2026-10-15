package com.studiowave.app

import com.studiowave.app.data.MidiNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun leadingEdgeResizeMovesStartAndKeepsOriginalEndFixed() {
        val original = MidiNote(pitch = 64, startTick = 720L, durationTicks = 480L)
        val extendedLeft = transformMidiNoteFromGesture(
            note = original, clipLengthTicks = 2400L, stepTicks = 240L,
            deltaSteps = -2, deltaPitch = 0, resize = false, resizeLeading = true
        )
        assertEquals(240L, extendedLeft.startTick)
        assertEquals(960L, extendedLeft.durationTicks)
        assertEquals(1200L, extendedLeft.startTick + extendedLeft.durationTicks)

        val shortenedLeft = transformMidiNoteFromGesture(
            note = original, clipLengthTicks = 2400L, stepTicks = 240L,
            deltaSteps = 1, deltaPitch = 0, resize = false, resizeLeading = true
        )
        assertEquals(960L, shortenedLeft.startTick)
        assertEquals(240L, shortenedLeft.durationTicks)
        assertEquals(1200L, shortenedLeft.startTick + shortenedLeft.durationTicks)
    }

    @Test
    fun leadingEdgeResizeCannotCrossClipStartOrCollapseBelowSnapStep() {
        val original = MidiNote(pitch = 64, startTick = 240L, durationTicks = 240L)
        val extendToStart = transformMidiNoteFromGesture(
            note = original, clipLengthTicks = 960L, stepTicks = 240L,
            deltaSteps = -20, deltaPitch = 0, resize = false, resizeLeading = true
        )
        assertEquals(0L, extendToStart.startTick)
        assertEquals(480L, extendToStart.durationTicks)
        assertEquals(480L, extendToStart.startTick + extendToStart.durationTicks)

        val collapse = transformMidiNoteFromGesture(
            note = original, clipLengthTicks = 960L, stepTicks = 240L,
            deltaSteps = 20, deltaPitch = 0, resize = false, resizeLeading = true
        )
        assertEquals(240L, collapse.startTick)
        assertEquals(240L, collapse.durationTicks)
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
    fun selectionIdsResolveToExistingNotesWithoutDuplicates() {
        val a = MidiNote(pitch = 60, startTick = 0L, durationTicks = 240L)
        val b = MidiNote(pitch = 64, startTick = 240L, durationTicks = 240L)
        val c = MidiNote(pitch = 67, startTick = 480L, durationTicks = 240L)
        val notes = listOf(a, b, c)
        val selectedIds = setOf(a.id, c.id, "stale-note-id")
        val selected = notes.filter { it.id in selectedIds }
        assertEquals(listOf(a.id, c.id), selected.map { it.id })
        assertEquals(2, selected.distinctBy { it.id }.size)
    }

    @Test
    fun selectionCanContainEveryNoteForContextMenuSelectAll() {
        val notes = listOf(
            MidiNote(pitch = 60, startTick = 0L, durationTicks = 240L),
            MidiNote(pitch = 64, startTick = 240L, durationTicks = 240L),
            MidiNote(pitch = 67, startTick = 480L, durationTicks = 240L)
        )
        val selectedIds = notes.map { it.id }.toSet()
        assertEquals(notes.size, notes.count { it.id in selectedIds })
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

    @Test
    fun extremelyShortClipStillProducesValidNoteBounds() {
        val note = MidiNote(pitch = 60, startTick = Long.MAX_VALUE, durationTicks = Long.MAX_VALUE)
        val resized = transformMidiNoteFromGesture(
            note = note, clipLengthTicks = 1L, stepTicks = 240L,
            deltaSteps = Int.MAX_VALUE, deltaPitch = 0, resize = true
        )
        assertEquals(0L, resized.startTick)
        assertEquals(1L, resized.durationTicks)

        val moved = transformMidiNoteFromGesture(
            note = note, clipLengthTicks = 1L, stepTicks = 240L,
            deltaSteps = Int.MIN_VALUE, deltaPitch = 0, resize = false
        )
        assertEquals(0L, moved.startTick)
        assertEquals(1L, moved.durationTicks)
        assertEquals(1L, moved.startTick + moved.durationTicks)
    }

    @Test
    fun gestureArithmeticCannotOverflowNearLongMax() {
        val note = MidiNote(pitch = 64, startTick = Long.MAX_VALUE - 100L, durationTicks = 80L)
        val result = transformMidiNoteFromGesture(
            note = note, clipLengthTicks = Long.MAX_VALUE, stepTicks = Long.MAX_VALUE / 3L,
            deltaSteps = Int.MAX_VALUE, deltaPitch = 0, resize = true
        )
        assertTrue(result.startTick >= 0L)
        assertTrue(result.durationTicks >= 1L)
        assertTrue(result.startTick <= Long.MAX_VALUE - result.durationTicks)

        val moved = transformMidiNoteFromGesture(
            note = note, clipLengthTicks = Long.MAX_VALUE, stepTicks = Long.MAX_VALUE / 3L,
            deltaSteps = Int.MAX_VALUE, deltaPitch = 0, resize = false
        )
        assertTrue(moved.startTick >= 0L)
        assertTrue(moved.durationTicks >= 1L)
        assertTrue(moved.startTick <= Long.MAX_VALUE - moved.durationTicks)
    }


}
