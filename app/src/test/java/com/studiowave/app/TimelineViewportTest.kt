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
        assertEquals(before - 48f, panned.timeToScreenX(7680L), 0.01f)
        assertTrue(panned.scrollTimeTicks > viewport.scrollTimeTicks)
    }

    @Test fun visibleBarRangeIncludesViewportAndMargins() {
        val viewport = TimelineViewport(TimelineTimebase(120, 4, 4, 960),
            pixelsPerBar = 100f, scrollTimeTicks = 3840.0, viewportWidthPx = 250f)
        assertEquals(0L, viewport.firstVisibleBar())
        assertEquals(5L, viewport.lastVisibleBar())
    }
    @Test fun zoomAnchorsRemainStableAtMinimumAndMaximumScale() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        // Keep enough content to the left that every tested zoom level can preserve
        // the anchor without requiring a negative scroll position.
        val initial = TimelineViewport(timebase, 74f, 200_000.0, 900f, 104f)
        val anchorX = 104f + 420f
        val tickUnderAnchor = initial.screenXToTime(anchorX)
        listOf(20f, 41f, 74f, 220f, 560f, 1200f, 2400f).forEach { requestedScale ->
            val zoomed = initial.zoomKeepingAnchor(requestedScale, anchorX)
            val effectiveScale = requestedScale.coerceIn(
                TimelineViewport.MIN_PIXELS_PER_BAR, TimelineViewport.MAX_PIXELS_PER_BAR
            )
            assertEquals(effectiveScale, zoomed.pixelsPerBar, 0.001f)
            assertTrue(kotlin.math.abs(tickUnderAnchor - zoomed.screenXToTime(anchorX)) <= 1L)
        }
    }

    @Test fun zoomAtTimelineStartClampsScrollToZero() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val viewport = TimelineViewport(timebase, 74f, 0.0, 800f, 104f)
        val zoomedOut = viewport.zoomKeepingAnchor(20f, 104f + 420f)
        assertEquals(0.0, zoomedOut.scrollTimeTicks, 0.0)
        assertEquals(20f, zoomedOut.pixelsPerBar, 0.001f)
    }

    @Test fun viewportClampsInvalidScaleAndScrollWithoutOverflow() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val viewport = TimelineViewport(timebase, 74f, 3840.0, 800f)
        assertEquals(TimelineViewport.MIN_PIXELS_PER_BAR,
            viewport.zoomKeepingAnchor(0f, 200f).pixelsPerBar)
        assertEquals(TimelineViewport.MAX_PIXELS_PER_BAR,
            viewport.zoomKeepingAnchor(Float.POSITIVE_INFINITY, 200f).pixelsPerBar)
        assertTrue(viewport.copy(scrollTimeTicks = Long.MAX_VALUE.toDouble())
            .panByScreenPixels(-500f).scrollTimeTicks <= Long.MAX_VALUE.toDouble())
    }

    @Test fun snappingNeverProducesNegativeTicksOrZeroSizedSteps() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        listOf(SnapDivision.BAR, SnapDivision.BEAT, SnapDivision.SIXTEENTH_BEAT,
            SnapDivision.EIGHTH_TRIPLET, SnapDivision.QUARTER_TRIPLET).forEach { division ->
            assertTrue(timebase.snap(-100L, division) >= 0L)
            assertTrue(division.ticks(timebase) > 0L)
        }
    }

}
