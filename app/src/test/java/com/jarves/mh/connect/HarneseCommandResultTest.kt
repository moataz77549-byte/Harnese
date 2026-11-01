package com.jarves.mh.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarneseCommandResultTest {
    @Test fun successFlag() {
        val result = HarneseCommandResult.success("ok")
        assertTrue(result.ok)
        assertEquals("ok", result.text)
    }
    @Test fun deniedFlag() {
        assertFalse(HarneseCommandResult.error("denied").ok)
    }
}
