// Pure, non-destructive port of Android RoutineAuditor.kt.
import type { ExerciseEntity, PlanDraft } from './entities'
import { bucketOf } from './baseline'
import { profileFor } from './exercise-swap-engine'
import { MUSCLES, type Muscle } from './muscle'
import { COOL_DOWN_BLOCK, isCompoundExercise } from './plan-builder'
import { isExtraBlock } from './pr-picker'
import type { ScheduleMode } from './rotation'
import { isWarmUp } from './workout-score'

export const AUDIT_KINDS = [
  'ABOVE_MRV', 'RECOVERY_OVERLAP', 'JUNK_VOLUME', 'PUSH_PULL', 'BELOW_MEV', 'ISOLATION_FIRST', 'SHORT_PLAN',
] as const
export type AuditKind = (typeof AUDIT_KINDS)[number]
export type AuditItem = { exerciseId: string; sets: number; block?: string; position?: number; restSeconds?: number }
export type AuditDay = { dayOfWeek?: number | null; items: readonly AuditItem[] }
export type Finding = {
  kind: AuditKind; muscle?: Muscle; dayIndex?: number; value: number; limit: number; dayOfWeek?: number | null
}
export type Landmark = { mev: number; mrv: number }

export const DAY_MUSCLE_LIMIT = 10
export const OVERLAP_SETS = 6
export const PUSH_PULL_MIN = 6
export const SHORT_PLAN_MAX_DAYS = 2
export const SHORT_PLAN_MAX_MINUTES = 25

/**
 * The one counting rule for weekly volume, shared with PlanBuilder: each distinct primary muscle earns 1.0 per set,
 * each secondary muscle that is not also primary earns 0.5.
 */
export function credits(ex: ExerciseEntity): Map<Muscle, number> {
  const primary = new Set(ex.primary)
  const out = new Map<Muscle, number>()
  for (const m of primary) out.set(m, 1.0)
  for (const m of ex.secondary) {
    if (!primary.has(m) && !out.has(m)) out.set(m, 0.5)
  }
  return out
}

/** +1 for a push set, -1 for a pull set, 0 otherwise (same buckets as the baseline). */
export function pushPull(ex: ExerciseEntity): number {
  const b = bucketOf(profileFor(ex).pattern)
  return b === 'PUSH' ? 1 : b === 'PULL' ? -1 : 0
}

/** Push sets break balance once there are at least PUSH_PULL_MIN and they exceed 1.2 x the pull sets. */
export function pushPullBroken(push: number, pull: number): boolean {
  return push >= PUSH_PULL_MIN && push * 10 > pull * 12
}

export const BELOW_MEV_MUSCLES = new Set<Muscle>(['CHEST', 'UPPER_BACK', 'SHOULDERS', 'QUADS', 'HAMSTRINGS', 'GLUTES'])
const LANDMARKS: Partial<Record<Muscle, Landmark>> = {
  CHEST: { mev: 8, mrv: 22 },
  UPPER_BACK: { mev: 10, mrv: 25 },
  SHOULDERS: { mev: 8, mrv: 26 },
  BICEPS: { mev: 8, mrv: 20 },
  TRICEPS: { mev: 6, mrv: 18 },
  QUADS: { mev: 8, mrv: 20 },
  HAMSTRINGS: { mev: 6, mrv: 20 },
  GLUTES: { mev: 4, mrv: 16 },
  CALVES: { mev: 8, mrv: 20 },
  ABS: { mev: 0, mrv: 25 },
  TRAPS: { mev: 0, mrv: 26 },
}
const DEFAULT_LANDMARK: Landmark = { mev: 0, mrv: 20 }

export function landmarkFor(muscle: Muscle): Landmark {
  return LANDMARKS[muscle] ?? DEFAULT_LANDMARK
}

export function fromDraft(draft: PlanDraft): AuditDay[] {
  return draft.days.filter((d) => !d.isRest).map((d) => ({
    dayOfWeek: d.dayOfWeek,
    items: d.items.map((item, position) => ({ ...item, position, restSeconds: item.restSeconds ?? 90 })),
  }))
}

/** Structural adapter keeps the pure domain independent of the storage layer. */
export function fromActivePlan(plan: {
  days: readonly { day: { isRest: boolean; dayOfWeek: number }; items: readonly AuditItem[] }[]
}): AuditDay[] {
  return plan.days.filter((d) => !d.day.isRest).sort((a, b) => a.day.dayOfWeek - b.day.dayOfWeek)
    .map((d) => ({
      dayOfWeek: d.day.dayOfWeek,
      items: [...d.items]
        .sort((a, b) => (a.position ?? 0) - (b.position ?? 0))
        .map((item) => ({ ...item, restSeconds: item.restSeconds ?? 90 })),
    }))
}

/** Minutes one day takes: sets x (40 s + rest) for the work, plus 5 for a warm-up block and 5 for a cool-down. */
export function estimateMinutes(day: AuditDay): number {
  const work = day.items
    .filter((it) => !isExtraBlock(it.block) && it.sets > 0)
    .reduce((sum, it) => sum + it.sets * (40 + (it.restSeconds ?? 90)), 0)
  const extras =
    (day.items.some((it) => isWarmUp(it.block)) ? 5 : 0) +
    (day.items.some((it) => it.block?.toLowerCase() === COOL_DOWN_BLOCK.toLowerCase()) ? 5 : 0)
  return Math.ceil(work / 60) + extras
}

/** At most SHORT_PLAN_MAX_DAYS training days, or an average session of SHORT_PLAN_MAX_MINUTES minutes or less. */
export function isShortPlan(days: readonly AuditDay[]): boolean {
  const training = days.filter((d) => d.items.some((it) => !isExtraBlock(it.block) && it.sets > 0))
  if (training.length === 0) return false
  if (training.length <= SHORT_PLAN_MAX_DAYS) return true
  return training.reduce((sum, d) => sum + estimateMinutes(d), 0) <= SHORT_PLAN_MAX_MINUTES * training.length
}

export function audit(
  days: readonly AuditDay[],
  exercises: ReadonlyMap<string, ExerciseEntity>,
  scheduleMode: ScheduleMode = 'WEEKDAY',
  shortPlanNote = true,
): Finding[] {
  const workingDays = days.map((day) => day.items
    .filter((it) => !isExtraBlock(it.block) && it.sets > 0 && exercises.has(it.exerciseId))
    .sort((a, b) => (a.position ?? 0) - (b.position ?? 0)))
  if (workingDays.every((items) => items.length === 0)) return []
  let findings: Finding[] = []
  const weeklyVolume = new Map<Muscle, number>()
  let pushSets = 0
  let pullSets = 0
  const dayPrimarySetsByDow = new Map<number, Map<Muscle, number>>()
  const indexByDow = new Map<number, number>()
  workingDays.forEach((items, dayIdx) => {
    const day = days[dayIdx]
    const dayPrimary = new Map<Muscle, number>()
    const seenIsoMuscles = new Set<Muscle>()
    const reportedIsoFirst = new Set<Muscle>()
    for (const item of items) {
      const ex = exercises.get(item.exerciseId)!
      const primary = new Set(ex.primary)
      for (const [m, c] of credits(ex)) {
        weeklyVolume.set(m, (weeklyVolume.get(m) ?? 0) + item.sets * c)
        if (c >= 1.0) dayPrimary.set(m, (dayPrimary.get(m) ?? 0) + item.sets)
      }
      const pp = pushPull(ex)
      if (pp === 1) pushSets += item.sets
      else if (pp === -1) pullSets += item.sets
      if (isCompoundExercise(ex)) {
        for (const m of primary) {
          if (seenIsoMuscles.has(m) && !reportedIsoFirst.has(m)) {
            reportedIsoFirst.add(m)
            findings.push({ kind: 'ISOLATION_FIRST', muscle: m, dayIndex: dayIdx, value: 1, limit: 0, dayOfWeek: day.dayOfWeek })
          }
        }
      } else {
        for (const m of primary) seenIsoMuscles.add(m)
      }
    }
    for (const [m, total] of dayPrimary) {
      if (total > DAY_MUSCLE_LIMIT) {
        findings.push({ kind: 'JUNK_VOLUME', muscle: m, dayIndex: dayIdx, value: total, limit: DAY_MUSCLE_LIMIT, dayOfWeek: day.dayOfWeek })
      }
    }
    const dow = day.dayOfWeek
    if (dow != null && dow >= 1 && dow <= 7 && items.length > 0) {
      dayPrimarySetsByDow.set(dow, dayPrimary)
      indexByDow.set(dow, dayIdx)
    }
  })
  for (const m of MUSCLES) {
    const vol = weeklyVolume.get(m) ?? 0
    const { mev, mrv } = landmarkFor(m)
    if (vol > mrv) findings.push({ kind: 'ABOVE_MRV', muscle: m, value: vol, limit: mrv })
    else if (BELOW_MEV_MUSCLES.has(m) && vol < mev) findings.push({ kind: 'BELOW_MEV', muscle: m, value: vol, limit: mev })
  }
  if (pushPullBroken(pushSets, pullSets)) {
    findings.push({ kind: 'PUSH_PULL', value: pushSets, limit: pullSets })
  }
  if (scheduleMode === 'WEEKDAY') {
    for (let dow = 1; dow <= 7; dow++) {
      const first = dayPrimarySetsByDow.get(dow)
      const nextDow = dow === 7 ? 1 : dow + 1
      const second = dayPrimarySetsByDow.get(nextDow)
      if (!first || !second) continue
      for (const m of MUSCLES) {
        const s1 = first.get(m) ?? 0
        const s2 = second.get(m) ?? 0
        if (s1 >= OVERLAP_SETS && s2 >= OVERLAP_SETS) findings.push({
          kind: 'RECOVERY_OVERLAP', muscle: m, dayIndex: indexByDow.get(nextDow),
          value: Math.min(s1, s2), limit: OVERLAP_SETS, dayOfWeek: nextDow,
        })
      }
    }
  }
  if (shortPlanNote && isShortPlan(days) && findings.some((f) => f.kind === 'BELOW_MEV')) {
    findings = findings.filter((f) => f.kind !== 'BELOW_MEV')
    findings.push({
      kind: 'SHORT_PLAN',
      value: workingDays.filter((d) => d.length > 0).length,
      limit: SHORT_PLAN_MAX_DAYS,
    })
  }
  return findings.sort((a, b) => AUDIT_KINDS.indexOf(a.kind) - AUDIT_KINDS.indexOf(b.kind)
    || (a.dayIndex ?? Number.MAX_SAFE_INTEGER) - (b.dayIndex ?? Number.MAX_SAFE_INTEGER)
    || (a.muscle == null ? MUSCLES.length : MUSCLES.indexOf(a.muscle)) - (b.muscle == null ? MUSCLES.length : MUSCLES.indexOf(b.muscle)))
}

export const RoutineAuditor = {
  audit,
  fromDraft,
  fromActivePlan,
  credits,
  pushPull,
  pushPullBroken,
  landmarkFor,
  estimateMinutes,
  isShortPlan,
  BELOW_MEV_MUSCLES,
  DAY_MUSCLE_LIMIT,
  OVERLAP_SETS,
  PUSH_PULL_MIN,
  SHORT_PLAN_MAX_DAYS,
  SHORT_PLAN_MAX_MINUTES,
}
