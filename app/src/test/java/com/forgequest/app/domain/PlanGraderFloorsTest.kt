package com.forgequest.app.domain

import com.forgequest.app.data.seed.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Locks plan quality: one deterministic plan per goal x days x preset x minutes group (700 plans).
 * Floors are the means measured after the master upgrade (3A) minus 2 points; every single plan must score at least 60.
 * The digest is shared with web (`web/src/domain/plan-grader-floors.test.ts`) through `tools/plan-grader.golden`.
 */
class PlanGraderFloorsTest {

    private val focuses = listOf<Muscle?>(
        null,
        Muscle.CHEST,
        Muscle.SHOULDERS,
        Muscle.BICEPS,
        Muscle.TRICEPS,
        Muscle.ABS,
        Muscle.UPPER_BACK,
        Muscle.GLUTES,
        Muscle.QUADS,
        Muscle.HAMSTRINGS,
        Muscle.CALVES,
    )

    private val graderFloors: Map<String, Double> = mapOf(
        "3x20:coverage" to 21.29,
        "days:2" to 87.97,
        "days:3" to 88.35,
        "days:4" to 86.42,
        "days:5" to 84.76,
        "days:6" to 81.79,
        "fatloss6:balance" to 14.21,
        "goal:ENDURANCE" to 84.77,
        "goal:FAT_LOSS" to 85.12,
        "goal:GENERAL" to 86.51,
        "goal:MUSCLE" to 86.36,
        "goal:STRENGTH" to 86.53,
        "minutes:20" to 88.07,
        "minutes:30" to 87.60,
        "minutes:45" to 84.40,
        "minutes:60" to 83.36,
    )

    private companion object {
        const val MIN_PLAN = 60.0
        fun fmt(v: Double): String = String.format(Locale.ROOT, "%.2f", v)
    }

    @Test(timeout = 90_000)
    fun everySampledPlanScoresAtLeast60GroupMeansStayAboveFloorsAndMatchSharedGolden() {
        val library = ExerciseCatalog.all
        val catalog = library.associateBy { it.id }
        val hash = MessageDigest.getInstance("SHA-256")
        val sums = LinkedHashMap<String, Pair<Double, Int>>()
        fun add(k: String, v: Double) {
            val cur = sums[k]
            sums[k] = if (cur == null) v to 1 else (cur.first + v) to (cur.second + 1)
        }

        var g = 0
        for (goal in TrainGoal.entries) {
            for (days in 2..6) {
                for (preset in EquipmentPreset.entries) {
                    for (minutes in listOf(20, 30, 45, 60)) {
                        val experience = Experience.entries[g % Experience.entries.size]
                        val focus = focuses[g % focuses.size]
                        g++
                        val equipment = preset.equipment + Equipment.BODYWEIGHT
                        val plan = PlanBuilder.build(
                            PlanInput(
                                goal = goal,
                                days = PlanBuilder.spreadDays(days),
                                minutes = minutes,
                                equipment = equipment,
                                experience = experience,
                                focus = focus?.let { setOf(it) } ?: emptySet(),
                            ),
                            library,
                        )
                        val grade = PlanGrader.gradePlan(plan.draft, catalog, goal, minutes)
                        val key = "$goal|$days|$preset|$experience|${focus ?: "-"}|$minutes"
                        assertTrue("$key: ${grade.total}", grade.total >= MIN_PLAN)
                        val s = grade.scores
                        val line = "$key\t${fmt(grade.total)}/${fmt(s.efficiency)}/${fmt(s.coverage)}/${fmt(s.balance)}/${fmt(s.quality)}/${fmt(s.pairing)}\n"
                        hash.update(line.toByteArray(Charsets.UTF_8))
                        add("goal:$goal", grade.total)
                        add("days:$days", grade.total)
                        add("minutes:$minutes", grade.total)
                        if (days == 3 && minutes == 20) add("3x20:coverage", s.coverage)
                        if (goal == TrainGoal.FAT_LOSS && days == 6) add("fatloss6:balance", s.balance)
                    }
                }
            }
        }
        assertEquals(700, g)

        val means = sums.entries.map { (k, pair) ->
            val (sum, n) = pair
            k to java.lang.Math.round((sum / n) * 100.0) / 100.0
        }
        println(means.joinToString(" ") { (k, m) -> "$k=${fmt(m)}" })
        for ((k, m) in means) {
            val floor = graderFloors[k]
            assertNotNull("missing floor $k", floor)
            assertTrue("$k: $m < $floor", m >= floor!!)
        }
        val digest = hash.digest().joinToString("") { "%02x".format(it) }
        println("grader SHA-256 $digest")
        val golden = listOf(File("../tools/plan-grader.golden"), File("tools/plan-grader.golden")).first { it.isFile }
        assertEquals(golden.readText().trim(), digest)
    }
}
