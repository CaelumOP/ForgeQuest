import 'fake-indexeddb/auto';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import fixtureText from './fixtures/android-backup.json?raw';
import { db } from './db';
import { applyBackup, decodeBackup } from './backup';
import { BACKUP_NAME, DriveError, backupNow, fetchDriveBackup, lastActivityAt } from './drive';
import { loadSettings } from './settings';

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
});
