package com.jarves.mh.ui

import com.jarves.mh.model.ChatMessage

/** One current immutable snapshot per dirty chat, including writes still in flight. */
internal class PendingChatWrites {
    private val pending = linkedMapOf<Pair<String, String>, List<ChatMessage>>()

    @Synchronized fun put(key: Pair<String, String>, messages: List<ChatMessage>) {
        pending[key] = messages
    }

    @Synchronized fun get(key: Pair<String, String>) = pending[key]
    @Synchronized fun snapshot() = pending.toMap()

    @Synchronized fun saved(key: Pair<String, String>, written: List<ChatMessage>) {
        if (pending[key] === written) pending.remove(key)
    }

    @Synchronized fun forgetProject(projectId: String) {
        pending.keys.removeAll { it.first == projectId }
    }
}
