package com.jarves.mh.telegram

/** Only explicit commands have remote execution authority; ordinary chat text is ignored. */
sealed interface TelegramInstruction {
    data object Help : TelegramInstruction
    data class Pair(val code: String) : TelegramInstruction
    data object Projects : TelegramInstruction
    data class NewProject(val name: String) : TelegramInstruction
    data object Files : TelegramInstruction
    data object History : TelegramInstruction
    data class Open(val projectIdOrPrefix: String) : TelegramInstruction
    data object Chats : TelegramInstruction
    data class Chat(val chatIdOrPrefix: String) : TelegramInstruction
    data object NewChat : TelegramInstruction
    data class Ask(val prompt: String) : TelegramInstruction
    data object Status : TelegramInstruction
    data object Stop : TelegramInstruction
    data object Pending : TelegramInstruction
    data class Reject(val approvalId: String) : TelegramInstruction
    data object Unknown : TelegramInstruction
}

object TelegramInstructionParser {
    fun parse(text: String): TelegramInstruction {
        val raw = text.trim()
        if (!raw.startsWith("/") || raw.length > 4500) return TelegramInstruction.Unknown
        val command = raw.substringBefore(' ').substringBefore('@').lowercase()
        val argument = raw.substringAfter(' ', "").trim()
        return when (command) {
            "/start", "/help" -> TelegramInstruction.Help
            "/pair" -> if (argument.matches(Regex("[0-9]{9}"))) TelegramInstruction.Pair(argument) else TelegramInstruction.Unknown
            "/projects" -> TelegramInstruction.Projects
            "/newproject" -> if (argument.length in 1..80) TelegramInstruction.NewProject(argument) else TelegramInstruction.Unknown
            "/files" -> TelegramInstruction.Files
            "/history" -> TelegramInstruction.History
            "/open" -> if (argument.length in 4..36) TelegramInstruction.Open(argument) else TelegramInstruction.Unknown
            "/chats" -> TelegramInstruction.Chats
            "/chat" -> if (argument.length in 4..36) TelegramInstruction.Chat(argument) else TelegramInstruction.Unknown
            "/newchat" -> TelegramInstruction.NewChat
            "/ask" -> if (argument.isNotBlank() && argument.length <= 4000) TelegramInstruction.Ask(argument) else TelegramInstruction.Unknown
            "/status" -> TelegramInstruction.Status
            "/stop" -> TelegramInstruction.Stop
            "/pending" -> TelegramInstruction.Pending
            "/reject" -> if (argument.length in 8..64) TelegramInstruction.Reject(argument) else TelegramInstruction.Unknown
            else -> TelegramInstruction.Unknown
        }
    }
}
