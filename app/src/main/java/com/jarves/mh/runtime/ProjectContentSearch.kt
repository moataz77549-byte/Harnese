package com.jarves.mh.runtime

import java.io.File
import java.nio.file.Files

data class ProjectSearchHit(val path: String, val line: Int, val preview: String)
data class ProjectSearchResult(val hits: List<ProjectSearchHit>, val limited: Boolean)

internal fun searchProjectContent(workspace: File, query: String): ProjectSearchResult {
    val needle = query.trim()
    if (needle.isEmpty()) return ProjectSearchResult(emptyList(), false)
    val root = workspace.canonicalFile
    val ignored = setOf("build", "node_modules", ".gradle", "dist", "target", ".idea", ".git", ".pocketdev")
    val extensions = setOf("kt", "kts", "java", "xml", "gradle", "toml", "properties", "json", "yaml", "yml",
        "md", "txt", "py", "js", "jsx", "ts", "tsx", "html", "css", "c", "cpp", "h", "hpp", "rs", "go", "sh", "dart")
    var scanned = 0
    var bytes = 0L
    val hits = mutableListOf<ProjectSearchHit>()
    for (file in root.walkTopDown().maxDepth(24).onEnter { directory ->
        directory == root || (directory.name !in ignored && !Files.isSymbolicLink(directory.toPath()) &&
            !isPrivateProjectExportPath(directory.relativeTo(root).invariantSeparatorsPath))
    }) {
        if (!file.isFile || Files.isSymbolicLink(file.toPath()) || file.extension.lowercase() !in extensions) continue
        val relative = file.relativeTo(root).invariantSeparatorsPath
        if (isPrivateProjectExportPath(relative) || file.length() > 512 * 1024) continue
        if (++scanned > 2_000 || bytes + file.length() > 20L * 1024 * 1024) return ProjectSearchResult(hits, true)
        bytes += file.length()
        runCatching {
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for ((index, line) in lines.withIndex()) {
                    if (line.contains(needle, ignoreCase = true)) {
                        val offset = line.indexOf(needle, ignoreCase = true)
                        val start = (offset - 40).coerceAtLeast(0)
                        hits += ProjectSearchHit(relative, index + 1, line.substring(start).take(160))
                        if (hits.size >= 100) return ProjectSearchResult(hits.toList(), true)
                    }
                }
            }
        }
    }
    return ProjectSearchResult(hits, false)
}
