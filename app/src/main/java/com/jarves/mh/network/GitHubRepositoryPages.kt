package com.jarves.mh.network

import java.io.IOException
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

internal enum class GitHubRepositoryLoadFailure {
    AUTH, FORBIDDEN, RATE_LIMIT, TEMPORARY, INVALID_RESPONSE, OTHER,
}

internal class GitHubRepositoryLoadException(val kind: GitHubRepositoryLoadFailure) :
    Exception(kind.name)

internal data class GitHubCliResponse(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
) {
    fun failure(): GitHubRepositoryLoadFailure {
        val message = stderr.lowercase(Locale.ROOT)
        return when {
            timedOut -> GitHubRepositoryLoadFailure.TEMPORARY
            "rate limit" in message || "http 429" in message -> GitHubRepositoryLoadFailure.RATE_LIMIT
            "http 401" in message || "bad credentials" in message ||
                "not logged in" in message || "gh auth login" in message ->
                GitHubRepositoryLoadFailure.AUTH
            "http 403" in message -> GitHubRepositoryLoadFailure.FORBIDDEN
            Regex("http 5[0-9]{2}").containsMatchIn(message) ||
                listOf("timeout", "timed out", "connection", "network", "unexpected eof", "tls", "temporary", "no such host")
                    .any { it in message } -> GitHubRepositoryLoadFailure.TEMPORARY
            else -> GitHubRepositoryLoadFailure.OTHER
        }
    }
}

/** Page-sized, projected JSON; no slurping, terminal formatting, or truncated output. */
internal object GitHubRepositoryPages {
    const val PAGE_SIZE = 100
    const val PROJECTION = "map({full_name: .full_name, clone_url: .clone_url, private: .private, default_branch: .default_branch, description: .description, updated_at: .updated_at})"
    private const val MAX_ATTEMPTS = 3

    fun endpoint(page: Int): String =
        "user/repos?visibility=all&affiliation=owner,collaborator,organization_member&sort=updated&direction=desc&per_page=$PAGE_SIZE&page=$page"

    fun load(
        fetchPage: (Int) -> GitHubCliResponse,
        onRetry: (Int) -> Unit = {},
    ): List<GitHubRepository> {
        val repositories = LinkedHashMap<String, GitHubRepository>()
        var page = 1
        while (true) {
            val items = fetchWithRetry(page, fetchPage, onRetry)
            val previousSize = repositories.size
            items.forEach { repositories[it.fullName] = it }
            if (items.size < PAGE_SIZE) return repositories.values.toList()
            // A repeated full page indicates a broken pagination implementation,
            // not the end of the account's repositories.
            if (repositories.size == previousSize) {
                throw GitHubRepositoryLoadException(GitHubRepositoryLoadFailure.INVALID_RESPONSE)
            }
            page++
        }
    }

    private fun fetchWithRetry(
        page: Int,
        fetchPage: (Int) -> GitHubCliResponse,
        onRetry: (Int) -> Unit,
    ): List<GitHubRepository> {
        for (attempt in 1..MAX_ATTEMPTS) {
            val failure = try {
                val response = fetchPage(page)
                if (response.exitCode != 0 || response.timedOut) {
                    response.failure()
                } else {
                    return parsePage(response.stdout)
                }
            } catch (_: IOException) {
                GitHubRepositoryLoadFailure.TEMPORARY
            } catch (_: JSONException) {
                GitHubRepositoryLoadFailure.INVALID_RESPONSE
            }
            if (attempt == MAX_ATTEMPTS ||
                failure !in setOf(GitHubRepositoryLoadFailure.TEMPORARY, GitHubRepositoryLoadFailure.INVALID_RESPONSE)
            ) {
                throw GitHubRepositoryLoadException(failure)
            }
            onRetry(attempt)
        }
        error("Unreachable retry state")
    }

    private fun JSONObject.textOrEmpty(key: String): String =
        if (isNull(key)) "" else optString(key)

    internal fun parsePage(output: String): List<GitHubRepository> {
        val tokener = JSONTokener(output)
        val array = tokener.nextValue() as? JSONArray ?: throw JSONException("Expected repository array")
        if (tokener.nextClean() != '\u0000' || array.length() > PAGE_SIZE) {
            throw JSONException("Invalid repository page")
        }
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val fullName = item.textOrEmpty("full_name").takeIf(String::isNotBlank)
                ?: throw JSONException("Missing repository name")
            GitHubRepository(
                fullName = fullName,
                cloneUrl = item.textOrEmpty("clone_url").ifBlank { "https://github.com/$fullName.git" },
                private = item.optBoolean("private"),
                defaultBranch = item.textOrEmpty("default_branch").ifBlank { "main" },
                description = item.textOrEmpty("description"),
                updatedAt = item.textOrEmpty("updated_at"),
            )
        }
    }
}
