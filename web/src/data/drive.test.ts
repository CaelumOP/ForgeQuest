import 'fake-indexeddb/auto';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import fixtureText from './fixtures/android-backup.json?raw';
import { db } from './db';
import { applyBackup, decodeBackup } from './backup';
import { BACKUP_NAME, DriveError, autoBackup, backupNow, connectDrive, disconnectDrive, fetchDriveBackup, isDeviceEmpty, lastActivityAt } from './drive';
import { autoBackupEffective, loadSettings, setAutoBackup, updateSettings } from './settings';

type Call = { url: string; method: string; body?: string };
let calls: Call[];

/** A fake Drive: `existing` is the backup file id already in appDataFolder (or null), `stored` its text. */
function fakeDrive(existing: string | null, stored = '') {
  calls = [];
  vi.stubGlobal('fetch', async (url: string, init: RequestInit = {}) => {
    calls.push({ url, method: init.method ?? 'GET', body: init.body as string | undefined });
    if (url.includes('/files?spaces=appDataFolder')) return Response.json({ files: existing ? [{ id: existing }] : [] });
    if (url.includes('alt=media')) return new Response(stored);
    if (url.includes('/upload/')) return Response.json({ id: existing ?? 'new' });
    return new Response('', { status: 404 });
  });
}

beforeEach(async () => { await Promise.all(db.tables.map((t) => t.clear())); });
afterEach(() => { vi.unstubAllGlobals(); });

describe('drive backup', () => {
  it('creates the backup in appDataFolder, then updates the same file', async () => {
    await applyBackup(decodeBackup(fixtureText));
    fakeDrive(null);
    expect(await backupNow('tok')).toBe(true);
    const create = calls.find((c) => c.method === 'POST')!;
    expect(create.url).toContain('/upload/drive/v3/files?uploadType=multipart');
    expect(create.body).toContain(`"name":"${BACKUP_NAME}","parents":["appDataFolder"]`);
    expect(decodeBackup(create.body!.split('\r\n')[7]).sessions.length).toBeGreaterThan(0);
    expect((await loadSettings()).lastBackupAt).toBeGreaterThan(0);

    fakeDrive('abc');
    await backupNow('tok');
    expect(calls.find((c) => c.method === 'PATCH')!.url).toContain('/files/abc?uploadType=multipart');
  });

  it('never overwrites a Drive backup from an empty phone', async () => {
    fakeDrive('abc');
    expect(await backupNow('tok')).toBe(false);
    expect(calls.some((c) => c.method !== 'GET')).toBe(false);
  });

  it('reads an Android backup from Drive, and reports when there is none', async () => {
    fakeDrive('abc', fixtureText);
    expect((await fetchDriveBackup('tok')).sessions.length).toBe(decodeBackup(fixtureText).sessions.length);
    fakeDrive(null);
    await expect(fetchDriveBackup('tok')).rejects.toMatchObject({ kind: 'no-backup' });
  });

  it('turns HTTP failures into a DriveError with the status', async () => {
    vi.stubGlobal('fetch', async () => new Response('', { status: 500 }));
    await expect(backupNow('tok')).rejects.toBeInstanceOf(DriveError);
    await expect(fetchDriveBackup('tok')).rejects.toMatchObject({ kind: 'http', status: 500 });
  });

  it('knows the newest workout, meal or weight time', async () => {
    expect(await lastActivityAt()).toBe(0);
    await applyBackup(decodeBackup(fixtureText));
    expect(await lastActivityAt()).toBeGreaterThan(0);
  });

  it('detects empty device ignoring seeded catalog rows', async () => {
    await db.exercises.put({
      id: 'push_up', name: 'Push-up', kind: 'REPS', primary: ['CHEST'], secondary: [],
      videoUrl: null, custom: false, equipment: '', pattern: '', setupNote: '', aliases: '',
      unitOverride: '', pinned: false, mediaKey: '',
    });
    const { buildBackup } = await import('./backup');
    expect(isDeviceEmpty(await buildBackup())).toBe(true);

    await db.bodyMeasurements.add({ epochDay: 20000, metric: 'WAIST', valueCm: 82, loggedAt: 1 });
    expect(isDeviceEmpty(await buildBackup())).toBe(false);
  });

  it('offers restore once per account on empty device without uploading, then remembers opt-out across reconnects', async () => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => { store.set(k, v); },
      removeItem: (k: string) => { store.delete(k); },
      clear: () => { store.clear(); },
    });
    let fetchCalls = 0;
    let uploads = 0;
    vi.stubGlobal('fetch', async (url: string) => {
      fetchCalls++;
      if (url.includes('/about')) return Response.json({ user: { emailAddress: 'User@Example.com' } });
      if (url.includes('/files?spaces=appDataFolder')) return Response.json({ files: [{ id: 'abc', modifiedTime: '2026-11-10T00:00:00Z' }] });
      if (url.includes('alt=media')) return new Response(fixtureText);
      if (url.includes('/upload/')) {
        uploads++;
        return Response.json({ id: 'abc', modifiedTime: '2026-11-10T01:00:00Z' });
      }
      return new Response('', { status: 404 });
    });
    vi.stubGlobal('window', {
      google: {
        accounts: {
          oauth2: {
            initTokenClient: (c: { callback: (r: { access_token: string; expires_in: number }) => void }) => ({
              requestAccessToken: () => c.callback({ access_token: 'tok1', expires_in: 3600 }),
            }),
            revoke: () => undefined,
          },
        },
      },
    });

    const first = await connectDrive();
    expect(first.kind).toBe('offer-restore');
    expect(uploads).toBe(0);
    expect(fetchCalls).toBeGreaterThan(0);
    expect(autoBackupEffective(await loadSettings())).toBe(true);

    // Opt out and disconnect; reconnecting must remember the opt-out.
    await setAutoBackup(false);
    expect(autoBackupEffective(await loadSettings())).toBe(false);
    await disconnectDrive();
    const second = await connectDrive();
    expect(second).toEqual({ kind: 'backed-up', backedUp: false, autoBackupOn: false });
    expect(uploads).toBe(0);
    expect((await loadSettings()).autoBackupOptedOut).toBe(true);

    // autoBackup skips while opted out
    await autoBackup('workout');
    expect(uploads).toBe(0);
  });

  it('blocks overwrite when remote backup was modified by another device', async () => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => { store.set(k, v); },
      removeItem: (k: string) => { store.delete(k); },
      clear: () => { store.clear(); },
    });
    await applyBackup(decodeBackup(fixtureText));
    await updateSettings((s) => ({ ...s, driveEmail: 'sync@example.com' }));
    fakeDrive('abc', fixtureText);
    await fetchDriveBackup('tok');
    // Simulate remote file modified externally after we last read/wrote it.
    vi.stubGlobal('fetch', async (url: string) => {
      if (url.includes('/files?spaces=appDataFolder')) return Response.json({ files: [{ id: 'abc', modifiedTime: '2026-11-12T00:00:00Z' }] });
      return new Response('', { status: 404 });
    });
    store.set('fq.drive.remoteMod.sync@example.com', '2026-11-10T00:00:00Z');
    await expect(backupNow('tok')).rejects.toMatchObject({ kind: 'remote-newer' });
  });
});
