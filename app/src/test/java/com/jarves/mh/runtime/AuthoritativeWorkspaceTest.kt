package com.jarves.mh.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class AuthoritativeWorkspaceTest {
    @Test fun missingWorkspaceAndRootAreNeverCreated() {
        val root = Files.createTempDirectory("nova-workspace").toFile()
        try {
            val dir = File(root, "workspaces").apply { mkdirs() }
            assertTrue(runCatching { AuthoritativeWorkspace.resolve(root, "missing", "") }.isFailure)
            assertFalse(File(dir, "missing").exists())
            val original = File(dir, "project").apply { mkdirs() }
            assertTrue(runCatching { AuthoritativeWorkspace.resolve(root, "project", "nested") }.isFailure)
            assertFalse(File(original, "nested").exists())
            val nested = File(original, "nested").apply { mkdirs() }
            assertEquals(nested.canonicalFile, AuthoritativeWorkspace.resolve(root, "project", "nested"))
            assertTrue(runCatching { AuthoritativeWorkspace.resolve(root, "project", "../outside") }.isFailure)
        } finally { root.deleteRecursively() }
    }
    @Test fun symlinkAndSecretExportsAreExcluded() {
        val root = Files.createTempDirectory("nova-symlink").toFile()
        try {
            val project = File(root, "workspaces/project").apply { mkdirs() }
            val outside = File(root, "outside").apply { mkdirs() }
            Files.createSymbolicLink(File(project, "alias").toPath(), outside.toPath())
            assertTrue(runCatching { AuthoritativeWorkspace.resolve(root, "project", "alias") }.isFailure)
            for (path in listOf(".git/config", "app/.env.local", "keys/release.p12", ".pocketdev/imports/old.zip", "key.properties")) assertTrue(isPrivateProjectExportPath(path))
            assertFalse(isPrivateProjectExportPath("app/src/main/Main.kt"))
        } finally { root.deleteRecursively() }
    }
}
