// The workout debrief, worked out from the person's own numbers (docs/plans/math-debrief-plan.md, section C). Pure: it
// returns typed lines with their numbers and the UI turns them into English or Arabic. It reuses workout-score.ts for
// trends and records, progression.ts for next time, trends.ts for plateau and deload and exercise-tiers.ts for a harder
// version. Port of app/.../domain/WorkoutDebriefMath.kt.
import { weekStart } from './dates'
import { effortOf, type SetEffort } from './effort'
import type { ExerciseEntity, PlanItemEntity, SessionEntity, SetEntity } from './entities'
import { harderVersion, readyForHarder } from './exercise-tiers'
import type { Equipment } from './exercise-swap-engine'
import type { ExerciseKind } from './muscle'
import { nextPrefill, progressionInputs, type ProgressionInputs, type ProgressionResult } from './progression'
import { recordScore } from './progress'
import { percentChange, type FatigueAndPlateauReport } from './trends'
import type { WeightUnit } from './units'
import {
  SAME_WEIGHT_KG, isWarmUp, prKindOf, trend as trendOf, trendStats,
  type PrKind, type Trend, type TrendStats, type WorkoutScoreResult,
} from './workout-score'

const f = Math.fround

/** In priority order (the first that matches wins), with how many wordings each has, picked by session id. */
export const HEADLINE_VARIANTS = {
  FIRST_EVER: 3, AFTER_BREAK: 3, ONLY_WARM_UPS: 2, ALL_NEW: 2, ROUGH_HELD: 3, ROUGH_SCALED: 3, SHORT: 3,
  PR_AND_UP: 3, MOSTLY_UP: 3, ALL_SAME: 3, MIXED: 3, MOSTLY_DOWN: 3,
} as const
export type Headline = keyof typeof HEADLINE_VARIANTS
export type HeadlineLine = { type: Headline; variant: number }

/** Weights are in kg; totals are reps, or seconds for holds. */
export type Win =
  /** The record set: weight and reps, reps (with any added weight), or seconds. */
  | { type: 'PR'; exerciseId: string; kind: PrKind; weightKg: number | null; reps: number | null; seconds: number | null }
  /** The best set improved by `percent` (by recordScore). */
  | { type: 'BEST_GAIN'; exerciseId: string; percent: number }
  | { type: 'MORE_WORK'; exerciseId: string; kind: ExerciseKind; from: number; to: number }
  /** Same numbers with fewer Hard or more Easy sets than last time. */
  | { type: 'EFFORT_MASTERY'; exerciseId: string }
  | { type: 'ALL_SETS'; sets: number }
  | { type: 'WEEKLY_TARGET'; target: number }
  | { type: 'BACK_AFTER_GAP'; exerciseId: string; days: number }
  /** Starting numbers set for `count` new exercises. */
  | { type: 'NEW_STARTS'; count: number }

export type Watch =
  /** Pain this time and last time: swap it for a pain-free option. */
  | { type: 'PAIN_SWAP'; exerciseId: string }
  /** Next time is eased (one load step lighter); if it hurts again, swap it. */
  | { type: 'PAIN_EASED'; exerciseId: string }
  /** Bodyweight or hold: next time keeps the same numbers; if it hurts again, swap it. */
  | { type: 'PAIN_SAME_NUMBERS'; exerciseId: string }
  | { type: 'DELOAD' }
  | { type: 'PLATEAU'; exerciseId: string; workouts: number; days: number }
  | { type: 'DROP'; exerciseId: string }
  /** `count` planned exercises had no done sets; `exerciseId` is the first. */
  | { type: 'SKIPPED'; exerciseId: string; count: number }

/** Next time's numbers from nextPrefill; weights in kg. Listed in priority order. */
export type Next =
  | { type: 'SWAP'; exerciseId: string }
  | { type: 'LIGHTER'; exerciseId: string; weightKg: number; reps: number | null }
  /** `harderName` null: suggest a slower tempo. */
  | { type: 'HARDER'; exerciseId: string; harderName: string | null }
  | { type: 'LOAD_UP'; exerciseId: string; weightKg: number; reps: number | null }
  | { type: 'PLUS_REP'; exerciseId: string; reps: number; weightKg: number | null }
  | { type: 'PLUS_SECONDS'; exerciseId: string; seconds: number }
  /** The top of the range was hit, but a Hard set keeps the numbers. */
  | { type: 'HOLD_STEADY'; exerciseId: string; weightKg: number | null; reps: number | null; seconds: number | null }

export type DebriefResult = {
  headline: HeadlineLine
  wins: Win[]
  watch: Watch[]
  next: Next[]
  /** "Everything else: same numbers": some exercise shown for next time keeps its numbers. */
  sameRest: boolean
  /** "The app eases off by itself on a low-recovery day." */
  footnote: boolean
}

type DebriefSet = Pick<SetEntity, 'sessionId' | 'exerciseId' | 'itemPosition' | 'block' | 'setIndex' | 'reps' | 'seconds' | 'weightKg' | 'isPr' | 'effort' | 'note'>
type DebriefItem = Pick<PlanItemEntity, 'exerciseId' | 'block' | 'sets' | 'repsMin' | 'repsMax' | 'seconds' | 'weightKg'>

/** One exercise of the workout. */
export type DebriefExercise = {
  exerciseId: string
  kind: ExerciseKind
  /** This workout's done sets of it, warm-ups included (they are left out here). */
  done: DebriefSet[]
  /** Its previous time's done sets (the most recent earlier workout with it), empty when none. */
  last: DebriefSet[]
  /** Days since that previous time; null when none. */
  gapDays: number | null
  /** No later workout contains it, so next time applies. */
  newest: boolean
  /** Its item on the plan day, for the target; null without one. */
  item: DebriefItem | null
  /** harderVersion's name; null when there is none. */
  harderName: string | null
}

export type DebriefInput = {
  sessionId: number
  score: WorkoutScoreResult
  exercises: DebriefExercise[]
  /** The plan day's exercises, warm-ups left out; empty without a plan day. */
  plannedExerciseIds: ReadonlySet<string>
  /** Workouts per week from the days/week setting; 0 means none. */
  weeklyTarget: number
  /** Workout days this week, with this workout and before it. */
  weekCount: number
  weekCountBefore: number
  report: FatigueAndPlateauReport | null
  unit: WeightUnit
  healthOn: boolean
}

/** An exercise back after this many days or more is a win. */
export const BACK_AFTER_DAYS = 42

/** What the rules need per exercise, worked out once. */
type Ex = {
  e: DebriefExercise; id: string; working: DebriefSet[]; lastWorking: DebriefSet[]
  trend: Trend | null; forgiven: boolean; pr: PrKind | null; pain: boolean; painLast: boolean
  now: TrendStats; was: TrendStats | null
}

const effort = (s: DebriefSet): SetEffort => effortOf(s.effort, s.note)
const count = (l: DebriefSet[], e: SetEffort) => l.filter((s) => effort(s) === e).length
const PR_ORDER: Record<PrKind, number> = { WEIGHT: 0, REPS: 1, HOLD: 2 }

export function computeDebrief(input: DebriefInput): DebriefResult {
  const score = input.score
  const exs: Ex[] = []
  for (const e of input.exercises) {
    const working = e.done.filter((s) => !isWarmUp(s.block))
    if (!working.length) continue
    const lastWorking = e.last.filter((s) => !isWarmUp(s.block))
    const fromScore = score.exercises?.find((x) => x.exerciseId === e.exerciseId)
    const pain = working.some((s) => effort(s) === 'PAIN')
    exs.push({
      e, id: e.exerciseId, working, lastWorking,
      trend: fromScore?.trend ?? (lastWorking.length ? trendOf(e.kind, e.exerciseId, working, lastWorking) : null),
      // Pain read from notes too, the same way Progression reads it.
      forgiven: (fromScore?.forgiven ?? false) || pain,
      pr: fromScore?.pr ?? (e.done.some((s) => s.isPr) ? prKindOf(e.kind) : null),
      pain,
      painLast: lastWorking.some((s) => effort(s) === 'PAIN'),
      now: trendStats(e.kind, e.exerciseId, working),
      was: lastWorking.length ? trendStats(e.kind, e.exerciseId, lastWorking) : null,
    })
  }
  const rough = score.rough || exs.some((x) => x.pain)
  const headline = pickHeadline(input, exs, rough)
  const { next, sameRest } = nextTime(input, exs)
  return {
    headline: { type: headline, variant: ((input.sessionId % HEADLINE_VARIANTS[headline]) + HEADLINE_VARIANTS[headline]) % HEADLINE_VARIANTS[headline] },
    wins: wins(input, exs, headline),
    watch: watch(input, exs, headline, rough),
    next,
    sameRest,
    footnote: input.healthOn || rough,
  }
}

function pickHeadline(input: DebriefInput, exs: Ex[], rough: boolean): Headline {
  const score = input.score
  const compared = exs.filter((x) => x.trend != null)
  const up = compared.filter((x) => x.trend === 'UP').length
  const downs = compared.filter((x) => x.trend === 'DOWN')
  const unforgiven = downs.filter((x) => !x.forgiven).length
  const effective = score.effectiveSets ?? null
  if (score.firstEver) return 'FIRST_EVER'
  if (score.afterBreak) return 'AFTER_BREAK'
  if (!exs.length) return 'ONLY_WARM_UPS'
  if (!compared.length) return 'ALL_NEW'
  if (rough && !downs.length) return 'ROUGH_HELD'
  if (rough && unforgiven === 0) return 'ROUGH_SCALED'
  if (effective != null && score.doneSets * 4 < effective * 3 && score.prs === 0) return 'SHORT'
  if (score.prs > 0 && up * 2 >= compared.length) return 'PR_AND_UP'
  if (up * 2 >= compared.length || score.prs > 0) return 'MOSTLY_UP'
  if (up === 0 && unforgiven === 0) return 'ALL_SAME'
  if (unforgiven * 2 <= compared.length) return 'MIXED'
  return 'MOSTLY_DOWN'
}

function wins(input: DebriefInput, exs: Ex[], headline: Headline): Win[] {
  const score = input.score
  const candidates: Win[] = []
  // 1. Records: weight > reps > hold.
  for (const x of exs.filter((x) => x.pr != null).sort((a, b) => PR_ORDER[a.pr!] - PR_ORDER[b.pr!])) {
    const pool = x.working.filter((s) => s.isPr)
    let top = (pool.length ? pool : x.working)[0]
    for (const s of pool.length ? pool : x.working) {
      if (recordScore(x.e.kind, s.reps, s.seconds, s.weightKg, x.id) > recordScore(x.e.kind, top.reps, top.seconds, top.weightKg, x.id)) top = s
    }
    candidates.push({ type: 'PR', exerciseId: x.id, kind: x.pr!, weightKg: top.weightKg != null && top.weightKg > 0 ? top.weightKg : null, reps: top.reps, seconds: top.seconds })
  }
  // 2. The biggest gain in the best set among UP exercises.
  let gain: { x: Ex; pct: number } | null = null
  for (const x of exs) {
    if (x.trend !== 'UP' || !x.was) continue
    const pct = percentChange(x.was.best, x.now.best)
    if (pct != null && pct > 0 && (!gain || pct > gain.pct)) gain = { x, pct }
  }
  if (gain) candidates.push({ type: 'BEST_GAIN', exerciseId: gain.x.id, percent: gain.pct })
  // 3. More total work at the same or a heavier weight.
  for (const x of exs) {
    if (x.trend === 'UP' && x.was && x.now.total > x.was.total && x.now.top >= f(x.was.top - SAME_WEIGHT_KG)) {
      candidates.push({ type: 'MORE_WORK', exerciseId: x.id, kind: x.e.kind, from: x.was.total, to: x.now.total })
    }
  }
  // 4. Effort mastery: the same numbers, felt easier.
  for (const x of exs) {
    if (x.trend !== 'SAME' || !x.was || x.pain || Math.abs(x.now.top - x.was.top) > SAME_WEIGHT_KG || x.now.total < x.was.total) continue
    if (count(x.working, 'HARD') < count(x.lastWorking, 'HARD') || count(x.working, 'EASY') > count(x.lastWorking, 'EASY')) {
      candidates.push({ type: 'EFFORT_MASTERY', exerciseId: x.id })
    }
  }
  // 5. Every planned set (as the score counts them) done.
  const effective = score.effectiveSets ?? null
  if (effective != null && score.doneSets >= effective) candidates.push({ type: 'ALL_SETS', sets: effective })
  // 6. This workout is the one that reaches the weekly target.
  if (input.weeklyTarget > 0 && input.weekCount === input.weeklyTarget && input.weekCountBefore < input.weeklyTarget) {
    candidates.push({ type: 'WEEKLY_TARGET', target: input.weeklyTarget })
  }
  // 7. Back after a long gap, or starting numbers on an all-new workout.
  if (headline === 'ALL_NEW') candidates.push({ type: 'NEW_STARTS', count: exs.length })
  else for (const x of exs) if (x.e.gapDays != null && x.e.gapDays >= BACK_AFTER_DAYS) candidates.push({ type: 'BACK_AFTER_GAP', exerciseId: x.id, days: x.e.gapDays })

  const out: Win[] = []
  const used = new Set<string>()
  for (const w of candidates) {
    if (out.length === 2) break
    const id = 'exerciseId' in w ? w.exerciseId : null
    if (id != null && used.has(id)) continue
    if (id != null) used.add(id)
    out.push(w)
  }
  return out
}

function watch(input: DebriefInput, exs: Ex[], headline: Headline, rough: boolean): Watch[] {
  const score = input.score
  const painful = exs.filter((x) => x.pain)
  const px = painful.find((x) => x.painLast) ?? painful[0]
  const pain: Watch | null = !px ? null
    : px.painLast ? { type: 'PAIN_SWAP', exerciseId: px.id }
      // Progression keeps a bodyweight move's or hold's numbers on pain.
      : px.e.kind !== 'WEIGHTED' || px.now.top <= 0 ? { type: 'PAIN_SAME_NUMBERS', exerciseId: px.id }
        : { type: 'PAIN_EASED', exerciseId: px.id }
  const deload = input.report?.needsDeload === true && !score.firstEver && !score.afterBreak
  if (pain) return deload ? [pain, { type: 'DELOAD' }] : [pain]
  if (deload) return [{ type: 'DELOAD' }]
  if (!score.afterBreak) {
    for (const x of exs) {
      const lift = input.report?.stalledLifts.find((l) => l.exerciseId === x.id)
      if (lift) return [{ type: 'PLATEAU', exerciseId: lift.exerciseId, workouts: lift.sessionsCount, days: lift.spanDays }]
    }
  }
  // A comeback is never told about a drop.
  if (headline !== 'MOSTLY_DOWN' && !score.afterBreak) {
    const drop = exs.find((x) => x.trend === 'DOWN' && !x.forgiven)
    if (drop) return [{ type: 'DROP', exerciseId: drop.id }]
  }
  const doneIds = new Set(input.exercises.filter((e) => e.done.length).map((e) => e.exerciseId))
  const skipped = [...input.plannedExerciseIds].filter((id) => !doneIds.has(id))
  // An unplanned exercise logged is a swap, not a skip.
  const swapped = exs.some((x) => !input.plannedExerciseIds.has(x.id))
  if (skipped.length && !swapped && !rough && headline !== 'SHORT' && !score.firstEver && !score.afterBreak) {
    return [{ type: 'SKIPPED', exerciseId: skipped[0], count: skipped.length }]
  }
  return []
}

/** Up to 3 change lines, and whether some exercise shown keeps its numbers. */
function nextTime(input: DebriefInput, exs: Ex[]): { next: Next[]; sameRest: boolean } {
  const lines: { order: number; line: Next }[] = []
  let unchanged = false
  for (const x of exs) {
    if (!x.e.newest) continue
    const p = progressionInputs(x.e.item, x.id, [...x.working, ...x.lastWorking])
    if (p.warmup) continue
    const r = nextPrefill(x.e.kind, p.target, p.last, p.previous, input.unit, false, false)
    const line = nextLine(x, p, r)
    if (line) lines.push(line)
    else unchanged = true
  }
  return { next: lines.sort((a, b) => a.order - b.order).slice(0, 3).map((l) => l.line), sameRest: unchanged }
}

/** The one line for an exercise with its order, or null when its numbers stay the same. */
function nextLine(x: Ex, p: ProgressionInputs, r: ProgressionResult): { order: number; line: Next } | null {
  const kind = x.e.kind
  if (!r.sets.length) return null
  const nowTop = Math.max(...p.last.map((s) => f(s.weightKg ?? 0)))
  const nextTop = Math.max(...r.sets.map((s) => f(s.weightKg ?? 0)))
  const firstReps = r.sets[0].reps
  if (x.pain && x.painLast) return { order: 1, line: { type: 'SWAP', exerciseId: x.id } }
  if (kind === 'WEIGHTED' && nextTop < f(nowTop - SAME_WEIGHT_KG)) return { order: 2, line: { type: 'LIGHTER', exerciseId: x.id, weightKg: nextTop, reps: firstReps } }
  const harder = (kind === 'HOLD' && r.holdCeilingReached) ||
    (kind === 'REPS' && x.id !== 'jumping_jacks' && readyForHarder(p.last, p.previous))
  if (harder) return { order: 3, line: { type: 'HARDER', exerciseId: x.id, harderName: x.e.harderName } }
  if (nextTop > f(nowTop + SAME_WEIGHT_KG)) return { order: 4, line: { type: 'LOAD_UP', exerciseId: x.id, weightKg: nextTop, reps: firstReps } }
  const lastAt = (i: number) => p.last[i] ?? p.last[p.last.length - 1]
  if (kind === 'HOLD') {
    if (r.sets.some((s, i) => (s.seconds ?? 0) > (lastAt(i).seconds ?? 0))) {
      return { order: 5, line: { type: 'PLUS_SECONDS', exerciseId: x.id, seconds: Math.max(...r.sets.map((s) => s.seconds ?? 0)) } }
    }
  } else if (Math.max(...r.sets.map((s) => s.reps ?? 0)) > Math.max(...p.last.map((s) => s.reps ?? 0))) {
    // The top set gains reps (a failed set back to the bottom of the range alone doesn't count).
    return { order: 5, line: { type: 'PLUS_REP', exerciseId: x.id, reps: Math.max(...r.sets.map((s) => s.reps ?? 0)), weightKg: nextTop > 0 ? nextTop : null } }
  }
  // The top of the range was hit, but a Hard set kept the numbers.
  const sets = p.target?.sets ?? 3
  const hitTop = p.last.length >= sets && (kind === 'HOLD'
    ? p.last.every((s) => (s.seconds ?? 0) >= (p.target?.seconds ?? 30))
    : p.last.every((s) => (s.reps ?? 0) >= (p.target?.repsMax ?? p.target?.repsMin ?? 12)))
  if (!x.pain && hitTop && p.last.some((s) => s.effort === 'HARD')) {
    const holds = kind === 'HOLD'
    return {
      order: 6,
      line: {
        type: 'HOLD_STEADY', exerciseId: x.id, weightKg: nowTop > 0 ? nowTop : null,
        reps: holds ? null : Math.max(...p.last.map((s) => s.reps ?? 0)), seconds: holds ? Math.max(...p.last.map((s) => s.seconds ?? 0)) : null,
      },
    }
  }
  return null
}

type ScoreSession = Pick<SessionEntity, 'epochDay' | 'startedAt'> & { id?: number }

/**
 * Gathers the inputs for one finished workout from data already loaded and returns its debrief. `sessions` and
 * `doneSets` are every finished workout and its done sets; `score` is this workout's score; `planItems` the items of
 * its plan day as the plan is now (empty without one); `weeklyTarget` from the days/week setting (fullTarget); `report`
 * from analyzeFatigueAndPlateaus as of the workout's day. Same as Android WorkoutDebriefMath.forSession.
 */
export function debriefForSession(
  session: ScoreSession,
  sessions: readonly ScoreSession[],
  doneSets: readonly DebriefSet[],
  exercises: ReadonlyMap<string, ExerciseEntity>,
  score: WorkoutScoreResult,
  planItems: readonly DebriefItem[],
  weeklyTarget: number,
  report: FatigueAndPlateauReport | null,
  unit: WeightUnit,
  healthOn: boolean,
  equipment: Set<Equipment>,
): DebriefResult {
  const byId = new Map(sessions.map((s) => [s.id!, s]))
  const earlier = (o: ScoreSession, s: ScoreSession) => o.epochDay < s.epochDay || (o.epochDay === s.epochDay && o.startedAt < s.startedAt)
  const mine = doneSets.filter((s) => s.sessionId === session.id).sort((a, b) => a.itemPosition - b.itemPosition || a.setIndex - b.setIndex)
  const library = [...exercises.values()]
  const list: DebriefExercise[] = [...new Set(mine.map((s) => s.exerciseId))].map((id) => {
    const bySession = new Map<number, DebriefSet[]>()
    for (const s of doneSets) {
      if (s.exerciseId !== id || s.sessionId === session.id || !byId.has(s.sessionId)) continue
      bySession.set(s.sessionId, [...(bySession.get(s.sessionId) ?? []), s])
    }
    const others = [...bySession].map(([sid, sets]) => ({ session: byId.get(sid)!, sets }))
    let previous: { session: ScoreSession; sets: DebriefSet[] } | null = null
    for (const o of others) {
      if (earlier(o.session, session) && (!previous || earlier(previous.session, o.session))) previous = o
    }
    const ex = exercises.get(id)
    const kind: ExerciseKind = ex?.kind ?? 'REPS'
    return {
      exerciseId: id,
      kind,
      done: mine.filter((s) => s.exerciseId === id),
      last: [...(previous?.sets ?? [])].sort((a, b) => a.setIndex - b.setIndex),
      gapDays: previous ? session.epochDay - previous.session.epochDay : null,
      newest: !others.some((o) => earlier(session, o.session)),
      item: planItems.find((i) => i.exerciseId === id) ?? null,
      harderName: ex && kind === 'REPS' ? harderVersion(ex, library, equipment)?.name ?? null : null,
    }
  })
  const ws = weekStart(session.epochDay)
  const before = new Set(sessions.filter((s) => earlier(s, session) && s.epochDay >= ws && s.epochDay <= ws + 6).map((s) => s.epochDay))
  return computeDebrief({
    sessionId: session.id!,
    score,
    exercises: list,
    plannedExerciseIds: new Set(planItems.filter((i) => !isWarmUp(i.block)).map((i) => i.exerciseId)),
    weeklyTarget,
    weekCount: new Set([...before, session.epochDay]).size,
    weekCountBefore: before.size,
    report,
    unit,
    healthOn,
  })
}
