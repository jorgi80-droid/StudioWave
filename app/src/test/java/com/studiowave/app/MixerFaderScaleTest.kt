package com.studiowave.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MixerFaderScaleTest {
    @Test
    fun faderMapsEndpointsAndTrueMute() {
        assertEquals(0f, mixerFaderGainAtFraction(0f, 1.5f), 0f)
        assertEquals(1.5f, mixerFaderGainAtFraction(1f, 1.5f), 1e-5f)
        assertEquals(0f, mixerFaderGainAtFraction(Float.NaN, 1.5f), 0f)
    }

    @Test
    fun unityGainOccupiesZeroDbPositionForChannelAndMaster() {
        val channelZeroDbPosition = mixerFaderFractionForGain(1f, 1.5f)
        val masterZeroDbPosition = mixerFaderFractionForGain(1f, 1f)
        assertTrue(channelZeroDbPosition in 0f..1f)
        assertEquals(1f, masterZeroDbPosition, 1e-6f)
        assertEquals(1f, mixerFaderGainAtFraction(channelZeroDbPosition, 1.5f), 1e-4f)
        assertEquals(1f, mixerFaderGainAtFraction(masterZeroDbPosition, 1f), 1e-4f)
    }

    @Test
    fun gainAndPositionRoundTripAcrossFaderTravel() {
        listOf(1.5f, 1f).forEach { maximum ->
            listOf(0.001f, 0.01f, 0.1f, 0.25f, 0.5f, 0.8f, 1f, maximum)
                .filter { it <= maximum }
                .forEach { gain ->
                    val fraction = mixerFaderFractionForGain(gain, maximum)
                    val restored = mixerFaderGainAtFraction(fraction, maximum)
                    assertTrue("gain=$gain maximum=$maximum restored=$restored", abs(gain - restored) <= 1e-4f)
                }
        }
    }

    @Test
    fun invalidInputsStayFiniteAndBounded() {
        assertEquals(0f, mixerFaderGainAtFraction(Float.NEGATIVE_INFINITY, 1f), 0f)
        assertEquals(0f, mixerFaderGainAtFraction(0.5f, Float.NaN), 0f)
        assertEquals(0f, mixerFaderFractionForGain(Float.NaN, 1f), 0f)
        assertTrue(mixerFaderFractionForGain(Float.POSITIVE_INFINITY, 1f).isFinite())
    }
}
