package com.jarves.mh.ui

import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.ChatAttachment
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRecoveryTest {
    @Test fun followUpsRoundTripWithProjectChatAgentAndAttachments() {
        val followUp = QueuedFollowUp(projectId = "project-a", chatId = "chat-a",
            agentKind = AgentKind.CODEX, prompt = "أكمل المشروع",
            attachments = listOf(ChatAttachment(displayName = "main.kt", relativePath = "attachments/chat-a/main.kt",
                mimeType = "text/plain", sizeBytes = 123)))
        assertEquals(listOf(followUp), readQueuedFollowUps(encodeQueuedFollowUps(listOf(followUp))))
    }

    @Test fun oldTerminalHistoryNameIsStableAndSlotsAreIndependent() {
        assertEquals("project.json", terminalHistoryName("project", 0))
        assertEquals(3, (0..2).map { terminalHistoryName("project", it) }.distinct().size)
    }
}
