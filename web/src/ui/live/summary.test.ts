import { describe, expect, it } from 'vitest'
import { nextUpDay, workoutMinutes } from './summary'

const day = (dayOfWeek: number, title: string, isRest = false, items = 1) => ({ day: { dayOfWeek, title, isRest }, items: new Array(items).fill(0) })

describe('nextUpDay', () => {
  const days = [day(1, 'Push'), day(3, 'Pull'), day(5, 'Legs'), day(6, 'Rest', true, 0), day(7, 'Empty', false, 0)]
  it('picks tomorrow or the next training day', () => {
    expect(nextUpDay(days, 1)).toEqual({ dow: 3, title: 'Pull' })
    expect(nextUpDay(days, 5)).toEqual({ dow: 1, title: 'Push' })
    expect(nextUpDay(days, 6)).toEqual({ dow: 1, title: 'Push' })
  })
  it('falls back to today only when it is the sole training day', () => {
    expect(nextUpDay([day(2, 'Solo')], 2)).toEqual({ dow: 2, title: 'Solo' })
    expect(nextUpDay([day(2, 'Rest', true)], 2)).toBeNull()
  })
})

describe('workoutMinutes', () => {
  it('is at least one minute', () => {
    expect(workoutMinutes(0, 10_000)).toBe(1)
    expect(workoutMinutes(0, 45 * 60_000 + 5)).toBe(45)
  })
})
