import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import { db } from './db';
import { categoryForNow, dayTotals, deleteMeal, getFoods, getMealsOn, logFoods, logOnce, restoreMeal, saveFood } from './food';

const food = (name: string, kcal: number, protein: number, pinned = false) => ({
  name, category: 'LUNCH' as const, kcal, protein, serving: '1', carbs: 10, photoPath: null, pinned, createdAt: 1,
});

beforeEach(async () => {
  await Promise.all(db.tables.map((t) => t.clear()));
});

describe('food', () => {
  it('sums meals for one day only', async () => {
    const rice = await saveFood(food('Rice', 200, 4));
    const chicken = await saveFood(food('Chicken', 165, 31));
    const foods = await getFoods();
    await logFoods([[foods.find((f) => f.id === rice)!, 1.5], [foods.find((f) => f.id === chicken)!, 2]], 20000);
    await logOnce('Restaurant plate', 'DINNER', 700, 40, 20000, 55);
    await logOnce('Yesterday', 'SNACKS', 999, 99, 19999);

    const t = await dayTotals(20000);
    expect(t.kcal).toBe(300 + 330 + 700);
    expect(t.protein).toBeCloseTo(6 + 62 + 40);
    expect(t.carbs).toBeCloseTo(15 + 20 + 55);
    expect(await getMealsOn(20000)).toHaveLength(3);
    expect((await dayTotals(20001)).kcal).toBe(0);
  });

  it('keeps My foods pinned first then by name', async () => {
    await saveFood(food('banana', 90, 1));
    await saveFood(food('Apple', 80, 0));
    await saveFood(food('Zucchini', 20, 1, true));
    expect((await getFoods()).map((f) => f.name)).toEqual(['Zucchini', 'Apple', 'banana']);
  });

  it('deletes and restores a meal with its id', async () => {
    await logOnce('Egg', 'BREAKFAST', 70, 6, 20000);
    const [m] = await getMealsOn(20000);
    await deleteMeal(m.id!);
    expect(await getMealsOn(20000)).toHaveLength(0);
    await restoreMeal(m);
    expect((await getMealsOn(20000))[0].id).toBe(m.id);
  });

  it('picks a category from the hour', () => {
    expect(categoryForNow(7)).toBe('BREAKFAST');
    expect(categoryForNow(12)).toBe('LUNCH');
    expect(categoryForNow(20)).toBe('DINNER');
    expect(categoryForNow(2)).toBe('SNACKS');
  });
});
