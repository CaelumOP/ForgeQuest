// Body areas a person can ask the plan builder to work around. Pure data, no text: the UI names them. Android: Injury.kt.
import type { ExerciseEntity } from './entities'
import type { BiomechanicalProfile, MovementPattern } from './exercise-swap-engine'

export const INJURIES = ['SHOULDER', 'KNEE', 'LOWER_BACK', 'WRIST', 'ELBOW', 'NECK'] as const
export type Injury = (typeof INJURIES)[number]

export function isInjury(raw: string): raw is Injury {
  return (INJURIES as readonly string[]).includes(raw)
}

const PATTERN_BLOCKS: Partial<Record<Injury, ReadonlySet<MovementPattern>>> = {
  SHOULDER: new Set<MovementPattern>(['VERTICAL_PUSH', 'DIP_PUSH', 'LATERAL_RAISE']),
  ELBOW: new Set<MovementPattern>(['DIP_PUSH']),
}

/** Ids from ExerciseCatalog (all verified to exist). Half squat and bridges are deliberately not here. */
const ID_BLOCKS: Partial<Record<Injury, ReadonlySet<string>>> = {
  SHOULDER: new Set(['muscle_up']),
  KNEE: new Set(['pistol_squat', 'jumping_jacks', 'burpee']),
  LOWER_BACK: new Set(['barbell_row', 'back_squat', 'kb_swing', 'kb_snatch', 'russian_twist']),
  ELBOW: new Set(['skull_crusher']),
  NECK: new Set(['shrug']),
}

/** Id fragments, so every variant (handstand_hold, handstand_push_up, kb_deadlift ...) is covered. */
const ID_PARTS: Partial<Record<Injury, readonly string[]>> = {
  SHOULDER: ['handstand', 'planche'],
  LOWER_BACK: ['deadlift', 'sit_up'],
  // Floor push-ups load the wrist flat; dumbbell and incline presses stay allowed.
  WRIST: ['push_up', 'planche', 'handstand'],
  NECK: ['handstand', 'crunch'],
}

/** True when `ex` should not appear in a plan for someone with any of `injuries`. */
export function blocked(
  ex: ExerciseEntity,
  profile: BiomechanicalProfile,
  injuries: ReadonlySet<Injury> | readonly Injury[],
): boolean {
  for (const injury of injuries) {
    if (
      PATTERN_BLOCKS[injury]?.has(profile.pattern) ||
      ID_BLOCKS[injury]?.has(ex.id) ||
      (ID_PARTS[injury]?.some((part) => ex.id.includes(part)) ?? false) ||
      (injury === 'ELBOW' && ex.id.endsWith('dip'))
    ) {
      return true
    }
  }
  return false
}
