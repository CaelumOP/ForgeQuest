// The workout score (4..10), calculated from the workout's own numbers so it shows at once, with or without an AI key.
// Port of app/.../domain/WorkoutScore.kt: 3 for showing up, up to 3 for finishing the plan, up to 3 for beating last time, 1 for a PR.
import type { SessionEntity, SetEntity } from './entities'
import type { ExerciseKind } from './muscle'
import { parseEffort } from './effort'
import { recordScore } from './progress'

type Done = Pick<SetEntity, 'reps' | 'seconds' | 'weightKg' | 'isPr' | 'effort'>
export type Trend = 'UP' | 'SAME' | 'DOWN'

/** One exercise of the workout: today's done sets and its last time (the most recent earlier workout's done sets, empty when none). */
export type ScoreWork = { exerciseId: string; kind: ExerciseKind; done: Done[]; last: Done[] }
export type WorkoutScoreResult = { score: number; doneSets: number; plannedSets: number | null; up: number; compared: number; prs: number }

const sum = (l: number[]) => l.reduce((a, b) => a + b, 0)

/** Best set (by recordScore), total reps or seconds, and heaviest weight, compared with last time. */
export function trend(kind: ExerciseKind, exerciseId: string, done: Done[], last: Done[]): Trend {
  const best = (l: Done[]) => Math.max(...l.map((s) => recordScore(kind, s.reps, s.seconds, s.weightKg, exerciseId)))
  const total = (l: Done[]) => sum(l.map((s) => (kind === 'HOLD' ? s.seconds : s.reps) ?? 0))
  const heaviest = (l: Done[]) => Math.max(...l.map((s) => s.weightKg ?? 0))
  const bestNow = best(done); const bestLast = best(last)
  const totalNow = total(done); const totalLast = total(last)
  if (bestNow > bestLast || (totalNow > totalLast && heaviest(done) >= heaviest(last))) return 'UP'
  if (bestNow < bestLast && totalNow <= totalLast) return 'DOWN'
  return 'SAME'
}

/**
 * `plannedSets` is null with no plan day (or an empty one). `lowReadiness`: that day's recovery said ease off or recover;
 * a PAIN set counts the same, and either way holding steady is full marks and a drop isn't penalised.
 */
export function computeWorkoutScore(work: ScoreWork[], plannedSets: number | null, lowReadiness: boolean): WorkoutScoreResult {
  const sets = work.flatMap((w) => w.done)
  const rough = lowReadiness || sets.some((s) => parseEffort(s.effort) === 'PAIN')
  const planned = plannedSets != null && plannedSets > 0 ? plannedSets : null
  // Twice the value of each exercise (UP 2, SAME 1, DOWN 0), so the sums stay whole numbers.
  let halves = 0; let up = 0; let compared = 0
  for (const w of work) {
    if (!w.done.length || !w.last.length) continue
    compared++
    const tr = trend(w.kind, w.exerciseId, w.done, w.last)
    if (tr === 'UP') { up++; halves += 2 } else halves += tr === 'SAME' ? (rough ? 2 : 1) : (rough ? 2 : 0)
  }
  const ratio = planned == null ? 1 : sets.length / planned
  const completion = ratio >= 1 ? 3 : ratio >= 0.75 ? 2 : ratio >= 0.5 ? 1 : 0
  const progress = compared === 0 ? 1 : Math.floor((3 * halves) / (2 * compared))
  const prs = sets.filter((s) => s.isPr).length
  const total = 3 + completion + progress + (prs > 0 ? 1 : 0)
  return { score: Math.min(10, Math.max(4, total)), doneSets: sets.length, plannedSets: planned, up, compared, prs }
}

type ScoreSession = Pick<SessionEntity, 'planDayId' | 'epochDay' | 'startedAt'> & { id?: number }
type ScoreSet = Done & Pick<SetEntity, 'sessionId' | 'exerciseId'>

/**
 * The score of every workout with done sets, in one pass over data already loaded. `plannedByDay` is the sum of `sets` over
 * each plan day's items; `lowReadinessDays` the epoch days whose recovery said ease off or recover.
 */
export function scoresForSessions(
  sessions: ScoreSession[], doneSets: ScoreSet[], kinds: Map<string, ExerciseKind>,
  plannedByDay: Map<number, number>, lowReadinessDays: Set<number>,
): Map<number, WorkoutScoreResult> {
  const bySession = new Map<number, ScoreSet[]>()
  for (const s of doneSets) bySession.set(s.sessionId, [...(bySession.get(s.sessionId) ?? []), s])
  const byId = new Map(sessions.map((s) => [s.id!, s]))
  // Per exercise, its workouts oldest first, so "last time" is the nearest one before.
  const history = new Map<string, { session: ScoreSession; sets: ScoreSet[] }[]>()
  for (const [id, list] of bySession) {
    const session = byId.get(id)
    if (!session) continue
    for (const exId of new Set(list.map((s) => s.exerciseId))) {
      history.set(exId, [...(history.get(exId) ?? []), { session, sets: list.filter((s) => s.exerciseId === exId) }])
    }
  }
  for (const l of history.values()) l.sort((a, b) => a.session.epochDay - b.session.epochDay || a.session.startedAt - b.session.startedAt)
  const out = new Map<number, WorkoutScoreResult>()
  for (const s of sessions) {
    const mine = bySession.get(s.id!) ?? []
    if (!mine.length) continue
    const work: ScoreWork[] = [...new Set(mine.map((x) => x.exerciseId))].map((exId) => {
      const earlier = (history.get(exId) ?? []).filter((h) => h.session.id !== s.id && (h.session.epochDay < s.epochDay || (h.session.epochDay === s.epochDay && h.session.startedAt < s.startedAt)))
      return { exerciseId: exId, kind: kinds.get(exId) ?? 'REPS', done: mine.filter((x) => x.exerciseId === exId), last: earlier.at(-1)?.sets ?? [] }
    })
    out.set(s.id!, computeWorkoutScore(work, s.planDayId != null ? plannedByDay.get(s.planDayId) ?? null : null, lowReadinessDays.has(s.epochDay)))
  }
  return out
}
