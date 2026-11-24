import { describe, expect, it } from 'vitest'
import { e1rm } from './strength'

describe('Strength', () => {
  it('scores a single as exactly the weight', () => {
    expect(e1rm(100, 1)).toBe(100)
    expect(e1rm(62.5, 1)).toBe(62.5)
  })
  it('uses Epley between two and twelve reps', () => {
    for (const reps of [2, 5, 12]) expect(e1rm(100, reps)).toBeCloseTo(100 * (1 + reps / 30), 3)
  })
  it('soft caps high reps', () => {
    expect(e1rm(60, 20)).toBeCloseTo(60 * (1 + (12 + 3 * Math.log(9)) / 30), 3)
    expect(e1rm(60, 20)).toBeLessThan(60 * (1 + 20 / 30))
    expect(e1rm(60, 20)).toBeGreaterThan(e1rm(60, 12))
  })
  it('twenty light reps never beat an honest heavy five', () => {
    expect(e1rm(100, 5)).toBeGreaterThan(e1rm(60, 20))
  })
  it('rejects nonpositive inputs and nonfinite numbers', () => {
    for (const weight of [0, -40, NaN, Infinity]) expect(e1rm(weight, 5)).toBe(0)
    for (const reps of [0, -3, NaN, Infinity]) expect(e1rm(100, reps)).toBe(0)
  })
})
