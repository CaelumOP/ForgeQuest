import { describe, expect, it } from 'vitest'
import { carbWeeks, formatDelta, formatValue, grouped, recoveryFill } from './helpers'

describe('progress helpers', () => {
  it('maps fatigue to the legend colors', () => {
    expect(recoveryFill(0)).toBe('var(--muscle-rest)')
    expect(recoveryFill(0.2)).toContain('color-mix')
    expect(recoveryFill(0.5)).toBe('var(--gold)')
    expect(recoveryFill(0.9)).toBe('var(--coral)')
  })
  it('formats values and deltas by kind', () => {
    expect(formatValue(12.4, 'REPS', 'KG', 's', false)).toBe('12')
    expect(formatValue(30, 'HOLD', 'KG', 's', false)).toBe('30 s')
    expect(formatDelta(0.001, 'REPS', 'KG', 's', 'kg')).toBeNull()
    expect(formatDelta(-3, 'REPS', 'KG', 's', 'kg')).toBe('−3')
    expect(formatDelta(5, 'HOLD', 'KG', 's', 'kg')).toBe('+5 s')
    expect(formatDelta(2.5, 'WEIGHTED', 'KG', 's', 'kg')).toBe('+3 kg')
  })
  it('averages carbs per logged day by week', () => {
    const today = 20_000
    const out = carbWeeks([{ epochDay: today, carbs: 200 }, { epochDay: today, carbs: 100 }, { epochDay: today - 1, carbs: 0 }], today)
    expect(out).toHaveLength(12)
    expect(out[11]).toBe(150)
    expect(out[0]).toBe(0)
  })
  it('groups thousands', () => expect(grouped(12345)).toBe('12,345'))
})
