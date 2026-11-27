package com.jarves.mh.ui

import com.jarves.mh.model.ActivityItem
import com.jarves.mh.model.ChatAttachment
import com.jarves.mh.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusWorkspaceUiTest {
    @Test fun searchPreservesTimelineIndicesAndFindsArabic() {
        val messages = listOf(
            ChatMessage(fromUser = true, text = "بناء المشروع"),
            ChatMessage(fromUser = false, text = "Ready"),
            ChatMessage(fromUser = false, text = "تم بناء المشروع"),
        )
        assertEquals(listOf(0, 2), matchingConversationIndices(messages, " المشروع "))
        assertEquals(listOf(1), matchingConversationIndices(messages, "READY"))
        assertEquals(emptyList<Int>(), matchingConversationIndices(messages, " "))
    }

    @Test fun attachmentAndActivitySearchIsLocalAndDoesNotMutateMessages() {
        val attachment = ChatAttachment(displayName = "Build.log", relativePath = "build.log", mimeType = "text/plain", sizeBytes = 5)
        val message = ChatMessage(fromUser = false, text = "", attachments = listOf(attachment),
            workItems = listOf(ActivityItem(title = "Build", detail = "Gradle finished")))
        assertEquals(listOf(0), matchingConversationIndices(listOf(message), "build.log"))
        assertEquals(listOf(0), matchingConversationIndices(listOf(message), "gradle"))
        assertEquals(emptyList<Int>(), matchingConversationIndices(listOf(message), "missing"))
    }

    @Test fun terminalFramesCoalesceBacklogButAlwaysShowTheLastChunk() {
        assertTrue(terminalUiFrameDue(0, 10, 8192))
        assertFalse(terminalUiFrameDue(1000, 1010, 8192))
        assertTrue(terminalUiFrameDue(1000, 1100, 8192))
        assertTrue(terminalUiFrameDue(1000, 1010, 0))
    }
}
