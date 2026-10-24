package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftDay
import com.forgequest.app.data.plan.PlanDraftItem
import com.forgequest.app.data.seed.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The 12 bugs from the 2026-10-24 math audit; the web app has the same cases in its *.test.ts files. */
class AuditFixesTest {
    private val hour = 3_600_000L

    @Test fun negatedWordsAreNotPain() {
        assertEquals(SetEffort.OK, SetEffort.of("", "felt strong, no pain"))
        assertEquals(SetEffort.OK, SetEffort.of("", "pain-free today"))
        assertEquals(SetEffort.OK, SetEffort.of("", "without pain this time"))
        assertEquals(SetEffort.OK, SetEffort.of("", "بدون ألم"))
        assertEquals(SetEffort.OK, SetEffort.of("", "didn't fail"))
        assertEquals(SetEffort.PAIN, SetEffort.of("", "sharp pain in left shoulder"))
        assertEquals(SetEffort.PAIN, SetEffort.of("", "no rest, knee pain"))
        assertEquals(SetEffort.PAIN, SetEffort.of("", "no pain at first but pain on the last rep"))
        assertEquals(SetEffort.PAIN, SetEffort.of("", "بألم في الركبة"))
    }

    @Test fun defaultRangeResetsToEightAfterALoadStep() {
        val last = List(3) { Progression.PastSet(12, null, 60f, SetEffort.OK) }
        val r = Progression.next(ExerciseKind.WEIGHTED, null, last, emptyList(), WeightUnit.KG, lowReadiness = false, warmup = false)
        assertEquals(List(3) { Progression.Prefill(8, null, 62.5f) }, r.sets)
    }

    @Test fun oneFailedSetStartsAtTheBottomOfTheRange() {
        val last = List(2) { Progression.PastSet(10, null, 60f, SetEffort.OK) } + Progression.PastSet(4, null, 60f, SetEffort.HARD)
        val r = Progression.next(ExerciseKind.WEIGHTED, Progression.Target(3, 8, 12, null, 60f), last, emptyList(), WeightUnit.KG, false, false)
        assertEquals(listOf(10, 10, 8), r.sets.map { it.reps })
    }

    @Test fun carbsNeverPassTheCalorieGoal() {
        // 1,200 kcal − 150 g protein (600) − 60 g fat (540) leaves 60 kcal: 15 g carbs, not the old 20 g minimum.
        assertEquals(15, Nutrition.suggestedCarbs(1200, 150, fatPct = 45))
    }

    @Test fun quarterKiloLoadsKeepBothDecimals() {
        assertEquals("11.25", WeightUnit.KG.number(11.25f))
        assertEquals("62.5", WeightUnit.KG.number(62.5f))
        assertEquals("60", WeightUnit.KG.number(60f))
        assertEquals("135", WeightUnit.LB.number(WeightUnit.LB.toKg(135f)))
    }

    @Test fun weightedPullUpsCountTheBody() {
        val tenReps = Records.score(ExerciseKind.WEIGHTED, 10, null, 10f, "weighted_pull_up")
        val single = Records.score(ExerciseKind.WEIGHTED, 1, null, 15f, "weighted_pull_up")
        assertEquals(38.33f, tenReps, 0.01f) // (75 + 10) × (1 + 10/30) − 75
        assertTrue(tenReps > single)
        assertEquals(20f, Records.score(ExerciseKind.WEIGHTED, 8, null, 0f, "weighted_pull_up"), 0.01f)
        assertEquals(116.67f, Records.score(ExerciseKind.WEIGHTED, 5, null, 100f), 0.01f)
    }

    @Test fun twoSessionsTwelveHoursApartAddUp() {
        val now = 100 * hour
        val r = Recovery.compute(listOf(Triple(Muscle.CHEST, now - 12 * hour, 0.5f), Triple(Muscle.CHEST, now, 0.5f)), now).getValue(Muscle.CHEST)
        assertEquals(0.75f, r.fatigue, 0.001f)
        assertEquals(36, r.hoursUntilReady)
    }

    @Test fun oneDayAWeekPlanReachesThreeMisses() {
        assertTrue(Nudges.missedInARow(30, setOf(6), emptySet(), emptySet(), 0) >= 3)
    }

    // Two lifts done the same way Mon/Wed/Fri for five weeks; checked on a Monday morning before training.
    private val monday = Dates.weekStart(LocalDate.of(2026, 10, 14).toEpochDay())
    private val sessions = (0 until 5).flatMap { w -> listOf(0, 2, 4).map { d -> monday - 35 + 7 * w + d } }
        .mapIndexed { i, d -> SessionEntity(id = i + 1L, planDayId = null, title = "A", epochDay = d, startedAt = 0L) }
    private fun lift(session: Long, id: String, block: String = "Main", kg: Float? = 60f, reps: Int? = 8, sec: Int? = null) =
        SetEntity(sessionId = session, exerciseId = id, itemPosition = 0, block = block, circuit = false, setIndex = 0, targetLabel = "", reps = reps, seconds = sec, weightKg = kg, done = true)
    private val lib = listOf(
        ExerciseEntity("bench", "Bench", ExerciseKind.WEIGHTED, listOf(Muscle.CHEST), emptyList()),
        ExerciseEntity("row", "Row", ExerciseKind.WEIGHTED, listOf(Muscle.UPPER_BACK), emptyList()),
        ExerciseEntity("plank", "Plank", ExerciseKind.HOLD, listOf(Muscle.ABS), emptyList()),
    ).associateBy { it.id }
    private val doneSets = sessions.flatMap { s -> List(3) { listOf(lift(s.id, "bench"), lift(s.id, "row")) }.flatten() }

    @Test fun deloadCheckSeesAFullWeekOnMonday() {
        val report = Trends.analyzeFatigueAndPlateaus(sessions, doneSets, lib, today = monday)
        assertEquals(100, report.trainingLoadPct)
        assertEquals(listOf("bench", "row"), report.stalledLifts.map { it.exerciseId }.sorted())
        assertTrue(report.needsDeload)
    }

    @Test fun warmUpsAndHeldPlanksDontCount() {
        val extra = sessions.flatMap { s -> listOf(lift(s.id, "bench", block = "Warm-up"), lift(s.id, "plank", kg = null, reps = null, sec = 60)) }
        val report = Trends.analyzeFatigueAndPlateaus(sessions, doneSets + extra, lib, today = monday)
        assertEquals(100, report.trainingLoadPct)
        assertFalse(report.stalledLifts.any { it.exerciseId == "plank" })
    }

    @Test fun aCurlIsKeptWhenNoBodyweightMoveIsARealMatch() {
        val draft = PlanDraft(name = "P", days = listOf(PlanDraftDay(1, "Arms", items = listOf(PlanDraftItem("bicep_curl", sets = 3, repsMin = 10, repsMax = 12)))))
        val (adapted, swaps) = ExerciseSwapEngine.adaptPlanToEquipment(draft, ExerciseCatalog.all.associateBy { it.id }, setOf(Equipment.BODYWEIGHT))
        assertEquals(0, swaps)
        assertEquals("bicep_curl", adapted.days.single().items.single().exerciseId)
    }

    @Test fun sleepOnlyFirstDaysDontCrashRecovery() {
        // No HRV or resting-HR baseline yet and a 6 h night (75 %): 53 % (Steady), not 37 % (Ease off).
        assertEquals(53, Readiness.recovery(null, null, null, 75, null, null, null))
        // With every input at its baseline the score is unchanged.
        val base = Baseline(4.0, 0.1, 14)
        assertEquals(60, Readiness.recovery(4.0, 60.0, 15.0, 85, base, Baseline(60.0, 2.0, 14), Baseline(15.0, 0.5, 14)))
    }

    @Test fun anotherAppsFullNightOverFitbitsTwoHalvesIsACopy() {
        val zone = ZoneId.of("UTC")
        fun at(s: String) = Instant.parse(s)
        val pieces = listOf(
            SleepPiece(at("2026-10-15T23:00:00Z"), at("2026-10-16T03:00:00Z"), 0, 0, 0, 0, 240, fromFitbit = true),
            SleepPiece(at("2026-10-16T03:30:00Z"), at("2026-10-16T07:30:00Z"), 0, 0, 0, 0, 240, fromFitbit = true),
            SleepPiece(at("2026-10-15T23:00:00Z"), at("2026-10-16T07:30:00Z"), 0, 0, 0, 0, 480, fromFitbit = false),
        )
        assertEquals(480, Readiness.nights(pieces, zone).getValue(LocalDate.of(2026, 10, 16)).asleepMin)
    }
}
