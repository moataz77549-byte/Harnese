package com.jarves.mh.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AntigravityDisplayNameTest {
    @Test fun displaysCorrectClaudeFourSixNames() {
        assertEquals("Claude Sonnet 4.6", formatAntigravityModelName("claude-sonnet-4-6"))
        assertEquals("Claude Opus 4.6 (Thinking)", formatAntigravityModelName("claude-opus-4-6-thinking"))
    }

    @Test fun displaysArgonVariants() {
        assertEquals("Gemini 4 Argon (High)", formatAntigravityModelName("gemini-4-argon-high"))
        assertEquals("Gemini 4 Argon (Medium)", formatAntigravityModelName("gemini-4-argon-medium"))
    }
}
