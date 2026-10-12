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
        assertEquals("Neon Horizon", project.name)
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