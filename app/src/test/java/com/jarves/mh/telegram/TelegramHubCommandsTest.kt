package com.jarves.mh.telegram

import org.junit.Assert.*
import org.junit.Test

class TelegramHubCommandsTest {
    @Test fun recognizesControlAndEnvironmentCommands() {
        for (name in listOf("dashboard", "build", "apk", "runtimes", "python", "node", "typescript", "androidsdk", "runtimehealth", "disk", "diff")) {
            assertEquals(TelegramInstruction.Control(name, ""), TelegramInstructionParser.parse("/$name"))
        }
        assertEquals(TelegramInstruction.Ask("follow up"), TelegramInstructionParser.parse("/reply follow up"))
        assertEquals(TelegramInstruction.Stop, TelegramInstructionParser.parse("/cancel"))
    }
    @Test fun arbitraryShellAndOversizedInstructionsHaveNoAuthority() {
        assertEquals(TelegramInstruction.Unknown,TelegramInstructionParser.parse("rm -rf /"))
        assertEquals(TelegramInstruction.Unknown,TelegramInstructionParser.parse("/exec whoami"))
        assertEquals(TelegramInstruction.Unknown,TelegramInstructionParser.parse("/installruntime " + "a".repeat(100)))
    }
}
