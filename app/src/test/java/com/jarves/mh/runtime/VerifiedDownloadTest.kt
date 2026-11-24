package com.jarves.mh.runtime

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifiedDownloadTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun retriesIncompleteResponseUsingValidatedRange() = runBlocking {
        val destination = File(directory.root, "agent")
        val sockets = ArrayDeque(listOf(
            Response(200, "ab", 6),
            Response(206, "cdef", 4, mapOf("Content-Range" to "bytes 2-5/6")),
        ))
        val ranges = mutableListOf<String?>()
        val downloader = downloader(sockets, ranges)
        downloader.fetch(origin, destination, digest("abcdef"), "SHA-256", 6) { _, _ -> }
        assertEquals("abcdef", destination.readText())
        assertEquals(listOf(null, "bytes=2-"), ranges)
        assertFalse(File(directory.root, "agent.part").exists())
    }

    @Test fun serverIgnoringRangeRestartsDownload() = runBlocking {
        val destination = File(directory.root, "agent")
        File(directory.root, "agent.part").writeText("bad")
        val downloader = downloader(ArrayDeque(listOf(Response(200, "abcdef", 6))), mutableListOf())
        downloader.fetch(origin, destination, digest("abcdef"), "SHA-256", 6) { _, _ -> }
        assertEquals("abcdef", destination.readText())
    }

    @Test fun wrongRangeLeavesVerifiedDestinationUntouched() = runBlocking {
        val destination = File(directory.root, "agent").apply { writeText("old") }
        File(directory.root, "agent.part").writeText("ab")
        val downloader = downloader(ArrayDeque(listOf(
            Response(206, "def", 3, mapOf("Content-Range" to "bytes 3-5/6")),
        )), mutableListOf())
        val result = runCatching { downloader.fetch(origin, destination, digest("abcdef"), "SHA-256", 6) { _, _ -> } }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("old", destination.readText())
        assertEquals("ab", File(directory.root, "agent.part").readText())
    }

    @Test fun digestMismatchDiscardsPartialWithoutPublishingArtifact() = runBlocking {
        val destination = File(directory.root, "agent")
        val downloader = downloader(ArrayDeque(listOf(Response(200, "abcdef", 6))), mutableListOf())
        val result = runCatching { downloader.fetch(origin, destination, digest("fedcba"), "SHA-256", 6) { _, _ -> } }
        assertTrue(result.exceptionOrNull() is IOException)
        assertFalse(destination.exists())
        assertFalse(File(directory.root, "agent.part").exists())
    }

    @Test fun rejectsNonTlsRedirectsBeforeReadingResponse() = runBlocking {
        val destination = File(directory.root, "agent")
        val ranges = mutableListOf<String?>()
        val downloader = downloader(ArrayDeque(listOf(
            Response(302, "", 0, mapOf("Location" to "http://example.invalid/executable")),
        )), ranges)
        val result = runCatching { downloader.fetch(origin, destination, digest("abcdef"), "SHA-256", 6) { _, _ -> } }
        assertTrue(result.exceptionOrNull() is IOException)
        assertFalse(destination.exists())
        assertEquals(1, ranges.size)
        assertNull(ranges.single())
    }

    private fun downloader(sockets: ArrayDeque<Response>, ranges: MutableList<String?>) =
        VerifiedDownload(open = {
            assertEquals(origin, it)
            val response = sockets.removeFirst()
            object : HttpURLConnection(it) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
                override fun getContentLengthLong() = response.declared
                override fun getInputStream() = ByteArrayInputStream(response.body.toByteArray())
                override fun getHeaderField(name: String?) = response.headers[name]
                override fun getResponseCode(): Int {
                    val range = getRequestProperty("Range")
                    if (ranges.size == response.ordinal) ranges += range
                    return response.code
                }
            }.also { response.ordinal = ranges.size }
        }, retryDelay = {})

    private data class Response(
        val code: Int, val body: String, val declared: Long, val headers: Map<String, String> = emptyMap(),
        var ordinal: Int = 0,
    )

    private fun digest(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        val origin = URL("https://example.invalid/executable")
    }
}
