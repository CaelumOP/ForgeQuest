import { STRETCH_IDS } from '../../data/seed/exercise-catalog'
import { weekStart, todayEpochDay } from '../../domain/dates'
import type { ExerciseEntity } from '../../domain/entities'
import type { Muscle } from '../../domain/muscle'
import { countsForScore } from '../../domain/set-types'

type VolumeSession = { id?: number; epochDay: number; finishedAt?: number | null }
type VolumeSet = { sessionId: number; exerciseId: string; done: boolean; setType?: string; block?: string }

/**
 * Estimated sets this week per muscle using WP1a countsForScore eligibility.
 * Primary muscles receive 1.0 set; secondary muscles receive 0.5 set.
 */
export function weeklyMuscleVolume(
  sessions: readonly VolumeSession[],
  doneSets: readonly VolumeSet[],
  exercises: ReadonlyMap<string, ExerciseEntity>,
  today = todayEpochDay(),
): Map<Muscle, number> {
  const ws = weekStart(today)
  const weekIds = new Set<number>()
  for (const s of sessions) {
    if (s.finishedAt != null && s.id != null && s.epochDay >= ws && s.epochDay <= ws + 6) {
      weekIds.add(s.id)
    }
  }
  const totals = new Map<Muscle, number>()
  if (!weekIds.size) return totals

  for (const set of doneSets) {
    if (!set.done || !weekIds.has(set.sessionId)) continue
    if (STRETCH_IDS.has(set.exerciseId)) continue
    if (!countsForScore(set)) continue
    const ex = exercises.get(set.exerciseId)
    if (!ex) continue
    for (const m of ex.primary) totals.set(m, (totals.get(m) ?? 0) + 1)
    for (const m of ex.secondary) totals.set(m, (totals.get(m) ?? 0) + 0.5)
  }
  return totals
}

/** Epley estimated 1RM. Exact weight when reps === 1; 0 when weight <= 0 or reps <= 0. */
export function epley1Rm(weight: number, reps: number): number {
  if (!Number.isFinite(weight) || weight <= 0 || !Number.isFinite(reps) || reps <= 0) return 0
  if (reps === 1) return weight
  return weight * (1 + reps / 30)
}

/** Ratio of estimated 1RM (kg) to body weight (kg), or null when body weight is unavailable. */
export function strengthToBodyweight(est1RmKg: number, bodyWeightKg: number | null): number | null {
  if (est1RmKg <= 0 || bodyWeightKg == null || bodyWeightKg <= 0) return null
  return est1RmKg / bodyWeightKg
}

const PERCENTAGES = [95, 90, 85, 80, 75, 70] as const

export function epleyPercentages(est1Rm: number): { pct: number; value: number }[] {
  return PERCENTAGES.map((pct) => ({ pct, value: (est1Rm * pct) / 100 }))
}

export function formatSets(value: number): string {
  const rounded = Math.round(value * 2) / 2
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1)
}
