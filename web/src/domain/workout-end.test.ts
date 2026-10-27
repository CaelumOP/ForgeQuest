import { describe, expect, it } from 'vitest'
import { endTime, lastDoneAt } from './workout-end'

const now = 10_000_000_000
const min = 60_000

describe('endTime', () => {
  it('ends two minutes after the last set when idle for hours', () => {
    const last = now - 3 * 60 * min
    expect(endTime(now, last)).toBe(last + 2 * min)
  })
  it('ends now when the last set was 5 minutes ago', () => expect(endTime(now, now - 5 * min)).toBe(now))
  it('ends now when there is no tick time', () => expect(endTime(now, null)).toBe(now))
})

describe('lastDoneAt', () => {
  it('takes the latest tick among done sets, ignoring unticked and untimed ones', () => {
    expect(lastDoneAt([{ done: true, doneAt: 5 }, { done: true, doneAt: 9 }, { done: false, doneAt: 20 }, { done: true }])).toBe(9)
    expect(lastDoneAt([{ done: true, doneAt: null }])).toBeNull()
  })
})
