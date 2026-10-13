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

    @Test fun rejectsOversizedProjectBeforeParsing() {
        val tooLarge = ByteArray(StudioProjectFile.MAX_PROJECT_FILE_BYTES + 1)
        assertThrows(IllegalArgumentException::class.java) {
            StudioProjectFile.decode(tooLarge)
        }
    }
}
