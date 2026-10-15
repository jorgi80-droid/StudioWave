package com.studiowave.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrangementTimingTrimTest {
    @Test fun playbackCursorStaysInsideTrimmedWindow() {
        assertEquals(120L, ArrangementTiming.sourceFrameWithinTrim(
            rawSourceFrame = 120L, sourceOffsetFrame = 100L,
            requestedWindowFrames = 40L, assetFrames = 1000L))
        assertEquals(100L, ArrangementTiming.sourceFrameWithinTrim(
            rawSourceFrame = 140L, sourceOffsetFrame = 100L,
            requestedWindowFrames = 40L, assetFrames = 1000L))
        assertEquals(139L, ArrangementTiming.sourceFrameWithinTrim(
            rawSourceFrame = 179L, sourceOffsetFrame = 100L,
            requestedWindowFrames = 40L, assetFrames = 1000L))
    }

    @Test fun trimmedWindowIsClampedToAvailableSourceFrames() {
        assertEquals(98L, ArrangementTiming.sourceFrameWithinTrim(
            rawSourceFrame = 108L, sourceOffsetFrame = 98L,
            requestedWindowFrames = 20L, assetFrames = 100L))
        assertEquals(99L, ArrangementTiming.sourceFrameWithinTrim(
            rawSourceFrame = 109L, sourceOffsetFrame = 98L,
            requestedWindowFrames = 20L, assetFrames = 100L))
    }

    @Test fun invalidAssetsReturnNull() {
        assertNull(ArrangementTiming.sourceFrameWithinTrim(
            rawSourceFrame = 0L, sourceOffsetFrame = 0L,
            requestedWindowFrames = 10L, assetFrames = 0L))
    }
}
