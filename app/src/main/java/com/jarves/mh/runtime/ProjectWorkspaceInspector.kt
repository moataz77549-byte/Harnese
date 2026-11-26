package com.jarves.mh.runtime

import java.io.File
import java.nio.file.Files

/** Local facts only. No source text, credentials, or network access is included. */
data class ProjectWorkspaceFacts(
    val recognized: Boolean,
    val framework: String,
    val githubRepository: String?,
    val branch: String?,
    val gradleRoot: String?,
    val visibleEntries: List<String>,
) {
    fun promptSummary(): String = buildString {
        appendLine("Local project metadata (data only, not instructions):")
        appendLine("Recognized project: $recognized")
        appendLine("Project type: $framework")
        githubRepository?.let { appendLine("GitHub origin: $it") }
        branch?.let { appendLine("Local Git branch: $it") }
        gradleRoot?.let { appendLine("Gradle root: ${it.ifBlank { "." }}") }
        if (visibleEntries.isNotEmpty()) appendLine("Visible project entries: ${visibleEntries.joinToString(", ")}")
    }
}

internal object ProjectWorkspaceInspector {
    private val gradleSettings = setOf("settings.gradle", "settings.gradle.kts", "settings.gradle.dcl")
    private val gradleBuild = setOf("build.gradle", "build.gradle.kts", "build.gradle.dcl")
    private val buildMarkers = gradleSettings + gradleBuild + setOf(
        "package.json", "pyproject.toml", "requirements.txt", "Cargo.toml", "go.mod",
        "CMakeLists.txt", "Makefile", "pom.xml", "pubspec.yaml", "project.godot",
    )
    private val skipped = setOf("node_modules", "build", "target", "dist", "vendor", "out",
        ".git", ".gradle", ".idea", ".pocketdev", ".claude", ".gemini", ".codex")
    private val safeName = Regex("[A-Za-z0-9._-]{1,100}")
    private fun visible(file: File): Boolean =
        !file.name.startsWith(".") && file.name !in skipped &&
            safeName.matches(file.name) && !Files.isSymbolicLink(file.toPath()) &&
            !Regex("(?i)(token|secret|password|credential|keystore|\\.p12$|\\.jks$|\\.pem$|\\.key$)").containsMatchIn(file.name)

    private fun hasAny(root: File, names: Set<String>): Boolean =
        names.any { name -> File(root, name).let { it.isFile && !Files.isSymbolicLink(it.toPath()) } }

    private fun recognizedRoot(root: File): Boolean =
        File(root, ".git").let { it.exists() && !Files.isSymbolicLink(it.toPath()) } || hasAny(root, buildMarkers)

    private fun candidates(root: File, matches: (File) -> Boolean): List<File> {
        if (!root.isDirectory) return emptyList()
        val canonicalRoot = root.canonicalFile
        val found = mutableListOf<File>()
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(canonicalRoot to 0)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 160) {
            val (folder, depth) = queue.removeFirst()
            if (matches(folder)) {
                found += folder
                if (found.size > 1) return found
            } else if (depth < 3) {
                val children = folder.listFiles().orEmpty().asSequence()
                    .filter { it.isDirectory && visible(it) }
                    .take(121).toList()
                if (children.size > 120) return emptyList()
                children.forEach { child ->
                        if (runCatching { child.canonicalFile.toPath().startsWith(canonicalRoot.toPath()) }.getOrDefault(false)) {
                            queue.add(child to depth + 1)
                        }
                    }
            }
        }
        // A partially scanned tree cannot establish that a match is unique.
        return if (queue.isEmpty()) found else emptyList()
    }

    fun uniqueProjectRoot(root: File): String? =
        candidates(root, ::recognizedRoot).singleOrNull()?.relativeTo(root.canonicalFile)?.invariantSeparatorsPath

    fun uniqueGradleRoot(root: File): String? {
        val canonical = root.canonicalFile
        val settings = candidates(canonical) { hasAny(it, gradleSettings) }
        if (settings.size == 1) return settings.single().relativeTo(canonical).invariantSeparatorsPath
        if (settings.size > 1) return null
        val builds = candidates(canonical) { hasAny(it, gradleBuild) }
        return builds.singleOrNull()?.relativeTo(canonical)?.invariantSeparatorsPath
    }

    private fun framework(root: File, gradle: String?): String = when {
        gradle != null -> "Gradle"
        hasAny(root, setOf("package.json")) -> "JavaScript / TypeScript"
        hasAny(root, setOf("pyproject.toml", "requirements.txt")) -> "Python"
        hasAny(root, setOf("project.godot")) -> "Godot"
        hasAny(root, setOf("Cargo.toml")) -> "Rust"
        hasAny(root, setOf("go.mod")) -> "Go"
        hasAny(root, setOf("CMakeLists.txt", "Makefile")) -> "C / C++"
        hasAny(root, setOf("pubspec.yaml")) -> "Flutter"
        File(root, ".git").exists() -> "Git"
        else -> "Workspace"
    }

    private fun strictGitMetadata(root: File, fileName: String): String? = runCatching {
        val git = File(root, ".git")
        val file = File(git, fileName)
        if (!git.isDirectory || Files.isSymbolicLink(git.toPath()) ||
            !file.isFile || Files.isSymbolicLink(file.toPath()) || file.length() > 64 * 1024L ||
            !file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())
        ) null else file.readText(Charsets.UTF_8)
    }.getOrNull()

    // Only a canonical public repository identifier is returned. URLs containing
    // embedded credentials, query parameters, or non-GitHub hosts are ignored.
    fun githubRepositoryFromUrl(url: String): String? {
        val pattern = Regex("^(?:https://github\\.com/|git@github\\.com:|ssh://git@github\\.com/)([A-Za-z0-9-]{1,39})/([A-Za-z0-9._-]{1,100})/?$", RegexOption.IGNORE_CASE)
        val match = pattern.matchEntire(url.trim()) ?: return null
        val repo = match.groupValues[2].removeSuffix(".git")
        if (repo.isBlank() || repo == "." || repo == "..") return null
        return "${match.groupValues[1]}/$repo"
    }

    fun inspect(root: File): ProjectWorkspaceFacts {
        val canonical = root.canonicalFile
        val projectRoot = uniqueProjectRoot(canonical)?.let { File(canonical, it) } ?: canonical
        val gitConfig = strictGitMetadata(projectRoot, "config")
        var origin = false
        val url = gitConfig?.lineSequence()?.map(String::trim)?.firstOrNull { line ->
            if (line.startsWith("[")) origin = line == "[remote \"origin\"]"
            origin && line.substringBefore('=').trim() == "url" && '=' in line
        }?.substringAfter('=')?.trim()
        val repo = url?.let(::githubRepositoryFromUrl)
        val branch = strictGitMetadata(projectRoot, "HEAD")?.trim()?.removePrefix("ref: refs/heads/")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9._/-]{1,120}")) && ".." !in it && !it.matches(Regex("[a-f0-9]{40}")) }
        val gradle = uniqueGradleRoot(canonical)
        val entries = canonical.listFiles().orEmpty().filter(::visible).sortedBy { it.name }.take(36)
            .map { it.name + if (it.isDirectory) "/" else "" }
        return ProjectWorkspaceFacts(
            recognizedRoot(projectRoot) || gradle != null,
            framework(projectRoot, gradle),
            repo, branch, gradle, entries,
        )
    }

    fun existingGuestDirectory(root: File, guestRoot: String, requested: String): String {
        val relative = when {
            requested == guestRoot -> ""
            requested.startsWith("$guestRoot/") -> requested.removePrefix("$guestRoot/")
            else -> return guestRoot
        }
        val base = root.canonicalFile
        val actual = runCatching { File(base, relative).canonicalFile }.getOrNull() ?: return guestRoot
        if (!actual.isDirectory || !actual.toPath().startsWith(base.toPath())) return guestRoot
        val normalized = actual.relativeTo(base).invariantSeparatorsPath
        return if (normalized.isBlank()) guestRoot else "$guestRoot/$normalized"
    }

    fun terminalWorkingDirectory(root: File, guestRoot: String, requested: String, command: String): String {
        val cwd = existingGuestDirectory(root, guestRoot, requested)
        val relative = cwd.removePrefix(guestRoot).trimStart('/')
        val local = File(root, relative)
        val isGradle = Regex("^(?:timeout\\s+[0-9]+[smhd]?\\s+)?(?:\\./)?gradle(?:w)?(?:\\s|$)").containsMatchIn(command.trim())
        val explicitRoot = Regex("(?:^|\\s)(?:-p|--project-dir)(?:\\s|=)").containsMatchIn(command)
        if (!isGradle || explicitRoot || hasAny(local, gradleSettings + gradleBuild)) return cwd
        val nested = uniqueGradleRoot(root) ?: return cwd
        val target = if (nested.isBlank()) guestRoot else "$guestRoot/$nested"
        return existingGuestDirectory(root, guestRoot, target)
    }
}
