// Port of the data parts of app/.../data/FoodRepository.kt: foods ("My foods"), meals and today's totals.
// Skipped: photos (the web never stores them), the AI scan and the Gemini meal inbox.
import { db, putMeal, type FoodCategory, type FoodRow, type MealRow } from './db';
import { useLive } from './live';
import { todayEpochDay } from '../domain/dates';

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

/** Logs saved foods with a quantity each (1 = one serving). */
export async function logFoods(selection: ReadonlyArray<readonly [FoodRow, number]>, epochDay = todayEpochDay()): Promise<void> {
  const now = Date.now();
  await db.meals.bulkAdd(selection.map(([food, qty]): MealRow => ({
    epochDay, loggedAt: now, name: food.name, category: food.category, kcal: Math.trunc(food.kcal * qty),
    protein: food.protein * qty, quantity: qty, carbs: food.carbs * qty, photoPath: null, foodId: food.id ?? null,
  })));
}

/** One meal that isn't saved to the food list: a restaurant plate, a snack you won't repeat. */
export async function logOnce(
  name: string, category: FoodCategory, kcal: number, protein: number, epochDay = todayEpochDay(), carbs = 0,
): Promise<void> {
  await putMeal({
    epochDay, loggedAt: Date.now(), name: name.trim(), category, kcal, protein, quantity: 1, carbs, photoPath: null, foodId: null,
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
export const useDayTotals = (epochDay = todayEpochDay()): DayTotals => useLive(() => dayTotals(epochDay), [epochDay], { kcal: 0, protein: 0, carbs: 0 });

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
