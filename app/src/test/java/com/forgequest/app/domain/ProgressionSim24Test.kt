package com.forgequest.app.domain

import com.forgequest.app.data.db.SetEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/** Mirror progression-sim.test.ts: 24 weeks, three scheduled sessions per week. */
class ProgressionSim24Test {
    @Test fun consistent() = simulate("consistent", "72|0|false|SAME|false|82.50/11/-;82.50/11/-|82.50/11/-;82.50/11/-|OK,OK")
    @Test fun layoff() = simulate("layoff", "60|12|false|SAME|false|72.50/9/-;72.50/9/-|72.50/9/-;72.50/9/-|OK,OK")
    @Test fun inconsistent() = simulate("inconsistent", "53|19|false|SAME|false|77.50/10/-;77.50/10/-|77.50/10/-;77.50/10/-|OK,OK")
    @Test fun lightDumbbells() = simulate("light-dumbbells", "72|0|false|SAME|false|7.50/18/-;7.50/18/-|7.50/18/-;7.50/18/-|OK,OK")
    @Test fun pushUp() = simulate("push-up", "72|0|true|SAME|false|-/14/-;-/14/-|-/14/-;-/14/-|OK,OK")
    @Test fun plank() = simulate("plank", "72|0|false|SAME|false|-/-/45;-/-/45|-/-/45;-/-/45|OK,OK")
    @Test fun consistentLb() = simulate("consistent-lb", "72|0|false|SAME|false|180.00/8/-;180.00/8/-|180.00/8/-;180.00/8/-|OK,OK")

    private fun simulate(name: String, expected: String) {
        val kind = when (name) { "push-up" -> ExerciseKind.REPS; "plank" -> ExerciseKind.HOLD; else -> ExerciseKind.WEIGHTED }
        val unit = if (name == "consistent-lb") WeightUnit.LB else WeightUnit.KG
        val start = when { kind != ExerciseKind.WEIGHTED -> null; name == "light-dumbbells" -> 5f; unit == WeightUnit.LB -> unit.toKg(135f); else -> 60f }
        val repsMin = if (kind == ExerciseKind.HOLD) null else if (name == "light-dumbbells") 10 else 8
        val repsMax = if (kind == ExerciseKind.HOLD) null else if (name == "light-dumbbells") 15 else 12
        val target = Progression.Target(3, repsMin, repsMax, if (kind == ExerciseKind.HOLD) 30 else null, start)
        val history = mutableListOf<Pair<Boolean, List<Progression.PastSet>>>()
        var seed = 12345L; var attended = 0; var missed = 0; var unchangedMisses = 0
        var previousPrescription = ""; var ready = false; var lastState = ""; var firstTop = 0f; var finalTop = 0f
        val stalls = mutableListOf<String>()
        fun signature(sets: List<Progression.Prefill>) = sets.joinToString(";") { p ->
            "${p.weightKg?.let { String.format(Locale.US, "%.2f", unit.fromKg(it)) } ?: "-"}/${p.reps ?: "-"}/${p.seconds ?: "-"}"
        }
        fun check(p: Progression.Prefill, context: String) {
            p.weightKg?.let { weight ->
                assertTrue(context, weight >= 0f)
                val grid = if (unit == WeightUnit.KG) 0.25 else 2.5
                val steps = unit.fromKg(weight) / grid
                assertTrue(context, abs(steps - round(steps)) < 0.0001)
            }
            p.reps?.let { assertTrue(context, it >= 1) }
            p.seconds?.let { assertTrue(context, it >= 5); assertEquals(context, 0, it % 5) }
        }
        for (slot in 0 until 72) {
            val week = slot / 3 + 1
            // Unsigned 32-bit LCG, including skipped slots; identical to JS Math.imul + >>> 0.
            seed = (seed * 1664525L + 1013904223L) and 0xffffffffL
            if ((name == "layoff" && week in 9..12) || (name == "inconsistent" && (seed ushr 16) % 4 == 0L)) { missed++; continue }
            attended++
            val deload = week % 4 == 0
            val usable = history.filter { !it.first }
            val last = usable.getOrNull(0)?.second.orEmpty(); val previous = usable.getOrNull(1)?.second.orEmpty()
            val result = Progression.next(kind, target, last, previous, unit, warmup = false, deload = deload)
            val context = "$name week $week session ${slot % 3 + 1}: ${result.reason}"
            val prescription = signature(result.sets)
            unchangedMisses = if (result.reason == Progression.Reason.MISSED) {
                if (prescription == previousPrescription) unchangedMisses + 1 else 1
            } else 0
            if (unchangedMisses == 6) stalls += "$context: $prescription"
            previousPrescription = prescription
            result.sets.forEach { check(it, context) }
            finalTop = result.sets.maxOf { it.weightKg ?: 0f }
            if (attended == 1) firstTop = finalTop
            val presc = result.sets.toMutableList(); val done = mutableListOf<Progression.PastSet>()
            for (i in presc.indices) {
                val p = presc[i]
                val cap = when {
                    name == "light-dumbbells" -> 8 + 0.2 * (week - 1)
                    kind == ExerciseKind.REPS -> 13 + 0.5 * (week - 1)
                    kind == ExerciseKind.HOLD -> 40.0 + 2 * (week - 1)
                    name == "layoff" && week >= 13 -> 82.0 + 2 * (week - 1) - 20
                    else -> 82.0 + 2 * (week - 1)
                }
                val max = when (kind) {
                    ExerciseKind.WEIGHTED -> floor(30 * (cap / (p.weightKg ?: 1f) - 1)).toInt() - i
                    ExerciseKind.REPS -> floor(cap).toInt() - i
                    ExerciseKind.HOLD -> floor(cap / 5).toInt() * 5 - 5 * i
                }
                // Two untagged misses exercise light-load back-off without dropping a 5 kg dumbbell.
                val forced = name == "light-dumbbells" && (slot == 3 || slot == 4)
                val actual = if (forced) 7 else minOf(p.reps ?: p.seconds ?: 0, maxOf(if (kind == ExerciseKind.HOLD) 5 else 1, max))
                val effort = if (!forced && actual < (p.reps ?: p.seconds ?: 0)) SetEffort.FAILURE else SetEffort.OK
                val completed = Progression.PastSet(if (kind == ExerciseKind.HOLD) null else actual,
                    if (kind == ExerciseKind.HOLD) actual else null, p.weightKg, effort)
                done += completed
                fun entity(index: Int, pre: Progression.Prefill, finished: Progression.PastSet? = null) = SetEntity(
                    id = index + 1L, sessionId = slot + 1L, exerciseId = "x", itemPosition = 0, block = "Main", circuit = false,
                    setIndex = index, targetLabel = "", reps = finished?.reps ?: pre.reps,
                    seconds = finished?.seconds ?: pre.seconds, weightKg = finished?.weightKg ?: pre.weightKg,
                    done = finished != null, effort = finished?.effort?.name ?: "",
                )
                val remaining = (i + 1 until presc.size).map { entity(it, presc[it]) }
                val adjustment = IntraSession.compute(entity(i, p, completed), remaining, kind, repsMin, unit)
                for (change in adjustment?.changed.orEmpty()) {
                    val next = Progression.Prefill(change.reps, change.seconds, change.weightKg)
                    check(next, "$context adjusted set ${change.setIndex}")
                    presc[change.setIndex] = next
                }
            }
            if (kind == ExerciseKind.REPS) ready = ready || ExerciseTiers.readyForHarder(done, last, repsMax)
            history.add(0, deload to done)
            lastState = "${result.reason ?: "-"}|${result.holdCeilingReached}|${signature(result.sets)}|" +
                signature(done.map { Progression.Prefill(it.reps, it.seconds, it.weightKg) }) + "|" + done.joinToString(",") { it.effort.name }
        }
        assertEquals(72, attended + missed)
        if (name != "inconsistent") assertEquals(if (name == "layoff") 12 else 0, missed)
        else { assertTrue(missed > 0); assertTrue(attended > 36) }
        if (name.startsWith("consistent")) assertTrue(finalTop > firstTop)
        if (name == "push-up") assertTrue(ready)
        val state = "$attended|$missed|$ready|$lastState"
        println("$name: $state")
        assertEquals(expected, state)
        // Light dumbbells: even with the high-rep bridge, stepping 5 -> 7.5 kg (+50 %) temporarily stalls a slow lifter
        // around week 9-10 at 7.50/10 (a step down is >10 %, audit S5) before recovering and finishing at 7.50/18.
        if (name == "light-dumbbells") assertTrue("light dumbbells never got heavier", finalTop > firstTop)
        else assertTrue("six unchanged MISSED prescriptions without recovery: $stalls", stalls.isEmpty())
    }
}
