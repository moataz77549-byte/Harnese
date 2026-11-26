package com.jarves.mh.ui

import com.jarves.mh.model.RuntimeEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEventRoutingTest {
    @Test fun startsOnlyOneSessionAndRejectsStaleEvents() {
        assertTrue(acceptsRuntimeEvent(null, RuntimeEvent.SessionStarted("new")))
        assertFalse(acceptsRuntimeEvent("new", RuntimeEvent.SessionStarted("old")))
        assertFalse(acceptsRuntimeEvent("new", RuntimeEvent.SessionCompleted("old")))
        assertTrue(acceptsRuntimeEvent("new", RuntimeEvent.SessionCompleted("new")))
    }

    @Test fun cannotAcceptACompletionWithoutAnEstablishedSession() {
        assertFalse(acceptsRuntimeEvent(null, RuntimeEvent.SessionCompleted("old")))
        assertFalse(acceptsRuntimeEvent(null, RuntimeEvent.SessionFailed("old", "error")))
        assertTrue(acceptsRuntimeEvent(null, RuntimeEvent.SessionFailed("new", "launch error"), true))
    }
}
