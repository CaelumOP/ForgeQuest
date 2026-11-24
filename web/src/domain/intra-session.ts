// Pure intra-session adjustment rules: after a working set hits Failure or misses the target rep minimum by 2+,
// adjusts remaining unticked, unedited working sets of the same exercise, and computes Failure rest extensions.
import type { PlanItemEntity, SetEntity } from './entities'
import type { ExerciseKind } from './muscle'
import { countsForProgression } from './set-types'
import { fromKg, loadStep, toKg, type WeightUnit } from './units'

const f = Math.fround

export const FAILURE_REST_BONUS_SEC = 45
export const FAILURE_REST_CAP_SEC = 240

export type IntraSessionSet = Pick<
  SetEntity,
  'exerciseId' | 'itemPosition' | 'setIndex' | 'reps' | 'seconds' | 'weightKg' | 'done' | 'effort'
> & {
  id?: number
  setType?: string
  block?: string
  autoTicked?: boolean
}

export type AdjustmentResult<T extends IntraSessionSet = IntraSessionSet> = {
  changed: T[]
  percent?: number
  reps?: number
  seconds?: number
}

function floorToStep(value: number, step: number): number {
  return f(Math.floor(f(value) / f(step) + 1e-4) * f(step))
}

/** Percentage drop for a triggered weighted set: 10% when 3+ reps under `repsMin`, else 5%. */
export function percentCut(source: Pick<IntraSessionSet, 'reps'>, repsMin: number | null): number {
  const miss = repsMin != null && source.reps != null ? Math.max(0, repsMin - source.reps) : 0
  return miss >= 3 ? 10 : 5
}

/** Extends `currentTotalSec` by 45 s capped at 240 s, never reducing an already longer timer. */
export function extendedRest(currentTotalSec: number): number {
  return Math.max(currentTotalSec, Math.min(currentTotalSec + FAILURE_REST_BONUS_SEC, FAILURE_REST_CAP_SEC))
}

/**
 * Computes adjusted copies of `remaining` when `source` triggers an intra-session adjustment, or null if none.
 * Caller filters `remaining` (same itemPosition + exerciseId, higher setIndex, not done, not hand-edited, countsForProgression).
 */
export function computeAdjustment<T extends IntraSessionSet>(
  source: IntraSessionSet,
  remaining: readonly T[],
  kind: ExerciseKind,
  repsMin: number | null,
  unit: WeightUnit,
): AdjustmentResult<T> | null {
  if (!countsForProgression(source) || remaining.length === 0) return null
  const isFailure = source.effort === 'FAILURE' || (source.effort.trim() === '' && source.setType === 'FAILURE')
  const repsMiss = kind !== 'HOLD' && repsMin != null && source.reps != null && source.reps <= repsMin - 2
  if (!isFailure && !repsMiss) return null

  switch (kind) {
    case 'WEIGHTED': {
      const wKg = source.weightKg ?? 0
      if (wKg <= 0) return null
      const cut = percentCut(source, repsMin)
      const factor = cut === 10 ? f(0.9) : f(0.95)
      const step = loadStep(unit)
      const displayW = fromKg(unit, wKg)
      const newWeightKg =
        displayW <= step + 1e-3
          ? wKg
          : toKg(unit, Math.max(step, Math.min(floorToStep(f(displayW * factor), step), f(displayW - step))))
      const droppedLoad = newWeightKg < wKg - 1e-4
      // A lighter load aims for the bottom of the range again; at the lightest load, aim for what was done.
      const targetReps = droppedLoad ? (repsMin ?? source.reps) : source.reps
      const changed: T[] = []
      for (const next of remaining) {
        const nextReps =
          targetReps != null ? (droppedLoad ? targetReps : Math.min(targetReps, next.reps ?? targetReps)) : next.reps
        if (next.weightKg !== newWeightKg || next.reps !== nextReps) {
          changed.push({ ...next, weightKg: newWeightKg, reps: nextReps })
        }
      }
      if (changed.length === 0) return null
      return droppedLoad ? { changed, percent: cut } : targetReps != null ? { changed, reps: targetReps } : { changed }
    }
    case 'REPS': {
      if (source.reps == null) return null
      const targetReps = Math.max(1, source.reps)
      // Never raises a target: failing at 12 when 10 was planned leaves the 10.
      const changed: T[] = []
      for (const next of remaining) {
        const nextReps = Math.min(targetReps, next.reps ?? targetReps)
        if (nextReps !== next.reps) {
          changed.push({ ...next, reps: nextReps })
        }
      }
      if (changed.length === 0) return null
      return { changed, reps: targetReps }
    }
    case 'HOLD': {
      if (!isFailure || source.seconds == null) return null
      const targetSec = Math.max(1, source.seconds)
      const changed: T[] = []
      for (const next of remaining) {
        const nextSec = Math.min(targetSec, next.seconds ?? targetSec)
        if (nextSec !== next.seconds) {
          changed.push({ ...next, seconds: nextSec })
        }
      }
      if (changed.length === 0) return null
      return { changed, seconds: targetSec }
    }
  }
}

/** The later working sets of `source`'s exercise that are still open for automatic changes. */
export function openLaterSets<T extends IntraSessionSet>(
  all: readonly T[],
  source: IntraSessionSet,
  isEdited: (id: number) => boolean,
): T[] {
  return all
    .filter(
      (s) =>
        s.itemPosition === source.itemPosition &&
        s.exerciseId === source.exerciseId &&
        s.setIndex > source.setIndex &&
        !s.done &&
        s.id != null &&
        !isEdited(s.id) &&
        countsForProgression(s),
    )
    .sort((a, b) => a.setIndex - b.setIndex)
}

/** Copies `values`' numbers onto matching rows from `current`, skipping rows ticked or hand-edited in the meantime. */
export function numbersToRestore<T extends IntraSessionSet>(
  values: readonly IntraSessionSet[],
  current: readonly T[],
  isEdited: (id: number) => boolean,
): T[] {
  const byId = new Map<number, T>()
  for (const s of current) {
    if (s.id != null) byId.set(s.id, s)
  }
  const out: T[] = []
  for (const v of values) {
    if (v.id == null) continue
    const cur = byId.get(v.id)
    if (!cur || cur.done || isEdited(v.id)) continue
    if (cur.reps !== v.reps || cur.seconds !== v.seconds || cur.weightKg !== v.weightKg) {
      out.push({ ...cur, reps: v.reps, seconds: v.seconds, weightKg: v.weightKg })
    }
  }
  return out
}

/** Plan item's repsMin when it matches the set's exercise; fallback 8 for WEIGHTED, null for HOLD and plan-less REPS. */
export function resolveRepsMin(
  set: Pick<IntraSessionSet, 'exerciseId'>,
  kind: ExerciseKind,
  planItem: Pick<PlanItemEntity, 'exerciseId' | 'repsMin'> | null | undefined,
): number | null {
  if (kind === 'HOLD') return null
  if (planItem != null && planItem.exerciseId === set.exerciseId && planItem.repsMin != null) {
    return planItem.repsMin
  }
  return kind === 'WEIGHTED' ? 8 : null
}
