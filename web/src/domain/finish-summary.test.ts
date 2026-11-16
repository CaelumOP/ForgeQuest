import { describe, expect, it } from 'vitest'
import { appendHistoryEntries, dayType, pickHeadlineVariant, scoreTier } from './finish-summary'
import type { DebriefResult } from './debrief'
import type { Insight } from './insights'
import type { WorkoutScoreResult } from './workout-score'

const baseScore = (over: Partial<WorkoutScoreResult> = {}): WorkoutScoreResult => ({
  score: 8, doneSets: 12, plannedSets: 12, up: 2, compared: 3, prs: 0,
  rough: false, firstEver: false, afterBreak: false, showNumber: true, exercises: [],
  ...over,
})

const baseDebrief = (headlineType: DebriefResult['headline']['type'] = 'MOSTLY_UP'): DebriefResult => ({
  headline: { type: headlineType, variant: 0 },
  wins: [], watch: [], next: [], sameRest: false, footnote: false,
})

describe('finish-summary', () => {
  it('classifies dayType and scoreTier matching Android Insights.kt', () => {
    const milestone: Insight = { kind: 'CROSS_WORKOUTS', plural: false, rank: 92, cooldownDays: 3, variant: 0, slots: [{ type: 'count', n: 10 }] }
    expect(dayType(baseScore({ prs: 1 }), baseDebrief(), [])).toBe('PR')
    expect(dayType(baseScore({ firstEver: true }), baseDebrief('FIRST_EVER'), [])).toBe('FIRST_OR_BACK')
    expect(dayType(baseScore({ rough: true }), baseDebrief(), [])).toBe('ROUGH')
    expect(dayType(baseScore(), baseDebrief(), [milestone])).toBe('MILESTONE')
    expect(dayType(baseScore(), baseDebrief(), [])).toBe('ORDINARY')

    expect(scoreTier(baseScore({ prs: 1 }))).toBe('RECORD_DAY')
    expect(scoreTier(baseScore({ doneSets: 6, effectiveSets: 12 }))).toBe('LIGHT_DAY')
    expect(scoreTier(baseScore({ up: 2, compared: 3 }))).toBe('STEP_FORWARD')
    expect(scoreTier(baseScore({ up: 0, compared: 3 }))).toBe('HELD_STEADY')
  })

  it('tracks headline variant and history cap', () => {
    expect(pickHeadlineVariant('MOSTLY_UP', '')).toBe(0)
    expect(pickHeadlineVariant('FIRST_EVER', '')).toBeNull()
    const hist = appendHistoryEntries('', [{ kind: 'CROSS_WORKOUTS', plural: false, rank: 92, cooldownDays: 3, variant: 0, slots: [] }], 100)
    expect(hist).toBe('CROSS_WORKOUTS:0:100')
  })
})
