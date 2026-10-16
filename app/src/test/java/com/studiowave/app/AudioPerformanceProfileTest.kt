package com.studiowave.app

import com.studiowave.app.audio.AudioPerformanceProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPerformanceProfileTest {
    @Test
    fun lowRamDevicePrefersStableQueue() {
        val device = AudioPerformanceProfile.DeviceCapabilities(
            cpuCores = 8,
            memoryClassMb = 128,
            lowRamDevice = true,
            outputSampleRateHz = 48000,
            outputBurstFrames = 192
        )
        assertEquals("STABLE", AudioPerformanceProfile.recommendedProfile(device))
    }

    @Test
    fun highCapabilityDevicePrefersLowLatency() {
        val device = AudioPerformanceProfile.DeviceCapabilities(
            cpuCores = 8,
            memoryClassMb = 512,
            lowRamDevice = false,
            outputSampleRateHz = 48000,
            outputBurstFrames = 192
        )
        assertEquals("LOW_LATENCY", AudioPerformanceProfile.recommendedProfile(device))
    }

    @Test
    fun midRangeDevicePrefersBalanced() {
        val device = AudioPerformanceProfile.DeviceCapabilities(
            cpuCores = 6,
            memoryClassMb = 256,
            lowRamDevice = false,
            outputSampleRateHz = 44100,
            outputBurstFrames = 256
        )
        assertEquals("BALANCED", AudioPerformanceProfile.recommendedProfile(device))
    }
}
