package com.forgequest.app.data.prefs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiOnTest {
    @Test fun noChoiceWithKeyIsOn() = assertTrue(resolveAiOn(null, true))
    @Test fun noChoiceNoKeyIsOff() = assertFalse(resolveAiOn(null, false))
    @Test fun offWithKeyIsOff() = assertFalse(resolveAiOn(false, true))
    @Test fun onWithoutKeyIsOn() = assertTrue(resolveAiOn(true, false))
    @Test fun chatTabNeedsAiAndKey() = assertTrue(chatIsTab(true, "k"))
    @Test fun chatTabOffWithoutKey() = assertFalse(chatIsTab(true, " "))
    @Test fun chatTabOffWhenAiOff() = assertFalse(chatIsTab(false, "k"))
}
