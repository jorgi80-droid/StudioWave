package com.studiowave.app

import com.studiowave.app.data.StudioProject
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
        val restored = StudioProject.fromJson(source.toJson())
        assertEquals(source.name, restored.name)
        assertEquals(source.bpm, restored.bpm)
        assertEquals(source.tracks.size, restored.tracks.size)
        assertEquals(source.tracks[1].clips.first().midiNotes.size, restored.tracks[1].clips.first().midiNotes.size)
    }
}