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

    @Test fun editorDraftKeepsFingerprintAndUnicode() {
        val draft = EditorDraft("val اسم = 42", "original")
        assertEquals(draft, readEditorDraft(encodeEditorDraft(draft)))
    }

    @Test fun completionUsesExistingLocalIdentifiersAndCursorRange() {
        val text = "val customerName = 1\ncust"
        val completion = localEditorCompletion(text, text.length)
        assertEquals(listOf("customerName"), completion?.words)
        assertEquals("cust", text.substring(completion!!.start, completion.end))
    }

    @Test fun oldTerminalHistoryNameIsStableAndSlotsAreIndependent() {
        assertEquals("project.json", terminalHistoryName("project", 0))
        assertEquals(3, (0..2).map { terminalHistoryName("project", it) }.distinct().size)
    }
}
