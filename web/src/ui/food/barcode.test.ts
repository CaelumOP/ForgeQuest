import { describe, expect, it } from 'vitest';
import { parseOpenFoodFacts } from './barcode';

describe('parseOpenFoodFacts', () => {
  it('parses per-serving nutrition when present', () => {
    const product = parseOpenFoodFacts('123456', {
      status: 1,
      product: {
        product_name: 'Greek Yogurt',
        brands: 'Chobani, Other',
        serving_size: '150 g',
        nutriments: {
          'energy-kcal_serving': 130,
          proteins_serving: 15.2,
          carbohydrates_serving: 6.4,
        },
      },
    });
    expect(product).toEqual({
      code: '123456',
      name: 'Greek Yogurt',
      brand: 'Chobani',
      kcal: 130,
      protein: 15.2,
      carbs: 6.4,
      serving: '150 g',
    });
  });

  it('falls back to per-100g when serving is missing and returns null when status != 1', () => {
    expect(parseOpenFoodFacts('999', { status: 0 })).toBeNull();
    const p = parseOpenFoodFacts('111', {
      status: 1,
      product: {
        product_name: 'Oats',
        nutriments: { 'energy-kcal_100g': 379, proteins_100g: 13.2, carbohydrates_100g: 67.7 },
      },
    });
    expect(p?.kcal).toBe(379);
    expect(p?.serving).toBe('100 g');
  });
});
