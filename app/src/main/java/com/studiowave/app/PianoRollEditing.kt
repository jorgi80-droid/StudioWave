package com.studiowave.app

import com.studiowave.app.data.MidiNote
import kotlin.math.max
import kotlin.math.min

/** Number of pitches visible at once; vertical scrolling moves a two-octave window. */
const val PIANO_VISIBLE_PITCH_ROWS = 24

enum class MidiNoteDragMode { MOVE, RESIZE_LEFT, RESIZE_RIGHT }

fun pianoPitchForRow(verticalScroll: Int, row: Int, visibleRows: Int = PIANO_VISIBLE_PITCH_ROWS): Int {
    val rows = visibleRows.coerceIn(1, 128)
    return (verticalScroll.coerceIn(0, 128 - rows) + rows - 1 - row.coerceIn(0, rows - 1))
        .coerceIn(0, 127)
}

fun midiNoteEndTick(note: MidiNote): Long {
    val start = note.startTick.coerceAtLeast(0L)
    val duration = note.durationTicks.coerceAtLeast(1L)
    return if (start > Long.MAX_VALUE - duration) Long.MAX_VALUE else start + duration
}

fun normalizeMidiNotes(notes: List<MidiNote>, maxTick: Long, maxNotes: Int = 16384): List<MidiNote> {
    val end = maxTick.coerceAtLeast(1L)
    return notes.asSequence().take(maxNotes.coerceAtLeast(0)).map { note ->
        val duration = note.durationTicks.coerceIn(1L, end)
        val start = note.startTick.coerceAtLeast(0L).coerceAtMost((end - duration).coerceAtLeast(0L))
        note.copy(
            pitch = note.pitch.coerceIn(0, 127),
            startTick = start,
            durationTicks = duration,
            velocity = note.velocity.coerceIn(1, 127)
        )
    }.distinctBy { it.id }.toList()
}

/** Round a note start to the closest snap step and reserve space for its minimum duration. */
fun snapMidiStartTick(rawTick: Long, snapTicks: Long, maxTick: Long): Long {
    val max = maxTick.coerceAtLeast(0L)
    if (max == 0L) return 0L
    val step = snapTicks.coerceAtLeast(1L).coerceAtMost(max)
    val raw = rawTick.coerceIn(0L, max)
    val rounded = if (raw > Long.MAX_VALUE - step / 2L) Long.MAX_VALUE else ((raw + step / 2L) / step) * step
    return rounded.coerceIn(0L, (max - step).coerceAtLeast(0L))
}

/** Pure drag geometry, shared by the editor gesture handling and regression tests. */
fun updateMidiNoteFromDrag(
    source: MidiNote,
    mode: MidiNoteDragMode,
    deltaTicks: Long,
    deltaPitch: Int,
    snapTicks: Long,
    maxTick: Long
): MidiNote {
    val maximum = maxTick.coerceAtLeast(1L)
    val duration = source.durationTicks.coerceIn(1L, maximum)
    val start = source.startTick.coerceAtLeast(0L).coerceAtMost(maximum - duration)
    val origin = source.copy(
        startTick = start,
        durationTicks = duration,
        pitch = source.pitch.coerceIn(0, 127),
        velocity = source.velocity.coerceIn(1, 127)
    )
    val step = snapTicks.coerceAtLeast(1L).coerceAtMost(maximum)

    fun addSaturated(a: Long, b: Long): Long = when {
        b > 0L && a > Long.MAX_VALUE - b -> Long.MAX_VALUE
        b < 0L && a < Long.MIN_VALUE - b -> Long.MIN_VALUE
        else -> a + b
    }
    fun nearestGrid(value: Long, upper: Long): Long {
        val safe = value.coerceIn(0L, upper.coerceAtLeast(0L))
        val rounded = if (safe > Long.MAX_VALUE - step / 2L) Long.MAX_VALUE else ((safe + step / 2L) / step) * step
        return rounded.coerceAtMost(upper.coerceAtLeast(0L))
    }

    return when (mode) {
        MidiNoteDragMode.MOVE -> {
            val maxStart = (maximum - duration).coerceAtLeast(0L)
            val rawStart = addSaturated(start, deltaTicks).coerceIn(0L, maxStart)
            val snappedStart = (rawStart / step * step).coerceAtMost(maxStart)
            origin.copy(startTick = snappedStart, pitch = (origin.pitch + deltaPitch).coerceIn(0, 127))
        }
        MidiNoteDragMode.RESIZE_RIGHT -> {
            val maxLength = (maximum - start).coerceAtLeast(1L)
            val minimum = min(step, maxLength).coerceAtLeast(1L)
            val rawLength = addSaturated(duration, deltaTicks).coerceIn(minimum, maxLength)
            val snappedLength = nearestGrid(rawLength, maxLength).coerceIn(minimum, maxLength)
            origin.copy(durationTicks = snappedLength)
        }
        MidiNoteDragMode.RESIZE_LEFT -> {
            val fixedEnd = midiNoteEndTick(origin).coerceAtMost(maximum)
            val minimum = min(step, fixedEnd).coerceAtLeast(1L)
            val latestStart = (fixedEnd - minimum).coerceAtLeast(0L)
            val rawStart = addSaturated(start, deltaTicks).coerceIn(0L, latestStart)
            val snappedStart = nearestGrid(rawStart, latestStart)
            origin.copy(
                startTick = snappedStart,
                durationTicks = (fixedEnd - snappedStart).coerceAtLeast(minimum)
            )
        }
    }
}

/** Extend, but never shorten, a MIDI pattern to the bar containing its latest note end. */
fun clipLengthForNotes(currentLength: Long, notes: List<MidiNote>, ticksPerBar: Long, maxTick: Long): Long {
    val maximum = maxTick.coerceAtLeast(1L)
    val bar = ticksPerBar.coerceAtLeast(1L).coerceAtMost(maximum)
    val current = currentLength.coerceIn(bar, maximum)
    val latestEnd = notes.maxOfOrNull { midiNoteEndTick(it).coerceAtMost(maximum) } ?: 0L
    if (latestEnd <= current) return current
    val bars = latestEnd / bar + if (latestEnd % bar == 0L) 0L else 1L
    val needed = if (bars > maximum / bar) maximum else bars * bar
    return max(current, needed.coerceIn(bar, maximum))
}
