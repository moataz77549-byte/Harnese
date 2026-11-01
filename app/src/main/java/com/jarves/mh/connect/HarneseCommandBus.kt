package com.jarves.mh.connect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Typed command entry point shared by Android UI and external integrations. */
sealed interface HarneseCommand {
    data object ListProjects : HarneseCommand
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

data class HarneseCommandResult(val ok: Boolean, val text: String) {
    companion object {
        fun success(text: String) = HarneseCommandResult(true, text)
        fun error(text: String) = HarneseCommandResult(false, text)
    }
}
fun interface HarneseCommandHandler {
    suspend fun execute(command: HarneseCommand): HarneseCommandResult
}

/**
 * The ViewModel owns the actual execution. A background service never stores
 * or calls a ViewModel reference; unavailable UI is explicitly reported.
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
