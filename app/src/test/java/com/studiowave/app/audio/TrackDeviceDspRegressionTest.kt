package com.studiowave.app.audio

import com.studiowave.app.data.StudioProject
import com.studiowave.app.data.TrackDevice
import com.studiowave.app.data.defaultDeviceParameters
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Regression tests for audible insert behavior and project persistence.
 * These tests intentionally exercise the processor output, not the Compose controls.
 */
class TrackDeviceDspRegressionTest {
    private fun rms(samples: FloatArray, firstFrame: Int = 0, frameCount: Int = samples.size / 2 - firstFrame): Double {
        var sum = 0.0
        val end = (firstFrame + frameCount).coerceAtMost(samples.size / 2)
        for (frame in firstFrame until end) {
            val left = samples[frame * 2].toDouble()
            val right = samples[frame * 2 + 1].toDouble()
            sum += (left * left + right * right) * 0.5
        }
        return sqrt(sum / (end - firstFrame).coerceAtLeast(1))
    }

    private fun sine(frequencyHz: Double, sampleRate: Int = 48000, frames: Int = 8192, amplitude: Float = 0.5f): FloatArray =
        FloatArray(frames * 2) { index ->
            val frame = index / 2
            (sin(2.0 * Math.PI * frequencyHz * frame / sampleRate) * amplitude).toFloat()
        }

    @Test fun parametricEqBandGainChangesTheTargetSignal() {
        val input = sine(1000.0)
        val neutral = input.copyOf()
        val neutralMaster = FloatArray(input.size)
        TrackDeviceDsp(48000).process(
            neutral, neutralMaster, neutral.size, 1f,
            listOf(TrackDevice(type = "PARAM_EQ_8", name = "EQ Eight", parameters = defaultDeviceParameters("PARAM_EQ_8")))
        )

        val boosted = sine(1000.0)
        val boostedMaster = FloatArray(boosted.size)
        TrackDeviceDsp(48000).process(
            boosted, boostedMaster, boosted.size, 1f,
            listOf(TrackDevice(type = "PARAM_EQ_8", name = "EQ Eight",
                parameters = defaultDeviceParameters("PARAM_EQ_8") + ("band5" to 12f)))
        )

        val neutralRms = rms(neutral, firstFrame = 2048)
        val boostedRms = rms(boosted, firstFrame = 2048)
        assertTrue("neutral EQ should pass the tone without severe attenuation", neutralRms > 0.25)
        assertTrue("12 dB band boost must measurably increase 1 kHz energy: $neutralRms -> $boostedRms", boostedRms > neutralRms * 1.5)
        assertTrue(boosted.all { it.isFinite() })
    }

    @Test fun compressorReducesSteadySignalAboveThreshold() {
        val input = sine(440.0, amplitude = 0.8f)
        val before = rms(input, firstFrame = 1024)
        val master = FloatArray(input.size)
        val compressor = TrackDevice(
            type = "COMPRESSOR",
            name = "Compressor",
            parameters = defaultDeviceParameters("COMPRESSOR") +
                mapOf("thresholdDb" to -30f, "ratio" to 8f, "attackMs" to 0.1f, "releaseMs" to 10f, "makeupDb" to 0f)
        )
        TrackDeviceDsp(48000).process(input, master, input.size, 1f, listOf(compressor))
        val after = rms(input, firstFrame = 2048)
        assertTrue("compressor must reduce a sustained signal above threshold: $before -> $after", after < before * 0.25)
        assertTrue(input.all { it.isFinite() })
    }

    @Test fun limiterOutputDoesNotExceedConfiguredCeiling() {
        val ceilingDb = -6f
        val ceiling = Math.pow(10.0, ceilingDb / 20.0).toFloat()
        val input = FloatArray(2048) { index -> if (index % 2 == 0) 0.95f else -0.95f }
        val master = FloatArray(input.size)
        TrackDeviceDsp(48000).process(
            input, master, input.size, 1f,
            listOf(TrackDevice(type = "LIMITER", name = "Limiter",
                parameters = defaultDeviceParameters("LIMITER") + ("ceilingDb" to ceilingDb)))
        )
        assertTrue("limiter must enforce its ceiling", input.all { abs(it) <= ceiling + 1e-5f })
        assertTrue("limiter must produce finite samples", input.all { it.isFinite() })
    }

    @Test fun masterReceivesOnlyTheProcessedMinusDryDelta() {
        val input = FloatArray(512) { index -> if (index % 2 == 0) 0.75f else -0.75f }
        val dry = input.copyOf()
        val master = FloatArray(input.size)
        TrackDeviceDsp(44100).process(
            input, master, input.size, 1f,
            listOf(TrackDevice(type = "SATURATOR", name = "Saturator",
                parameters = defaultDeviceParameters("SATURATOR") + mapOf("drive" to 6f, "wet" to 1f)))
        )
        for (i in input.indices) {
            assertTrue("master delta should equal processed minus dry at index $i",
                abs(master[i] - (input[i] - dry[i])) < 1e-5f)
        }
    }

    @Test fun effectParametersAndEnabledStateSurviveProjectSerialization() {
        val base = StudioProject()
        val track = base.tracks.first { it.kind == "midi" }
        val devices = listOf(
            TrackDevice(type = "PARAM_EQ_8", name = "EQ Eight", enabled = true,
                parameters = defaultDeviceParameters("PARAM_EQ_8") + mapOf("band5" to 4.5f)),
            TrackDevice(type = "DELAY", name = "Delay", enabled = false,
                parameters = defaultDeviceParameters("DELAY") + mapOf("wet" to 0.67f, "timeMs" to 245f))
        )
        val edited = base.copy(tracks = base.tracks.map { if (it.id == track.id) it.copy(devices = devices) else it })
        val restored = StudioProject.fromJson(JSONObject(edited.toJson().toString()))
        val restoredDevices = restored.tracks.first { it.id == track.id }.devices
        assertEquals(2, restoredDevices.size)
        assertEquals("PARAM_EQ_8", restoredDevices[0].type)
        assertEquals(4.5f, restoredDevices[0].parameters["band5"]!!)
        assertEquals(false, restoredDevices[1].enabled)
        assertEquals(0.67f, restoredDevices[1].parameters["wet"]!!)
        assertEquals(245f, restoredDevices[1].parameters["timeMs"]!!)
    }
}
