package com.studiowave.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineViewportTest {
    private val common = TimelineTimebase(120, 4, 4, 960)

    @Test fun preferredRefreshRateSelectsHighestSupportedHighRefreshMode() {
        assertEquals(120f, choosePreferredRefreshRate(listOf(60f, 90f, 120f, 144f))!!)
        assertEquals(90f, choosePreferredRefreshRate(listOf(60f, 90f, 144f))!!)
        assertEquals(null, choosePreferredRefreshRate(listOf(30f, 60f, 144f)))
        assertEquals(120f, choosePreferredRefreshRate(listOf(Float.NaN, Float.POSITIVE_INFINITY, 120f))!!)
    }

    @Test fun timebaseUsesPpqAndMeter() {
        assertEquals(960L, common.ticksPerBeat)
        assertEquals(3840L, common.ticksPerBar)
        val sevenEight = TimelineTimebase(96, 7, 8, 960)
        assertEquals(480L, sevenEight.ticksPerBeat)
        assertEquals(3360L, sevenEight.ticksPerBar)
        assertEquals(2880L, TimelineTimebase(100, 3, 4, 960).ticksPerBar)
    }

    @Test fun tickAndScreenCoordinatesRoundTripWithHeaderOffset() {
        val viewport = TimelineViewport(common, 148f, 7680.0, 720f, 104f)
        listOf(0L, 7680L, 9120L, 192000L).forEach { tick ->
            assertTrue(kotlin.math.abs(tick - viewport.screenXToTime(viewport.timeToScreenX(tick))) <= 1L)
            assertEquals(viewport.timeToScreenX(tick) - 104f, viewport.timeToViewportX(tick), 0.001f)
        }
    }

    @Test fun zoomKeepsFractionalAnchorStationary() {
        val viewport = TimelineViewport(common, 82f, 200000.125, 900f, 104f)
        val x = 104f + 420f
        val tick = viewport.effectiveScrollTimeTicks + (x - viewport.safeTrackHeaderWidthPx) / viewport.pixelsPerTick
        val zoomed = viewport.zoomKeepingAnchor(176f, x)
        val tickAfter = zoomed.effectiveScrollTimeTicks + (x - zoomed.safeTrackHeaderWidthPx) / zoomed.pixelsPerTick
        assertEquals(tick, tickAfter, 1e-5)
    }

    @Test fun snapDivisionsUseNamedMusicalFractions() {
        assertEquals("1/16", SnapDivision.QUARTER_BEAT.label)
        assertEquals(240L, SnapDivision.QUARTER_BEAT.ticks(common))
        assertEquals(480L, SnapDivision.HALF_BEAT.ticks(common))
        assertEquals(120L, SnapDivision.EIGHTH_BEAT.ticks(common))
        assertEquals(3840L, SnapDivision.BAR.ticks(common))
        assertEquals(320L, common.snap(350L, SnapDivision.EIGHTH_TRIPLET))
        assertEquals(347L, common.snap(347L, SnapDivision.OFF))
    }

    @Test fun snappingNeverReturnsNegativeTicksOrZeroSizedSteps() {
        SnapDivision.entries.filterNot { it == SnapDivision.OFF }.forEach {
            assertTrue(common.snap(-100L, it) >= 0L)
            assertTrue(it.ticks(common) > 0L)
        }
    }

    @Test fun panAndPlaceTickStayInside1800BarArrangement() {
        val end = maxArrangementTick(common)
        assertEquals(1800L * common.ticksPerBar, end)
        val initial = TimelineViewport(common, 74f, 0.0, 900f)
        assertEquals(0.0, initial.panByScreenPixels(Float.NaN).effectiveScrollTimeTicks, 0.0)
        val atEnd = initial.placeTickAtViewportX(end, 900f)
        val maxScroll = end.toDouble() - 900.0 / atEnd.pixelsPerTick
        assertTrue(atEnd.effectiveScrollTimeTicks <= maxScroll + 1e-6)
        assertEquals(end, atEnd.screenXToTime(Float.POSITIVE_INFINITY))
        assertEquals(1799L, atEnd.lastVisibleBar(10))
        assertTrue(atEnd.panByScreenPixels(-100000f).effectiveScrollTimeTicks <= end.toDouble())
    }

    @Test fun zoomAndPanClampAtStartAndEnd() {
        val start = TimelineViewport(common, 74f, 0.0, 800f, 104f)
        assertEquals(0.0, start.zoomKeepingAnchor(20f, 524f).effectiveScrollTimeTicks, 0.0)
        val end = TimelineViewport(common, 4800f, maxArrangementTick(common).toDouble(), 800f, 104f)
        assertTrue(end.effectiveScrollTimeTicks <= maxArrangementTick(common).toDouble())
        assertTrue(end.panByScreenPixels(-50000f).effectiveScrollTimeTicks <= maxArrangementTick(common).toDouble())
    }

    @Test fun pixelsAndTicksShareScaleAtEveryZoom() {
        listOf(20f, 37f, 74f, 180f, 560f, 1200f, 4800f).forEach { scale ->
            val viewport = TimelineViewport(common, scale, 50000.0, 1200f, 104f)
            val start = 9L * common.ticksPerBar + 480L
            val length = 3L * common.ticksPerBar + 240L
            val end = start + length
            val width = viewport.tickDeltaToPixels(length)
            assertEquals(width, viewport.timeToViewportX(end) - viewport.timeToViewportX(start), 0.01f)
            assertTrue(kotlin.math.abs(viewport.viewportXToTime(viewport.timeToViewportX(end)) - end) <= 1L)
            assertTrue(kotlin.math.abs(viewport.pixelsToTickDelta(width) - length) <= 1L)
        }
    }

    @Test fun maximumArrangementExtentDoesNotOverflowForUnusualTimebase() {
        val unusual = TimelineTimebase(120, 32, 1, 3840)
        val end = maxArrangementTick(unusual)
        assertTrue(end > 0L)
        assertEquals(0L, end % unusual.ticksPerBar)
    }

    @Test fun subdivisionsGetFinerAtHigherZoom() {
        val far = TimelineViewport(common, 20f, 0.0, 800f)
            .gridSubdivisionTicks(20.0 * 4.0 / common.ticksPerBar.toDouble())
        val close = TimelineViewport(common, 4800f, 0.0, 800f)
            .gridSubdivisionTicks(4800.0 * 4.0 / common.ticksPerBar.toDouble())
        assertTrue(close <= far)
        assertTrue(far >= 0L && close >= 0L)
    }
    
    @Test fun arrangementPaneCoordinatesDoNotIncludeTrackHeader() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val viewport = arrangementPaneViewport(timebase, 148f, 3840.5, 720f)
        val tick = 15360L
        assertEquals(0f, viewport.safeTrackHeaderWidthPx, 0f)
        assertEquals(
            ((tick.toDouble() - 3840.5) * viewport.pixelsPerTick).toFloat(),
            viewport.timeToViewportX(tick),
            0.001f
        )
        assertEquals(tick, viewport.viewportXToTime(viewport.timeToViewportX(tick)).toLong())
    }

    @Test fun arrangementPaneRepeatedZoomRestoresFractionalAnchor() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val initial = arrangementPaneViewport(timebase, 74f, 155555.25, 900f)
        val x = 421.75f
        val anchorTick = initial.effectiveScrollTimeTicks + x / initial.pixelsPerTick
        val result = initial.zoomKeepingAnchor(296f, x).zoomKeepingAnchor(74f, x)
        val resultingTick = result.effectiveScrollTimeTicks + x / result.pixelsPerTick
        assertEquals(anchorTick, resultingTick, 1e-6)
    }

    @Test fun zoomAfterHorizontalScrollKeepsFractionalAnchorAndRoundTripsTicks() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val initial = arrangementPaneViewport(timebase, 96f, 0.0, 920f)
            .panByScreenPixels(-173.25f)
        val anchorX = 417.5f
        val anchorTick = initial.effectiveScrollTimeTicks + anchorX / initial.pixelsPerTick
        val zoomed = initial.zoomKeepingAnchor(312f, anchorX)
        val tickAfterZoom = zoomed.effectiveScrollTimeTicks + anchorX / zoomed.pixelsPerTick
        assertEquals(anchorTick, tickAfterZoom, 1e-5)
        listOf(0L, 3840L, 7680L, 145920L, maxArrangementTick(timebase)).forEach { tick ->
            val x = zoomed.timeToViewportX(tick)
            assertTrue(kotlin.math.abs(zoomed.viewportXToTime(x) - tick) <= 1L)
        }
    }

    @Test fun playbackFollowKeepsPlayheadVisibleAtBothArrangementEdges() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val initial = arrangementPaneViewport(timebase, 120f, 0.0, 900f)
        val startFollow = initial.followPlayhead(0L)
        assertTrue(startFollow.timeToViewportX(0L) in -0.01f..900.01f)
        val endTick = maxArrangementTick(timebase) - timebase.ticksPerBeat
        val endFollow = initial.followPlayhead(endTick)
        val endX = endFollow.timeToViewportX(endTick)
        assertTrue("playhead should remain visible near arrangement end: x=$endX", endX >= -0.01f && endX <= 900.01f)
        assertTrue(endFollow.effectiveScrollTimeTicks <= maxArrangementTick(timebase).toDouble())
    }

    @Test fun clipDurationWidthRemainsInvariantAcrossScrollThenZoom() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val base = arrangementPaneViewport(timebase, 74f, 32000.25, 860f)
        val moved = base.panByScreenPixels(-267f)
        val zoomed = moved.zoomKeepingAnchor(444f, 302.25f)
        val start = 43L * timebase.ticksPerBar + 120L
        val duration = 2L * timebase.ticksPerBar + 720L
        val renderedWidth = zoomed.tickDeltaToPixels(duration)
        val mappedWidth = zoomed.timeToViewportX(start + duration) - zoomed.timeToViewportX(start)
        assertEquals(renderedWidth, mappedWidth, 0.01f)
        assertTrue(kotlin.math.abs(zoomed.pixelsToTickDelta(renderedWidth) - duration) <= 1L)
    }

    @Test fun malformedViewportInputsAreNormalizedBeforeMapping() {
        val viewport = TimelineViewport(
            timebase = common,
            pixelsPerBar = Float.NaN,
            scrollTimeTicks = Double.NaN,
            viewportWidthPx = Float.NaN,
            trackHeaderWidthPx = Float.NaN
        )
        assertEquals(TimelineViewport.MIN_PIXELS_PER_BAR, viewport.effectivePixelsPerBar, 0f)
        assertEquals(0.0, viewport.effectiveScrollTimeTicks, 0.0)
        assertEquals(0f, viewport.effectiveViewportWidthPx, 0f)
        assertEquals(0f, viewport.safeTrackHeaderWidthPx, 0f)
        assertEquals(0L, viewport.pixelsToTickDelta(Float.NaN))
        assertEquals(0L, viewport.pixelsToTickDelta(Float.POSITIVE_INFINITY))
        assertEquals(0.0, viewport.panByScreenPixels(Float.NaN).effectiveScrollTimeTicks, 0.0)
    }

    @Test fun viewportHitTestingClampsOutsideArrangementBounds() {
        val viewport = arrangementPaneViewport(common, 148f, 0.0, 900f)
        assertEquals(0L, viewport.viewportXToTime(-500f))
        assertEquals(maxArrangementTick(common), viewport.viewportXToTime(Float.MAX_VALUE))
        assertEquals(maxArrangementTick(common), viewport.screenXToTime(Float.POSITIVE_INFINITY))
    }


    @Test fun audioTrimRestoresPersistedRangeAndClampsToSource() {
        val restored = audioTrimFractionsFromTicks(
            sourceOffsetTicks = 250L,
            clipLengthTicks = 400L,
            sourceDurationTicks = 1000L
        )
        assertEquals(0.25f, restored.first, 0.0001f)
        assertEquals(0.65f, restored.second, 0.0001f)

        val clipped = audioTrimFractionsFromTicks(
            sourceOffsetTicks = Long.MAX_VALUE,
            clipLengthTicks = Long.MAX_VALUE,
            sourceDurationTicks = 1000L
        )
        assertTrue(clipped.first < clipped.second)
        assertEquals(1f, clipped.second, 0.0001f)
    }

    @Test fun audioTrimConversionAlwaysProducesNonEmptyBoundedRange() {
        val full = audioTrimRangeFromFractions(0f, 1f, 1000L)!!
        assertEquals(0L, full.startTicks)
        assertEquals(1000L, full.endTicksExclusive)

        val nearEnd = audioTrimRangeFromFractions(0.9999f, Float.NaN, 1000L)!!
        assertEquals(999L, nearEnd.startTicks)
        assertTrue(nearEnd.endTicksExclusive <= 1000L)
        assertTrue(nearEnd.endTicksExclusive > nearEnd.startTicks)

        val restoredNearEnd = audioTrimFractionsFromTicks(999L, 1L, 1000L)
        assertEquals(0.999f, restoredNearEnd.first, 0.0001f)
        assertEquals(1f, restoredNearEnd.second, 0.0001f)

        assertEquals(null, audioTrimRangeFromFractions(0f, 1f, 1L))
        assertEquals(null, audioTrimRangeFromFractions(Float.NaN, Float.POSITIVE_INFINITY, 0L))
    }

}
