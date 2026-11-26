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

/** Ordered harder candidates tried first by harderStep (either kind): the first that is in scope and fits the gear wins. */
export const HARDER_IN_LINE: Record<string, string[]> = {
  push_up: ['diamond_push_up', 'decline_push_up'],
  knee_push_up: ['push_up'],
  // Diamond is catalogued as elbow extension, which has no harder move without gear: keep the push-up line climbing.
  diamond_push_up: ['archer_push_up'],
  negative_pull_up: ['pull_up'],
  pull_up: ['archer_pull_up', 'l_sit_pull_up'],
  chin_up: ['archer_pull_up'],
  australian_pull_up: ['pull_up', 'chin_up'],
  squat: ['jump_squat'],
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

/** True for a move the starting-point ladder reasons about: reps or hold, built-in, not a stretch, not mobility or conditioning. */
export function isProgressionMove(ex: ExerciseEntity): boolean {
  if (ex.kind !== 'REPS' && ex.kind !== 'HOLD') return false
  if (ex.custom || STRETCH_IDS.has(ex.id)) return false
  const p = profileFor(ex).pattern
  return p !== 'MOBILITY' && p !== 'CARRY_CONDITIONING'
}

/** The two core patterns share one family; every other pattern is its own. */
export const patternFamily = (p: MovementPattern): string =>
  p === 'CORE_ANTI_EXTENSION' || p === 'CORE_FLEXION_ROTATION' ? 'core' : p

export const allowedEquipment = (equipment: ReadonlySet<Equipment>): Set<Equipment> => {
  const exp = expandLegacyEquipment(equipment as Set<Equipment>)
  return new Set<Equipment>(exp.size ? [...exp, 'BODYWEIGHT'] : [...ALL_ACTIVE_EQUIPMENT, 'BODYWEIGHT'])
}

/** Deterministic pick: same pattern, most primary muscles shared with the source, same kind, shorter name, id. */
export function pickCandidate(src: ExerciseEntity, cands: ExerciseEntity[]): ExerciseEntity | null {
  const pattern = profileFor(src).pattern
  const key = (c: ExerciseEntity) => [
    profileFor(c).pattern === pattern ? 0 : 1,
    -c.primary.filter((m) => src.primary.includes(m)).length,
    c.kind === src.kind ? 0 : 1,
    c.name.length,
  ]
  let best: ExerciseEntity | null = null
  let bestKey: number[] = []
  for (const c of cands) {
    const k = key(c)
    let better = best == null
    if (!better) {
      better = c.id < best!.id
      for (let i = 0; i < k.length; i++) {
        if (k[i] !== bestKey[i]) { better = k[i] < bestKey[i]; break }
      }
    }
    if (better) { best = c; bestKey = k }
  }
  return best
}

/**
 * The next step up from a reps or hold move that fits `equipment` (empty = not set, so anything), either kind:
 * the named line, the next in tier, the same family one tier up, the related pattern (same tier, then one up), the
 * same family two tiers up. Advanced moves only use the named steps. Null at the true top for the gear.
 */
export function harderStep(ex: ExerciseEntity, library: readonly ExerciseEntity[], equipment: ReadonlySet<Equipment>): ExerciseEntity | null {
  if (!isProgressionMove(ex)) return null
  const tier = tierOf(ex)
  if (!tier) return null
  const allowed = allowedEquipment(equipment)
  const ok = (c: ExerciseEntity) => c.id !== ex.id && isProgressionMove(c) && profileFits(profileFor(c), allowed)
  for (const id of HARDER_IN_LINE[ex.id] ?? []) {
    const c = library.find((e) => e.id === id)
    if (c && ok(c)) return c
  }
  const nextId = NEXT_IN_TIER[ex.id]
  const named = nextId ? library.find((e) => e.id === nextId) : undefined
  if (named && ok(named)) return named
  if (tier === 'ADVANCED') return null
  const fits = library.filter(ok)
  const pattern = profileFor(ex).pattern
  const family = patternFamily(pattern)
  const tierIdx = TIERS.indexOf(tier)
  const inFamily = (t: number) => fits.filter((c) => patternFamily(profileFor(c).pattern) === family && tierOf(c) === TIERS[t])
  const inPattern = (p: MovementPattern, t: number) => fits.filter((c) => profileFor(c).pattern === p && tierOf(c) === TIERS[t])
  const up = pickCandidate(ex, inFamily(tierIdx + 1))
  if (up) return up
  const related = RELATED[pattern]
  if (related) {
    const same = pickCandidate(ex, inPattern(related, tierIdx))
    if (same) return same
    const relUp = pickCandidate(ex, inPattern(related, tierIdx + 1))
    if (relUp) return relUp
  }
  return tierIdx + 2 < TIERS.length ? pickCandidate(ex, inFamily(tierIdx + 2)) : null
}

/**
 * The next step up from a REPS `ex` that is itself a reps move (live workout hint and debrief); null when the step
 * is a hold or there is none: the screen then suggests a slower tempo instead.
 */
export function harderVersion(ex: ExerciseEntity, library: readonly ExerciseEntity[], equipment: ReadonlySet<Equipment>): ExerciseEntity | null {
  if (ex.kind !== 'REPS') return null
  const step = harderStep(ex, library, equipment)
  return step && step.kind === 'REPS' ? step : null
}

/**
 * The next step down from a reps or hold move (a weighted move stays with weighted moves): the named line first,
 * walking down until one fits, else the same family one tier lower, then two lower. Foundation moves with no named
 * step are the true bottom: null.
 */
export function easierStep(ex: ExerciseEntity, library: readonly ExerciseEntity[], equipment: ReadonlySet<Equipment>): ExerciseEntity | null {
  if (STRETCH_IDS.has(ex.id)) return null
  const allowed = allowedEquipment(equipment)
  const weighted = ex.kind === 'WEIGHTED'
  const ok = (c: ExerciseEntity) => {
    if (c.id === ex.id || c.custom || STRETCH_IDS.has(c.id) || !profileFits(profileFor(c), allowed)) return false
    if (weighted) {
      const p = profileFor(c).pattern
      return c.kind === 'WEIGHTED' && p !== 'MOBILITY' && p !== 'CARRY_CONDITIONING'
    }
    return isProgressionMove(c)
  }
  const byId = new Map(library.map((e) => [e.id, e]))
  let curId: string | undefined = ex.id
  const visited = new Set<string>()
  while (curId != null && !visited.has(curId)) {
    visited.add(curId)
    const prevId = prevInLineId(curId)
    if (!prevId) break
    const c = byId.get(prevId)
    if (c && ok(c)) return c
    curId = prevId
  }
  const tier = tierOf(ex)
  if (!tier || tier === 'FOUNDATION') return null
  const family = patternFamily(profileFor(ex).pattern)
  const tierIdx = TIERS.indexOf(tier)
  const fits = library.filter(ok)
  const inFamily = (t: number) => fits.filter((c) => patternFamily(profileFor(c).pattern) === family && tierOf(c) === TIERS[t])
  return pickCandidate(ex, inFamily(tierIdx - 1)) ?? (tierIdx - 2 >= 0 ? pickCandidate(ex, inFamily(tierIdx - 2)) : null)
}
