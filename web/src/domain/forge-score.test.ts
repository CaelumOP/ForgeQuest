import { describe, expect, it } from 'vitest'
import vectors from '../../../worker/test/forge-score-v2.vectors.json'
import { buildLeaderboard, computeForgeWeek, scoreBreakdown, scoreReachedAt, normalizeUsername, type Friend, type Person, type Snapshot } from './forge-score'

describe('computeForgeWeek', () => {
  for (const v of vectors.cases) it(v.name, () => {
    expect(scoreBreakdown(v.done, v.target, v.prCount, v.proteinDays)).toEqual(v.expect)
  })
  it('uses the full target, override, distinct days, and Sunday reset', () => {
    const ws = 20772
    const scheduled = new Set([1, 3, 5])
    expect(computeForgeWeek(ws, scheduled, new Set([ws - 1])).score).toBe(0)
    expect(computeForgeWeek(ws + 2, scheduled, new Set([ws, ws + 1, ws + 8]), new Set([ws - 1, ws]), 0, 1))
      .toMatchObject({ score: 74, target: 3, done: 2, proteinDays: 1 })
    expect(computeForgeWeek(ws + 1, scheduled, new Set([ws]), new Set(), 2).score).toBe(50)
    expect(computeForgeWeek(ws, new Set(), new Set()).score).toBe(0)
  })
})

describe('scoreReachedAt', () => {
  it('ignores duplicate days and PRs beyond the cap, irrespective of input order', () => {
    expect(scoreReachedAt(1, [{ at: 50, pr: true }, { at: 40, pr: true }, { at: 30, pr: true },
      { at: 20, workoutDay: 1 }, { at: 10, workoutDay: 1 }], 0)).toBe(40)
    expect(scoreReachedAt(3, [], 0)).toBe(0)
  })
})

describe('buildLeaderboard', () => {
  const ws = 20772
  const me: Person = { id: 'me', username: 'tariq', name: 'Tariq', photoUrl: null }
  const p = (id: string, name: string) => ({ id, username: id, name, photoUrl: null })
  const snap = (weekStart: number, score: number, badge: Snapshot['badge'] = 'on_track', prs = true): Snapshot => ({
    weekStart,
    done: 3,
    due: 4,
    score,
    badge,
    streakWeeks: 3,
    proteinDays: 4,
    prs: prs ? [{ exercise: 'Bench Press', kg: 80, reps: 5 }] : null,
  })

  it('carries done, weekState and sharedStreak; a stale friend keeps the streak but shows no week', () => {
    const friends: Friend[] = [
      { ...p('o', 'Omar'), snapshot: { ...snap(ws, 100, 'trained'), weekState: 'hit' }, sharedStreak: 4 },
      { ...p('n', 'Noor'), snapshot: { ...snap(ws - 7, 80), weekState: null }, sharedStreak: 2 },
      { ...p('s', 'Sami'), snapshot: snap(ws, 40) },
    ]
    const board = buildLeaderboard(ws, me, snap(ws, 90, 'trained'), friends)
    const by = Object.fromEntries(board.map((e) => [e.person.id, e]))
    expect(by.o).toMatchObject({ done: 3, weekState: 'hit', sharedStreak: 4 })
    expect(by.n).toMatchObject({ syncedThisWeek: false, done: 0, weekState: null, sharedStreak: 2 })
    expect(by.s).toMatchObject({ weekState: null, sharedStreak: 0 })
    expect(by.me.sharedStreak).toBe(0)
  })

  it('uses reachedAt before username and never re-derives the server score', () => {
    const board = buildLeaderboard(ws, null, null, [
      { ...p('aaa', 'A'), snapshot: { ...snap(ws, 72), reachedAt: 30 } },
      { ...p('zzz', 'Z'), snapshot: { ...snap(ws, 72), reachedAt: 20 } },
    ])
    expect(board.map(e => e.person.id)).toEqual(['zzz', 'aaa'])
    expect(board[0].score).toBe(72)
  })

  it('includes me highlighted and sorts by score desc then username asc', () => {
    const friends: Friend[] = [
      { ...p('z', 'Zaid'), snapshot: snap(ws, 90, 'trained') },
      { ...p('a', 'omar'), snapshot: snap(ws, 90, 'on_track') },
      { ...p('l', 'Lina'), snapshot: snap(ws, 100, 'trained') },
    ]
    const board = buildLeaderboard(ws, me, snap(ws, 90, 'trained'), friends)
    expect(board.map((e) => e.person.name)).toEqual(['Lina', 'omar', 'Tariq', 'Zaid'])
    expect(board.map((e) => e.isMe)).toEqual([false, false, true, false])
  })

  it('zeros stale or missing snapshots as not_synced and maps null prs to []', () => {
    const friends: Friend[] = [
      { ...p('stale', 'Karim'), snapshot: snap(ws - 7, 100, 'trained') },
      { ...p('empty', 'Maya'), snapshot: null },
      { ...p('fresh', 'Sami'), snapshot: snap(ws, 50, 'slacking', false) },
    ]
    const board = buildLeaderboard(ws, me, snap(ws, 75, 'on_track'), friends)
    const karim = board.find((e) => e.person.id === 'stale')!
    expect(karim).toMatchObject({ score: 0, badge: 'not_synced', syncedThisWeek: false, streakWeeks: 0, prs: [] })
    expect(board.find((e) => e.person.id === 'empty')!.badge).toBe('not_synced')
    expect(board.find((e) => e.person.id === 'fresh')!.prs).toEqual([])
    expect(board.map((e) => e.person.id)).toEqual(['me', 'fresh', 'empty', 'stale'])
  })
})

describe('normalizeUsername (mirrors worker/src/validate.ts)', () => {
  it('trims, strips one @ and lowercases', () => {
    expect(normalizeUsername('  @Tariq_99 ')).toBe('tariq_99')
  })
  it('rejects bad length, chars, dots and reserved names', () => {
    for (const bad of ['ab', 'a'.repeat(21), 'has space', 'ta-riq', '.abc', 'abc.', 'a..b', 'admin', 'ForgeQuestX', '@@abc', 'Kelvin']) {
      expect(normalizeUsername(bad)).toBeNull()
    }
  })
})
