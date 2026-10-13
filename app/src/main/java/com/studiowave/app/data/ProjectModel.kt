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
    val colorArgb: Long? = null
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

        fun fromJson(json: JSONObject): StudioProject {
            val version = json.optInt("schemaVersion", 0)
            require(version in 1..CURRENT_SCHEMA_VERSION) { "Unsupported project schema version: $version" }
            val time = json.optJSONObject("timeSignature") ?: JSONObject()
            val tracksJson = json.optJSONArray("tracks") ?: JSONArray()
            val loadedTracks = buildList {
                for (i in 0 until tracksJson.length()) {
                    val t = tracksJson.getJSONObject(i)
                    val clipsJson = t.optJSONArray("clips") ?: JSONArray()
                    val clips = buildList {
                        for (j in 0 until clipsJson.length()) {
                            val c = clipsJson.getJSONObject(j)
                            val stepsJson = c.optJSONArray("drumSteps") ?: JSONArray()
                            val drumSteps = buildList { for (stepIndex in 0 until stepsJson.length()) add(stepsJson.optBoolean(stepIndex)) }
                            val notesJson = c.optJSONArray("midiNotes") ?: JSONArray()
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
                                colorArgb = if (c.has("colorArgb")) c.optLong("colorArgb") else null
                            ))
                        }
                    }
                    add(StudioTrack(
                        id = t.optString("id", UUID.randomUUID().toString()),
                        name = t.optString("name", "Track ${i + 1}"),
                        colorArgb = t.optLong("colorArgb", 0xFF00D2B4),
                        kind = t.optString("kind", "midi"),
                        muted = t.optBoolean("muted"),
                        solo = t.optBoolean("solo"),
                        volume = t.optDouble("volume", 0.8).toFloat().coerceIn(0f, 1.5f),
                        pan = t.optDouble("pan", 0.0).toFloat().coerceIn(-1f, 1f),
                        clips = clips
                    ))
                }
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