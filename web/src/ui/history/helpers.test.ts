import { describe, expect, it } from 'vitest'
import type { SessionRow } from '../../data/db'
import { dayOfIso, decodeDebrief, groupWeeks, isoOfDay, minutesOf, weekStart } from './helpers'

const ses = (id: number, epochDay: number): SessionRow =>
  ({ id, planDayId: null, title: 'A', epochDay, startedAt: epochDay * 1000 + id, finishedAt: null, note: '', debrief: '' })

describe('history helpers', () => {
  it('finds week starts', () => {
    expect(weekStart(4, 0)).toBe(4) // 1970-01-05 is a Monday
    expect(weekStart(10, 0)).toBe(4)
    expect(weekStart(3, 6)).toBe(3) // Sunday 1970-01-04
    expect(weekStart(9, 6)).toBe(3)
    expect(weekStart(0, 0)).toBe(-3)
  })
  it('groups newest week first', () => {
    const w = groupWeeks([ses(1, 4), ses(2, 11), ses(3, 6)], 0)
    expect(w.map((x) => x.start)).toEqual([11, 4])
    expect(w[1].sessions.map((s) => s.id)).toEqual([3, 1])
  })
  it('decodes debriefs', () => {
    expect(decodeDebrief('')).toBeNull()
    expect(decodeDebrief('nope')).toBeNull()
    expect(decodeDebrief('{"score":2,"summary":" ok "}')).toEqual({ summary: 'ok', nextTime: [] })
    expect(decodeDebrief('{"score":9,"summary":"x","nextTime":["add"]}')?.nextTime).toEqual(['add'])
  })
  it('minutes and iso days', () => {
    expect(minutesOf({ startedAt: 0, finishedAt: 10_000 })).toBe(1)
    expect(minutesOf({ startedAt: 0, finishedAt: null })).toBeNull()
    expect(dayOfIso(isoOfDay(20000))).toBe(20000)
  })
})
