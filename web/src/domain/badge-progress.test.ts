import { describe, expect, it } from 'vitest';
import { badgeStats } from './badges';
import { badgeProgress } from './badge-progress';

describe('badgeProgress', () => {
  it('returns capped [done, needed] for counting badges', () => {
    const stats = badgeStats({ workouts: 8, bestStreakWeeks: 4, proteinGoalDaysInARow: 10 });
    expect(badgeProgress('workouts_10', stats)).toEqual([8, 10]);
    expect(badgeProgress('streak_4', stats)).toEqual([4, 4]);
    expect(badgeProgress('protein_7', stats)).toEqual([7, 7]);
    expect(badgeProgress('protein_30', stats)).toEqual([10, 30]);
  });

  it('returns null for one-off badges', () => {
    const stats = badgeStats({ workouts: 5 });
    expect(badgeProgress('first_workout', stats)).toBeNull();
    expect(badgeProgress('skill_pistol', stats)).toBeNull();
    expect(badgeProgress('str_pull_10', stats)).toBeNull();
  });
});
