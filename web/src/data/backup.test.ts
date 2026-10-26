import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import fixtureText from './fixtures/android-backup.json?raw';
import { db, putSession, putSet } from './db';
import {
  applyBackup, buildBackup, decodeBackup, encodeBackup, listSafetyCopies, DEFAULT_BACKUP_SETTINGS,
} from './backup';

const fixture = () => JSON.parse(fixtureText) as Record<string, unknown>;
const withoutCreatedAt = (o: Record<string, unknown>) => {
  const { createdAt: _c, ...rest } = o;
  return rest;
};

beforeEach(async () => {
  await Promise.all(db.tables.map((t) => t.clear()));
});

describe('backup', () => {
  it('round-trips the Android fixture unchanged except createdAt', async () => {
    const settings = await applyBackup(decodeBackup(fixtureText));
    const text = encodeBackup(await buildBackup(settings));
    const again = JSON.parse(text) as Record<string, unknown>;
    expect(withoutCreatedAt(again)).toEqual(withoutCreatedAt(fixture()));
    expect(Object.keys(again)).toEqual(Object.keys(fixture()));
    expect(typeof again.createdAt).toBe('number');
    expect(decodeBackup(text).version).toBe(2);
  });

  it('keeps a safety copy only when there is something to lose, and only the latest 3', async () => {
    const file = decodeBackup(fixtureText);
    await applyBackup(file);
    expect(await listSafetyCopies()).toHaveLength(0);

    await applyBackup(file);
    expect(await listSafetyCopies()).toHaveLength(1);
    const copy = decodeBackup((await listSafetyCopies())[0].json);
    expect(copy.sessions).toHaveLength(2);

    for (let i = 0; i < 4; i++) await applyBackup(file);
    expect(await listSafetyCopies()).toHaveLength(3);
  });

  it('makes a safety copy when the device only has one session', async () => {
    await putSession({ planDayId: null, title: 'Solo', epochDay: 1, startedAt: 1, finishedAt: null, note: '', debrief: '' });
    await applyBackup(decodeBackup(fixtureText));
    expect(await listSafetyCopies()).toHaveLength(1);
  });

  it('loads a v1 file without settings or dayNotes and keeps local readiness', async () => {
    const v1 = fixture();
    v1.version = 1;
    delete v1.settings;
    delete v1.dayNotes;
    delete v1.readinessDays;
    v1.extraFromTheFuture = { ignored: true };
    await db.readinessDays.put({
      epochDay: 5, recovery: 70, sleepPerf: 80, sleepMin: 420, strain: 10, hrv: 50, rhr: 55, feltGreat: false, updatedAt: 1,
    });
    const file = decodeBackup(JSON.stringify(v1));
    expect(file.settings).toBeNull();
    expect(file.dayNotes).toEqual([]);
    expect(await applyBackup(file)).toBeNull();
    expect(await db.sessions.count()).toBe(2);
    expect(await db.readinessDays.count()).toBe(1);
    expect(await db.dayNotes.count()).toBe(0);
  });

  it('drops photo paths', async () => {
    const f = fixture() as { foods: { photoPath: string | null }[]; meals: { photoPath: string | null }[] };
    f.foods[0].photoPath = '/data/user/0/com.forgequest.app/files/photos/a.jpg';
    f.meals[0].photoPath = '/data/user/0/com.forgequest.app/files/photos/b.jpg';
    await applyBackup(decodeBackup(JSON.stringify(f)));
    const out = await buildBackup(null);
    expect(out.foods[0].photoPath).toBeNull();
    expect(out.meals[0].photoPath).toBe('scan:no-photo'); // keeps "Snap & log" counting
  });

  it('carries the restaurant and parts of a food, and gives older backups none', async () => {
    const f = fixture() as { foods: Record<string, unknown>[] };
    const parts = [{ name: 'Bread', grams: 80, kcal: 220, protein: 7, carbs: 44, on: true }, { name: 'Ketchup', grams: 15, kcal: 17, protein: 0.2, carbs: 4, on: false }];
    f.foods[0].place = 'كفتيريا افراح وتهاني';
    f.foods[0].parts = parts;
    // Two foods from an older app, without the new fields.
    const { place: _p, parts: _q, ...old }: Record<string, unknown> = { ...f.foods[0], id: 2, name: 'Dates' };
    f.foods.push(old, { ...old, id: 3, name: 'Laban' });
    const file = decodeBackup(JSON.stringify(f));
    expect(file.foods[1].place).toBe('');
    expect(file.foods[1].parts).toEqual([]);
    expect(file.foods[1].parts).not.toBe(file.foods[2].parts); // a fresh list per row
    await applyBackup(file);
    const out = await buildBackup(null);
    const back = out.foods.find((x) => x.name === f.foods[0].name)!;
    expect(back.place).toBe('كفتيريا افراح وتهاني');
    expect(back.parts).toEqual(parts);
  });

  it('fills Kotlin defaults for missing fields and rejects missing required ones', () => {
    const f = fixture() as { sets: Record<string, unknown>[]; settings: Record<string, unknown> };
    delete f.sets[0].effort;
    delete f.sets[0].restSeconds;
    f.settings = { name: 'A' };
    const file = decodeBackup(JSON.stringify(f));
    expect(file.sets[0].effort).toBe('');
    expect(file.sets[0].restSeconds).toBe(60);
    expect(file.settings).toEqual({ ...DEFAULT_BACKUP_SETTINGS, name: 'A' });
    delete f.sets[0].sessionId;
    expect(() => decodeBackup(JSON.stringify(f))).toThrow();
    expect(() => decodeBackup('nope')).toThrow();
  });

  it('putSet assigns a new id when the row has none or 0', async () => {
    const base = {
      sessionId: 1, exerciseId: 'plank', itemPosition: 0, block: 'Core', circuit: false, setIndex: 0,
      targetLabel: '45 s', reps: null, seconds: 45, weightKg: null, done: true, isPr: false, restSeconds: 60, note: '', effort: '',
    };
    const a = await putSet({ ...base, id: 0 });
    const b = await putSet(base);
    expect(a).toBeGreaterThan(0);
    expect(b).toBe(a + 1);
  });
});
