package com.studiowave.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class StudioProjectFileTest {
    @Test fun sdawWriteAndReadRoundTripProjectState() {
        val original = StudioProject()
        val bytes = StudioProjectFile.encode(original)
        val restored = StudioProjectFile.decode(bytes)
        assertEquals(original, restored)
    }

    @Test fun streamReadAndWriteUseTheSamePortableFormat() {
        val original = StudioProject()
        val output = ByteArrayOutputStream()
        StudioProjectFile.write(original, output)
        val restored = StudioProjectFile.read(ByteArrayInputStream(output.toByteArray()))
        assertEquals(original, restored)
        assertTrue(String(output.toByteArray(), StandardCharsets.UTF_8).contains("\"schemaVersion\""))
    }

    @Test fun rejectsWrongFileTypeAndUnsupportedSchema() {
        assertThrows(IllegalArgumentException::class.java) {
            StudioProjectFile.decode("{\"hello\":\"world\"}".toByteArray(StandardCharsets.UTF_8))
        }
        val incompatible = StudioProject().toJson().put("schemaVersion", 999)
        assertThrows(IllegalArgumentException::class.java) {
            StudioProjectFile.decode(incompatible.toString().toByteArray(StandardCharsets.UTF_8))
        }
    }

    @Test fun fiftyTrackSixtyDeviceFiveMinuteSessionLoadsWithinFifteenSeconds() {
        // Regression benchmark for the user's real-world target: 50 tracks, 60
        // mixer/device modules, and a five-minute arrangement at 120 BPM.
        val tracks = (0 until 50).map { trackIndex ->
            val kind = when (trackIndex % 3) {
                0 -> "audio"
                1 -> "midi"
                else -> "drum"
            }
            StudioTrack(
                id = "benchmark-track-$trackIndex",
                name = "Track $trackIndex",
                colorArgb = 0xFF30343B,
                kind = kind,
                volume = 0.8f,
                pan = if (trackIndex % 2 == 0) -0.15f else 0.15f,
                clips = listOf(
                    WaveClip(
                        id = "benchmark-clip-$trackIndex",
                        name = "Five minute clip $trackIndex",
                        startTick = 0L,
                        lengthTicks = 9_600L,
                        sourceUri = if (kind == "audio") "content://benchmark/source-$trackIndex" else null,
                        midiNotes = if (kind == "midi") (0 until 8).map { noteIndex ->
                            MidiNote(
                                id = "note-$trackIndex-$noteIndex",
                                pitch = 48 + noteIndex,
                                startTick = noteIndex * 120L,
                                durationTicks = 120L,
                                velocity = 90
                            )
                        } else emptyList(),
                        drumSteps = if (kind == "drum") List(16) { it % 4 == 0 } else emptyList()
                    )
                ),
                devices = if (trackIndex < 10) (0 until 6).map { deviceIndex ->
                    TrackDevice(
                        id = "device-$trackIndex-$deviceIndex",
                        type = if (deviceIndex % 2 == 0) "PARAM_EQ_8" else "COMPRESSOR",
                        name = "Module $deviceIndex",
                        parameters = if (deviceIndex % 2 == 0) {
                            defaultDeviceParameters("PARAM_EQ_8")
                        } else {
                            defaultDeviceParameters("COMPRESSOR")
                        }
                    )
                } else emptyList()
            )
        }
        val original = StudioProject(name = "50 Track Performance Benchmark", bpm = 120, tracks = tracks)

        val startedAt = System.nanoTime()
        val restored = StudioProjectFile.decode(StudioProjectFile.encode(original))
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L

        assertEquals(50, restored.tracks.size)
        assertEquals(60, restored.tracks.sumOf { it.devices.size })
        assertEquals(50, restored.tracks.sumOf { it.clips.size })
        assertEquals(9_600L, restored.tracks.first().clips.single().lengthTicks)
        assertTrue("50-track/60-device project load took ${elapsedMs} ms", elapsedMs < 15_000L)
    }

    @Test fun projectBundleRoundTripsProjectAndMediaBytes() {
        val original = StudioProject(
            name = "Portable project",
            tracks = listOf(
                StudioTrack(
                    name = "Audio",
                    colorArgb = 0xFF3B82F6L,
                    kind = "audio",
                    clips = listOf(
                        WaveClip(
                            name = "kick.wav",
                            startTick = 0L,
                            lengthTicks = 960L,
                            sourceUri = "studiowave-bundle://media-0"
                        )
                    )
                )
            )
        )
        val mediaBytes = byteArrayOf(82, 73, 70, 70, 1, 2, 3, 4)
        val output = ByteArrayOutputStream()
        StudioProjectBundle.write(
            original,
            listOf(StudioProjectBundle.MediaAsset("media-0", "kick.wav", "audio/wav", mediaBytes)),
            output
        )

        val restored = StudioProjectBundle.read(ByteArrayInputStream(output.toByteArray()))
        // Project decoding intentionally normalizes legacy trim metadata, so assert
        // portable musical/source state rather than data-class equality on derived fields.
        assertEquals(original.name, restored.project.name)
        assertEquals(1, restored.project.tracks.size)
        assertEquals("audio", restored.project.tracks.single().kind)
        assertEquals("studiowave-bundle://media-0", restored.project.tracks.single().clips.single().sourceUri)
        assertEquals(960L, restored.project.tracks.single().clips.single().lengthTicks)
        assertEquals(1, restored.media.size)
        assertEquals("media-0", restored.media.single().id)
        assertTrue(restored.media.single().bytes.contentEquals(mediaBytes))
    }

    @Test fun projectBundleRejectsUnexpectedArchivePaths() {
        val output = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("../escape"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            StudioProjectBundle.read(ByteArrayInputStream(output.toByteArray()))
        }
    }

    @Test fun rejectsOversizedProjectBeforeParsing() {
        val tooLarge = ByteArray(StudioProjectFile.MAX_PROJECT_FILE_BYTES + 1)
        assertThrows(IllegalArgumentException::class.java) {
            StudioProjectFile.decode(tooLarge)
        }
    }
}
