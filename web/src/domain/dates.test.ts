import { describe, expect, it } from 'vitest'
import { dayOfWeek, epochDayOf, WEEK_ORDER, weekStart } from './dates'

describe('dates', () => {
  it('starts weeks on Sunday: Sunday returns itself, Saturday returns 6 days earlier', () => {
    const sunday = epochDayOf(2026, 10, 11)
    const monday = epochDayOf(2026, 10, 12)
    const saturday = epochDayOf(2026, 10, 17)
    expect(dayOfWeek(sunday)).toBe(7)
    expect(dayOfWeek(saturday)).toBe(6)
    expect(weekStart(sunday)).toBe(sunday)
    expect(weekStart(monday)).toBe(sunday)
    expect(weekStart(saturday)).toBe(sunday)
    expect(weekStart(saturday)).toBe(saturday - 6)
  })

  it('orders display weekdays Sunday-first while keeping ISO day numbers', () => {
    expect([...WEEK_ORDER]).toEqual([7, 1, 2, 3, 4, 5, 6])
  })
})
