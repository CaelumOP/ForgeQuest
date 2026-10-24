import { describe, expect, it } from 'vitest'
import {
  allowedCutPace, bmrKcal, calorieFloor, DAY_BASE, dailyBurn, legacySuggestedKcal, proteinPerKgFor, stepsBase,
  suggestedKcal, suggestedProtein, weightTrendNote,
} from './nutrition'
import { calorieSignals, loggedShare, nextTarget, realBurn, stepDrop, theilSen } from './calorie-check'

// The eight people from docs/plans/calorie-plan.md (v2); Android CaloriePlanTest.kt has the same cases.
describe('calorie plan v2: targets', () => {
  it('1. desk worker, 5 days, Lose fat: 1,850 (Gentle 2,050, Fast 1,750), 165 g protein', () => {
    expect(Math.round(dailyBurn(83, 172, 20, true, 5))).toBe(2489)
    expect(suggestedKcal(83, 172, 20, true, 5, 0, 'CUT')).toBe(1850)
    expect(suggestedKcal(83, 172, 20, true, 5, 0, 'CUT', { cutPace: 'GENTLE' })).toBe(2050)
    expect(suggestedKcal(83, 172, 20, true, 5, 0, 'CUT', { cutPace: 'AGGRESSIVE' })).toBe(1750)
    expect(suggestedProtein(83, proteinPerKgFor('CUT'), 0, 172)).toBe(165)
    expect(legacySuggestedKcal(83, 172, 20, true, 5, 0, 'CUT')).toBe(2300)
  })
  it('2-3. fewer days or a more active day change the burn', () => {
    expect(suggestedKcal(83, 172, 20, true, 3, 0, 'CUT')).toBe(1800)
    expect(suggestedKcal(83, 172, 20, true, 5, 0, 'CUT', { base: DAY_BASE.MIXED })).toBe(2050)
  })
  it('4. woman, 3 days: 1,300, Fast stops at the 1,200 floor', () => {
    expect(suggestedKcal(60, 165, 30, false, 3, 0, 'CUT')).toBe(1300)
    expect(suggestedKcal(60, 165, 30, false, 3, 0, 'CUT', { cutPace: 'AGGRESSIVE' })).toBe(1200)
    expect(calorieFloor(60, 165, 30, false)).toBe(1200)
  })
  it('5. 100 kg, no plan: 1,850, protein from adjusted weight above BMI 30', () => {
    expect(suggestedKcal(100, 180, 35, true, 0, 0, 'CUT')).toBe(1850)
    expect(suggestedProtein(100, 2.0, 0, 180)).toBe(170)
    // BMI 28 (muscular lifter) keeps protein on real weight
    expect(suggestedProtein(83, 2.0, 0, 172)).toBe(165)
  })
  it('6. Build muscle: Standard +228 → 2,700, Slow +137 → 2,650', () => {
    expect(suggestedKcal(83, 172, 20, true, 5, 0, 'GAIN')).toBe(2700)
    expect(suggestedKcal(83, 172, 20, true, 5, 0, 'GAIN', { gainPace: 'SLOW' })).toBe(2650)
  })
  it('7. under 18 or lean: Fast becomes Standard', () => {
    expect(allowedCutPace('AGGRESSIVE', 52, 168, 17, false)).toBe('STANDARD')
    expect(suggestedKcal(52, 168, 17, false, 3, 0, 'CUT', { cutPace: 'AGGRESSIVE' })).toBe(1350)
    expect(allowedCutPace('AGGRESSIVE', 60, 180, 25, true)).toBe('STANDARD') // BMI 18.5
    expect(allowedCutPace('AGGRESSIVE', 80, 180, 25, true, 10)).toBe('STANDARD') // 10 % body fat
    expect(allowedCutPace('AGGRESSIVE', 83, 172, 20, true)).toBe('AGGRESSIVE')
  })
  it('8. watch steps replace the day answer (300 = watch off; workout days lose 1,000)', () => {
    const base = stepsBase([[7800, false], [8200, false], [300, false], [8000, false], [7500, false], [8500, false], [8000, false]])!
    expect(base).toBeCloseTo(1.33, 3)
    expect(suggestedKcal(83, 172, 20, true, 4, 0, 'CUT', { base })).toBe(1950)
    expect(stepsBase([[9000, true], [9000, true], [9000, true], [9000, true], [9000, true]])).toBeCloseTo(1.33, 3)
    expect(stepsBase([[9000, false], [9000, false], [100, false], [100, false], [9000, false], [9000, false]])).toBeNull()
    expect(stepsBase(Array(7).fill([30000, false]))).toBe(Math.fround(1.5))
  })
  it('floors: men 1,500, never below 85 % of BMR', () => {
    expect(calorieFloor(83, 172, 20, true)).toBe(1550) // 0.85 × 1,810 = 1,539 → 1,550
    expect(calorieFloor(55, 160, 40, true)).toBe(1500)
    expect(Math.round(bmrKcal(83, 172, 20, true))).toBe(1810)
  })
  it('pace changes the warning and when a diet break is due', () => {
    // 0.9 %/week: fine for Standard (warns past 1.0), too fast for Gentle (past 0.75)
    const w: [number, number][] = [[0, 80], [1, 80], [2, 80], [14, 78.56], [15, 78.56], [16, 78.56]]
    expect(weightTrendNote('CUT', 0, w, 16)).toBeNull()
    expect(weightTrendNote('CUT', 0, w, 16, 'GENTLE')).toBe('CUT_TOO_FAST')
    expect(weightTrendNote('CUT', 1, [], 1 + 6 * 7, 'AGGRESSIVE')).toBe('DIET_BREAK_DUE')
    expect(weightTrendNote('CUT', 1, [], 1 + 6 * 7, 'STANDARD')).toBeNull()
    // a woman's gain needs 28 days before it reads as stalled
    const flat: [number, number][] = [[0, 60], [1, 60], [2, 60], [21, 60], [22, 60], [23, 60]]
    expect(weightTrendNote('GAIN', 0, flat, 23)).toBe('GAIN_STALLED')
    expect(weightTrendNote('GAIN', 0, flat, 23, 'STANDARD', 'STANDARD', true)).toBeNull()
  })
})

describe('calorie plan v2: real burn and signals', () => {
  const today = 100
  const window = Array.from({ length: 21 }, (_, i) => today - 21 + i) // days 79..99
  const intake = new Map(window.map((d) => [d, 2000]))
  // losing 0.5 kg a week
  const days = [79, 81, 83, 90, 95, 97, 99]
  const weights = days.map((d) => [d, 85 - (0.5 / 7) * (d - 79)] as [number, number])

  it('average intake minus the weight trend × 7,700 kcal/kg', () => {
    const b = realBurn(intake, weights, today, 0, 1810, 2489)!
    expect(b.kcal).toBe(2550)
    expect(b.loggedDays).toBe(21)
    expect(b.kgPerWeek).toBeCloseTo(-0.5, 2)
  })
  it('one salty day does not move the trend much (median of slopes)', () => {
    const salty = weights.map(([d, kg]) => [d, d === 95 ? kg + 1.2 : kg] as [number, number])
    const b = realBurn(intake, salty, today, 0, 1810, 2489)!
    expect(Math.abs(b.kcal - 2550)).toBeLessThanOrEqual(60)
    expect(theilSen([[0, 1], [1, 2], [2, 3]])).toBeCloseTo(1, 5)
  })
  it('waits for full logs, enough weigh-ins and a week after a goal change', () => {
    const partial = new Map(intake)
    for (const d of [80, 82, 84, 86, 88]) partial.set(d, 600) // half-logged days
    expect(realBurn(partial, weights, today, 0, 1810, 2489)).toBeNull()
    expect(realBurn(intake, weights.slice(0, 5), today, 0, 1810, 2489)).toBeNull()
    expect(realBurn(intake, weights, today, 75, 1810, 2489)).toBeNull()
    expect(loggedShare(partial, today, 1810)).toBeCloseTo(16 / 21, 5)
  })
  it('stays within 0.8–1.3 × the formula', () => {
    const tiny = new Map(window.map((d) => [d, 1300]))
    expect(realBurn(tiny, weights, today, 0, 1810, 2489)!.kcal).toBe(Math.round((0.8 * 2489) / 10) * 10)
  })
  it('moves the target at most 200 kcal, at most every 14 days', () => {
    expect(nextTarget(2300, 1850, 0, 20)).toBe(2100)
    expect(nextTarget(2300, 1850, 10, 20)).toBeNull()
    expect(nextTarget(1900, 1850, 0, 20)).toBeNull()
  })
  it('the four signals', () => {
    const base = { goal: 'CUT' as const, cutPace: 'STANDARD' as const, goalSetDay: 0, today, weights: [] as [number, number][], loggedShare: 1, steps: new Map<number, number>(), deloadNeeded: false, targetWeightKg: 0 }
    expect(calorieSignals({ ...base, deloadNeeded: true })).toEqual(['UNDER_FUELLED'])
    expect(calorieSignals({ ...base, cutPace: 'GENTLE', deloadNeeded: true })).toEqual([])
    expect(calorieSignals({ ...base, targetWeightKg: 83, weights: [[96, 80.4], [99, 80.5]] })).toEqual(['WEIGHT_CHANGED'])
    const flat: [number, number][] = [[60, 83], [62, 83], [64, 83.1], [88, 83], [92, 82.9], [96, 83]]
    expect(calorieSignals({ ...base, goalSetDay: 60, weights: flat, loggedShare: 0.3 })).toEqual(['SCALE_CREEP'])
    expect(calorieSignals({ ...base, goalSetDay: 60, weights: flat, loggedShare: 0.9 })).toEqual([])
    const steps = new Map<number, number>()
    for (let d = 50; d < 64; d++) steps.set(d, 8000)
    for (let d = today - 14; d < today; d++) steps.set(d, 5500)
    expect(stepDrop(steps, 50, today)).toBe(true)
    expect(calorieSignals({ ...base, goalSetDay: 50, steps })).toEqual(['STEP_DROP'])
    expect(calorieSignals({ ...base, goal: 'MAINTAIN', deloadNeeded: true, steps })).toEqual([])
  })
})
