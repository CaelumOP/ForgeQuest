import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import fixtureText from './fixtures/android-backup.json?raw';
import { db } from './db';
import { applyBackup, decodeBackup } from './backup';
import {
  autoBackupEffective, setAutoBackup, markRestoreOfferShown, weightUnit,
  DEFAULT_SETTINGS, clearWeekTargets, fromBackupSettings, isAiOn, loadSettings, migrateWeekTargets, saveWeekTargets,
  shiftWeekTargetsRecord, toBackupSettings, updateSettings, weekTargets,
} from './settings';

beforeEach(async () => {
  await Promise.all(db.tables.map((t) => t.clear()));
});

describe('settings', () => {
  it('keeps backup opt-out and account restore offers across reconnect and restore', async () => {
    expect(autoBackupEffective(await loadSettings())).toBe(false);
    await updateSettings((s) => ({ ...s, driveEmail: 'a@example.test' }));
    expect(autoBackupEffective(await loadSettings())).toBe(true);
    await setAutoBackup(false);
    await markRestoreOfferShown('A@example.test');
    await updateSettings((s) => ({ ...s, driveEmail: '' }));
    await updateSettings((s) => ({ ...s, driveEmail: 'b@example.test' }));
    await applyBackup(decodeBackup(fixtureText));
    const s = await loadSettings();
    expect(autoBackupEffective(s)).toBe(false);
    expect(s.autoBackupOptedOut).toBe(true);
    expect(s.restoreOfferShown).toEqual(['a@example.test']);
    expect(toBackupSettings(s)).not.toHaveProperty('restoreOfferShown');
    expect(toBackupSettings(s)).not.toHaveProperty('driveEmail');
    expect(s.barWeightKg).toBe(20);
    expect(s.plateInventory).toEqual([25, 20, 15, 10, 5, 2.5, 1.25]);
    expect(weightUnit(s, 'lb')).toBe(weightUnit({ ...s, unit: 'lb' }));
  });
  it('starts with the Android defaults', async () => {
    const s = await loadSettings();
    expect(s).toEqual(DEFAULT_SETTINGS);
    expect(s.kcalGoal).toBe(2200);
    expect(s.proteinGoal).toBe(140);
    expect(s.unit).toBe('kg');
    expect(s.geminiMealsEnabled).toBe(false);
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

  it('never puts the API keys or geminiMealsEnabled in a backup', async () => {
    await updateSettings((s) => ({ ...s, apiKey: 'GEMINI-SECRET-123', geminiMealsEnabled: true, name: 'Omar' }));
    const backup = toBackupSettings(await loadSettings());
    const text = JSON.stringify(backup);
    expect(text).not.toContain('SECRET');
    expect(text).not.toMatch(/apiKey|geminiMealsEnabled/);
    expect(backup.debriefEnabled).toBe(true);
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

  it('migrates Monday weekTargets keys to Sunday once and only once', async () => {
    await db.kv.put({ key: 'weekTargets', value: { 20500: 3, 20507: 4 } });

    // Read-only weekTargets() shifts old rows on the fly for live queries.
    expect([...(await weekTargets())]).toEqual([[20499, 3], [20506, 4]]);

    // First persisted migration shifts once and marks sundayWeeks: true.
    expect(await migrateWeekTargets()).toBe(true);
    expect(await migrateWeekTargets()).toBe(false);
    expect((await db.kv.get('weekTargets'))?.value).toEqual({ 20499: 3, 20506: 4, sundayWeeks: true });
    expect([...(await weekTargets())]).toEqual([[20499, 3], [20506, 4]]);

    // Saving a new Sunday week preserves migrated keys without re-shifting them.
    await saveWeekTargets(new Map([[20513, 5]]));
    expect([...(await weekTargets())]).toEqual([[20499, 3], [20506, 4], [20513, 5]]);

    expect(shiftWeekTargetsRecord({ 20500: 3, sundayWeeks: false })).toEqual({
      targets: new Map([[20499, 3]]),
      migrated: true,
    });
    expect(shiftWeekTargetsRecord({ 20499: 3, sundayWeeks: true })).toEqual({
      targets: new Map([[20499, 3]]),
      migrated: false,
    });
  });

  it('resolves isAiOn from aiOptIn or apiKey presence', async () => {
    expect(isAiOn(DEFAULT_SETTINGS)).toBe(false);
    expect(isAiOn({ aiOptIn: null, apiKey: 'key' })).toBe(true);
    expect(isAiOn({ aiOptIn: false, apiKey: 'key' })).toBe(false);
    expect(isAiOn({ aiOptIn: true, apiKey: '' })).toBe(true);
    await updateSettings((s) => ({ ...s, aiOptIn: true }));
    expect((await loadSettings()).aiOptIn).toBe(true);
  });
});
