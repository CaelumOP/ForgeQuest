// Today's prefill for one exercise from the last two times it was done. Same rules as Android Progression.kt:
// - Rep range (e.g. 8–12): top of the range on every set adds one load step and resets to the bottom.
// - Single rep target (e.g. 3×8): load only goes up after hitting it in two sessions in a row at the same weight.
// - Holds: +5 s per session up to 1.5× the plan's target, then they stop and the screen suggests a harder version.
// - Hard or Pain on any set, a missed set, or a low-readiness day keeps last session's numbers.
//   Pain also takes the load back one step (bodyweight moves keep their reps).
// - Load steps follow the person's unit: +2.5 kg or +5 lb, landing on numbers they can actually load.
// Kotlin uses 32-bit Float here, so weights go through Math.fround.
import { effortOf, isStrain, type SetEffort } from './effort'
import type { PlanItemEntity, SetEntity } from './entities'
import type { ExerciseKind, Muscle } from './muscle'
import { countsForProgression } from './set-types'
import { fromKg, loadStep, toKg, type WeightUnit } from './units'
import { STRETCH_IDS } from '../data/seed/exercise-catalog'

const f = Math.fround

export const PROGRESSION_MODES = ['DOUBLE', 'LINEAR', 'MAINTAIN'] as const
export type ProgressionMode = (typeof PROGRESSION_MODES)[number]

export function parseProgressionMode(raw: string | null | undefined): ProgressionMode {
  const up = (raw ?? '').trim().toUpperCase()
  return up === 'LINEAR' || up === 'MAINTAIN' ? up : 'DOUBLE'
}

const LOWER_BODY_MUSCLES: ReadonlySet<Muscle> = new Set<Muscle>([
  'QUADS', 'HAMSTRINGS', 'GLUTES', 'ADDUCTORS', 'CALVES', 'TIBIALIS',
])

export function isLowerBodyExercise(primary: readonly Muscle[]): boolean {
  return primary.some((m) => LOWER_BODY_MUSCLES.has(m))
}

/** One finished set as the rules need it. */
export type PastSet = { reps: number | null; seconds: number | null; weightKg: number | null; effort: SetEffort }
export type Target = { sets: number; repsMin: number | null; repsMax: number | null; seconds: number | null; weightKg: number | null }
export type Prefill = { reps: number | null; seconds: number | null; weightKg: number | null }
export type ProgressionReason = 'LOAD_UP' | 'MORE_REPS' | 'MORE_TIME' | 'ONCE_MORE' | 'SAME' | 'HARD' | 'PAIN' | 'MISSED' | 'LOW_READINESS'
export type ProgressionResult = { sets: Prefill[]; holdCeilingReached: boolean; reason: ProgressionReason | null }

/** Every 4th week since the plan was made is a lighter one: half the sets, same weights. `createdAt` is ms. */
export function isDeloadWeek(createdAtMs: number, todayEpochDay: number): boolean {
  const startDay = Math.floor((createdAtMs - new Date(createdAtMs).getTimezoneOffset() * 60_000) / 86_400_000)
  return Math.floor(Math.max(0, todayEpochDay - startDay) / 7) % 4 === 3
}

/** Sets in a lighter week: half, rounded up. */
export const deloadSets = (sets: number): number => Math.trunc((sets + 1) / 2)

export const HOLD_STEP_SEC = 5
export const HOLD_CEILING = 1.5

/** What nextPrefill needs for one exercise, apart from the unit and the readiness. */
export type ProgressionInputs = { target: Target | null; last: PastSet[]; previous: PastSet[]; warmup: boolean; mode: ProgressionMode }
type PlanItemLike = Pick<PlanItemEntity, 'block' | 'sets' | 'repsMin' | 'repsMax' | 'seconds' | 'weightKg'> & { progressionMode?: string }
type RecentSet = Pick<SetEntity, 'sessionId' | 'setIndex' | 'reps' | 'seconds' | 'weightKg' | 'effort' | 'note'> & { setType?: string; block?: string }

/**
 * The inputs to nextPrefill for one exercise, shared by the prefill and the debrief so both read history the same way:
 * filters `recent` through `countsForProgression` so warm-up, dropset, and rest-pause sets never count.
 */
export function progressionInputs(item: PlanItemLike | null | undefined, exerciseId: string | null | undefined, recent: readonly RecentSet[]): ProgressionInputs {
  const eligible = recent.filter((s) => countsForProgression(s))
  const sessionIds = [...new Set(eligible.map((s) => s.sessionId))]
  const past = (id: number | undefined): PastSet[] =>
    eligible.filter((s) => s.sessionId === id).sort((a, b) => a.setIndex - b.setIndex)
      .map((s) => ({ reps: s.reps, seconds: s.seconds, weightKg: s.weightKg, effort: effortOf(s.effort, s.note) }))
  return {
    target: item ? { sets: item.sets, repsMin: item.repsMin, repsMax: item.repsMax, seconds: item.seconds, weightKg: item.weightKg } : null,
    last: past(sessionIds[0]),
    previous: past(sessionIds[1]),
    warmup: item?.block.toLowerCase() === 'warm-up' || item?.block.toLowerCase() === 'cool-down' || exerciseId === 'jumping_jacks' || (exerciseId != null && STRETCH_IDS.has(exerciseId)),
    mode: parseProgressionMode(item?.progressionMode),
  }
}

export function nextPrefill(
  kind: ExerciseKind,
  target: Target | null,
  last: readonly PastSet[],
  previous: readonly PastSet[],
  unit: WeightUnit,
  lowReadiness: boolean,
  warmup: boolean,
  deload = false,
  mode: ProgressionMode = 'DOUBLE',
  lowerBody = false,
): ProgressionResult {
  const rawCount = target?.sets ?? 3
  const count = deload ? deloadSets(rawCount) : rawCount
  const repsMin = target?.repsMin ?? 8
  const repsMax = target?.repsMax ?? target?.repsMin ?? 12
  const holdTarget = target?.seconds ?? 30
  // The default 8–12 is a range too, so it resets to 8 after a load step (audit 2026-10-24).
  const hasRange = repsMax > repsMin

  const pain = last.some((s) => s.effort === 'PAIN')
  // Failure twice in a row (this workout and the one before) means the load is too heavy: back off like pain.
  const failureStreak = last.some((s) => s.effort === 'FAILURE') && previous.some((s) => s.effort === 'FAILURE')
  const strained = pain || last.some((s) => isStrain(s.effort))
  // Every set Easy (3+ reps left): add load without waiting for the top of the rep range.
  const allEasy = last.length > 0 && last.every((s) => s.effort === 'EASY')
  // "Completed" means every set was done without a hard or painful one; only then may anything go up.
  const completed = !warmup && last.length >= count && !strained
  const mayProgress = completed && !lowReadiness && !deload && mode !== 'MAINTAIN'

  const hitTop = (session: readonly PastSet[]) =>
    session.length >= count &&
    (kind === 'HOLD' ? session.every((s) => (s.seconds ?? 0) >= holdTarget) : session.every((s) => (s.reps ?? 0) >= repsMax))
  const hitLinearTarget = (session: readonly PastSet[]) =>
    session.length >= count && session.every((s) => (s.reps ?? 0) >= repsMin)
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
        if ((pain || failureStreak) && weight > 0) return { reps: baseReps, seconds: null, weightKg: stepDown(weight, unit) }
        if (mode === 'MAINTAIN') return { reps: lastSet?.reps ?? baseReps, seconds: null, weightKg: weight }
        if (mode === 'LINEAR') {
          const linearUp = mayProgress && weight > 0 && hitLinearTarget(last)
          if (linearUp) return { reps: repsMin, seconds: null, weightKg: stepUpLinear(weight, unit, lowerBody) }
          return { reps: baseReps, seconds: null, weightKg: weight }
        }
        const easyUp = mayProgress && allEasy && weight > 0 && hitLinearTarget(last)
        const loadUp = easyUp || (mayProgress && weight > 0 && hitTop(last) && (
          hasRange || (
            hitTop(previous) &&
            !previous.some((s) => isStrain(s.effort) || s.effort === 'PAIN') &&
            sameWeight(sessionWeight(previous), sessionWeight(last))
          )
        ))
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
  const lastTop = sessionWeight(last)
  const nextTop = sets.reduce((m, s) => Math.max(m, s.weightKg ?? 0), 0)
  let reason: ProgressionReason | null = null
  if (!warmup && last.length > 0 && !ceilingReached) {
    if (strained) reason = pain && nextTop < lastTop - 0.1 ? 'PAIN' : 'HARD'
    else if (last.length < count) reason = 'MISSED'
    else if (lowReadiness && completed) reason = 'LOW_READINESS'
    else if (deload || mode === 'MAINTAIN') reason = 'SAME'
    else if (nextTop > lastTop + 0.1) reason = 'LOAD_UP'
    else if (kind === 'HOLD' && (sets[0]?.seconds ?? 0) > (last[0]?.seconds ?? 0)) reason = 'MORE_TIME'
    else if (sets[0]?.reps != null && sets[0].reps > (last[0]?.reps ?? 0)) reason = 'MORE_REPS'
    else if (mode === 'DOUBLE' && kind === 'WEIGHTED' && hitTop(last) && !hasRange) reason = 'ONCE_MORE'
    else reason = 'SAME'
  }
  return { sets, holdCeilingReached: ceilingReached, reason }
}

/** Linear increments are canonical kilograms; changing display units must not change the prescription. */
export function stepUpLinear(kg: number, _unit: WeightUnit, lowerBody: boolean): number {
  return Math.min(f(kg + (lowerBody ? 5 : 2.5)), 999)
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
