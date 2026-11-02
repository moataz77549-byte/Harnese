package com.jarves.mh.connect

import java.io.File
import java.nio.file.Files

object ProjectFilePolicy {
    fun allowedTextFile(root: File, relative: String): File {
        require(relative.isNotBlank() && !File(relative).isAbsolute)
        val segments = relative.replace('\\', '/').split('/')
        require(segments.none { it.isBlank() || it == ".." || it.startsWith(".") || Regex("(?i).*(secret|credential|password|token|\\.pem|\\.key|\\.p12|\\.jks|\\.keystore).*").matches(it) })
        var current = root.canonicalFile
        for (part in segments) {
            current = File(current, part)
            require(!Files.isSymbolicLink(current.toPath()))
        }
        val file = current.canonicalFile
        require(file.toPath().startsWith(root.canonicalFile.toPath()) && file.isFile && file.length() <= 32 * 1024)
        val bytes = file.readBytes()
        require(bytes.none { it == 0.toByte() })
        return file
    }
}
