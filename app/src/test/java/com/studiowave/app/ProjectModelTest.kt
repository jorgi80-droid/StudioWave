package com.studiowave.app

import com.studiowave.app.data.StudioProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
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
                    colorArgb = 0xFF12AB34L
                ) else clip
            }) else track
        })
        val restored = StudioProject.fromJson(JSONObject(edited.toJson().toString()))
        val restoredClip = restored.tracks.first { it.id == drumTrack.id }.clips.first()
        assertEquals(listOf(true, false, true, false) + List(12) { false }, restoredClip.drumSteps)
        assertEquals(0xFF12AB34L, restoredClip.colorArgb)
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
            }
        }
        val restored = StudioProject.fromJson(json)
        assertEquals(project.tracks.size, restored.tracks.size)
        assertTrue(restored.tracks.flatMap { it.clips }.all { it.drumSteps.isEmpty() && it.colorArgb == null })
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
}