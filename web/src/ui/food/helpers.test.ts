import { describe, expect, it } from 'vitest';
import { recentOneOffsOf } from '../../data/food';
import type { MealRow } from '../../data/db';
import { fmtQty, lastSevenDays, qtyMinus, qtyPlus } from './helpers';

describe('serving stepper', () => {
  it('steps down through half a serving to unticked', () => {
    expect([3, 1, 0.5].map(qtyMinus)).toEqual([2, 0.5, 0]);
  });
  it('steps up from half to one, capped at 20', () => {
    expect([0.5, 1, 20].map(qtyPlus)).toEqual([1, 2, 20]);
  });
  it('formats', () => {
    expect([fmtQty(2), fmtQty(0.5)]).toEqual(['2×', '0.5×']);
  });
  it('lists a week', () => expect(lastSevenDays(100)).toEqual([94, 95, 96, 97, 98, 99, 100]));
});

describe('recent one-offs', () => {
  const m = (id: number, name: string, loggedAt: number, foodId: number | null = null): MealRow =>
    ({ id, epochDay: 1, loggedAt, name, category: 'LUNCH', kcal: 1, protein: 1, quantity: 1, carbs: 0, photoPath: null, foodId });
  it('keeps the newest of each name, skips saved foods, newest first', () => {
    const out = recentOneOffsOf([m(1, 'Pizza', 10), m(2, 'pizza', 30), m(3, 'Soup', 20), m(4, 'Rice', 40, 7)]);
    expect(out.map((x) => x.id)).toEqual([2, 3]);
  });
});
