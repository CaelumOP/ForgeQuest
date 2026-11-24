package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.domain.RoutineAuditor.AuditDay
import com.forgequest.app.domain.RoutineAuditor.AuditItem
import com.forgequest.app.domain.RoutineAuditor.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineAuditorTest {

    private fun ex(
        id: String,
        primary: List<Muscle>,
        secondary: List<Muscle> = emptyList(),
        pattern: MovementPattern,
        kind: ExerciseKind = ExerciseKind.WEIGHTED,
    ) = ExerciseEntity(
        id = id,
        name = id,
        kind = kind,
        primary = primary,
        secondary = secondary,
        pattern = pattern.name,
    )

    private val bench = ex("test_bench", listOf(Muscle.CHEST), listOf(Muscle.SHOULDERS, Muscle.TRICEPS), MovementPattern.HORIZONTAL_PUSH)
    private val ohp = ex("test_ohp", listOf(Muscle.SHOULDERS), listOf(Muscle.TRICEPS), MovementPattern.VERTICAL_PUSH)
    private val latRaise = ex("test_lat_raise", listOf(Muscle.SHOULDERS), emptyList(), MovementPattern.LATERAL_RAISE)
    private val row = ex("test_row", listOf(Muscle.UPPER_BACK), listOf(Muscle.BICEPS), MovementPattern.HORIZONTAL_PULL)
    private val pulldown = ex("test_pulldown", listOf(Muscle.UPPER_BACK), listOf(Muscle.BICEPS), MovementPattern.VERTICAL_PULL)
    private val curl = ex("test_curl", listOf(Muscle.BICEPS), emptyList(), MovementPattern.ELBOW_FLEXION)
    private val squat = ex("test_squat", listOf(Muscle.QUADS, Muscle.GLUTES), emptyList(), MovementPattern.SQUAT_BILATERAL)
    private val rdl = ex("test_rdl", listOf(Muscle.HAMSTRINGS, Muscle.GLUTES), emptyList(), MovementPattern.HIP_HINGE)
    private val calf = ex("test_calf", listOf(Muscle.CALVES), emptyList(), MovementPattern.ANKLE_FLEXION)
    private val exercises = listOf(bench, ohp, latRaise, row, pulldown, curl, squat, rdl, calf).associateBy { it.id }

    @Test
    fun belowMevForMajorMuscleOnly() {
        // BICEPS/TRICEPS/CALVES have MEV > 0 in landmarks, yet BELOW_MEV only triggers for the 6 major muscles.
        val days = listOf(
            AuditDay(
                dayOfWeek = 1,
                items = listOf(
                    AuditItem(bench.id, 8),
                    AuditItem(row.id, 10),
                    AuditItem(ohp.id, 8),
                    AuditItem(squat.id, 8),
                    AuditItem(rdl.id, 6),
                ),
            ),
        )
        val findings = RoutineAuditor.audit(days, exercises)
        assertTrue(findings.none { it.kind == Kind.BELOW_MEV })

        val lowChestDays = listOf(
            AuditDay(
                dayOfWeek = 1,
                items = listOf(
                    AuditItem(bench.id, 4), // CHEST 4 < MEV 8
                    AuditItem(row.id, 10),
                    AuditItem(ohp.id, 8),
                    AuditItem(squat.id, 8),
                    AuditItem(rdl.id, 6),
                ),
            ),
        )
        val lowFindings = RoutineAuditor.audit(lowChestDays, exercises)
        assertEquals(listOf(Muscle.CHEST), lowFindings.filter { it.kind == Kind.BELOW_MEV }.map { it.muscle })
    }

    @Test
    fun secondaryCountsHalf() {
        // 8 sets of bench give CHEST 8, SHOULDERS 4 (0.5x), plus 4 direct sets of latRaise -> SHOULDERS = 8.0 (meets MEV 8).
        val days = listOf(
            AuditDay(1, listOf(AuditItem(bench.id, 8), AuditItem(latRaise.id, 4))),
            AuditDay(3, listOf(AuditItem(row.id, 10))),
            AuditDay(5, listOf(AuditItem(squat.id, 8), AuditItem(rdl.id, 6))),
        )
        val findings = RoutineAuditor.audit(days, exercises)
        assertTrue(findings.none { it.kind == Kind.BELOW_MEV && it.muscle == Muscle.SHOULDERS })
    }

    @Test
    fun aboveMrvFlagged() {
        val days = listOf(
            AuditDay(1, listOf(AuditItem(bench.id, 8))),
            AuditDay(3, listOf(AuditItem(bench.id, 8))),
            AuditDay(5, listOf(AuditItem(bench.id, 7))), // 23 > CHEST MRV 22
        )
        val findings = RoutineAuditor.audit(days, exercises)
        val mrv = findings.filter { it.kind == Kind.ABOVE_MRV }
        assertEquals(1, mrv.size)
        assertEquals(Muscle.CHEST, mrv[0].muscle)
        assertEquals(23.0, mrv[0].value, 0.01)
        assertEquals(22.0, mrv[0].limit, 0.01)
    }

    @Test
    fun pushPullImbalanceCountsExerciseOnce() {
        // bench has 1 primary (CHEST) and 2 secondary (SHOULDERS, TRICEPS), but counts once toward PUSH = 6 sets.
        // 6 push vs 4 pull -> 6 > 1.2 * 4 (4.8) and push >= 6 -> flagged with value=6, limit=4.
        val days = listOf(
            AuditDay(1, listOf(AuditItem(bench.id, 6), AuditItem(row.id, 4))),
        )
        val findings = RoutineAuditor.audit(days, exercises)
        val pp = findings.filter { it.kind == Kind.PUSH_PULL }
        assertEquals(1, pp.size)
        assertEquals(6.0, pp[0].value, 0.01)
        assertEquals(4.0, pp[0].limit, 0.01)
    }

    @Test
    fun balancedPushPullIsQuiet() {
        val days = listOf(
            AuditDay(1, listOf(AuditItem(bench.id, 6), AuditItem(row.id, 5))), // 6 == 1.2 * 5 (not strictly >)
        )
        val findings = RoutineAuditor.audit(days, exercises)
        assertTrue(findings.none { it.kind == Kind.PUSH_PULL })
    }

    @Test
    fun isolationBeforeSameMuscleCompound() {
        val days = listOf(
            AuditDay(
                dayOfWeek = 2,
                items = listOf(
                    AuditItem(latRaise.id, 3, position = 0),
                    AuditItem(ohp.id, 4, position = 1),
                ),
            ),
        )
        val findings = RoutineAuditor.audit(days, exercises)
        val iso = findings.filter { it.kind == Kind.ISOLATION_FIRST }
        assertEquals(1, iso.size)
        assertEquals(Muscle.SHOULDERS, iso[0].muscle)
        assertEquals(0, iso[0].dayIndex)
    }

    @Test
    fun junkVolumeOverTenInOneDay() {
        val days = listOf(
            AuditDay(
                dayOfWeek = 1,
                items = listOf(AuditItem(bench.id, 11)),
            ),
        )
        val findings = RoutineAuditor.audit(days, exercises)
        val junk = findings.filter { it.kind == Kind.JUNK_VOLUME }
        assertEquals(1, junk.size)
        assertEquals(Muscle.CHEST, junk[0].muscle)
        assertEquals(11.0, junk[0].value, 0.01)
        assertEquals(10.0, junk[0].limit, 0.01)
    }

    @Test
    fun recoveryOverlapOnConsecutiveWeekdays() {
        val days = listOf(
            AuditDay(1, listOf(AuditItem(bench.id, 6))),
            AuditDay(2, listOf(AuditItem(bench.id, 6))),
        )
        val findings = RoutineAuditor.audit(days, exercises, ScheduleMode.WEEKDAY)
        val overlap = findings.filter { it.kind == Kind.RECOVERY_OVERLAP }
        assertEquals(1, overlap.size)
        assertEquals(Muscle.CHEST, overlap[0].muscle)
        assertEquals(1, overlap[0].dayIndex)
    }

    @Test
    fun sundayMondayWrapCounts() {
        val days = listOf(
            AuditDay(7, listOf(AuditItem(row.id, 6))),
            AuditDay(1, listOf(AuditItem(pulldown.id, 6))),
        )
        val findings = RoutineAuditor.audit(days, exercises, ScheduleMode.WEEKDAY)
        val overlap = findings.filter { it.kind == Kind.RECOVERY_OVERLAP }
        assertEquals(1, overlap.size)
        assertEquals(Muscle.UPPER_BACK, overlap[0].muscle)
        assertEquals(1, overlap[0].dayIndex)
    }

    @Test
    fun rotationPlanSkipsOverlap() {
        val days = listOf(
            AuditDay(7, listOf(AuditItem(bench.id, 6))),
            AuditDay(1, listOf(AuditItem(bench.id, 6))),
        )
        val findings = RoutineAuditor.audit(days, exercises, ScheduleMode.ROTATION)
        assertTrue(findings.none { it.kind == Kind.RECOVERY_OVERLAP })
    }

    @Test
    fun warmupAndCooldownIgnored() {
        val days = listOf(
            AuditDay(
                dayOfWeek = 1,
                items = listOf(
                    AuditItem(latRaise.id, 12, block = WorkoutScore.WARM_UP_BLOCK, position = 0),
                    AuditItem(bench.id, 12, block = PlanBuilder.COOL_DOWN_BLOCK, position = 1),
                    AuditItem(ohp.id, 8, block = "Main", position = 2),
                    AuditItem(row.id, 10, block = "Main", position = 3),
                    AuditItem(squat.id, 8, block = "Main", position = 4),
                    AuditItem(rdl.id, 6, block = "Main", position = 5),
                ),
            ),
        )
        val findings = RoutineAuditor.audit(days, exercises)
        // The warm-up raise and cool-down bench would put shoulders at 20 and chest at 12 if they counted.
        assertTrue(findings.toString(), findings.none { it.kind == Kind.ISOLATION_FIRST })
        assertTrue(findings.toString(), findings.none { it.kind == Kind.JUNK_VOLUME && it.muscle in setOf(Muscle.SHOULDERS, Muscle.CHEST) })
        assertTrue(findings.toString(), findings.none { it.kind == Kind.ABOVE_MRV })
        assertEquals(listOf(Muscle.CHEST), findings.filter { it.kind == Kind.BELOW_MEV }.map { it.muscle })
    }

    @Test
    fun emptyPlanGivesNoCrash() {
        assertEquals(emptyList<RoutineAuditor.Finding>(), RoutineAuditor.audit(emptyList(), exercises))
        assertEquals(emptyList<RoutineAuditor.Finding>(), RoutineAuditor.audit(listOf(AuditDay(1, emptyList())), exercises))
    }
}
