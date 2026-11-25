package com.jarves.mh.network

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GitHubRepositoryPagesTest {
    private fun page(first: Int, count: Int, longDescription: Boolean = false): String =
        JSONArray().apply {
            repeat(count) { offset ->
                val name = "owner/repo-${first + offset}"
                put(JSONObject()
                    .put("full_name", name)
                    .put("clone_url", "https://github.com/$name.git")
                    .put("private", offset % 2 == 0)
                    .put("default_branch", "main")
                    .put("description", if (longDescription) "مستودع".repeat(900) else JSONObject.NULL)
                    .put("updated_at", "2026-11-25T00:00:00Z"))
            }
        }.toString()

    @Test fun loadsHundredsOfRepositoriesWithoutTailTruncationOrStderrMixing() {
        val calls = mutableListOf<Int>()
        val longPage = page(1, 100, true)
        assertTrue(longPage.toByteArray(Charsets.UTF_8).size > 512 * 1024)
        val repos = GitHubRepositoryPages.load(fetchPage = { number ->
            calls += number
            GitHubCliResponse(0, when (number) {
                1 -> longPage
                2 -> page(101, 100)
                3 -> page(201, 100)
                else -> page(301, 21)
            }, "gh: harmless diagnostic")
        })
        assertEquals(listOf(1, 2, 3, 4), calls)
        assertEquals(321, repos.size)
        assertEquals("owner/repo-1", repos.first().fullName)
        assertEquals("مستودع".repeat(900), repos.first().description)
        assertEquals("", repos.last().description)
        assertEquals("owner/repo-321", repos.last().fullName)
        assertTrue(repos.first().private)
    }

    @Test fun exactMultipleContinuesToAnEmptyPageAndOverlappingNamesAreDeduplicated() {
        val calls = mutableListOf<Int>()
        val repos = GitHubRepositoryPages.load(fetchPage = { number ->
            calls += number
            GitHubCliResponse(0, when (number) {
                1 -> page(1, 100)
                2 -> page(100, 100)
                else -> "[]"
            }, "")
        })
        assertEquals(199, repos.size)
        assertEquals(listOf(1, 2, 3), calls)
    }

    @Test fun retriesTimeoutAndMalformedJsonThenReturnsCompletePage() {
        var calls = 0
        val retries = mutableListOf<Int>()
        val repos = GitHubRepositoryPages.load(fetchPage = {
            when (++calls) {
                1 -> GitHubCliResponse(-1, "", "", timedOut = true)
                2 -> GitHubCliResponse(0, "[{\"full_name\":\"owner/repo\"", "")
                else -> GitHubCliResponse(0, page(1, 1), "")
            }
        }, onRetry = { retries += it })
        assertEquals(3, calls)
        assertEquals(listOf(1, 2), retries)
        assertEquals("owner/repo-1", repos.single().fullName)
    }

    @Test fun rejectsNonArrayOrContaminatedOutputInsteadOfSilentlyReturningPartialData() {
        for (bad in listOf("\"not an array\"", "{\"message\":\"error\"}", "[]\nwarning")) {
            var calls = 0
            try {
                GitHubRepositoryPages.load(fetchPage = { calls++; GitHubCliResponse(0, bad, "") })
                fail("Expected invalid-response failure")
            } catch (error: GitHubRepositoryLoadException) {
                assertEquals(GitHubRepositoryLoadFailure.INVALID_RESPONSE, error.kind)
            }
            assertEquals(3, calls)
        }
    }

    @Test fun authenticationAndRateLimitsFailWithoutRetryingOrParsingErrorBody() {
        for ((message, kind) in listOf(
            "gh: Bad credentials (HTTP 401)" to GitHubRepositoryLoadFailure.AUTH,
            "gh: Forbidden (HTTP 403)" to GitHubRepositoryLoadFailure.FORBIDDEN,
            "gh: API rate limit exceeded (HTTP 403)" to GitHubRepositoryLoadFailure.RATE_LIMIT,
        )) {
            var calls = 0
            try {
                GitHubRepositoryPages.load(fetchPage = { calls++; GitHubCliResponse(1, "[]", message) })
                fail("Expected CLI failure")
            } catch (error: GitHubRepositoryLoadException) {
                assertEquals(kind, error.kind)
            }
            assertEquals(1, calls)
        }
    }
}
