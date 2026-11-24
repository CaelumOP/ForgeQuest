package com.forgequest.app.domain

import kotlin.math.ln

/**
 * Pure 1-rep max estimation shared by progress metrics and personal record scoring.
 * Uses raw reps with no RIR credit: exact weight for a single, standard Epley for 2..12 reps,
 * and a logarithmic soft cap above 12 reps so high-rep sets never beat heavy strength work.
 */
object Strength {

    /**
     * Estimated 1-rep max in kilograms for [weightKg] lifted for [reps].
     * Returns 0 when [weightKg] is non-finite or <= 0, or when [reps] <= 0.
     */
    fun e1rm(weightKg: Float, reps: Int): Float {
        if (!weightKg.isFinite() || weightKg <= 0f || reps <= 0) return 0f
        if (reps == 1) return weightKg
        val effectiveReps = if (reps <= 12) reps.toFloat() else 12f + 3f * ln(1f + (reps - 12).toFloat())
        return weightKg * (1f + effectiveReps / 30f)
    }
}
