// Pure working-target calculations and easier-variation lookup for 3B baseline tests.
import { STRETCH_IDS } from '../data/seed/exercise-catalog'
import type { ExerciseEntity } from './entities'
import {
  ALL_ACTIVE_EQUIPMENT, expandLegacyEquipment, profileFits, profileFor,
  type Equipment,
} from './exercise-swap-engine'
import { harderVersion, prevInLineId, tierOf, TIERS } from './exercise-tiers'
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

/** A bodyweight reps test at or above this many reps means the move is outgrown (progression caps near 15). */
export const OUTGROWN_REPS = 25

/** True for a REPS-kind max-reps result of OUTGROWN_REPS or more; WEIGHTED and HOLD results never are. */
export function isOutgrown(kind: ExerciseEntity['kind'], maxReps: number): boolean {
  return kind === 'REPS' && Number.isFinite(maxReps) && maxReps >= OUTGROWN_REPS
}

/** The next step up that fits `equipment` (reuses ExerciseTiers.harderVersion), or null. */
export function harderVariation(
  exercise: ExerciseEntity,
  catalog: readonly ExerciseEntity[],
  equipment: ReadonlySet<Equipment> = new Set(),
): ExerciseEntity | null {
  return harderVersion(exercise, catalog as ExerciseEntity[], equipment as Set<Equipment>)
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

/**
 * Walks easier along the same progression line in `exercise-tiers` until one fits `equipment`,
 * else the easiest same-movement-pattern exercise that `equipment` allows (ties by id), else null.
 */
export function easierVariation(
  exercise: ExerciseEntity,
  catalog: readonly ExerciseEntity[],
  equipment: ReadonlySet<Equipment> = new Set(),
): ExerciseEntity | null {
  if (STRETCH_IDS.has(exercise.id)) return null
  const exp = expandLegacyEquipment(equipment)
  const allowed = new Set<Equipment>(exp.size ? [...exp, 'BODYWEIGHT'] : [...ALL_ACTIVE_EQUIPMENT, 'BODYWEIGHT'])
  const byId = new Map<string, ExerciseEntity>(catalog.map((e) => [e.id, e]))

  let curId: string | undefined = exercise.id
  const visited = new Set<string>()
  while (curId != null && !visited.has(curId)) {
    visited.add(curId)
    const prevId = prevInLineId(curId)
    if (!prevId) break
    const candidate = byId.get(prevId)
    if (
      candidate &&
      !STRETCH_IDS.has(candidate.id) &&
      candidate.kind === exercise.kind &&
      profileFits(profileFor(candidate), allowed)
    ) {
      return candidate
    }
    curId = prevId
  }

  const srcTier = tierOf(exercise)
  if (srcTier === 'FOUNDATION') return null
  const srcOrd = srcTier ? TIERS.indexOf(srcTier) : null
  const pattern = profileFor(exercise).pattern
  let best: ExerciseEntity | null = null
  let bestOrd = Infinity
  for (const c of catalog) {
    if (c.id === exercise.id || STRETCH_IDS.has(c.id) || c.kind !== exercise.kind) continue
    const profile = profileFor(c)
    if (profile.pattern !== pattern || !profileFits(profile, allowed)) continue
    const candTier = tierOf(c)
    if (!candTier) continue
    const candOrd = TIERS.indexOf(candTier)
    if (srcOrd != null && candOrd >= srcOrd) continue
    if (!best || candOrd < bestOrd || (candOrd === bestOrd && c.id < best.id)) {
      best = c
      bestOrd = candOrd
    }
  }
  return best
}
