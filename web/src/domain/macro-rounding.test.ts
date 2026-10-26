import { describe, expect, it } from 'vitest'
import { gramsLeft, scaledKcal } from './nutrition'
import { scanTotals } from '../ui/food/scan'
import { carbWeeks } from '../ui/progress/helpers'

// Audit item 15: one rounding rule for calories and macros; app/src/test/.../domain/MacroRoundingTest.kt has the same cases.
describe('MacroRoundingTest', () => {
  it('scaledCaloriesRoundToTheNearestKcal', () => {
    expect(scaledKcal(125, 0.5)).toBe(63) // logging used to cut this to 62
    expect(scaledKcal(125, 1.5)).toBe(188)
    expect(scaledKcal(250, 1.3)).toBe(325)
    expect(scaledKcal(0, 2)).toBe(0)
    expect(scaledKcal(140, 1)).toBe(140)
  })

  it('theLogButtonTotalIsWhatTheDayGains', () => {
    // Two half servings of a 101 kcal food: each is logged as 51, so the button says 102, not round(101) = 101.
    const picks: [number, number][] = [[101, 0.5], [101, 0.5]]
    expect(picks.map(([k, q]) => scaledKcal(k, q))).toEqual([51, 51])
    expect(picks.reduce((a, [k, q]) => a + scaledKcal(k, q), 0)).toBe(102)
  })

  it('aScannedMealIsTheSumOfItsSavedParts', () => {
    const item = (kcal: number) => ({ name: 'x', grams: 100, kcal, protein: 0, carbs: 0 })
    // 102 + 102 kcal at a small portion: 76.5 each, saved as two 77 kcal parts, so the meal logs 154 (not 153).
    expect(scanTotals([item(102), item(102)], 0.75, 0).kcal).toBe(154)
    expect(scanTotals([item(102), item(102)], 0.75, 50).kcal).toBe(154 + 50)
    expect(scanTotals([item(400), item(201)], 1.25, 50).kcal).toBe(500 + 251 + 50)
    expect(scanTotals([], 1, 150).kcal).toBe(0)
  })

  it('gramsLeftMatchTheEatenNumberOnScreen', () => {
    expect(gramsLeft(100, 99.6)).toBe(0) // shows 100 eaten, so nothing left (it said 1 left)
    expect(gramsLeft(100, 99.5)).toBe(0)
    expect(gramsLeft(100, 99.4)).toBe(1)
    expect(gramsLeft(150, 155.2)).toBe(-5)
  })

  it('weeklyCarbAveragesRoundInsteadOfCutting', () => {
    const today = 20000
    const weeks = carbWeeks([{ epochDay: today, carbs: 100 }, { epochDay: today, carbs: 101 }, { epochDay: today - 1, carbs: 0 }], today)
    expect(weeks).toHaveLength(12)
    expect(weeks[11]).toBe(101) // 100.5 used to show as 100
    expect(weeks[0]).toBe(0)
  })
})
