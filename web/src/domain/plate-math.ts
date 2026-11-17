import { CATALOG_PROFILES } from './exercise-swap-engine';
import type { ExerciseEntity } from './entities';
import { fromKg, type WeightUnit } from './units';

export const DEFAULT_KG_PLATES = [25, 20, 15, 10, 5, 2.5, 1.25] as const;
const DEFAULT_LB_PLATES = [45, 35, 25, 10, 5, 2.5] as const;
const KG_TO_LB_PLATE: readonly [number, number][] = [
  [25, 45],
  [20, 45],
  [15, 35],
  [10, 25],
  [5, 10],
  [2.5, 5],
  [1.25, 2.5],
];

export type PlateCount = { weight: number; count: number };

export type PlateBreakdown = {
  unit: WeightUnit;
  barWeight: number;
  targetWeight: number;
  loadedWeight: number;
  perSide: PlateCount[];
  remainder: number;
  belowBar: boolean;
  barOnly: boolean;
};

const round2 = (v: number): number => Math.round(v * 100) / 100;
const roundHalf = (v: number): number => Math.round(v * 2) / 2;

/** True when `exercise` uses a barbell (persisted equipment field or swap-engine profile). */
export function isBarbellExercise(
  exercise: Pick<ExerciseEntity, 'id' | 'equipment'> | null | undefined,
): boolean {
  if (!exercise) return false;
  const eq = (exercise.equipment ?? '').trim();
  if (eq) {
    return eq.split(',').some((token) => token.trim().toUpperCase() === 'BARBELL');
  }
  const profile = CATALOG_PROFILES.get(exercise.id);
  if (!profile) return false;
  return profile.equipment === 'BARBELL' || (profile.requires?.includes('BARBELL') ?? false);
}

/** Standard 20/15 kg bars map cleanly to 45/35 lb bars when displaying pounds. */
export function barWeightInUnit(barWeightKg: number, unit: WeightUnit): number {
  const safeKg = Math.max(0, barWeightKg);
  if (unit === 'KG') return round2(safeKg);
  if (safeKg >= 19.5 && safeKg <= 20.5) return 45;
  if (safeKg >= 14.5 && safeKg <= 15.5) return 35;
  return roundHalf(fromKg('LB', safeKg));
}

export function platesForUnit(plateInventoryKg: readonly number[], unit: WeightUnit): number[] {
  const positive = plateInventoryKg.filter((p) => Number.isFinite(p) && p > 0);
  if (positive.length === 0) return [];
  if (unit === 'KG') {
    return [...new Set(positive.map(round2))].sort((a, b) => b - a);
  }
  const mapped: number[] = [];
  for (const kg of positive) {
    const hit = KG_TO_LB_PLATE.find(([k]) => Math.abs(k - kg) < 0.05);
    if (hit) mapped.push(hit[1]);
  }
  const list = mapped.length > 0 ? mapped : [...DEFAULT_LB_PLATES];
  return [...new Set(list)].sort((a, b) => b - a);
}

export function calculatePlates(
  targetKg: number,
  unit: WeightUnit,
  barWeightKg = 20,
  plateInventoryKg: readonly number[] = DEFAULT_KG_PLATES,
): PlateBreakdown {
  const bar = barWeightInUnit(barWeightKg, unit);
  const rawDisplay = fromKg(unit, targetKg);
  const target = unit === 'KG' ? round2(rawDisplay) : roundHalf(rawDisplay);
  if (targetKg <= 0 || target < bar - 0.01) {
    return {
      unit,
      barWeight: bar,
      targetWeight: Math.max(0, target),
      loadedWeight: 0,
      perSide: [],
      remainder: 0,
      belowBar: true,
      barOnly: false,
    };
  }
  const plates = platesForUnit(plateInventoryKg, unit);
  let sideLeft = Math.max(0, (target - bar) / 2);
  const perSide: PlateCount[] = [];
  for (const plate of plates) {
    if (plate <= 0) continue;
    const count = Math.floor((sideLeft + 1e-3) / plate);
    if (count > 0) {
      perSide.push({ weight: plate, count });
      sideLeft = Math.max(0, sideLeft - count * plate);
    }
  }
  const sideLoaded = perSide.reduce((sum, p) => sum + p.weight * p.count, 0);
  const loaded = round2(bar + 2 * sideLoaded);
  const remainder = round2(Math.max(0, target - loaded));
  const barOnly = perSide.length === 0 && remainder < 0.01;
  return {
    unit,
    barWeight: bar,
    targetWeight: target,
    loadedWeight: loaded,
    perSide,
    remainder,
    belowBar: false,
    barOnly,
  };
}

export function formatPlateNumber(weight: number): string {
  const r = round2(weight);
  const whole = Math.round(r);
  if (Math.abs(r - whole) < 0.005) return String(whole);
  const tenths = Math.round(r * 10);
  if (Math.abs(r - tenths / 10) < 0.005) return r.toFixed(1);
  return r.toFixed(2);
}

export function formatPlatesSummary(perSide: readonly PlateCount[]): string {
  return perSide
    .map((p) => (p.count > 1 ? `${p.count}×${formatPlateNumber(p.weight)}` : formatPlateNumber(p.weight)))
    .join(' + ');
}
