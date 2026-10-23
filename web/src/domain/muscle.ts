/** Muscle groups the app tracks. `slug` matches the region names in body_map.json. */
export const MUSCLES = [
  'CHEST', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'FOREARMS', 'ABS', 'OBLIQUES', 'TRAPS',
  'UPPER_BACK', 'LOWER_BACK', 'GLUTES', 'QUADS', 'HAMSTRINGS', 'ADDUCTORS', 'CALVES', 'TIBIALIS',
] as const
export type Muscle = (typeof MUSCLES)[number]

export const MUSCLE_INFO: Record<Muscle, { slug: string; label: string }> = {
  CHEST: { slug: 'chest', label: 'Chest' },
  SHOULDERS: { slug: 'deltoids', label: 'Shoulders' },
  BICEPS: { slug: 'biceps', label: 'Biceps' },
  TRICEPS: { slug: 'triceps', label: 'Triceps' },
  FOREARMS: { slug: 'forearm', label: 'Forearms' },
  ABS: { slug: 'abs', label: 'Abs' },
  OBLIQUES: { slug: 'obliques', label: 'Obliques' },
  TRAPS: { slug: 'trapezius', label: 'Traps' },
  UPPER_BACK: { slug: 'upper-back', label: 'Lats & upper back' },
  LOWER_BACK: { slug: 'lower-back', label: 'Lower back' },
  GLUTES: { slug: 'gluteal', label: 'Glutes' },
  QUADS: { slug: 'quadriceps', label: 'Quads' },
  HAMSTRINGS: { slug: 'hamstring', label: 'Hamstrings' },
  ADDUCTORS: { slug: 'adductors', label: 'Adductors' },
  CALVES: { slug: 'calves', label: 'Calves' },
  TIBIALIS: { slug: 'tibialis', label: 'Shins' },
}

export function muscleFromSlug(slug: string): Muscle | null {
  return MUSCLES.find((m) => MUSCLE_INFO[m].slug === slug) ?? null
}

export function isMuscle(raw: string): raw is Muscle {
  return (MUSCLES as readonly string[]).includes(raw)
}

/** How a set is measured: bodyweight reps, a timed hold in seconds, or reps with load in kg. */
export const EXERCISE_KINDS = ['REPS', 'HOLD', 'WEIGHTED'] as const
export type ExerciseKind = (typeof EXERCISE_KINDS)[number]

export function isExerciseKind(raw: string): raw is ExerciseKind {
  return (EXERCISE_KINDS as readonly string[]).includes(raw)
}
