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

    @Test fun labeledSixteenthNoteSnapUsesQuarterBeatTicks() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        assertEquals("1/16", SnapDivision.QUARTER_BEAT.label)
        assertEquals(240L, SnapDivision.QUARTER_BEAT.ticks(timebase))
        assertEquals(480L, SnapDivision.HALF_BEAT.ticks(timebase))
        assertEquals(120L, SnapDivision.EIGHTH_BEAT.ticks(timebase))
        assertEquals(3840L, SnapDivision.BAR.ticks(timebase))
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

    @Test fun snapDoesNotWrapNearLongMaxValue() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val snapped = timebase.snap(Long.MAX_VALUE, SnapDivision.BAR)
        assertTrue(snapped >= 0L)
        assertTrue(snapped <= Long.MAX_VALUE)
        assertEquals(0L, snapped % timebase.ticksPerBar)
    }

    @Test fun viewportClampsNanScaleAndNonFiniteScrollCoordinates() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val viewport = TimelineViewport(timebase, Float.NaN, 0.0, 640f)
        assertEquals(TimelineViewport.MIN_PIXELS_PER_BAR, viewport.pixelsPerTick.toFloat() * timebase.ticksPerBar, 0.01f)
        assertTrue(viewport.timeToScreenX(Long.MAX_VALUE).isFinite())
    }

    @Test fun snappingNeverProducesNegativeTicksOrZeroSizedSteps() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        listOf(SnapDivision.BAR, SnapDivision.BEAT, SnapDivision.SIXTEENTH_BEAT,
            SnapDivision.EIGHTH_TRIPLET, SnapDivision.QUARTER_TRIPLET).forEach { division ->
            assertTrue(timebase.snap(-100L, division) >= 0L)
            assertTrue(division.ticks(timebase) > 0L)
        }
    }

    @Test fun clipEndRemainsAlignedWithRulerAcrossZoomLevels() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val clipStart = timebase.ticksPerBar * 4L + 720L
        val clipLength = timebase.ticksPerBar * 5L + 960L
        val clipEnd = clipStart + clipLength

        listOf(20f, 41f, 74f, 180f, 600f, 1199f, 1800f).forEach { requestedScale ->
            val viewport = TimelineViewport(
                timebase, requestedScale, 2400.0, 1200f, 104f
            )
            val drawnWidth = viewport.tickDeltaToPixels(clipLength)
            val mappedWidth = viewport.timeToViewportX(clipEnd) - viewport.timeToViewportX(clipStart)
            assertEquals("scale=$requestedScale", mappedWidth, drawnWidth, 0.01f)
            assertEquals("scale=$requestedScale", viewport.effectivePixelsPerBar,
                (viewport.pixelsPerTick * timebase.ticksPerBar).toFloat(), 0.01f)
            assertTrue("scale=$requestedScale", kotlin.math.abs(
                viewport.viewportXToTime(viewport.timeToViewportX(clipEnd)) - clipEnd
            ) <= 1L)
        }
    }

    @Test fun freeResizeDeltaDoesNotDependOnZoomOrScroll() {
        val timebase = TimelineTimebase(126, 4, 4, 960)
        val startTick = 8123L
        val lengthTicks = 19321L
        listOf(36f, 74f, 180f, 500f, 1100f).forEach { scale ->
            val viewport = TimelineViewport(timebase, scale, 5000.0, 900f)
            val endX = viewport.timeToViewportX(startTick + lengthTicks)
            val startX = viewport.timeToViewportX(startTick)
            val lengthFromPixels = viewport.pixelsToTickDelta(endX - startX)
            assertTrue(kotlin.math.abs(lengthFromPixels - lengthTicks) <= 1L)
        }
    }

    @Test fun clipGeometryUsesExactMusicalExtentAtAllZoomLevels() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val clipStart = timebase.ticksPerBar * 8L + timebase.ticksPerBeat / 2L
        val clipLength = timebase.ticksPerBar * 3L + timebase.ticksPerBeat / 4L
        listOf(20f, 37f, 74f, 180f, 560f, 1200f).forEach { scale ->
            val viewport = TimelineViewport(timebase, scale, 0.0, 1200f, 104f)
            val left = viewport.timeToViewportX(clipStart)
            val right = viewport.timeToViewportX(clipStart + clipLength)
            val width = viewport.tickDeltaToPixels(clipLength)
            assertEquals("scale=$scale", right - left, width, 0.01f)
            assertTrue("clip start at scale=$scale",
                kotlin.math.abs(viewport.viewportXToTime(left) - clipStart) <= 1L)
            assertTrue("clip end at scale=$scale",
                kotlin.math.abs(viewport.viewportXToTime(right) - (clipStart + clipLength)) <= 1L)
            assertTrue("resize delta at scale=$scale",
                kotlin.math.abs(viewport.pixelsToTickDelta(width) - clipLength) <= 1L)
        }
    }

}
