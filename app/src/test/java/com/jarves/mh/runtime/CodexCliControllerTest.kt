package com.jarves.mh.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodexCliControllerTest {
    @Test fun acceptsOfficialDeviceChallenge() {
        val challenge = codexDeviceChallenge("Sign in at https://auth.openai.com/codex/device\nABCD-EFGH")
        assertEquals("ABCD-EFGH", challenge?.code)
        assertEquals("https://auth.openai.com/codex/device", challenge?.url)
    }

    @Test fun ignoresCodeWithoutOfficialPage() {
        assertNull(codexDeviceChallenge("Sign in at https://example.invalid/device\nABCD-EFGH"))
    }
}
