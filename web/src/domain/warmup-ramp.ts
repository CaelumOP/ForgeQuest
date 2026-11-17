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

/**
 * Generates 2–3 ramp sets below `workingWeightKg`, rounded to `unit` plate steps (2.5 kg / 5 lb)
 * and floored at `barWeightKg` (default 20 kg). Returns [] when working weight is missing or at/below the bar.
 */
export function generateWarmupRamp(
  workingWeightKg: number,
  unit: WeightUnit = 'KG',
  barWeightKg = 20,
): RampSet[] {
  if (!Number.isFinite(workingWeightKg) || workingWeightKg <= 0) return [];
  const step = loadStep(unit);
  const workingDisplay = fromKg(unit, workingWeightKg);
  const rawBar = Number.isFinite(barWeightKg) && barWeightKg > 0 ? barWeightKg : 20;
  const barDisplay = Math.max(snap(fromKg(unit, rawBar), step), step);
  if (workingDisplay <= barDisplay + 0.01) return [];

  const out: RampSet[] = [];
  let lastDisplay = -1;
  for (const [pct, reps] of RAMP_STEPS) {
    const targetDisplay = Math.max(snap(f(workingDisplay * pct), step), barDisplay);
    if (targetDisplay >= workingDisplay - 0.01) continue;
    if (Math.abs(targetDisplay - lastDisplay) < 0.01) continue;
    lastDisplay = targetDisplay;
    out.push({ weightKg: toKg(unit, targetDisplay), reps });
  }
  // Near the empty bar, two preparation sets share the only load below working weight.
  if (out.length === 1) out.push({ weightKg: out[0].weightKg, reps: 5 });
  return out;
}
