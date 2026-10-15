package com.studiowave.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveControlSurfaceTest {
    @Test fun decibelReadoutHasFiniteAndMutedLabels() {
        assertEquals("-inf dB", liveDbLabel(0f))
        assertEquals("0 dB", liveDbLabel(1f))
        assertEquals("-6 dB", liveDbLabel(0.5f))
    }

    @Test fun floatReadoutUsesRequestedPrecision() {
        assertEquals("1.2", liveFormatFloat(1.234f, 1))
        assertEquals("1.23", liveFormatFloat(1.234f, 2))
    }
}
