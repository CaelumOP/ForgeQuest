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

  const unlocked = (stats: Parameters<typeof badgeStats>[0]) =>
    new Set(BADGES.filter((b) => b.unlocked(badgeStats(stats))).map((b) => b.id))

  it('pullUpRepBadgesCountChinUpsAndWeightedVariants', () => {
    for (const id of ['pull_up', 'chin_up', 'weighted_pull_up', 'weighted_chin_up']) {
      const got = unlocked({ bestReps: { [id]: 10 } })
      expect(got.has('str_pull_1') && got.has('str_pull_10')).toBe(true)
    }
    expect(unlocked({ bestReps: { australian_pull_up: 20 } }).has('str_pull_1')).toBe(false)
  })

  it('weightedPullUpBadgeCountsAddedWeightOnAnyPullUp', () => {
    for (const id of ['weighted_pull_up', 'weighted_chin_up', 'pull_up', 'chin_up']) {
      expect(unlocked({ bestWeight: { [id]: 20 } }).has('str_weighted_pull_20')).toBe(true)
    }
    expect(unlocked({ bestWeight: { chin_up: 19 } }).has('str_weighted_pull_20')).toBe(false)
  })

  it('dipBadgesCountBothDips', () => {
    expect(unlocked({ bestReps: { weighted_dip: 15 } }).has('str_dip_15')).toBe(true)
    expect(unlocked({ bestReps: { dip: 15 } }).has('str_dip_15')).toBe(true)
    expect(unlocked({ bestWeight: { dip: 30 } }).has('str_weighted_dip_30')).toBe(true)
    expect(unlocked({ bestWeight: { weighted_dip: 30 } }).has('str_weighted_dip_30')).toBe(true)
    expect(unlocked({ bestReps: { bent_leg_bench_dip: 30 } }).has('str_dip_15')).toBe(false)
  })

  it('twentyFiveWorkoutsBadgeSitsBetweenTenAndFifty', () => {
    expect(unlocked({ workouts: 24 }).has('workouts_25')).toBe(false)
    const got = unlocked({ workouts: 25 })
    expect(got.has('workouts_25') && got.has('workouts_10') && !got.has('workouts_50')).toBe(true)
    const ids = BADGES.map((b) => b.id)
    expect(ids.indexOf('workouts_25')).toBe(ids.indexOf('workouts_10') + 1)
    expect(ids.indexOf('workouts_50')).toBe(ids.indexOf('workouts_25') + 1)
  })

  it('badgeIdsAreUnique', () => {
    expect(new Set(BADGES.map((b) => b.id)).size).toBe(BADGES.length)
  })
})
