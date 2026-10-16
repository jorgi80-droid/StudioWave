package com.studiowave.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioWaveVoiceDspTest {
    @Test fun midiPitchMapsToEqualTemperament() {
        assertEquals(440.0, StudioWaveVoiceDsp.midiFrequency(69), 1e-9)
        assertEquals(880.0, StudioWaveVoiceDsp.midiFrequency(81), 1e-8)
        assertEquals(220.0, StudioWaveVoiceDsp.midiFrequency(57), 1e-8)
    }

    @Test fun invalidPitchAndReferenceAreBounded() {
        assertEquals(StudioWaveVoiceDsp.midiFrequency(0), StudioWaveVoiceDsp.midiFrequency(-99), 0.0)
        assertEquals(440.0, StudioWaveVoiceDsp.midiFrequency(69, Double.NaN), 1e-9)
    }

    @Test fun voiceIsSilentOutsideNoteAndRampsAtEdges() {
        assertEquals(0f, StudioWaveVoiceDsp.sample(69, -1, 1000, 48000, 0.5f), 0f)
        assertEquals(0f, StudioWaveVoiceDsp.sample(69, 1000, 1000, 48000, 0.5f), 0f)
        assertEquals(0f, StudioWaveVoiceDsp.sample(69, 0, 1000, 48000, 0.5f), 0f)
        assertTrue(StudioWaveVoiceDsp.sample(69, 100, 10000, 48000, 0.5f) != 0f)
    }

    @Test fun peakIgnoresNonFiniteSamples() {
        assertEquals(0.75f, StudioWaveVoiceDsp.peak(floatArrayOf(Float.NaN, -0.75f, Float.POSITIVE_INFINITY)), 0f)
    }
}
