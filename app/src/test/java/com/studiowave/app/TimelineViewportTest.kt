package com.studiowave.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineViewportTest {
    @Test fun timebaseUsesProjectPpqAndMeter() {
        val common = TimelineTimebase(bpm = 120, numerator = 4, denominator = 4, ppq = 960)
        assertEquals(960L, common.ticksPerBeat)
        assertEquals(3840L, common.ticksPerBar)

        val sevenEight = TimelineTimebase(bpm = 96, numerator = 7, denominator = 8, ppq = 960)
        assertEquals(480L, sevenEight.ticksPerBeat)
        assertEquals(3360L, sevenEight.ticksPerBar)

        val threeFour = TimelineTimebase(bpm = 100, numerator = 3, denominator = 4, ppq = 960)
        assertEquals(2880L, threeFour.ticksPerBar)
    }

    @Test fun tickAndScreenCoordinatesRoundTripWithHeaderOffset() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val viewport = TimelineViewport(timebase, pixelsPerBar = 148f, scrollTimeTicks = 7680.0,
            viewportWidthPx = 720f, trackHeaderWidthPx = 104f)
        listOf(0L, 7680L, 9120L, 192000L).forEach { tick ->
            assertTrue(kotlin.math.abs(tick - viewport.screenXToTime(viewport.timeToScreenX(tick))) <= 1L)
            assertEquals(viewport.timeToScreenX(tick) - 104f, viewport.timeToViewportX(tick), 0.001f)
        }
    }

    @Test fun pinchZoomKeepsMusicalAnchorStationary() {
        val timebase = TimelineTimebase(123, 7, 8, 960)
        val initial = TimelineViewport(timebase, 82f, 4120.0, 690f, 102f)
        val anchorScreenX = 102f + 318f
        val anchorTick = initial.screenXToTime(anchorScreenX)
        val zoomed = initial.zoomKeepingAnchor(176f, anchorScreenX)
        assertTrue(kotlin.math.abs(anchorTick - zoomed.screenXToTime(anchorScreenX)) <= 1L)
    }

    @Test fun snapIsAppliedInMusicalTicksAndOffPreservesTicks() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        assertEquals(3840L, timebase.snap(3700L, SnapDivision.BAR))
        assertEquals(1920L, timebase.snap(2050L, SnapDivision.HALF_BEAT))
        assertEquals(320L, timebase.snap(350L, SnapDivision.EIGHTH_TRIPLET))
        assertTrue(timebase.snap(347L, SnapDivision.OFF) == 347L)

        val viewport = TimelineViewport(timebase, 74f, 0.0, 800f)
        assertTrue(viewport.snapTick(347L, SnapDivision.SIXTEENTH_BEAT, enabled = false) == 347L)
        assertEquals(480L, viewport.snapTick(470L, SnapDivision.SIXTEENTH_BEAT, enabled = true))
    }

    @Test fun viewportPanMovesTheSameTickByExpectedPixels() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val viewport = TimelineViewport(timebase, 192f, 3840.0, 800f)
        val before = viewport.timeToScreenX(7680L)
        val panned = viewport.panByScreenPixels(-48f)
        assertEquals(before + 48f, panned.timeToScreenX(7680L), 0.01f)
        assertTrue(panned.scrollTimeTicks > viewport.scrollTimeTicks)
    }

    @Test fun visibleBarRangeIncludesViewportAndMargins() {
        val viewport = TimelineViewport(TimelineTimebase(120, 4, 4, 960),
            pixelsPerBar = 100f, scrollTimeTicks = 3840.0, viewportWidthPx = 250f)
        assertEquals(0L, viewport.firstVisibleBar())
        assertEquals(5L, viewport.lastVisibleBar())
    }
}
