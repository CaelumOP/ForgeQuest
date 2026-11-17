import type { BadgeStats } from './badges';

/**
 * How far along a locked counting badge is as `[done, needed]`, for `"X / Y"` on the badges screen;
 * `null` for one-off skill and strength badges.
 */
export function badgeProgress(id: string, s: BadgeStats): [number, number] | null {
  const tail = id.split('_').pop();
  const n = tail ? Number(tail) : NaN;
  if (!Number.isInteger(n) || n <= 0) return null;
  let done: number;
  if (id.startsWith('workouts_')) done = s.workouts;
  else if (id.startsWith('streak_')) done = s.bestStreakWeeks;
  else if (id.startsWith('protein_')) done = s.proteinGoalDaysInARow;
  else return null;
  return [Math.min(done, n), n];
}
