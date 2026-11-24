import type { ExerciseEntity } from './entities';
import { profileFor, type MovementPattern } from './exercise-swap-engine';
import { fromKg, loadStep, toKg, type WeightUnit } from './units';

const f = Math.fround;

export type RampSet = { weightKg: number; reps: number };

const COMPOUND_PATTERNS = new Set<MovementPattern>([
  'HORIZONTAL_PUSH',
  'DIP_PUSH',
  'VERTICAL_PUSH',
  'VERTICAL_PULL',
  'HORIZONTAL_PULL',
  'UPPER_BACK_PULL',
  'SQUAT_BILATERAL',
  'SQUAT_UNILATERAL',
  'HIP_HINGE',
]);

const RAMP_STEPS: readonly [number, number][] = [
  [0.5, 8],
  [0.7, 5],
  [0.85, 3],
];

/** True for weighted barbell compound lifts (squat, bench, deadlift, OHP, barbell row, hip thrust, good morning). */
export function isHeavyBarbellCompound(ex: ExerciseEntity | null | undefined): boolean {
  if (!ex || ex.kind !== 'WEIGHTED') return false;
  const profile = profileFor(ex);
  const usesBarbell =
    profile.equipment === 'BARBELL' ||
    Boolean(profile.requires?.includes('BARBELL')) ||
    (ex.equipment ?? '').split(',').some((t) => t.trim().toUpperCase() === 'BARBELL');
  return usesBarbell && COMPOUND_PATTERNS.has(profile.pattern);
}

const snap = (value: number, step: number): number => f(Math.round(f(value / step)) * step);

/** Ramps below working weight: barbell floor + empty bar at 3x bar; other lifts floor at one load step. */
export function generateWarmupRamp(
  workingWeightKg: number,
  unit: WeightUnit = 'KG',
  barWeightKg = 20,
  isBarbell = true,
): RampSet[] {
  if (!Number.isFinite(workingWeightKg) || workingWeightKg <= 0) return [];
  const step = loadStep(unit);
  const workingDisplay = fromKg(unit, workingWeightKg);
  const rawBar = Number.isFinite(barWeightKg) && barWeightKg > 0 ? barWeightKg : 20;
  const barDisplay = Math.max(snap(fromKg(unit, rawBar), step), step);
  const floorDisplay = isBarbell ? barDisplay : step;
  if (workingDisplay <= floorDisplay + 0.01) return [];

  const out: RampSet[] = [];
  let lastDisplay = -1;
  if (isBarbell && workingDisplay >= 3 * barDisplay - 0.01) {
    lastDisplay = barDisplay;
    out.push({ weightKg: toKg(unit, barDisplay), reps: 10 });
  }
  for (const [pct, reps] of RAMP_STEPS) {
    const targetDisplay = Math.max(snap(f(workingDisplay * pct), step), floorDisplay);
    if (targetDisplay >= workingDisplay - 0.01) continue;
    if (Math.abs(targetDisplay - lastDisplay) < 0.01) continue;
    lastDisplay = targetDisplay;
    out.push({ weightKg: toKg(unit, targetDisplay), reps });
  }
  // Near the empty bar, two preparation sets share the only load below working weight.
  if (out.length === 1) out.push({ weightKg: out[0].weightKg, reps: 5 });
  return out;
}
