// Port of Android data/bridge/MealInbox.kt: parses and buffers meals from the "ForgeQuest meals from Gemini"
// Google Doc until the user confirms or dismisses them on the Food tab. Kept in db.kv (outside backups).
import { db, type FoodCategory } from './db';
import { readDoc, upsertTextDoc } from './drive';
import { useLive } from './live';
import { epochDayOf, todayEpochDay } from '../domain/dates';

export interface InboxScanItem {
  name: string;
  grams: number;
  kcal: number;
  protein: number;
  carbs: number;
}

export interface GeminiMeal {
  mealId: string;
  date: string;
  time: string;
  dish: string;
  place: string;
  items: InboxScanItem[];
  handVisible: boolean;
  note: string;
}

export type OfferResult = 'ADDED' | 'UPDATED' | 'UNCHANGED' | 'ALREADY_LOGGED';

export const MEAL_DOC_NAME = 'ForgeQuest meals from Gemini';
export const MEAL_HEADER = 'FORGEQUEST-MEAL v1';

const KV_PENDING = 'meals.pending';
const KV_HANDLED = 'meals.handled';
const KV_DOC_MOD = 'meals.docModified';
const MAX_HANDLED = 400;
const TIDY_AFTER = 40;

const num = (v: unknown): number => {
  const n = typeof v === 'string' ? Number(v) : v;
  return typeof n === 'number' && Number.isFinite(n) ? n : 0;
};
const clamp = (n: number, min: number, max: number): number => Math.min(max, Math.max(min, n));

/** First balanced `{...}` in `text`, skipping braces inside strings. */
function firstJsonObject(text: string): string | null {
  const start = text.indexOf('{');
  if (start < 0) return null;
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let i = start; i < text.length; i++) {
    const ch = text[i];
    if (escaped) {
      escaped = false;
    } else if (ch === '\\' && inString) {
      escaped = true;
    } else if (ch === '"') {
      inString = !inString;
    } else if (!inString) {
      if (ch === '{') depth++;
      else if (ch === '}' && --depth === 0) return text.slice(start, i + 1);
    }
  }
  return null;
}

function parseMealObject(rawJson: string): GeminiMeal | null {
  let root: unknown;
  try { root = JSON.parse(rawJson); } catch { return null; }
  if (typeof root !== 'object' || root === null || Array.isArray(root)) return null;
  const o = root as Record<string, unknown>;
  if (!Array.isArray(o.items)) return null;
  const items: InboxScanItem[] = [];
  for (const raw of o.items) {
    if (typeof raw !== 'object' || raw === null) continue;
    const r = raw as Record<string, unknown>;
    const name = typeof r.name === 'string' ? r.name.trim() : '';
    if (!name) continue;
    items.push({
      name,
      grams: clamp(Math.round(num(r.grams)), 0, 5000),
      kcal: clamp(Math.round(num(r.kcal)), 0, 5000),
      protein: clamp(num(r.protein), 0, 500),
      carbs: clamp(num(r.carbs), 0, 500),
    });
  }
  if (items.length === 0) return null;
  return {
    mealId: typeof o.mealId === 'string' ? o.mealId.trim() : '',
    date: typeof o.date === 'string' ? o.date.trim() : '',
    time: typeof o.time === 'string' ? o.time.trim() : '',
    dish: typeof o.dish === 'string' && o.dish.trim() ? o.dish.trim() : 'Meal',
    place: typeof o.place === 'string' ? o.place.trim() : '',
    items,
    handVisible: o.handVisible === true,
    note: typeof o.note === 'string' ? o.note.trim() : '',
  };
}

/**
 * Every meal in `raw`: blocks after each `FORGEQUEST-MEAL v1` header in the Doc, or a single bare JSON
 * answer pasted/shared from an AI app. Meals with no food items are dropped.
 */
export function parseAllMeals(raw: string): GeminiMeal[] {
  // Docs can turn straight quotes into curly ones.
  const text = raw.replace(/\u201C|\u201D/g, '"');
  const chunks = text.includes(MEAL_HEADER) ? text.split(MEAL_HEADER).slice(1) : [text];
  const out: GeminiMeal[] = [];
  for (const chunk of chunks) {
    const obj = firstJsonObject(chunk);
    if (!obj) continue;
    const parsed = parseMealObject(obj);
    if (parsed) out.push(parsed);
  }
  return out;
}

export const parseOneMeal = (raw: string): GeminiMeal | null => parseAllMeals(raw).at(-1) ?? null;

export const mealBlock = (meal: GeminiMeal): string => `${MEAL_HEADER}\n${JSON.stringify(meal)}`;

export const mealKcal = (meal: GeminiMeal): number => meal.items.reduce((a, i) => a + i.kcal, 0);

export const mealToScan = (meal: GeminiMeal) => ({
  dish: meal.dish,
  items: meal.items,
  handVisible: meal.handVisible,
  note: meal.note,
});

/** The epoch day the meal was eaten; `today` when missing/invalid, never in the future. */
export function mealEpochDay(meal: GeminiMeal, today = todayEpochDay()): number {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(meal.date.trim());
  if (!m) return today;
  const y = Number(m[1]);
  const mo = Number(m[2]);
  const d = Number(m[3]);
  const dt = new Date(Date.UTC(y, mo - 1, d));
  if (dt.getUTCFullYear() !== y || dt.getUTCMonth() !== mo - 1 || dt.getUTCDate() !== d) return today;
  return Math.min(today, epochDayOf(y, mo, d));
}

export function mealTimeParts(meal: GeminiMeal): { hour: number; minute: number } | null {
  const raw = meal.time.trim();
  if (!raw) return null;
  const padded = raw.length === 4 && raw[1] === ':' ? `0${raw}` : raw;
  const m = /^(\d{2}):(\d{2})$/.exec(padded);
  if (!m) return null;
  const hour = Number(m[1]);
  const minute = Number(m[2]);
  if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
  return { hour, minute };
}

/** Breakfast/lunch/dinner/snacks from the time it was eaten, matching Android MealInbox.kt. */
export function mealCategory(meal: GeminiMeal): FoodCategory | null {
  const tp = mealTimeParts(meal);
  if (!tp) return null;
  const h = tp.hour;
  if (h >= 4 && h <= 10) return 'BREAKFAST';
  if (h >= 11 && h <= 15) return 'LUNCH';
  if (h >= 18 && h <= 22) return 'DINNER';
  return 'SNACKS';
}

function stableHashId(meal: GeminiMeal): string {
  const src = `${meal.date}|${meal.time}|${meal.dish}|${mealKcal(meal)}`;
  let h = 0;
  for (let i = 0; i < src.length; i++) {
    h = (Math.imul(31, h) + src.charCodeAt(i)) | 0;
  }
  return `h${(h >>> 0).toString(36)}`;
}

function mealsEqual(a: GeminiMeal, b: GeminiMeal): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

// ---------------------------------------------------------------- Storage & Lock

let inboxQueue: Promise<unknown> = Promise.resolve();
function withInboxLock<T>(job: () => Promise<T>): Promise<T> {
  const next = inboxQueue.then(job, job);
  inboxQueue = next.catch(() => undefined);
  return next;
}

export async function loadPendingMeals(): Promise<GeminiMeal[]> {
  const row = await db.kv.get(KV_PENDING);
  return Array.isArray(row?.value) ? (row.value as GeminiMeal[]) : [];
}

export function usePendingMeals(): GeminiMeal[] {
  return useLive(loadPendingMeals, [], []);
}

export async function getPendingMeal(mealId: string): Promise<GeminiMeal | null> {
  const list = await loadPendingMeals();
  return list.find((m) => m.mealId === mealId) ?? null;
}

async function loadHandledIds(): Promise<string[]> {
  const row = await db.kv.get(KV_HANDLED);
  return Array.isArray(row?.value) ? (row.value as string[]).filter((x) => typeof x === 'string') : [];
}

async function alreadyLogged(meal: GeminiMeal, today = todayEpochDay()): Promise<boolean> {
  const kcal = mealKcal(meal);
  if (kcal <= 0) return false;
  const day = mealEpochDay(meal, today);
  const tp = mealTimeParts(meal);
  let eatenAt: number | null = null;
  if (tp) {
    const utc = new Date(day * 86_400_000);
    eatenAt = new Date(utc.getUTCFullYear(), utc.getUTCMonth(), utc.getUTCDate(), tp.hour, tp.minute, 0, 0).getTime();
  }
  const dayMeals = await db.meals.where('epochDay').equals(day).toArray();
  return dayMeals.some((logged) => {
    const closeKcal = Math.abs(logged.kcal - kcal) <= kcal * 0.05;
    const sameName = logged.name.trim().toLowerCase() === meal.dish.trim().toLowerCase();
    const closeTime = eatenAt != null && Math.abs(logged.loggedAt - eatenAt) <= 2 * 60 * 60 * 1000;
    return closeKcal && (closeTime || (eatenAt == null && sameName));
  });
}

/** Buffers `meal` in the inbox unless it was already handled or logged. */
export function offerMeal(meal: GeminiMeal, today = todayEpochDay()): Promise<OfferResult> {
  return withInboxLock(async () => {
    const withId = meal.mealId ? meal : { ...meal, mealId: stableHashId(meal) };
    const [handled, pending] = await Promise.all([loadHandledIds(), loadPendingMeals()]);
    if (handled.includes(withId.mealId) || (await alreadyLogged(withId, today))) return 'ALREADY_LOGGED';
    if (pending.some((m) => mealsEqual(m, withId))) return 'UNCHANGED';
    const updated = pending.some((m) => m.mealId === withId.mealId);
    const next = updated ? pending.map((m) => (m.mealId === withId.mealId ? withId : m)) : [...pending, withId];
    await db.kv.put({ key: KV_PENDING, value: next });
    return updated ? 'UPDATED' : 'ADDED';
  });
}

/** Marks `mealId` as logged or dismissed and drops it from the pending inbox. */
export function markMealHandled(mealId: string): Promise<void> {
  return withInboxLock(async () => {
    if (!mealId) return;
    const [handled, pending] = await Promise.all([loadHandledIds(), loadPendingMeals()]);
    const nextHandled = [...new Set([...handled, mealId])].slice(-MAX_HANDLED);
    const nextPending = pending.filter((m) => m.mealId !== mealId);
    await Promise.all([
      db.kv.put({ key: KV_HANDLED, value: nextHandled }),
      db.kv.put({ key: KV_PENDING, value: nextPending }),
    ]);
  });
}

/**
 * Reads the "ForgeQuest meals from Gemini" Doc and puts new meals in the inbox. Creates the Doc on first use.
 * Returns how many meals were added or updated.
 */
export async function syncMeals(token: string, placeholder: string, today = todayEpochDay()): Promise<number> {
  const sinceRow = await db.kv.get(KV_DOC_MOD);
  const since = typeof sinceRow?.value === 'number' ? sinceRow.value : 0;
  const doc = await readDoc(token, MEAL_DOC_NAME, since, placeholder);
  if (!doc) return 0;
  const meals = parseAllMeals(doc.text);
  let count = 0;
  for (const m of meals) {
    const res = await offerMeal(m, today);
    if (res === 'ADDED' || res === 'UPDATED') count++;
  }
  await db.kv.put({ key: KV_DOC_MOD, value: doc.modifiedAt });
  if (meals.length > TIDY_AFTER) {
    const keepFrom = today - 14;
    const kept = meals.filter((m) => mealEpochDay(m, today) >= keepFrom);
    const text = `${placeholder}\n\n${kept.map(mealBlock).join('\n\n')}`;
    await upsertTextDoc(token, MEAL_DOC_NAME, text, doc.id).catch(() => undefined);
  }
  return count;
}
