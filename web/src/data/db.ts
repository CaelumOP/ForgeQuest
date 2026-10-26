// The on-phone database. One Dexie table per Room entity in
// app/src/main/java/com/forgequest/app/data/db/Entities.kt (schema v7), with the Room field
// names unchanged so a backup file moves between the Android and web apps as is.
import Dexie, { type Table } from 'dexie';
import type {
  ExerciseEntity, PlanItemEntity, ReadinessDayEntity, SessionEntity, SetEntity,
} from '../domain/entities';

export type FoodCategory = 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACKS' | 'DRINKS';

// Auto-generated ids are optional on rows that are not saved yet (Room uses 0 for that).

// Row types for entities the domain also reads: the domain type is the single definition.
export type ExerciseRow = Required<ExerciseEntity>;
export interface PlanRow {
  id?: number; name: string; description: string; source: string; active: boolean; createdAt: number;
}
export interface PlanDayRow {
  id?: number; planId: number; dayOfWeek: number; title: string; isRest: boolean; note: string;
}
export type PlanItemRow = Omit<PlanItemEntity, 'id'> & { id?: number };
export type SessionRow = Omit<SessionEntity, 'id'> & { id?: number };
export type SetRow = Omit<SetEntity, 'id'> & { id?: number };
export interface FoodRow {
  id?: number; name: string; category: FoodCategory; kcal: number; protein: number; serving: string;
  carbs: number; photoPath: string | null; pinned: boolean; createdAt: number;
  /** Where it's from ("كفتيريا افراح وتهاني"); missing on foods saved before restaurants existed. */
  place?: string;
  /** What it's made of, each part on or off; when set, kcal/protein/carbs are the "on" parts' totals. */
  parts?: FoodPart[];
}
/** One part of a saved food (the bread, the chicken, the ketchup). Unticked parts stay, switched off, for next time. */
export interface FoodPart { name: string; grams: number; kcal: number; protein: number; carbs: number; on: boolean }
export interface MealRow {
  id?: number; epochDay: number; loggedAt: number; name: string; category: FoodCategory; kcal: number;
  protein: number; quantity: number; carbs: number; photoPath: string | null; foodId: number | null;
}
export interface WeightRow { epochDay: number; kg: number; loggedAt: number }
export interface ChatRow {
  id?: number; role: string; text: string; planJson: string | null; planApplied: boolean; kind: string;
  createdAt: number; detail: string;
}
export interface BadgeRow { id: string; unlockedAt: number; seen: boolean }
export interface DayNoteRow { epochDay: number; tag: string; note: string; updatedAt: number }
export type ReadinessDayRow = ReadinessDayEntity;
/** A backup of this device taken right before a restore (web stand-in for Android's backups/ folder). */
export interface SafetyCopyRow { id?: number; createdAt: number; json: string }
/** Small key-value rows: 'settings', 'weekTargets' (web stand-in for DataStore and SharedPreferences). */
export interface KvRow { key: string; value: unknown }

export class ForgeQuestDb extends Dexie {
  exercises!: Table<ExerciseRow, string>;
  plans!: Table<PlanRow, number>;
  planDays!: Table<PlanDayRow, number>;
  planItems!: Table<PlanItemRow, number>;
  sessions!: Table<SessionRow, number>;
  sets!: Table<SetRow, number>;
  foods!: Table<FoodRow, number>;
  meals!: Table<MealRow, number>;
  weights!: Table<WeightRow, number>;
  chat!: Table<ChatRow, number>;
  badges!: Table<BadgeRow, string>;
  dayNotes!: Table<DayNoteRow, number>;
  readinessDays!: Table<ReadinessDayRow, number>;
  safetyCopies!: Table<SafetyCopyRow, number>;
  kv!: Table<KvRow, string>;

  constructor(name = 'forgequest') {
    super(name);
    // Booleans can't be IndexedDB keys, so `active`, `done`, `pinned` are filtered in memory.
    this.version(1).stores({
      exercises: 'id, name',
      plans: '++id, createdAt',
      planDays: '++id, planId',
      planItems: '++id, dayId, exerciseId',
      sessions: '++id, epochDay, startedAt, finishedAt, planDayId',
      sets: '++id, sessionId, exerciseId',
      foods: '++id, name',
      meals: '++id, epochDay, loggedAt, foodId',
      weights: 'epochDay',
      chat: '++id, createdAt',
      badges: 'id',
      dayNotes: 'epochDay',
      readinessDays: 'epochDay',
      safetyCopies: '++id, createdAt',
    });
    this.version(2).stores({ kv: 'key' });
  }
}

export const db = new ForgeQuestDb();

/** Asks the browser once to keep our data under storage pressure. Failures are ignored. */
export async function requestPersistence(): Promise<boolean> {
  try {
    return (await navigator.storage?.persist?.()) ?? false;
  } catch {
    return false;
  }
}

// Live-workout safety: each helper writes exactly one row in its own short implicit transaction,
// so a tap is on disk before the next one and nothing holds the database open.

type AutoId = { id?: number };
function forInsert<T extends AutoId>(row: T): T {
  if (row.id) return row;
  const { id: _drop, ...rest } = row; // Room's 0 means "new"; let IndexedDB pick the id.
  return rest as T;
}

export const putSet = (row: SetRow): Promise<number> => db.sets.put(forInsert(row));
export const putSession = (row: SessionRow): Promise<number> => db.sessions.put(forInsert(row));
export const putMeal = (row: MealRow): Promise<number> => db.meals.put(forInsert(row));
export const putChat = (row: ChatRow): Promise<number> => db.chat.put(forInsert(row));
export const putWeight = (row: WeightRow): Promise<number> => db.weights.put(row);
export const putDayNote = (row: DayNoteRow): Promise<number> => db.dayNotes.put(row);
export const putReadinessDay = (row: ReadinessDayRow): Promise<number> => db.readinessDays.put(row);
export const deleteSet = (id: number): Promise<void> => db.sets.delete(id);
