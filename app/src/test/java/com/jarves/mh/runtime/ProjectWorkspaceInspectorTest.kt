package com.jarves.mh.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectWorkspaceInspectorTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun findsNestedGradleRootForExplicitTerminalCommand() {
        val root = temp.newFolder("workspace")
        val app = File(root, "game").apply { mkdir() }
        File(app, "settings.gradle.kts").writeText("")
        assertEquals("game", ProjectWorkspaceInspector.uniqueGradleRoot(root))
        assertEquals("/workspace/project/game", ProjectWorkspaceInspector.terminalWorkingDirectory(
            root, "/workspace/project", "/workspace/project", "timeout 15m gradle assembleDebug",
        ))
        assertEquals("/workspace/project", ProjectWorkspaceInspector.terminalWorkingDirectory(
            root, "/workspace/project", "/workspace/project", "gradle -p game assembleDebug",
        ))
        assertEquals("/workspace/project/game", ProjectWorkspaceInspector.existingGuestDirectory(
            root, "/workspace/project", "/workspace/project/game",
        ))
        assertEquals("/workspace/project", ProjectWorkspaceInspector.existingGuestDirectory(
            root, "/workspace/project", "/workspace/project/../outside",
        ))
    }

    @Test fun ambiguousBuildsDoNotSelectAnArbitraryRoot() {
        val root = temp.newFolder("multiple")
        listOf("one", "two").forEach { name ->
            File(root, name).apply { mkdir() }.resolve("settings.gradle").writeText("")
        }
        assertNull(ProjectWorkspaceInspector.uniqueGradleRoot(root))
        assertEquals("/workspace/project", ProjectWorkspaceInspector.terminalWorkingDirectory(
            root, "/workspace/project", "/workspace/project", "gradle assembleDebug",
        ))
    }

    @Test fun repositoryIdentityUsesOnlySafeLocalMetadata() {
        val root = temp.newFolder("repository")
        val git = File(root, ".git").apply { mkdir() }
        File(git, "config").writeText("[remote \"origin\"]\n\turl = https://github.com/owner/repo.git\n")
        File(git, "HEAD").writeText("ref: refs/heads/main\n")
        File(root, "package.json").writeText("{\"name\":\"example\"}")
        File(root, "private-token.txt").writeText("Sensitive test fixture")
        val facts = ProjectWorkspaceInspector.inspect(root)
        assertTrue(facts.recognized)
        assertEquals("owner/repo", facts.githubRepository)
        assertEquals("main", facts.branch)
        assertTrue(facts.visibleEntries.contains("package.json"))
        assertTrue(facts.visibleEntries.none { it.contains("token") })
        assertNull(ProjectWorkspaceInspector.githubRepositoryFromUrl("https://user@example.github.com/owner/repo"))
        assertNull(ProjectWorkspaceInspector.githubRepositoryFromUrl("https://user@github.com/owner/repo"))
        assertEquals("owner/repo", ProjectWorkspaceInspector.githubRepositoryFromUrl("git@github.com:owner/repo.git"))
        File(git, "HEAD").writeText("a".repeat(64))
        assertNull(ProjectWorkspaceInspector.inspect(root).branch)
    }

    @Test fun ArabicDirectoryNamesAreRecognized() {
        val root = temp.newFolder("arabic")
        val nested = File(root, "مشروع اللعبة").apply { mkdir() }
        File(nested, "settings.gradle.kts").writeText("")
        assertEquals("مشروع اللعبة", ProjectWorkspaceInspector.uniqueProjectRoot(root))
        assertEquals("مشروع اللعبة", ProjectWorkspaceInspector.uniqueGradleRoot(root))
    }
}
