package com.jarves.mh.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramInstructionParserTest {
    @Test fun acceptsBoundedPrompt() {
        assertEquals(TelegramInstruction.Ask("fix tests"), TelegramInstructionParser.parse("/ask fix tests"))
    }
    @Test fun rejectsBareChatOrNonCommands() {
        assertEquals(TelegramInstruction.Unknown, TelegramInstructionParser.parse("fix everything"))
        assertEquals(TelegramInstruction.Unknown, TelegramInstructionParser.parse("/ask"))
        assertEquals(TelegramInstruction.Unknown, TelegramInstructionParser.parse("/pair 123"))
    }
    @Test fun projectAndHistoryCommands() {
        assertEquals(TelegramInstruction.NewProject("New application"), TelegramInstructionParser.parse("/newproject New application"))
        assertEquals(TelegramInstruction.History, TelegramInstructionParser.parse("/history"))
        assertEquals(TelegramInstruction.Files, TelegramInstructionParser.parse("/files"))
    }
    @Test fun parsesBotMention() {
        assertEquals(TelegramInstruction.Projects, TelegramInstructionParser.parse("/projects@MyBot"))
        assertTrue(TelegramInstructionParser.parse("/pair 123456789") is TelegramInstruction.Pair)
    }
}
