// Pure working-target calculations and easier-variation lookup for 3B baseline tests.
import { STRETCH_IDS } from '../data/seed/exercise-catalog'
import type { ExerciseEntity } from './entities'
import {
  ALL_ACTIVE_EQUIPMENT, expandLegacyEquipment, profileFits, profileFor,
  type Equipment,
} from './exercise-swap-engine'
import { harderHoldVersion, harderVersion, prevInLineId, tierOf, TIERS } from './exercise-tiers'
import { fromKg, loadStep, toKg, type WeightUnit } from './units'

export type WeightedTarget =
  | { kind: 'TARGET'; kg: number }
  | { kind: 'TOO_LIGHT' }
  | { kind: 'TOO_HEAVY' }
  | { kind: 'NONE' }

/** Highest working-rep target we ever suggest. */
export const MAX_REPS_TARGET = 20
/** A bodyweight reps test at or above this many reps means the move is outgrown (progression caps near 15). */
export const OUTGROWN_REPS = 25
/** A reps test of 1..LOW_REPS means the move is too hard to start with. */
export const LOW_REPS = 3
export const MAX_HOLD_TARGET = 60
export const OUTGROWN_HOLD = 90
export const LOW_HOLD = 15
export const MAX_REPS_INPUT = 200
export const MAX_HOLD_INPUT = 600
export const MAX_WEIGHT_KG_INPUT = 500.0
export const MAX_WEIGHTED_REPS_INPUT = 100

const clampInt = (v: number, max: number): number => (Number.isFinite(v) ? Math.min(max, Math.max(0, Math.trunc(v))) : 0)
export const clampRepsInput = (v: number): number => clampInt(v, MAX_REPS_INPUT)
export const clampHoldInput = (v: number): number => clampInt(v, MAX_HOLD_INPUT)
export const clampWeightKgInput = (v: number): number => (Number.isFinite(v) ? Math.min(MAX_WEIGHT_KG_INPUT, Math.max(0, v)) : 0)
export const clampWeightedRepsInput = (v: number): number => clampInt(v, MAX_WEIGHTED_REPS_INPUT)

/** Working rep target from a max-reps test: null when max <= 0, else min(MAX_REPS_TARGET, max(1, floor(0.7 * max))). */
export function repsTarget(max: number): number | null {
  if (!Number.isFinite(max) || max <= 0) return null
  return Math.min(MAX_REPS_TARGET, Math.max(1, Math.floor(0.7 * max + 1e-9)))
}

/** True for a REPS max-reps result of OUTGROWN_REPS+ or a HOLD of OUTGROWN_HOLD+ seconds; WEIGHTED never. */
export function isOutgrown(kind: ExerciseEntity['kind'], value: number): boolean {
  if (!Number.isFinite(value)) return false
  return (kind === 'REPS' && value >= OUTGROWN_REPS) || (kind === 'HOLD' && value >= OUTGROWN_HOLD)
}

/** True for 1..LOW_REPS reps or 1..LOW_HOLD-1 seconds; 0 keeps the no-target path. */
export function isTooHard(kind: ExerciseEntity['kind'], value: number): boolean {
  return (kind === 'REPS' && value >= 1 && value <= LOW_REPS) || (kind === 'HOLD' && value >= 1 && value <= LOW_HOLD - 1)
}

/** Hold time text: under 60 s -> "45 <suffix>" (suffix is "s" or "ث"), else "m:ss" with Latin digits ("1:00", "13:45"). */
export function formatHoldSeconds(seconds: number, unitSuffix: string): string {
  const s = Math.max(0, Math.trunc(Number.isFinite(seconds) ? seconds : 0))
  if (s < 60) return `${s} ${unitSuffix}`
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/** The next step up that fits `equipment`: reps via harderVersion, holds via harderHoldVersion, weighted never. */
export function harderVariation(
  exercise: ExerciseEntity,
  catalog: readonly ExerciseEntity[],
  equipment: ReadonlySet<Equipment> = new Set(),
): ExerciseEntity | null {
  if (exercise.kind === 'REPS') return harderVersion(exercise, catalog as ExerciseEntity[], equipment as Set<Equipment>)
  if (exercise.kind === 'HOLD') return harderHoldVersion(exercise, catalog as ExerciseEntity[], equipment as Set<Equipment>)
  return null
}

/** Working hold target (seconds): null when <= 0, else min(max(5, floor(0.7 * s / 5) * 5), s, MAX_HOLD_TARGET). */
export function holdTarget(seconds: number): number | null {
  if (!Number.isFinite(seconds) || seconds <= 0) return null
  const grid = Math.max(5, Math.floor((0.7 * seconds) / 5 + 1e-9) * 5)
  return Math.min(grid, seconds, MAX_HOLD_TARGET)
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
  if (reps >= 1 && reps <= 2) return { kind: 'TOO_HEAVY' }
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
