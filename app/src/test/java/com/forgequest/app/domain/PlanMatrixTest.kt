package com.forgequest.app.domain

import com.forgequest.app.data.seed.ExerciseCatalog
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class PlanMatrixTest {
    // Keep iteration order and canonical UTF-8/LF records identical to plan-matrix.test.ts.
    private val focusOptions = listOf<Muscle?>(null, Muscle.CHEST, Muscle.SHOULDERS,
        Muscle.BICEPS, Muscle.TRICEPS, Muscle.ABS, Muscle.UPPER_BACK, Muscle.GLUTES,
        Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.CALVES)

    @Test(timeout = 90_000) fun matrixQualityAndSharedGolden() {
        val library = ExerciseCatalog.all
        val byId = library.associateBy { it.id }
        val hash = MessageDigest.getInstance("SHA-256")
        var index = 0; var total = 0; var standard = 0; var clean = 0; var shortNotes = 0
        for (goal in TrainGoal.entries) for (n in 1..7) for (preset in EquipmentPreset.entries)
            for (exp in Experience.entries) for (focus in focusOptions) for (minutes in listOf(20, 30, 45, 60)) {
                // All standard plans plus a rotating 1-in-11 sample of nonstandard plans (44 = 11 focuses x 4 durations).
                val standardPlan = n in 3..6 && minutes >= 45
                val sampled = index % 11 == (index / 44) % 11
                index++
                if (!sampled && !standardPlan) continue
                val plan = PlanBuilder.build(PlanInput(goal = goal, days = PlanBuilder.spreadDays(n),
                    minutes = minutes, equipment = preset.equipment + Equipment.BODYWEIGHT,
                    experience = exp, focus = focus?.let { setOf(it) } ?: emptySet()), library)
                val key = "$goal|$n|$preset|$exp|${focus ?: "-"}|$minutes"
                val kinds = RoutineAuditor.audit(RoutineAuditor.fromDraft(plan.draft), byId).map { it.kind }
                assertTrue("$key: $kinds", kinds.none { it in setOf(RoutineAuditor.Kind.ABOVE_MRV,
                    RoutineAuditor.Kind.JUNK_VOLUME, RoutineAuditor.Kind.RECOVERY_OVERLAP) })
                if (n <= 2) {
                    assertFalse(key, RoutineAuditor.Kind.BELOW_MEV in kinds)
                    if (RoutineAuditor.Kind.SHORT_PLAN in kinds) shortNotes++
                }
                total++
                if (n !in 3..6 || minutes < 45) continue
                standard++
                if (kinds.isEmpty()) clean++
                assertTrue("$key: $kinds", kinds.all { it == RoutineAuditor.Kind.BELOW_MEV })
                // Day and item order are significant; null is literal "null"; no localized titles.
                val record = key + "\t" + plan.draft.days.joinToString(" || ") { d ->
                    "${d.dayOfWeek}:" + d.items.joinToString(";") { i ->
                        "${i.exerciseId}/${i.sets}/${i.repsMin}-${i.repsMax}/${i.seconds}/${i.restSeconds}"
                    }
                } + "\n"
                hash.update(record.toByteArray(Charsets.UTF_8))
            }
        assertEquals(11340, total)
        assertEquals(9240, standard)
        assertTrue(shortNotes > 0)
        assertTrue("$clean/$standard clean", clean.toDouble() / standard >= 0.92)
        val digest = hash.digest().joinToString("") { "%02x".format(it) }
        println("matrix: $clean/$standard clean; SHA-256 $digest")
        val golden = listOf(File("../tools/plan-matrix.golden"), File("tools/plan-matrix.golden")).first { it.isFile }
        assertEquals(golden.readText().trim(), digest)
    }
}
