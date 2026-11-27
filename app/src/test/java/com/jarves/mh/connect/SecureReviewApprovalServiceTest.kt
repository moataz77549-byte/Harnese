package com.jarves.mh.connect

import java.nio.file.Files
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SecureReviewApprovalServiceTest {
    @Test fun editsStayStagedUntilApprovalAndCanRollBack() {
        val dir = Files.createTempDirectory("nova-review").toFile()
        try {
            val original = File(dir,"project").apply { mkdirs() }
            File(original,"test.txt").writeText("before")
            val service = SecureReviewApprovalService(File(dir,"reviews"))
            val stage = service.stage("project",original)
            File(stage.workspace,"test.txt").writeText("after")
            assertEquals("before",File(original,"test.txt").readText())
            assertEquals(1,service.finish(stage.id).entries.size)
            service.applyLocally(stage.id)
            assertEquals("after",File(original,"test.txt").readText())
            assertTrue(runCatching { service.applyLocally(stage.id) }.isFailure)
            service.rollbackLocally(stage.id)
            assertEquals("before",File(original,"test.txt").readText())
        } finally { dir.deleteRecursively() }
    }
    @Test fun concurrentEditsAndExpiredReviewsCannotApply() {
        val dir = Files.createTempDirectory("nova-review-conflict").toFile()
        try {
            val original = File(dir,"project").apply { mkdirs() }
            val file = File(original,"a.txt").apply { writeText("base") }
            val service = SecureReviewApprovalService(File(dir,"reviews"))
            val stage = service.stage("project",original)
            File(stage.workspace,"a.txt").writeText("proposal")
            service.finish(stage.id)
            file.writeText("user change")
            assertTrue(runCatching { service.applyLocally(stage.id) }.isFailure)
            assertEquals("user change",file.readText())
            file.writeText("base")
            val meta = File(dir,"reviews/${stage.id}/review.json")
            val json = org.json.JSONObject(meta.readText()).put("expires",1)
            meta.writeText(json.toString())
            assertTrue(runCatching { service.applyLocally(stage.id) }.isFailure)
        } finally { dir.deleteRecursively() }
    }
    @Test fun traversalAndSymlinksCannotExposeSecrets() {
        val dir=Files.createTempDirectory("nova-files").toFile()
        try {
            File(dir,"a.txt").writeText("safe")
            File(dir,".env").writeText("private")
            Files.createSymbolicLink(File(dir,"alias.txt").toPath(),File(dir,".env").toPath())
            for (name in listOf("../outside", ".env", "alias.txt")) assertTrue(runCatching { ProjectFilePolicy.allowedTextFile(dir,name) }.isFailure)
            assertEquals("safe",ProjectFilePolicy.allowedTextFile(dir,"a.txt").readText())
        } finally { dir.deleteRecursively() }
    }
    @Test fun protectedCodexStagingExcludesSensitiveFilesAndDoesNotDeleteThem() {
        val dir = Files.createTempDirectory("codex-stage").toFile()
        try {
            val original = File(dir, "project").apply { mkdirs() }
            File(original, ".env").writeText("private")
            File(original, "source.kt").writeText("before")
            val service = SecureReviewApprovalService(File(dir, "reviews"))
            val staged = service.stage("project", original, protectSensitive = true)
            assertFalse(File(staged.workspace, ".env").exists())
            File(staged.workspace, "source.kt").writeText("after")
            File(staged.workspace, ".env").writeText("new private file")
            assertEquals(listOf("source.kt"), service.finish(staged.id).entries.map { it.path })
            service.applyLocally(staged.id)
            assertEquals("private", File(original, ".env").readText())
            assertEquals("after", File(original, "source.kt").readText())
        } finally { dir.deleteRecursively() }
    }
    @Test fun `approved new nested files preserve large UTF8 bytes and survive reopening`() {
        val dir = Files.createTempDirectory("nova-review-utf8").toFile()
        try {
            val original = File(dir, "project").apply { mkdirs() }
            val service = SecureReviewApprovalService(File(dir, "reviews"))
            val stage = service.stage("project", original)
            val text = "مرحبا بالعالم\n".repeat(100000)
            File(stage.workspace, "app/src/source.txt").apply { parentFile.mkdirs(); writeText(text) }
            assertFalse(File(original, "app/src/source.txt").exists())
            service.finish(stage.id)
            val reopened = SecureReviewApprovalService(File(dir, "reviews"))
            reopened.applyLocally(stage.id)
            assertEquals(text, File(original, "app/src/source.txt").readText())
            assertEquals("applied", reopened.read(stage.id).state)
        } finally { dir.deleteRecursively() }
    }
    @Test fun selectedApprovalAndRollbackNeverModifyUnselectedFiles() {
        val dir = Files.createTempDirectory("nova-selection").toFile()
        try {
            val original = File(dir, "project").apply { mkdirs() }
            File(original, "a.txt").writeText("a")
            File(original, "b.txt").writeText("b")
            val service = SecureReviewApprovalService(File(dir, "reviews"))
            val stage = service.stage("p", original)
            File(stage.workspace, "a.txt").writeText("approved")
            File(stage.workspace, "b.txt").writeText("not approved")
            service.finish(stage.id)
            assertTrue(runCatching { service.applyLocally(stage.id, setOf("unknown.txt")) }.isFailure)
            service.applyLocally(stage.id, setOf("a.txt"))
            assertEquals("approved", File(original, "a.txt").readText())
            assertEquals("b", File(original, "b.txt").readText())
            File(original, "b.txt").writeText("later edit")
            SecureReviewApprovalService(File(dir, "reviews")).rollbackLocally(stage.id)
            assertEquals("a", File(original, "a.txt").readText())
            assertEquals("later edit", File(original, "b.txt").readText())
        } finally { dir.deleteRecursively() }
    }
}
