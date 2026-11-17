package com.forgequest.app.domain

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure plain-text workout recap generator for sharing on Android / Web.
 * Produces clean EN or AR summary text with date, set/duration/tonnage totals,
 * each exercise's best set, and PRs.
 */
object WorkoutRecap {
    data class RecapSet(
        val reps: Int?,
        val seconds: Int?,
        val weightKg: Float?,
        val isPr: Boolean,
        val countsForPr: Boolean,
    )

    data class RecapExercise(
        val exerciseId: String,
        val name: String,
        val kind: ExerciseKind,
        val unit: WeightUnit,
        val sets: List<RecapSet>,
    )

    data class Strings(
        val setsLabel: (Int) -> String,
        val minutesLabel: (Int) -> String,
        val totalLiftedLabel: (String) -> String,
        val bestLabel: (String) -> String,
        val prTag: String,
        val prsHeaderLabel: (String) -> String,
        val repsLabel: (Int) -> String,
        val secondsLabel: (Int) -> String,
        val listSeparator: String,
    )

    data class Input(
        val title: String,
        val epochDay: Long,
        val minutes: Int,
        val doneSetsCount: Int,
        val defaultUnit: WeightUnit,
        val exercises: List<RecapExercise>,
        val prs: List<String>,
        val arabic: Boolean,
    )

    fun format(input: Input, strings: Strings): String {
        val dateText = formatDate(input.epochDay, input.arabic)
        val header = listOf(input.title.trim(), dateText).filter { it.isNotBlank() }.joinToString(" · ")

        val totalKg = input.exercises.sumOf { ex ->
            ex.sets.filter { it.countsForPr || ex.sets.none { s -> s.countsForPr } }
                .sumOf { ((it.weightKg ?: 0f).coerceAtLeast(0f) * (it.reps ?: 0).coerceAtLeast(0)).toDouble() }
        }.toFloat()

        val metaParts = mutableListOf(
            strings.setsLabel(input.doneSetsCount),
            strings.minutesLabel(input.minutes),
        )
        if (totalKg > 0.5f) {
            val totalDisplay = formatWholeWeight(input.defaultUnit, totalKg)
            metaParts += strings.totalLiftedLabel(totalDisplay)
        }

        val lines = mutableListOf(header, metaParts.joinToString(" · "))

        val exLines = input.exercises.mapNotNull { ex ->
            val eligible = ex.sets.filter { it.countsForPr }.ifEmpty { ex.sets }
            val best = eligible.maxByOrNull {
                Records.score(ex.kind, it.reps, it.seconds, it.weightKg, ex.exerciseId)
            } ?: return@mapNotNull null
            val bestValue = formatSetValue(ex.kind, ex.unit, best, strings) ?: return@mapNotNull null
            val hasPr = ex.sets.any { it.isPr }
            val prSuffix = if (hasPr) " (${strings.prTag})" else ""
            "• ${ex.name} — ${strings.bestLabel(bestValue)}$prSuffix"
        }
        if (exLines.isNotEmpty()) {
            lines += ""
            lines += exLines
        }

        val distinctPrs = input.prs.distinct()
        if (distinctPrs.isNotEmpty()) {
            lines += ""
            lines += strings.prsHeaderLabel(distinctPrs.joinToString(strings.listSeparator))
        }
        return lines.joinToString("\n")
    }

    private fun formatSetValue(kind: ExerciseKind, unit: WeightUnit, set: RecapSet, strings: Strings): String? = when (kind) {
        ExerciseKind.HOLD -> set.seconds?.takeIf { it > 0 }?.let(strings.secondsLabel)
        ExerciseKind.WEIGHTED -> {
            val r = set.reps?.takeIf { it > 0 } ?: return null
            val w = set.weightKg ?: 0f
            if (w > 0f) "${unit.format(w)} × $r" else strings.repsLabel(r)
        }
        ExerciseKind.REPS -> {
            val r = set.reps?.takeIf { it > 0 } ?: return null
            val base = strings.repsLabel(r)
            val w = set.weightKg ?: 0f
            if (w > 0f) "$base · +${unit.format(w)}" else base
        }
    }

    private fun formatWholeWeight(unit: WeightUnit, kg: Float): String {
        val converted = unit.fromKg(kg).roundToInt()
        val formatted = NumberFormat.getIntegerInstance(Locale.US).format(converted)
        return "$formatted ${unit.label}"
    }

    internal fun formatDate(epochDay: Long, arabic: Boolean): String {
        val locale = if (arabic) Locale.forLanguageTag("ar-u-nu-latn") else Locale.US
        val pattern = if (arabic) "d MMM yyyy" else "MMM d, yyyy"
        return LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}
