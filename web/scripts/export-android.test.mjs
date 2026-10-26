import { readFileSync } from 'node:fs';
import { describe, it, expect } from 'vitest';

const load = (f) => JSON.parse(readFileSync(new URL(`../src/i18n/${f}`, import.meta.url), 'utf8'));
const en = load('en.json');
const ar = load('ar.json');

const LEFTOVER = /%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z]/;
// Keys whose placeholder sets differ between EN and AR in the Android source (AR drops the rank letter / set count).
const SKIP_PLACEHOLDER_KEYS = new Set(['live_finish_unticked_title', 'live_rank', 'profile_rank_line']);

const texts = (v) => (typeof v === 'string' ? [v] : Object.values(v));
const phSet = (v) =>
  [...new Set(texts(v).flatMap((t) => [...t.matchAll(/\{(\d+)\}/g)].map((m) => m[1])))].sort();

describe('exported i18n', () => {
  it('parses and is non-empty', () => {
    expect(Object.keys(en).length).toBeGreaterThan(1000);
    expect(Object.keys(ar).length).toBeGreaterThan(1000);
  });
  it('every AR key exists in EN', () => {
    expect(Object.keys(ar).filter((k) => !(k in en))).toEqual([]);
  });
  it('every plural has other', () => {
    for (const o of [en, ar])
      for (const [k, v] of Object.entries(o))
        if (typeof v === 'object') expect(v.other, k).toBeTypeOf('string');
  });
  it('has no leftover Android placeholders', () => {
    for (const o of [en, ar])
      for (const [k, v] of Object.entries(o))
        // A literal "%" (from %%) followed by space+letter would look like a format spec; drop it first.
        for (const t of texts(v)) expect(LEFTOVER.test(t.replace(/% /g, ' ')), `${k}: ${t}`).toBe(false);
  });
  it('AR uses the same placeholder numbers as EN', () => {
    const bad = Object.keys(ar)
      .filter((k) => k in en && !SKIP_PLACEHOLDER_KEYS.has(k))
      .filter((k) => phSet(ar[k]).join() !== phSet(en[k]).join());
    expect(bad).toEqual([]);
  });
});
