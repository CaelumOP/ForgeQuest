import { describe, expect, it } from 'vitest'
import type { SessionRow } from '../../data/db'
import type { WorkoutScoreResult } from '../../domain/workout-score'
import { dayOfIso, decodeDebrief, groupWeeks, isoOfDay, minutesOf, scoreLine, scoreNote, scoreTone, weekStart } from './helpers'

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
  it('journal facts line has no N/10, shows the recovery note when rough', () => {
    const r: WorkoutScoreResult = { score: 8, doneSets: 12, plannedSets: 12, up: 3, compared: 4, prs: 1, rough: false, firstEver: false, afterBreak: false, showNumber: true }
    expect(scoreLine(r)).not.toContain('/10')
    expect(scoreLine(r)).not.toContain('8')
    expect(scoreLine({ ...r, rough: true })).toContain('Adjusted for recovery')
  })
  it('number is gold only from 8 up; first-ever and after-break show a sentence instead', () => {
    expect(scoreTone(10)).toBe('gold')
    expect(scoreTone(8)).toBe('gold')
    expect(scoreTone(7)).toBe('quiet')
    expect(scoreTone(4)).toBe('quiet')
    const r: WorkoutScoreResult = { score: 6, doneSets: 5, plannedSets: null, up: 0, compared: 0, prs: 0, rough: false, firstEver: true, afterBreak: false, showNumber: false }
    expect(scoreNote(r)).toBe("First one done. That's the hardest part.")
    expect(scoreNote({ ...r, firstEver: false, afterBreak: true })).toBe("Back in. That's what counts.")
    expect(scoreNote({ ...r, showNumber: true })).toBeNull()
  })
})
