import { describe, expect, it } from 'vitest'
import { epochDayOf, weekStart } from './dates'
import { nudgeTarget, showTargetNudge } from './target-nudge'

const today = epochDayOf(2026, 10, 28) // a Wednesday
const thisWeek = weekStart(today)
const start = thisWeek - 5 * 7
/** [days trained two weeks ago, days trained last week] */
const days = (a: number, b: number): Set<number> => {
  const set = new Set<number>()
  for (let i = 0; i < a; i++) set.add(thisWeek - 14 + i)
  for (let i = 0; i < b; i++) set.add(thisWeek - 7 + i)
  return set
}

describe('target nudge', () => {
  it('offers 3 after two weeks at 3 with a target of 5', () => {
    expect(nudgeTarget(days(3, 3), 5, start, today)).toBe(3)
  })
  it('offers the lower of the two weeks', () => {
    expect(nudgeTarget(days(4, 2), 5, start, today)).toBe(2)
  })
  it('stays quiet when a week hit the target, had under 2 workouts, or the target is already low', () => {
    expect(nudgeTarget(days(5, 3), 5, start, today)).toBeNull()
    expect(nudgeTarget(days(3, 1), 5, start, today)).toBeNull()
    expect(nudgeTarget(days(3, 0), 5, start, today)).toBeNull()
    expect(nudgeTarget(days(3, 3), 3, start, today)).toBeNull()
  })
  it('needs two finished weeks since the first workout', () => {
    expect(nudgeTarget(days(3, 3), 5, thisWeek - 7, today)).toBeNull()
    expect(nudgeTarget(days(3, 3), 5, thisWeek - 14, today)).toBe(3)
  })
  it('shows once: any answer ends it', () => {
    expect(showTargetNudge(false, 3)).toBe(true)
    expect(showTargetNudge(true, 3)).toBe(false)
    expect(showTargetNudge(false, null)).toBe(false)
  })
})
