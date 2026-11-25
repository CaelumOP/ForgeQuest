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
})
