import { describe, expect, it } from 'vitest'
import type { SetRow } from '../../data/db'
import { carryForwardUpdates, clampField, currentGroupIndex, groupSetsByExercise, nextUntickedSet } from './groups'

let id = 0
const s = (itemPosition: number, setIndex: number, o: Partial<SetRow> = {}): SetRow => ({
  id: ++id, sessionId: 1, exerciseId: 'e' + itemPosition, itemPosition, block: 'MAIN', circuit: false, setIndex, targetLabel: '',
  reps: 10, seconds: null, weightKg: null, done: false, isPr: false, restSeconds: 60, note: '', effort: '', ...o,
})

describe('groupSetsByExercise', () => {
  it('keeps straight exercises apart and folds a circuit into rounds', () => {
    const sets = [s(0, 1), s(0, 0), s(1, 0, { circuit: true, block: 'CORE' }), s(1, 1, { circuit: true, block: 'CORE' }), s(2, 0, { circuit: true, block: 'CORE' }), s(2, 1, { circuit: true, block: 'CORE' })]
    const g = groupSetsByExercise(sets)
    expect(g.map((x) => x.kind)).toEqual(['straight', 'circuit'])
    expect(g[0].kind === 'straight' && g[0].sets.map((x) => x.setIndex)).toEqual([0, 1])
    expect(g[1].kind === 'circuit' && g[1].rounds.map((r) => r.map((x) => x.itemPosition))).toEqual([[1, 2], [1, 2]])
  })
  it('finds the group with the first unticked set', () => {
    const g = groupSetsByExercise([s(0, 0, { done: true }), s(1, 0)])
    expect(currentGroupIndex(g)).toBe(1)
    expect(currentGroupIndex(groupSetsByExercise([s(0, 0, { done: true })]))).toBe(-1)
  })
})

describe('clampField', () => {
  it('clamps like Android', () => {
    expect(clampField('reps', -3)).toBe(0)
    expect(clampField('reps', 1200)).toBe(999)
    expect(clampField('seconds', 4000)).toBe(3600)
    expect(clampField('weightKg', 22.54)).toBe(22.5)
  })
})

describe('nextUntickedSet', () => {
  it('stays on the current or later exercise instead of jumping back to a skipped warm-up', () => {
    const sets = [
      s(0, 0, { block: 'Warm-up', exerciseId: 'calf_raise', done: false }),
      s(1, 0, { block: 'Main', exerciseId: 'bench', done: true, doneAt: 1000 }),
      s(1, 1, { block: 'Main', exerciseId: 'bench', done: false }),
      s(2, 0, { block: 'Main', exerciseId: 'row', done: false }),
    ]
    const g = groupSetsByExercise(sets)
    expect(nextUntickedSet(g)?.exerciseId).toBe('bench')
    const afterBench = groupSetsByExercise([
      sets[0],
      sets[1],
      { ...sets[2], done: true, doneAt: 2000 },
      sets[3],
    ])
    expect(nextUntickedSet(afterBench)?.exerciseId).toBe('row')
  })
})

describe('carryForwardUpdates', () => {
  it('preserves coach rep targets when only weight was edited on set 1', () => {
    const s1 = s(0, 0, { reps: 9, weightKg: 5 })
    const s2 = s(0, 1, { reps: 10, weightKg: 2.5 })
    const s3 = s(0, 2, { reps: 10, weightKg: 2.5 })
    const editedReps = new Set<number>()
    const editedWeight = new Set<number>([s1.id!])
    const updates = carryForwardUpdates([s1, s2, s3], s1, editedReps, editedWeight)
    expect(updates).toEqual([
      { ...s2, reps: 10, weightKg: 5 },
      { ...s3, reps: 10, weightKg: 5 },
    ])
  })

  it('carries both reps and weight when both were edited, skipping manually edited later sets', () => {
    const s1 = s(0, 0, { reps: 9, weightKg: 5 })
    const s2 = s(0, 1, { reps: 10, weightKg: 2.5 })
    const s3 = s(0, 2, { reps: 12, weightKg: 2.5 })
    const editedReps = new Set<number>([s1.id!, s3.id!])
    const editedWeight = new Set<number>([s1.id!])
    const updates = carryForwardUpdates([s1, s2, s3], s1, editedReps, editedWeight)
    expect(updates).toEqual([
      { ...s2, reps: 9, weightKg: 5 },
      { ...s3, reps: 12, weightKg: 5 },
    ])
  })
})
