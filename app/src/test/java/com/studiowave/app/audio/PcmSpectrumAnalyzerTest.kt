package com.studiowave.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class PcmSpectrumAnalyzerTest {
    @Test fun fftLocatesAOneKilohertzToneAtTheExpectedBin() {
        val real = DoubleArray(PcmSpectrumAnalyzer.FFT_SIZE)
        val imaginary = DoubleArray(PcmSpectrumAnalyzer.FFT_SIZE)
        for (i in real.indices) real[i] = sin(2.0 * PI * 1_000.0 * i / 8_192.0)
        PcmSpectrumAnalyzer.fftInPlace(real, imaginary)
        val strongest = (1 until real.size / 2).maxByOrNull { real[it] * real[it] + imaginary[it] * imaginary[it] }!!
        assertEquals(125, strongest)
        assertTrue(real[strongest] * real[strongest] + imaginary[strongest] * imaginary[strongest] > 100_000.0)
    }

    @Test fun frequencyToBinMappingIsConsistent() {
        assertEquals(125, PcmSpectrumAnalyzer.binForFrequency(1_000.0, 8_192))
        assertEquals(0, PcmSpectrumAnalyzer.binForFrequency(-1.0, 44_100))
        assertEquals(512, PcmSpectrumAnalyzer.binForFrequency(99_000.0, 44_100))
    }

    @Test fun tuningMathRemainsFiniteForLowAndHighMidiPitches() {
        val state = StudioWaveVoiceDsp.VoiceState()
        for (pitch in 0..127) {
            val sample = StudioWaveVoiceDsp.sampleConfigured(pitch, pitch.toLong() + 100, 1000, 44_100, 0.5f,
                StudioWaveVoiceDsp.Parameters.DEFAULT, state)
            assertTrue(sample.isFinite())
        }
    }
}
