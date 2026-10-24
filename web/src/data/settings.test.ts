import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import fixtureText from './fixtures/android-backup.json?raw';
import { db } from './db';
import { applyBackup, decodeBackup } from './backup';
import {
  DEFAULT_SETTINGS, clearWeekTargets, fromBackupSettings, loadSettings, saveWeekTargets, toBackupSettings,
  updateSettings, weekTargets,
} from './settings';

beforeEach(async () => {
  await Promise.all(db.tables.map((t) => t.clear()));
});

describe('settings', () => {
  it('starts with the Android defaults', async () => {
    const s = await loadSettings();
    expect(s).toEqual(DEFAULT_SETTINGS);
    expect(s.kcalGoal).toBe(2200);
    expect(s.proteinGoal).toBe(140);
    expect(s.unit).toBe('kg');
    expect(s.lastCheckinWeek).toBe(-1);
  });

  it('survives a reload of the database', async () => {
    await updateSettings((s) => ({ ...s, name: 'Omar', onboarded: true, unit: 'lb', kcalGoal: 100, apiKey: '  k1 ' }));
    db.close();
    await db.open();
    const s = await loadSettings();
    expect(s.name).toBe('Omar');
    expect(s.onboarded).toBe(true);
    expect(s.unit).toBe('lb');
    expect(s.kcalGoal).toBe(1200); // floor, like Kotlin
    expect(s.apiKey).toBe('k1');
  });

  it('never puts the API keys in a backup', async () => {
    await updateSettings((s) => ({ ...s, apiKey: 'GEMINI-SECRET-123', groqKey: 'GROQ-SECRET-456', name: 'Omar' }));
    const text = JSON.stringify(toBackupSettings(await loadSettings()));
    expect(text).not.toContain('SECRET');
    expect(text).not.toMatch(/apiKey|groqKey/);
    expect(text).toContain('Omar');
  });

  it('restoring settings keeps this phone\'s keys and clears the locked week targets', async () => {
    await updateSettings((s) => ({ ...s, apiKey: 'KEEP-ME', onboarded: true }));
    await saveWeekTargets(new Map([[20000, 3]]));
    const file = decodeBackup(fixtureText);
    const restored = await applyBackup(file);
    expect(restored).not.toBeNull();
    const s = await loadSettings();
    expect(s.apiKey).toBe('KEEP-ME');
    expect(s.onboarded).toBe(true);
    expect(s.name).toBe(restored!.name);
    expect((await weekTargets()).size).toBe(0);
  });

  it('round-trips through backup settings', async () => {
    const s = await updateSettings((x) => ({ ...x, proteinGoal: 170, nutritionGoal: 'CUT', trackCarbs: true }));
    const back = fromBackupSettings(toBackupSettings(s), DEFAULT_SETTINGS);
    expect(back.proteinGoal).toBe(170);
    expect(back.nutritionGoal).toBe('CUT');
    expect(back.trackCarbs).toBe(true);
  });

  it('week targets merge, then clear', async () => {
    await saveWeekTargets(new Map([[100, 3]]));
    await saveWeekTargets(new Map([[107, 4]]));
    expect([...(await weekTargets())]).toEqual([[100, 3], [107, 4]]);
    await clearWeekTargets();
    expect((await weekTargets()).size).toBe(0);
  });
});
