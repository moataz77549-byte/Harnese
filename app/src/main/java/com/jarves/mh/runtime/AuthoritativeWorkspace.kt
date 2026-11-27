package com.jarves.mh.runtime

import java.io.File
import java.nio.file.Files

/** Resolve existing project roots without recreating a missing workspace. */
internal object AuthoritativeWorkspace {
    fun resolve(filesDir: File, projectId: String, relativeRoot: String): File {
        require(projectId.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Invalid workspace ID" }
        val storage = File(filesDir, "workspaces")
        require(storage.isDirectory && !Files.isSymbolicLink(storage.toPath())) { "Workspace storage unavailable" }
        val base = File(storage, projectId)
        require(base.isDirectory && !Files.isSymbolicLink(base.toPath())) { "Workspace unavailable: $projectId" }
        val canonical = base.canonicalFile
        var cursor = canonical
        if (relativeRoot.isNotEmpty()) {
            require(!relativeRoot.startsWith('/') && '\\' !in relativeRoot)
            relativeRoot.split('/').forEach { part ->
                require(part.isNotBlank() && part != "." && part != "..")
                cursor = File(cursor, part)
                require(cursor.isDirectory && !Files.isSymbolicLink(cursor.toPath())) { "Selected project root unavailable: $relativeRoot" }
            }
        }
        require(cursor.canonicalFile.toPath().startsWith(canonical.toPath()))
        return cursor.canonicalFile
    }
}
