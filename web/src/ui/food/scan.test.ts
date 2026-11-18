import { describe, expect, it } from 'vitest';
import { extractJson, fitWithin, parseLabelRead, parseScan, scanInstructions, scanTotals, withGrams } from './scan';

describe('fitWithin (photo shrink size math)', () => {
  it('shrinks the long edge to 1024 and keeps the ratio', () => {
    expect(fitWithin(4000, 3000)).toEqual({ width: 1024, height: 768 });
    expect(fitWithin(3000, 4000)).toEqual({ width: 768, height: 1024 });
    expect(fitWithin(3024, 4032)).toEqual({ width: 768, height: 1024 });
  });
  it('never enlarges', () => {
    expect(fitWithin(800, 600)).toEqual({ width: 800, height: 600 });
    expect(fitWithin(1024, 1024)).toEqual({ width: 1024, height: 1024 });
  });
  it('keeps at least one pixel and survives bad input', () => {
    expect(fitWithin(10000, 3)).toEqual({ width: 1024, height: 1 });
    expect(fitWithin(0, 0)).toEqual({ width: 1, height: 1 });
    expect(fitWithin(2000, 1000, 500)).toEqual({ width: 500, height: 250 });
  });
});

describe('parseScan (Gemini food JSON)', () => {
  const json = '{"dish":"Chicken and rice","items":[{"name":"Grilled chicken breast","grams":150,"kcal":248,"protein":46.5,"carbs":0},{"name":"Basmati rice","grams":180,"kcal":234,"protein":4.9,"carbs":50}],"handVisible":true,"note":"Rice portion guessed."}';
  it('reads the documented shape', () => {
    const s = parseScan(json)!;
    expect(s.dish).toBe('Chicken and rice');
    expect(s.items).toHaveLength(2);
    expect(s.items[1]).toEqual({ name: 'Basmati rice', grams: 180, kcal: 234, protein: 4.9, carbs: 50 });
    expect(s.handVisible).toBe(true);
    expect(s.note).toBe('Rice portion guessed.');
  });
  it('accepts a code block around the JSON', () => {
    expect(parseScan('```json\n' + json + '\n```')?.items).toHaveLength(2);
    expect(extractJson('x {"a":1} y')).toBe('{"a":1}');
  });
  it('reads a text-only Gem reply with handVisible false and Arabic item names', () => {
    const reply = '```json\n{"mealId": "m8p4qz1b", "date": "2026-11-11", "time": "14:15", "dish": "مندي دجاج", "place": "الرومانسية", "items": [{"name": "نصف دجاجة مندي (لحم صافي)", "grams": 195, "kcal": 448, "protein": 47, "carbs": 0, "fat": 29}, {"name": "أرز مندي", "grams": 280, "kcal": 490, "protein": 8.4, "carbs": 84, "fat": 13.5}], "handVisible": false, "confidence": "high", "note": "تقدير نصي بدون صورة؛ خُصم نحو 35% لوزن العظم."}\n```';
    const s = parseScan(reply)!;
    expect(s.handVisible).toBe(false);
    expect(s.dish).toBe('مندي دجاج');
    expect(s.items[0]).toEqual({ name: 'نصف دجاجة مندي (لحم صافي)', grams: 195, kcal: 448, protein: 47, carbs: 0 });
    expect(s.items[1].name).toBe('أرز مندي');
    expect(s.note).toContain('35%');
  });
  it('fills defaults, coerces numbers and drops nameless items', () => {
    const s = parseScan('{"items":[{"name":" Egg ","kcal":"72","protein":6.3},{"grams":5},{"name":"Dates","grams":-3,"kcal":null}]}')!;
    expect(s.dish).toBe('Meal');
    expect(s.items).toEqual([
      { name: 'Egg', grams: 0, kcal: 72, protein: 6.3, carbs: 0 },
      { name: 'Dates', grams: 0, kcal: 0, protein: 0, carbs: 0 },
    ]);
  });
  it('returns an empty list for no food, null for junk', () => {
    expect(parseScan('{"dish":"","items":[],"handVisible":false,"note":""}')?.items).toEqual([]);
    expect(parseScan('sorry, I cannot')).toBeNull();
    expect(parseScan('[1,2]')).toBeNull();
    expect(parseScan('{"items":"x"}')).toBeNull();
  });
});

describe('scan maths and prompt', () => {
  it('rescales an item to corrected grams', () => {
    expect(withGrams({ name: 'Rice', grams: 100, kcal: 130, protein: 2.7, carbs: 28 }, 200)).toEqual({ name: 'Rice', grams: 200, kcal: 260, protein: 5.4, carbs: 56 });
  });
  it('totals with portion factor and extra fat', () => {
    const items = [{ name: 'a', grams: 1, kcal: 400, protein: 40, carbs: 10 }, { name: 'b', grams: 1, kcal: 201, protein: 4, carbs: 30 }];
    expect(scanTotals(items, 1.25, 50)).toEqual({ kcal: 751 + 50, protein: 55, carbs: 50 });
    expect(scanTotals([], 1, 150).kcal).toBe(0);
  });
  it('uses standard objects as scale reference without hand measurements', () => {
    const prompt = scanInstructions();
    expect(prompt).toContain('standard fork');
    expect(prompt).not.toMatch(/hand length|palm width/i);
  });
});

describe('parseLabelRead (Gemini nutrition label JSON)', () => {
  it('parses per-100g and per-serving labels and rejects unreadable ones', () => {
    expect(
      parseLabelRead('```json\n{"name":"Tuna","basis":"100g","servingGrams":80,"kcal":110,"protein":25,"carbs":0}\n```', '12345678'),
    ).toEqual({
      code: '12345678',
      name: 'Tuna',
      brand: '',
      kcal: 110,
      protein: 25,
      carbs: 0,
      serving: '100 g',
    });
    expect(
      parseLabelRead('{"name":"Bar","basis":"serving","servingGrams":45,"kcal":190,"protein":15,"carbs":20}', '12345678'),
    ).toEqual({
      code: '12345678',
      name: 'Bar',
      brand: '',
      kcal: 190,
      protein: 15,
      carbs: 20,
      serving: '45 g',
    });
    expect(parseLabelRead('{"kcal":0}', '12345678')).toBeNull();
    expect(parseLabelRead('{"basis":"serving","servingGrams":0,"kcal":100}', '12345678')).toBeNull();
  });

  it('clamps out-of-range AI numbers and turns NaN/Infinity into 0', () => {
    const scan = parseScan(JSON.stringify({
      dish: 'Feast',
      items: [
        { name: 'Huge', grams: 99999, kcal: 25000, protein: 1200, carbs: 999 },
        { name: 'Bogus', grams: 'NaN', kcal: 'Infinity', protein: -10, carbs: 'abc' },
      ],
    }))!;
    expect(scan.items[0]).toEqual({ name: 'Huge', grams: 5000, kcal: 5000, protein: 500, carbs: 500 });
    expect(scan.items[1]).toEqual({ name: 'Bogus', grams: 0, kcal: 0, protein: 0, carbs: 0 });

    const label = parseLabelRead('{"name":"Powder","basis":"serving","servingGrams":9000,"kcal":9999,"protein":800,"carbs":700}', '1111');
    expect(label).toMatchObject({ kcal: 5000, protein: 500, carbs: 500, serving: '5000 g' });
  });
});
