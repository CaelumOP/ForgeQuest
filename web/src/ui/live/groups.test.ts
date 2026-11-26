import { describe, expect, it } from 'vitest'
import type { BaselineTestRow, PlanItemRow, SetRow } from '../../data/db'
import { EXERCISE_BY_ID } from '../../data/seed/exercise-catalog'
import { setLang } from '../../i18n'
import {
  carryForwardUpdates, clampField, currentGroupIndex, effectiveRest, effectiveRestSeconds,
  fillBlankSetsFromWeightedBaseline, formatTargetLabel, groupSetsByExercise, nextUntickedSet,
} from './groups'

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

describe('fillBlankSetsFromWeightedBaseline', () => {
  it('updates only undone 0/null kg sets when no history or explicit plan weight exists', () => {
    const dbBench = EXERCISE_BY_ID.get('db_bench_press')!
    const pushUp = EXERCISE_BY_ID.get('push_up')!
    const baseline: BaselineTestRow = {
      id: 1, exerciseId: dbBench.id, kind: 'WEIGHTED', reps: 8, seconds: null, weightKg: 20, createdAt: 100,
    }
    const itemBlank: PlanItemRow = {
      dayId: 1, position: 0, exerciseId: dbBench.id, block: 'Main', circuit: false,
      sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: 0, restSeconds: 90, note: '',
    }
    const doneBlank = s(0, 0, { exerciseId: dbBench.id, weightKg: 0, done: true })
    const undoneZero = s(0, 1, { exerciseId: dbBench.id, weightKg: 0, done: false })
    const undoneNull = s(0, 2, { exerciseId: dbBench.id, weightKg: null, done: false })
    const undoneLoaded = s(0, 3, { exerciseId: dbBench.id, weightKg: 17.5, done: false })

    // 20 * (1 + 8/30) / (1 + 11/30) * 0.9 = 16.68... -> floor to 2.5 = 15 kg
    expect(
      fillBlankSetsFromWeightedBaseline(
        dbBench, itemBlank, [doneBlank, undoneZero, undoneNull, undoneLoaded], [], 'KG', baseline,
      ),
    ).toEqual([
      { ...undoneZero, weightKg: 15 },
      { ...undoneNull, weightKg: 15 },
    ])

    // Precedence: existing working history blocks baseline fill
    expect(
      fillBlankSetsFromWeightedBaseline(
        dbBench, itemBlank, [undoneZero], [s(0, 0, { exerciseId: dbBench.id, weightKg: 20, done: true })], 'KG', baseline,
      ),
    ).toEqual([])

    // Precedence: explicit positive plan weight blocks baseline fill
    expect(
      fillBlankSetsFromWeightedBaseline(
        dbBench, { ...itemBlank, weightKg: 12.5 }, [undoneZero], [], 'KG', baseline,
      ),
    ).toEqual([])

    // Non-WEIGHTED or TooLight baseline never fills
    expect(
      fillBlankSetsFromWeightedBaseline(
        pushUp, null, [s(0, 0, { exerciseId: pushUp.id, weightKg: 0, done: false })], [], 'KG',
        { id: 2, exerciseId: pushUp.id, kind: 'REPS', reps: 12, seconds: null, weightKg: null, createdAt: 100 },
      ),
    ).toEqual([])
    expect(
      fillBlankSetsFromWeightedBaseline(
        dbBench, itemBlank, [undoneZero], [], 'KG', { ...baseline, reps: 15 },
      ),
    ).toEqual([])
  })
})

describe('formatTargetLabel', () => {
  it('localizes rep ranges, single reps, and seconds in Arabic', () => {
    setLang('ar')
    try {
      expect(formatTargetLabel('8-12 reps')).toBe('8–12 عدّة')
      expect(formatTargetLabel('30 s')).toBe('30 ث')
    } finally {
      setLang('en')
    }
    expect(formatTargetLabel('8-12 reps')).toBe('8–12 reps')
  })
})
