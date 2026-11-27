package com.jarves.mh.ui

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeRecordTest {
    @Test fun recordsReplacePreviousBytesWithoutLeavingTemporaryFiles() {
        val directory = Files.createTempDirectory("nova-record").toFile()
        try {
            val destination = File(directory, "history.json")
            writeRuntimeRecord(destination, """{"output":"نتيجة سابقة"}""")
            writeRuntimeRecord(destination, """{"output":"جديد"}""")
            assertEquals("""{"output":"جديد"}""", destination.readText())
            assertTrue(directory.listFiles().orEmpty().contentEquals(arrayOf(destination)))
        } finally {
            directory.deleteRecursively()
        }
    }
}
