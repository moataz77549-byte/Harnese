package com.jarves.mh.connect

import org.junit.Assert.*
import org.junit.Test

class TelegramContentPolicyTest {
    @Test fun removesCredentialsAndPrivateKeys() {
        val raw = "answer\nAPI_KEY=secret\npostgresql://owner:password@host/db\n.env contents\nsk-abcdefghijklmnopqrstuvwxyz123456\n-----BEGIN PRIVATE KEY-----\nprivate-data\n-----END PRIVATE KEY-----"
        val safe = TelegramContentPolicy.redact(raw)
        assertTrue(safe.contains("answer"))
        listOf("=secret", "owner:", "private-data", "sk-abc").forEach { assertFalse(safe.contains(it)) }
    }
    @Test fun splitsWithoutLossOrBrokenEmoji() {
        val text = "abc😀".repeat(5000)
        val parts = TelegramContentPolicy.chunks(text)
        assertEquals(text, parts.joinToString(""))
        assertTrue(parts.all { it.length <= 3500 && !it.last().isHighSurrogate() })
    }
    @Test fun emptyTextHasValidTelegramBody() { assertEquals(listOf("—"), TelegramContentPolicy.chunks("")) }
}
