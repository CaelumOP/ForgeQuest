package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTextTest {
    private fun ex(id: String, kind: ExerciseKind, vararg m: Muscle) = ExerciseEntity(id, id, kind, m.toList(), emptyList())
    private fun item(pos: Int, id: String, block: String = "Main") = PlanItemEntity(dayId = 1, position = pos, exerciseId = id, block = block, circuit = false, sets = 3)
    private val exercises = listOf(
        ex("band", ExerciseKind.REPS, Muscle.SHOULDERS),
        ex("squat", ExerciseKind.WEIGHTED, Muscle.QUADS),
        ex("bench", ExerciseKind.WEIGHTED, Muscle.CHEST),
    ).associateBy { it.id }

    @Test fun firstMainLiftSkipsWarmupAndBodyweight() {
        val items = listOf(item(0, "squat", "Warm-up"), item(1, "band"), item(2, "bench"), item(3, "squat"))
        assertEquals("bench", ReminderText.firstMainLift(items, exercises)?.exerciseId)
    }

    @Test fun noWeightedLiftGivesNull() = assertNull(ReminderText.firstMainLift(listOf(item(0, "band")), exercises))

    @Test fun loadUpWhenNextIsHeavier() =
        assertEquals(ReminderBody.LoadUp("bench", 62.5f), ReminderText.body("bench", 60f, 62.5f, 5))

    @Test fun countWhenWeightHolds() = assertEquals(ReminderBody.Count(5), ReminderText.body("bench", 60f, 60f, 5))

    @Test fun countWhenNoHistoryOrLift() {
        assertEquals(ReminderBody.Count(4), ReminderText.body("bench", null, 62.5f, 4))
        assertEquals(ReminderBody.Count(4), ReminderText.body("bench", 0f, 5f, 4))
        assertEquals(ReminderBody.Count(4), ReminderText.body(null, 60f, 62.5f, 4))
    }

    @Test fun focusNeedsTwoThirds() {
        assertEquals(TodayFocus.LEGS, Readiness.focusOf(listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.CALVES, Muscle.CHEST)))
        assertEquals(TodayFocus.UPPER, Readiness.focusOf(listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.SHOULDERS)))
        assertNull(Readiness.focusOf(listOf(Muscle.QUADS, Muscle.CHEST)))
        assertNull(Readiness.focusOf(listOf(Muscle.ABS)))
        assertNull(Readiness.focusOf(emptyList()))
    }

    @Test fun adviceNamesTheDay() {
        assertEquals(DayAdvice.LEGS_STEADY, Readiness.adviceFor(DayCall.STEADY, TodayFocus.LEGS))
        assertEquals(DayAdvice.LEGS_RECOVER, Readiness.adviceFor(DayCall.RECOVER, TodayFocus.LEGS))
        assertEquals(DayAdvice.UPPER_EASE, Readiness.adviceFor(DayCall.EASE_OFF, TodayFocus.UPPER))
        assertNull(Readiness.adviceFor(DayCall.READY, TodayFocus.LEGS))
        assertNull(Readiness.adviceFor(DayCall.STEADY, TodayFocus.UPPER))
        assertNull(Readiness.adviceFor(DayCall.RECOVER, null))
    }
}
