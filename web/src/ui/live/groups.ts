import type { SetRow } from '../../data/db'
import { countsForProgression } from '../../domain/set-types'

/** One visual group: a circuit/superset block (shown round by round) or a single straight-sets exercise (Android LiveWorkoutScreen.group). */
export type Group =
  | { kind: 'straight'; key: string; position: number; block: string; sets: SetRow[] }
  | { kind: 'circuit'; key: string; block: string; positions: number[]; rounds: SetRow[][]; supersetGroup?: number | null }

export const groupSets = (g: Group): SetRow[] => (g.kind === 'straight' ? g.sets : g.rounds.flat())

export function groupSetsByExercise(
  sets: readonly SetRow[],
  supersetGroupOf: (itemPosition: number) => number | null = () => null,
): Group[] {
  const byPosition = new Map<number, SetRow[]>()
  for (const s of sets) byPosition.set(s.itemPosition, [...(byPosition.get(s.itemPosition) ?? []), s])
  const out: Group[] = []
  let pending: number[] | null = null
  let block = ''
  let pendingSuperset: number | null = null
  const flush = () => {
    if (!pending) return
    const positions = pending
    if (pendingSuperset != null && positions.length < 2) {
      const p = positions[0]
      const list = [...(byPosition.get(p) ?? [])].sort((a, b) => a.setIndex - b.setIndex)
      out.push({ kind: 'straight', key: `s${p}`, position: p, block: list[0]?.block ?? '', sets: list })
    } else {
      const max = Math.max(...positions.map((p) => byPosition.get(p)?.length ?? 0))
      const sorted = (p: number) => [...(byPosition.get(p) ?? [])].sort((a, b) => a.setIndex - b.setIndex)
      const rounds = Array.from({ length: max }, (_, r) => positions.flatMap((p) => sorted(p)[r] ?? []))
      out.push({
        kind: 'circuit',
        key: `c${positions[0]}`,
        block,
        positions,
        rounds,
        ...(pendingSuperset != null ? { supersetGroup: pendingSuperset } : {}),
      })
    }
    pending = null
    pendingSuperset = null
  }
  for (const position of [...byPosition.keys()].sort((a, b) => a - b)) {
    const list = byPosition.get(position)!
    const first = list[0]
    const sg = supersetGroupOf(position)
    if (sg != null) {
      if (!pending || pendingSuperset !== sg) {
        flush()
        pending = []
        block = first.block
        pendingSuperset = sg
      }
      pending.push(position)
    } else if (first.circuit) {
      if (!pending || pendingSuperset != null || block !== first.block) {
        flush()
        pending = []
        block = first.block
        pendingSuperset = null
      }
      pending.push(position)
    } else {
      flush()
      out.push({ kind: 'straight', key: `s${position}`, position, block: first.block, sets: [...list].sort((a, b) => a.setIndex - b.setIndex) })
    }
  }
  flush()
  return out
}

/** Rest seconds after ticking `set`: warm-ups skip auto-rest, rest-pause uses 15 s, superset A1 gets 10 s transition. */
export function effectiveRestSeconds(set: SetRow, groups: readonly Group[]): number {
  const type = set.setType ?? 'WORKING'
  if (type === 'WARMUP') return 0
  if (type === 'RESTPAUSE') return 15
  if (set.restSeconds <= 0) return 0
  for (const g of groups) {
    if (g.kind !== 'circuit' || g.supersetGroup == null) continue
    const round = g.rounds.find((r) => r.some((s) => s.id === set.id))
    if (!round) continue
    const isLast = round[round.length - 1]?.id === set.id
    const otherUndone = round.some((s) => s.id !== set.id && !s.done)
    if (!isLast && otherUndone) return Math.min(set.restSeconds, 10)
  }
  return set.restSeconds
}

/** Index of the group holding the first unticked set in display order, or -1. */
export function currentGroupIndex(groups: readonly Group[]): number {
  const next = groups.flatMap(groupSets).find((s) => !s.done)
  return next ? groups.findIndex((g) => groupSets(g).some((s) => s.id === next.id)) : -1
}

/**
 * The set to do next: the first undone set of the group ticked most recently; if that group is finished, the first undone set in a
 * later group (so it never jumps back to a skipped warm-up); otherwise the first undone set overall (Android pickNextSet).
 */
export function nextUntickedSet(groups: readonly Group[]): SetRow | undefined {
  let last = -1
  let latest = -Infinity
  for (let i = 0; i < groups.length; i++) {
    for (const s of groupSets(groups[i])) {
      if (s.done && s.doneAt != null && s.doneAt >= latest) {
        latest = s.doneAt
        last = i
      }
    }
  }
  if (last >= 0) {
    const same = groupSets(groups[last]).find((s) => !s.done)
    if (same) return same
    const later = groups.slice(last + 1).flatMap(groupSets).find((s) => !s.done)
    if (later) return later
  }
  return groups.flatMap(groupSets).find((s) => !s.done)
}

/**
 * Computes which later unticked working sets should inherit edited fields from `ticked`: only fields the user actually
 * edited on `ticked` carry forward, and never onto sets whose own field the user already edited.
 * Warm-ups, dropsets, and rest-pause sets never overwrite working sets.
 */
export function carryForwardUpdates(
  sets: readonly SetRow[],
  ticked: SetRow,
  editedReps: ReadonlySet<number>,
  editedWeight: ReadonlySet<number>,
): SetRow[] {
  if (ticked.id == null || !countsForProgression(ticked)) return []
  const copyReps = editedReps.has(ticked.id)
  const copyWeight = editedWeight.has(ticked.id) && ticked.weightKg != null && ticked.weightKg > 0
  if (!copyReps && !copyWeight) return []
  const out: SetRow[] = []
  for (const next of sets) {
    if (
      next.itemPosition === ticked.itemPosition &&
      next.exerciseId === ticked.exerciseId &&
      next.setIndex > ticked.setIndex &&
      !next.done &&
      next.id != null &&
      countsForProgression(next)
    ) {
      const nextReps = copyReps && !editedReps.has(next.id) && ticked.reps != null && next.reps != null
        ? ticked.reps
        : next.reps
      const nextWeight = copyWeight && !editedWeight.has(next.id)
        ? ticked.weightKg
        : next.weightKg
      if (nextReps !== next.reps || nextWeight !== next.weightKg) {
        out.push({ ...next, reps: nextReps, weightKg: nextWeight })
      }
    }
  }
  return out
}

/** Typed or nudged value, clamped like Android setField. Weight is in kg. */
export function clampField(field: 'reps' | 'seconds' | 'weightKg', value: number): number {
  if (field === 'reps') return Math.min(999, Math.max(0, Math.round(value)))
  if (field === 'seconds') return Math.min(3600, Math.max(0, Math.round(value)))
  return Math.min(999, Math.max(0, Math.round(value * 100) / 100))
}
