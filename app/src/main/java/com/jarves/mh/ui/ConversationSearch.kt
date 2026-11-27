package com.jarves.mh.ui

import com.jarves.mh.model.ChatMessage

/** Keep original list indices so locating a message never changes the timeline or its saved state. */
internal fun matchingConversationIndices(messages: List<ChatMessage>, query: String): List<Int> {
    val term = query.trim().take(256)
    if (term.isEmpty()) return emptyList()
    return messages.asSequence().withIndex()
        .filter { (_, message) ->
            message.text.contains(term, ignoreCase = true) ||
                message.attachments.any { it.displayName.contains(term, ignoreCase = true) } ||
                message.workItems.any { it.title.contains(term, ignoreCase = true) || it.detail.contains(term, ignoreCase = true) }
        }
        .map { it.index }.toList()
}
