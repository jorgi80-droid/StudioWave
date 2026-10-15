package com.studiowave.app

import kotlin.math.floor
import kotlin.math.roundToLong

/** Project musical timebase shared by ruler, grid, clip geometry and editing gestures. */
data class TimelineTimebase(
    val bpm: Int,
    val numerator: Int,
    val denominator: Int,
    val ppq: Int
) {
    val safePpq: Int = ppq.coerceIn(24, 3840)
    val safeNumerator: Int = numerator.coerceIn(1, 32)
    val safeDenominator: Int = denominator.coerceIn(1, 32)
    val ticksPerBeat: Long = (safePpq.toLong() * 4L / safeDenominator).coerceAtLeast(1L)
    val ticksPerBar: Long = (ticksPerBeat * safeNumerator).coerceAtLeast(1L)
    val quarterNoteTicks: Long = safePpq.toLong()

    fun snap(tick: Long, division: SnapDivision): Long {
        val safeTick = tick.coerceAtLeast(0L)
        val step = division.ticks(this)
        if (step <= 0L) return safeTick
        val quotient = safeTick / step
        val remainder = safeTick % step
        val roundUp = remainder >= (step / 2L + step % 2L)
        val rounded = if (roundUp && quotient < Long.MAX_VALUE / step) quotient + 1L else quotient
        return if (rounded > Long.MAX_VALUE / step) Long.MAX_VALUE - Long.MAX_VALUE % step
        else (rounded * step).coerceAtLeast(0L)
    }
}

enum class SnapDivision(val label: String) {
    BAR("BAR"),
    BEAT("1/4"),
    HALF_BEAT("1/8"),
    QUARTER_BEAT("1/16"),
    EIGHTH_BEAT("1/32"),
    SIXTEENTH_BEAT("1/64"),
    EIGHTH_TRIPLET("1/8T"),
    QUARTER_TRIPLET("1/4T"),
    OFF("OFF");

    fun ticks(timebase: TimelineTimebase): Long = when (this) {
        BAR -> timebase.ticksPerBar
        BEAT -> timebase.ticksPerBeat
        HALF_BEAT -> timebase.ticksPerBeat / 2L
        QUARTER_BEAT -> timebase.ticksPerBeat / 4L
        EIGHTH_BEAT -> timebase.ticksPerBeat / 8L
        SIXTEENTH_BEAT -> timebase.ticksPerBeat / 16L
        EIGHTH_TRIPLET -> timebase.ticksPerBeat / 3L
        QUARTER_TRIPLET -> timebase.ticksPerBeat * 2L / 3L
        OFF -> 0L
    }.coerceAtLeast(if (this == OFF) 0L else 1L)

    fun next(): SnapDivision = entries[(ordinal + 1) % entries.size]
}

/** End tick of the 1800-bar arrangement, with overflow protection. */
fun maxArrangementTick(timebase: TimelineTimebase): Long {
    val bars = TimelineViewport.MAX_ARRANGEMENT_BARS
    return if (timebase.ticksPerBar > Long.MAX_VALUE / bars) Long.MAX_VALUE
    else timebase.ticksPerBar * bars
}

/**
 * Arrangement pane-local viewport factory. Track headers are a sibling column;
 * all x coordinates passed to this viewport are local to the timeline canvas.
 */
fun arrangementPaneViewport(
    timebase: TimelineTimebase,
    pixelsPerBar: Float,
    scrollTimeTicks: Double,
    viewportWidthPx: Float
): TimelineViewport = TimelineViewport(
    timebase = timebase,
    pixelsPerBar = pixelsPerBar,
    scrollTimeTicks = scrollTimeTicks,
    viewportWidthPx = viewportWidthPx,
    trackHeaderWidthPx = 0f
)

/**
 * Shared horizontal mapping for the arrangement. scrollTimeTicks represents the musical
 * tick at the left edge of the timeline pane, excluding the fixed track-header column.
 * All viewport navigation and conversions use one scale and bounded project extent.
 */
data class TimelineViewport(
    val timebase: TimelineTimebase,
    val pixelsPerBar: Float,
    val scrollTimeTicks: Double,
    val viewportWidthPx: Float,
    val trackHeaderWidthPx: Float = 0f
) {
    val effectivePixelsPerBar: Float =
        (if (pixelsPerBar.isFinite()) pixelsPerBar else MIN_PIXELS_PER_BAR)
            .coerceIn(MIN_PIXELS_PER_BAR, MAX_PIXELS_PER_BAR)
    val pixelsPerTick: Double =
        (effectivePixelsPerBar.toDouble() / timebase.ticksPerBar.toDouble()).coerceAtLeast(0.000001)
    val effectiveViewportWidthPx: Float =
        (if (viewportWidthPx.isFinite()) viewportWidthPx else 0f).coerceAtLeast(0f)
    val safeTrackHeaderWidthPx: Float =
        (if (trackHeaderWidthPx.isFinite()) trackHeaderWidthPx else 0f).coerceAtLeast(0f)
    val effectiveScrollTimeTicks: Double =
        (if (scrollTimeTicks.isFinite()) scrollTimeTicks else 0.0).coerceIn(0.0, maxScrollTicks())

    fun tickDeltaToPixels(ticks: Long): Float =
        (ticks.toDouble() * pixelsPerTick).coerceIn(0.0, Float.MAX_VALUE.toDouble()).toFloat()

    fun pixelsToTickDelta(pixels: Float): Long {
        if (!pixels.isFinite()) return 0L
        val ticks = pixels.toDouble() / pixelsPerTick
        return when {
            ticks >= Long.MAX_VALUE.toDouble() -> Long.MAX_VALUE
            ticks <= Long.MIN_VALUE.toDouble() -> Long.MIN_VALUE
            else -> ticks.roundToLong()
        }
    }

    fun timeToScreenX(tick: Long): Float {
        val boundedTick = tick.coerceIn(0L, maxArrangementTick(timebase))
        val x = safeTrackHeaderWidthPx.toDouble() +
            (boundedTick.toDouble() - effectiveScrollTimeTicks) * pixelsPerTick
        return x.coerceIn(-Float.MAX_VALUE.toDouble(), Float.MAX_VALUE.toDouble()).toFloat()
    }

    fun screenXToTime(screenX: Float): Long {
        if (!screenX.isFinite()) return if (screenX > 0f) maxArrangementTick(timebase) else 0L
        return (effectiveScrollTimeTicks + (screenX.toDouble() - safeTrackHeaderWidthPx) / pixelsPerTick)
            .coerceIn(0.0, maxArrangementTick(timebase).toDouble()).roundToLong()
    }

    fun timeToViewportX(tick: Long): Float = timeToScreenX(tick) - safeTrackHeaderWidthPx

    fun viewportXToTime(viewportX: Float): Long {
        if (!viewportX.isFinite()) return if (viewportX > 0f) maxArrangementTick(timebase) else 0L
        return screenXToTime(viewportX + safeTrackHeaderWidthPx)
    }

    fun firstVisibleBar(marginBars: Int = 1): Long =
        (floor(effectiveScrollTimeTicks / timebase.ticksPerBar.toDouble()).toLong() - marginBars)
            .coerceIn(0L, MAX_ARRANGEMENT_BARS - 1L)

    fun lastVisibleBar(marginBars: Int = 2): Long =
        floor((effectiveScrollTimeTicks + effectiveViewportWidthPx.toDouble() / pixelsPerTick) /
            timebase.ticksPerBar.toDouble()).toLong().plus(marginBars)
            .coerceIn(0L, MAX_ARRANGEMENT_BARS - 1L)

    /** Change the scale without moving the musical tick under the screen anchor. */
    fun zoomKeepingAnchor(newPixelsPerBar: Float, anchorScreenX: Float): TimelineViewport {
        if (!anchorScreenX.isFinite()) return this
        val anchorTick = (effectiveScrollTimeTicks +
            (anchorScreenX - safeTrackHeaderWidthPx).toDouble() / pixelsPerTick)
            .coerceIn(0.0, maxArrangementTick(timebase).toDouble())
        val requestedScale = if (newPixelsPerBar.isFinite()) newPixelsPerBar else MAX_PIXELS_PER_BAR
        val nextScale = requestedScale.coerceIn(MIN_PIXELS_PER_BAR, MAX_PIXELS_PER_BAR)
        val nextPixelsPerTick = nextScale.toDouble() / timebase.ticksPerBar.toDouble()
        val nextScroll = (anchorTick -
            (anchorScreenX - safeTrackHeaderWidthPx).toDouble() / nextPixelsPerTick)
            .coerceIn(0.0, maxScrollTicks(nextPixelsPerTick))
        return copy(pixelsPerBar = nextScale, scrollTimeTicks = nextScroll)
    }

    /** Place a fractional tick at local timeline x without rounding to the snap grid. */
    fun placeTickAtViewportX(tick: Double, viewportX: Float): TimelineViewport {
        if (!tick.isFinite() || !viewportX.isFinite()) return this
        val boundedTick = tick.coerceIn(0.0, maxArrangementTick(timebase).toDouble())
        val nextScroll = (boundedTick - viewportX.toDouble() / pixelsPerTick)
            .coerceIn(0.0, maxScrollTicks())
        return copy(scrollTimeTicks = nextScroll)
    }

    fun placeTickAtViewportX(tick: Long, viewportX: Float): TimelineViewport =
        placeTickAtViewportX(tick.toDouble(), viewportX)

    fun panByScreenPixels(deltaX: Float): TimelineViewport {
        if (!deltaX.isFinite()) return this
        return copy(scrollTimeTicks = (effectiveScrollTimeTicks - deltaX.toDouble() / pixelsPerTick)
            .coerceIn(0.0, maxScrollTicks()))
    }

    fun followPlayhead(tick: Long, followFraction: Float = 0.78f, targetFraction: Float = 0.68f): TimelineViewport {
        if (effectiveViewportWidthPx <= 0f || tick < 0L) return this
        val boundedTick = tick.coerceIn(0L, maxArrangementTick(timebase))
        val x = (boundedTick.toDouble() - effectiveScrollTimeTicks) * pixelsPerTick
        val guard = effectiveViewportWidthPx * followFraction.coerceIn(0.2f, 0.95f)
        if (x >= 0.0 && x <= guard.toDouble()) return this
        val targetX = (effectiveViewportWidthPx * targetFraction.coerceIn(0.1f, 0.9f))
            .coerceIn(0f, effectiveViewportWidthPx)
        return placeTickAtViewportX(boundedTick, targetX)
    }

    fun panByBars(bars: Double): TimelineViewport {
        if (!bars.isFinite()) return this
        return copy(scrollTimeTicks = (effectiveScrollTimeTicks + bars * timebase.ticksPerBar)
            .coerceIn(0.0, maxScrollTicks()))
    }

    fun snapTick(tick: Long, division: SnapDivision, enabled: Boolean = true): Long =
        if (enabled) timebase.snap(tick.coerceIn(0L, maxArrangementTick(timebase)), division)
        else tick.coerceIn(0L, maxArrangementTick(timebase))

    fun visibleSubdivisionTicks(minimumPixels: Double = 0.35): Long {
        val candidates = listOf(
            timebase.ticksPerBeat / 16L,
            timebase.ticksPerBeat / 8L,
            timebase.ticksPerBeat / 4L,
            timebase.ticksPerBeat / 2L,
            timebase.ticksPerBeat
        ).filter { it > 0L }
        return candidates.firstOrNull { it * pixelsPerTick >= minimumPixels } ?: timebase.ticksPerBeat
    }

    private fun maxScrollTicks(scalePixelsPerTick: Double = pixelsPerTick): Double =
        (maxArrangementTick(timebase).toDouble() -
            effectiveViewportWidthPx.toDouble() / scalePixelsPerTick.coerceAtLeast(0.000001)).coerceAtLeast(0.0)

    companion object {
        const val MIN_PIXELS_PER_BAR = 20f
        const val MAX_PIXELS_PER_BAR = 4800f
        const val MAX_ARRANGEMENT_BARS = 1800L
    }
}
