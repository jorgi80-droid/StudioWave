package com.studiowave.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MidiNote(
    val id: String = UUID.randomUUID().toString(),
    val pitch: Int,
    val startTick: Long,
    val durationTicks: Long,
    val velocity: Int = 100
)

data class WaveClip(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val startTick: Long,
    val lengthTicks: Long,
    val sourceUri: String? = null,
    val midiNotes: List<MidiNote> = emptyList(),
    val drumSteps: List<Boolean> = emptyList(),
    val colorArgb: Long? = null,
    /** Source-relative trim offset for audio clips, measured in musical ticks. */
    val sourceOffsetTicks: Long = 0L
)

data class StudioTrack(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorArgb: Long,
    val kind: String,
    val muted: Boolean = false,
    val solo: Boolean = false,
    val volume: Float = 0.8f,
    val pan: Float = 0f,
    val clips: List<WaveClip> = emptyList()
)

data class StudioProject(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Neon Horizon",
    val bpm: Int = 120,
    val numerator: Int = 4,
    val denominator: Int = 4,
    val ppq: Int = 960,
    val tracks: List<StudioTrack> = defaultTracks()
) {
    /**
     * Moves an existing clip to a compatible track, removing any stale duplicate IDs first.
     * Returns null when either entity is missing, the track kinds differ, or the target is full.
     */
    fun moveClipToTrack(clipId: String, targetTrackId: String, startTick: Long): StudioProject? {
        val sourceTrack = tracks.firstOrNull { track -> track.clips.any { it.id == clipId } } ?: return null
        val targetTrack = tracks.firstOrNull { it.id == targetTrackId } ?: return null
        if (sourceTrack.kind != targetTrack.kind) return null
        val sourceClip = sourceTrack.clips.first { it.id == clipId }
        val cleanedTracks = tracks.map { track ->
            track.copy(clips = track.clips.filterNot { it.id == clipId })
        }
        val targetIndex = cleanedTracks.indexOfFirst { it.id == targetTrackId }
        if (targetIndex < 0 || cleanedTracks[targetIndex].clips.size >= MAX_CLIPS_PER_TRACK) return null
        val moved = sourceClip.copy(startTick = startTick.coerceAtLeast(0L))
        return copy(tracks = cleanedTracks.mapIndexed { index, track ->
            if (index == targetIndex) track.copy(clips = (track.clips + moved).sortedBy { it.startTick })
            else track
        })
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("id", id)
        put("name", name)
        put("bpm", bpm.coerceIn(40, 240))
        put("timeSignature", JSONObject().put("numerator", numerator).put("denominator", denominator))
        put("ppq", ppq)
        put("tracks", JSONArray().apply {
            tracks.forEach { tr ->
                put(JSONObject().apply {
                    put("id", tr.id); put("name", tr.name); put("colorArgb", tr.colorArgb)
                    put("kind", tr.kind); put("muted", tr.muted); put("solo", tr.solo)
                    put("volume", tr.volume.coerceIn(0f, 1.5f).toDouble())
                    put("pan", tr.pan.coerceIn(-1f, 1f).toDouble())
                    put("clips", JSONArray().apply {
                        tr.clips.forEach { clip ->
                            put(JSONObject().apply {
                                put("id", clip.id); put("name", clip.name)
                                put("startTick", clip.startTick); put("lengthTicks", clip.lengthTicks)
                                put("sourceUri", clip.sourceUri)
                                put("sourceOffsetTicks", clip.sourceOffsetTicks.coerceAtLeast(0L))
                                clip.colorArgb?.let { put("colorArgb", it) }
                                put("drumSteps", JSONArray().apply { clip.drumSteps.forEach { put(it) } })
                                put("midiNotes", JSONArray().apply {
                                    clip.midiNotes.forEach { note -> put(JSONObject()
                                        .put("id", note.id).put("pitch", note.pitch.coerceIn(0, 127))
                                        .put("startTick", note.startTick)
                                        .put("durationTicks", note.durationTicks.coerceAtLeast(1))
                                        .put("velocity", note.velocity.coerceIn(1, 127))) }
                                })
                            })
                        }
                    })
                })
            }
        })
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_TRACKS = 256
        const val MAX_CLIPS_PER_TRACK = 4096
        const val MAX_TOTAL_CLIPS = 16384
        const val MAX_NOTES_PER_CLIP = 16384
        const val MAX_TOTAL_MIDI_NOTES = 65536
        const val MAX_DRUM_STEPS_PER_CLIP = 4096
        const val MAX_TOTAL_DRUM_STEPS = 65536

        fun fromJson(json: JSONObject): StudioProject {
            val version = json.optInt("schemaVersion", 0)
            require(version in 1..CURRENT_SCHEMA_VERSION) { "Unsupported project schema version: $version" }
            val time = json.optJSONObject("timeSignature") ?: JSONObject()
            val tracksJson = json.optJSONArray("tracks") ?: JSONArray()
            require(tracksJson.length() <= MAX_TRACKS) { "Project contains too many tracks" }
            val loadedTracks = buildList {
                for (i in 0 until tracksJson.length()) {
                    val t = tracksJson.getJSONObject(i)
                    val kind = t.optString("kind", "midi")
                    require(kind in setOf("audio", "midi", "drum")) { "Unsupported track kind: $kind" }
                    val clipsJson = t.optJSONArray("clips") ?: JSONArray()
                    require(clipsJson.length() <= MAX_CLIPS_PER_TRACK) { "Track contains too many clips" }
                    val clips = buildList {
                        for (j in 0 until clipsJson.length()) {
                            val c = clipsJson.getJSONObject(j)
                            val stepsJson = c.optJSONArray("drumSteps") ?: JSONArray()
                            val drumSteps = buildList { for (stepIndex in 0 until stepsJson.length()) add(stepsJson.optBoolean(stepIndex)) }
                            val notesJson = c.optJSONArray("midiNotes") ?: JSONArray()
                            require(notesJson.length() <= MAX_NOTES_PER_CLIP) { "Clip contains too many MIDI notes" }
                            require(stepsJson.length() <= MAX_DRUM_STEPS_PER_CLIP) { "Clip contains too many drum steps" }
                            val notes = buildList {
                                for (k in 0 until notesJson.length()) {
                                    val n = notesJson.getJSONObject(k)
                                    add(MidiNote(
                                        id = n.optString("id", UUID.randomUUID().toString()),
                                        pitch = n.optInt("pitch", 60).coerceIn(0, 127),
                                        startTick = n.optLong("startTick", 0).coerceAtLeast(0),
                                        durationTicks = n.optLong("durationTicks", 240).coerceAtLeast(1),
                                        velocity = n.optInt("velocity", 100).coerceIn(1, 127)
                                    ))
                                }
                            }
                            add(WaveClip(
                                id = c.optString("id", UUID.randomUUID().toString()),
                                name = c.optString("name", "Clip"),
                                startTick = c.optLong("startTick", 0).coerceAtLeast(0),
                                lengthTicks = c.optLong("lengthTicks", 3840).coerceAtLeast(1),
                                sourceUri = c.optString("sourceUri").takeUnless { it == "null" || it.isBlank() },
                                midiNotes = notes,
                                drumSteps = drumSteps,
                                colorArgb = if (c.has("colorArgb")) c.optLong("colorArgb") else null,
                                sourceOffsetTicks = c.optLong("sourceOffsetTicks", 0L).coerceAtLeast(0L)
                            ))
                        }
                    }
                    add(StudioTrack(
                        id = t.optString("id", UUID.randomUUID().toString()),
                        name = t.optString("name", "Track ${i + 1}"),
                        colorArgb = t.optLong("colorArgb", 0xFF00D2B4),
                        kind = kind,
                        muted = t.optBoolean("muted"),
                        solo = t.optBoolean("solo"),
                        volume = t.optDouble("volume", 0.8).toFloat().coerceIn(0f, 1.5f),
                        pan = t.optDouble("pan", 0.0).toFloat().coerceIn(-1f, 1f),
                        clips = clips
                    ))
                }
            }
            require(loadedTracks.sumOf { it.clips.size } <= MAX_TOTAL_CLIPS) {
                "Project contains too many clips"
            }
            require(loadedTracks.sumOf { track -> track.clips.sumOf { it.midiNotes.size } } <= MAX_TOTAL_MIDI_NOTES) {
                "Project contains too many MIDI notes"
            }
            require(loadedTracks.sumOf { track -> track.clips.sumOf { it.drumSteps.size } } <= MAX_TOTAL_DRUM_STEPS) {
                "Project contains too many drum steps"
            }
            require(loadedTracks.map { it.id }.distinct().size == loadedTracks.size) {
                "Project contains duplicate track IDs"
            }
            val allClips = loadedTracks.flatMap { it.clips }
            require(allClips.map { it.id }.distinct().size == allClips.size) {
                "Project contains duplicate clip IDs"
            }
            return StudioProject(
                schemaVersion = version,
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Untitled"),
                bpm = json.optInt("bpm", 120).coerceIn(40, 240),
                numerator = time.optInt("numerator", 4).coerceIn(1, 32),
                denominator = time.optInt("denominator", 4).coerceIn(1, 32),
                ppq = json.optInt("ppq", 960).coerceIn(24, 3840),
                tracks = loadedTracks
            )
        }

        fun defaultTracks(): List<StudioTrack> = listOf(
            StudioTrack(name = "DRUM RACK", colorArgb = 0xFFFF7644, kind = "drum",
                clips = listOf(WaveClip(name = "808 GROOVE", startTick = 0, lengthTicks = 3840,
                    drumSteps = (0 until 16).map { it in listOf(0, 4, 8, 12) }))),
            StudioTrack(name = "SYNTH BASS", colorArgb = 0xFF00D2B4, kind = "midi",
                clips = listOf(WaveClip(name = "BASSLINE", startTick = 0, lengthTicks = 7680,
                    midiNotes = listOf(MidiNote(pitch = 36, startTick = 0, durationTicks = 480),
                        MidiNote(pitch = 36, startTick = 960, durationTicks = 240),
                        MidiNote(pitch = 43, startTick = 1920, durationTicks = 480))))),
            StudioTrack(name = "LEAD / CHORDS", colorArgb = 0xFFA855F7, kind = "midi",
                clips = listOf(WaveClip(name = "NEON CHORDS", startTick = 0, lengthTicks = 7680,
                    midiNotes = listOf(MidiNote(pitch = 60, startTick = 0, durationTicks = 960),
                        MidiNote(pitch = 64, startTick = 0, durationTicks = 960),
                        MidiNote(pitch = 67, startTick = 0, durationTicks = 960))))),
            StudioTrack(name = "AUDIO TEXTURE", colorArgb = 0xFF3B82F6, kind = "audio",
                clips = listOf(WaveClip(name = "TEXTURE FX", startTick = 0, lengthTicks = 15360)))
        )
    }
}