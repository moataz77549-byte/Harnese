package com.jarves.mh.ui

import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.ChatAttachment
import org.json.JSONArray
import org.json.JSONObject

internal fun terminalHistoryName(projectId: String, slot: Int): String {
    require(slot in 0..2)
    return if (slot == 0) "$projectId.json" else "$projectId-slot-$slot.json"
}

internal fun encodeQueuedFollowUps(queue: List<QueuedFollowUp>): String = JSONArray().apply {
    queue.forEach { item ->
        put(JSONObject()
            .put("id", item.id).put("project", item.projectId).put("chat", item.chatId)
            .put("agent", item.agentKind.stableId).put("prompt", item.prompt)
            .put("attachments", JSONArray().apply {
                item.attachments.forEach { attachment ->
                    put(JSONObject().put("id", attachment.id).put("name", attachment.displayName)
                        .put("path", attachment.relativePath).put("mime", attachment.mimeType)
                        .put("size", attachment.sizeBytes))
                }
            }))
    }
}.toString()

internal fun readQueuedFollowUps(raw: String?): List<QueuedFollowUp> = runCatching {
    val array = JSONArray(raw ?: "[]")
    (0 until array.length()).mapNotNull { index ->
        val item = array.getJSONObject(index)
        val kind = AgentKind.entries.firstOrNull { it.stableId == item.optString("agent") } ?: return@mapNotNull null
        val files = item.getJSONArray("attachments")
        QueuedFollowUp(id = item.getString("id"), projectId = item.getString("project"), chatId = item.getString("chat"),
            agentKind = kind, prompt = item.getString("prompt"),
            attachments = (0 until files.length()).map { n ->
                val file = files.getJSONObject(n)
                ChatAttachment(id = file.getString("id"), displayName = file.getString("name"),
                    relativePath = file.getString("path"), mimeType = file.getString("mime"), sizeBytes = file.getLong("size"))
            })
    }
}.getOrDefault(emptyList())
