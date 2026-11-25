import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import fixtureText from './fixtures/android-backup.json?raw';
import fixture41 from './fixtures/backup-4.1.json?raw';
import { db, putSession, putSet } from './db';
import {
  applyBackup, BackupUnreadableError, buildBackup, decodeBackup, encodeBackup, listSafetyCopies,
  DEFAULT_BACKUP_SETTINGS,
} from './backup';
import { loadSettings, weekTargets } from './settings';

const fixture = () => JSON.parse(fixtureText) as Record<string, unknown>;
const withoutCreatedAt = (o: Record<string, unknown>) => {
  const { createdAt: _c, ...rest } = o;
  return rest;
};

beforeEach(async () => {
  await Promise.all(db.tables.map((t) => t.clear()));
});

describe('backup', () => {
  it('restores populated 4.1 fields and re-exports them through the database', async () => {
    const input = decodeBackup(fixture41);
    await applyBackup(input);
    const output = decodeBackup(encodeBackup(await buildBackup()));
    expect({ ...output, createdAt: input.createdAt }).toEqual(input);
    expect(await db.bodyMeasurements.toArray()).toEqual(input.bodyMeasurements);
    expect((await db.sessions.get(2))?.finishedAt).toBeNull();
  });

  it('retains a safety copy when measurements are the only user data', async () => {
    await db.bodyMeasurements.add({ epochDay: 1, metric: 'WAIST', valueCm: 80, loggedAt: 1 });
    await applyBackup(decodeBackup(fixtureText));
    expect(decodeBackup((await listSafetyCopies())[0].json).bodyMeasurements).toHaveLength(1);
    expect(await db.bodyMeasurements.count()).toBe(0);
  });

  it('fills 4.0 defaults on restore and direct legacy row reads', async () => {
    await applyBackup(decodeBackup(fixtureText));
    expect((await db.exercises.get('bench_press'))?.unitOverride).toBe('');
    expect((await db.plans.get(1))?.scheduleMode).toBe('WEEKDAY');
    expect((await db.planItems.get(1))?.supersetGroup).toBeNull();
    const legacy = JSON.parse(fixtureText);
    await db.sets.put(legacy.sets[0]);
    expect((await db.sets.get(1))?.setType).toBe('WORKING');
    expect((await db.sets.toCollection().raw().first())?.setType).toBeUndefined();
    expect((await buildBackup()).bodyMeasurements).toEqual([]);
  });
  it('round-trips the Android fixture unchanged except createdAt', async () => {
    const settings = await applyBackup(decodeBackup(fixtureText));
    const text = encodeBackup(await buildBackup(settings));
    const again = JSON.parse(text) as Record<string, unknown>;
    expect(withoutCreatedAt(again)).toEqual(withoutCreatedAt(JSON.parse(encodeBackup(decodeBackup(fixtureText)))));
    expect(Object.keys(again)).toEqual(Object.keys(JSON.parse(encodeBackup(decodeBackup(fixtureText)))));
    expect(typeof again.createdAt).toBe('number');
    expect(decodeBackup(text).version).toBe(2);
  });

  it('keeps the planned sets saved on each workout, and an older backup without them restores with null', async () => {
    await applyBackup(decodeBackup(fixtureText));
    expect((await db.sessions.get(1))?.plannedSets).toBe(9);
    const old = fixtureText.replace(', "plannedSets": 9', '').replace(', "plannedSets": null', '');
    expect(old).not.toContain('plannedSets');
    await applyBackup(decodeBackup(old));
    expect((await db.sessions.get(1))?.plannedSets).toBeNull();
    expect((await db.sessions.get(2))?.plannedSets).toBeNull();
  });

  it('keeps when each set was ticked, and an older backup without it restores with null', async () => {
    await applyBackup(decodeBackup(fixtureText));
    expect((await db.sets.get(2))?.doneAt).toBe(1760891000000);
    expect((await db.sets.get(3))?.doneAt).toBeNull();
    const old = fixtureText.replace(/, "doneAt": (\d+|null)/g, '');
    expect(old).not.toContain('doneAt');
    await applyBackup(decodeBackup(old));
    expect((await db.sets.get(1))?.doneAt).toBeNull();
    expect((await db.sets.get(2))?.doneAt).toBeNull();
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

  it('carries "Just log" mode, and an older backup without it restores as false', async () => {
    const f = fixture() as { settings: Record<string, unknown> };
    f.settings.justLog = true;
    expect((await applyBackup(decodeBackup(JSON.stringify(f))))?.justLog).toBe(true);
    expect((await loadSettings()).justLog).toBe(true);
    expect((await buildBackup()).settings?.justLog).toBe(true);
    const old = fixtureText.replace(',"justLog":false', '');
    expect(old).not.toContain('justLog');
    expect(decodeBackup(old).settings?.justLog).toBe(false);
    await applyBackup(decodeBackup(old));
    expect((await loadSettings()).justLog).toBe(false);
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

  it('imports and re-exports food barcodes from Android backups', async () => {
    const f = fixture() as { foods: Record<string, unknown>[] };
    f.foods[0].barcode = '6281007012345';
    const settings = await applyBackup(decodeBackup(JSON.stringify(f)));
    expect((await db.foods.get(1))?.barcode).toBe('6281007012345');
    const out = await buildBackup(settings);
    expect(out.foods[0].barcode).toBe('6281007012345');
    expect(JSON.parse(encodeBackup(out)).foods[0].barcode).toBe('6281007012345');
  });

  it('restores Sunday weekTargets without double-shifting and preserves weekTargets on export', async () => {
    const f = fixture();
    f.weekTargets = { '20000': 4, '20007': 5, bad: 3, '20014': 9, '20021': 2.5 };
    f.weekStart = 'sunday';
    const decoded = decodeBackup(JSON.stringify(f));
    expect(decoded.weekTargets).toEqual({ '20000': 4, '20007': 5 });

    const settings = await applyBackup(decoded);
    const kvRow = await db.kv.get('weekTargets');
    expect(kvRow?.value).toEqual({ '20000': 4, '20007': 5, sundayWeeks: true });
    expect(await weekTargets()).toEqual(new Map([[20000, 4], [20007, 5]]));

    const exported = await buildBackup(settings);
    expect(exported.weekTargets).toEqual({ '20000': 4, '20007': 5 });
    expect(exported.weekStart).toBe('sunday');
  });

  it('shifts legacy weekTargets keys by -1 when weekStart is missing', async () => {
    const f = fixture();
    f.weekTargets = { '20001': 4, '20008': 6 };
    delete f.weekStart;
    await applyBackup(decodeBackup(JSON.stringify(f)));
    expect(await weekTargets()).toEqual(new Map([[20000, 4], [20007, 6]]));
    expect((await db.kv.get('weekTargets'))?.value).toEqual({ '20000': 4, '20007': 6, sundayWeeks: true });
  });

  it('writes { sundayWeeks: true } when restoring a backup without weekTargets', async () => {
    await db.kv.put({ key: 'weekTargets', value: { '20000': 3, sundayWeeks: true } });
    const f = fixture();
    delete f.weekTargets;
    delete f.weekStart;
    await applyBackup(decodeBackup(JSON.stringify(f)));
    expect((await db.kv.get('weekTargets'))?.value).toEqual({ sundayWeeks: true });
    expect(await weekTargets()).toEqual(new Map());
  });

  it('rejects wrong primitive types and invalid enums without touching existing data', async () => {
    const seed = decodeBackup(fixtureText);
    await applyBackup(seed);
    await db.kv.put({ key: 'weekTargets', value: { '20000': 4, sundayWeeks: true } });

    const badStartedAt = fixture() as { sessions: Record<string, unknown>[] };
    badStartedAt.sessions[0].startedAt = 'x';
    expect(() => decodeBackup(JSON.stringify(badStartedAt))).toThrow(BackupUnreadableError);
    await expect(applyBackup(badStartedAt as never)).rejects.toThrow(BackupUnreadableError);

    const badKcal = fixture() as { foods: Record<string, unknown>[] };
    badKcal.foods[0].kcal = '100';
    expect(() => decodeBackup(JSON.stringify(badKcal))).toThrow(BackupUnreadableError);

    const badEnum = fixture() as { exercises: Record<string, unknown>[] };
    badEnum.exercises[0].kind = 'CARDIO';
    expect(() => decodeBackup(JSON.stringify(badEnum))).toThrow(BackupUnreadableError);

    expect(await db.sessions.count()).toBe(2);
    expect(await db.foods.count()).toBe(1);
    expect(await weekTargets()).toEqual(new Map([[20000, 4]]));
  });

  it('keeps a safety copy when only plans exist on the device', async () => {
    await db.plans.add({ name: 'PPL', description: '', source: 'manual', active: true, createdAt: 1760000000000 });
    await applyBackup(decodeBackup(fixtureText));
    const copies = await listSafetyCopies();
    expect(copies).toHaveLength(1);
    expect(decodeBackup(copies[0].json).plans).toHaveLength(1);
  });

  it('defaults missing autoTicked to false and round-trips autoTicked true', async () => {
    const f = fixture() as { sets: Record<string, unknown>[] };
    delete f.sets[0].autoTicked;
    f.sets[1] = { ...f.sets[0], id: 99, setIndex: 1, autoTicked: true };
    const decoded = decodeBackup(JSON.stringify(f));
    expect(decoded.sets[0].autoTicked).toBe(false);
    expect(decoded.sets[1].autoTicked).toBe(true);

    await applyBackup(decoded);
    const out = await buildBackup(null);
    expect(out.sets.find((s) => s.id === decoded.sets[0].id)?.autoTicked).toBe(false);
    expect(out.sets.find((s) => s.id === 99)?.autoTicked).toBe(true);
  });

  it('applies catalog fixes on backup restore (Gate point 8) without touching custom or user-edited rows', async () => {
    const f = fixture() as { exercises: Record<string, unknown>[] };
    f.exercises.push(
      {
        id: 'kipping_muscle_up', name: 'Kipping Muscle Up', kind: 'REPS',
        primary: ['UPPER_BACK'], secondary: ['BICEPS'], videoUrl: null,
        custom: false, equipment: 'BODYWEIGHT', pattern: 'HORIZONTAL_PULL',
      },
      {
        id: 'db_bench_press', name: 'Dumbbell bench press', kind: 'WEIGHTED',
        primary: ['CHEST'], secondary: ['TRICEPS'], videoUrl: null,
        custom: false, equipment: 'DUMBBELLS,FLAT_BENCH', pattern: 'HORIZONTAL_PUSH',
      },
      {
        id: 'sled_row', name: 'Sled Row', kind: 'REPS',
        primary: ['UPPER_BACK'], secondary: ['BICEPS'], videoUrl: null,
        custom: false, equipment: 'CABLE_MACHINE', pattern: 'HORIZONTAL_PULL',
      },
      {
        id: 'sled_push', name: 'My Custom Sled', kind: 'HOLD',
        primary: ['QUADS'], secondary: [], videoUrl: null,
        custom: true, equipment: 'BODYWEIGHT', pattern: 'CARRY_CONDITIONING',
      },
    );
    await applyBackup(decodeBackup(JSON.stringify(f)));

    expect((await db.exercises.get('kipping_muscle_up'))?.equipment).toBe('PULL_UP_BAR');
    expect((await db.exercises.get('kipping_muscle_up'))?.pattern).toBe('VERTICAL_PULL');
    expect((await db.exercises.get('db_bench_press'))?.secondary).toEqual(['TRICEPS', 'SHOULDERS']);
    expect((await db.exercises.get('sled_row'))?.equipment).toBe('CABLE_MACHINE');
    expect((await db.exercises.get('sled_push'))?.equipment).toBe('BODYWEIGHT');
  });

  it('restores old backups without baselineTests and round-trips populated baselineTests', async () => {
    // Old backup (backup-4.1.json) without baselineTests field restores cleanly as empty.
    const legacy = JSON.parse(fixture41) as Record<string, unknown>;
    expect(legacy.baselineTests).toBeUndefined();
    const decodedLegacy = decodeBackup(fixture41);
    expect(decodedLegacy.baselineTests).toEqual([]);
    await applyBackup(decodedLegacy);
    expect(await db.baselineTests.count()).toBe(0);

    // Populated baselineTests round-trip through export and restore.
    const rows = [
      { id: 1, exerciseId: 'push_up', kind: 'REPS' as const, reps: 15, seconds: null, weightKg: null, createdAt: 1761000000000 },
      { id: 2, exerciseId: 'plank', kind: 'HOLD' as const, reps: null, seconds: 60, weightKg: null, createdAt: 1761000100000 },
      { id: 3, exerciseId: 'bench_press', kind: 'WEIGHTED' as const, reps: 8, seconds: null, weightKg: 60, createdAt: 1761000200000 },
    ];
    const withBaseline = decodeBackup(JSON.stringify({ ...legacy, baselineTests: rows }));
    expect(withBaseline.baselineTests).toEqual(rows);
    await applyBackup(withBaseline);
    expect(await db.baselineTests.toArray()).toEqual(rows);

    const exported = decodeBackup(encodeBackup(await buildBackup()));
    expect(exported.baselineTests).toEqual(rows);

    // Invalid kind is rejected.
    expect(() => decodeBackup(JSON.stringify({
      ...legacy,
      baselineTests: [{ id: 1, exerciseId: 'push_up', kind: 'CARDIO', reps: 10, seconds: null, weightKg: null, createdAt: 1 }],
    }))).toThrow(BackupUnreadableError);
  });

  it('keeps a safety copy when only baselineTests exist on the device', async () => {
    await db.baselineTests.add({ exerciseId: 'push_up', kind: 'REPS', reps: 12, seconds: null, weightKg: null, createdAt: 1 });
    await applyBackup(decodeBackup(fixture41));
    const copies = await listSafetyCopies();
    expect(copies).toHaveLength(1);
    expect(decodeBackup(copies[0].json).baselineTests).toHaveLength(1);
  });
});
