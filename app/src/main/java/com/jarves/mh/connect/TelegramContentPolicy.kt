package com.jarves.mh.connect

/** Best-effort text redaction; content forwarding remains an explicit opt-in. */
object TelegramContentPolicy {
    fun redact(text: String): String = text
        .replace(Regex("(?is)```(?:env|dotenv)\\b.*?```"), "[REDACTED ENV FILE]")
        .replace(Regex("-----BEGIN [^-]*PRIVATE KEY-----[\\s\\S]*?-----END [^-]*PRIVATE KEY-----"), "[REDACTED PRIVATE KEY]")
        .replace(Regex("(?im)^.*(?:api[_-]?key|secret|password|passwd|token|authorization|database_url|postgres(?:ql)?://|\\.env).*$"), "[REDACTED SENSITIVE LINE]")
        .replace(Regex("\\b[0-9]{5,12}:[A-Za-z0-9_-]{30,}\\b"), "[REDACTED BOT TOKEN]")
        .replace(Regex("\\b(?:sk-|ghp_|github_pat_|gho_)[A-Za-z0-9_-]{16,}\\b"), "[REDACTED CREDENTIAL]")

    /** UTF-16 safe, plain text; no Markdown entities to escape. */
    fun chunks(text: String, limit: Int = 3500): List<String> {
        require(limit in 2..3900)
        if (text.isEmpty()) return listOf("—")
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + limit, text.length)
            if (end < text.length && text[end - 1].isHighSurrogate()) end--
            result += text.substring(start, end)
            start = end
        }
        return result
    }
}
