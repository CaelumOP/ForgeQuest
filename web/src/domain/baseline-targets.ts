// Pure working-target calculations and easier-variation lookup for 3B baseline tests.
import { STRETCH_IDS } from '../data/seed/exercise-catalog'
import type { ExerciseEntity } from './entities'
import {
  ALL_ACTIVE_EQUIPMENT, expandLegacyEquipment, profileFits, profileFor,
  type Equipment, type MovementPattern,
} from './exercise-swap-engine'
import { NEXT_IN_TIER, tierOf, TIERS, type Tier } from './exercise-tiers'
import { STAPLE_IDS } from './plan-builder'
import { fromKg, loadStep, toKg, type WeightUnit } from './units'

export type WeightedTarget =
  | { kind: 'TARGET'; kg: number }
  | { kind: 'TOO_LIGHT' }
  | { kind: 'NONE' }

/** Working rep target from a max-reps test: null when max <= 0, else max(1, floor(0.7 * max)). */
export function repsTarget(max: number): number | null {
  if (!Number.isFinite(max) || max <= 0) return null
  return Math.max(1, Math.floor(0.7 * max + 1e-9))
}

/** Working hold target (seconds) from a max-hold test: null when <= 0, else max(5, floor(0.7 * s / 5) * 5). */
export function holdTarget(seconds: number): number | null {
  if (!Number.isFinite(seconds) || seconds <= 0) return null
  return Math.max(5, Math.floor((0.7 * seconds) / 5 + 1e-9) * 5)
}

/**
 * Working load from a submax weighted test set, snapped down to `step`.
 * Reps > 12 returns TOO_LIGHT; invalid inputs or snapped load below one step return NONE.
 */
export function weightedTarget(
  weightKg: number,
  reps: number,
  repsMin: number,
  step: number,
): WeightedTarget {
  if (!Number.isFinite(weightKg) || !Number.isFinite(reps) || !Number.isFinite(step) || weightKg <= 0 || reps <= 0 || step <= 0) {
    return { kind: 'NONE' }
  }
  if (reps > 12) return { kind: 'TOO_LIGHT' }
  const safeRepsMin = Number.isFinite(repsMin) && repsMin >= 1 ? repsMin : 1
  const e1rm = weightKg * (1 + reps / 30)
  const raw = (e1rm / (1 + (safeRepsMin + 3) / 30)) * 0.9
  const snapped = Math.floor(raw / step + 1e-9) * step
  if (snapped < step - 1e-9) return { kind: 'NONE' }
  return { kind: 'TARGET', kg: Math.round(snapped * 10000) / 10000 }
}

/** Weighted target snapped to the user's display unit load step, returning kilograms. */
export function weightedTargetInUnit(
  weightKg: number,
  reps: number,
  repsMin: number,
  unit: WeightUnit,
): WeightedTarget {
  const step = loadStep(unit)
  const res = weightedTarget(fromKg(unit, weightKg), reps, repsMin, step)
  if (res.kind !== 'TARGET') return res
  return { kind: 'TARGET', kg: toKg(unit, res.kg) }
}

const PREV_IN_TIER: Record<string, string> = (() => {
  const out: Record<string, string> = {}
  for (const [easier, harder] of Object.entries(NEXT_IN_TIER)) {
    if (!(harder in out)) out[harder] = easier
  }
  return out
})()

const tierIndex = (t: Tier | null): number => (t ? TIERS.indexOf(t) : TIERS.length)

/**
 * Finds an easier variation for a bodyweight REPS move: first one tier easier in the same
 * progression line (or explicit in-tier predecessor), else the easiest same-movement-pattern
 * exercise allowed by `equipment`, else null.
 */
export function easierVariation(
  exercise: ExerciseEntity,
  catalog: readonly ExerciseEntity[],
  equipment: ReadonlySet<Equipment>,
): ExerciseEntity | null {
  if (exercise.kind !== 'REPS') return null
  const exp = expandLegacyEquipment(equipment)
  const allowed = new Set<Equipment>(exp.size ? [...exp, 'BODYWEIGHT'] : ALL_ACTIVE_EQUIPMENT)

  const prevId = PREV_IN_TIER[exercise.id]
  const named = prevId ? catalog.find((e) => e.id === prevId) : undefined
  if (named && profileFits(profileFor(named), allowed)) return named

  const tier = tierOf(exercise)
  const pattern = profileFor(exercise).pattern
  const fits = catalog.filter(
    (e) =>
      e.id !== exercise.id &&
      e.kind === 'REPS' &&
      !STRETCH_IDS.has(e.id) &&
      profileFits(profileFor(e), allowed),
  )

  const pick = (p: MovementPattern, targetTier: Tier | null): ExerciseEntity | null => {
    let best: ExerciseEntity | null = null
    const rank = (c: ExerciseEntity): [number, number, number, number, string] => [
      targetTier == null ? tierIndex(tierOf(c)) : 0,
      -c.primary.filter((m) => exercise.primary.includes(m)).length,
      STAPLE_IDS.has(c.id) ? 0 : 1,
      c.name.length,
      c.id,
    ]
    for (const c of fits) {
      if (profileFor(c).pattern !== p) continue
      if (targetTier != null && tierOf(c) !== targetTier) continue
      if (!best) {
        best = c
        continue
      }
      const a = rank(c)
      const b = rank(best)
      const cmp = a[0] - b[0] || a[1] - b[1] || a[2] - b[2] || a[3] - b[3] || (a[4] < b[4] ? -1 : a[4] > b[4] ? 1 : 0)
      if (cmp < 0) best = c
    }
    return best
  }

  if (tier === 'ADVANCED') {
    return pick(pattern, 'INTERMEDIATE') ?? pick(pattern, 'FOUNDATION')
  }
  if (tier === 'INTERMEDIATE') {
    return pick(pattern, 'FOUNDATION')
  }
  if (tier === null) {
    return pick(pattern, null)
  }
  return null
}
