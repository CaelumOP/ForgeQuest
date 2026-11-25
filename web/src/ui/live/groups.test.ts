import { describe, expect, it } from 'vitest'
import type { SetRow } from '../../data/db'
import { carryForwardUpdates, clampField, currentGroupIndex, effectiveRest, effectiveRestSeconds, groupSetsByExercise, nextUntickedSet } from './groups'

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
    expect(clampField('weightKg', 22.54)).toBe(22.54)
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

  it('excludes warm-up, drop-set, and rest-pause sets from carry-forward updates', () => {
    const warmup = s(0, 0, { reps: 12, weightKg: 40, setType: 'WARMUP' })
    const working = s(0, 1, { reps: 8, weightKg: 60, setType: 'WORKING' })
    const dropset = s(0, 2, { reps: 10, weightKg: 45, setType: 'DROPSET' })
    const workingNext = s(0, 3, { reps: 8, weightKg: 60, setType: 'WORKING' })

    // Editing a warm-up never overwrites later working sets
    expect(
      carryForwardUpdates(
        [warmup, working],
        warmup,
        new Set([warmup.id!]),
        new Set([warmup.id!]),
      ),
    ).toEqual([])

    // Editing a working set skips dropsets and updates only later unticked working/failure sets
    expect(
      carryForwardUpdates(
        [working, dropset, workingNext],
        { ...working, weightKg: 65 },
        new Set(),
        new Set([working.id!]),
      ),
    ).toEqual([{ ...workingNext, weightKg: 65 }])
  })
})

describe('supersetGroup & effectiveRestSeconds', () => {
  it('pairs superset items into circuit rounds and dissolves singletons back to straight', () => {
    const sets = [s(0, 0), s(0, 1), s(1, 0), s(1, 1), s(2, 0)]
    const groups = groupSetsByExercise(sets, (pos) => (pos === 0 || pos === 1 ? 1 : pos === 2 ? 2 : null))
    expect(groups.map((g) => g.kind)).toEqual(['circuit', 'straight'])
    expect(groups[0].kind === 'circuit' ? groups[0].supersetGroup : null).toBe(1)
    expect(groups[0].kind === 'circuit' ? groups[0].rounds.map((r) => r.map((x) => x.itemPosition)) : []).toEqual([
      [0, 1],
      [0, 1],
    ])
  })

  it('computes 0s for warm-up, 15s for rest-pause, 0s for non-last member in superset/circuit, and full rest after last', () => {
    const a1 = s(0, 0, { restSeconds: 90 })
    const a2 = s(1, 0, { restSeconds: 90 })
    const warmup = s(2, 0, { restSeconds: 90, setType: 'WARMUP' })
    const restPause = s(2, 1, { restSeconds: 90, setType: 'RESTPAUSE' })
    const c1 = s(3, 0, { circuit: true, block: 'Circuit A', restSeconds: 30 })
    const c2 = s(4, 0, { circuit: true, block: 'Circuit A', restSeconds: 30 })
    const c3 = s(5, 0, { circuit: true, block: 'Circuit A', restSeconds: 30 })
    const groups = groupSetsByExercise([a1, a2, warmup, restPause, c1, c2, c3], (pos) => (pos === 0 || pos === 1 ? 1 : null))

    expect(effectiveRestSeconds(warmup, groups)).toBe(0)
    expect(effectiveRestSeconds(restPause, groups)).toBe(15)
    expect(effectiveRestSeconds(a1, groups)).toBe(0)
    expect(effectiveRestSeconds(a2, groups)).toBe(90)
    expect(effectiveRest(a1, groups)).toEqual({ seconds: 0, isShortTransition: true })
    expect(effectiveRest(a2, groups)).toEqual({ seconds: 90, isShortTransition: false })
    expect(effectiveRest(restPause, groups)).toEqual({ seconds: 15, isShortTransition: false })
    expect(effectiveRest(warmup, groups)).toEqual({ seconds: 0, isShortTransition: false })
    expect(effectiveRest(c1, groups)).toEqual({ seconds: 0, isShortTransition: true })
    expect(effectiveRest(c2, groups)).toEqual({ seconds: 0, isShortTransition: true })
    expect(effectiveRest(c3, groups)).toEqual({ seconds: 30, isShortTransition: false })
  })
})
