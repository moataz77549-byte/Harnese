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
}
