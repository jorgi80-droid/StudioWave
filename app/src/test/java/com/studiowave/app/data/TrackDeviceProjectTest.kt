package com.studiowave.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackDeviceProjectTest {
    @Test fun deviceChainAndParametersRoundTripThroughProjectJson() {
        val original = StudioProject()
        val midi = original.tracks.first { it.kind == "midi" }
        val devices = listOf(
            TrackDevice(type = "DIRECTWAVE", name = "DirectWave"),
            TrackDevice(type = "PARAM_EQ_8", name = "Parametric EQ Eight", parameters = defaultDeviceParameters("PARAM_EQ_8") + ("band4" to 4.5f)),
            TrackDevice(type = "COMPRESSOR", name = "Compressor", parameters = defaultDeviceParameters("COMPRESSOR") + ("thresholdDb" to -21f))
        )
        val edited = original.copy(tracks = original.tracks.map { if (it.id == midi.id) it.copy(devices = devices) else it })
        val actual = StudioProject.fromJson(JSONObject(edited.toJson().toString())).tracks.first { it.id == midi.id }.devices
        assertEquals(listOf("DIRECTWAVE", "PARAM_EQ_8", "COMPRESSOR"), actual.map { it.type })
        assertEquals(4.5f, actual[1].parameters["band4"]!!, 0.001f)
        assertEquals(-21f, actual[2].parameters["thresholdDb"]!!, 0.001f)
        assertTrue(actual.all { it.enabled })
    }

    @Test fun studioWaveSynthParametersRoundTripThroughProjectJson() {
        val original = StudioProject()
        val midi = original.tracks.first { it.kind == "midi" }
        val instrument = TrackDevice(
            type = "STUDIO_SYNTH",
            name = "StudioWave Dual Synth",
            parameters = defaultDeviceParameters("STUDIO_SYNTH") + mapOf(
                "oscillatorA" to 4f, "oscillatorB" to 2f, "mix" to 0.63f,
                "tuning" to 2f, "cutoffHz" to 1240f, "releaseMs" to 420f, "modRateHz" to 3.5f
            )
        )
        val edited = original.copy(tracks = original.tracks.map {
            if (it.id == midi.id) it.copy(devices = listOf(instrument)) else it
        })
        val restored = StudioProject.fromJson(JSONObject(edited.toJson().toString()))
            .tracks.first { it.id == midi.id }.devices.single()
        assertEquals("STUDIO_SYNTH", restored.type)
        assertEquals(4f, restored.parameters["oscillatorA"]!!, 0.001f)
        assertEquals(2f, restored.parameters["tuning"]!!, 0.001f)
        assertEquals(1240f, restored.parameters["cutoffHz"]!!, 0.001f)
        assertEquals(420f, restored.parameters["releaseMs"]!!, 0.001f)
        assertEquals(3.5f, restored.parameters["modRateHz"]!!, 0.001f)
    }

    @Test fun audioWarpToggleRoundTripsAndOldProjectsDefaultToOff() {
        val original = StudioProject()
        val audio = original.tracks.first { it.kind == "audio" }
        val clip = WaveClip(
            name = "Tempo Sync",
            startTick = 0,
            lengthTicks = 3840,
            sourceUri = "content://studiowave/test.wav",
            sourceOffsetMicros = 125_000L,
            sourceLengthMicros = 2_000_000L,
            warpEnabled = true,
            clipGainDb = -6.0f
        )
        val edited = original.copy(tracks = original.tracks.map {
            if (it.id == audio.id) it.copy(clips = listOf(clip)) else it
        })
        val restored = StudioProject.fromJson(JSONObject(edited.toJson().toString()))
            .tracks.first { it.id == audio.id }.clips.single()
        assertTrue(restored.warpEnabled)
        assertEquals(-6.0f, restored.clipGainDb, 0.001f)
        assertEquals(125_000L, restored.sourceOffsetMicros)
        assertEquals(2_000_000L, restored.sourceLengthMicros)

        val legacyJson = edited.toJson()
        val tracks = legacyJson.getJSONArray("tracks")
        val audioJson = (0 until tracks.length()).map { tracks.getJSONObject(it) }
            .first { it.optString("kind") == "audio" }
        audioJson.getJSONArray("clips").getJSONObject(0).remove("warpEnabled")
        audioJson.getJSONArray("clips").getJSONObject(0).remove("clipGainDb")
        val legacyRestored = StudioProject.fromJson(legacyJson)
            .tracks.first { it.id == audio.id }.clips.single()
        assertFalse(legacyRestored.warpEnabled)
        assertEquals(0f, legacyRestored.clipGainDb, 0f)
    }

    @Test fun legacyProjectWithoutDeviceArrayStillOpens() {
        val json = StudioProject().toJson()
        val tracks = json.getJSONArray("tracks")
        for (index in 0 until tracks.length()) tracks.getJSONObject(index).remove("devices")
        assertTrue(StudioProject.fromJson(json).tracks.all { it.devices.isEmpty() })
    }

    @Test fun duplicateInstrumentDevicesAreRejected() {
        val json = StudioProject().toJson()
        val tracks = json.getJSONArray("tracks")
        val trackIndex = (0 until tracks.length()).first { tracks.getJSONObject(it).optString("kind") == "midi" }
        tracks.getJSONObject(trackIndex).put("devices", org.json.JSONArray()
            .put(JSONObject().put("type", "SYNTH_SINE").put("parameters", JSONObject()))
            .put(JSONObject().put("type", "SYNTH_SAW").put("parameters", JSONObject())))
        var rejected = false
        try { StudioProject.fromJson(json) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun disabledEffectsAndDefaultParametersPersist() {
        val json = StudioProject().toJson()
        val tracks = json.getJSONArray("tracks")
        val trackIndex = (0 until tracks.length()).first { tracks.getJSONObject(it).optString("kind") == "audio" }
        tracks.getJSONObject(trackIndex).put("devices", org.json.JSONArray().put(
            JSONObject().put("type", "REVERB").put("enabled", false).put("parameters", JSONObject().put("wet", 0.8))
        ))
        val actual = StudioProject.fromJson(json).tracks.first { it.kind == "audio" }.devices.single()
        assertFalse(actual.enabled)
        assertEquals(0.8f, actual.parameters["wet"]!!, 0.001f)
        assertEquals(1.6f, actual.parameters["decay"]!!, 0.001f)
    }

    @Test fun unknownDeviceTypeIsRejected() {
        val json = StudioProject().toJson()
        val tracks = json.getJSONArray("tracks")
        val trackIndex = (0 until tracks.length()).first { tracks.getJSONObject(it).optString("kind") == "audio" }
        tracks.getJSONObject(trackIndex).put("devices", org.json.JSONArray().put(JSONObject().put("type", "MAGIC_ABLETON_DEVICE").put("parameters", JSONObject())))
        var rejected = false
        try { StudioProject.fromJson(json) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
}
