import { describe, expect, it } from 'vitest'
import { BADGES, badgeStats } from './badges'

describe('BadgesTest', () => {
  it('badgeRulesReadStats', () => {
    const stats = badgeStats({ workouts: 10, bestReps: { pull_up: 10 }, bestSeconds: { dead_hang: 60 } })
    const unlocked = new Set(BADGES.filter((b) => b.unlocked(stats)).map((b) => b.id))
    expect(unlocked.has('first_workout')).toBe(true)
    expect(unlocked.has('workouts_10')).toBe(true)
    expect(unlocked.has('str_pull_10')).toBe(true)
    expect(unlocked.has('skill_dead_hang')).toBe(true)
    expect(unlocked.has('workouts_50')).toBe(false)
  })

  it('badgeIdsAreUnique', () => {
    expect(new Set(BADGES.map((b) => b.id)).size).toBe(BADGES.length)
  })
})
