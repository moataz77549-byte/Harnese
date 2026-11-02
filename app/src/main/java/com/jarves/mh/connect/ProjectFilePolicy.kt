package com.jarves.mh.connect

import java.io.File
import java.nio.file.Files

object ProjectFilePolicy {
    fun allowedApk(root: File, path: String): File {
        val file = File(path).canonicalFile
        val base = root.canonicalFile
        require(file.toPath().startsWith(base.toPath()))
        val relative = file.relativeTo(base).invariantSeparatorsPath
        require(relative.contains("build/outputs/apk/") && file.extension == "apk" && file.isFile)
        var current = base
        relative.split('/').forEach { current = File(current, it); require(!Files.isSymbolicLink(current.toPath())) }
        java.util.zip.ZipFile(file).use { zip ->
            require(zip.getEntry("AndroidManifest.xml") != null) { "File is not an Android APK" }
        }
        require(file.length() in 1..49_000_000L) { "APK exceeds safe Telegram upload limit; use the phone share or save action." }
        return file
    }
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
