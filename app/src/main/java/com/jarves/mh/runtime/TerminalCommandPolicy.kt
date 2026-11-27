package com.jarves.mh.runtime

/** Only a simple package manager invocation may receive legacy noninteractive handling. */
internal fun isSimplePackageCommand(command: String): Boolean {
    val text = command.trim()
    if (text.any { it in "\n\r;&|<>`\$\\\"'" }) return false
    return Regex("^(?:sudo\\s+)?(?:apt|apt-get|dpkg)(?:\\s|$)").containsMatchIn(text)
}
