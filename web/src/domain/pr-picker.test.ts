import { describe, expect, it } from 'vitest'
import { isExtraBlock, pickPrs } from './pr-picker'

describe('pr-picker', () => {
  it('excludes warm-up, cool-down, and stretch sets from PRs', () => {
    const sets = [
      { id: 1, exerciseId: 'back_squat', block: 'Warm-up', done: true, reps: 10, seconds: null, weightKg: 100 },
      { id: 2, exerciseId: 'back_squat', block: 'Main', done: true, reps: 5, seconds: null, weightKg: 90 },
      { id: 3, exerciseId: 'quad_stretch', block: 'Cool-down', done: true, reps: null, seconds: 60, weightKg: null },
    ]
    const prev = new Map([['back_squat', 80], ['quad_stretch', 30]])
    const picked = pickPrs(sets, (id) => (id === 'quad_stretch' ? 'HOLD' : 'WEIGHTED'), prev)
    expect([...picked]).toEqual([2])
  })

  it('picks at most one PR per exercise and skips first ever session', () => {
    const sets = [
      { id: 1, exerciseId: 'push_up', block: 'Main', done: true, reps: 15, seconds: null, weightKg: null },
      { id: 2, exerciseId: 'push_up', block: 'Main', done: true, reps: 16, seconds: null, weightKg: null },
      { id: 3, exerciseId: 'pull_up', block: 'Main', done: true, reps: 10, seconds: null, weightKg: null },
    ]
    const prev = new Map([['push_up', 12]])
    const picked = pickPrs(sets, () => 'REPS', prev)
    expect([...picked]).toEqual([2])
  })

  it('never awards PRs to WARMUP, DROPSET, or RESTPAUSE sets while FAILURE sets still count', () => {
    const sets = [
      { id: 1, exerciseId: 'bench_press', block: 'Main', setType: 'WARMUP', done: true, reps: 5, seconds: null, weightKg: 120 },
      { id: 2, exerciseId: 'bench_press', block: 'Main', setType: 'DROPSET', done: true, reps: 10, seconds: null, weightKg: 110 },
      { id: 3, exerciseId: 'bench_press', block: 'Main', setType: 'RESTPAUSE', done: true, reps: 3, seconds: null, weightKg: 105 },
      { id: 4, exerciseId: 'bench_press', block: 'Main', setType: 'FAILURE', done: true, reps: 5, seconds: null, weightKg: 100 },
    ]
    const prev = new Map([['bench_press', 90]])
    const picked = pickPrs(sets, () => 'WEIGHTED', prev)
    expect([...picked]).toEqual([4])
  })

  it('does not award a PR for weighted lifts when previous best is 0 kg', () => {
    const sets = [
      { id: 1, exerciseId: 'goblet_squat', block: 'Main', done: true, reps: 8, seconds: null, weightKg: 15 },
    ]
    const prev = new Map([['goblet_squat', 0]])
    expect([...pickPrs(sets, () => 'WEIGHTED', prev)]).toEqual([])
  })

  it('isExtraBlock detects warm-up and cool-down case-insensitively', () => {
    expect(isExtraBlock('Warm-up')).toBe(true)
    expect(isExtraBlock('COOL-DOWN')).toBe(true)
    expect(isExtraBlock('Main')).toBe(false)
    expect(isExtraBlock(null)).toBe(false)
  })
})
