package com.studiowave.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class MasterLoudnessMeterTest {
    @Test
    fun silenceReportsFloor() {
        val meter = MasterLoudnessMeter(44_100)
        val silence = FloatArray(44_100 * 2)
        val result = meter.processBlock(silence, silence.size)
        assertEquals(MasterLoudnessMeter.SILENCE_LUFS, result, 0.01f)
    }

    @Test
    fun stereoToneProducesFiniteMomentaryLoudness() {
        val sampleRate = 44_100
        val frames = sampleRate * 2
        val pcm = FloatArray(frames * 2)
        for (frame in 0 until frames) {
            val sample = (0.1 * sin(2.0 * PI * 1_000.0 * frame / sampleRate)).toFloat()
            pcm[frame * 2] = sample
            pcm[frame * 2 + 1] = sample
        }
        val meter = MasterLoudnessMeter(sampleRate)
        val result = meter.processBlock(pcm, pcm.size)
        assertTrue("LUFS must be finite", result.isFinite())
        assertTrue("Tone should be above the silence gate", result > -40f)
        assertTrue("Tone should not exceed full scale", result < 0f)
    }

    @Test
    fun resetReturnsMeterToSilenceFloor() {
        val meter = MasterLoudnessMeter(44_100)
        val pcm = FloatArray(44_100 * 2) { 0.25f }
        meter.processBlock(pcm, pcm.size)
        meter.reset()
        val silence = FloatArray(512)
        assertEquals(MasterLoudnessMeter.SILENCE_LUFS, meter.processBlock(silence, silence.size), 0.01f)
    }
}
