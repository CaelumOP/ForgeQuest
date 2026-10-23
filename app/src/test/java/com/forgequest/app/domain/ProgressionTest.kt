package com.forgequest.app.domain

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
}
