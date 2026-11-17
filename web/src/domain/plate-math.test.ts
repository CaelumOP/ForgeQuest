import { describe, expect, it } from 'vitest'
import {
  barWeightInUnit,
  calculatePlates,
  formatPlatesSummary,
  isBarbellExercise,
} from './plate-math'
import { toKg } from './units'

describe('plate-math', () => {
  it('detects barbell exercises from equipment field or swap-engine profile', () => {
    expect(isBarbellExercise(null)).toBe(false)
    expect(isBarbellExercise({ id: 'custom_bb', equipment: 'BARBELL,BENCH' })).toBe(true)
    expect(isBarbellExercise({ id: 'custom_db', equipment: 'DUMBBELL' })).toBe(false)
    expect(isBarbellExercise({ id: 'bench_press', equipment: '' })).toBe(true)
    expect(isBarbellExercise({ id: 'db_bench_press', equipment: '' })).toBe(false)
  })

  it('computes standard 20 kg bar plates and formats repeats cleanly', () => {
    const b100 = calculatePlates(100, 'KG', 20)
    expect(b100.barWeight).toBe(20)
    expect(b100.loadedWeight).toBe(100)
    expect(b100.remainder).toBe(0)
    expect(b100.belowBar).toBe(false)
    expect(b100.barOnly).toBe(false)
    expect(b100.perSide).toEqual([
      { weight: 25, count: 1 },
      { weight: 15, count: 1 },
    ])
    expect(formatPlatesSummary(b100.perSide)).toBe('25 + 15')

    const b140 = calculatePlates(140, 'KG', 20)
    expect(formatPlatesSummary(b140.perSide)).toBe('2×25 + 10')
  })

  it('handles bar-only, below-bar, and custom plate inventory remainders', () => {
    const barOnly = calculatePlates(15, 'KG', 15)
    expect(barOnly.barOnly).toBe(true)
    expect(barOnly.belowBar).toBe(false)
    expect(barOnly.perSide).toEqual([])

    const below = calculatePlates(10, 'KG', 15)
    expect(below.belowBar).toBe(true)

    const partial = calculatePlates(62.5, 'KG', 20, [20, 10, 5])
    expect(partial.loadedWeight).toBe(60)
    expect(partial.remainder).toBe(2.5)
  })

  it('converts 20/15 kg bars to 45/35 lb bars and calculates lb plates', () => {
    expect(barWeightInUnit(20, 'LB')).toBe(45)
    expect(barWeightInUnit(15, 'LB')).toBe(35)

    const b135 = calculatePlates(toKg('LB', 135), 'LB', 20)
    expect(b135.loadedWeight).toBe(135)
    expect(b135.perSide).toEqual([{ weight: 45, count: 1 }])

    const b225 = calculatePlates(toKg('LB', 225), 'LB', 20)
    expect(b225.perSide).toEqual([{ weight: 45, count: 2 }])
  })
})
