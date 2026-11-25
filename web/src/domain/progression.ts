// Today's prefill for one exercise from the last two times it was done. Same rules as Android Progression.kt:
// - DOUBLE (default): rep range climbs reps then adds one load step and resets to bottom; single rep target needs 2 clean sessions.
// - LINEAR: adds +2.5 kg upper / +5 kg lower (+5 lb / +10 lb in lb) per clean session where every set hit repsMin.
// - MAINTAIN: holds last session's weight/reps/seconds on clean sessions.
// - Precedence: warm-up -> pain -> BACK_OFF -> deload / MAINTAIN -> FATIGUE -> failure hold -> missed -> mode rules.
// - HARD sets count as clean; only FAILURE (or PAIN) blocks progression.
// - Uses countsForProgression so warm-up/drop/rest-pause sets never count.
// - Load steps follow the person's unit: +2.5 kg or +5 lb, landing on numbers they can actually load.
// Kotlin uses 32-bit Float here, so weights go through Math.fround.
import { effortOf, type SetEffort } from './effort'
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
export type ProgressionReason = 'LOAD_UP' | 'MORE_REPS' | 'MORE_TIME' | 'ONCE_MORE' | 'SAME' | 'HARD' | 'PAIN' | 'MISSED' | 'BACK_OFF' | 'FATIGUE' | 'EASIER'
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

/** What nextPrefill needs for one exercise, apart from the unit. */
export type ProgressionInputs = { target: Target | null; last: PastSet[]; previous: PastSet[]; warmup: boolean; mode: ProgressionMode }
type PlanItemLike = Pick<PlanItemEntity, 'block' | 'sets' | 'repsMin' | 'repsMax' | 'seconds' | 'weightKg'> & { progressionMode?: string }
type PastSetSource = Pick<SetEntity, 'reps' | 'seconds' | 'weightKg' | 'effort' | 'note'> & { setType?: string }
type RecentSet = PastSetSource & Pick<SetEntity, 'sessionId' | 'setIndex'> & { block?: string }

/** One done row as the rules read it; a Failure-type row with no tag counts as Failure (A11). */
export function pastSet(s: PastSetSource): PastSet {
  const effort: SetEffort = s.effort.trim() === '' && s.setType === 'FAILURE' ? 'FAILURE' : effortOf(s.effort, s.note)
  return { reps: s.reps, seconds: s.seconds, weightKg: s.weightKg, effort }
}

/**
 * The inputs to nextPrefill for one exercise, shared by the prefill and the debrief so both read history the same way:
 * filters `recent` through `countsForProgression` so warm-up, dropset, and rest-pause sets never count.
 */
export function progressionInputs(item: PlanItemLike | null | undefined, exerciseId: string | null | undefined, recent: readonly RecentSet[]): ProgressionInputs {
  const eligible = recent.filter((s) => countsForProgression(s))
  const sessionIds = [...new Set(eligible.map((s) => s.sessionId))]
  const past = (id: number | undefined): PastSet[] =>
    eligible.filter((s) => s.sessionId === id).sort((a, b) => a.setIndex - b.setIndex).map(pastSet)
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
  const failed = last.some((s) => s.effort === 'FAILURE')
  const sessionWeight = (session: readonly PastSet[]) =>
    session.length === 0 ? 0 : Math.max(...session.map((s) => s.weightKg ?? 0))
  // Weighted: only the sets at the session's top weight say whether the weight was handled (audit S1, Gate 5).
  const topSets = (session: readonly PastSet[]): readonly PastSet[] => {
    if (kind !== 'WEIGHTED') return session
    const top = sessionWeight(session)
    return session.filter((s) => sameWeight(s.weightKg ?? 0, top))
  }
  const missedAtFailure = (session: readonly PastSet[]) =>
    kind !== 'HOLD' && session.some((s) => s.effort === 'FAILURE') && session.some((s) => (s.reps ?? 0) < repsMin)
  // Two or more reps under the minimum, tagged or not (audit S4).
  const farUnder = (session: readonly PastSet[]) =>
    kind !== 'HOLD' && session.some((s) => (s.reps ?? 0) <= repsMin - 2)
  const bestSeconds = (session: readonly PastSet[]) =>
    session.length === 0 ? 0 : Math.max(...session.map((s) => s.seconds ?? 0))
  // A missed bodyweight session: a rep set under the minimum, or a hold whose best stayed under the target (S2, S3).
  const missedSession = (session: readonly PastSet[]) =>
    session.length > 0 && (
      kind === 'REPS' ? session.some((s) => (s.reps ?? 0) < repsMin)
      : kind === 'HOLD' ? bestSeconds(session) < holdTarget
      : false
    )
  const fatigue = missedAtFailure(last)
  // Back-off pairs two workouts at the same top weight only (Gate 16).
  const backOff = previous.length > 0 &&
    ((fatigue && missedAtFailure(previous)) || (farUnder(last) && farUnder(previous))) &&
    (kind !== 'WEIGHTED' || sameWeight(sessionWeight(last), sessionWeight(previous)))
  // Two missed bodyweight workouts in a row suggest an easier version (S2, Gate 6: needs a previous workout).
  const easier = !warmup && missedSession(last) && missedSession(previous)
  const complete = last.length >= count
  const metMin = complete && (
    kind === 'HOLD' ? last.every((s) => (s.seconds ?? 0) >= holdTarget)
    : kind === 'WEIGHTED' ? topSets(last).every((s) => (s.reps ?? 0) >= repsMin)
    : last.every((s) => (s.reps ?? 0) >= repsMin)
  )
  // Every set Easy (3+ reps left): add load without waiting for the top of the rep range.
  const allEasy = last.length > 0 && last.every((s) => s.effort === 'EASY')
  const mayProgress = !warmup && last.length > 0 && metMin && !pain && !failed && !deload && mode !== 'MAINTAIN'

  // Holds read the session's best seconds, rounded down to 5 s, never under 5 s (audit S3).
  const holdBest = Math.max(Math.trunc(bestSeconds(last) / HOLD_STEP_SEC) * HOLD_STEP_SEC, HOLD_STEP_SEC)
  const hitTop = (session: readonly PastSet[]) =>
    session.length >= count && (
      kind === 'HOLD' ? session.every((s) => (s.seconds ?? 0) >= Math.max(holdTarget, holdBest))
      : kind === 'REPS' ? session.every((s) => (s.reps ?? 0) >= repsMax)
      : topSets(session).every((s) => (s.reps ?? 0) >= repsMax)
    )
  const stepDownFloor = (w: number) => Math.max(stepDown(w, unit), Math.min(w, toKg(unit, loadStep(unit))))

  const lastTop = sessionWeight(last)
  const backOffWeighted = kind === 'WEIGHTED' && backOff && lastTop > 0
  // One load step is more than 10 % of the weight: a step down is too big, so back-off keeps the weight (audit S5).
  const bigStep = lastTop > 0 && loadStep(unit) > f(0.1) * fromKg(unit, lastTop)
  const holdCeiling = Math.max(roundTo5(Math.round(f(holdTarget * HOLD_CEILING))), holdTarget)
  const holdMissed = kind === 'HOLD' && !warmup && missedSession(last)
  let ceilingReached = false

  const sets = Array.from({ length: count }, (_, i): Prefill => {
    const lastSet = last[i] ?? last[last.length - 1] ?? null
    const lastWeight = lastSet?.weightKg ?? target?.weightKg ?? 0
    switch (kind) {
      case 'HOLD': {
        const next = warmup || last.length === 0
          ? (lastSet?.seconds ?? holdTarget)
          // Missed: Failure keeps the best, otherwise 5 s more, never past the target.
          : holdMissed
            ? (failed ? holdBest : Math.max(Math.min(holdBest + HOLD_STEP_SEC, holdTarget), holdBest))
            : (mayProgress && hitTop(last) && holdBest < holdCeiling ? Math.min(holdBest + HOLD_STEP_SEC, holdCeiling) : holdBest)
        if (!warmup && !holdMissed && !pain && !failed && !deload && mode !== 'MAINTAIN' &&
          last.length > 0 && hitTop(last) && holdBest >= holdCeiling) {
          ceilingReached = true
        }
        const w = target?.weightKg != null || lastSet?.weightKg != null ? lastWeight : null
        return { reps: null, seconds: next, weightKg: w }
      }
      case 'WEIGHTED': {
        // web: audit #9 (a 0 kg last set falls back to the plan's weight instead of staying at 0 for good)
        const fallbackWeight = lastSet?.weightKg != null && lastSet.weightKg > 0 ? lastSet.weightKg : (target?.weightKg ?? 0)
        // Every set starts from the session's top weight; a set done lighter restarts at the bottom of the range (S1).
        const top = lastTop > 0 ? lastTop : fallbackWeight
        const atTop = lastSet != null && sameWeight(lastSet.weightKg ?? 0, lastTop)
        // One missed set (4 of 8) starts next time at the bottom of the range, not below it.
        const baseReps = atTop ? Math.max(lastSet.reps ?? repsMin, repsMin) : repsMin
        if (warmup || last.length === 0) return { reps: lastSet?.reps ?? target?.repsMin ?? 10, seconds: null, weightKg: fallbackWeight }
        if (pain && top > 0) return { reps: baseReps, seconds: null, weightKg: stepDownFloor(top) }
        if (pain) return { reps: baseReps, seconds: null, weightKg: top }
        if (backOffWeighted && bigStep) return { reps: repsMin, seconds: null, weightKg: top }
        if (backOffWeighted) return { reps: repsMin, seconds: null, weightKg: stepDownFloor(top) }
        if (deload || mode === 'MAINTAIN') return { reps: mode === 'MAINTAIN' && atTop ? (lastSet?.reps ?? baseReps) : baseReps, seconds: null, weightKg: top }
        if (fatigue) return { reps: repsMin, seconds: null, weightKg: top }
        if (failed) return { reps: baseReps, seconds: null, weightKg: top }
        if (!metMin) return { reps: baseReps, seconds: null, weightKg: top }
        if (mode === 'LINEAR') {
          return top > 0
            ? { reps: repsMin, seconds: null, weightKg: stepUpLinear(top, unit, lowerBody) }
            : { reps: baseReps, seconds: null, weightKg: top }
        }
        const easyUp = allEasy && top > 0
        const loadUp = easyUp || (top > 0 && hitTop(last) && (
          hasRange || (
            hitTop(previous) &&
            !previous.some((s) => s.effort === 'FAILURE' || s.effort === 'PAIN') &&
            sameWeight(sessionWeight(previous), lastTop)
          )
        ))
        if (loadUp && hasRange) return { reps: repsMin, seconds: null, weightKg: stepUp(top, unit) }
        if (loadUp) return { reps: baseReps, seconds: null, weightKg: stepUp(top, unit) }
        // A set done lighter restarts at the minimum at the top weight, with no extra rep (S1).
        if (!atTop) return { reps: repsMin, seconds: null, weightKg: top }
        if (baseReps < repsMax) return { reps: baseReps + 1, seconds: null, weightKg: top }
        return { reps: baseReps, seconds: null, weightKg: top }
      }
      case 'REPS': {
        const baseReps = Math.max(lastSet?.reps ?? target?.repsMin ?? 10, 1)
        let next: number
        if (!warmup && missedSession(last)) {
          // Missed: every set at the reps the best set reached, up to the minimum, never 0 (audit S2).
          const bestDone = last.length === 0 ? 0 : Math.max(...last.map((s) => s.reps ?? 0))
          next = Math.max(Math.min(repsMin, bestDone), 1)
        } else if (mayProgress && baseReps < repsMax + 2) {
          next = Math.min(baseReps + 1, 999)
        } else {
          next = baseReps
        }
        const w = target?.weightKg != null || (lastSet?.weightKg != null && lastSet.weightKg > 0) ? lastWeight : null
        return { reps: next, seconds: null, weightKg: w }
      }
    }
  })

  const nextTop = sets.reduce((m, s) => Math.max(m, s.weightKg ?? 0), 0)
  let reason: ProgressionReason | null = null
  if (warmup || last.length === 0 || ceilingReached) reason = null
  else if (pain) reason = kind === 'WEIGHTED' && nextTop < f(lastTop - f(0.1)) ? 'PAIN' : 'HARD'
  // A back-off that keeps the weight reads as a missed workout (Gate 7).
  else if (backOffWeighted) reason = bigStep ? 'MISSED' : 'BACK_OFF'
  else if (deload || mode === 'MAINTAIN') reason = 'SAME'
  else if (easier) reason = 'EASIER'
  else if (fatigue) reason = 'FATIGUE'
  else if (failed) reason = 'HARD'
  else if (!metMin) reason = 'MISSED'
  else if (nextTop > f(lastTop + f(0.1))) reason = 'LOAD_UP'
  else if (kind === 'HOLD' && sets.some((s, i) => (s.seconds ?? 0) > ((last[i] ?? last[last.length - 1])?.seconds ?? 0))) reason = 'MORE_TIME'
  else if (sets.some((s, i) => (s.reps ?? 0) > ((last[i] ?? last[last.length - 1])?.reps ?? 0))) reason = 'MORE_REPS'
  else if (mode === 'DOUBLE' && kind === 'WEIGHTED' && hitTop(last) && !hasRange) reason = 'ONCE_MORE'
  else reason = 'SAME'
  return { sets, holdCeilingReached: ceilingReached, reason }
}

/** Linear step in the person's display unit: +2.5/+5 kg or +5/+10 lb, snapped to the unit grid. */
export function stepUpLinear(kg: number, unit: WeightUnit, lowerBody: boolean): number {
  const delta = lowerBody ? f(loadStep(unit) * 2) : loadStep(unit)
  return Math.min(toKg(unit, snap(f(fromKg(unit, kg) + delta), unit)), 999)
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
