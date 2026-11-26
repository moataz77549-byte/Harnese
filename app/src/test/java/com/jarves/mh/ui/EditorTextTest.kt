package com.jarves.mh.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorTextTest {
    @Test fun preserveDeliberateFinalBlankLinesAndMissingFinalNewline() {
        assertEquals("مرحبا\n\n", editorTextForSave("مرحبا\n\n", "\n"))
        assertEquals("last", editorTextForSave("last", "\r\n"))
    }

    @Test fun preserveCrlfWithoutDoublingOrRemovingBlankLines() {
        assertEquals("a\r\nb\r\n\r\n", editorTextForSave("a\r\nb\n\n", "\r\n"))
    }
}
