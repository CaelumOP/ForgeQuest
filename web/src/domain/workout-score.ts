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

/** The kind of personal record an exercise set, from its exercise kind. */
export type PrKind = 'WEIGHT' | 'REPS' | 'HOLD'
export const prKindOf = (kind: ExerciseKind): PrKind => (kind === 'WEIGHTED' ? 'WEIGHT' : kind)

/**
 * One exercise as the score saw it, for the debrief: its raw `trend` (null with no last time or only warm-up sets),
 * `forgiven` when that day's recovery was low or it had a PAIN set (a drop then counts as holding steady), and the kind
 * of personal record it set, if any.
 */
export type ScoreExercise = { exerciseId: string; trend: Trend | null; forgiven: boolean; pr: PrKind | null }

/** One exercise of the workout: today's done sets and its last time (the most recent earlier workout's done sets, empty when none). */
export type ScoreWork = { exerciseId: string; kind: ExerciseKind; done: Done[]; last: Done[] }
export type WorkoutScoreResult = {
  score: number; doneSets: number; plannedSets: number | null; up: number; compared: number; prs: number
  /** Recovery under 50 or a PAIN set: drops count as holding steady and fewer sets are needed. */
  rough: boolean
  /** That day's recovery was under 50 (a rough day from low recovery, not just from pain). */
  lowRecovery?: boolean
  /** The person's first finished workout with done sets. */
  firstEver: boolean
  /** The first workout after a break (the same rule as the Welcome back card, progress.ts welcomeBack). */
  afterBreak: boolean
  /** False on a first-ever or first-after-a-break workout: show the reasons without the number. */
  showNumber: boolean
  /** The planned sets this workout needed: plannedSets, or two thirds of it on a rough day. */
  effectiveSets?: number | null
  /** Per exercise, in the order given. */
  exercises?: ScoreExercise[]
}

const sum = (l: number[]) => l.reduce((a, b) => a + b, 0)
const f = Math.fround

/**
 * The numbers `trend` compares for one workout's sets of one exercise: the best set (recordScore) and its reps, the
 * reference weight `top` (the heaviest set of 3+ reps, or the heaviest overall when every set has 1 or 2 reps) and the
 * total reps (seconds for holds). For WEIGHTED, the total counts only sets at 60 % of `top` or more, so ramp-up sets and
 * a heavy single tested on top of the work sets don't change it. `volume` is the sum of kg x reps over those same sets.
 */
export type TrendStats = { best: number; bestReps: number; total: number; top: number; volume: number }

export function trendStats(kind: ExerciseKind, exerciseId: string, sets: readonly Done[]): TrendStats {
  let bestSet = sets[0]
  let best = recordScore(kind, bestSet.reps, bestSet.seconds, bestSet.weightKg, exerciseId)
  for (const s of sets) {
    const v = recordScore(kind, s.reps, s.seconds, s.weightKg, exerciseId)
    if (v > best) { best = v; bestSet = s }
  }
  const pool = sets.filter((s) => (s.reps ?? 0) >= 3)
  const top = Math.max(...(pool.length ? pool : sets).map((s) => f(s.weightKg ?? 0)))
  const working = kind === 'WEIGHTED' && top > 0 ? sets.filter((s) => f(s.weightKg ?? 0) >= f(f(0.6) * top)) : sets
  const total = sum(working.map((s) => (kind === 'HOLD' ? s.seconds : s.reps) ?? 0))
  const volume = sum(working.map((s) => f(f(s.weightKg ?? 0) * (s.reps ?? 0))))
  return { best, bestReps: bestSet.reps ?? 0, total, top, volume }
}

/** Weights closer than this are the same weight (a 135 lb entry stored in kg). */
export const SAME_WEIGHT_KG = f(0.05)

/**
 * Rule A of docs/plans/math-debrief-plan.md, comparing trendStats with last time.
 * - UP: a better best set; more total at the same or a heavier `top`; or (WEIGHTED) a heavier top weight with at least
 *   60 % of last time's total, so the app's own reset (3x12 to 3x8, heavier) is progress.
 * - DOWN only when the best set dropped (WEIGHTED: under 95 %, or 2+ fewer reps at the same weight; REPS: 2+ fewer reps
 *   and under 95 %; HOLD: under 85 %), the total fell under 95 % (WEIGHTED: the volume, kg x reps, so a lighter weight
 *   at the same reps counts), and it isn't a heavier reset with at least half the total.
 * - SAME otherwise, so a one-rep wobble at the same weight holds steady.
 */
export function trend(kind: ExerciseKind, exerciseId: string, done: readonly Done[], last: readonly Done[]): Trend {
  const now = trendStats(kind, exerciseId, done)
  const was = trendStats(kind, exerciseId, last)
  const heavier = now.top > f(was.top + SAME_WEIGHT_KG)
  const sameOrHeavier = now.top >= f(was.top - SAME_WEIGHT_KG)
  const up = now.best > was.best ||
    (now.total > was.total && sameOrHeavier) ||
    (kind === 'WEIGHTED' && heavier && now.total * 10 >= was.total * 6)
  if (up) return 'UP'
  const bestDropped =
    kind === 'WEIGHTED' ? now.best < f(f(0.95) * was.best) || (sameOrHeavier && !heavier && now.bestReps <= was.bestReps - 2)
      : kind === 'REPS' ? now.bestReps <= was.bestReps - 2 && now.best < f(f(0.95) * was.best)
        : now.best < f(f(0.85) * was.best)
  // Whole numbers: total < 0.95 x last and total >= 0.5 x last. WEIGHTED with weights compares the volume instead.
  const totalFell = kind === 'WEIGHTED' && was.volume > 0 ? now.volume < 0.95 * was.volume : now.total * 100 < was.total * 95
  const reset = heavier && now.total * 2 >= was.total
  return bestDropped && totalFell && !reset ? 'DOWN' : 'SAME'
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
  const exercises = working.map((w, i): ScoreExercise => {
    const forgiven = lowReadiness || pain.has(w.exerciseId)
    const tr = w.done.length && w.last.length ? trend(w.kind, w.exerciseId, w.done, w.last) : null
    if (tr != null) compared++
    if (tr === 'UP') { up++; halves += 2 } else if (tr === 'SAME') halves += 1
    else if (tr === 'DOWN') halves += forgiven ? 1 : 0
    return { exerciseId: w.exerciseId, trend: tr, forgiven, pr: work[i].done.some((s) => s.isPr) ? prKindOf(w.kind) : null }
  })
  const ratio = effective == null ? 1 : sets.length / effective
  const completion = ratio >= 1 ? 3 : ratio >= 0.75 ? 2 : ratio >= 0.5 ? 1 : 0
  // min(3, floor(4 × sum / compared)) with sum = halves / 2; below 75 % of the sets, progress doesn't count.
  const progress = ratio < 0.75 ? 0 : compared === 0 ? 2 : Math.min(3, Math.floor((2 * halves) / compared))
  const prs = work.reduce((n, w) => n + w.done.filter((s) => s.isPr).length, 0)
  const total = 3 + completion + progress + (prs > 0 ? 1 : 0)
  return {
    score: Math.min(10, Math.max(4, total)), doneSets: sets.length, plannedSets: planned, up, compared, prs,
    rough, lowRecovery: lowReadiness, firstEver, afterBreak, showNumber: !(firstEver || afterBreak), effectiveSets: effective, exercises,
  }
}

type ScoreSession = Pick<SessionEntity, 'planDayId' | 'epochDay' | 'startedAt'> & { id?: number; plannedSets?: number | null }
type ScoreSet = Done & Pick<SetEntity, 'sessionId' | 'exerciseId'>

/**
 * The score of every workout with done sets, in one pass over data already loaded. A workout's saved `plannedSets` wins;
 * `plannedByDay` (each plan day's work sets as the plan is now) is the fallback for older workouts. `lowReadinessDays` are
 * the epoch days with recovery under 50; `dayTags` and `trainingDays` feed the Welcome back rule behind `afterBreak`.
 * With `only` set, just that workout is scored (history still comes from all of them).
 */
export function scoresForSessions(
  sessions: ScoreSession[], doneSets: ScoreSet[], kinds: Map<string, ExerciseKind>,
  plannedByDay: Map<number, number>, lowReadinessDays: Set<number>,
  dayTags: ReadonlyMap<number, DayTag> = new Map(), trainingDays: ReadonlySet<number> = new Set(),
  only?: number,
): Map<number, WorkoutScoreResult> {
  const bySession = new Map<number, ScoreSet[]>()
  for (const s of doneSets) bySession.set(s.sessionId, [...(bySession.get(s.sessionId) ?? []), s])
  const byId = new Map(sessions.map((s) => [s.id!, s]))
  // Strictly earlier: equal (day, start) keys never count as before each other.
  const earlier = (o: ScoreSession, s: ScoreSession) => o.epochDay < s.epochDay || (o.epochDay === s.epochDay && o.startedAt < s.startedAt)
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
  const scoreOf = (s: ScoreSession, earlierDays: ReadonlySet<number>, lastOf: (exId: string) => ScoreSet[]): WorkoutScoreResult | null => {
    const mine = bySession.get(s.id!) ?? []
    if (!mine.length) return null
    const work: ScoreWork[] = [...new Set(mine.map((x) => x.exerciseId))].map((exId) => (
      { exerciseId: exId, kind: kinds.get(exId) ?? 'REPS', done: mine.filter((x) => x.exerciseId === exId), last: lastOf(exId) }
    ))
    // The workout days known when this one started, as the Welcome back card saw them then.
    const afterBreak = welcomeBack(s.epochDay, earlierDays, dayTags, trainingDays, start ?? s.epochDay)
    const planned = s.plannedSets ?? (s.planDayId != null ? plannedByDay.get(s.planDayId) ?? null : null)
    return computeWorkoutScore(work, planned, lowReadinessDays.has(s.epochDay), s.id === firstWithSets, afterBreak)
  }
  const out = new Map<number, WorkoutScoreResult>()
  if (only !== undefined) {
    const s = byId.get(only)
    if (!s) return out
    const days = new Set(sessions.filter((o) => earlier(o, s)).map((o) => o.epochDay))
    const r = scoreOf(s, days, (exId) => (history.get(exId) ?? []).filter((h) => earlier(h.session, s)).at(-1)?.sets ?? [])
    if (r) out.set(s.id!, r)
    return out
  }
  // One walk in time order: the earlier workout days build up, and each exercise keeps a pointer into its history.
  const ordered = [...sessions].sort((a, b) => order(a, b) || a.id! - b.id!)
  const pointer = new Map<string, number>()
  const earlierDays = new Set<number>()
  let i = 0
  while (i < ordered.length) {
    let j = i
    while (j < ordered.length && order(ordered[j], ordered[i]) === 0) j++
    for (let k = i; k < j; k++) {
      const s = ordered[k]
      const r = scoreOf(s, earlierDays, (exId) => {
        const l = history.get(exId) ?? []
        let p = pointer.get(exId) ?? 0
        while (p < l.length && earlier(l[p].session, s)) p++
        pointer.set(exId, p)
        return p === 0 ? [] : l[p - 1].sets
      })
      if (r) out.set(s.id!, r)
    }
    for (let k = i; k < j; k++) earlierDays.add(ordered[k].epochDay)
    i = j
  }
  return out
}
