package com.jarves.mh.ui

/** Coalesce bursts of UI-only terminal updates without changing process IO or final output. */
internal fun terminalUiFrameDue(lastFrameMillis: Long, nowMillis: Long, unreadBytes: Long): Boolean =
    lastFrameMillis == 0L || unreadBytes <= 0L || nowMillis - lastFrameMillis >= 100L
