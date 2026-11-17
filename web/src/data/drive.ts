// Google Drive backup, same file as Android DriveSync.kt: "forgequest_backup.json" in the app's hidden
// appDataFolder. Both apps use OAuth clients of the same Cloud project, so they share that folder and
// either app can restore the other's backup. Tokens come from Google Identity Services (GIS) and last
// about an hour; a fresh one needs a tap (iPhone blocks the sign-in popup without one).
import { GOOGLE_CLIENT_ID } from '../config'
import { applyBackup, buildBackup, decodeBackup, encodeBackup, type BackupFile } from './backup'
import { db } from './db'
import { autoBackupEffective, loadSettings, markRestoreOfferShown, updateSettings } from './settings'

const SCOPE = 'https://www.googleapis.com/auth/drive.appdata'
const API = 'https://www.googleapis.com/drive/v3'
const UPLOAD = 'https://www.googleapis.com/upload/drive/v3'
export const BACKUP_NAME = 'forgequest_backup.json'
const TOKEN_KEY = 'fq.drive.token'
const REMOTE_MOD_PREFIX = 'fq.drive.remoteMod.'
/** On open, back up only when the last one is older than this. After a workout, always. */
const OPEN_BACKUP_AGE_MS = 6 * 3_600_000

export type DriveErrorKind = 'offline' | 'cancelled' | 'http' | 'no-backup' | 'remote-newer'
export class DriveError extends Error {
  constructor(readonly kind: DriveErrorKind, readonly status = 0) { super(`drive ${kind} ${status}`); this.name = 'DriveError' }
}

// ---------------------------------------------------------------- Serialization & policy

let syncQueue: Promise<unknown> = Promise.resolve()

/** Serializes backup, fetch, connect, and restore operations so concurrent triggers never race. */
export function withSyncLock<T>(job: () => Promise<T>): Promise<T> {
  const next = syncQueue.then(job, job)
  syncQueue = next.catch(() => undefined)
  return next
}

/** True when this device holds no meaningful user data (ignores seeded built-in exercises). */
export function isDeviceEmpty(f: BackupFile): boolean {
  return (
    !f.sessions.length &&
    !f.meals.length &&
    !f.weights.length &&
    !f.plans.length &&
    !f.foods.length &&
    !(f.bodyMeasurements?.length) &&
    !f.exercises.some((e) => e.custom)
  )
}

export function normalizeAccountKey(email: string): string {
  return email.trim().toLowerCase() || 'connected'
}

export function shouldOfferRestore(
  local: BackupFile,
  hasRemoteBackup: boolean,
  email: string,
  restoreOfferShown: readonly string[],
): boolean {
  return hasRemoteBackup && isDeviceEmpty(local) && !restoreOfferShown.includes(normalizeAccountKey(email))
}

export function needsChoice(
  knownModifiedTime: string | null,
  remoteExists: boolean,
  remoteModifiedTime?: string | null,
): boolean {
  if (!remoteExists) return false
  if (!knownModifiedTime) return true
  if (!remoteModifiedTime) return false
  return remoteModifiedTime !== knownModifiedTime
}

export function isRemoteNewer(knownModifiedTime: string | null, remoteModifiedTime: string | null | undefined): boolean {
  return needsChoice(knownModifiedTime, remoteModifiedTime != null, remoteModifiedTime)
}

function getKnownRemoteMod(accountKey: string): string | null {
  try { return localStorage.getItem(`${REMOTE_MOD_PREFIX}${accountKey}`) } catch { return null }
}

function setKnownRemoteMod(accountKey: string, mod: string | null | undefined): void {
  if (!mod) return
  try { localStorage.setItem(`${REMOTE_MOD_PREFIX}${accountKey}`, mod) } catch { /* ignore */ }
}

// ---------------------------------------------------------------- Token

type Gis = {
  accounts: { oauth2: {
    initTokenClient(c: {
      client_id: string; scope: string; hint?: string
      callback: (r: { access_token?: string; expires_in?: number; error?: string }) => void
      error_callback?: (e: unknown) => void
    }): { requestAccessToken(o?: { prompt?: string }): void }
    revoke(token: string, done?: () => void): void
  } }
}
const gis = () => (window as unknown as { google?: Gis }).google

let gisLoading: Promise<void> | null = null
/** Loads the Google sign-in script. Call early (Profile open, app start when connected) so a later tap can open the popup at once. */
export function preloadGis(): Promise<void> {
  if (gis()?.accounts?.oauth2) return Promise.resolve()
  gisLoading ??= new Promise<void>((resolve, reject) => {
    const s = document.createElement('script')
    s.src = 'https://accounts.google.com/gsi/client'
    s.async = true
    s.onload = () => resolve()
    s.onerror = () => { gisLoading = null; reject(new DriveError('offline')) }
    document.head.appendChild(s)
  })
  return gisLoading
}

type Saved = { token: string; exp: number }
function savedToken(): Saved | null {
  try {
    const v = JSON.parse(localStorage.getItem(TOKEN_KEY) ?? 'null') as Saved | null
    return v && typeof v.token === 'string' && v.exp > Date.now() + 60_000 ? v : null
  } catch { return null }
}
function saveToken(v: Saved | null) {
  try { v ? localStorage.setItem(TOKEN_KEY, JSON.stringify(v)) : localStorage.removeItem(TOKEN_KEY) } catch { /* kept in memory only */ }
}

/** A token that is still good for at least a minute, or null (then a tap is needed). */
export const validToken = (): string | null => savedToken()?.token ?? null

/** Opens Google's sign-in popup. Must run straight from a tap. */
export async function requestToken(hint?: string): Promise<string> {
  await preloadGis()
  const oauth2 = gis()!.accounts.oauth2
  const r = await new Promise<{ access_token?: string; expires_in?: number; error?: string }>((resolve, reject) => {
    oauth2.initTokenClient({
      client_id: GOOGLE_CLIENT_ID, scope: SCOPE, hint,
      callback: resolve,
      error_callback: () => reject(new DriveError('cancelled')),
    }).requestAccessToken({ prompt: hint ? '' : 'select_account' })
  })
  if (!r.access_token) throw new DriveError('cancelled')
  saveToken({ token: r.access_token, exp: Date.now() + (r.expires_in ?? 3600) * 1000 })
  return r.access_token
}

// ---------------------------------------------------------------- Drive calls

async function call(token: string, url: string, init: RequestInit = {}): Promise<Response> {
  let r: Response
  try {
    r = await fetch(url, { ...init, headers: { ...init.headers, Authorization: `Bearer ${token}` } })
  } catch {
    throw new DriveError('offline')
  }
  if (r.status === 401) saveToken(null) // expired or revoked: the next backup asks for a tap
  if (!r.ok) throw new DriveError('http', r.status)
  return r
}

type RemoteFileInfo = { id: string; modifiedTime?: string }

async function findBackupInfo(token: string): Promise<RemoteFileInfo | null> {
  const q = encodeURIComponent(`name='${BACKUP_NAME}'`)
  const body = await (await call(token, `${API}/files?spaces=appDataFolder&q=${q}&fields=files(id,modifiedTime)`)).json() as { files?: RemoteFileInfo[] }
  return body.files?.[0] ?? null
}

export async function accountEmail(token: string): Promise<string> {
  const body = await (await call(token, `${API}/about?fields=user(emailAddress)`)).json() as { user?: { emailAddress?: string } }
  return body.user?.emailAddress ?? ''
}

async function backupInternal(token: string, accountKey: string, forceOverwrite = false): Promise<boolean> {
  const file = await buildBackup()
  const existing = await findBackupInfo(token)
  if (existing && isDeviceEmpty(file)) return false
  if (!forceOverwrite && existing && needsChoice(getKnownRemoteMod(accountKey), true, existing.modifiedTime)) {
    throw new DriveError('remote-newer')
  }
  const boundary = `fq${Date.now().toString(36)}`
  const meta = existing ? { name: BACKUP_NAME } : { name: BACKUP_NAME, parents: ['appDataFolder'] }
  const body = [
    `--${boundary}`, 'Content-Type: application/json; charset=UTF-8', '', JSON.stringify(meta),
    `--${boundary}`, 'Content-Type: application/json', '', encodeBackup(file),
    `--${boundary}--`, '',
  ].join('\r\n')
  const resp = await call(
    token,
    existing
      ? `${UPLOAD}/files/${existing.id}?uploadType=multipart&fields=id,modifiedTime`
      : `${UPLOAD}/files?uploadType=multipart&fields=id,modifiedTime`,
    {
      method: existing ? 'PATCH' : 'POST',
      headers: { 'Content-Type': `multipart/related; boundary=${boundary}` },
      body,
    },
  )
  try {
    const info = await resp.json() as { modifiedTime?: string }
    setKnownRemoteMod(accountKey, info.modifiedTime)
  } catch { /* mocked responses without JSON body or modifiedTime */ }
  await updateSettings((s) => ({ ...s, lastBackupAt: Date.now() }))
  return true
}

/**
 * Uploads this phone's data over the Drive backup. Returns false, writing nothing, when this phone has no
 * meaningful user data and Drive already holds a backup: a new phone must never wipe the old backup.
 */
export function backupNow(token: string, forceOverwrite = false): Promise<boolean> {
  return withSyncLock(async () => {
    const s = await loadSettings()
    return backupInternal(token, normalizeAccountKey(s.driveEmail), forceOverwrite)
  })
}

async function fetchByInfo(token: string, info: RemoteFileInfo): Promise<BackupFile> {
  return decodeBackup(await (await call(token, `${API}/files/${info.id}?alt=media`)).text())
}

export type DriveBackupPreview = { file: BackupFile; accountKey: string; modifiedTime: string | null }
export type DriveRestoreMeta = { accountKey?: string; modifiedTime: string | null }

/** Downloads the Drive backup without applying it or marking it seen, so the person can preview it first. */
export function fetchDriveBackupPreview(token: string): Promise<DriveBackupPreview> {
  return withSyncLock(async () => {
    const info = await findBackupInfo(token)
    if (!info) throw new DriveError('no-backup')
    const s = await loadSettings()
    const file = await fetchByInfo(token, info)
    return { file, accountKey: normalizeAccountKey(s.driveEmail), modifiedTime: info.modifiedTime ?? null }
  })
}

/** Downloads the Drive backup without applying it, so the person can see what's in it first. */
export async function fetchDriveBackup(token: string): Promise<BackupFile> {
  return (await fetchDriveBackupPreview(token)).file
}

/** Serializes restoring a backup against any background or manual upload. Records Drive modifiedTime only on confirmed Drive restores. */
export function restoreSerialized(file: BackupFile, driveMeta?: DriveRestoreMeta): Promise<unknown> {
  return withSyncLock(async () => {
    const res = await applyBackup(file)
    if (driveMeta?.modifiedTime) {
      const key = driveMeta.accountKey ?? normalizeAccountKey((await loadSettings()).driveEmail)
      setKnownRemoteMod(key, driveMeta.modifiedTime)
    }
    return res
  })
}

// ---------------------------------------------------------------- Flows

export type ConnectResult =
  | { kind: 'backed-up'; backedUp: boolean; autoBackupOn: boolean }
  | { kind: 'offer-restore'; file: BackupFile; accountKey: string; modifiedTime: string | null }
  | { kind: 'conflict' }

/**
 * Connect from a tap: sign in, remember the account, inspect remote backup, offer one-tap restore once per
 * account/device when empty, surface a conflict when an unseen remote exists and an upload would follow, and only then upload.
 */
export function connectDrive(): Promise<ConnectResult> {
  return withSyncLock(async () => {
    const token = await requestToken()
    const email = await accountEmail(token)
    const accountKey = normalizeAccountKey(email)
    const updated = await updateSettings((s) => ({ ...s, driveEmail: email || 'connected' }))
    const [local, existing] = await Promise.all([buildBackup(), findBackupInfo(token)])

    if (shouldOfferRestore(local, existing != null, accountKey, updated.restoreOfferShown)) {
      await markRestoreOfferShown(accountKey)
      const file = await fetchByInfo(token, existing!)
      return { kind: 'offer-restore', file, accountKey, modifiedTime: existing!.modifiedTime ?? null }
    }

    // Only when an upload would follow: never overwrite a remote this device has not seen without asking.
    if (existing && autoBackupEffective(updated) && needsChoice(getKnownRemoteMod(accountKey), true, existing.modifiedTime)) {
      return { kind: 'conflict' }
    }

    if (autoBackupEffective(updated)) {
      const backedUp = await backupInternal(token, accountKey, false)
      return { kind: 'backed-up', backedUp, autoBackupOn: true }
    }
    return { kind: 'backed-up', backedUp: false, autoBackupOn: false }
  })
}

export async function disconnectDrive(): Promise<void> {
  const token = validToken()
  saveToken(null)
  if (token) gis()?.accounts.oauth2.revoke(token)
  await updateSettings((s) => ({ ...s, driveEmail: '' }))
}

/** A token from storage, or a new one from a tap (signed in to the same account). */
export async function tokenForTap(): Promise<string> {
  return validToken() ?? requestToken((await loadSettings()).driveEmail || undefined)
}

/**
 * Background backup (app open, finished workout): only when connected, auto-backup is effective, and a token
 * is still valid; never opens a popup. On open it skips when the last backup is recent.
 */
export async function autoBackup(reason: 'open' | 'workout'): Promise<void> {
  const s = await loadSettings()
  const token = validToken()
  if (!autoBackupEffective(s) || !token) return
  if (reason === 'open' && Date.now() - s.lastBackupAt < OPEN_BACKUP_AGE_MS) return
  try {
    await withSyncLock(() => backupInternal(token, normalizeAccountKey(s.driveEmail), false))
  } catch (e) {
    if (e instanceof DriveError && e.kind === 'remote-newer') return
    console.warn('auto backup failed', e)
  }
}

/** Newest change time among workouts, meals, weights and measurements: newer than lastBackupAt means not yet backed up. */
export async function lastActivityAt(): Promise<number> {
  const [session, meal, weight, meas] = await Promise.all([
    db.sessions.orderBy('finishedAt').last(),
    db.meals.orderBy('loggedAt').last(),
    db.weights.toArray().then((w) => w.reduce((m, x) => Math.max(m, x.loggedAt), 0)),
    db.bodyMeasurements.toArray().then((b) => b.reduce((m, x) => Math.max(m, x.loggedAt), 0)),
  ])
  return Math.max(session?.finishedAt ?? 0, meal?.loggedAt ?? 0, weight, meas)
}
