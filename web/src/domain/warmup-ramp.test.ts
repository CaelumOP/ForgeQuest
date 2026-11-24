import { describe, expect, it } from 'vitest';
import { EXERCISE_CATALOG } from '../data/seed/exercise-catalog';
import { fromKg, toKg } from './units';
import { generateWarmupRamp, isHeavyBarbellCompound } from './warmup-ramp';

const byId = new Map(EXERCISE_CATALOG.map((e) => [e.id, e]));

describe('WarmupRamp', () => {
  it('detects heavy barbell compounds only (not dumbbell, isolation, bodyweight, or hold)', () => {
    expect(isHeavyBarbellCompound(byId.get('back_squat'))).toBe(true);
    expect(isHeavyBarbellCompound(byId.get('bench_press'))).toBe(true);
    expect(isHeavyBarbellCompound(byId.get('deadlift'))).toBe(true);
    expect(isHeavyBarbellCompound(byId.get('barbell_row'))).toBe(true);

    expect(isHeavyBarbellCompound(byId.get('barbell_curl'))).toBe(false);
    expect(isHeavyBarbellCompound(byId.get('db_bench_press'))).toBe(false);
    expect(isHeavyBarbellCompound(byId.get('burpee'))).toBe(false);
    expect(isHeavyBarbellCompound(byId.get('incline_hollow_plank'))).toBe(false);
    expect(isHeavyBarbellCompound(null)).toBe(false);
  });

  it('generates 50% / 70% / 85% ramp sets rounded to kg plate steps', () => {
    const sets = generateWarmupRamp(100, 'KG', 20);
    expect(sets).toHaveLength(4);
    expect(sets.map((s) => s.weightKg)).toEqual([20, 50, 70, 85]);
    expect(sets.map((s) => s.reps)).toEqual([10, 8, 5, 3]);
  });

  it('rounds to 5 lb plate steps and floors at bar weight in LB', () => {
    const sets = generateWarmupRamp(toKg('LB', 225), 'LB', 20);
    expect(sets).toHaveLength(4);
    expect(sets.map((s) => Math.round(fromKg('LB', s.weightKg)))).toEqual([45, 115, 160, 190]);
  });

  it('deduplicates ramp steps above the bar and returns empty at/below bar weight', () => {
    const light = generateWarmupRamp(40, 'KG', 20);
    // 50% of 40 = 20 (= bar), 70% = 27.5, 85% = 35
    expect(light.map((s) => s.weightKg)).toEqual([20, 27.5, 35]);

    const veryLight = generateWarmupRamp(25, 'KG', 20);
    // 50%=20, 70%=20(dedup), 85%=22.5 -> 2 sets strictly below 25
    expect(veryLight.map((s) => s.weightKg)).toEqual([20, 22.5]);

    expect(generateWarmupRamp(20, 'KG', 20)).toEqual([]);
    expect(generateWarmupRamp(0, 'KG', 20)).toEqual([]);
    expect(generateWarmupRamp(22.5, 'KG', 20).map((s) => s.weightKg)).toEqual([20, 20]);
    expect(generateWarmupRamp(Number.NaN)).toEqual([]);
  });
});

it('uses one load step as the non-barbell floor', () => {
  const ramp = generateWarmupRamp(20, 'KG', 20, false);
  expect(ramp.map((s) => s.weightKg)).toEqual([10, 15, 17.5]);
  expect(ramp.map((s) => s.reps)).toEqual([8, 5, 3]);
  expect(generateWarmupRamp(2.5, 'KG', 20, false)).toEqual([]);
});
it('prepends the empty bar at three times bar weight only', () => {
  const ramp = generateWarmupRamp(60, 'KG', 20, true);
  expect(ramp.map((s) => s.weightKg)).toEqual([20, 30, 42.5, 50]);
  expect(ramp.map((s) => s.reps)).toEqual([10, 8, 5, 3]);
  expect(generateWarmupRamp(40).map((s) => s.reps)).toEqual([8, 5, 3]);
  expect(generateWarmupRamp(15)).toEqual([]);
});
it('snaps barbell and dumbbell ramps in pounds', () => {
  const bar = generateWarmupRamp(toKg('LB', 135), 'LB', toKg('LB', 45), true);
  expect(bar.map((s) => fromKg('LB', s.weightKg))).toEqual([45, 70, 95, 115]);
  expect(bar.map((s) => s.reps)).toEqual([10, 8, 5, 3]);
  const db = generateWarmupRamp(toKg('LB', 30), 'LB', 20, false);
  expect(db.map((s) => fromKg('LB', s.weightKg))).toEqual([15, 20, 25]);
});
