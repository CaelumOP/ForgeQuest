// The workout score (4..10), calculated from the workout's own numbers so it shows at once, with or without an AI key.
// Port of app/.../domain/WorkoutScore.kt: 3 for showing up, up to 3 for finishing the plan, up to 3 for beating last time, 1 for a PR.
// Rule v2: docs/math/workout-score.md.
import type { SessionEntity, SetEntity } from './entities'
import type { ExerciseKind } from './muscle'
import { parseEffort } from './effort'
import { recordScore, welcomeBack, type DayTag } from './progress'

type Done = Pick<SetEntity, 'reps' | 'seconds' | 'weightKg' | 'isPr' | 'effort'> & { block?: string }
export type Trend = 'UP' | 'SAME' | 'DOWN'

/** The block name the starter plans give warm-ups (the stored value, not the translated label). */
export const WARM_UP_BLOCK = 'Warm-up'
export const isWarmUp = (block: string | undefined): boolean => block?.toLowerCase() === WARM_UP_BLOCK.toLowerCase()

/** One exercise of the workout: today's done sets and its last time (the most recent earlier workout's done sets, empty when none). */
export type ScoreWork = { exerciseId: string; kind: ExerciseKind; done: Done[]; last: Done[] }
export type WorkoutScoreResult = {
  score: number; doneSets: number; plannedSets: number | null; up: number; compared: number; prs: number
  /** Recovery under 50 or a PAIN set: drops count as holding steady and fewer sets are needed. */
  rough: boolean
  /** The person's first finished workout with done sets. */
  firstEver: boolean
  /** The first workout after a break (the same rule as the Welcome back card, progress.ts welcomeBack). */
  afterBreak: boolean
  /** False on a first-ever or first-after-a-break workout: show the reasons without the number. */
  showNumber: boolean
}

const sum = (l: number[]) => l.reduce((a, b) => a + b, 0)

/**
 * Best set (by recordScore), total reps or seconds, and heaviest weight, compared with last time. For WEIGHTED, the total
 * leaves out ramp-up sets lighter than 60 % of that workout's heaviest. A drop of 2 % or less in the best set, or a heavier
 * load with at least half the reps (a reset to fewer reps), counts as holding steady.
 */
export function trend(kind: ExerciseKind, exerciseId: string, done: Done[], last: Done[]): Trend {
  const best = (l: Done[]) => Math.max(...l.map((s) => recordScore(kind, s.reps, s.seconds, s.weightKg, exerciseId)))
  const heaviest = (l: Done[]) => Math.max(...l.map((s) => s.weightKg ?? 0))
  const total = (l: Done[]) => {
    const top = heaviest(l)
    const working = kind === 'WEIGHTED' && top > 0 ? l.filter((s) => (s.weightKg ?? 0) >= 0.6 * top) : l
    return sum(working.map((s) => (kind === 'HOLD' ? s.seconds : s.reps) ?? 0))
  }
  const bestNow = best(done); const bestLast = best(last)
  const totalNow = total(done); const totalLast = total(last)
  const heavyNow = heaviest(done); const heavyLast = heaviest(last)
  const reset = heavyNow > heavyLast && totalNow * 2 >= totalLast
  if (bestNow > bestLast || (totalNow > totalLast && heavyNow >= heavyLast)) return 'UP'
  if (bestNow < 0.98 * bestLast && totalNow <= totalLast && !reset) return 'DOWN'
  return 'SAME'
}

/**
 * `plannedSets` is null with no plan day (or an empty one); warm-ups are already left out of it. `lowReadiness`: that
 * day's recovery was under 50, so every drop counts as holding steady; a PAIN set does that for its own exercise only.
 * Either way the workout needs only two thirds of the planned sets. Warm-up sets count for nothing but a PR.
 */
export function computeWorkoutScore(
  work: ScoreWork[], plannedSets: number | null, lowReadiness: boolean, firstEver = false, afterBreak = false,
): WorkoutScoreResult {
  const working = work.map((w) => ({ ...w, done: w.done.filter((s) => !isWarmUp(s.block)), last: w.last.filter((s) => !isWarmUp(s.block)) }))
  const sets = working.flatMap((w) => w.done)
  const pain = new Set(working.filter((w) => w.done.some((s) => parseEffort(s.effort) === 'PAIN')).map((w) => w.exerciseId))
  const rough = lowReadiness || pain.size > 0
  const planned = plannedSets != null && plannedSets > 0 ? plannedSets : null
  // ceil(0.67 × planned) in whole numbers, so 0.67 × 100 can't round up to 68.
  const effective = planned == null ? null : rough ? Math.floor((67 * planned + 99) / 100) : planned
  // Twice the value of each exercise (UP 2, SAME 1, DOWN 0), so the sums stay whole numbers.
  let halves = 0; let up = 0; let compared = 0
  for (const w of working) {
    if (!w.done.length || !w.last.length) continue
    compared++
    const tr = trend(w.kind, w.exerciseId, w.done, w.last)
    if (tr === 'UP') { up++; halves += 2 } else if (tr === 'SAME') halves += 1
    else halves += lowReadiness || pain.has(w.exerciseId) ? 1 : 0
  }
  const ratio = effective == null ? 1 : sets.length / effective
  const completion = ratio >= 1 ? 3 : ratio >= 0.75 ? 2 : ratio >= 0.5 ? 1 : 0
  // min(3, floor(4 × sum / compared)) with sum = halves / 2; below 75 % of the sets, progress doesn't count.
  const progress = ratio < 0.75 ? 0 : compared === 0 ? 2 : Math.min(3, Math.floor((2 * halves) / compared))
  const prs = work.reduce((n, w) => n + w.done.filter((s) => s.isPr).length, 0)
  const total = 3 + completion + progress + (prs > 0 ? 1 : 0)
  return {
    score: Math.min(10, Math.max(4, total)), doneSets: sets.length, plannedSets: planned, up, compared, prs,
    rough, firstEver, afterBreak, showNumber: !(firstEver || afterBreak),
  }
}

type ScoreSession = Pick<SessionEntity, 'planDayId' | 'epochDay' | 'startedAt'> & { id?: number; plannedSets?: number | null }
type ScoreSet = Done & Pick<SetEntity, 'sessionId' | 'exerciseId'>

/**
 * The score of every workout with done sets, in one pass over data already loaded. A workout's saved `plannedSets` wins;
 * `plannedByDay` (each plan day's work sets as the plan is now) is the fallback for older workouts. `lowReadinessDays` are
 * the epoch days with recovery under 50; `dayTags` and `trainingDays` feed the Welcome back rule behind `afterBreak`.
 */
export function scoresForSessions(
  sessions: ScoreSession[], doneSets: ScoreSet[], kinds: Map<string, ExerciseKind>,
  plannedByDay: Map<number, number>, lowReadinessDays: Set<number>,
  dayTags: ReadonlyMap<number, DayTag> = new Map(), trainingDays: ReadonlySet<number> = new Set(),
): Map<number, WorkoutScoreResult> {
  const bySession = new Map<number, ScoreSet[]>()
  for (const s of doneSets) bySession.set(s.sessionId, [...(bySession.get(s.sessionId) ?? []), s])
  const byId = new Map(sessions.map((s) => [s.id!, s]))
  const before = (o: ScoreSession, s: ScoreSession) =>
    o.id !== s.id && (o.epochDay < s.epochDay || (o.epochDay === s.epochDay && o.startedAt < s.startedAt))
  const order = (a: ScoreSession, b: ScoreSession) => a.epochDay - b.epochDay || a.startedAt - b.startedAt
  // Per exercise, its workouts oldest first, so "last time" is the nearest one before.
  const history = new Map<string, { session: ScoreSession; sets: ScoreSet[] }[]>()
  for (const [id, list] of bySession) {
    const session = byId.get(id)
    if (!session) continue
    for (const exId of new Set(list.map((s) => s.exerciseId))) {
      history.set(exId, [...(history.get(exId) ?? []), { session, sets: list.filter((s) => s.exerciseId === exId) }])
    }
  }
  for (const l of history.values()) l.sort((a, b) => order(a.session, b.session))
  const firstWithSets = sessions.filter((s) => bySession.get(s.id!)?.length).sort(order)[0]?.id
  const start = sessions.length ? Math.min(...sessions.map((s) => s.epochDay)) : null
  const out = new Map<number, WorkoutScoreResult>()
  for (const s of sessions) {
    const mine = bySession.get(s.id!) ?? []
    if (!mine.length) continue
    const work: ScoreWork[] = [...new Set(mine.map((x) => x.exerciseId))].map((exId) => {
      const earlier = (history.get(exId) ?? []).filter((h) => before(h.session, s))
      return { exerciseId: exId, kind: kinds.get(exId) ?? 'REPS', done: mine.filter((x) => x.exerciseId === exId), last: earlier.at(-1)?.sets ?? [] }
    })
    // The workout days known when this one started, as the Welcome back card saw them then.
    const earlierDays = new Set(sessions.filter((o) => before(o, s)).map((o) => o.epochDay))
    const afterBreak = welcomeBack(s.epochDay, earlierDays, dayTags, trainingDays, start ?? s.epochDay)
    const planned = s.plannedSets ?? (s.planDayId != null ? plannedByDay.get(s.planDayId) ?? null : null)
    out.set(s.id!, computeWorkoutScore(work, planned, lowReadinessDays.has(s.epochDay), s.id === firstWithSets, afterBreak))
  }
  return out
}
