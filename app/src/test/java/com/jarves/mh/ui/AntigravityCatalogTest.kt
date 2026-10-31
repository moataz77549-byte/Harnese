package com.jarves.mh.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AntigravityCatalogTest {
    @Test fun parsesArgonAndClaudeFromLiveCatalog() {
        val result = parseAntigravityCliModels("""
            Available models:
              gemini-4-argon-high     Gemini 4 Argon (High)
              gemini-4-argon-medium   Gemini 4 Argon (Medium)
              gemini-4-argon-low      Gemini 4 Argon (Low)
              claude-opus-5-5-high    Claude Opus 5.5
              gemini-3.8-flash-medium Gemini 3.8 Flash
            Usage: agy models
            gemini-4-argon-high       Duplicate
        """.trimIndent())
        assertEquals(listOf(
            "gemini-4-argon-high",
            "gemini-4-argon-medium",
            "gemini-4-argon-low",
            "claude-opus-5-5-high",
            "gemini-3.8-flash-medium",
        ), result)
    }

    @Test fun ignoresHeadersWarningsAndUnrecognizedTokens() {
        assertEquals(emptyList<String>(), parseAntigravityCliModels("Available models:\nPlease sign in first\nFailed to list models"))
    }
}
