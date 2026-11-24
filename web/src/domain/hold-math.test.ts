import { describe, expect, it } from 'vitest'
import { recordedHold } from './hold-math'

describe('HoldMath', () => {
  it('holdStoppedOneSecondLateSavesTarget', () => {
    expect(recordedHold(31, 30)).toBe(30)
  })

  it('holdStoppedThreeSecondsLateSavesTarget', () => {
    expect(recordedHold(33, 30)).toBe(30)
  })

  it('holdStoppedFourSecondsLateSavesHeld', () => {
    expect(recordedHold(34, 30)).toBe(34)
  })

  it('holdStoppedEarlySavesHeld', () => {
    expect(recordedHold(22, 30)).toBe(22)
  })

  it('zeroTargetSavesHeld', () => {
    expect(recordedHold(15, 0)).toBe(15)
    expect(recordedHold(2, 0)).toBe(2)
  })
})
