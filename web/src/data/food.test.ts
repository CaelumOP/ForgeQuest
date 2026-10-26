import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import { db } from './db';
import { categoryForNow, dayTotals, deleteMeal, foodUsage, getFoods, getMealsOn, logFoods, logName, logOnce, partWithGrams, restoreMeal, saveFood, saveMeal, withParts } from './food';
import type { FoodPart } from './db';

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
    await logFoods([{ food: foods.find((f) => f.id === rice)!, qty: 1.5 }, { food: foods.find((f) => f.id === chicken)!, qty: 2 }], 20000);
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

// Same cases as app/src/test/java/.../data/FoodPartsTest.kt.
describe('food library: parts, restaurants, no duplicates', () => {
  const shawarma: FoodPart[] = [
    { name: 'Bread', grams: 80, kcal: 220, protein: 7, carbs: 44, on: true },
    { name: 'Chicken', grams: 120, kcal: 250, protein: 30, carbs: 0, on: true },
    { name: 'Garlic sauce', grams: 30, kcal: 180, protein: 0.5, carbs: 2, on: true },
    { name: 'Ketchup', grams: 15, kcal: 17, protein: 0.2, carbs: 4, on: false },
  ];

  it('totals come from the parts that are on; unticked parts are kept', () => {
    const f = withParts(food('Chicken shawarma', 0, 0), shawarma);
    expect(f.kcal).toBe(650);
    expect(f.protein).toBeCloseTo(37.5);
    expect(f.carbs).toBeCloseTo(46);
    expect(f.parts).toHaveLength(4);
    expect(withParts(food('Rice', 300, 6), []).kcal).toBe(300);
  });

  it('scales a part with its grams', () => {
    const p = partWithGrams(shawarma[1], 60);
    expect([p.grams, p.kcal, p.protein]).toEqual([60, 125, 15]);
  });

  it('names what was left out in the log of the day', () => {
    const without = (n: string, off: string) => `${n}, no ${off}`;
    expect(logName(withParts(food('Chicken shawarma', 0, 0), shawarma), without, ', ')).toBe('Chicken shawarma, no Ketchup');
    expect(logName(food('Rice', 300, 6), without, ', ')).toBe('Rice');
  });

  it('logs each pick under its meal with its name', async () => {
    const id = await saveFood(food('Coca-Cola', 140, 0));
    const coke = (await getFoods()).find((f) => f.id === id)!;
    await logFoods([{ food: { ...coke, category: 'DRINKS' }, qty: 0.5, category: 'DRINKS', name: 'Coca-Cola' }], 20000);
    const [m] = await getMealsOn(20000);
    expect([m.category, m.kcal, m.quantity, m.foodId]).toEqual(['DRINKS', 70, 0.5, id]);
    expect((await foodUsage()).get(id)?.times).toBe(1);
  });

  it('saving the same meal at the same restaurant updates it instead of adding a copy', async () => {
    const first = await saveMeal(withParts({ ...food('Chicken shawarma', 0, 0), place: 'كفتيريا افراح وتهاني', pinned: true }, shawarma));
    const again = await saveMeal(withParts({ ...food('chicken Shawarma', 0, 0), place: 'شاورما افراح وتهاني' }, shawarma.map((p) => ({ ...p, on: true }))));
    expect(again).toBe(first);
    const foods = await getFoods();
    expect(foods).toHaveLength(1);
    expect(foods[0].kcal).toBe(667);
    expect(foods[0].pinned).toBe(true);
    await saveMeal({ ...food('Chicken shawarma', 500, 30), place: 'مطعم البيك' });
    await saveMeal(food('Chicken shawarma', 500, 30));
    expect(await getFoods()).toHaveLength(3);
  });
});
