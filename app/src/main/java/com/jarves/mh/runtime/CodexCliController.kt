package com.jarves.mh.runtime

import android.content.Context
import com.jarves.mh.ui.sanitizeTerminalOutput
import com.jarves.mh.model.Project
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal data class CodexDeviceChallenge(val code: String) {
    val url: String get() = "https://auth.openai.com/codex/device"
}

internal fun codexDeviceChallenge(output: String): CodexDeviceChallenge? {
    if (!output.contains("https://auth.openai.com/codex/device")) return null
    val code = output.lineSequence().map(String::trim)
        .firstOrNull { it.matches(Regex("[A-Z0-9]{4,6}-[A-Z0-9]{4,6}")) } ?: return null
    return CodexDeviceChallenge(code)
}

/** Official CLI commands run in a separate diagnostics workspace with ephemeral output. */
internal class CodexCliController(context: Context, private val installer: RuntimeInstaller) {
    private val appContext = context.applicationContext

    suspend fun signedIn(): Boolean = run(listOf("login", "status"), 30_000L) == 0
    suspend fun signIn(onChallenge: suspend (CodexDeviceChallenge) -> Unit): Boolean =
        run(listOf("login", "--device-auth"), 15 * 60_000L, onChallenge) == 0
    suspend fun signOut(): Boolean = (run(listOf("logout"), 30_000L) == 0).also {
        if (it) CodexModelSettings(appContext).model = ""
    }
    suspend fun sandboxAvailable(): Boolean = run(listOf("sandbox", "linux", "/usr/bin/true"), 30_000L) == 0

    suspend fun models(): List<CodexModelOption> = withContext(Dispatchers.IO) {
        check(signedIn()) { "Sign in to Codex before loading models" }
        val runtime = installer.installedRuntime()
        val workspace = File(appContext.filesDir, "engine-diagnostics/codex-models").apply { mkdirs() }
        val output = File.createTempFile("codex-models-", ".jsonl", appContext.cacheDir)
        val process = installer.process(runtime.proot, runtime.rootfs, workspace, emptyMap(),
            listOf(RuntimeInstaller.CODEX_GUEST_PATH, "app-server", "--listen", "stdio://"),
            guestWorkspacePath = "/workspace/codex-models", emulateHardLinks = false, outputFile = output)
        try {
            val input = process.outputStream.bufferedWriter()
            fun send(message: JSONObject) { input.write(message.toString()); input.newLine(); input.flush() }
            send(JSONObject().put("id", 1).put("method", "initialize")
                .put("params", JSONObject().put("clientInfo", JSONObject()
                    .put("name", "harnese-nova").put("title", "Harnese Nova").put("version", "2.0.0-rc.3"))))
            var offset = 0L
            val pending = StringBuilder()
            var models: List<CodexModelOption>? = null
            withTimeout(45_000L) {
                while (models == null && (process.isAlive || output.length() > offset)) {
                    check(output.length() <= 4L * 1024 * 1024) { "Codex model catalog output exceeds limit" }
                    val available = output.length() - offset
                    if (available <= 0) { delay(120); continue }
                    val bytes = ByteArray(minOf(available, 16L * 1024).toInt())
                    val read = RandomAccessFile(output, "r").use { it.seek(offset); it.read(bytes) }
                    if (read <= 0) continue
                    offset += read
                    pending.append(bytes.copyOf(read).toString(Charsets.ISO_8859_1))
                    var newline = pending.indexOf("\n")
                    while (newline >= 0) {
                        val raw = pending.substring(0, newline).toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8)
                        pending.delete(0, newline + 1)
                        val response = runCatching { JSONObject(raw) }.getOrNull()
                        if (response?.optInt("id", -1) == 1 && response.has("result")) {
                            send(JSONObject().put("method", "initialized"))
                            send(JSONObject().put("id", 2).put("method", "model/list")
                                .put("params", JSONObject().put("limit", 100).put("includeHidden", false)))
                        } else if (response?.optInt("id", -1) == 2) {
                            models = parseCodexModels(response)
                        }
                        newline = pending.indexOf("\n")
                    }
                }
            }
            models.orEmpty().also { check(it.isNotEmpty()) { "Codex did not return model choices" } }
        } finally {
            runCatching { process.outputStream.close() }
            if (process.isAlive) process.destroyForcibly()
            output.delete()
        }
    }

    /**
     * A deliberately read-only project review. Provider sandboxing must work
     * before the source is sent to the coding service.
     */
    suspend fun reviewProject(project: Project): String = withContext(Dispatchers.IO) {
        check(sandboxAvailable()) { "Codex Linux sandbox is unavailable" }
        check(signedIn()) { "Codex account is not signed in" }
        val workspaceRoot = File(appContext.filesDir, "workspaces").canonicalFile
        val base = File(workspaceRoot, project.id).canonicalFile
        check(base.isDirectory && base.toPath().startsWith(workspaceRoot.toPath()) && base != workspaceRoot)
        val workspace = if (project.rootPath.isBlank()) base else File(base, project.rootPath).canonicalFile
        check(workspace.isDirectory && workspace.toPath().startsWith(base.toPath())) { "Invalid project workspace" }
        val runtime = installer.installedRuntime()
        val output = File.createTempFile("codex-review-", ".jsonl", appContext.cacheDir)
        var process: Process? = null
        try {
            val command = listOf(
                RuntimeInstaller.CODEX_GUEST_PATH, "--ask-for-approval", "never",
                "exec", "--sandbox", "read-only", "--ignore-user-config", "--ephemeral",
                "--skip-git-repo-check", "--json",
                "Review this authorized project for code correctness, regressions, and maintainability. " +
                    "Inspect source and the current diff if available. Do not modify files or run builds. " +
                    "Avoid credential files and report findings with locations and suggested corrections.",
            )
            val started = installer.process(runtime.proot, runtime.rootfs, workspace,
                emptyMap(), command, guestWorkspacePath = "/workspace/${project.slug}",
                emulateHardLinks = false, outputFile = output)
            process = started
            started.outputStream.close()
            withTimeout(10 * 60_000L) {
                while (started.isAlive) {
                    check(output.length() <= MAX_REVIEW_OUTPUT_BYTES) { "Codex output exceeds its size limit" }
                    delay(200)
                }
                check(started.waitFor() == 0) { "Codex could not complete the review" }
            }
            check(output.length() <= MAX_REVIEW_OUTPUT_BYTES) { "Codex output exceeds its size limit" }
            val review = output.useLines { lines ->
                lines.mapNotNull(::codexReviewMessage).joinToString("\n\n").takeLast(60_000)
            }
            check(review.isNotBlank()) { "Codex returned no review message" }
            review
        } finally {
            process?.let { if (it.isAlive) it.destroyForcibly() }
            output.delete()
        }
    }

    private suspend fun run(
        arguments: List<String>,
        timeoutMs: Long,
        onChallenge: (suspend (CodexDeviceChallenge) -> Unit)? = null,
    ): Int = withContext(Dispatchers.IO) {
        check(installer.installedCodexVersion() != null) { "Codex CLI is not installed" }
        val runtime = installer.installedRuntime()
        val workspace = File(appContext.filesDir, "engine-diagnostics/codex").apply { mkdirs() }
        val output = File.createTempFile("codex-session-", ".log", appContext.cacheDir)
        var process: Process? = null
        try {
            val started = installer.process(runtime.proot, runtime.rootfs, workspace,
                mapOf("NO_COLOR" to "1"), listOf(RuntimeInstaller.CODEX_GUEST_PATH) + arguments,
                guestWorkspacePath = "/workspace/codex-diagnostics",
                emulateHardLinks = false, outputFile = output)
            process = started
            withTimeout(timeoutMs) {
                var challengeReported = false
                while (started.isAlive) {
                    if (!challengeReported && onChallenge != null) {
                        val text = sanitizeTerminalOutput(output.readTailText(8 * 1024))
                        val next = codexDeviceChallenge(text)
                        if (next != null) {
                            onChallenge(next)
                            challengeReported = true
                        }
                    }
                    delay(200)
                }
                started.waitFor()
            }
        } finally {
            process?.let { if (it.isAlive) it.destroyForcibly() }
            runCatching { process?.outputStream?.close() }
            output.delete()
        }
    }
}

private const val MAX_REVIEW_OUTPUT_BYTES = 8L * 1024 * 1024

internal fun codexReviewMessage(line: String): String? {
    val event = runCatching { JSONObject(line) }.getOrNull() ?: return null
    if (event.optString("type") != "item.completed") return null
    val item = event.optJSONObject("item") ?: return null
    if (item.optString("type") != "agent_message") return null
    return item.optString("text").takeIf(String::isNotBlank)
}

internal data class CodexModelOption(val id: String, val title: String)

internal fun parseCodexModels(response: JSONObject): List<CodexModelOption> {
    val data = response.optJSONObject("result")?.optJSONArray("data") ?: return emptyList()
    return (0 until data.length()).mapNotNull { index ->
        val item = data.optJSONObject(index) ?: return@mapNotNull null
        if (item.optBoolean("hidden")) return@mapNotNull null
        val id = item.optString("model").ifBlank { item.optString("id") }
        if (id.isBlank() || id.length > 180) return@mapNotNull null
        CodexModelOption(id, item.optString("displayName").ifBlank { id }.take(120))
    }.distinctBy { it.id }
}

internal class CodexModelSettings(context: Context) {
    private val storage = context.getSharedPreferences("codex-engine-settings", Context.MODE_PRIVATE)
    var model: String
        get() = storage.getString("model", "").orEmpty()
        set(value) { storage.edit().putString("model", value).apply() }
}
