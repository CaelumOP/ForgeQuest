// The person's settings, same fields and defaults as Android SettingsRepository.kt (Settings), minus the
// Health Connect, Fitbit, Drive bridge and Drive doc fields the web doesn't have. Kept as one kv row
// 'settings'. The AI keys live here too, but never leave the phone: toBackupSettings() leaves them out.
import { db } from './db';
import type { BackupSettings } from './backup';
import { useLive } from './live';
import {
  inferEquipmentFromText, parseEquipmentSet, type Equipment,
} from '../domain/exercise-swap-engine';
import { NUTRITION_GOALS, type NutritionGoal } from '../domain/nutrition';
import { unitCode, unitFromCodeOrLabel, type WeightUnit } from '../domain/units';

export type BodyType = 'MALE' | 'FEMALE';

export interface Settings {
  onboarded: boolean;
  name: string;
  bodyType: BodyType;
  kcalGoal: number;
  proteinGoal: number;
  /** Planned sessions per week that count as "hit". 0 means: use the plan's training days. */
  weeklyTargetOverride: number;
  handLengthCm: number;
  palmWidthCm: number;
  fastModel: string;
  smartModel: string;
  liteModel: string;
  modelsCheckedAt: number;
  /** Which model the coach uses: "auto", "lite", "flash" or "pro". */
  coachModel: string;
  groqModel: string;
  lastCheckinWeek: number;
  driveEmail: string;
  lastBackupAt: number;
  /** Lasting facts for the coach (injuries, equipment, preferences), one per line. */
  coachNotes: string;
  theme: string;
  hapticsEnabled: boolean;
  /** "kg" or "lb" */
  unit: string;
  reminderHour: number;
  heightCm: number;
  birthYear: number;
  trackCarbs: boolean;
  carbsGoal: number;
  proteinPerKg: number;
  fatSharePct: number;
  /** Comma-separated Equipment names or a preset id; empty means not yet specified. */
  equipment: string;
  bodyFatPct: number;
  nutritionGoal: NutritionGoal;
  goalSetDay: number;
  debriefEnabled: boolean;
  /** Gemini key. Never in a backup, never logged. */
  apiKey: string;
  /** Optional Groq key. Never in a backup, never logged. */
  groqKey: string;
}

export const DEFAULT_SETTINGS: Readonly<Settings> = Object.freeze({
  onboarded: false, name: '', bodyType: 'MALE', kcalGoal: 2200, proteinGoal: 140, weeklyTargetOverride: 0,
  handLengthCm: 0, palmWidthCm: 0, fastModel: '', smartModel: '', liteModel: '', modelsCheckedAt: 0,
  coachModel: 'auto', groqModel: '', lastCheckinWeek: -1, driveEmail: '', lastBackupAt: 0, coachNotes: '',
  theme: 'system', hapticsEnabled: true, unit: 'kg', reminderHour: -1, heightCm: 0, birthYear: 0,
  trackCarbs: false, carbsGoal: 250, proteinPerKg: 1.8, fatSharePct: 25, equipment: '', bodyFatPct: 0,
  nutritionGoal: 'MAINTAIN', goalSetDay: 0, debriefEnabled: true, apiKey: '', groqKey: '',
} satisfies Settings);

const SETTINGS_KEY = 'settings';
const WEEK_TARGETS_KEY = 'weekTargets';

/** Same clean-up as Kotlin toSettings()/update(): unknown keys dropped, bad enums back to default, kcal ≥ 1200. */
function normalize(raw: unknown): Settings {
  const src = (typeof raw === 'object' && raw !== null ? raw : {}) as Record<string, unknown>;
  const out = { ...DEFAULT_SETTINGS } as Record<string, unknown>;
  for (const key of Object.keys(DEFAULT_SETTINGS)) {
    const v = src[key];
    if (v !== undefined && typeof v === typeof out[key]) out[key] = v;
  }
  const s = out as unknown as Settings;
  return {
    ...s,
    bodyType: s.bodyType === 'FEMALE' ? 'FEMALE' : 'MALE',
    kcalGoal: Math.max(s.kcalGoal, 1200),
    unit: unitCode(unitFromCodeOrLabel(s.unit)),
    nutritionGoal: NUTRITION_GOALS.includes(s.nutritionGoal) ? s.nutritionGoal : 'MAINTAIN',
    apiKey: s.apiKey.trim(),
    groqKey: s.groqKey.trim(),
  };
}

export async function loadSettings(): Promise<Settings> {
  return normalize((await db.kv.get(SETTINGS_KEY))?.value);
}

/** Reads, transforms and saves in one short transaction, like DataStore edit. Returns what was saved. */
export function updateSettings(fn: (s: Settings) => Settings): Promise<Settings> {
  return db.transaction('rw', db.kv, async () => {
    const next = normalize(fn(await loadSettings()));
    await db.kv.put({ key: SETTINGS_KEY, value: next });
    return next;
  });
}

/** Current settings; re-renders on every change. Defaults until the first read lands. */
export function useSettings(): Settings {
  return useLive(loadSettings, [], DEFAULT_SETTINGS as Settings);
}

export const weightUnit = (s: Settings): WeightUnit => unitFromCodeOrLabel(s.unit);

/** Available equipment from the explicit setting, else inferred from the coach notes; empty if unknown. */
export function configuredEquipment(s: Settings): Set<Equipment> {
  const set = parseEquipmentSet(s.equipment);
  return set.size ? set : (inferEquipmentFromText(s.coachNotes) ?? new Set());
}

// ---------------------------------------------------------------- Backup (Kotlin BackupSettings.from / applyTo)

export function toBackupSettings(s: Settings): BackupSettings {
  return {
    name: s.name, bodyType: s.bodyType, kcalGoal: Math.max(s.kcalGoal, 1200), proteinGoal: s.proteinGoal,
    weeklyTargetOverride: s.weeklyTargetOverride, handLengthCm: s.handLengthCm, palmWidthCm: s.palmWidthCm,
    coachNotes: s.coachNotes, theme: s.theme, hapticsEnabled: s.hapticsEnabled,
    unit: unitCode(unitFromCodeOrLabel(s.unit)), reminderHour: s.reminderHour, heightCm: s.heightCm,
    birthYear: s.birthYear, trackCarbs: s.trackCarbs, carbsGoal: s.carbsGoal, proteinPerKg: s.proteinPerKg,
    fatSharePct: s.fatSharePct, equipment: s.equipment, bodyFatPct: s.bodyFatPct, nutritionGoal: s.nutritionGoal,
    goalSetDay: s.goalSetDay, debriefEnabled: s.debriefEnabled,
  };
}

/** `s` with the restored fields applied; keys, models and onboarding stay as they are on this phone. */
export function fromBackupSettings(b: BackupSettings, s: Settings): Settings {
  return normalize({
    ...s,
    name: b.name,
    bodyType: b.bodyType === 'MALE' || b.bodyType === 'FEMALE' ? b.bodyType : s.bodyType,
    kcalGoal: Math.max(b.kcalGoal, 1200), proteinGoal: b.proteinGoal, weeklyTargetOverride: b.weeklyTargetOverride,
    handLengthCm: b.handLengthCm, palmWidthCm: b.palmWidthCm, coachNotes: b.coachNotes, theme: b.theme,
    hapticsEnabled: b.hapticsEnabled, unit: unitCode(unitFromCodeOrLabel(b.unit)), reminderHour: b.reminderHour,
    heightCm: b.heightCm, birthYear: b.birthYear, trackCarbs: b.trackCarbs, carbsGoal: b.carbsGoal,
    proteinPerKg: b.proteinPerKg, fatSharePct: b.fatSharePct, equipment: b.equipment, bodyFatPct: b.bodyFatPct,
    nutritionGoal: (NUTRITION_GOALS as readonly string[]).includes(b.nutritionGoal) ? b.nutritionGoal : s.nutritionGoal,
    goalSetDay: b.goalSetDay, debriefEnabled: b.debriefEnabled,
  });
}

// ---------------------------------------------------------------- Weekly streak targets of finished weeks

/** Saved targets by Monday's epoch day. A restore clears them. */
export async function weekTargets(): Promise<Map<number, number>> {
  const raw = (await db.kv.get(WEEK_TARGETS_KEY))?.value;
  const map = new Map<number, number>();
  if (typeof raw === 'object' && raw !== null) {
    for (const [week, target] of Object.entries(raw)) {
      const w = Number(week);
      if (Number.isInteger(w) && typeof target === 'number') map.set(w, target);
    }
  }
  return map;
}

/** Adds (or overwrites) the given weeks; other saved weeks stay. */
export function saveWeekTargets(targets: ReadonlyMap<number, number>): Promise<void> {
  return db.transaction('rw', db.kv, async () => {
    const merged = Object.fromEntries(await weekTargets());
    for (const [w, t] of targets) merged[w] = t;
    await db.kv.put({ key: WEEK_TARGETS_KEY, value: merged });
  });
}

export async function clearWeekTargets(): Promise<void> {
  await db.kv.delete(WEEK_TARGETS_KEY);
}
