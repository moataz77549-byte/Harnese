package com.jarves.mh.ui

internal fun editorTextForSave(draft: String, lineEnding: String): String {
    val lf = draft.replace("\r\n", "\n").replace('\r', '\n')
    return if (lineEnding == "\r\n") lf.replace("\n", "\r\n") else lf
}
