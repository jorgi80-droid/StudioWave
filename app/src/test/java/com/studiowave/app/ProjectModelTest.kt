package com.studiowave.app

import com.studiowave.app.data.StudioProject
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectModelTest {
    @Test fun starterProjectHasFourTracksAndUsableTempo() {
        val project = StudioProject()
        assertEquals(4, project.tracks.size)
        assertTrue(project.bpm in 40..240)
    }

    @Test fun projectJsonRoundTripsTrackAndMidiData() {
        val source = StudioProject()
        // Android's org.json.JSONObject is not available as a platform implementation in local unit tests.
        // Exercise the JSON parser via its standard textual constructor, which is supported on Android runtime.
        val restored = StudioProject.fromJson(JSONObject(source.toJson().toString()))
        assertEquals(source.name, restored.name)
        assertEquals(source.bpm, restored.bpm)
        assertEquals(source.tracks.size, restored.tracks.size)
        assertEquals(source.tracks[1].name, restored.tracks[1].name)
        assertEquals(source.tracks[1].clips.first().midiNotes.size, restored.tracks[1].clips.first().midiNotes.size)
    }
}