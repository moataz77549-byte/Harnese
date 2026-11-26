package com.jarves.mh.runtime

import android.content.Context
import com.jarves.mh.connect.HarneseEventBus
import com.jarves.mh.connect.SecureReviewApprovalService
import com.jarves.mh.model.ChatMessage
import com.jarves.mh.model.ChangeItem
import com.jarves.mh.model.ProjectKind
import com.jarves.mh.model.ProviderProfile
import com.jarves.mh.model.RuntimeEvent
import com.jarves.mh.model.ToolRequest
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Codex is selectable in the project chat. A verified local sandbox is required
 * for project tools. Edits occur in a private staging copy and require the
 * existing local credential-confirmed review workflow before application.
 */
class CodexRuntimeBridge(context: Context) : RuntimeBridge {
    private val app = context.applicationContext
    private val installer = RuntimeInstaller(app)
    private val controller = CodexCliController(app, installer)
    private val checkpoints = WorkspaceCheckpoints(app.filesDir, app)
    private val reviews = SecureReviewApprovalService.get(File(app.filesDir, "secure-reviews"))
    private val journal = HarneseEventBus.get(app)
    private val eventBus = MutableSharedFlow<RuntimeEvent>(extraBufferCapacity = 64)
    override val events = eventBus.asSharedFlow()
    @Volatile private var activeId: String? = null
    @Volatile private var process: Process? = null
    @Volatile private var stopped = false

    fun configureProjectRoot(projectId: String, rootPath: String) = checkpoints.configureProjectRoot(projectId, rootPath)

    private suspend fun emit(event: RuntimeEvent) {
        journal.runtime(event)
        eventBus.emit(event)
    }

    override suspend fun startSession(
        projectId: String, projectSlug: String, projectKind: ProjectKind, prompt: String,
        conversationHistory: List<ChatMessage>, provider: ProviderProfile,
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        activeId = id
        stopped = false
        journal.begin(id, projectId, "OpenAI Codex")
        emit(RuntimeEvent.SessionStarted(id))
        var staged: SecureReviewApprovalService.StagedRun? = null
        var output: File? = null
        try {
            check(installer.installedCodexVersion() != null) { "Install Codex from the agent screen." }
            check(controller.signedIn()) { "Sign in with your ChatGPT account in the Codex agent screen." }
            val selectedWorkspace = checkpoints.ensureWorkspace(projectId)
            val facts = runCatching { ProjectWorkspaceInspector.inspect(selectedWorkspace) }
                .getOrElse { ProjectWorkspaceFacts(false, "Workspace", null, null, null, emptyList()) }
            val actualProject = projectKind == ProjectKind.PROJECT || facts.recognized
            val sandboxAvailable = actualProject && controller.sandboxAvailable()
            val workspace = if (sandboxAvailable) {
                reviews.stage(projectId, selectedWorkspace, protectSensitive = true)
                    .also { staged = it }.workspace
            } else {
                File(app.filesDir, "engine-diagnostics/codex-chat").apply { mkdirs() }
            }
            if (stopped) error("Stopped by user")
            val runtime = installer.installedRuntime()
            val archive = File.createTempFile("codex-task-", ".jsonl", app.cacheDir)
            output = archive
            val mode = if (sandboxAvailable) "workspace-write" else "read-only"
            val instruction = if (sandboxAvailable) {
                "Work only with the authorized staged project copy. Produce ordinary code changes for local review. Do not use external services, deploy, or access credentials."
            } else {
                "Answer using the user's supplied prompt, conversation, and verified local metadata below. Provide code examples or suggested edits as text. Project file contents and tools are unavailable. Never claim to have built, edited, pushed, or inspected source files."
            }
            val history = conversationHistory.recentWithinCharacterBudget(18_000)
                .dropLast(1).joinToString("\n") { (if (it.fromUser) "User: " else "Assistant: ") + it.text }
            val projectContext = if (actualProject) facts.promptSummary() else "Quick chat; no recognized project files."
            val input = "$instruction\n\n$projectContext\n\nRecent conversation:\n$history\n\nRequest:\n$prompt"
            val args = buildList {
                addAll(listOf(RuntimeInstaller.CODEX_GUEST_PATH, "--ask-for-approval", "never",
                    "exec", "--sandbox", mode, "--ignore-user-config", "--ephemeral", "--skip-git-repo-check", "--json"))
                CodexModelSettings(app).model.takeIf(String::isNotBlank)?.let { addAll(listOf("--model", it)) }
                add("-")
            }
            if (!sandboxAvailable) emit(RuntimeEvent.RuntimeLog(id, "Codex", if (actualProject) {
                "Project identity detected. The local sandbox is unavailable; metadata-only assistance, no source files or project tools."
            } else "Quick chat. No recognized local project."))
            val running = installer.process(runtime.proot, runtime.rootfs, workspace, emptyMap(), args,
                guestWorkspacePath = if (sandboxAvailable) "/workspace/$projectSlug" else "/workspace/codex-chat",
                emulateHardLinks = false, outputFile = archive)
            process = running
            running.outputStream.bufferedWriter().use { it.write(input); it.newLine() }
            var offset = 0L
            val pending = StringBuilder()
            var answered = false
            suspend fun handle(line: String) {
                val event = runCatching { JSONObject(line) }.getOrNull() ?: return
                if (event.optString("type") == "turn.failed") error("Codex could not finish this request. Check account and engine diagnostics.")
                codexReviewMessage(line)?.let { message ->
                    if (answered) emit(RuntimeEvent.AssistantDelta(id, "\n\n"))
                    emit(RuntimeEvent.AssistantDelta(id, message))
                    answered = true
                }
                if (event.optString("type") == "item.started") {
                    val kind = event.optJSONObject("item")?.optString("type").orEmpty()
                    if (kind == "command_execution" || kind == "file_change") emit(RuntimeEvent.ToolStarted(id, "Codex", kind))
                }
            }
            withTimeout(15 * 60_000L) {
                while (running.isAlive || archive.length() > offset) {
                    check(!stopped) { "Stopped by user" }
                    check(archive.length() <= 16L * 1024 * 1024) { "Codex output exceeded its limit" }
                    val available = archive.length() - offset
                    if (available <= 0) { delay(120); continue }
                    val bytes = ByteArray(minOf(available, 16L * 1024).toInt())
                    val read = RandomAccessFile(archive, "r").use { it.seek(offset); it.read(bytes) }
                    if (read <= 0) continue
                    offset += read
                    // JSONL is UTF-8. Only decode complete lines so multibyte text is preserved.
                    pending.append(bytes.copyOf(read).toString(Charsets.ISO_8859_1))
                    var newline = pending.indexOf("\n")
                    while (newline >= 0) {
                        val line = pending.substring(0, newline).toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8)
                        pending.delete(0, newline + 1)
                        handle(line)
                        newline = pending.indexOf("\n")
                    }
                }
                if (pending.isNotBlank()) handle(pending.toString().toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8))
                check(!stopped && running.waitFor() == 0 && answered) { "Codex did not return a successful answer." }
            }
            staged?.let {
                val review = reviews.finish(it.id)
                if (review.entries.isNotEmpty()) {
                    journal.publish(
                        id = "review:${it.id}", project = projectId, chat = "", task = id,
                        kind = "review.pending", body = "Codex: ${review.entries.size} proposed files. Open Agent Hub to review and approve locally.",
                    )
                    emit(RuntimeEvent.AssistantDelta(id, "\n\nProposed changes: ${review.entries.size} files. Review them in Agent Hub before applying."))
                }
            }
            emit(RuntimeEvent.SessionCompleted(id))
        } catch (error: Throwable) {
            staged?.let { runCatching { reviews.abandon(it.id) } }
            emit(RuntimeEvent.SessionFailed(id, if (stopped) "Stopped by user" else error.message?.take(240).orEmpty().ifBlank { "Codex session failed" }))
        } finally {
            process?.let { if (it.isAlive) it.destroyForcibly() }
            process = null
            output?.delete()
            activeId = null
        }
        id
    }

    override suspend fun respondToApproval(request: ToolRequest, approved: Boolean) = Unit
    override suspend fun stopSession(sessionId: String) {
        if (activeId == sessionId) { stopped = true; process?.destroyForcibly() }
    }
    override suspend fun stopActiveSession() { activeId?.let { stopSession(it) } }
    override suspend fun undoLastChanges(projectId: String): Boolean = false
    override suspend fun acceptLastChanges(projectId: String) = Unit
    override suspend fun loadPendingChanges(projectId: String): List<ChangeItem> = emptyList()
    override suspend fun undoFileChange(projectId: String, path: String): Boolean = false
    override suspend fun acceptFileChange(projectId: String, path: String): Boolean = false
}
