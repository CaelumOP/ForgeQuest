package com.forgequest.app.data.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskGeminiTest {
    @Test fun promptHasQuestionDataFormatAndNoDocs() {
        val p = AskGemini.prompt("DATA-BLOCK-123", "Should I deload?", arabic = false)
        assertTrue(p.contains("Should I deload?"))
        assertTrue(p.contains("DATA-BLOCK-123"))
        assertTrue(p.contains("FORGEQUEST-REPLY v1"))
        assertTrue(p.contains("Do not open, create or edit any Google Doc"))
        assertTrue(p.contains("in English"))
    }

    @Test fun arabicFlagChangesLanguage() {
        val p = AskGemini.prompt("d", "", arabic = true)
        assertTrue(p.contains("Modern Standard Arabic"))
        assertFalse(p.contains("in English"))
    }
}
