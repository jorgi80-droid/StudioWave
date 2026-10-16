package com.studiowave.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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

    @Test fun equalTemperamentMatchesReferenceA4() {
        assertEquals(440.0, StudioWaveVoiceDsp.midiFrequency(69), 0.0001)
        assertEquals(261.625565, StudioWaveVoiceDsp.midiFrequency(60), 0.001)
    }

    @Test fun midiPitchAndReferenceAreBounded() {
        assertEquals(StudioWaveVoiceDsp.midiFrequency(0), StudioWaveVoiceDsp.midiFrequency(-500), 0.0)
        assertEquals(StudioWaveVoiceDsp.midiFrequency(127), StudioWaveVoiceDsp.midiFrequency(500), 0.0)
        assertEquals(440.0, StudioWaveVoiceDsp.midiFrequency(69, Double.NaN), 0.0)
    }

    @Test fun justAndPythagoreanTuningsAreRealIntervals() {
        val root = StudioWaveVoiceDsp.tunedFrequency(60, 1, 60)
        assertEquals(StudioWaveVoiceDsp.midiFrequency(60), root, 1e-8)
        val equalThird = StudioWaveVoiceDsp.tunedFrequency(64, 0, 60)
        val justThird = StudioWaveVoiceDsp.tunedFrequency(64, 1, 60)
        val pythagoreanThird = StudioWaveVoiceDsp.tunedFrequency(64, 2, 60)
        assertTrue(abs(equalThird - justThird) > 0.5)
        assertTrue(abs(pythagoreanThird - justThird) > 0.5)
        assertEquals(StudioWaveVoiceDsp.tunedFrequency(60, 1, 60) * 2.0, StudioWaveVoiceDsp.tunedFrequency(72, 1, 60), 1e-8)
    }

    @Test fun configuredVoiceHasAdsrAndStopsAfterReleaseTail() {
        val parameters = StudioWaveVoiceDsp.Parameters.DEFAULT.copy(
            attackMs = 0, decayMs = 0, sustain = 1f, releaseMs = 20, gain = 1f, cutoffHz = 20_000f
        )
        val state = StudioWaveVoiceDsp.VoiceState()
        val early = StudioWaveVoiceDsp.sampleConfigured(60, 200, 2000, 44_100, 1f, parameters, state, heldFrames = 1000)
        assertTrue(early.isFinite())
        val last = StudioWaveVoiceDsp.sampleConfigured(60, 1999, 2000, 44_100, 1f, parameters, state, heldFrames = 1000)
        assertTrue(abs(last) < 0.01f)
        assertEquals(0f, StudioWaveVoiceDsp.sampleConfigured(60, 2000, 2000, 44_100, 1f, parameters, state, heldFrames = 1000), 0f)
    }

    @Test fun allOscillatorShapesProduceFiniteSignal() {
        for (shape in 0..4) {
            val params = StudioWaveVoiceDsp.Parameters.DEFAULT.copy(oscillatorA = shape, oscillatorB = shape, mix = 0.5f)
            val state = StudioWaveVoiceDsp.VoiceState()
            val values = (1L..128L).map {
                StudioWaveVoiceDsp.sampleConfigured(60, it, 2000, 44_100, 0.8f, params, state)
            }
            assertTrue(values.all { it.isFinite() })
            assertTrue(values.any { abs(it) > 0.0001f })
        }
    }

    @Test fun peakIgnoresNonFiniteValues() {
        assertEquals(0.75f, StudioWaveVoiceDsp.peak(floatArrayOf(Float.NaN, 0.5f, -0.75f, Float.POSITIVE_INFINITY)), 0.0f)
    }
}
