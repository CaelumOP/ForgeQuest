import { describe, expect, it } from 'vitest'
import {
  computeAdjustment,
  extendedRest,
  numbersToRestore,
  openLaterSets,
  resolveRepsMin,
  type IntraSessionSet,
} from './intra-session'
import { fromKg, loadStep, toKg } from './units'

function makeSet(
  id: number,
  overrides: Partial<IntraSessionSet> = {},
): IntraSessionSet {
  return {
    id,
    exerciseId: 'bench',
    itemPosition: 0,
    setIndex: id - 1,
    reps: 8,
    seconds: null,
    weightKg: 100,
    done: false,
    effort: '',
    setType: 'WORKING',
    block: 'Main',
    ...overrides,
  }
}

describe('IntraSession', () => {
  it('set1FailureCutsSet2And3ByFivePercentSnappedToPlateStep', () => {
    const s1 = makeSet(1, { setIndex: 0, reps: 8, weightKg: 100, done: true, effort: 'FAILURE' })
    const s2 = makeSet(2, { setIndex: 1, reps: 8, weightKg: 100 })
    const s3 = makeSet(3, { setIndex: 2, reps: 8, weightKg: 100 })

    const result = computeAdjustment(s1, [s2, s3], 'WEIGHTED', 8, 'KG')
    const changed = result?.changed ?? []

    expect(changed).toHaveLength(2)
    expect(changed[0].weightKg!).toBeCloseTo(95, 2)
    expect(changed[1].weightKg!).toBeCloseTo(95, 2)
    expect(changed[0].reps).toBe(8)
    expect(changed[1].reps).toBe(8)
    expect(result?.percent).toBe(5)
  })

  it('missByThreeCutsTenPercent', () => {
    const s1 = makeSet(1, { setIndex: 0, reps: 5, weightKg: 100, done: true, effort: '' })
    const s2 = makeSet(2, { setIndex: 1, reps: 8, weightKg: 100 })

    const result = computeAdjustment(s1, [s2], 'WEIGHTED', 8, 'KG')
    const changed = result?.changed ?? []

    expect(changed).toHaveLength(1)
    expect(changed[0].weightKg!).toBeCloseTo(90, 2)
    expect(changed[0].reps).toBe(8)
    expect(result?.percent).toBe(10)
  })

  it('twoRepsUnderMinimumTriggersWithoutTag', () => {
    const s1 = makeSet(1, { setIndex: 0, reps: 6, weightKg: 100, done: true, effort: '' })
    const s2 = makeSet(2, { setIndex: 1, reps: 8, weightKg: 100 })

    const result = computeAdjustment(s1, [s2], 'WEIGHTED', 8, 'KG')
    const changed = result?.changed ?? []

    expect(changed).toHaveLength(1)
    expect(changed[0].weightKg!).toBeCloseTo(95, 2)
    expect(changed[0].reps).toBe(8)
    expect(result?.percent).toBe(5)
  })

  it('oneRepUnderMinimumDoesNothing', () => {
    const s1 = makeSet(1, { setIndex: 0, reps: 7, weightKg: 100, done: true, effort: '' })
    const s2 = makeSet(2, { setIndex: 1, reps: 8, weightKg: 100 })

    const result = computeAdjustment(s1, [s2], 'WEIGHTED', 8, 'KG')

    expect(result).toBeNull()
  })

  it('blankEffortOnTargetDoesNothing', () => {
    const s1 = makeSet(1, { setIndex: 0, reps: 8, weightKg: 100, done: true, effort: '' })
    const s2 = makeSet(2, { setIndex: 1, reps: 8, weightKg: 100 })

    const result = computeAdjustment(s1, [s2], 'WEIGHTED', 8, 'KG')

    expect(result).toBeNull()
  })

  it('hardTagDoesNothing', () => {
    const s1 = makeSet(1, { setIndex: 0, reps: 8, weightKg: 100, done: true, effort: 'HARD' })
    const s2 = makeSet(2, { setIndex: 1, reps: 8, weightKg: 100 })

    const result = computeAdjustment(s1, [s2], 'WEIGHTED', 8, 'KG')

    expect(result).toBeNull()
  })

  it('lastSetFailureHasNothingToAdjust', () => {
    const s3 = makeSet(3, { setIndex: 2, reps: 8, weightKg: 100, done: true, effort: 'FAILURE' })

    const result = computeAdjustment(s3, [], 'WEIGHTED', 8, 'KG')

    expect(result).toBeNull()
  })

  it('bodyweightFailureSetsRemainingRepsToRepsDone', () => {
    const s1 = makeSet(1, { setIndex: 0, reps: 6, weightKg: null, done: true, effort: 'FAILURE' })
    const s2 = makeSet(2, { setIndex: 1, reps: 10, weightKg: null })
    const s3 = makeSet(3, { setIndex: 2, reps: 10, weightKg: 10 })

    const result = computeAdjustment(s1, [s2, s3], 'REPS', 8, 'KG')
    const changed = result?.changed ?? []

    expect(changed).toHaveLength(2)
    expect(changed[0].reps).toBe(6)
    expect(changed[1].reps).toBe(6)
    expect(changed[1].weightKg!).toBeCloseTo(10, 2)
    expect(result?.reps).toBe(6)
  })

  it('holdFailureSetsRemainingSecondsToHeld', () => {
    const s1 = makeSet(1, { setIndex: 0, reps: null, seconds: 24, weightKg: null, done: true, effort: 'FAILURE' })
    const s2 = makeSet(2, { setIndex: 1, reps: null, seconds: 30, weightKg: null })

    const result = computeAdjustment(s1, [s2], 'HOLD', null, 'KG')
    const changed = result?.changed ?? []

    expect(changed).toHaveLength(1)
    expect(changed[0].seconds).toBe(24)
    expect(result?.seconds).toBe(24)
  })

  it('poundUserCutsInFivePoundSteps', () => {
    const wKg = toKg('LB', 100)
    const s1 = makeSet(1, { setIndex: 0, reps: 8, weightKg: wKg, done: true, effort: 'FAILURE' })
    const s2 = makeSet(2, { setIndex: 1, reps: 8, weightKg: wKg })

    const result = computeAdjustment(s1, [s2], 'WEIGHTED', 8, 'LB')
    const changed = result?.changed ?? []

    expect(changed).toHaveLength(1)
    expect(fromKg('LB', changed[0].weightKg!)).toBeCloseTo(95, 1)
    expect(result?.percent).toBe(5)
  })

  it('lightestLoadKeepsWeight', () => {
    const stepKg = loadStep('KG')
    const s1 = makeSet(1, { setIndex: 0, reps: 6, weightKg: stepKg, done: true, effort: 'FAILURE' })
    const s2 = makeSet(2, { setIndex: 1, reps: 10, weightKg: stepKg })

    const result = computeAdjustment(s1, [s2], 'WEIGHTED', 8, 'KG')
    const changed = result?.changed ?? []

    expect(changed).toHaveLength(1)
    expect(changed[0].weightKg!).toBeCloseTo(stepKg, 2)
    // The load cannot drop, so the next set aims for the 6 reps actually done, not the 8 that just failed.
    expect(changed[0].reps).toBe(6)
    expect(result?.reps).toBe(6)
    expect(result?.percent).toBeUndefined()
  })

  it('bodyweightFailureNeverRaisesTheNextTarget', () => {
    const s1 = makeSet(1, { setIndex: 0, reps: 12, weightKg: null, done: true, effort: 'FAILURE' })
    const s2 = makeSet(2, { setIndex: 1, reps: 10, weightKg: null })

    expect(computeAdjustment(s1, [s2], 'REPS', 8, 'KG')).toBeNull()
  })

  it('holdFailurePastTheTargetNeverRaisesIt', () => {
    const s1 = makeSet(1, { setIndex: 0, reps: null, seconds: 40, weightKg: null, done: true, effort: 'FAILURE' })
    const s2 = makeSet(2, { setIndex: 1, reps: null, seconds: 30, weightKg: null })

    expect(computeAdjustment(s1, [s2], 'HOLD', null, 'KG')).toBeNull()
  })

  it('warmupSourceIsIgnored', () => {
    const s1 = makeSet(1, { setIndex: 0, reps: 4, weightKg: 60, done: true, effort: 'FAILURE', setType: 'WARMUP' })
    const s2 = makeSet(2, { setIndex: 1, reps: 8, weightKg: 100 })

    expect(computeAdjustment(s1, [s2], 'WEIGHTED', 8, 'KG')).toBeNull()
  })

  it('setTypeFailureWithBlankEffortTriggers', () => {
    const s1 = makeSet(1, { setIndex: 0, reps: 8, weightKg: 100, done: true, effort: '', setType: 'FAILURE' })
    const s2 = makeSet(2, { setIndex: 1, reps: 8, weightKg: 100 })

    const result = computeAdjustment(s1, [s2], 'WEIGHTED', 8, 'KG')
    const changed = result?.changed ?? []

    expect(changed).toHaveLength(1)
    expect(changed[0].weightKg!).toBeCloseTo(95, 2)
  })

  it('extendedRestAddsFortyFiveCappedAt240', () => {
    expect(extendedRest(90)).toBe(135)
    expect(extendedRest(210)).toBe(240)
    expect(extendedRest(240)).toBe(240)
    expect(extendedRest(300)).toBe(300)
  })
})

describe('openLaterSets', () => {
  it('excludes hand-edited, done, non-progression, and other exercise sets and sorts by setIndex', () => {
    const s1 = makeSet(1, { setIndex: 0, done: true })
    const s2 = makeSet(2, { setIndex: 2 })
    const s3 = makeSet(3, { setIndex: 1 })
    const edited = makeSet(4, { setIndex: 3 })
    const ticked = makeSet(5, { setIndex: 4, done: true })
    const dropset = makeSet(6, { setIndex: 5, setType: 'DROPSET' })
    const otherPos = makeSet(7, { setIndex: 1, itemPosition: 1 })
    const otherEx = makeSet(8, { setIndex: 1, exerciseId: 'squat' })

    const result = openLaterSets([s1, s2, s3, edited, ticked, dropset, otherPos, otherEx], s1, (id) => id === 4)
    expect(result.map((s) => s.id)).toEqual([3, 2])
  })
})

describe('numbersToRestore', () => {
  it('skips rows ticked or edited since and only returns rows whose numbers differ', () => {
    const prev2 = makeSet(2, { setIndex: 1, reps: 8, weightKg: 100 })
    const prev3 = makeSet(3, { setIndex: 2, reps: 8, weightKg: 100 })
    const prev4 = makeSet(4, { setIndex: 3, reps: 8, weightKg: 100 })
    const prev5 = makeSet(5, { setIndex: 4, reps: 8, weightKg: 100 })

    const cur2 = makeSet(2, { setIndex: 1, reps: 8, weightKg: 95, effort: 'HARD' })
    const cur3 = makeSet(3, { setIndex: 2, reps: 8, weightKg: 95, done: true }) // ticked since
    const cur4 = makeSet(4, { setIndex: 3, reps: 8, weightKg: 95 }) // hand-edited since
    const cur5 = makeSet(5, { setIndex: 4, reps: 8, weightKg: 100 }) // already same

    const restored = numbersToRestore([prev2, prev3, prev4, prev5], [cur2, cur3, cur4, cur5], (id) => id === 4)
    expect(restored).toEqual([{ ...cur2, reps: 8, seconds: null, weightKg: 100 }])
  })
})

describe('resolveRepsMin', () => {
  it('returns plan repsMin when exerciseId matches, else 8 for WEIGHTED and null for HOLD or plan-less REPS', () => {
    expect(resolveRepsMin({ exerciseId: 'bench' }, 'WEIGHTED', { exerciseId: 'bench', repsMin: 5 })).toBe(5)
    expect(resolveRepsMin({ exerciseId: 'bench' }, 'WEIGHTED', { exerciseId: 'squat', repsMin: 5 })).toBe(8)
    expect(resolveRepsMin({ exerciseId: 'bench' }, 'WEIGHTED', null)).toBe(8)
    expect(resolveRepsMin({ exerciseId: 'plank' }, 'HOLD', { exerciseId: 'plank', repsMin: 5 })).toBeNull()
    expect(resolveRepsMin({ exerciseId: 'pushup' }, 'REPS', null)).toBeNull()
    expect(resolveRepsMin({ exerciseId: 'pushup' }, 'REPS', { exerciseId: 'pushup', repsMin: 12 })).toBe(12)
  })
})
