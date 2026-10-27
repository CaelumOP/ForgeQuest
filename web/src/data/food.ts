// Port of the data parts of app/.../data/FoodRepository.kt: foods ("My foods"), meals and today's totals.
// Skipped: photos (the web never stores them), the AI scan and the Gemini meal inbox.
import { db, putMeal, type FoodCategory, type FoodPart, type FoodRow, type MealRow } from './db';
import { sameFood } from '../domain/food-search';
import { useLive } from './live';
import { todayEpochDay } from '../domain/dates';
import { scaledKcal } from '../domain/nutrition';

export type DayTotals = { kcal: number; protein: number; carbs: number };

/** My foods: pinned first, then by name (case-insensitive). */
export async function getFoods(): Promise<FoodRow[]> {
  return (await db.foods.toArray()).sort((a, b) => Number(b.pinned) - Number(a.pinned) || a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }));
}
export const useFoods = (): FoodRow[] => useLive(getFoods, [], []);

/** Meals of one day in the order they were logged. */
export async function getMealsOn(epochDay: number): Promise<MealRow[]> {
  return (await db.meals.where('epochDay').equals(epochDay).toArray()).sort((a, b) => a.loggedAt - b.loggedAt || a.id! - b.id!);
}
export const useMealsOn = (epochDay: number): MealRow[] => useLive(() => getMealsOn(epochDay), [epochDay], []);

/** Adds or edits a food. Returns its id. */
export const saveFood = (food: FoodRow): Promise<number> => db.foods.put(food.id ? food : (({ id: _i, ...f }) => f)(food));
export const deleteFood = (id: number): Promise<void> => db.foods.delete(id);
export const deleteMeal = (id: number): Promise<void> => db.meals.delete(id);
/** Puts a deleted meal back with its original id (undo). */
export const restoreMeal = async (meal: MealRow): Promise<void> => { await db.meals.put(meal); };

/** A saved food ticked to log: how much, under which meal (default its own tab) and its name in the day's log. */
export type FoodPick = { food: FoodRow; qty: number; category?: FoodCategory; name?: string };

/** Logs saved foods, each with its amount (1 = one serving), meal and the name to show in the day's log. */
export async function logFoods(picks: ReadonlyArray<FoodPick>, epochDay = todayEpochDay()): Promise<void> {
  const now = Date.now();
  await db.meals.bulkAdd(picks.map(({ food, qty, category, name }): MealRow => ({
    epochDay, loggedAt: now, name: name ?? food.name, category: category ?? food.category, kcal: scaledKcal(food.kcal, qty),
    protein: food.protein * qty, quantity: qty, carbs: food.carbs * qty, photoPath: null, foodId: food.id ?? null,
  })));
}

/** This food with `parts`; when there are parts, its calories, protein and carbs become the totals of the parts that are on. */
export function withParts(food: FoodRow, parts: FoodPart[]): FoodRow {
  if (!parts.length) return { ...food, parts };
  const on = parts.filter((p) => p.on);
  return { ...food, parts, kcal: on.reduce((a, p) => a + p.kcal, 0), protein: on.reduce((a, p) => a + p.protein, 0), carbs: on.reduce((a, p) => a + p.carbs, 0) };
}

/** A part moved to `grams`, its calories, protein and carbs scaled with it. */
export function partWithGrams(part: FoodPart, grams: number): FoodPart {
  const f = part.grams > 0 ? grams / part.grams : 1;
  return { ...part, grams, kcal: scaledKcal(part.kcal, f), protein: part.protein * f, carbs: part.carbs * f };
}

/** The name in the day's log, saying which parts were left out: "Chicken shawarma, no Ketchup". */
export function logName(food: FoodRow, without: (name: string, off: string) => string, sep: string): string {
  const off = (food.parts ?? []).filter((p) => !p.on).map((p) => p.name);
  return off.length ? without(food.name, off.join(sep)) : food.name;
}

/** Restaurants used on saved foods, each once. */
export const placesOf = (foods: readonly FoodRow[]): string[] => [...new Set(foods.map((f) => (f.place ?? '').trim()).filter(Boolean))];

/**
 * Saves a scanned meal to My foods. The same food at the same restaurant (spelling variants and venue words like
 * كفتيريا or مطعم aside) is updated in place instead of saved again; it keeps its id, photo and pin. Returns its id.
 */
export async function saveMeal(food: FoodRow): Promise<number> {
  const all = await db.foods.toArray();
  const places = [...placesOf(all), food.place ?? ''];
  const existing = all.find((f) => sameFood(f.name, f.place ?? '', food.name, food.place ?? '', places));
  if (!existing) return saveFood(food);
  await db.foods.put({
    ...food, id: existing.id, createdAt: existing.createdAt, pinned: existing.pinned || food.pinned,
    photoPath: food.photoPath ?? existing.photoPath, place: (food.place ?? '').trim() || existing.place || '',
    serving: existing.serving || food.serving, // "1 sandwich" stays, not the scan's "1 plate"
  });
  return existing.id!;
}

/** How often and how lately each saved food was logged, by food id, so search puts your usual foods first. */
export async function foodUsage(): Promise<Map<number, { times: number; lastAt: number }>> {
  const out = new Map<number, { times: number; lastAt: number }>();
  await db.meals.each((m) => {
    if (m.foodId == null) return;
    const u = out.get(m.foodId) ?? { times: 0, lastAt: 0 };
    out.set(m.foodId, { times: u.times + 1, lastAt: Math.max(u.lastAt, m.loggedAt) });
  });
  return out;
}
export const useFoodUsage = (): Map<number, { times: number; lastAt: number }> => useLive(foodUsage, [], new Map());

/** photoPath of a scanned meal whose photo isn't kept (the web never stores photos), so "Snap & log" still counts it. Same value as Android. */
export const SCANNED_NO_PHOTO = 'scan:no-photo';

/** One meal that isn't saved to the food list: a restaurant plate, a snack you won't repeat. `scanned` marks a photo scan. */
export async function logOnce(
  name: string, category: FoodCategory, kcal: number, protein: number, epochDay = todayEpochDay(), carbs = 0, scanned = false,
): Promise<void> {
  await putMeal({
    epochDay, loggedAt: Date.now(), name: name.trim(), category, kcal, protein, quantity: 1, carbs,
    photoPath: scanned ? SCANNED_NO_PHOTO : null, foodId: null,
  });
}

/** Category that fits the hour of the day, so the right tab opens first. */
export function categoryForNow(hour = new Date().getHours()): FoodCategory {
  if (hour >= 4 && hour <= 10) return 'BREAKFAST';
  if (hour >= 11 && hour <= 15) return 'LUNCH';
  if (hour >= 18 && hour <= 22) return 'DINNER';
  return 'SNACKS';
}

export function totalsOf(meals: readonly MealRow[]): DayTotals {
  return meals.reduce((t, m) => ({ kcal: t.kcal + m.kcal, protein: t.protein + m.protein, carbs: t.carbs + m.carbs }), { kcal: 0, protein: 0, carbs: 0 });
}

/** Calories, protein and carbs eaten on one day (default today). */
export async function dayTotals(epochDay = todayEpochDay()): Promise<DayTotals> {
  return totalsOf(await getMealsOn(epochDay));
}

/** The latest of each one-off meal (quick log or scan) by name, newest first, max 8, for one-tap relogging. */
export function recentOneOffsOf(meals: readonly MealRow[]): MealRow[] {
  const seen = new Set<string>();
  const out: MealRow[] = [];
  for (const m of [...meals].filter((x) => x.foodId == null).sort((a, b) => b.loggedAt - a.loggedAt || (b.id ?? 0) - (a.id ?? 0))) {
    const key = m.name.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(m);
    if (out.length === 8) break;
  }
  return out;
}
export const getRecentOneOffs = async (): Promise<MealRow[]> => recentOneOffsOf(await db.meals.filter((m) => m.foodId == null).toArray());
export const useRecentOneOffs = (): MealRow[] => useLive(getRecentOneOffs, [], []);
