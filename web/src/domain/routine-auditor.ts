// Pure, non-destructive port of Android RoutineAuditor.kt.
import type { ExerciseEntity, PlanDraft } from './entities'
import { bucketOf } from './baseline'
import { profileFor } from './exercise-swap-engine'
import { MUSCLES, type Muscle } from './muscle'
import { isCompound } from './plan-builder'
import { isExtraBlock } from './pr-picker'
import type { ScheduleMode } from './rotation'

export const AUDIT_KINDS = [
  'ABOVE_MRV', 'RECOVERY_OVERLAP', 'JUNK_VOLUME', 'PUSH_PULL', 'BELOW_MEV', 'ISOLATION_FIRST',
] as const
export type AuditKind = (typeof AUDIT_KINDS)[number]
export type AuditItem = { exerciseId: string; sets: number; block?: string; position?: number }
export type AuditDay = { dayOfWeek?: number | null; items: readonly AuditItem[] }
export type Finding = {
  kind: AuditKind; muscle?: Muscle; dayIndex?: number; value: number; limit: number; dayOfWeek?: number | null
}
const BELOW_MEV_MUSCLES = new Set<Muscle>(['CHEST', 'UPPER_BACK', 'SHOULDERS', 'QUADS', 'HAMSTRINGS', 'GLUTES'])
const LANDMARKS: Partial<Record<Muscle, readonly [number, number]>> = {
  CHEST: [8, 22], UPPER_BACK: [10, 25], SHOULDERS: [8, 26], BICEPS: [8, 20], TRICEPS: [6, 18],
  QUADS: [8, 20], HAMSTRINGS: [6, 20], GLUTES: [4, 16], CALVES: [8, 20], ABS: [0, 25], TRAPS: [0, 26],
}

export function fromDraft(draft: PlanDraft): AuditDay[] {
  return draft.days.filter((d) => !d.isRest).map((d) => ({
    dayOfWeek: d.dayOfWeek,
    items: d.items.map((item, position) => ({ ...item, position })),
  }))
}

/** Structural adapter keeps the pure domain independent of the storage layer. */
export function fromActivePlan(plan: {
  days: readonly { day: { isRest: boolean; dayOfWeek: number }; items: readonly AuditItem[] }[]
}): AuditDay[] {
  return plan.days.filter((d) => !d.day.isRest).sort((a, b) => a.day.dayOfWeek - b.day.dayOfWeek)
    .map((d) => ({ dayOfWeek: d.day.dayOfWeek, items: [...d.items].sort((a, b) => (a.position ?? 0) - (b.position ?? 0)) }))
}

export function audit(
  days: readonly AuditDay[], exercises: ReadonlyMap<string, ExerciseEntity>, scheduleMode: ScheduleMode = 'WEEKDAY',
): Finding[] {
  const workingDays = days.map((day) => day.items
    .filter((it) => !isExtraBlock(it.block) && it.sets > 0 && exercises.has(it.exerciseId))
    .sort((a, b) => (a.position ?? 0) - (b.position ?? 0)))
  if (workingDays.every((items) => items.length === 0)) return []
  const findings: Finding[] = []
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
      const secondary = new Set(ex.secondary.filter((m) => !primary.has(m)))
      for (const m of primary) {
        weeklyVolume.set(m, (weeklyVolume.get(m) ?? 0) + item.sets)
        dayPrimary.set(m, (dayPrimary.get(m) ?? 0) + item.sets)
      }
      for (const m of secondary) weeklyVolume.set(m, (weeklyVolume.get(m) ?? 0) + item.sets * 0.5)
      const pattern = profileFor(ex).pattern
      const bucket = bucketOf(pattern)
      if (bucket === 'PUSH') pushSets += item.sets
      else if (bucket === 'PULL') pullSets += item.sets
      if (isCompound(pattern)) {
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
      if (total > 10) findings.push({ kind: 'JUNK_VOLUME', muscle: m, dayIndex: dayIdx, value: total, limit: 10, dayOfWeek: day.dayOfWeek })
    }
    const dow = day.dayOfWeek
    if (dow != null && dow >= 1 && dow <= 7 && items.length > 0) {
      dayPrimarySetsByDow.set(dow, dayPrimary)
      indexByDow.set(dow, dayIdx)
    }
  })
  for (const m of MUSCLES) {
    const vol = weeklyVolume.get(m) ?? 0
    const [mev, mrv] = LANDMARKS[m] ?? [0, 20]
    if (vol > mrv) findings.push({ kind: 'ABOVE_MRV', muscle: m, value: vol, limit: mrv })
    else if (BELOW_MEV_MUSCLES.has(m) && vol < mev) findings.push({ kind: 'BELOW_MEV', muscle: m, value: vol, limit: mev })
  }
  if (pushSets >= 6 && pushSets * 10 > pullSets * 12) {
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
        if (s1 >= 6 && s2 >= 6) findings.push({
          kind: 'RECOVERY_OVERLAP', muscle: m, dayIndex: indexByDow.get(nextDow),
          value: Math.min(s1, s2), limit: 6, dayOfWeek: nextDow,
        })
      }
    }
  }
  return findings.sort((a, b) => AUDIT_KINDS.indexOf(a.kind) - AUDIT_KINDS.indexOf(b.kind)
    || (a.dayIndex ?? Number.MAX_SAFE_INTEGER) - (b.dayIndex ?? Number.MAX_SAFE_INTEGER)
    || (a.muscle == null ? MUSCLES.length : MUSCLES.indexOf(a.muscle)) - (b.muscle == null ? MUSCLES.length : MUSCLES.indexOf(b.muscle)))
}

export const RoutineAuditor = { audit, fromDraft, fromActivePlan }
