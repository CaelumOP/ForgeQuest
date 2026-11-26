import { describe, expect, it } from 'vitest'
import type { BaselineTestEntity } from './entities'
import { defaultPlanAnswers, planAnswersToInput } from './plan-answers'

describe('planAnswersToInput baseline overrides', () => {
  it('reads pushUpMax and pullUpMax from baseline rows with fallback to answers', () => {
    const answers = { ...defaultPlanAnswers(), pushUpMax: 8, pullUpMax: 3 }
    const baselines = new Map<string, BaselineTestEntity>([
      ['push_up', { id: 1, exerciseId: 'push_up', kind: 'REPS', reps: 20, seconds: null, weightKg: null, createdAt: 100 }],
    ])

    const input = planAnswersToInput(answers, 0, baselines)
    expect(input.pushUpMax).toBe(20)
    expect(input.pullUpMax).toBe(3)

    // Explicit 0 baseline overrides non-zero answer.
    baselines.set('pull_up', { id: 2, exerciseId: 'pull_up', kind: 'REPS', reps: 0, seconds: null, weightKg: null, createdAt: 200 })
    const zeroPull = planAnswersToInput(answers, 0, baselines)
    expect(zeroPull.pullUpMax).toBe(0)
  })

  it('feeds squat, hip bridge and plank baselines into the plan input, and nothing without rows', () => {
    const row = (exerciseId: string, kind: 'REPS' | 'HOLD', reps: number | null, seconds: number | null): BaselineTestEntity =>
      ({ id: 1, exerciseId, kind, reps, seconds, weightKg: null, createdAt: 100 })
    const baselines = new Map<string, BaselineTestEntity>([
      ['squat', row('squat', 'REPS', 12, null)],
      ['hip_bridge', row('hip_bridge', 'REPS', 20, null)],
      ['plank', row('plank', 'HOLD', null, 45)],
    ])
    const input = planAnswersToInput(defaultPlanAnswers(), 0, baselines)
    expect([input.squatMax, input.hingeMax, input.plankSeconds]).toEqual([12, 20, 45])
    // A row of the wrong kind is ignored.
    baselines.set('plank', row('plank', 'REPS', 9, null))
    expect(planAnswersToInput(defaultPlanAnswers(), 0, baselines).plankSeconds).toBeNull()
    const none = planAnswersToInput(defaultPlanAnswers())
    expect([none.squatMax, none.hingeMax, none.plankSeconds]).toEqual([null, null, null])
  })
})
