package com.studiowave.app.audio

import com.studiowave.app.data.defaultDeviceParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParametricEqMathTest {
    @Test fun unityGainsProduceFlatZeroDbResponse() {
        val response = ParametricEqMath.responseDbAt(1_000.0, 44_100, defaultDeviceParameters("PARAM_EQ_8"))
        assertEquals(0.0, response, 0.0001)
    }

    @Test fun graphResponseUsesTheActualBandGainFrequencyAndQ() {
        val parameters = defaultDeviceParameters("PARAM_EQ_8") + mapOf(
            "band5" to 9f,
            "band5FreqNorm" to ParametricEqMath.frequencyNormForHz(1_000.0),
            "band5Q" to 1.2f
        )
        val center = ParametricEqMath.responseDbAt(1_000.0, 44_100, parameters)
        val far = ParametricEqMath.responseDbAt(120.0, 44_100, parameters)
        assertTrue(center > 7.0)
        assertTrue(center > far)
    }

    @Test fun disabledEqShowsUnityResponse() {
        val parameters = defaultDeviceParameters("PARAM_EQ_8") + ("band4" to -12f)
        assertEquals(0.0, ParametricEqMath.responseDbAt(900.0, 44_100, parameters, enabled = false), 0.0)
    }
}
