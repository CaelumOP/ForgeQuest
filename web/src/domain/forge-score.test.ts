import { describe, expect, it } from 'vitest'
import vectors from './forge-score.vectors.json'
import { buildLeaderboard, computeForgeWeek, normalizeUsername, type Friend, type Person, type Snapshot } from './forge-score'

describe('computeForgeWeek', () => {
  for (const v of vectors.cases) {
    it(v.name, () => {
      const actual = computeForgeWeek(
        v.today,
        new Set(v.scheduled),
        new Set(v.trained),
        new Set(v.protein),
      )
      expect(actual).toEqual(v.expect)
    })
  }
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

  it('includes me highlighted and sorts by score desc then name asc', () => {
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
    expect(board.map((e) => e.person.id)).toEqual(['me', 'fresh', 'stale', 'empty'])
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
