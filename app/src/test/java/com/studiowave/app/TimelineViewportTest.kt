package com.studiowave.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineViewportTest {
    @Test fun pinchFrameReturnsToStartScaleAndScrollAfterRelease() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val initialZoom = 1f
        val startScroll = 123_456.375
        val anchorX = 417.25f
        val baseViewport = TimelineViewport(timebase, 74f * initialZoom, startScroll, 900f)
        val anchor = startScroll + anchorX / baseViewport.pixelsPerTick
        val spans = listOf(180f, 220f, 255f, 230f, 190f, 180f)
        val frames = spans.map { span ->
            calculatePinchZoomFrame(timebase, initialZoom, startScroll, 180f, span, anchor, anchorX, 74f)
        }
        assertTrue(frames.first().zoom > initialZoom)
        assertTrue(frames[2].zoom > frames[1].zoom)
        assertEquals(initialZoom, frames.last().zoom, 0.001f)
        val restoredViewport = TimelineViewport(
            timebase, 74f * frames.last().zoom, frames.last().scrollTicks, 900f
        )
        assertEquals(anchor, frames.last().scrollTicks + anchorX / restoredViewport.pixelsPerTick, 1e-6)
        frames.forEach { assertTrue(it.scrollTicks >= 0.0) }
    }

    @Test fun pinchFramesDoNotAccumulatePreviousFrameRounding() {
        val timebase = TimelineTimebase(126, 7, 8, 960)
        val start = TimelineViewport(timebase, 111f, 50_000.125, 900f)
        val anchorX = 320f
        val anchorTick = start.scrollTimeTicks + anchorX / start.pixelsPerTick
        val shrunk = calculatePinchZoomFrame(timebase, 1.5f, start.scrollTimeTicks,
            200f, 160f, anchorTick, anchorX, 74f)
        val restored = calculatePinchZoomFrame(timebase, 1.5f, start.scrollTimeTicks,
            200f, 200f, anchorTick, anchorX, 74f)
        assertTrue(shrunk.zoom < 1.5f)
        assertEquals(1.5f, restored.zoom, 0.001f)
        assertEquals(start.scrollTimeTicks, restored.scrollTicks, 1e-6)
    }

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

    @Test fun gridSubdivisionNeverReturnsZeroWhenAVisibleBeatNeedsSubdivision() {
        // Small PPQ plus a large denominator can produce fewer than 16 ticks per beat.
        // A zero subdivision would make the Compose Canvas grid loop never advance.
        val unusual = TimelineTimebase(120, 1, 32, 24)
        val viewport = TimelineViewport(unusual, 1200f, 0.0, 800f)
        assertEquals(3L, unusual.ticksPerBeat)
        assertEquals(1L, viewport.gridSubdivisionTicks(1200.0))
        assertEquals(0L, viewport.gridSubdivisionTicks(15.9))
    }

    @Test fun gridSubdivisionScalesWithZoomForNormalMeter() {
        val viewport = TimelineViewport(TimelineTimebase(120, 4, 4, 960), 100f, 0.0, 800f)
        assertEquals(60L, viewport.gridSubdivisionTicks(100.0))
        assertEquals(120L, viewport.gridSubdivisionTicks(60.0))
        assertEquals(240L, viewport.gridSubdivisionTicks(35.0))
        assertEquals(480L, viewport.gridSubdivisionTicks(20.0))
        assertEquals(0L, viewport.gridSubdivisionTicks(10.0))
    }


    @Test fun viewportXAndScreenXConversionsAreExactInTheirOwnCoordinateSpaces() {
        val viewport = TimelineViewport(TimelineTimebase(120, 4, 4, 960), 148f, 7680.0, 900f, 104f)
        listOf(0L, 7680L, 9120L, 50000L).forEach { tick ->
            val paneX = viewport.timeToViewportX(tick)
            assertTrue(kotlin.math.abs(tick - viewport.viewportXToTime(paneX)) <= 1L)
            assertTrue(kotlin.math.abs(tick - viewport.screenXToTime(viewport.timeToScreenX(tick))) <= 1L)
            assertEquals(paneX, viewport.timeToScreenX(tick) - viewport.trackHeaderWidthPx, 0.001f)
        }
    }

    @Test fun clipWidthUsesOneScaleAndDoesNotChangeWithViewportScroll() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val start = 12_345L
        val length = 9_876L
        listOf(20f, 74f, 600f, 1200f).forEach { scale ->
            val a = TimelineViewport(timebase, scale, 0.0, 900f, 104f)
            val b = a.copy(scrollTimeTicks = 27_000.0)
            assertEquals(a.tickDeltaToPixels(length), b.tickDeltaToPixels(length), 0.001f)
            assertEquals(a.tickDeltaToPixels(length),
                b.timeToViewportX(start + length) - b.timeToViewportX(start), 0.02f)
        }
    }

    @Test fun nonFinitePixelDeltasAreIgnored() {
        val viewport = TimelineViewport(TimelineTimebase(120, 4, 4, 960), 20f, 0.0, 500f)
        assertEquals(0L, viewport.pixelsToTickDelta(Float.NaN))
        assertEquals(0L, viewport.pixelsToTickDelta(Float.POSITIVE_INFINITY))
        assertEquals(0L, viewport.pixelsToTickDelta(Float.NEGATIVE_INFINITY))
    }


    @Test fun placeTickAtViewportXKeepsAnchorAcrossScrollAndZoom() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        listOf(24f, 74f, 240f, 1190f).forEach { scale ->
            val initial = TimelineViewport(timebase, scale, 7680.0, 900f)
            val anchorX = 377f
            val anchorTick = initial.viewportXToTime(anchorX)
            val placed = initial.placeTickAtViewportX(anchorTick, anchorX)
            assertTrue(kotlin.math.abs(anchorTick - placed.viewportXToTime(anchorX)) <= 1L)
            assertEquals(scale, placed.effectivePixelsPerBar, 0.001f)
        }
    }

    @Test fun placeTickAtViewportXIgnoresNonFiniteAnchor() {
        val viewport = TimelineViewport(TimelineTimebase(120, 4, 4, 960), 74f, 1200.0, 900f)
        assertTrue(viewport == viewport.placeTickAtViewportX(3840L, Float.NaN))
        assertTrue(viewport == viewport.placeTickAtViewportX(3840L, Float.POSITIVE_INFINITY))
    }


    @Test fun longClipGeometryRemainsLongerThanViewportAtHighZoom() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val viewportWidthPx = 720f
        val clipStart = 0L
        val clipLength = timebase.ticksPerBar * 12L
        listOf(1f, 2f, 4f).forEach { zoom ->
            val viewport = TimelineViewport(timebase, 180f * zoom, 0.0, viewportWidthPx)
            val clipWidth = viewport.tickDeltaToPixels(clipLength)
            val mappedEnd = viewport.timeToViewportX(clipStart + clipLength)
            val mappedStart = viewport.timeToViewportX(clipStart)
            assertTrue("zoom=$zoom clip should extend beyond the visible lane", clipWidth > viewportWidthPx)
            assertEquals("zoom=$zoom exact clip extent", clipWidth, mappedEnd - mappedStart, 0.05f)
        }
    }


    @Test fun extendedZoomRangeKeepsClipGeometryAndResizeDeltasConsistent() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        val start = 8192L
        val length = timebase.ticksPerBar * 10L
        listOf(1200f, 1800f, 2400f, 3600f).forEach { scale ->
            val viewport = TimelineViewport(timebase, scale, 4000.0, 720f, 104f)
            val left = viewport.timeToViewportX(start)
            val right = viewport.timeToViewportX(start + length)
            val width = viewport.tickDeltaToPixels(length)
            assertEquals("scale=$scale", width, right - left, 0.05f)
            assertTrue("scale=$scale", kotlin.math.abs(viewport.pixelsToTickDelta(width) - length) <= 1L)
            assertEquals(scale, viewport.effectivePixelsPerBar, 0.01f)
        }
    }

    @Test fun repeatedPinchZoomKeepsFractionalAnchorStableWithoutTickRoundingDrift() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        var viewport = TimelineViewport(timebase, 74f, 123_456.375, 900f, 104f)
        val anchorX = 417.25f
        val originalAnchorTick = viewport.scrollTimeTicks + anchorX.toDouble() / viewport.pixelsPerTick
        val scales = listOf(80f, 96f, 72f, 120f, 60f, 148f, 74f, 200f, 74f)
        scales.forEach { scale ->
            viewport = viewport.zoomKeepingAnchor(scale, 104f + anchorX)
        }
        val finalAnchorTick = viewport.scrollTimeTicks + anchorX.toDouble() / viewport.pixelsPerTick
        assertEquals(originalAnchorTick, finalAnchorTick, 1e-7)
    }

    @Test fun repeatedAnchoredZoomNeverProducesNegativeScrollAtSongStart() {
        val timebase = TimelineTimebase(120, 4, 4, 960)
        var viewport = TimelineViewport(timebase, 74f, 0.0, 900f, 104f)
        repeat(100) { index ->
            val scale = if (index % 2 == 0) 150f else 38f
            viewport = viewport.zoomKeepingAnchor(scale, 104f + 450f)
            assertTrue(viewport.scrollTimeTicks >= 0.0)
        }
    }

}
