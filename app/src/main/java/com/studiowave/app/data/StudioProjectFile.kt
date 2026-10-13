package com.studiowave.app.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Portable StudioWave project container. The current format is bounded UTF-8 JSON;
 * source-linked audio remains referenced by URI and is not embedded in this file.
 */
object StudioProjectFile {
    const val MAX_PROJECT_FILE_BYTES = 16 * 1024 * 1024
    private const val READ_BUFFER_BYTES = 8 * 1024

    fun encode(project: StudioProject): ByteArray {
        val encoded = project.toJson().toString(2).toByteArray(StandardCharsets.UTF_8)
        require(encoded.size <= MAX_PROJECT_FILE_BYTES) {
            "Project file exceeds the ${MAX_PROJECT_FILE_BYTES / (1024 * 1024)} MiB safety limit"
        }
        return encoded
    }

    fun decode(encoded: ByteArray): StudioProject {
        require(encoded.isNotEmpty()) { "Project file is empty" }
        require(encoded.size <= MAX_PROJECT_FILE_BYTES) {
            "Project file exceeds the ${MAX_PROJECT_FILE_BYTES / (1024 * 1024)} MiB safety limit"
        }
        val json = JSONObject(String(encoded, StandardCharsets.UTF_8))
        require(json.has("schemaVersion") && json.has("tracks")) {
            "File is not a valid StudioWave project"
        }
        return StudioProject.fromJson(json)
    }

    fun read(input: InputStream): StudioProject {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var totalBytes = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            totalBytes += read
            require(totalBytes <= MAX_PROJECT_FILE_BYTES) {
                "Project file exceeds the ${MAX_PROJECT_FILE_BYTES / (1024 * 1024)} MiB safety limit"
            }
            result.write(buffer, 0, read)
        }
        return decode(result.toByteArray())
    }

    fun write(project: StudioProject, output: OutputStream) {
        output.write(encode(project))
        output.flush()
    }
}
