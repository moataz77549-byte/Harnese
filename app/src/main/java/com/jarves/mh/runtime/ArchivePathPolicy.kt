package com.jarves.mh.runtime

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption

internal fun checkedArchiveChild(root: File, relative: String): File {
    require(relative.isNotBlank() && !relative.startsWith('/') &&
        !relative.contains('\\') && relative.split('/').none { it == ".." }) {
        "Unsafe archive path"
    }
    val rootPath = root.canonicalFile.toPath()
    val file = File(root, relative)
    val parentPath = (file.parentFile ?: root).canonicalFile.toPath()
    require(file.toPath().toAbsolutePath().normalize().startsWith(root.toPath().toAbsolutePath().normalize()) &&
        (relative == "." || parentPath.startsWith(rootPath))) { "Archive path escapes runtime" }
    return file
}

internal fun createArchiveDirectory(target: File) {
    require(!Files.isSymbolicLink(target.toPath())) { "Archive directory is a symbolic link" }
    check(target.isDirectory || target.mkdirs()) { "Could not create archive directory" }
}

internal fun openArchiveFile(target: File): OutputStream {
    val path = target.toPath()
    // A runtime overlay may intentionally replace an existing leaf link.
    // Unlink it first so opening the replacement never follows its target.
    if (Files.isSymbolicLink(path)) Files.delete(path)
    return Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
}

internal fun archiveRegularFile(target: File): Boolean =
    Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS)

internal fun copyArchiveBytes(input: java.io.InputStream, output: OutputStream, limit: Long): Long {
    var written = 0L
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) return written
        written += count
        check(written <= limit) { "Archive expansion exceeds limit" }
        output.write(buffer, 0, count)
    }
}
