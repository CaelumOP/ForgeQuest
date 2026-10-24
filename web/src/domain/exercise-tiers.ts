// Difficulty of the built-in exercises (docs/plans/exercise-tiers.md) and the "ready for a harder version" ladder for
// bodyweight rep moves. Custom exercises have no tier. Android: ExerciseTiers.kt.
import type { ExerciseEntity } from './entities'
import { EQUIPMENT, profileFor, type Equipment, type MovementPattern } from './exercise-swap-engine'
import type { PastSet } from './progression'

/** How hard a movement is to learn and its leverage, not the load (the person picks the weight). */
export const TIERS = ['FOUNDATION', 'INTERMEDIATE', 'ADVANCED'] as const
export type Tier = (typeof TIERS)[number]

const INTERMEDIATE = new Set([
  'burpee', 'push_up', 'diamond_push_up', 'pike_push_up', 'pull_up', 'chin_up', 'dip', 'bulgarian_split_squat',
  'hollow_hold', 'hanging_knee_raise', 'weighted_push_up', 'bench_press', 'overhead_press', 'barbell_row', 'deadlift',
  'romanian_deadlift', 'back_squat', 'skull_crusher', 'kb_swing', 'kb_clean', 'turkish_get_up', 'db_lunge',
])
const ADVANCED = new Set([
  'pistol_squat', 'nordic_curl', 'hanging_leg_raise', 'muscle_up', 'l_sit', 'handstand_hold', 'handstand_push_up',
  'tuck_planche', 'pseudo_planche_push_up', 'tuck_front_lever', 'front_lever_row', 'back_lever_tuck',
  'weighted_pull_up', 'weighted_chin_up', 'weighted_dip', 'kb_snatch',
])

export function tierOf(ex: ExerciseEntity): Tier | null {
  if (ex.custom) return null
  if (ADVANCED.has(ex.id)) return 'ADVANCED'
  if (INTERMEDIATE.has(ex.id)) return 'INTERMEDIATE'
  return 'FOUNDATION'
}

/** Every set at this many reps, two sessions in a row, means the move has become easy. */
export const READY_REPS = 15

/** Steps inside one tier where the next move is obvious (half squats → full squats). */
const NEXT_IN_TIER: Record<string, string> = {
  half_squat: 'squat', half_wide_squat: 'squat', half_narrow_squat: 'squat',
  knee_scapular_push_up: 'knee_push_up',
  scapular_australian_pull_up: 'australian_pull_up', scapular_australian_chin_up: 'australian_chin_up',
}

/** When a movement has no harder version, the closest one that does: two legs → one leg, rows → pull-ups. */
const RELATED: Partial<Record<MovementPattern, MovementPattern>> = {
  SQUAT_BILATERAL: 'SQUAT_UNILATERAL',
  HORIZONTAL_PULL: 'VERTICAL_PULL',
}

/** True when both of the last two sessions had 2+ sets, all at READY_REPS+ reps, none marked Hard or Pain. */
export const readyForHarder = (last: PastSet[], previous: PastSet[]): boolean =>
  [last, previous].every((session) => session.length >= 2 &&
    session.every((s) => (s.reps ?? 0) >= READY_REPS && s.effort !== 'HARD' && s.effort !== 'PAIN'))

/**
 * The next step up from `ex` that fits `equipment` (empty = not set, so anything): a bodyweight rep move one tier up
 * in the same movement, else the related movement at the same tier (squat → lunge, rows → negative pull-up), else the
 * related movement one tier up. Null when there is none: the screen then suggests a slower tempo instead.
 */
export function harderVersion(ex: ExerciseEntity, library: ExerciseEntity[], equipment: Set<Equipment>): ExerciseEntity | null {
  if (ex.kind !== 'REPS') return null
  const tier = tierOf(ex)
  if (!tier) return null
  const allowed = new Set<Equipment>(equipment.size ? [...equipment, 'BODYWEIGHT'] : EQUIPMENT)
  const nextId = NEXT_IN_TIER[ex.id]
  const named = nextId ? library.find((e) => e.id === nextId) : undefined
  if (named && allowed.has(profileFor(named).equipment)) return named
  if (tier === 'ADVANCED') return null
  const up = TIERS[TIERS.indexOf(tier) + 1]
  const pattern = profileFor(ex).pattern
  const fits = library.filter((e) => e.id !== ex.id && e.kind === 'REPS' && allowed.has(profileFor(e).equipment))
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
