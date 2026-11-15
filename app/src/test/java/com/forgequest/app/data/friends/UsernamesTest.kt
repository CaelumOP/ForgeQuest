package com.forgequest.app.data.friends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Same cases as web forge-score.test.ts and worker validate rules. */
class UsernamesTest {
    @Test
    fun trimsStripsAtAndLowercases() {
        assertEquals("tariq_99", Usernames.normalize("  @Tariq_99 "))
    }

    @Test
    fun rejectsBadInput() {
        listOf("ab", "a".repeat(21), "has space", "ta-riq", ".abc", "abc.", "a..b", "admin", "ForgeQuestX", "@@abc", "\u212Aelvin")
            .forEach { assertNull(it, Usernames.normalize(it)) }
    }
}
