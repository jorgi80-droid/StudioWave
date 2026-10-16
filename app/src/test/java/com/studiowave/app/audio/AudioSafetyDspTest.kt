package com.studiowave.app.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioSafetyDspTest {
    @Test
    fun masterLimiterLeavesNormalLevelsUnchanged() {
        assertEquals(0.5f, softLimitMasterSample(0.5f), 0.000001f)
        assertEquals(-0.8f, softLimitMasterSample(-0.8f), 0.000001f)
    }

    @Test
    fun masterLimiterIsFiniteSymmetricAndBounded() {
        val inputs = listOf(-100f, -4f, -1f, -0.95f, -0.92f, 0f, 0.92f, 0.95f, 1f, 4f, 100f)
        val outputs = inputs.map { softLimitMasterSample(it) }
        assertTrue(outputs.all { it.isFinite() && it in -0.980001f..0.980001f })
        assertEquals(outputs[2], -outputs[8], 0.000001f)
        assertEquals(outputs[3], -outputs[7], 0.000001f)
    }

    @Test
    fun masterLimiterRejectsNonFiniteSamplesAsSilence() {
        assertEquals(0f, softLimitMasterSample(Float.NaN))
        assertEquals(0f, softLimitMasterSample(Float.POSITIVE_INFINITY))
        assertEquals(0f, softLimitMasterSample(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun masterLimiterIsMonotonicAboveThreshold() {
        val values = listOf(0.92f, 0.93f, 0.95f, 1f, 2f, 10f)
            .map { softLimitMasterSample(it) }
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a })
    }
}
