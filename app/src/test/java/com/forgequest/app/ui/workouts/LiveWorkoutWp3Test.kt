package com.forgequest.app.ui.workouts

import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.domain.SetType
import com.forgequest.app.ui.components.KeypadDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWorkoutWp3Test {
    private var id = 0L
    private fun set(
        pos: Int,
        idx: Int,
        restSeconds: Int = 90,
        setType: SetType = SetType.WORKING,
        done: Boolean = false,
        block: String = "MAIN",
        circuit: Boolean = false,
    ) = SetEntity(
        id = ++id,
        sessionId = 1,
        exerciseId = "e$pos",
        itemPosition = pos,
        block = block,
        circuit = circuit,
        setIndex = idx,
        targetLabel = "",
        restSeconds = restSeconds,
        setType = setType.name,
        done = done,
        doneAt = if (done) 100L else null,
    )

    @Test
    fun groupsSupersetItemsIntoCircuitAndSinglesBackToStraight() {
        val sA1 = set(0, 0)
        val sA2 = set(1, 0)
        val sSingle = set(2, 0)
        val sets = listOf(sA1, set(0, 1), sA2, set(1, 1), sSingle)
        val groups = group(sets) { pos ->
            when (pos) {
                0, 1 -> 1
                2 -> 2 // lone item in supersetGroup 2 falls back to Straight
                else -> null
            }
        }
        assertEquals(2, groups.size)
        val superset = groups[0] as Group.Circuit
        assertEquals(1, superset.supersetGroup)
        assertEquals(listOf(0, 1), superset.positions)
        assertEquals(listOf(0, 1), superset.rounds[0].map { it.itemPosition })

        val lone = groups[1] as Group.Straight
        assertEquals(2, lone.position)
    }

    @Test
    fun effectiveRestHandlesWarmupRestPauseAndSupersetAndCircuitTransition() {
        val a1r1 = set(0, 0, restSeconds = 90)
        val a2r1 = set(1, 0, restSeconds = 90)
        val warmup = set(2, 0, restSeconds = 90, setType = SetType.WARMUP)
        val restPause = set(2, 1, restSeconds = 90, setType = SetType.RESTPAUSE)
        val c1 = set(3, 0, restSeconds = 30, block = "Circuit A", circuit = true)
        val c2 = set(4, 0, restSeconds = 30, block = "Circuit A", circuit = true)
        val c3 = set(5, 0, restSeconds = 30, block = "Circuit A", circuit = true)
        val groups = group(listOf(a1r1, a2r1, warmup, restPause, c1, c2, c3)) { pos ->
            if (pos == 0 || pos == 1) 1 else null
        }

        // Warm-up skips auto-rest; rest-pause uses fixed 15 s preset
        assertEquals(0, effectiveRestSeconds(warmup, groups))
        assertEquals(15, effectiveRestSeconds(restPause, groups))

        // Non-last member in superset/circuit gets 0s (short transition); last member gets full rest
        assertEquals(0, effectiveRestSeconds(a1r1, groups))
        assertEquals(90, effectiveRestSeconds(a2r1, groups))
        assertEquals(EffectiveRest(0, isShortTransition = true), effectiveRest(a1r1, groups))
        assertEquals(EffectiveRest(90, isShortTransition = false), effectiveRest(a2r1, groups))
        assertEquals(EffectiveRest(15, isShortTransition = false), effectiveRest(restPause, groups))
        assertEquals(EffectiveRest(0, isShortTransition = false), effectiveRest(warmup, groups))
        assertEquals(EffectiveRest(0, isShortTransition = true), effectiveRest(c1, groups))
        assertEquals(EffectiveRest(0, isShortTransition = true), effectiveRest(c2, groups))
        assertEquals(EffectiveRest(30, isShortTransition = false), effectiveRest(c3, groups))
    }

    @Test
    fun holdCueEventEmitsReadyStartHalfwayCountdownAndDone() {
        assertEquals(HoldCueEvent.READY_3, holdCueEvent(-3, 20))
        assertEquals(HoldCueEvent.READY_1, holdCueEvent(-1, 20))
        assertEquals(HoldCueEvent.START, holdCueEvent(0, 20))
        assertEquals(HoldCueEvent.HALFWAY, holdCueEvent(10, 20))
        assertEquals(HoldCueEvent.COUNT_3, holdCueEvent(17, 20))
        assertEquals(HoldCueEvent.COUNT_1, holdCueEvent(19, 20))
        assertEquals(HoldCueEvent.DONE, holdCueEvent(20, 20))
        // Short holds under 10s omit HALFWAY
        assertNull(holdCueEvent(4, 8))
    }

    @Test
    fun keypadDraftHandlesDigitReplaceDecimalDeltasAndLastTime() {
        val initial = KeypadDraft("60", fresh = true)
        val typed = initial.pressDigit('8', decimals = 2).pressDigit('2', decimals = 2)
            .pressDecimal(2).pressDigit('5', decimals = 2)
        assertEquals("82.5", typed.text)
        assertEquals(82.5f, typed.parsed() ?: 0f, 0.001f)

        val bumped = typed.applyDelta(2.5f, fallback = 60f, min = 0f, max = 999f, decimals = 2)
        assertEquals("85", bumped.text)

        val clamped = bumped.applyDelta(2000f, fallback = 60f, min = 0f, max = 999f, decimals = 2)
        assertEquals("999", clamped.text)

        val erased = typed.pressBackspace().pressBackspace()
        assertEquals("82", erased.text)

        val exact = initial.setExact(77.5f, min = 0f, max = 999f, decimals = 2)
        assertEquals("77.5", exact.text)
        assertTrue(exact.isValid(0f, 999f))
    }
}
