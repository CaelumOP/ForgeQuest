package com.forgequest.app.domain

/**
 * Muscle groups the app tracks. [slug] matches the region names in assets/body_map.json
 * so recovery colors can be painted straight onto the anatomy art.
 */
enum class Muscle(val slug: String, val label: String) {
    CHEST("chest", "Chest"),
    SHOULDERS("deltoids", "Shoulders"),
    BICEPS("biceps", "Biceps"),
    TRICEPS("triceps", "Triceps"),
    FOREARMS("forearm", "Forearms"),
    ABS("abs", "Abs"),
    OBLIQUES("obliques", "Obliques"),
    TRAPS("trapezius", "Traps"),
    UPPER_BACK("upper-back", "Lats & upper back"),
    LOWER_BACK("lower-back", "Lower back"),
    GLUTES("gluteal", "Glutes"),
    QUADS("quadriceps", "Quads"),
    HAMSTRINGS("hamstring", "Hamstrings"),
    ADDUCTORS("adductors", "Adductors"),
    CALVES("calves", "Calves"),
    TIBIALIS("tibialis", "Shins");

    companion object {
        private val bySlug = entries.associateBy { it.slug }
        fun fromSlug(slug: String): Muscle? = bySlug[slug]
    }
}

/** How a set is measured. */
enum class ExerciseKind {
    /** Bodyweight reps, e.g. push-ups. */
    REPS,

    /** Timed hold in seconds, e.g. plank, dead hang. */
    HOLD,

    /** Reps with added or external load in kg, e.g. weighted dips, bench press. */
    WEIGHTED,
}
