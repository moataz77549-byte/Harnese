package com.jarves.mh.runtime

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.json.JSONObject

internal enum class OfficialCli(
    val repository: String,
    val tagPrefix: String,
    val assetName: String,
    val executableNames: Set<String>,
    val executable: String,
) {
    ANTIGRAVITY(
        "google-antigravity/antigravity-cli", "", "agy_cli_linux_arm64.tar.gz",
        setOf("antigravity", "agy"), "agy",
    ),
    CODEX(
        "openai/codex", "rust-v", "codex-aarch64-unknown-linux-musl.tar.gz",
        setOf("codex", "codex-aarch64-unknown-linux-musl"), "codex",
    );

    val latestUrl: String get() = "https://api.github.com/repos/$repository/releases/latest"

    fun readRelease(release: JSONObject): OfficialCliRelease {
        require(!release.optBoolean("draft") && !release.optBoolean("prerelease")) { "Stable release required" }
        val tag = release.getString("tag_name")
        require(tag.startsWith(tagPrefix)) { "Unexpected release tag" }
        val version = tag.removePrefix(tagPrefix).removePrefix("v")
        require(version.matches(versionPattern)) { "Unexpected release version" }
        val assets = release.getJSONArray("assets")
        val asset = (0 until assets.length()).asSequence().map(assets::getJSONObject)
            .singleOrNull { it.optString("name") == assetName }
            ?: error("Official Linux ARM64 asset was not found")
        val digest = asset.getString("digest")
        require(digest.matches(Regex("sha256:[0-9a-fA-F]{64}"))) { "Official SHA-256 digest required" }
        val url = asset.getString("browser_download_url")
        require(url == "https://github.com/$repository/releases/download/$tag/$assetName") {
            "Unexpected official artifact URL"
        }
        val size = asset.getLong("size")
        require(size in 1..512L * 1024 * 1024) { "Unexpected official artifact size" }
        return OfficialCliRelease(version, url, digest.substringAfter(':'), size)
    }
}

internal data class OfficialCliRelease(val version: String, val url: String, val sha256: String, val size: Long)

private val versionPattern = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")

internal fun cliVersion(output: String): String? = versionPattern.find(output)?.value

/** Extract only the expected regular executable. Archive paths are never materialized. */
internal suspend fun extractCliExecutable(archive: File, output: File, names: Set<String>) {
    var found = false
    TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(archive.inputStream()))).use { tar ->
        var expanded = 0L
        var count = 0
        while (true) {
            coroutineContext.ensureActive()
            val entry = tar.nextEntry ?: break
            check(++count <= 64) { "Unexpected number of files in CLI archive" }
            check(entry.size in 0..(512L * 1024 * 1024 - expanded)) { "CLI archive expansion exceeds limit" }
            expanded += entry.size
            if (entry.name.trimEnd('/').substringAfterLast('/') !in names) continue
            check(!found && entry.isFile && !entry.isSparse) { "Unexpected CLI executable entry" }
            var written = 0L
            FileOutputStream(output).use { target ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val bytes = tar.read(buffer)
                    if (bytes < 0) break
                    target.write(buffer, 0, bytes)
                    written += bytes
                }
            }
            check(written == entry.size) { "Incomplete CLI executable" }
            found = true
        }
    }
    check(found && isLinuxArm64Elf(output)) { "Official CLI executable is missing or has the wrong architecture" }
}

internal fun isLinuxArm64Elf(file: File): Boolean = file.inputStream().use { input ->
    val header = ByteArray(64)
    var offset = 0
    while (offset < header.size) {
        val count = input.read(header, offset, header.size - offset)
        if (count < 0) return@use false
        offset += count
    }
    header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
        header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte() &&
        header[4] == 2.toByte() && header[5] == 1.toByte() && header[6] == 1.toByte() &&
        header[16].toInt() in 2..3 && header[17] == 0.toByte() &&
        header[18] == 183.toByte() && header[19] == 0.toByte()
}

internal fun atomicReplace(source: File, destination: File) {
    Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
