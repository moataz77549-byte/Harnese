package com.jarves.mh.ui

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun writeRuntimeRecord(destination: File, content: String) {
    val directory = requireNotNull(destination.parentFile)
    check(directory.isDirectory || directory.mkdirs())
    val temporary = File.createTempFile(".record-", ".tmp", directory)
    try {
        temporary.outputStream().use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        temporary.delete()
    }
}
