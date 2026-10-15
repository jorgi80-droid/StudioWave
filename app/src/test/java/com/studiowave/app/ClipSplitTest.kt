package com.studiowave.app

import com.studiowave.app.data.MidiNote
import com.studiowave.app.data.WaveClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ClipSplitTest {
    @Test
    fun splitKeepsAudioSourceOffsetContinuousAndClipBoundsContiguous() {
        val source = WaveClip(
            name = "Take", startTick = 1000L, lengthTicks = 4000L,
            sourceUri = "file:///take.wav", sourceOffsetTicks = 200L
        )

        val (left, right) = splitWaveClip(source, 2500L)!!

        assertEquals(1000L, left.startTick)
        assertEquals(1500L, left.lengthTicks)
        assertEquals(200L, left.sourceOffsetTicks)
        assertEquals(2500L, right.startTick)
        assertEquals(2500L, right.lengthTicks)
        assertEquals(1700L, right.sourceOffsetTicks)
        assertEquals(source.sourceUri, right.sourceUri)
        assertNotEquals(left.id, right.id)
    }

    @Test
    fun splitClipsCrossingMidiNotesIntoSeparateNonOverlappingSegments() {
        val crossing = MidiNote(pitch = 60, startTick = 1200L, durationTicks = 1800L)
        val rightOnly = MidiNote(pitch = 64, startTick = 2000L, durationTicks = 300L)
        val source = WaveClip(
            name = "MIDI", startTick = 1000L, lengthTicks = 4000L,
            midiNotes = listOf(crossing, rightOnly)
        )

        val (left, right) = splitWaveClip(source, 2500L)!!

        assertEquals(1, left.midiNotes.size)
        assertEquals(1200L, left.midiNotes.single().startTick)
        assertEquals(300L, left.midiNotes.single().durationTicks)
        assertEquals(crossing.id, left.midiNotes.single().id)

        assertEquals(2, right.midiNotes.size)
        val crossingRight = right.midiNotes.first { it.pitch == 60 }
        assertEquals(0L, crossingRight.startTick)
        assertEquals(1500L, crossingRight.durationTicks)
        assertNotEquals(crossing.id, crossingRight.id)
        val rightOnlyNote = right.midiNotes.first { it.pitch == 64 }
        assertEquals(500L, rightOnlyNote.startTick)
        assertEquals(300L, rightOnlyNote.durationTicks)
    }

    @Test
    fun splitRejectsPositionsAtOrOutsideClipEdges() {
        val source = WaveClip(name = "MIDI", startTick = 100L, lengthTicks = 800L)
        assertEquals(null, splitWaveClip(source, 100L))
        assertEquals(null, splitWaveClip(source, 900L))
        assertEquals(null, splitWaveClip(source, 50L))
    }

    @Test
    fun splitHandlesSaturatedNoteEndWithoutOverflowingItsWindow() {
        val note = MidiNote(pitch = 60, startTick = Long.MAX_VALUE - 50L, durationTicks = 100L)
        val source = WaveClip(name = "MIDI", startTick = 0L, lengthTicks = Long.MAX_VALUE,
            midiNotes = listOf(note))
        val split = splitWaveClip(source, Long.MAX_VALUE - 100L)
        assertNotNull(split)
        val (left, right) = split!!
        assertEquals(0, left.midiNotes.size)
        assertEquals(1, right.midiNotes.size)
        assertEquals(50L, right.midiNotes.single().startTick)
        assertEquals(50L, right.midiNotes.single().durationTicks)
    }
}
