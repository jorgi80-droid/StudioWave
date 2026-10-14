package com.studiowave.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class StereoMeterTest {
    @Test
    fun leftAndRightChannelsAreMeasuredIndependently() {
        val levels = measureStereoMeterLevels(floatArrayOf(
            0.25f, -0.75f,
            -0.50f, 0.50f
        ))
        assertEquals(0.50f, levels.peakLeft, 0.0001f)
        assertEquals(0.75f, levels.peakRight, 0.0001f)
        assertEquals(kotlin.math.sqrt((0.25 * 0.25 + 0.50 * 0.50) / 2.0).toFloat(),
            levels.rmsLeft, 0.0001f)
        assertEquals(kotlin.math.sqrt((0.75 * 0.75 + 0.50 * 0.50) / 2.0).toFloat(),
            levels.rmsRight, 0.0001f)
    }

    @Test
    fun silenceAndInvalidSampleCountProduceZeroMeters() {
        assertEquals(StereoMeterLevels(0f, 0f, 0f, 0f), measureStereoMeterLevels(FloatArray(8)))
        assertEquals(StereoMeterLevels(0f, 0f, 0f, 0f),
            measureStereoMeterLevels(floatArrayOf(0.8f, 0.7f), sampleCount = 0))
    }

    @Test
    fun sampleCountIsBoundedToBufferAndOddTailIsHandledSafely() {
        val levels = measureStereoMeterLevels(floatArrayOf(0.3f, 0.4f, 0.9f), sampleCount = 50)
        assertEquals(0.9f, levels.peakLeft, 0.0001f)
        assertEquals(0.4f, levels.peakRight, 0.0001f)
        assertEquals(kotlin.math.sqrt((0.3 * 0.3 + 0.9 * 0.9) / 2.0).toFloat(), levels.rmsLeft, 0.0001f)
        assertEquals(0.4f, levels.rmsRight, 0.0001f)
    }

    @Test
    fun samplesOverUnityAreBoundedForDisplay() {
        val levels = measureStereoMeterLevels(floatArrayOf(1.3f, -1.4f))
        assertEquals(1f, levels.peakLeft, 0.0001f)
        assertEquals(1f, levels.peakRight, 0.0001f)
        assertEquals(1f, levels.rmsLeft, 0.0001f)
        assertEquals(1f, levels.rmsRight, 0.0001f)
    }
}
