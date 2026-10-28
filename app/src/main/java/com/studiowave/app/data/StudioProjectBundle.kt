package com.studiowave.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Safe portable project bundle for moving a project between devices.
 *
 * Version 1 deliberately supports JSON metadata and declared media entries only.
 * Callers provide the media bytes, so Android content URIs are never opened implicitly.
 * Every archive path is generated here; caller-provided paths are not accepted.
 */
object StudioProjectBundle {
    const val FORMAT_VERSION = 1
    const val MAX_BUNDLE_BYTES = 64 * 1024 * 1024
    const val MAX_MEDIA_FILES = 256
    const val MAX_SINGLE_MEDIA_BYTES = 16 * 1024 * 1024
    const val MAX_TOTAL_MEDIA_BYTES = 48 * 1024 * 1024

    private const val MANIFEST_NAME = "manifest.json"
    private const val PROJECT_NAME = "project.sdaw"
    private const val MEDIA_PREFIX = "media/"
    private const val COPY_BUFFER_SIZE = 8 * 1024

    data class MediaAsset(
        val id: String,
        val fileName: String,
        val mimeType: String = "application/octet-stream",
        val bytes: ByteArray
    )

    data class RestoredBundle(
        val project: StudioProject,
        val media: List<MediaAsset>
    )

    fun write(project: StudioProject, media: List<MediaAsset>, output: OutputStream) {
        validateMedia(media)
        validateProjectMediaReferences(project, media.mapTo(HashSet()) { it.id })
        val projectBytes = StudioProjectFile.encode(project)
        val manifest = JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("project", PROJECT_NAME)
            .put("media", JSONArray().apply {
                media.forEachIndexed { index, asset ->
                    put(JSONObject()
                        .put("id", asset.id)
                        .put("entry", "$MEDIA_PREFIX$index.bin")
                        .put("fileName", safeDisplayName(asset.fileName))
                        .put("mimeType", asset.mimeType.take(128))
                        .put("bytes", asset.bytes.size))
                }
            })
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

        ZipOutputStream(output).use { zip ->
            putEntry(zip, MANIFEST_NAME, manifest)
            putEntry(zip, PROJECT_NAME, projectBytes)
            media.forEachIndexed { index, asset ->
                putEntry(zip, "$MEDIA_PREFIX$index.bin", asset.bytes)
            }
        }
    }

    fun read(input: InputStream): RestoredBundle {
        val entries = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "Bundle must not contain directory entries" }
                require(entries.size < MAX_MEDIA_FILES + 2) { "Bundle contains too many archive entries" }
                val name = entry.name
                require(name == MANIFEST_NAME || name == PROJECT_NAME ||
                    (name.startsWith(MEDIA_PREFIX) && name.matches(Regex("media/[0-9]+\\.bin")))) {
                    "Unsupported bundle path"
                }
                require(!entries.containsKey(name)) { "Duplicate bundle entry: $name" }
                val cap = if (name.startsWith(MEDIA_PREFIX)) MAX_SINGLE_MEDIA_BYTES else StudioProjectFile.MAX_PROJECT_FILE_BYTES
                val bytes = readBounded(zip, cap)
                totalBytes += bytes.size
                require(totalBytes <= MAX_BUNDLE_BYTES) { "Bundle exceeds the 64 MiB safety limit" }
                entries[name] = bytes
                zip.closeEntry()
            }
        }

        val manifestBytes = entries[MANIFEST_NAME] ?: error("Bundle manifest is missing")
        val projectBytes = entries[PROJECT_NAME] ?: error("Project data is missing")
        val manifest = JSONObject(String(manifestBytes, StandardCharsets.UTF_8))
        require(manifest.optInt("formatVersion", -1) == FORMAT_VERSION) { "Unsupported bundle version" }
        require(manifest.optString("project") == PROJECT_NAME) { "Invalid project entry" }
        val mediaJson = manifest.optJSONArray("media") ?: JSONArray()
        require(mediaJson.length() <= MAX_MEDIA_FILES) { "Too many media files in bundle" }

        val restoredMedia = ArrayList<MediaAsset>(mediaJson.length())
        val seenIds = HashSet<String>()
        var totalMediaBytes = 0L
        for (index in 0 until mediaJson.length()) {
            val item = mediaJson.getJSONObject(index)
            val expectedEntry = "$MEDIA_PREFIX$index.bin"
            require(item.optString("entry") == expectedEntry) { "Invalid media entry mapping" }
            val id = item.optString("id")
            require(id.isNotBlank() && seenIds.add(id)) { "Missing or duplicate media ID" }
            val bytes = entries[expectedEntry] ?: error("Media data is missing: $expectedEntry")
            require(item.optLong("bytes", -1L) == bytes.size.toLong()) { "Media length mismatch" }
            totalMediaBytes += bytes.size
            require(totalMediaBytes <= MAX_TOTAL_MEDIA_BYTES) { "Media exceeds the 48 MiB safety limit" }
            restoredMedia += MediaAsset(
                id = id,
                fileName = safeDisplayName(item.optString("fileName", "audio")),
                mimeType = item.optString("mimeType", "application/octet-stream").take(128),
                bytes = bytes
            )
        }
        val expectedNames = setOf(MANIFEST_NAME, PROJECT_NAME) + (0 until mediaJson.length()).map { "$MEDIA_PREFIX$it.bin" }
        require(entries.keys == expectedNames) { "Bundle contains undeclared or missing entries" }
        val project = StudioProjectFile.decode(projectBytes)
        validateProjectMediaReferences(project, restoredMedia.mapTo(HashSet()) { it.id })
        return RestoredBundle(project, restoredMedia)
    }

    /**
     * Portable token URIs must resolve to a declared media ID. This catches broken
     * project references before the Android importer writes any media to disk.
     */
    private fun validateProjectMediaReferences(project: StudioProject, mediaIds: Set<String>) {
        project.tracks.forEach { track ->
            track.clips.forEach { clip ->
                val source = clip.sourceUri ?: return@forEach
                if (source.startsWith("studiowave-bundle://")) {
                    val id = source.removePrefix("studiowave-bundle://")
                    require(id.isNotBlank() && mediaIds.contains(id)) {
                        "Project references missing bundled media"
                    }
                }
            }
        }
    }

    private fun validateMedia(media: List<MediaAsset>) {
        require(media.size <= MAX_MEDIA_FILES) { "Too many media files for project bundle" }
        val ids = HashSet<String>()
        var totalBytes = 0L
        media.forEach { asset ->
            require(asset.id.isNotBlank() && ids.add(asset.id)) { "Missing or duplicate media ID" }
            require(asset.bytes.size <= MAX_SINGLE_MEDIA_BYTES) { "A media file exceeds the 16 MiB safety limit" }
            totalBytes += asset.bytes.size
            require(totalBytes <= MAX_TOTAL_MEDIA_BYTES) { "Media exceeds the 48 MiB safety limit" }
            require(asset.mimeType.length <= 128) { "Invalid MIME type" }
        }
    }

    private fun safeDisplayName(value: String): String {
        val normalized = value.replace('\\', '/').substringAfterLast('/').trim()
        return normalized.take(128).ifBlank { "audio" }
    }

    private fun putEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun readBounded(input: InputStream, cap: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            require(total <= cap) { "Bundle entry exceeds its safety limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
