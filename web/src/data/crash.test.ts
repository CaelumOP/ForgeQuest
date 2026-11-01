import { describe, expect, it } from 'vitest';
import { CRASH_KEY, clearCrash, crashText, loadCrash, saveCrash } from './crash';

function memoryStore() {
  const m = new Map<string, string>();
  return {
    m,
    getItem: (k: string) => m.get(k) ?? null,
    setItem: (k: string, v: string) => { m.set(k, v); },
    removeItem: (k: string) => { m.delete(k); },
  };
}

describe('crash report', () => {
  it('saves the last error under one key and reads it back', () => {
    const s = memoryStore();
    saveCrash(new TypeError('x is undefined'), s, 1000);
    expect([...s.m.keys()]).toEqual([CRASH_KEY]);
    const r = loadCrash(s)!;
    expect(r.time).toBe(1000);
    expect(r.message).toBe('TypeError: x is undefined');
    expect(r.stack).toContain('x is undefined');
    expect(typeof r.version).toBe('string');
  });

  it('keeps only the newest crash', () => {
    const s = memoryStore();
    saveCrash(new Error('first'), s, 1);
    saveCrash('second', s, 2);
    const r = loadCrash(s)!;
    expect(r.message).toBe('second');
    expect(r.stack).toBe('');
    expect(s.m.size).toBe(1);
  });

  it('clears it', () => {
    const s = memoryStore();
    saveCrash(new Error('boom'), s);
    clearCrash(s);
    expect(loadCrash(s)).toBeNull();
  });

  it('never throws when storage is missing, blocked or holds junk', () => {
    const blocked = {
      getItem: () => { throw new Error('denied'); },
      setItem: () => { throw new Error('denied'); },
      removeItem: () => { throw new Error('denied'); },
    };
    expect(() => saveCrash(new Error('a'), blocked)).not.toThrow();
    expect(loadCrash(blocked)).toBeNull();
    expect(() => clearCrash(blocked)).not.toThrow();
    expect(() => saveCrash(new Error('a'), null)).not.toThrow();
    const s = memoryStore();
    s.setItem(CRASH_KEY, '{not json');
    expect(loadCrash(s)).toBeNull();
  });

  it('turns into shareable text without user data', () => {
    const s = memoryStore();
    saveCrash(new Error('boom'), s, 0);
    const text = crashText(loadCrash(s)!);
    expect(text).toContain('ForgeQuest Web crash report');
    expect(text).toContain('Time: 1970-01-01T00:00:00.000Z');
    expect(text).toContain('Error: Error: boom');
  });
});
