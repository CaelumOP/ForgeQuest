// Weekly streak, reminders, XP, recovery and personal records: same rules as Android Progress.kt.
// Kotlin uses 32-bit Float for recovery and record scores, so those go through Math.fround.
import { dayOfWeek, weekStart } from './dates'
import { effortOf } from './effort'
import type { ExerciseEntity, SetEntity } from './entities'
import type { ExerciseKind, Muscle } from './muscle'

const f = Math.fround
const clamp = (v: number, lo: number, hi: number) => Math.min(Math.max(v, lo), hi)

// ---------------------------------------------------------------- Weekly streak

export type DayState = 'DONE' | 'REST' | 'MISSED' | 'TODAY_PLANNED' | 'UPCOMING' | 'OFF' | 'EXCUSED'

/** Why a day was excused. Sick and Injured also offer an easier first session back. */
export const DAY_TAGS = ['SICK', 'INJURED', 'TRAVEL', 'REST'] as const
export type DayTag = (typeof DAY_TAGS)[number]

export function parseDayTag(raw: string | null | undefined): DayTag | null {
  return DAY_TAGS.find((t) => t === raw) ?? null
}

/** Each day's tag (sick, injured...) from the day notes; notes without a known tag are left out. */
export function dayTagMap(notes: { epochDay: number; tag: string | null }[]): Map<number, DayTag> {
  const out = new Map<number, DayTag>()
  for (const n of notes) { const t = parseDayTag(n.tag); if (t) out.set(n.epochDay, t) }
  return out
}

export type WeeklyStreak = {
  /** Consecutive weeks (ending this week if hit, otherwise last week) where the target was reached. */
  weeks: number
  best: number
  doneThisWeek: number
  targetThisWeek: number
  /** Monday..Sunday */
  days: DayState[]
  weekProgress: number
  /** The week you joined, when no planned workouts were left in it. */
  freeWeek: boolean
  hitThisWeek: boolean
}

export function fullTarget(plannedTrainingDays: ReadonlySet<number>, targetOverride: number): number {
  return targetOverride > 0 ? targetOverride : Math.max(1, plannedTrainingDays.size)
}

/**
 * A week counts when the number of workout days reaches the target. Rest days never hurt.
 * The first week is prorated from [startEpochDay] so joining on a Thursday is fair.
 * The current week never breaks the streak while it's still in progress.
 * [lockedTargets]: targets saved for finished weeks (Monday epoch day to days); a plan change never rewrites them.
 */
export function computeStreak(
  workoutDays: ReadonlySet<number>,
  plannedTrainingDays: ReadonlySet<number>,
  targetOverride: number,
  startEpochDay: number,
  today: number,
  excusedDays: ReadonlySet<number> = new Set(),
  lockedTargets: ReadonlyMap<number, number> = new Map(),
): WeeklyStreak {
  const full = fullTarget(plannedTrainingDays, targetOverride)
  const thisWeek = weekStart(today)
  const firstWeek = weekStart(startEpochDay)

  const countDays = (from: number, to: number, pred: (d: number) => boolean) => {
    let n = 0
    for (let d = from; d <= to; d++) if (pred(d)) n++
    return n
  }
  const doneIn = (ws: number) => [...workoutDays].filter((d) => d >= ws && d <= ws + 6).length

  const targetFor = (ws: number) => {
    const weekTarget = lockedTargets.get(ws) ?? full
    // Joined after the last planned day of the week: that week is free, it neither counts nor breaks.
    const base = ws !== firstWeek
      ? weekTarget
      : Math.min(weekTarget, countDays(startEpochDay, ws + 6, (d) => plannedTrainingDays.has(dayOfWeek(d))))
    // Each excused planned day (sick, travel...) lowers the week's target; a fully excused week is free.
    const excused = countDays(ws, ws + 6, (d) => excusedDays.has(d) && !workoutDays.has(d) && plannedTrainingDays.has(dayOfWeek(d)))
    return Math.max(base - excused, 0)
  }

  const doneThisWeek = doneIn(thisWeek)
  const targetThisWeek = targetFor(thisWeek)

  // A workout in a free week still counts: effort should never feel wasted.
  const hit = (ws: number) => {
    const t = targetFor(ws)
    return t === 0 ? doneIn(ws) > 0 : doneIn(ws) >= t
  }
  const free = (ws: number) => targetFor(ws) === 0 && doneIn(ws) === 0

  let streak = 0
  let week = thisWeek - 7
  while (week >= firstWeek && (hit(week) || free(week))) {
    if (hit(week)) streak++
    week -= 7
  }
  if (hit(thisWeek)) streak++

  let best = 0
  let run = 0
  for (let w = firstWeek; w <= thisWeek; w += 7) {
    if (hit(w)) {
      run++
      best = Math.max(best, run)
    } else if (!(free(w) || w === thisWeek)) {
      run = 0
    }
  }

  const days = Array.from({ length: 7 }, (_, offset): DayState => {
    const day = thisWeek + offset
    const planned = plannedTrainingDays.has(offset + 1)
    if (workoutDays.has(day)) return 'DONE'
    if (excusedDays.has(day) && day <= today) return 'EXCUSED'
    if (day === today && planned) return 'TODAY_PLANNED'
    if (day > today && planned) return 'UPCOMING'
    if (day < startEpochDay) return 'OFF'
    if (day < today && planned) return 'MISSED'
    return 'REST'
  })

  return {
    weeks: streak,
    best: Math.max(best, streak),
    doneThisWeek,
    targetThisWeek,
    days,
    weekProgress: targetThisWeek <= 0 ? (doneThisWeek > 0 ? 1 : 0) : Math.min(1, f(doneThisWeek / targetThisWeek)),
    freeWeek: targetThisWeek === 0 && doneThisWeek === 0,
    hitThisWeek: targetThisWeek === 0 ? doneThisWeek > 0 : doneThisWeek >= targetThisWeek,
  }
}

/** Finished weeks since [startEpochDay] with no saved target yet, each given today's [target] to save. */
export function weeksToLock(locked: ReadonlyMap<number, number>, target: number, startEpochDay: number, today: number): Map<number, number> {
  const result = new Map<number, number>()
  for (let w = weekStart(startEpochDay); w < weekStart(today); w += 7) if (!locked.has(w)) result.set(w, target)
  return result
}

// ---------------------------------------------------------------- Reminders & comebacks

/** After this many planned days missed in a row (not excused), reminders pause until a workout or a tag. */
export const MAX_MISSES = 3

/** Planned days missed back to back, counting back from yesterday; a workout or an excused day ends the run. */
export function missedInARow(
  today: number,
  plannedTrainingDays: ReadonlySet<number>,
  workoutDays: ReadonlySet<number>,
  excusedDays: ReadonlySet<number>,
  startEpochDay: number,
): number {
  let count = 0
  for (let day = today - 1; day >= startEpochDay && day >= today - 28; day--) {
    if (workoutDays.has(day) || excusedDays.has(day)) break
    if (plannedTrainingDays.has(dayOfWeek(day))) count++
  }
  return count
}

export function shouldRemind(plannedToday: boolean, doneToday: boolean, excusedToday: boolean, missed: number): boolean {
  return plannedToday && !doneToday && !excusedToday && missed < MAX_MISSES
}

/**
 * True for the first workout back after a sick or injured day since the last workout, or after a reminder
 * back-off: the session then offers a one-tap "Ease in" (one set fewer, never below 2).
 */
export function welcomeBack(
  today: number,
  workoutDays: ReadonlySet<number>,
  tags: ReadonlyMap<number, DayTag>,
  plannedTrainingDays: ReadonlySet<number>,
  startEpochDay: number,
): boolean {
  const before = [...workoutDays].filter((d) => d < today)
  if (before.length === 0) return false
  const lastWorkout = Math.max(...before)
  if (workoutDays.has(today)) return false
  let illSince = false
  for (let d = lastWorkout + 1; d < today; d++) if (tags.get(d) === 'SICK' || tags.get(d) === 'INJURED') illSince = true
  return illSince || missedInARow(today, plannedTrainingDays, workoutDays, new Set(tags.keys()), startEpochDay) >= MAX_MISSES
}

// ---------------------------------------------------------------- XP & rank

export type RankInfo = { letter: string; title: string }

export type LevelInfo = {
  totalXp: number
  level: number
  xpIntoLevel: number
  xpForNextLevel: number
  rank: RankInfo
  progress: number
}

export const XP_PER_WORKOUT = 50
export const XP_PER_SET = 2
export const XP_PER_PR = 25
export const XP_PER_BADGE = 100

/** XP needed to go from [level] to [level] + 1. */
export const xpStepFor = (level: number): number => 100 + 50 * (level - 1)

export function levelFor(totalXp: number): LevelInfo {
  let level = 1
  let remaining = totalXp
  while (remaining >= xpStepFor(level)) {
    remaining -= xpStepFor(level)
    level++
  }
  const next = xpStepFor(level)
  return { totalXp, level, xpIntoLevel: remaining, xpForNextLevel: next, rank: rankFor(level), progress: next === 0 ? 0 : f(remaining / next) }
}

export function rankFor(level: number): RankInfo {
  if (level >= 45) return { letter: 'S', title: 'Legend' }
  if (level >= 30) return { letter: 'A', title: 'Elite' }
  if (level >= 20) return { letter: 'B', title: 'Strong' }
  if (level >= 10) return { letter: 'C', title: 'Committed' }
  if (level >= 5) return { letter: 'D', title: 'Regular' }
  return { letter: 'E', title: 'Starter' }
}

export function totalXp(workouts: number, doneSets: number, prs: number, badges: number): number {
  return workouts * XP_PER_WORKOUT + doneSets * XP_PER_SET + prs * XP_PER_PR + badges * XP_PER_BADGE
}

// ---------------------------------------------------------------- Recovery

export type MuscleRecovery = {
  /** 0 = fully recovered, 1 = just trained hard (4+ full working sets). */
  fatigue: number
  hoursUntilReady: number
}

/** (muscle, trainedAtMillis, stimulus weight in (0, 1]) */
export type MuscleHit = [Muscle, number, number]

export const RECOVERY_WINDOW_HOURS = 48
/** Number of full working sets on a primary muscle that constitutes a 100 % (48-hour) session stimulus. */
const FULL_SESSION_EFFECTIVE_SETS = 4
const HIGH_SKILL_LOW_REP_IDS = new Set([
  'muscle_up', 'pistol_squat', 'handstand_push_up', 'tuck_planche',
  'pseudo_planche_push_up', 'tuck_front_lever', 'front_lever_row', 'back_lever_tuck', 'nordic_curl',
])

/**
 * Converts a session's completed sets into per-muscle stimulus weights in (0, 1]:
 * one full working set (10 reps or a 30 s hold) is 0.25 on each primary muscle, half that on secondary ones,
 * 4+ sets is 1.0 (48 h). Warm-up sets count a quarter. Stimulus below 0.02 is dropped.
 */
export function sessionHits(
  sets: readonly SetEntity[],
  exercises: ReadonlyMap<string, ExerciseEntity>,
  sessionFinishedAt: ReadonlyMap<number, number>,
): MuscleHit[] {
  const bySession = new Map<number, SetEntity[]>()
  for (const s of sets) {
    const list = bySession.get(s.sessionId)
    if (list) list.push(s)
    else bySession.set(s.sessionId, [s])
  }
  const result: MuscleHit[] = []
  for (const [sessionId, sessionSets] of bySession) {
    const finishedAt = sessionFinishedAt.get(sessionId)
    if (finishedAt == null) continue
    const stimulus = new Map<Muscle, number>()
    for (const set of sessionSets) {
      if (!set.done) continue
      const ex = exercises.get(set.exerciseId)
      if (!ex) continue
      const eff = effectiveSetEffort(set, ex)
      if (eff <= 0) continue
      const primaryDelta = f(eff / FULL_SESSION_EFFECTIVE_SETS)
      const secondaryDelta = f(primaryDelta * 0.5)
      for (const m of ex.primary) stimulus.set(m, f((stimulus.get(m) ?? 0) + primaryDelta))
      for (const m of ex.secondary) stimulus.set(m, f((stimulus.get(m) ?? 0) + secondaryDelta))
    }
    for (const [muscle, total] of stimulus) {
      const clamped = clamp(total, 0, 1)
      if (clamped >= f(0.02)) result.push([muscle, finishedAt, clamped])
    }
  }
  return result
}

/** Working-set equivalent for one logged set: a full working set is 1.0, one bodyweight rep 0.10. */
export function effectiveSetEffort(set: SetEntity, ex: ExerciseEntity): number {
  let rawEffort: number
  if (ex.kind === 'HOLD') {
    const sec = set.seconds ?? 0
    if (sec <= 0) return 0
    const refSec = HIGH_SKILL_LOW_REP_IDS.has(ex.id) ? 12 : 30
    rawEffort = clamp(f(sec / refSec), f(0.08), 1.25)
  } else if (ex.kind === 'WEIGHTED') {
    const r = set.reps ?? 0
    if (r <= 0) return 0
    // With external load, 6 reps already count as a full working set (10 without); a heavy triple counts half.
    const refReps = (set.weightKg ?? 0) > 0 ? 6 : 10
    rawEffort = clamp(f(r / refReps), f(0.15), 1.25)
  } else {
    const r = set.reps ?? 0
    if (r <= 0) return 0
    const refReps = HIGH_SKILL_LOW_REP_IDS.has(ex.id) ? 4 : (set.weightKg ?? 0) > 0 ? 6 : 10
    rawEffort = clamp(f(r / refReps), f(0.08), 1.25)
  }
  const blockFactor = set.block.toLowerCase() === 'warm-up' || ex.id === 'jumping_jacks' ? 0.25 : 1.0
  // web: audit #8 (Android reads notes with its own word list here; the web uses the one in effort.ts)
  const effort = effortOf(set.effort, set.note)
  const rpeFactor = effort === 'HARD' || effort === 'PAIN' ? f(1.15) : effort === 'EASY' ? f(0.8) : 1.0
  return clamp(f(f(rawEffort * blockFactor) * rpeFactor), 0, f(1.35))
}

/**
 * Fatigue per muscle at [now]. Fatigue clears at 1 / WINDOW_HOURS per hour. Sessions add up: what's left of an
 * earlier session plus the new one (capped at 1), so training a muscle twice in a day counts both (audit 2026-10-24).
 */
export function computeRecovery(hits: readonly MuscleHit[], now: number): Map<Muscle, MuscleRecovery> {
  const byMuscle = new Map<Muscle, MuscleHit[]>()
  for (const h of hits) if (h[2] > 0 && h[1] <= now) byMuscle.set(h[0], [...(byMuscle.get(h[0]) ?? []), h])
  const result = new Map<Muscle, MuscleRecovery>()
  for (const [muscle, list] of byMuscle) {
    let fatigue = 0
    let lastAt: number | null = null
    for (const [, at, weight] of [...list].sort((a, b) => a[1] - b[1])) {
      if (lastAt != null) fatigue = Math.max(f(fatigue - f(f(f(at - lastAt) / 3_600_000) / RECOVERY_WINDOW_HOURS)), 0)
      fatigue = Math.min(f(fatigue + clamp(f(weight), 0, 1)), 1)
      lastAt = at
    }
    fatigue = f(fatigue - f(f(f(now - lastAt!) / 3_600_000) / RECOVERY_WINDOW_HOURS))
    if (fatigue <= 0) continue
    // + 0.001 so float noise never turns 36 h into 35.
    result.set(muscle, { fatigue, hoursUntilReady: Math.max(Math.trunc(f(fatigue * RECOVERY_WINDOW_HOURS + f(0.001))), 1) })
  }
  return result
}

// ---------------------------------------------------------------- Personal records

/**
 * Comparable score for one set:
 * - WEIGHTED: Epley-style estimate, linear up to 12 reps and logarithmic above, so 5×100 kg beats 8×80 kg.
 *   Without load it is reps / 100.
 * - REPS and HOLD also credit added weight (vest or belt), so going from bodyweight to +10 kg is a record.
 * - Weighted pull-ups, chin-ups, dips and push-ups move the body too, so their Epley score uses body + added load
 *   (then takes the body back off): +10 kg × 10 beats +15 kg × 1. A fixed 75 kg reference body keeps one person's
 *   sets in the right order without needing their weight (audit 2026-10-24).
 */
export const REFERENCE_BODY_KG = 75
/** Share of body weight each rep moves: pull-ups, chin-ups and dips the whole body, push-ups about 65 %. */
const BODY_SHARE: Record<string, number> = { weighted_pull_up: 1, weighted_chin_up: 1, weighted_dip: 1, weighted_push_up: f(0.65) }

export function recordScore(kind: ExerciseKind, reps: number | null, seconds: number | null, weightKg: number | null, exerciseId = ''): number {
  const extraKg = Math.max(f(weightKg ?? 0), 0)
  if (kind === 'REPS') {
    const r = Math.max(reps ?? 0, 0)
    return r === 0 ? 0 : f(r + f(extraKg * f(0.35)))
  }
  if (kind === 'HOLD') {
    const s = Math.max(seconds ?? 0, 0)
    return s === 0 ? 0 : f(s + extraKg)
  }
  const r = Math.max(reps ?? 0, 0)
  if (r === 0) return 0
  const body = f(REFERENCE_BODY_KG * (BODY_SHARE[exerciseId] ?? 0))
  if (extraKg <= 0 && body <= 0) return f(r / 100)
  const effectiveReps = r <= 12 ? r : f(12 + f(3 * f(Math.log(f(1 + (r - 12))))))
  return f(f(f(body + extraKg) * f(1 + f(effectiveReps / 30))) - body)
}

/**
 * Which sets count as personal records when the whole history of one exercise is replayed in order.
 * [sessions] is oldest first, each holding that session's done sets. The first session never produces a record;
 * after that, the best set of a session is a record when it beats every set before it.
 */
export function personalRecords<T>(
  kind: ExerciseKind,
  sessions: readonly (readonly T[])[],
  reps: (t: T) => number | null,
  seconds: (t: T) => number | null,
  weightKg: (t: T) => number | null,
  exerciseId = '',
): Set<T> {
  const score = (t: T) => recordScore(kind, reps(t), seconds(t), weightKg(t), exerciseId)
  const result = new Set<T>()
  let best: number | null = null
  for (const sets of sessions) {
    if (sets.length === 0) continue
    let top = sets[0]
    for (const s of sets) if (score(s) > score(top)) top = s
    const topScore = score(top)
    if (best != null && topScore > best) result.add(top)
    best = best == null ? topScore : Math.max(best, topScore)
  }
  return result
}
