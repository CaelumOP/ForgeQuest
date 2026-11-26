// Difficulty of the built-in exercises (docs/plans/exercise-tiers.md) and the "ready for a harder version" ladder for
// bodyweight rep moves. Custom exercises have no tier. Android: ExerciseTiers.kt.
import { STRETCH_IDS } from '../data/seed/exercise-catalog'
import { EXERCISE_LEVELS } from '../data/seed/exercise-levels'
import type { ExerciseEntity } from './entities'
import { ALL_ACTIVE_EQUIPMENT, expandLegacyEquipment, profileFits, profileFor, type Equipment, type MovementPattern } from './exercise-swap-engine'
import type { PastSet } from './progression'

/** How hard a movement is to learn and its leverage, not the load (the person picks the weight). */
export const TIERS = ['FOUNDATION', 'INTERMEDIATE', 'ADVANCED'] as const
export type Tier = (typeof TIERS)[number]

const INTERMEDIATE = new Set([
  'burpee', 'push_up', 'diamond_push_up', 'pike_push_up', 'pull_up', 'chin_up', 'dip', 'bulgarian_split_squat',
  'hollow_hold', 'hanging_knee_raise', 'weighted_push_up', 'bench_press', 'overhead_press', 'barbell_row', 'deadlift',
  'romanian_deadlift', 'back_squat', 'skull_crusher', 'kb_swing', 'kb_clean', 'turkish_get_up', 'db_lunge',
  'copenhagen_plank_short', 'cossack_squat',
  'single_leg_rdl', 'good_morning', 'kb_windmill',
  'neutral_grip_pull_up', 'wide_grip_pull_up', 'decline_push_up', 'single_leg_hip_thrust', 'incline_bench_press', 'front_squat',
  'table_inverted_row',
])
const ADVANCED = new Set([
  'pistol_squat', 'nordic_curl', 'hanging_leg_raise', 'muscle_up', 'l_sit', 'handstand_hold', 'handstand_push_up',
  'tuck_planche', 'pseudo_planche_push_up', 'tuck_front_lever', 'front_lever_row', 'back_lever_tuck',
  'weighted_pull_up', 'weighted_chin_up', 'weighted_dip', 'kb_snatch', 'copenhagen_plank_long',
  'archer_pull_up', 'l_sit_pull_up', 'archer_push_up',
  // Imported levels label explosive and unilateral push-ups as beginner.
  'plyo_push_up', 'single_arm_push_up', 'incline_push_up_depth_jump', 'drop_push',
])

export function tierOf(ex: ExerciseEntity): Tier | null {
  if (ex.custom) return null
  if (ADVANCED.has(ex.id)) return 'ADVANCED'
  if (INTERMEDIATE.has(ex.id)) return 'INTERMEDIATE'
  const lvl = EXERCISE_LEVELS[ex.id]
  if (lvl === 'expert') return 'ADVANCED'
  if (lvl === 'intermediate') return 'INTERMEDIATE'
  return 'FOUNDATION'
}

/** Every set at this many reps, two sessions in a row, means the move has become easy. */
export const READY_REPS = 15

/** Steps inside one tier where the next move is obvious (half squats → full squats). */
export const NEXT_IN_TIER: Record<string, string> = {
  half_squat: 'squat', half_wide_squat: 'squat', half_narrow_squat: 'squat',
  knee_scapular_push_up: 'knee_push_up',
  scapular_australian_pull_up: 'australian_pull_up', scapular_australian_chin_up: 'australian_chin_up',
}

/** Explicit easier steps along a progression line (Android ExerciseTiers.EASIER_IN_LINE). */
export const EASIER_IN_LINE: Record<string, string> = {
  // Push-up line
  pseudo_planche_push_up: 'push_up',
  archer_push_up: 'push_up',
  decline_push_up: 'push_up',
  push_up: 'knee_push_up',
  wide_knee_push_up: 'knee_scapular_push_up',
  knee_push_up: 'knee_scapular_push_up',
  diamond_push_up: 'incline_diamond_knee_push_up',
  handstand_push_up: 'pike_push_up',
  dip: 'bent_leg_bench_dip',
  // Pull-up & row lines
  muscle_up: 'pull_up',
  archer_pull_up: 'pull_up',
  l_sit_pull_up: 'pull_up',
  wide_grip_pull_up: 'pull_up',
  neutral_grip_pull_up: 'negative_pull_up',
  pull_up: 'negative_pull_up',
  chin_up: 'negative_pull_up',
  negative_pull_up: 'scapular_pull_up',
  australian_pull_up: 'scapular_australian_pull_up',
  australian_chin_up: 'scapular_australian_chin_up',
  // Squat & core lines
  squat: 'half_squat',
  pistol_squat: 'bulgarian_split_squat',
  bulgarian_split_squat: 'lunge',
  hanging_leg_raise: 'hanging_knee_raise',
  hanging_knee_raise: 'crunch',
  // Hold line
  plank: 'incline_hollow_plank',
  hollow_hold: 'plank',
  l_sit: 'hollow_hold',
}

/** Ordered harder candidates tried first by harderVersion: the first that exists, has the same kind and fits the gear wins. */
export const HARDER_IN_LINE: Record<string, string[]> = {
  push_up: ['diamond_push_up', 'decline_push_up'],
  knee_push_up: ['push_up'],
  pull_up: ['archer_pull_up', 'l_sit_pull_up'],
  chin_up: ['archer_pull_up'],
  australian_pull_up: ['pull_up', 'chin_up'],
  squat: ['jump_squat'],
}

/** Ordered harder candidates for holds, tried first by harderHoldVersion. */
export const HARDER_HOLD_IN_LINE: Record<string, string[]> = {
  plank: ['hollow_hold'],
  hollow_hold: ['l_sit'],
  side_plank: ['copenhagen_plank_short'],
}

export const prevInLineId = (id: string): string | undefined => EASIER_IN_LINE[id]

/** When a movement has no harder version, the closest one that does: two legs → one leg, rows → pull-ups. */
const RELATED: Partial<Record<MovementPattern, MovementPattern>> = {
  SQUAT_BILATERAL: 'SQUAT_UNILATERAL',
  HORIZONTAL_PULL: 'VERTICAL_PULL',
}

/**
 * True when both of the last two sessions had 2+ sets, all at the ready reps or more, none marked Failure or Pain.
 * The ready reps are READY_REPS, or two past the plan's top reps `topReps` when that is lower (audit S7): an 8-12
 * push-up done 14 x 3 twice is ready.
 */
export const readyForHarder = (last: PastSet[], previous: PastSet[], topReps: number | null = null): boolean => {
  const ready = topReps != null ? Math.min(READY_REPS, topReps + 2) : READY_REPS
  return [last, previous].every(
    (session) =>
      session.length >= 2 &&
      session.every((s) => (s.reps ?? 0) >= ready && s.effort !== 'FAILURE' && s.effort !== 'PAIN'),
  )
}

/**
 * The next step up from `ex` that fits `equipment` (empty = not set, so anything): a bodyweight rep move one tier up
 * in the same movement, else the related movement at the same tier (squat → lunge, rows → negative pull-up), else the
 * related movement one tier up. Null when there is none: the screen then suggests a slower tempo instead.
 */
export function harderVersion(ex: ExerciseEntity, library: ExerciseEntity[], equipment: Set<Equipment>): ExerciseEntity | null {
  if (ex.kind !== 'REPS') return null
  const tier = tierOf(ex)
  if (!tier) return null
  const exp = expandLegacyEquipment(equipment)
  const allowed = new Set<Equipment>(exp.size ? [...exp, 'BODYWEIGHT'] : ALL_ACTIVE_EQUIPMENT)
  for (const id of HARDER_IN_LINE[ex.id] ?? []) {
    const cand = library.find((e) => e.id === id)
    if (cand && cand.kind === ex.kind && profileFits(profileFor(cand), allowed)) return cand
  }
  const nextId = NEXT_IN_TIER[ex.id]
  const named = nextId ? library.find((e) => e.id === nextId) : undefined
  if (named && profileFits(profileFor(named), allowed)) return named
  if (tier === 'ADVANCED') return null
  const up = TIERS[TIERS.indexOf(tier) + 1]
  const pattern = profileFor(ex).pattern
  const fits = library.filter((e) => e.id !== ex.id && e.kind === 'REPS' && profileFits(profileFor(e), allowed))
  const pick = (p: MovementPattern | undefined, t: Tier): ExerciseEntity | null => {
    if (!p) return null
    // Closest muscles first, so a chest push-up steps up to a chest move, then the shorter (simpler) name.
    const score = (c: ExerciseEntity) => [c.primary.filter((m) => ex.primary.includes(m)).length, -c.name.length]
    let best: ExerciseEntity | null = null
    for (const c of fits) {
      if (tierOf(c) !== t || profileFor(c).pattern !== p) continue
      if (!best) { best = c; continue }
      const [a1, a2] = score(c)
      const [b1, b2] = score(best)
      if (a1 > b1 || (a1 === b1 && a2 > b2)) best = c
    }
    return best
  }
  return pick(pattern, up) ?? pick(RELATED[pattern], tier) ?? pick(RELATED[pattern], up)
}

/**
 * The next step up from a HOLD `ex` that fits `equipment`: a named harder hold first, else a hold of the same movement
 * pattern exactly one tier up (closest primary muscles, then id). Null when there is none.
 */
export function harderHoldVersion(ex: ExerciseEntity, library: ExerciseEntity[], equipment: Set<Equipment>): ExerciseEntity | null {
  if (ex.kind !== 'HOLD') return null
  const exp = expandLegacyEquipment(equipment)
  const allowed = new Set<Equipment>(exp.size ? [...exp, 'BODYWEIGHT'] : [...ALL_ACTIVE_EQUIPMENT, 'BODYWEIGHT'])
  for (const id of HARDER_HOLD_IN_LINE[ex.id] ?? []) {
    const cand = library.find((e) => e.id === id)
    if (cand && cand.kind === 'HOLD' && profileFits(profileFor(cand), allowed)) return cand
  }
  const tier = tierOf(ex)
  if (!tier || tier === 'ADVANCED') return null
  const up = TIERS[TIERS.indexOf(tier) + 1]
  const pattern = profileFor(ex).pattern
  if (pattern === 'MOBILITY' || pattern === 'CARRY_CONDITIONING') return null
  let best: ExerciseEntity | null = null
  let bestScore = -1
  for (const c of library) {
    if (c.id === ex.id || c.kind !== 'HOLD' || STRETCH_IDS.has(c.id)) continue
    const prof = profileFor(c)
    if (prof.pattern !== pattern || !profileFits(prof, allowed) || tierOf(c) !== up) continue
    const score = c.primary.filter((m) => ex.primary.includes(m)).length
    if (!best || score > bestScore || (score === bestScore && c.id < best.id)) {
      best = c
      bestScore = score
    }
  }
  return best
}
