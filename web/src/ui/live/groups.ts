import type { SetRow } from '../../data/db'

/** One visual group: a circuit block (shown round by round) or a single straight-sets exercise (Android LiveWorkoutScreen.group). */
export type Group =
  | { kind: 'straight'; key: string; position: number; block: string; sets: SetRow[] }
  | { kind: 'circuit'; key: string; block: string; positions: number[]; rounds: SetRow[][] }

export const groupSets = (g: Group): SetRow[] => (g.kind === 'straight' ? g.sets : g.rounds.flat())

export function groupSetsByExercise(sets: readonly SetRow[]): Group[] {
  const byPosition = new Map<number, SetRow[]>()
  for (const s of sets) byPosition.set(s.itemPosition, [...(byPosition.get(s.itemPosition) ?? []), s])
  const out: Group[] = []
  let pending: number[] | null = null
  let block = ''
  const flush = () => {
    if (!pending) return
    const positions = pending
    const max = Math.max(...positions.map((p) => byPosition.get(p)?.length ?? 0))
    const sorted = (p: number) => [...(byPosition.get(p) ?? [])].sort((a, b) => a.setIndex - b.setIndex)
    const rounds = Array.from({ length: max }, (_, r) => positions.flatMap((p) => sorted(p)[r] ?? []))
    out.push({ kind: 'circuit', key: `c${positions[0]}`, block, positions, rounds })
    pending = null
  }
  for (const position of [...byPosition.keys()].sort((a, b) => a - b)) {
    const list = byPosition.get(position)!
    const first = list[0]
    if (first.circuit) {
      if (!pending || block !== first.block) { flush(); pending = []; block = first.block }
      pending.push(position)
    } else {
      flush()
      out.push({ kind: 'straight', key: `s${position}`, position, block: first.block, sets: [...list].sort((a, b) => a.setIndex - b.setIndex) })
    }
  }
  flush()
  return out
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
 * Computes which later unticked sets should inherit edited fields from `ticked`: only fields the user actually
 * edited on `ticked` carry forward, and never onto sets whose own field the user already edited.
 */
export function carryForwardUpdates(
  sets: readonly SetRow[],
  ticked: SetRow,
  editedReps: ReadonlySet<number>,
  editedWeight: ReadonlySet<number>,
): SetRow[] {
  if (ticked.id == null) return []
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
      next.id != null
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
  return Math.min(999, Math.max(0, Math.round(value * 10) / 10))
}
