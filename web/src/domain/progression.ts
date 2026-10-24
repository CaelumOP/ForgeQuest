// Today's prefill for one exercise from the last two times it was done. Same rules as Android Progression.kt:
// - Rep range (e.g. 8–12): top of the range on every set adds one load step and resets to the bottom.
// - Single rep target (e.g. 3×8): load only goes up after hitting it in two sessions in a row at the same weight.
// - Holds: +5 s per session up to 1.5× the plan's target, then they stop and the screen suggests a harder version.
// - Hard or Pain on any set, a missed set, or a low-readiness day keeps last session's numbers.
//   Pain also takes the load back one step (bodyweight moves keep their reps).
// - Load steps follow the person's unit: +2.5 kg or +5 lb, landing on numbers they can actually load.
// Kotlin uses 32-bit Float here, so weights go through Math.fround.
import type { SetEffort } from './effort'
import type { ExerciseKind } from './muscle'
import { fromKg, loadStep, toKg, type WeightUnit } from './units'

const f = Math.fround

/** One finished set as the rules need it. */
export type PastSet = { reps: number | null; seconds: number | null; weightKg: number | null; effort: SetEffort }
export type Target = { sets: number; repsMin: number | null; repsMax: number | null; seconds: number | null; weightKg: number | null }
export type Prefill = { reps: number | null; seconds: number | null; weightKg: number | null }
export type ProgressionResult = { sets: Prefill[]; holdCeilingReached: boolean }

export const HOLD_STEP_SEC = 5
export const HOLD_CEILING = 1.5

export function nextPrefill(
  kind: ExerciseKind,
  target: Target | null,
  last: readonly PastSet[],
  previous: readonly PastSet[],
  unit: WeightUnit,
  lowReadiness: boolean,
  warmup: boolean,
): ProgressionResult {
  const count = target?.sets ?? 3
  const repsMin = target?.repsMin ?? 8
  const repsMax = target?.repsMax ?? target?.repsMin ?? 12
  const holdTarget = target?.seconds ?? 30
  // The default 8–12 is a range too, so it resets to 8 after a load step (audit 2026-10-24).
  const hasRange = repsMax > repsMin

  const pain = last.some((s) => s.effort === 'PAIN')
  const strained = pain || last.some((s) => s.effort === 'HARD')
  // "Completed" means every set was done without a hard or painful one; only then may anything go up.
  const completed = !warmup && last.length >= count && !strained
  const mayProgress = completed && !lowReadiness

  const hitTop = (session: readonly PastSet[]) =>
    session.length >= count &&
    (kind === 'HOLD' ? session.every((s) => (s.seconds ?? 0) >= holdTarget) : session.every((s) => (s.reps ?? 0) >= repsMax))
  const sessionWeight = (session: readonly PastSet[]) =>
    session.length === 0 ? 0 : Math.max(...session.map((s) => s.weightKg ?? 0))

  const holdCeiling = Math.max(roundTo5(Math.round(f(holdTarget * HOLD_CEILING))), holdTarget)
  let ceilingReached = false

  const sets = Array.from({ length: count }, (_, i): Prefill => {
    const lastSet = last[i] ?? last[last.length - 1] ?? null
    const lastWeight = lastSet?.weightKg ?? target?.weightKg ?? 0
    switch (kind) {
      case 'HOLD': {
        const base = lastSet?.seconds ?? holdTarget
        const next = mayProgress && hitTop(last) && base < holdCeiling ? Math.min(base + HOLD_STEP_SEC, holdCeiling) : base
        if (hitTop(last) && base >= holdCeiling) ceilingReached = true
        const w = target?.weightKg != null || lastSet?.weightKg != null ? lastWeight : null
        return { reps: null, seconds: next, weightKg: w }
      }
      case 'WEIGHTED': {
        // web: audit #9 (a pain step-down to 0 kg falls back to the plan's weight instead of staying at 0 for good)
        const weight = lastSet?.weightKg != null && lastSet.weightKg > 0 ? lastSet.weightKg : (target?.weightKg ?? 0)
        // One failed set (4 of 8) starts next time at the bottom of the range, not below it.
        const baseReps = lastSet?.reps != null ? Math.max(lastSet.reps, repsMin) : target?.repsMin ?? 10
        const loadUp = mayProgress && weight > 0 && hitTop(last) && (
          hasRange || (
            hitTop(previous) &&
            !previous.some((s) => s.effort === 'HARD' || s.effort === 'PAIN') &&
            sameWeight(sessionWeight(previous), sessionWeight(last))
          )
        )
        if (pain && weight > 0) return { reps: baseReps, seconds: null, weightKg: stepDown(weight, unit) }
        if (loadUp && hasRange) return { reps: repsMin, seconds: null, weightKg: stepUp(weight, unit) }
        if (loadUp) return { reps: baseReps, seconds: null, weightKg: stepUp(weight, unit) }
        if (mayProgress && baseReps < repsMax) return { reps: baseReps + 1, seconds: null, weightKg: weight }
        return { reps: baseReps, seconds: null, weightKg: weight }
      }
      case 'REPS': {
        const baseReps = lastSet?.reps ?? target?.repsMin ?? 10
        const next = mayProgress && baseReps < repsMax + 2 ? Math.min(baseReps + 1, 999) : baseReps
        const w = target?.weightKg != null || (lastSet?.weightKg != null && lastSet.weightKg > 0) ? lastWeight : null
        return { reps: next, seconds: null, weightKg: w }
      }
    }
  })
  return { sets, holdCeilingReached: ceilingReached }
}

/** One load step up in the person's unit, landing on a loadable number (0.25 kg or 2.5 lb). */
export function stepUp(kg: number, unit: WeightUnit): number {
  return Math.min(toKg(unit, snap(f(fromKg(unit, kg) + loadStep(unit)), unit)), 999)
}

export function stepDown(kg: number, unit: WeightUnit): number {
  return toKg(unit, Math.max(snap(f(fromKg(unit, kg) - loadStep(unit)), unit), 0))
}

function snap(value: number, unit: WeightUnit): number {
  const grid = unit === 'LB' ? 2.5 : 0.25
  return f(Math.round(f(value / grid)) * grid)
}

/** Equal once shown in either unit: 61.2 kg from a 135 lb entry and 61.23 kg are the same weight. */
function sameWeight(a: number, b: number): boolean {
  return Math.abs(f(a - b)) < f(0.1)
}

function roundTo5(sec: number): number {
  return Math.trunc((sec + 2) / 5) * 5
}
