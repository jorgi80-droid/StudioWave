package com.studiowave.app

import com.studiowave.app.data.StudioProject
import com.studiowave.app.data.TrackDevice
import com.studiowave.app.data.defaultDeviceParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.json.JSONObject
import org.junit.Test

class ProjectModelTest {
    @Test fun starterProjectHasFourTracksAndUsableTempo() {
        val project = StudioProject()
        assertEquals(4, project.tracks.size)
        assertTrue(project.bpm in 40..240)
        assertEquals("Neon Horizon", project.name)
    }


    @Test fun drumStepsAndClipColorsRoundTripThroughProjectJson() {
        val original = StudioProject()
        val drumTrack = original.tracks.first { it.kind == "drum" }
        val sourceClip = drumTrack.clips.first()
        val edited = original.copy(tracks = original.tracks.map { track ->
            if (track.id == drumTrack.id) track.copy(clips = track.clips.map { clip ->
                if (clip.id == sourceClip.id) clip.copy(
                    drumSteps = listOf(true, false, true, false) + List(12) { false },
                    colorArgb = 0xFF12AB34L,
                    sourceOffsetTicks = 1920L
                ) else clip
            }) else track
        })
        val restored = StudioProject.fromJson(JSONObject(edited.toJson().toString()))
        val restoredClip = restored.tracks.first { it.id == drumTrack.id }.clips.first()
        assertEquals(listOf(true, false, true, false) + List(12) { false }, restoredClip.drumSteps)
        assertEquals(0xFF12AB34L, restoredClip.colorArgb)
        assertEquals(1920L, restoredClip.sourceOffsetTicks)
    }

    @Test fun legacyProjectWithoutDrumStepsAndClipColorStillLoads() {
        val project = StudioProject()
        val json = project.toJson()
        val tracks = json.getJSONArray("tracks")
        for (i in 0 until tracks.length()) {
            val clips = tracks.getJSONObject(i).getJSONArray("clips")
            for (j in 0 until clips.length()) {
                clips.getJSONObject(j).remove("drumSteps")
                clips.getJSONObject(j).remove("colorArgb")
                clips.getJSONObject(j).remove("sourceOffsetTicks")
            }
        }
        val restored = StudioProject.fromJson(json)
        assertEquals(project.tracks.size, restored.tracks.size)
        assertTrue(restored.tracks.flatMap { it.clips }.all {
            it.drumSteps.isEmpty() && it.colorArgb == null && it.sourceOffsetTicks == 0L
        })
    }

    @Test fun MIDIStarterNotesAreWithinValidRange() {
        val project = StudioProject()
        val notes = project.tracks.flatMap { it.clips }.flatMap { it.midiNotes }
        assertTrue(notes.isNotEmpty())
        notes.forEach {
            assertTrue(it.pitch in 0..127)
            assertTrue(it.velocity in 1..127)
            assertTrue(it.durationTicks > 0)
        }
    }

    @Test fun moveClipReplacesStaleDuplicateIdsAndKeepsExactlyOneClip() {
        val project = StudioProject()
        val source = project.tracks.first { it.kind == "midi" }
        val target = project.tracks.last { it.kind == "midi" }
        val clip = source.clips.first()
        val dirty = project.copy(tracks = project.tracks.map { track ->
            when (track.id) {
                source.id -> track
                target.id -> track.copy(clips = track.clips + clip.copy(startTick = 9999L))
                else -> track
            }
        })
        val moved = dirty.moveClipToTrack(clip.id, target.id, 4800L)
        assertNotNull(moved)
        assertEquals(1, moved!!.tracks.flatMap { it.clips }.count { it.id == clip.id })
        assertTrue(moved.tracks.first { it.id == target.id }.clips.any { it.id == clip.id && it.startTick == 4800L })
        assertTrue(moved.tracks.first { it.id == source.id }.clips.none { it.id == clip.id })
    }

    @Test fun moveClipRejectsIncompatibleTrackKindWithoutChangingProject() {
        val project = StudioProject()
        val source = project.tracks.first { it.kind == "drum" }
        val target = project.tracks.first { it.kind == "midi" }
        val clip = source.clips.first()
        assertEquals(null, project.moveClipToTrack(clip.id, target.id, 1200L))
        assertTrue(project.tracks.first { it.id == source.id }.clips.any { it.id == clip.id })
    }

    @Test fun projectLoaderRejectsExcessiveTrackCount() {
        val json = StudioProject().toJson()
        val tracks = json.getJSONArray("tracks")
        val source = tracks.getJSONObject(0)
        val many = org.json.JSONArray()
        repeat(StudioProject.MAX_TRACKS + 1) { many.put(source) }
        json.put("tracks", many)
        try {
            StudioProject.fromJson(json)
            fail("oversized projects must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("too many tracks"))
        }
    }

    @Test fun projectLoaderClampsInvalidTempoAndNoteValues() {
        val json = StudioProject().toJson().put("bpm", 900)
        val tracks = json.getJSONArray("tracks")
        val midi = (0 until tracks.length()).map { tracks.getJSONObject(it) }
            .first { it.getString("kind") == "midi" }
        val note = midi.getJSONArray("clips").getJSONObject(0).getJSONArray("midiNotes").getJSONObject(0)
        note.put("pitch", 999).put("velocity", -5).put("durationTicks", 0).put("startTick", -10)
        val restored = StudioProject.fromJson(json)
        assertEquals(240, restored.bpm)
        val restoredNote = restored.tracks.first { it.id == midi.getString("id") }.clips.first().midiNotes.first()
        assertEquals(127, restoredNote.pitch)
        assertEquals(1, restoredNote.velocity)
        assertEquals(1L, restoredNote.durationTicks)
        assertEquals(0L, restoredNote.startTick)
    }


    @Test fun projectLoaderRejectsUnknownTrackKindsAndDuplicateIds() {
        val unsupportedKind = StudioProject().toJson()
        unsupportedKind.getJSONArray("tracks").getJSONObject(0).put("kind", "plugin")
        try {
            StudioProject.fromJson(unsupportedKind)
            fail("unknown track kind must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Unsupported track kind"))
        }

        val duplicateTrack = StudioProject().toJson()
        val tracks = duplicateTrack.getJSONArray("tracks")
        tracks.getJSONObject(1).put("id", tracks.getJSONObject(0).getString("id"))
        try {
            StudioProject.fromJson(duplicateTrack)
            fail("duplicate track ids must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("duplicate track IDs"))
        }

        val duplicateClip = StudioProject().toJson()
        val clipTracks = duplicateClip.getJSONArray("tracks")
        val firstClipId = clipTracks.getJSONObject(0).getJSONArray("clips").getJSONObject(0).getString("id")
        clipTracks.getJSONObject(1).getJSONArray("clips").getJSONObject(0).put("id", firstClipId)
        try {
            StudioProject.fromJson(duplicateClip)
            fail("duplicate clip ids must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("duplicate clip IDs"))
        }
    }


    @Test fun parametricEqEightDefaultsExposeFrequencyAndQForEveryBand() {
        val parameters = defaultDeviceParameters("PARAM_EQ_8")
        assertEquals(8, (1..8).count { parameters.containsKey("band$it") })
        (1..8).forEach { band ->
            assertTrue((parameters["band${band}FreqNorm"] ?: -1f) in 0f..1f)
            assertTrue((parameters["band${band}Q"] ?: 0f) in 0.25f..8f)
        }
    }

    @Test fun parametricEqEightFrequencyAndQParametersRoundTripThroughProjectJson() {
        val project = StudioProject()
        val track = project.tracks.first { it.kind == "midi" }
        val parameters = defaultDeviceParameters("PARAM_EQ_8") + mapOf(
            "band4" to 4.5f,
            "band4FreqNorm" to 0.72f,
            "band4Q" to 2.4f
        )
        val device = TrackDevice(type = "PARAM_EQ_8", name = "Parametric EQ Eight", parameters = parameters)
        val edited = project.copy(tracks = project.tracks.map {
            if (it.id == track.id) it.copy(devices = it.devices + device) else it
        })
        val restored = StudioProject.fromJson(JSONObject(edited.toJson().toString()))
        val restoredDevice = restored.tracks.first { it.id == track.id }.devices.last()
        assertEquals(4.5f, restoredDevice.parameters["band4"] ?: 0f, 0.001f)
        assertEquals(0.72f, restoredDevice.parameters["band4FreqNorm"] ?: 0f, 0.001f)
        assertEquals(2.4f, restoredDevice.parameters["band4Q"] ?: 0f, 0.001f)
    }


    @Test fun absoluteAudioTrimCoordinatesRoundTripThroughProjectJson() {
        val original = StudioProject()
        val audioTrack = original.tracks.first { it.kind == "audio" }
        val clip = com.studiowave.app.data.WaveClip(
            name = "Trimmed audio",
            startTick = 0L,
            lengthTicks = 1_920L,
            sourceUri = "content://test/audio",
            sourceOffsetTicks = 960L,
            sourceOffsetMicros = 375_000L,
            sourceLengthMicros = 625_000L
        )
        val edited = original.copy(tracks = original.tracks.map { track ->
            if (track.id == audioTrack.id) track.copy(clips = listOf(clip)) else track
        })
        val restored = StudioProject.fromJson(edited.toJson())
        val restoredClip = restored.tracks.first { it.id == audioTrack.id }.clips.single()
        assertEquals(375_000L, restoredClip.sourceOffsetMicros)
        assertEquals(625_000L, restoredClip.sourceLengthMicros)
        assertEquals(960L, restoredClip.sourceOffsetTicks)
        assertEquals(1_920L, restoredClip.lengthTicks)
    }

    @Test fun legacyAudioTrimMigratesUsingSavedProjectTempo() {
        val original = StudioProject(bpm = 120, ppq = 960)
        val audioTrack = original.tracks.first { it.kind == "audio" }
        val legacyClip = com.studiowave.app.data.WaveClip(
            name = "Legacy audio",
            startTick = 0L,
            lengthTicks = 1_920L,
            sourceUri = "content://test/legacy-audio",
            sourceOffsetTicks = 960L
        )
        val legacyProject = original.copy(tracks = original.tracks.map { track ->
            if (track.id == audioTrack.id) track.copy(clips = listOf(legacyClip)) else track
        })
        val restored = StudioProject.fromJson(legacyProject.toJson())
        val migrated = restored.tracks.first { it.id == audioTrack.id }.clips.single()
        assertEquals(500_000L, migrated.sourceOffsetMicros)
        assertEquals(1_000_000L, migrated.sourceLengthMicros)
        val reopened = StudioProject.fromJson(restored.toJson())
        val reopenedClip = reopened.tracks.first { it.id == audioTrack.id }.clips.single()
        assertEquals(migrated.sourceOffsetMicros, reopenedClip.sourceOffsetMicros)
        assertEquals(migrated.sourceLengthMicros, reopenedClip.sourceLengthMicros)
    }

}