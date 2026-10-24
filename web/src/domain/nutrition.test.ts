import { describe, expect, it } from 'vitest'
import {
  activityFactor, ageFromBirthYear, proteinPerKgFor, suggestedCarbs, suggestedKcal, suggestedProtein, weightTrendNote,
} from './nutrition'

describe('NutritionTest', () => {
  it('mifflinStJeorWithActivity', () => {
    // 75 kg, 178 cm, 25-year-old man, sitting, 5 training days: rest 1742.5 × (1.25 + 5 × 0.025) = 2396 → 2400
    expect(suggestedKcal(75, 178, 25, true, 5)).toBe(2400)
    // same woman, 3 days: rest 1576.5 × 1.325 = 2089 → 2100
    expect(suggestedKcal(75, 178, 25, false, 3)).toBe(2100)
    // 6 days: 1742.5 × 1.40 = 2440 → 2450
    expect(suggestedKcal(75, 178, 25, true, 6)).toBe(2450)
    expect(activityFactor(7)).toBeCloseTo(1.425, 5)
  })

  it('goalShiftsCaloriesAndProtein', () => {
    // maintenance 2396: a Standard cut takes min(0.75 % of 75 kg × 1,100 = 619, 25 % = 599); a Standard gain adds 206
    expect(suggestedKcal(75, 178, 25, true, 5, 0, 'CUT')).toBe(1800)
    expect(suggestedKcal(75, 178, 25, true, 5, 0, 'GAIN')).toBe(2600)
    expect(proteinPerKgFor('CUT')).toBe(2.0)
    expect(proteinPerKgFor('GAIN')).toBe(1.8)
  })

  it('weightTrendWaitsForTwoWeeksThenFlagsFastCut', () => {
    const fast: [number, number][] = [[0, 80], [1, 79.8], [2, 79.6], [13, 77.6], [14, 77.4], [15, 77.2]]
    // only 9 days of data since the goal was set: say nothing yet
    expect(weightTrendNote('CUT', 6, fast, 15)).toBeNull()
    // ~1.3 % a week over two weeks: too fast
    expect(weightTrendNote('CUT', 0, fast, 15)).toBe('CUT_TOO_FAST')
    const steady: [number, number][] = [[0, 80], [1, 80], [2, 80], [20, 80], [21, 80.1], [22, 80]]
    expect(weightTrendNote('GAIN', 0, steady, 22)).toBe('GAIN_STALLED')
    expect(weightTrendNote('MAINTAIN', 0, fast, 15)).toBeNull()
  })

  it('weightTrendWithFewWeighInsSeesTheRealChange', () => {
    // Three weigh-ins used to be averaged into the same number twice, so a real gain read as "stalled".
    // ~1 % a week is a real gain, and faster than the Standard pace allows (0.5 %).
    expect(weightTrendNote('GAIN', 0, [[0, 70], [10, 71], [21, 72]], 21)).toBe('GAIN_TOO_FAST')
    // 2.5 % a week with three weigh-ins: too fast
    expect(weightTrendNote('CUT', 0, [[0, 80], [7, 78], [14, 76]], 14)).toBe('CUT_TOO_FAST')
  })

  it('fallsBackToPerKgWithoutHeightOrAge', () => {
    // no height: 22 kcal/kg × 1.375 = 2269 → 2250
    expect(suggestedKcal(75, 0, 25, true, 5)).toBe(2250)
    expect(suggestedProtein(75)).toBe(135)
    expect(suggestedProtein(75, 2.2)).toBe(165)
    // 2500 kcal, 135 g protein: 25 % fat (69.4 → 70 g) leaves 1330 kcal = 335 g.
    expect(suggestedCarbs(2500, 135)).toBe(335)
    // web: audit #15 (Android 240): 40 % fat is 111.1 → 110 g, which leaves 970 kcal = 242.5 → 245 g.
    expect(suggestedCarbs(2500, 135, 40)).toBe(245)
    expect(ageFromBirthYear(2000, 2026)).toBe(26)
    expect(ageFromBirthYear(0, 2026)).toBe(0)
  })
})

describe('web additions', () => {
  it('macro grams add up to the calorie goal within one 5 g carb step (audit #15)', () => {
    for (const [kcal, protein, pct] of [[2500, 135, 25], [2500, 135, 40], [1800, 150, 30], [3100, 160, 20]]) {
      const fat = Math.round(Math.max((kcal * pct) / 100 / 9, 0) / 5) * 5
      const carbs = suggestedCarbs(kcal, protein, pct)
      expect(Math.abs(protein * 4 + fat * 9 + carbs * 4 - kcal)).toBeLessThanOrEqual(10)
    }
  })

  it('essential fat (0.7 g/kg) wins over a small share', () => {
    // 1800 kcal at 20 % is 40 g fat, but 100 kg needs 70 g: 1800 − 600 − 630 = 570 kcal → 142.5 → 145 g
    expect(suggestedCarbs(1800, 150, 20, 100)).toBe(145)
  })
})

describe('audit 2026-10-24', () => {
  it('carbs never push the day over its calorie goal', () => {
    // 1,200 kcal - 150 g protein (600) - 60 g fat (540) leaves 60 kcal: 15 g carbs, not the old 20 g minimum.
    expect(suggestedCarbs(1200, 150, 45)).toBe(15)
  })
})
