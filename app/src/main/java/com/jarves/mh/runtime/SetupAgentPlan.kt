package com.jarves.mh.runtime

import com.jarves.mh.model.AgentKind

/** Core and toolchains are prepared first, then each selected network agent once. */
internal fun setupAgentPlan(primary: AgentKind, includeCodex: Boolean, offlineCore: Boolean): List<AgentKind> =
    buildList {
        if (!offlineCore || primary == AgentKind.CODEX) add(primary)
        if (includeCodex && primary != AgentKind.CODEX) add(AgentKind.CODEX)
    }

/** A ready core is not a completed first-run setup if a later agent failed. */
internal fun setupNeedsCompletion(
    coreReady: Boolean,
    onboardingComplete: Boolean,
    runtimeSetupComplete: Boolean,
    status: RuntimeSetupStatus,
): Boolean = !coreReady || (!runtimeSetupComplete &&
    (!onboardingComplete || status in setOf(RuntimeSetupStatus.ERROR, RuntimeSetupStatus.CANCELLED, RuntimeSetupStatus.RUNNING)))
