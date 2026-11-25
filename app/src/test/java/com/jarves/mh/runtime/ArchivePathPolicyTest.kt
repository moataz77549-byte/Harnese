package com.jarves.mh.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchivePathPolicyTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun rejectsTraversalAndAbsolutePaths() {
        val root = temp.newFolder("root")
        for (path in listOf("../outside", "/tmp/outside", "dir/../file", "dir\\file")) {
            assertTrue(runCatching { checkedArchiveChild(root, path) }.isFailure)
        }
    }

    @Test fun refusesWritesThroughParentLink() {
        val root = temp.newFolder("root")
        val outside = temp.newFolder("outside")
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        assertTrue(runCatching { checkedArchiveChild(root, "link/file") }.isFailure)
    }

    @Test fun replacingLeafLinkKeepsExternalFileIntact() {
        val root = temp.newFolder("root")
        val external = temp.newFile("external").apply { writeText("original") }
        val link = File(root, "leaf")
        Files.createSymbolicLink(link.toPath(), external.toPath())
        openArchiveFile(checkedArchiveChild(root, "leaf")).use { it.write("replacement".toByteArray()) }
        assertEquals("original", external.readText())
        assertEquals("replacement", link.readText())
    }

    @Test fun enforcesExpandedByteLimit() {
        val output = ByteArrayOutputStream()
        assertTrue(runCatching { copyArchiveBytes(ByteArrayInputStream(ByteArray(20)), output, 10) }.isFailure)
        assertEquals(0, output.size())
    }
}
