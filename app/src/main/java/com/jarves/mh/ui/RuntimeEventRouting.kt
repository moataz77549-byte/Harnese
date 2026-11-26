package com.jarves.mh.ui

import com.jarves.mh.model.RuntimeEvent

/**
 * A session must establish its own identifier before it can publish output.
 * The only exception is a launch failure raised by the request owner itself.
 */
internal fun acceptsRuntimeEvent(
    activeSessionId: String?,
    event: RuntimeEvent,
    allowUnstartedFailure: Boolean = false,
): Boolean {
    if (event is RuntimeEvent.SessionStarted) return activeSessionId == null
    if (activeSessionId == null) return allowUnstartedFailure && event is RuntimeEvent.SessionFailed
    return event.sessionId == activeSessionId
}
