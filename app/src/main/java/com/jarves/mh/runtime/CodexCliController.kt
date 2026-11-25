package com.jarves.mh.runtime

import android.content.Context
import com.jarves.mh.ui.sanitizeTerminalOutput
import java.io.File
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
    suspend fun signOut(): Boolean = run(listOf("logout"), 30_000L) == 0
    suspend fun sandboxAvailable(): Boolean = run(listOf("sandbox", "linux", "/usr/bin/true"), 30_000L) == 0

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
