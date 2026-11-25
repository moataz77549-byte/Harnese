package com.jarves.mh.runtime

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Downloads immutable, checksum-pinned artifacts. The partial file is never executable. */
internal class VerifiedDownload(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val retryDelay: suspend (Int) -> Unit = { delay(1_000L shl it) },
) {
    suspend fun fetch(
        url: URL,
        destination: File,
        expectedChecksum: String,
        algorithm: String,
        maximumBytes: Long,
        onBytes: suspend (Long, Long) -> Unit,
    ) {
        require(url.protocol == "https" && url.userInfo == null) { "HTTPS artifact URL required" }
        require(maximumBytes > 0)
        require(expectedChecksum.matches(Regex("[0-9a-fA-F]+")) &&
            expectedChecksum.length == MessageDigest.getInstance(algorithm).digestLength * 2) {
            "Invalid artifact checksum"
        }
        val parent = checkNotNull(destination.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Could not create download directory" }
        val partial = File(parent, "${destination.name}.part")
        fun valid(file: File) = file.isFile && file.length() in 1..maximumBytes &&
            checksum(file, algorithm).equals(expectedChecksum, ignoreCase = true)
        if (valid(destination)) {
            onBytes(destination.length(), destination.length())
            return
        }
        if (!valid(partial)) {
            var completed = false
            for (attempt in 0..2) {
                try {
                    transfer(url, partial, maximumBytes, onBytes)
                    completed = true
                    break
                } catch (error: IOException) {
                    if (attempt == 2 || error is ArtifactRejected) throw error
                    retryDelay(attempt)
                }
            }
            check(completed)
            if (!valid(partial)) {
                Files.deleteIfExists(partial.toPath())
                throw ArtifactRejected("Downloaded artifact checksum did not match")
            }
        }
        coroutineContext.ensureActive()
        Files.move(partial.toPath(), destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        onBytes(destination.length(), destination.length())
    }

    private suspend fun transfer(
        source: URL,
        partial: File,
        maximumBytes: Long,
        onBytes: suspend (Long, Long) -> Unit,
    ) {
        coroutineContext.ensureActive()
        if (partial.length() >= maximumBytes) Files.deleteIfExists(partial.toPath())
        var existing = partial.length()
        var connection = connect(source, existing)
        try {
            if (connection.responseCode == 416 && existing > 0) {
                connection.disconnect()
                Files.deleteIfExists(partial.toPath())
                existing = 0
                connection = connect(source, existing)
            }
            val status = connection.responseCode
            val bounds = when (status) {
                HttpURLConnection.HTTP_OK -> {
                    existing = 0
                    val total = connection.contentLengthLong
                    TransferBounds(total, total)
                }
                HttpURLConnection.HTTP_PARTIAL -> parseRange(connection, existing)
                in 500..599, 408, 429 -> throw IOException("Artifact download returned HTTP $status")
                else -> throw ArtifactRejected("Artifact download returned HTTP $status")
            }
            if (bounds.total > maximumBytes) throw ArtifactRejected("Artifact exceeds its download size limit")
            val requiredBytes = bounds.total - existing
            if (requiredBytes > 0 && partial.parentFile!!.usableSpace < requiredBytes) {
                throw ArtifactRejected("Insufficient storage for artifact download")
            }
            onBytes(existing, bounds.total)
            connection.inputStream.use { input ->
                FileOutputStream(partial, existing > 0).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var downloaded = existing
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        if (downloaded > maximumBytes || (bounds.endExclusive > 0 && downloaded > bounds.endExclusive)) {
                            Files.deleteIfExists(partial.toPath())
                            throw ArtifactRejected("Artifact response exceeds its declared size")
                        }
                        output.write(buffer, 0, count)
                        onBytes(downloaded, bounds.total)
                    }
                    if (bounds.total > 0 && downloaded != bounds.total) {
                        throw IOException("Artifact download ended before all bytes arrived")
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun connect(source: URL, offset: Long): HttpURLConnection {
        var url = source
        repeat(6) {
            val connection = open(url).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
            }
            try {
                if (connection.responseCode !in listOf(301, 302, 303, 307, 308)) return connection
                val location = connection.getHeaderField("Location")
                    ?: throw ArtifactRejected("Artifact redirect has no location")
                url = URL(url, location)
                if (url.protocol != "https" || url.userInfo != null) {
                    throw ArtifactRejected("Artifact redirect must use HTTPS")
                }
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            connection.disconnect()
        }
        throw ArtifactRejected("Too many artifact redirects")
    }

    private fun parseRange(connection: HttpURLConnection, expectedStart: Long): TransferBounds {
        val match = contentRange.matchEntire(connection.getHeaderField("Content-Range").orEmpty())
        val start = match?.groupValues?.get(1)?.toLongOrNull()
        val end = match?.groupValues?.get(2)?.toLongOrNull()
        val total = match?.groupValues?.get(3)?.toLongOrNull()
        if (expectedStart <= 0 || start != expectedStart || end == null || total == null ||
            end < start || end >= total) {
            throw ArtifactRejected("Artifact server returned an invalid range")
        }
        val declared = connection.contentLengthLong
        if (declared >= 0 && declared != end - start + 1) {
            throw ArtifactRejected("Artifact range length did not match")
        }
        return TransferBounds(total, end + 1)
    }

    private data class TransferBounds(val total: Long, val endExclusive: Long)
    private class ArtifactRejected(message: String) : IOException(message)

    private companion object {
        val contentRange = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
    }
}

internal fun checksum(file: File, algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
