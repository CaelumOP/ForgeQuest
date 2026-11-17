package com.forgequest.app.ui.progress

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.countsForScore
import java.util.Locale
import kotlin.math.roundToInt

/** Pure calculations for Progress tab: weekly muscle volume, estimated 1RM (Epley), and strength-to-bodyweight. */
object ProgressMetrics {

    /**
     * Estimated sets this week per muscle using WP1a [countsForScore] eligibility.
     * Primary muscles receive 1.0 set; secondary muscles receive 0.5 set.
     */
    fun weeklyMuscleVolume(
        sessions: List<SessionEntity>,
        doneSets: List<SetEntity>,
        exercises: Map<String, ExerciseEntity>,
        today: Long = Dates.today(),
    ): Map<Muscle, Float> {
        val ws = Dates.weekStart(today)
        val weekSessionIds = sessions
            .filter { it.finishedAt != null && it.epochDay in ws..(ws + 6) }
            .mapTo(HashSet()) { it.id }
        if (weekSessionIds.isEmpty()) return emptyMap()

        val totals = mutableMapOf<Muscle, Float>()
        for (set in doneSets) {
            if (!set.done || set.sessionId !in weekSessionIds) continue
            if (set.exerciseId in ExerciseCatalog.STRETCH_IDS) continue
            if (!countsForScore(set)) continue
            val ex = exercises[set.exerciseId] ?: continue
            for (m in ex.primary) {
                totals[m] = (totals[m] ?: 0f) + 1f
            }
            for (m in ex.secondary) {
                totals[m] = (totals[m] ?: 0f) + 0.5f
            }
        }
        return totals
    }

    /** Epley estimated 1RM. Exact weight when reps == 1; 0 when weight <= 0 or reps <= 0. */
    fun epley1Rm(weight: Float, reps: Int): Float {
        if (weight <= 0f || reps <= 0) return 0f
        if (reps == 1) return weight
        return weight * (1f + reps / 30f)
    }

    /** Ratio of estimated 1RM (in kg) to body weight (in kg), or null when body weight is missing. */
    fun strengthToBodyweight(est1RmKg: Float, bodyWeightKg: Float?): Float? {
        if (est1RmKg <= 0f || bodyWeightKg == null || bodyWeightKg <= 0f) return null
        return est1RmKg / bodyWeightKg
    }

    private val PERCENTAGES = listOf(95, 90, 85, 80, 75, 70)

    fun epleyPercentages(est1Rm: Float): List<Pair<Int, Float>> =
        PERCENTAGES.map { pct -> pct to (est1Rm * pct / 100f) }

    fun formatSets(value: Float): String {
        val rounded = (value * 2f).roundToInt() / 2f
        return if (rounded % 1f == 0f) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }
}
