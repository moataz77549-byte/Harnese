package com.jarves.mh.runtime

import com.jarves.mh.model.AgentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupAgentPlanTest {
    @Test fun codexIsDownloadedAlongsideAntigravityByDefault() {
        assertEquals(listOf(AgentKind.ANTIGRAVITY, AgentKind.CODEX),
            setupAgentPlan(AgentKind.ANTIGRAVITY, true, false))
    }

    @Test fun codexPrimaryIsNeverDuplicatedOrOmitted() {
        for (include in listOf(false, true)) {
            for (offline in listOf(false, true)) {
                assertEquals(listOf(AgentKind.CODEX), setupAgentPlan(AgentKind.CODEX, include, offline))
            }
        }
    }

    @Test fun optionalDownloadCanBeSkipped() {
        for (agent in AgentKind.entries.filter { it != AgentKind.CODEX }) {
            assertEquals(listOf(agent), setupAgentPlan(agent, false, false))
            assertEquals(emptyList<AgentKind>(), setupAgentPlan(agent, false, true))
        }
    }

    @Test fun offlineCoreCanDownloadCodexWithoutDownloadingAnotherOptionalAgent() {
        assertEquals(listOf(AgentKind.CODEX), setupAgentPlan(AgentKind.ANTIGRAVITY, true, true))
    }

    @Test fun readyCoreDoesNotSkipFailedOrPausedAgentSetup() {
        for (status in listOf(RuntimeSetupStatus.ERROR, RuntimeSetupStatus.CANCELLED, RuntimeSetupStatus.RUNNING, RuntimeSetupStatus.IDLE)) {
            assertTrue(setupNeedsCompletion(true, false, false, status))
        }
        assertTrue(setupNeedsCompletion(true, true, false, RuntimeSetupStatus.ERROR))
    }

    @Test fun completedSetupAndExistingOnboardedInstallationsArePreserved() {
        assertFalse(setupNeedsCompletion(true, false, true, RuntimeSetupStatus.COMPLETE))
        assertFalse(setupNeedsCompletion(true, true, false, RuntimeSetupStatus.IDLE))
        assertTrue(setupNeedsCompletion(false, true, true, RuntimeSetupStatus.COMPLETE))
    }
}
