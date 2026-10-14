package com.studiowave.app

import com.studiowave.app.data.MidiNote
import com.studiowave.app.data.StudioProject
import com.studiowave.app.data.WaveClip
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrangementClipSummaryTest {
    @Test
    fun midiClipSummaryIncludesNoteCountAndMusicalLength() {
        val project = StudioProject(ppq = 960, numerator = 4, denominator = 4)
        val clip = WaveClip(name = "Chords", startTick = 0L, lengthTicks = 7680L,
            midiNotes = listOf(
                MidiNote(pitch = 60, startTick = 0L, durationTicks = 480L),
                MidiNote(pitch = 64, startTick = 0L, durationTicks = 480L)
            ))
        assertEquals("MIDI · 2 NOTES · 2 BARS", arrangementClipSummary("midi", clip, project))
    }

    @Test
    fun drumClipSummaryShowsActiveAndTotalSteps() {
        val project = StudioProject(ppq = 960)
        val clip = WaveClip(name = "Beat", startTick = 0L, lengthTicks = 3840L,
            drumSteps = listOf(true, false, true, false, false, false, false, false))
        assertEquals("DRUM · 2/8 STEPS · 1 BAR", arrangementClipSummary("drum", clip, project))
    }

    @Test
    fun audioClipSummaryUsesSourceNameAndBarCount() {
        val project = StudioProject(ppq = 960)
        val clip = WaveClip(name = "Audio", startTick = 0L, lengthTicks = 7680L,
            sourceUri = "content://samples/Breakbeat.wav")
        assertEquals("AUDIO · Breakbeat.wav · 2 BARS", arrangementClipSummary("audio", clip, project))
    }

    @Test
    fun summaryRemainsSafeForTinyClipAndInvalidMeterInputs() {
        val project = StudioProject(ppq = 960, numerator = 0, denominator = 0)
        val clip = WaveClip(name = "Tiny", startTick = 0L, lengthTicks = 1L)
        assertEquals("MIDI · 0 NOTES · 1 BAR", arrangementClipSummary("midi", clip, project))
    }
}
