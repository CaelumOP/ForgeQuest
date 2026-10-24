import { describe, expect, it } from 'vitest';
import { extractJson, fitWithin, parseScan, scanInstructions, scanTotals, withGrams } from './scan';

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
  it('uses the hand size only when known', () => {
    expect(scanInstructions(18.5, 8)).toContain('hand length = 18.5 cm, palm width = 8 cm');
    expect(scanInstructions(0, 0)).toContain('standard fork');
  });
});
