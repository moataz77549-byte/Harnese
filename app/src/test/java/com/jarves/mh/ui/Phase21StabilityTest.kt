package com.jarves.mh.ui

import com.jarves.mh.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class Phase21StabilityTest {
    @Test fun arabicAndEmojiStayIntactAcrossReadBoundaries() {
        val tail = TerminalOutputTail(32)
        val message = "خطأ 🙂"
        for (byte in message.encodeToByteArray()) {
            tail.append(byteArrayOf(byte), 1)
            assertFalse(tail.text().contains('\uFFFD'))
        }
        assertEquals(message, tail.text(final = true))
    }

    @Test fun oversizedChunksAndRepeatedWrapsKeepOnlyTheTail() {
        val tail = TerminalOutputTail(12)
        repeat(100) { tail.append("1234567890123456".encodeToByteArray(), 16) }
        tail.append("END".encodeToByteArray(), 3)
        assertEquals("890123456END", tail.text())
    }

    @Test fun anEvictedPartialCodePointIsNeverShown() {
        val tail = TerminalOutputTail(4)
        val bytes = "🙂abc".encodeToByteArray()
        tail.append(bytes, bytes.size)
        assertEquals("abc", tail.text())
    }

    @Test fun lateWriteCompletionCannotDropANewerChatSnapshot() {
        val pending = PendingChatWrites()
        val a = "project" to "chat-a"
        val b = "project" to "chat-b"
        val initial = listOf(ChatMessage(fromUser = true, text = "أ"))
        pending.put(a, initial)
        pending.put(b, initial)
        repeat(1000) { pending.put(a, listOf(ChatMessage(fromUser = false, text = it.toString()))) }
        pending.saved(a, initial)
        assertEquals(2, pending.snapshot().size)
        assertEquals("999", pending.get(a)?.single()?.text)
        pending.saved(b, initial)
        assertNull(pending.get(b))
        pending.put("other" to "chat-a", initial)
        pending.forgetProject("project")
        assertEquals(setOf("other" to "chat-a"), pending.snapshot().keys)
    }
}
