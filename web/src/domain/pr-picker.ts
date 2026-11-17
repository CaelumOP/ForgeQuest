import { STRETCH_IDS } from '../data/seed/exercise-catalog'
import type { ExerciseKind } from './muscle'
import { recordScore } from './progress'
import { countsForPR } from './set-types'

export const isExtraBlock = (block: string | undefined | null): boolean =>
  block != null && (block.toLowerCase() === 'warm-up' || block.toLowerCase() === 'cool-down')

type PrSet = {
  id?: number
  exerciseId: string
  block?: string
  setType?: string
  done: boolean
  reps: number | null
  seconds: number | null
  weightKg: number | null
}

/**
 * Which done sets are personal records: one per exercise, the highest score strictly above the previous best
 * (the earliest set wins a tie), and none on an exercise's first ever session. Warm-up, cool-down, drop sets,
 * rest-pause sets, and stretches never count. Mirrors Android PrPicker.kt.
 */
export function pickPrs(
  sets: readonly PrSet[],
  kindOf: (exerciseId: string) => ExerciseKind,
  previousBest: ReadonlyMap<string, number>,
): Set<number> {
  const out = new Set<number>()
  const byEx = new Map<string, PrSet[]>()
  for (const s of sets) {
    if (!s.done || s.id == null || !countsForPR(s) || STRETCH_IDS.has(s.exerciseId)) continue
    byEx.set(s.exerciseId, [...(byEx.get(s.exerciseId) ?? []), s])
  }
  for (const [id, group] of byEx) {
    const prev = previousBest.get(id)
    if (prev == null || !Number.isFinite(prev)) continue
    const kind = kindOf(id)
    let best: PrSet | null = null
    let bestScore = -Infinity
    for (const s of group) {
      const sc = recordScore(kind, s.reps, s.seconds, s.weightKg, id)
      if (sc > bestScore) { bestScore = sc; best = s }
    }
    if (best?.id != null && bestScore > prev) out.add(best.id)
  }
  return out
}
