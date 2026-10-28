package com.studiowave.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StudioProjectBundleTest {
    @Test fun projectAndMediaRoundTripAcrossPortableBundle() {
        val project = StudioProject(name = "Bundle round trip")
        val media = listOf(
            StudioProjectBundle.MediaAsset(
                id = "kick",
                fileName = "samples/drum-kick.wav",
                mimeType = "audio/wav",
                bytes = byteArrayOf(0, 1, 2, 3, -1)
            ),
            StudioProjectBundle.MediaAsset(
                id = "texture",
                fileName = "../outside/texture.bin",
                bytes = ByteArray(1024) { (it % 127).toByte() }
            )
        )
        val output = ByteArrayOutputStream()
        StudioProjectBundle.write(project, media, output)

        val restored = StudioProjectBundle.read(ByteArrayInputStream(output.toByteArray()))
        assertEquals(project, restored.project)
        assertEquals(listOf("kick", "texture"), restored.media.map { it.id })
        assertEquals("drum-kick.wav", restored.media[0].fileName)
        assertEquals("texture.bin", restored.media[1].fileName)
        assertArrayEquals(media[0].bytes, restored.media[0].bytes)
        assertArrayEquals(media[1].bytes, restored.media[1].bytes)
    }

    @Test fun rejectsDuplicateMediaIdsAndOversizedMedia() {
        val duplicateIds = listOf(
            StudioProjectBundle.MediaAsset("same", "a.wav", bytes = byteArrayOf(1)),
            StudioProjectBundle.MediaAsset("same", "b.wav", bytes = byteArrayOf(2))
        )
        assertThrows(IllegalArgumentException::class.java) {
            StudioProjectBundle.write(StudioProject(), duplicateIds, ByteArrayOutputStream())
        }
        val tooLarge = StudioProjectBundle.MediaAsset(
            id = "large",
            fileName = "large.bin",
            bytes = ByteArray(StudioProjectBundle.MAX_SINGLE_MEDIA_BYTES + 1)
        )
        assertThrows(IllegalArgumentException::class.java) {
            StudioProjectBundle.write(StudioProject(), listOf(tooLarge), ByteArrayOutputStream())
        }
    }

    @Test fun rejectsPathTraversalOrUnknownZipEntries() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.txt"))
            zip.write("nope".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            StudioProjectBundle.read(ByteArrayInputStream(output.toByteArray()))
        }
    }

    @Test fun rejectsCorruptManifestAndUndeclaredMedia() {
        val projectBytes = StudioProjectFile.encode(StudioProject())
        val manifest = """{"formatVersion":1,"project":"project.sdaw","media":[]}""".toByteArray()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
            zip.putNextEntry(ZipEntry("project.sdaw")); zip.write(projectBytes); zip.closeEntry()
            zip.putNextEntry(ZipEntry("media/0.bin")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            StudioProjectBundle.read(ByteArrayInputStream(output.toByteArray()))
        }
    }

    @Test fun rejectsUnsupportedFormatVersion() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"formatVersion":2,"project":"project.sdaw","media":[]}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("project.sdaw"))
            zip.write(StudioProjectFile.encode(StudioProject()))
            zip.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            StudioProjectBundle.read(ByteArrayInputStream(output.toByteArray()))
        }
    }
}
