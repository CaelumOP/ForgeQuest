// Backup file, same JSON as Android's BackupRepository.kt (BackupFile v2, kotlinx.serialization with
// ignoreUnknownKeys = true and encodeDefaults = true): fields in Kotlin declaration order, every
// default written out, enums as their names, nullable fields as null.
import {
  db, type BadgeRow, type ChatRow, type DayNoteRow, type ExerciseRow, type FoodRow, type MealRow,
  type PlanDayRow, type PlanItemRow, type PlanRow, type ReadinessDayRow, type SessionRow, type SetRow,
  type WeightRow,
} from './db';
import { fromBackupSettings, loadSettings, toBackupSettings, updateSettings } from './settings';
import { SCANNED_NO_PHOTO } from './food';

const currentBackupSettings = async (): Promise<BackupSettings> => toBackupSettings(await loadSettings());

/** The person's settings (never the AI key). Same fields and defaults as Kotlin BackupSettings. */
export interface BackupSettings {
  name: string; bodyType: string; kcalGoal: number; proteinGoal: number; weeklyTargetOverride: number;
  handLengthCm: number; palmWidthCm: number; coachNotes: string; theme: string; hapticsEnabled: boolean;
  unit: string; reminderHour: number; heightCm: number; birthYear: number; trackCarbs: boolean;
  carbsGoal: number; proteinPerKg: number; fatSharePct: number; equipment: string; bodyFatPct: number;
  nutritionGoal: string; goalSetDay: number; debriefEnabled: boolean;
  dayActivity: string; cutPace: string; gainPace: string; targetWeightKg: number; targetSetDay: number;
}

export const DEFAULT_BACKUP_SETTINGS: Readonly<BackupSettings> = Object.freeze({
  name: '', bodyType: 'MALE', kcalGoal: 2200, proteinGoal: 140, weeklyTargetOverride: 0,
  handLengthCm: 0, palmWidthCm: 0, coachNotes: '', theme: 'system', hapticsEnabled: true,
  unit: 'kg', reminderHour: -1, heightCm: 0, birthYear: 0, trackCarbs: false,
  carbsGoal: 250, proteinPerKg: 1.8, fatSharePct: 25, equipment: '', bodyFatPct: 0,
  nutritionGoal: 'MAINTAIN', goalSetDay: 0, debriefEnabled: true,
  dayActivity: 'SITTING', cutPace: 'STANDARD', gainPace: 'STANDARD', targetWeightKg: 0, targetSetDay: 0,
});

export interface BackupFile {
  version: number;
  createdAt: number;
  exercises: ExerciseRow[];
  plans: PlanRow[];
  planDays: PlanDayRow[];
  planItems: PlanItemRow[];
  sessions: SessionRow[];
  sets: SetRow[];
  foods: FoodRow[];
  meals: MealRow[];
  weights: WeightRow[];
  chat: ChatRow[];
  badges: BadgeRow[];
  settings: BackupSettings | null;
  dayNotes: DayNoteRow[];
  readinessDays: ReadinessDayRow[];
}

export class BackupUnreadableError extends Error {
  constructor(why: string) { super(`Backup unreadable: ${why}`); this.name = 'BackupUnreadableError'; }
}

// ---------------------------------------------------------------- Field specs (Kotlin order + defaults)

const REQ = Symbol('required');
type Spec = Record<string, unknown>; // value = default, or REQ when Kotlin has no default

const SPECS = {
  exercises: { id: REQ, name: REQ, kind: REQ, primary: REQ, secondary: REQ, videoUrl: null, custom: false },
  plans: { id: 0, name: REQ, description: '', source: REQ, active: REQ, createdAt: REQ },
  planDays: { id: 0, planId: REQ, dayOfWeek: REQ, title: REQ, isRest: REQ, note: '' },
  planItems: {
    id: 0, dayId: REQ, position: REQ, exerciseId: REQ, block: REQ, circuit: REQ, sets: REQ,
    repsMin: null, repsMax: null, seconds: null, weightKg: null, restSeconds: 60, note: '',
  },
  sessions: {
    id: 0, planDayId: REQ, title: REQ, epochDay: REQ, startedAt: REQ, finishedAt: null, note: '', debrief: '',
  },
  sets: {
    id: 0, sessionId: REQ, exerciseId: REQ, itemPosition: REQ, block: REQ, circuit: REQ, setIndex: REQ,
    targetLabel: REQ, reps: null, seconds: null, weightKg: null, done: false, isPr: false, restSeconds: 60,
    note: '', effort: '',
  },
  foods: {
    id: 0, name: REQ, category: REQ, kcal: REQ, protein: REQ, serving: '', carbs: 0, photoPath: null,
    pinned: false, createdAt: REQ, place: '', parts: [],
  },
  meals: {
    id: 0, epochDay: REQ, loggedAt: REQ, name: REQ, category: REQ, kcal: REQ, protein: REQ, quantity: 1,
    carbs: 0, photoPath: null, foodId: null,
  },
  weights: { epochDay: REQ, kg: REQ, loggedAt: REQ },
  chat: {
    id: 0, role: REQ, text: REQ, planJson: null, planApplied: false, kind: 'chat', createdAt: REQ, detail: '',
  },
  badges: { id: REQ, unlockedAt: REQ, seen: false },
  dayNotes: { epochDay: REQ, tag: REQ, note: '', updatedAt: REQ },
  readinessDays: {
    epochDay: REQ, recovery: REQ, sleepPerf: REQ, sleepMin: REQ, strain: REQ, hrv: REQ, rhr: REQ,
    feltGreat: REQ, updatedAt: REQ,
  },
} satisfies Record<string, Spec>;

type ListKey = keyof typeof SPECS;
const REQUIRED_LISTS: ListKey[] = [
  'exercises', 'plans', 'planDays', 'planItems', 'sessions', 'sets', 'foods', 'meals', 'weights', 'chat', 'badges',
];

const isObj = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);

/** Copies `src` into a new object in spec order: known keys only, defaults filled in. */
function shape(spec: Spec, src: unknown, where: string): Record<string, unknown> {
  if (!isObj(src)) throw new BackupUnreadableError(`${where} is not an object`);
  const out: Record<string, unknown> = {};
  for (const [key, def] of Object.entries(spec)) {
    const v = src[key];
    if (v === undefined) {
      if (def === REQ) throw new BackupUnreadableError(`${where}.${key} is missing`);
      out[key] = Array.isArray(def) ? [] : def; // a fresh list per row, never one shared array
    } else {
      out[key] = v;
    }
  }
  return out;
}

function shapeList<K extends ListKey>(key: K, src: unknown): unknown[] {
  if (src === undefined) {
    if (REQUIRED_LISTS.includes(key)) throw new BackupUnreadableError(`${key} is missing`);
    return [];
  }
  if (!Array.isArray(src)) throw new BackupUnreadableError(`${key} is not a list`);
  return src.map((row, i) => shape(SPECS[key], row, `${key}[${i}]`));
}

function shapeFile(src: unknown): BackupFile {
  if (!isObj(src)) throw new BackupUnreadableError('not an object');
  if (typeof src.createdAt !== 'number') throw new BackupUnreadableError('createdAt is missing');
  const lists = {} as Record<ListKey, unknown[]>;
  for (const key of Object.keys(SPECS) as ListKey[]) lists[key] = shapeList(key, src[key]);
  const settings = src.settings == null
    ? null
    : (shape(DEFAULT_BACKUP_SETTINGS, src.settings, 'settings') as unknown as BackupSettings);
  // Built key by key so the JSON keeps Kotlin's declaration order.
  return {
    version: typeof src.version === 'number' ? src.version : 2,
    createdAt: src.createdAt,
    exercises: lists.exercises as ExerciseRow[],
    plans: lists.plans as PlanRow[],
    planDays: lists.planDays as PlanDayRow[],
    planItems: lists.planItems as PlanItemRow[],
    sessions: lists.sessions as SessionRow[],
    sets: lists.sets as SetRow[],
    foods: lists.foods as FoodRow[],
    meals: lists.meals as MealRow[],
    weights: lists.weights as WeightRow[],
    chat: lists.chat as ChatRow[],
    badges: lists.badges as BadgeRow[],
    settings,
    dayNotes: lists.dayNotes as DayNoteRow[],
    readinessDays: lists.readinessDays as ReadinessDayRow[],
  };
}

// ---------------------------------------------------------------- Build / encode / decode

const TABLES = [
  'exercises', 'plans', 'planDays', 'planItems', 'sessions', 'sets', 'foods', 'meals', 'weights', 'chat',
  'badges', 'dayNotes', 'readinessDays',
] as const;

async function readAll(settings: BackupSettings | null): Promise<BackupFile> {
  const rows = await Promise.all(TABLES.map((t) => db.table(t).toArray()));
  const raw: Record<string, unknown> = { version: 2, createdAt: Date.now(), settings };
  TABLES.forEach((t, i) => { raw[t] = rows[i]; });
  return shapeFile(raw);
}

/** Everything on this device plus `settings` (default: the saved settings, never the AI keys), as a version 2 backup. */
export async function buildBackup(settings?: BackupSettings | null): Promise<BackupFile> {
  const s = settings === undefined ? await currentBackupSettings() : settings;
  return db.transaction('r', TABLES, () => readAll(s));
}

export function encodeBackup(file: BackupFile): string {
  return JSON.stringify(shapeFile(file));
}

/** Parses a backup from either app. Unknown keys are ignored; v1 files (no settings, no dayNotes) load. */
export function decodeBackup(text: string): BackupFile {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new BackupUnreadableError('not JSON');
  }
  return shapeFile(parsed);
}

/** What a person reads before restoring (Android BackupFile.summary, left for the UI to word). */
export function backupCounts(file: BackupFile) {
  return {
    workouts: file.sessions.filter((s) => s.finishedAt != null).length,
    meals: file.meals.length,
    weights: file.weights.length,
    createdAt: file.createdAt,
  };
}

// ---------------------------------------------------------------- Apply

const KEEP_SAFETY_COPIES = 3;

/**
 * Replaces everything on this device with `file`, in one transaction. First keeps a safety copy of
 * what is here now (latest 3), unless there are no sessions, meals or weights. Photos are dropped
 * (the web never stores them); readiness history stays when the file has none. The file's settings
 * are saved through settings.ts (keys untouched) and the locked weekly streak targets are cleared, so
 * they are locked again from the restored history (decision 11). Returns the file's settings (null
 * when it has none, e.g. a v1 file).
 */
export async function applyBackup(
  file: BackupFile,
  currentSettings?: BackupSettings | null,
): Promise<BackupSettings | null> {
  const f = shapeFile(file);
  const before = currentSettings === undefined ? await currentBackupSettings() : currentSettings;
  await db.transaction('rw', [...TABLES, 'safetyCopies', 'kv'], async () => {
    const current = await readAll(before);
    if (current.sessions.length || current.meals.length || current.weights.length) {
      await db.safetyCopies.add({ createdAt: current.createdAt, json: encodeBackup(current) });
      const all = await db.safetyCopies.orderBy('createdAt').reverse().primaryKeys();
      if (all.length > KEEP_SAFETY_COPIES) await db.safetyCopies.bulkDelete(all.slice(KEEP_SAFETY_COPIES));
    }
    const rows: Record<(typeof TABLES)[number], unknown[]> = {
      ...f,
      foods: f.foods.map((r) => ({ ...r, photoPath: null })),
      // Photos never come along; a scanned meal keeps a marker so the "Snap & log" badge still counts it.
      meals: f.meals.map((r) => ({ ...r, photoPath: r.photoPath == null ? null : SCANNED_NO_PHOTO })),
    };
    for (const t of TABLES) {
      if (t === 'readinessDays' && f.readinessDays.length === 0) continue;
      await db.table(t).clear();
      await db.table(t).bulkAdd(rows[t]);
    }
    await db.kv.delete('weekTargets');
  });
  const restored = f.settings;
  if (restored) await updateSettings((s) => fromBackupSettings(restored, s));
  return restored;
}

/** Pre-restore copies, newest first. */
export function listSafetyCopies() {
  return db.safetyCopies.orderBy('createdAt').reverse().toArray();
}
