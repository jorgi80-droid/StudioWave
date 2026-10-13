package com.studiowave.app

import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Musical timebase shared by the Arrangement grid, ruler, clip placement and resizing.
 * PPQ denotes ticks per quarter note; meter denominator changes the size of a beat.
 */
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
        val roundedQuotient = if (remainder >= (step + 1L) / 2L) quotient + 1L else quotient
        return if (roundedQuotient > Long.MAX_VALUE / step) Long.MAX_VALUE - (Long.MAX_VALUE % step)
        else (roundedQuotient * step).coerceAtLeast(0L)
    }
}

enum class SnapDivision(val label: String) {
    BAR("BAR"),
    BEAT("BEAT"),
    HALF_BEAT("1/2"),
    QUARTER_BEAT("1/4"),
    EIGHTH_BEAT("1/8"),
    SIXTEENTH_BEAT("1/16"),
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

/**
 * Horizontal mapping for the timeline viewport.
 *
 * screenX uses the arrangement's coordinate space, where track names occupy the
 * first [trackHeaderWidthPx] pixels. The timeline viewport width is separate and
 * must not include that header. When called from a Canvas inside the timeline pane,
 * subtract trackHeaderWidthPx to get its local x.
 */
data class TimelineViewport(
    val timebase: TimelineTimebase,
    val pixelsPerBar: Float,
    val scrollTimeTicks: Double,
    val viewportWidthPx: Float,
    val trackHeaderWidthPx: Float = 0f
) {
    val pixelsPerTick: Double =
        ((if (pixelsPerBar.isFinite()) pixelsPerBar else MIN_PIXELS_PER_BAR)
            .coerceIn(MIN_PIXELS_PER_BAR, MAX_PIXELS_PER_BAR).toDouble() /
            timebase.ticksPerBar.toDouble()).coerceAtLeast(0.000001)

    fun timeToScreenX(tick: Long): Float {
        val x = trackHeaderWidthPx.toDouble() + (tick.toDouble() - scrollTimeTicks) * pixelsPerTick
        return x.coerceIn(-Float.MAX_VALUE.toDouble(), Float.MAX_VALUE.toDouble()).toFloat()
    }

    fun screenXToTime(screenX: Float): Long =
        (scrollTimeTicks + (screenX.toDouble() - trackHeaderWidthPx) / pixelsPerTick)
            .coerceIn(0.0, Long.MAX_VALUE.toDouble()).roundToLong()

    fun timeToViewportX(tick: Long): Float = timeToScreenX(tick) - trackHeaderWidthPx

    fun viewportXToTime(viewportX: Float): Long = screenXToTime(viewportX + trackHeaderWidthPx)

    fun firstVisibleBar(marginBars: Int = 1): Long =
        floor(scrollTimeTicks / timebase.ticksPerBar.toDouble()).toLong().minus(marginBars)
            .coerceAtLeast(0L)

    fun lastVisibleBar(marginBars: Int = 2): Long =
        floor((scrollTimeTicks + viewportWidthPx.coerceAtLeast(0f) / pixelsPerTick) /
            timebase.ticksPerBar.toDouble()).toLong().plus(marginBars).coerceAtLeast(0L)

    /** Change scale without changing the musical tick under the screen anchor. */
    fun zoomKeepingAnchor(newPixelsPerBar: Float, anchorScreenX: Float): TimelineViewport {
        val anchorTick = screenXToTime(anchorScreenX).toDouble()
        val requestedScale = if (newPixelsPerBar.isFinite()) newPixelsPerBar else MAX_PIXELS_PER_BAR
        val clampedScale = requestedScale.coerceIn(MIN_PIXELS_PER_BAR, MAX_PIXELS_PER_BAR)
        val newScale = clampedScale.toDouble() / timebase.ticksPerBar.toDouble()
        // scrollTimeTicks is the tick at the left edge of the timeline pane, not
        // the left edge of the whole arrangement including the track header.
        val newScroll = (anchorTick - (anchorScreenX - trackHeaderWidthPx) / newScale)
            .coerceIn(0.0, Long.MAX_VALUE.toDouble())
        return copy(pixelsPerBar = clampedScale, scrollTimeTicks = newScroll)
    }

    fun panByScreenPixels(deltaX: Float): TimelineViewport =
        copy(scrollTimeTicks = (scrollTimeTicks - deltaX.toDouble() / pixelsPerTick)
            .coerceIn(0.0, Long.MAX_VALUE.toDouble()))

    fun snapTick(tick: Long, division: SnapDivision, enabled: Boolean = true): Long =
        if (enabled) timebase.snap(tick, division) else tick.coerceAtLeast(0L)

    companion object {
        const val MIN_PIXELS_PER_BAR = 20f
        const val MAX_PIXELS_PER_BAR = 1200f
    }

    fun visibleSubdivisionTicks(pixelsPerTickThreshold: Double = 0.35): Long {
        val candidates = listOf(
            timebase.ticksPerBeat / 4L,
            timebase.ticksPerBeat / 2L,
            timebase.ticksPerBeat
        ).filter { it > 0L }
        return candidates.firstOrNull { it * pixelsPerTick >= pixelsPerTickThreshold }
            ?: timebase.ticksPerBeat
    }
}
