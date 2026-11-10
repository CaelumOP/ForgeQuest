package com.forgequest.app.domain

import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    private fun sets(n: Int, reps: Int?, kg: Float?, sec: Int? = null, effort: SetEffort = SetEffort.OK) =
        List(n) { Progression.PastSet(reps, sec, kg, effort) }

    private fun next(
        kind: ExerciseKind,
        target: Progression.Target,
        last: List<Progression.PastSet>,
        previous: List<Progression.PastSet> = emptyList(),
        unit: WeightUnit = WeightUnit.KG,
        low: Boolean = false,
    ) = Progression.next(kind, target, last, previous, unit, low, warmup = false)

    @Test
    fun rangeTopAddsOneStepAndResets() {
        val r = next(ExerciseKind.WEIGHTED, Progression.Target(3, 8, 12, null, 40f), sets(3, 12, 40f))
        assertEquals(8, r.sets[0].reps)
        assertEquals(42.5f, r.sets[0].weightKg!!, 0.01f)
    }

    @Test
    fun singleTargetNeedsTwoCleanSessionsAtTheSameWeight() {
        val t = Progression.Target(3, 8, null, null, 60f)
        // one session at target: hold
        assertEquals(60f, next(ExerciseKind.WEIGHTED, t, sets(3, 8, 60f), emptyList()).sets[0].weightKg!!, 0.01f)
        // two in a row at 60: go up
        assertEquals(62.5f, next(ExerciseKind.WEIGHTED, t, sets(3, 8, 60f), sets(3, 8, 60f)).sets[0].weightKg!!, 0.01f)
        // just went up (62.5 after 60): no second increase in a row
        assertEquals(62.5f, next(ExerciseKind.WEIGHTED, t, sets(3, 8, 62.5f), sets(3, 8, 60f)).sets[0].weightKg!!, 0.01f)
        // a hard set in the previous session blocks it
        assertEquals(60f, next(ExerciseKind.WEIGHTED, t, sets(3, 8, 60f), sets(3, 8, 60f, effort = SetEffort.HARD)).sets[0].weightKg!!, 0.01f)
    }

    @Test
    fun poundsStepInPoundsOnLoadableNumbers() {
        val unit = WeightUnit.LB
        val r = next(ExerciseKind.WEIGHTED, Progression.Target(3, 8, 12, null, null), sets(3, 12, unit.toKg(135f)), unit = unit)
        assertEquals(140f, unit.fromKg(r.sets[0].weightKg!!), 0.01f)
    }

    @Test
    fun hardPainAndLowReadinessHoldOrBackOff() {
        val t = Progression.Target(3, 8, 12, null, 40f)
        val hard = next(ExerciseKind.WEIGHTED, t, sets(3, 12, 40f, effort = SetEffort.HARD))
        assertEquals(40f, hard.sets[0].weightKg!!, 0.01f)
        assertEquals(12, hard.sets[0].reps)
        val pain = next(ExerciseKind.WEIGHTED, t, sets(3, 10, 40f, effort = SetEffort.PAIN))
        assertEquals(37.5f, pain.sets[0].weightKg!!, 0.01f)
        val low = next(ExerciseKind.WEIGHTED, t, sets(3, 12, 40f), low = true)
        assertEquals(40f, low.sets[0].weightKg!!, 0.01f)
        // bodyweight move with pain: reps stay
        val bw = next(ExerciseKind.REPS, Progression.Target(3, 8, 12, null, null), sets(3, 10, null, effort = SetEffort.PAIN))
        assertEquals(10, bw.sets[0].reps)
    }

    @Test
    fun holdsStopAtOneAndAHalfTimesTarget() {
        val t = Progression.Target(3, null, null, 30, null)
        assertEquals(40, next(ExerciseKind.HOLD, t, sets(3, null, null, sec = 35)).sets[0].seconds)
        val atCeiling = next(ExerciseKind.HOLD, t, sets(3, null, null, sec = 45))
        assertEquals(45, atCeiling.sets[0].seconds)
        assertTrue(atCeiling.holdCeilingReached)
    }

    @Test
    fun oldNotesStillCountAsEffort() {
        assertEquals(SetEffort.PAIN, SetEffort.of("", "left knee twinge"))
        assertEquals(SetEffort.HARD, SetEffort.of("", "RIR 0"))
        assertEquals(SetEffort.EASY, SetEffort.of("EASY", "fail"))
        assertEquals(SetEffort.OK, SetEffort.of("", ""))
        assertEquals(SetEffort.EASY, SetEffort.of("", "سهل"))
        assertEquals(SetEffort.EASY, SetEffort.of("", "felt easy, RIR 3"))
        assertEquals(SetEffort.HARD, SetEffort.of("", "وصلت إلى فشل"))
    }

    @Test
    fun painAtTheSmallestLoadKeepsOneStepSoItCanClimbAgain() {
        val t = Progression.Target(3, 10, 15, null, 2.5f)
        val pain = next(ExerciseKind.WEIGHTED, t, sets(3, 12, 2.5f, effort = SetEffort.PAIN))
        assertEquals(2.5f, pain.sets[0].weightKg!!, 0.01f)
        assertTrue(pain.sets[0].weightKg!! > 0f)
        // and a clean top-of-range session from there still loads up
        assertEquals(5f, next(ExerciseKind.WEIGHTED, t, sets(3, 15, 2.5f)).sets[0].weightKg!!, 0.01f)
        // pounds: 5 lb with pain stays at 5 lb
        val lb = WeightUnit.LB
        val p = next(ExerciseKind.WEIGHTED, t, sets(3, 12, lb.toKg(5f), effort = SetEffort.PAIN), unit = lb)
        assertEquals(5f, lb.fromKg(p.sets[0].weightKg!!), 0.01f)
    }

    @Test
    fun easySetsProgressLikeOk() {
        val t = Progression.Target(3, 8, 12, null, 40f)
        val r = next(ExerciseKind.WEIGHTED, t, sets(3, 12, 40f, effort = SetEffort.EASY))
        assertEquals(42.5f, r.sets[0].weightKg!!, 0.01f)
    }

    @Test
    fun inputsReadTheLastTwoWorkoutsNewestFirstInSetOrder() {
        fun row(session: Long, index: Int, reps: Int, effort: String = "", note: String = "") = SetEntity(
            sessionId = session, exerciseId = "squat", itemPosition = 0, block = "Main", circuit = false, setIndex = index,
            targetLabel = "", reps = reps, weightKg = 60f, done = true, effort = effort, note = note,
        )
        val recent = listOf(row(9, 1, 8, note = "knee pain"), row(9, 0, 10), row(5, 0, 12, effort = "HARD"), row(2, 0, 6))
        val item = PlanItemEntity(dayId = 1, position = 0, exerciseId = "squat", block = "Main", circuit = false, sets = 3, repsMin = 8, repsMax = 12, weightKg = 60f)
        val input = Progression.inputs(item, "squat", recent)
        assertEquals(Progression.Target(3, 8, 12, null, 60f), input.target)
        assertEquals(listOf(Progression.PastSet(10, null, 60f, SetEffort.OK), Progression.PastSet(8, null, 60f, SetEffort.PAIN)), input.last)
        assertEquals(listOf(Progression.PastSet(12, null, 60f, SetEffort.HARD)), input.previous)
        assertEquals(false, input.warmup)
        assertEquals(true, Progression.inputs(item.copy(block = "warm-up"), "squat", recent).warmup)
        assertEquals(true, Progression.inputs(null, "jumping_jacks", emptyList()).warmup)
        assertEquals(null, Progression.inputs(null, "squat", emptyList()).target)
    }

    private fun reason(
        kind: ExerciseKind, t: Progression.Target, last: List<Progression.PastSet>,
        previous: List<Progression.PastSet> = emptyList(), low: Boolean = false, warmup: Boolean = false,
    ) = Progression.next(kind, t, last, previous, WeightUnit.KG, low, warmup).reason

    @Test
    fun reasonLoadUpMoreRepsMoreTime() {
        val range = Progression.Target(3, 8, 12, null, 40f)
        assertEquals(Progression.Reason.LOAD_UP, reason(ExerciseKind.WEIGHTED, range, sets(3, 12, 40f)))
        assertEquals(Progression.Reason.MORE_REPS, reason(ExerciseKind.WEIGHTED, range, sets(3, 10, 40f)))
        assertEquals(Progression.Reason.MORE_TIME, reason(ExerciseKind.HOLD, Progression.Target(3, null, null, 30, null), sets(3, null, null, sec = 30)))
    }

    @Test
    fun reasonOnceMoreAndSame() {
        val single = Progression.Target(3, 8, null, null, 60f)
        assertEquals(Progression.Reason.ONCE_MORE, reason(ExerciseKind.WEIGHTED, single, sets(3, 8, 60f)))
        // bodyweight reps already past the range ceiling: nothing goes up
        assertEquals(Progression.Reason.SAME, reason(ExerciseKind.REPS, Progression.Target(3, 8, 12, null, null), sets(3, 14, null)))
    }

    @Test
    fun reasonHardPainMissedLowReadiness() {
        val t = Progression.Target(3, 8, 12, null, 40f)
        assertEquals(Progression.Reason.HARD, reason(ExerciseKind.WEIGHTED, t, sets(3, 12, 40f, effort = SetEffort.HARD)))
        assertEquals(Progression.Reason.PAIN, reason(ExerciseKind.WEIGHTED, t, sets(3, 10, 40f, effort = SetEffort.PAIN)))
        // pain on a bodyweight move: no weight drop, so it reads as hard
        assertEquals(Progression.Reason.HARD, reason(ExerciseKind.REPS, Progression.Target(3, 8, 12, null, null), sets(3, 10, null, effort = SetEffort.PAIN)))
        assertEquals(Progression.Reason.MISSED, reason(ExerciseKind.WEIGHTED, t, sets(2, 12, 40f)))
        assertEquals(Progression.Reason.LOW_READINESS, reason(ExerciseKind.WEIGHTED, t, sets(3, 12, 40f), low = true))
    }

    @Test
    fun reasonIsNullForWarmupNoHistoryAndHoldCeiling() {
        val t = Progression.Target(3, 8, 12, null, 40f)
        assertEquals(null, reason(ExerciseKind.WEIGHTED, t, sets(3, 12, 40f), warmup = true))
        assertEquals(null, reason(ExerciseKind.WEIGHTED, t, emptyList()))
        assertEquals(null, reason(ExerciseKind.HOLD, Progression.Target(3, null, null, 30, null), sets(3, null, null, sec = 45)))
    }

    private fun madeOn(day: Long) = java.time.LocalDate.ofEpochDay(day).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun deloadWeekIsEveryFourthWeek() {
        val made = madeOn(1000)
        assertEquals(false, Progression.isDeloadWeek(made, 1000))
        assertEquals(false, Progression.isDeloadWeek(made, 1020))
        assertEquals(true, Progression.isDeloadWeek(made, 1021))
        assertEquals(true, Progression.isDeloadWeek(made, 1027))
        assertEquals(false, Progression.isDeloadWeek(made, 1028))
        assertEquals(true, Progression.isDeloadWeek(made, 1021 + 28))
        assertEquals(false, Progression.isDeloadWeek(made, 900))
    }

    @Test
    fun deloadHalvesSetsRoundedUpAndHoldsTheWeight() {
        val target = Progression.Target(3, 8, 12, null, 40f)
        val r = Progression.next(ExerciseKind.WEIGHTED, target, sets(3, 12, 40f), emptyList(), WeightUnit.KG, lowReadiness = false, warmup = false, deload = true)
        assertEquals(2, r.sets.size)
        assertTrue(r.sets.all { it.weightKg == 40f })
        assertEquals(2, Progression.deloadSets(4))
        assertEquals(1, Progression.deloadSets(1))
    }

    @Test
    fun afterADeloadSessionProgressIsNotStalled() {
        val target = Progression.Target(3, 8, 12, null, 40f)
        // A normal week with all 3 sets at the top steps up; the 2-set deload session would not.
        val normal = Progression.next(ExerciseKind.WEIGHTED, target, sets(3, 12, 40f), emptyList(), WeightUnit.KG, false, false)
        assertEquals(42.5f, normal.sets[0].weightKg!!, 0.01f)
        val afterDeload = Progression.next(ExerciseKind.WEIGHTED, target, sets(2, 12, 40f), emptyList(), WeightUnit.KG, false, false)
        assertEquals(40f, afterDeload.sets[0].weightKg!!, 0.01f)
    }
}
