import { STRETCH_IDS } from '../data/seed/exercise-catalog';
import type { ExerciseEntity } from './entities';
import {
  ALL_ACTIVE_EQUIPMENT, expandLegacyEquipment, profileFits, profileFor, type BiomechanicalProfile,
  type Equipment, type MovementPattern,
} from './exercise-swap-engine';
import { TIERS, tierOf } from './exercise-tiers';
import type { StalledLift } from './trends';

export const VARIATION_COOLDOWN_DAYS = 56;

const CORE_PATTERNS: Record<string, readonly MovementPattern[]> = {
  H_PRESS: ['HORIZONTAL_PUSH'],
  H_PULL: ['HORIZONTAL_PULL'],
  V_PRESS: ['VERTICAL_PUSH'],
  V_PULL: ['VERTICAL_PULL'],
  SQUAT: ['SQUAT_BILATERAL', 'SQUAT_UNILATERAL'],
  HINGE: ['HIP_HINGE'],
};

function corePatternsFor(pattern: MovementPattern): readonly MovementPattern[] | null {
  for (const list of Object.values(CORE_PATTERNS)) {
    if (list.includes(pattern)) return list;
  }
  return null;
}

const ISOLATION = new Set([
  'cable_fly', 'pec_deck', 'db_fly',
  'cable_crossover_high_to_low', 'cable_crossover_low_to_high', 'cable_glute_kickback',
]);

export type VariationSuggestion = { from: StalledLift; to: ExerciseEntity };

export function nextVariation(
  stalled: StalledLift,
  library: readonly ExerciseEntity[],
  equipment: ReadonlySet<Equipment>,
  planIds: ReadonlySet<string>,
  recentlyCycled: ReadonlySet<string>,
): VariationSuggestion | null {
  const source = library.find((e) => e.id === stalled.exerciseId);
  if (!source) return null;
  const profile = profileFor(source);
  const core = corePatternsFor(profile.pattern);
  if (!core) return null;
  const tier = tierOf(source);
  const tierIdx = tier ? TIERS.indexOf(tier) : -1;
  const expanded = expandLegacyEquipment(equipment);
  const allowed = new Set<Equipment>([
    ...(expanded.size > 0 ? expanded : ALL_ACTIVE_EQUIPMENT),
    'BODYWEIGHT',
  ]);
  const curated = (id: string) => {
    const i = profile.preferredSwaps.indexOf(id);
    return i < 0 ? Number.MAX_SAFE_INTEGER : i;
  };

  const candidates: Array<{ ex: ExerciseEntity; prof: BiomechanicalProfile }> = [];
  for (const ex of library) {
    if (ex.id === source.id || STRETCH_IDS.has(ex.id) || ISOLATION.has(ex.id)) continue;
    if (planIds.has(ex.id) || recentlyCycled.has(ex.id)) continue;
    if (ex.kind !== source.kind) continue;
    const p = profileFor(ex);
    if (!core.includes(p.pattern) || !profileFits(p, allowed)) continue;
    const other = tierOf(ex);
    if (tierIdx >= 0 && other !== null && Math.abs(TIERS.indexOf(other) - tierIdx) > 1) continue;
    candidates.push({ ex, prof: p });
  }

  candidates.sort((a, b) => {
    const aDiff = a.prof.angle !== profile.angle || a.prof.equipment !== profile.equipment ? 0 : 1;
    const bDiff = b.prof.angle !== profile.angle || b.prof.equipment !== profile.equipment ? 0 : 1;
    if (aDiff !== bDiff) return aDiff - bDiff;
    const ca = curated(a.ex.id);
    const cb = curated(b.ex.id);
    if (ca !== cb) return ca - cb;
    return a.ex.id < b.ex.id ? -1 : a.ex.id > b.ex.id ? 1 : 0;
  });

  return candidates.length ? { from: stalled, to: candidates[0].ex } : null;
}

function parseHistory(history: string): Array<[string, number]> {
  const out: Array<[string, number]> = [];
  for (const entry of history.split(';')) {
    const idx = entry.lastIndexOf(':');
    if (idx <= 0) continue;
    const id = entry.slice(0, idx);
    const day = Number(entry.slice(idx + 1));
    if (id.trim() && Number.isInteger(day)) out.push([id, day]);
  }
  return out;
}

export function recentlyCycledIds(history: string, today: number): Set<string> {
  const out = new Set<string>();
  for (const [id, day] of parseHistory(history)) {
    if (today - day < VARIATION_COOLDOWN_DAYS) out.add(id);
  }
  return out;
}

export function recordCycle(history: string, exerciseId: string, today: number): string {
  const kept = parseHistory(history).filter(
    ([id, day]) => id !== exerciseId && today - day < VARIATION_COOLDOWN_DAYS,
  );
  kept.push([exerciseId, today]);
  return kept.map(([id, day]) => `${id}:${day}`).join(';');
}
