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

    @Test fun projectModelClampsTempoAndMIDIValues() {
        val project = StudioProject(bpm = 310)
        assertEquals(240, project.toJson().optInt("bpm"))
        val bass = project.tracks[1]
        val note = bass.clips.first().midiNotes.first()
        assertTrue(note.pitch in 0..127)
        assertTrue(note.velocity in 1..127)
    }
}