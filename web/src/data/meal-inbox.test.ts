import 'fake-indexeddb/auto';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { db, putMeal } from './db';
import {
  MEAL_HEADER, loadPendingMeals, markMealHandled, mealCategory, mealEpochDay, mealKcal, offerMeal,
  parseAllMeals, parseOneMeal, syncMeals, type GeminiMeal,
} from './meal-inbox';

const sampleMeal = (over: Partial<GeminiMeal> = {}): GeminiMeal => ({
  mealId: 'm1abc234',
  date: '2026-11-10',
  time: '13:30',
  dish: 'Chicken Kabsa',
  place: 'Romansiah',
  items: [{ name: 'Rice & chicken', grams: 400, kcal: 650, protein: 42, carbs: 70 }],
  handVisible: false,
  note: 'Home style',
  ...over,
});

beforeEach(async () => {
  await Promise.all(db.tables.map((t) => t.clear()));
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('meal inbox parsing', () => {
  it('parses multiple Doc blocks with curly quotes and drops empty meals', () => {
    const raw = [
      'Placeholder text',
      MEAL_HEADER,
      '```json',
      '{\u201CmealId\u201D:\u201Ca1\u201D,\u201Cdish\u201D:\u201CKabsa\u201D,\u201Citems\u201D:[{\u201Cname\u201D:\u201CRice\u201D,\u201Cgrams\u201D:200,\u201Ckcal\u201D:260,\u201Cprotein\u201D:5,\u201Ccarbs\u201D:56}]}',
      '```',
      MEAL_HEADER,
      '{"mealId":"empty","dish":"Water","items":[]}',
      MEAL_HEADER,
      '{"mealId":"b2","dish":"Dates","items":[{"name":"Dates","grams":40,"kcal":110,"protein":1,"carbs":28}]}',
    ].join('\n');

    const meals = parseAllMeals(raw);
    expect(meals.map((m) => m.mealId)).toEqual(['a1', 'b2']);
    expect(meals[0].dish).toBe('Kabsa');
    expect(parseOneMeal(raw)?.mealId).toBe('b2');
  });

  it('derives epochDay clamped to today and category from time', () => {
    expect(mealEpochDay(sampleMeal({ date: '2026-11-10' }), 20800)).toBe(20767);
    expect(mealEpochDay(sampleMeal({ date: '2099-01-01' }), 20767)).toBe(20767);
    expect(mealEpochDay(sampleMeal({ date: 'bad' }), 20767)).toBe(20767);
    expect(mealCategory(sampleMeal({ time: '8:05' }))).toBe('BREAKFAST');
    expect(mealCategory(sampleMeal({ time: '13:30' }))).toBe('LUNCH');
    expect(mealCategory(sampleMeal({ time: '20:00' }))).toBe('DINNER');
    expect(mealCategory(sampleMeal({ time: '23:30' }))).toBe('SNACKS');
    expect(mealCategory(sampleMeal({ time: '' }))).toBeNull();
  });
});

describe('meal inbox buffering and sync', () => {
  it('offers, updates corrections by mealId, and removes on markHandled', async () => {
    const m = sampleMeal();
    expect(await offerMeal(m, 20800)).toBe('ADDED');
    expect(await offerMeal(m, 20800)).toBe('UNCHANGED');
    expect(await offerMeal({ ...m, items: [{ name: 'Less rice', grams: 250, kcal: 420, protein: 38, carbs: 40 }] }, 20800)).toBe('UPDATED');
    expect(mealKcal((await loadPendingMeals())[0])).toBe(420);

    await markMealHandled(m.mealId);
    expect(await loadPendingMeals()).toEqual([]);
    expect(await offerMeal(m, 20800)).toBe('ALREADY_LOGGED');
  });

  it('skips meals already logged on that day within 5% kcal', async () => {
    const m = sampleMeal({ date: '2026-11-10', time: '' });
    const day = mealEpochDay(m, 20800);
    await putMeal({
      epochDay: day, loggedAt: 1000, name: 'Chicken Kabsa', category: 'LUNCH',
      kcal: 645, protein: 40, quantity: 1, carbs: 68, photoPath: null, foodId: null,
    });
    expect(await offerMeal(m, 20800)).toBe('ALREADY_LOGGED');
  });

  it('syncMeals reads the Doc, buffers new meals, and skips unchanged modifiedTime', async () => {
    const docText = `${MEAL_HEADER}\n${JSON.stringify(sampleMeal())}`;
    let exports = 0;
    vi.stubGlobal('fetch', async (url: string) => {
      if (url.includes('orderBy=modifiedTime')) {
        return Response.json({ files: [{ id: 'doc1', name: 'ForgeQuest meals from Gemini', modifiedTime: '2026-11-12T10:00:00Z' }] });
      }
      if (url.includes('/files/doc1/export')) {
        exports++;
        return new Response(docText);
      }
      return new Response('', { status: 404 });
    });

    expect(await syncMeals('tok', 'placeholder', 20800)).toBe(1);
    expect((await loadPendingMeals())).toHaveLength(1);
    expect(exports).toBe(1);

    // Second sync with the same modifiedTime skips exporting and adds 0.
    expect(await syncMeals('tok', 'placeholder', 20800)).toBe(0);
    expect(exports).toBe(1);
  });
});
