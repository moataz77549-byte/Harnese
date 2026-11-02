package com.jarves.mh.connect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Typed command entry point shared by Android UI and external integrations. */
sealed interface HarneseCommand {
    data class HubOperation(val projectId: String?, val name: String, val argument: String = "") : HarneseCommand
    data object ListProjects : HarneseCommand
    data class CreateProject(val name: String) : HarneseCommand
    data class ListFiles(val projectId: String) : HarneseCommand
    data class ReadMessages(val projectId: String, val chatId: String) : HarneseCommand
    data class ListChats(val projectId: String) : HarneseCommand
    data class ProjectStatus(val projectId: String?) : HarneseCommand
    data class OpenProject(val projectId: String) : HarneseCommand
    data class SelectChat(val projectId: String, val chatId: String) : HarneseCommand
    data class NewChat(val projectId: String) : HarneseCommand
    data class SendPrompt(val projectId: String, val chatId: String, val prompt: String) : HarneseCommand
    data class RejectPendingTool(val approvalId: String) : HarneseCommand
    data object PendingApproval : HarneseCommand
    data object StopTask : HarneseCommand
}

data class HarneseCommandResult(val ok: Boolean, val text: String, val filePath: String? = null) {
    companion object {
        fun success(text: String) = HarneseCommandResult(true, text)
        fun error(text: String) = HarneseCommandResult(false, text)
    }
}
fun interface HarneseCommandHandler {
    suspend fun execute(command: HarneseCommand): HarneseCommandResult
}

/**
 * The application runtime owns execution. Services dispatch typed commands without
 * retaining an Activity or ViewModel. Handlers run on the main dispatcher.
 */
object HarneseCommandBus {
    private val guard = Any()
    private var owner: Any? = null
    private var handler: HarneseCommandHandler? = null
    fun register(newOwner: Any, newHandler: HarneseCommandHandler) = synchronized(guard) {
        owner = newOwner
        handler = newHandler
    }
    fun unregister(oldOwner: Any) = synchronized(guard) {
        if (owner === oldOwner) {
            owner = null
            handler = null
        }
    }
    suspend fun dispatch(command: HarneseCommand): HarneseCommandResult = withContext(Dispatchers.Main.immediate) {
        val target = synchronized(guard) { handler }
            ?: return@withContext HarneseCommandResult.error("افتح تطبيق Harnese لتفعيل التحكم بالمشاريع.")
        try { target.execute(command) }
        catch (_: Exception) { HarneseCommandResult.error("تعذر تنفيذ الأمر. راجع حالة التطبيق وأعد المحاولة.") }
    }
}
