package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRecapTest {
    private val enStrings = WorkoutRecap.Strings(
        setsLabel = { "$it sets" },
        minutesLabel = { "$it min" },
        totalLiftedLabel = { "$it lifted" },
        bestLabel = { "best $it" },
        prTag = "PR",
        prsHeaderLabel = { "PRs: $it" },
        repsLabel = { "$it reps" },
        secondsLabel = { "${it}s" },
        listSeparator = ", ",
    )

    private val arStrings = WorkoutRecap.Strings(
        setsLabel = { "$it جلسات" },
        minutesLabel = { "$it د" },
        totalLiftedLabel = { "الإجمالي $it" },
        bestLabel = { "الأفضل $it" },
        prTag = "رقم قياسي",
        prsHeaderLabel = { "أرقام قياسية: $it" },
        repsLabel = { "$it عدّات" },
        secondsLabel = { "${it} ث" },
        listSeparator = "، ",
    )

    @Test
    fun formatsEnglishRecapWithMixedUnitsAndPrsAndExcludesWarmupsFromBest() {
        val text = WorkoutRecap.format(
            input = WorkoutRecap.Input(
                title = "Push A",
                epochDay = 20_500L,
                minutes = 42,
                doneSetsCount = 6,
                defaultUnit = WeightUnit.KG,
                exercises = listOf(
                    WorkoutRecap.RecapExercise(
                        exerciseId = "barbell_bench_press",
                        name = "Bench Press",
                        kind = ExerciseKind.WEIGHTED,
                        unit = WeightUnit.LB, // exercise unitOverride respected
                        sets = listOf(
                            // Warm-up with high weight shouldn't count when working sets countForPr=true
                            WorkoutRecap.RecapSet(reps = 5, seconds = null, weightKg = WeightUnit.LB.toKg(250f), isPr = false, countsForPr = false),
                            WorkoutRecap.RecapSet(reps = 8, seconds = null, weightKg = WeightUnit.LB.toKg(185f), isPr = true, countsForPr = true),
                        ),
                    ),
                    WorkoutRecap.RecapExercise(
                        exerciseId = "plank",
                        name = "Plank",
                        kind = ExerciseKind.HOLD,
                        unit = WeightUnit.KG,
                        sets = listOf(
                            WorkoutRecap.RecapSet(reps = null, seconds = 75, weightKg = null, isPr = false, countsForPr = true),
                        ),
                    ),
                ),
                prs = listOf("Bench Press", "Bench Press"),
                arabic = false,
            ),
            strings = enStrings,
        )

        val plain = text.replace(Regex("[\u2066\u2067\u2069]"), "")
        assertTrue(plain.contains("Push A · "))
        assertTrue(plain.contains("6 sets · 42 min"))
        assertTrue(plain.contains("Bench Press — best 185 lb × 8 (PR)"))
        assertFalse(plain.contains("250"))
        assertTrue(plain.contains("Plank — best 75s"))
        assertTrue(plain.contains("PRs: Bench Press"))
        assertEquals("\u2066100 kg\u2069", WeightUnit.KG.format(100f))
        assertEquals("\u2066102.5 kg\u2069", WeightUnit.KG.format(102.5f))
    }

    @Test
    fun formatsArabicRecapWithLatinDigitsAndRepsExercise() {
        val text = WorkoutRecap.format(
            input = WorkoutRecap.Input(
                title = "علوي أ",
                epochDay = 20_500L,
                minutes = 35,
                doneSetsCount = 4,
                defaultUnit = WeightUnit.KG,
                exercises = listOf(
                    WorkoutRecap.RecapExercise(
                        exerciseId = "pull_up",
                        name = "سحب عالي",
                        kind = ExerciseKind.REPS,
                        unit = WeightUnit.KG,
                        sets = listOf(
                            WorkoutRecap.RecapSet(reps = 12, seconds = null, weightKg = null, isPr = false, countsForPr = true),
                        ),
                    ),
                ),
                prs = emptyList(),
                arabic = true,
            ),
            strings = arStrings,
        )

        assertTrue(text.contains("4 جلسات · 35 د"))
        assertTrue(text.contains("سحب عالي — الأفضل 12 عدّات"))
        assertFalse(text.contains("أرقام قياسية"))
    }
}
