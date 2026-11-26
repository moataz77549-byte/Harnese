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

/** Keep a valid saved credential across network, quota and ordinary process failures. */
internal fun needsAntigravityReconnect(reason: String, credentialPresent: Boolean): Boolean =
    reason.contains("invalid_grant", ignoreCase = true) ||
        reason.contains("token revoked", ignoreCase = true) ||
        (!credentialPresent && (reason.contains("sign-in", ignoreCase = true) ||
            reason.contains("authentication", ignoreCase = true)))
