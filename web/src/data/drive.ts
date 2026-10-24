// Google Drive backup, same file as Android DriveSync.kt: "forgequest_backup.json" in the app's hidden
// appDataFolder. Both apps use OAuth clients of the same Cloud project, so they share that folder and
// either app can restore the other's backup. Tokens come from Google Identity Services (GIS) and last
// about an hour; a fresh one needs a tap (iPhone blocks the sign-in popup without one).
import { GOOGLE_CLIENT_ID } from '../config'
import { buildBackup, decodeBackup, encodeBackup, type BackupFile } from './backup'
import { db } from './db'
import { loadSettings, updateSettings } from './settings'

const SCOPE = 'https://www.googleapis.com/auth/drive.appdata'
const API = 'https://www.googleapis.com/drive/v3'
const UPLOAD = 'https://www.googleapis.com/upload/drive/v3'
export const BACKUP_NAME = 'forgequest_backup.json'
const TOKEN_KEY = 'fq.drive.token'
/** On open, back up only when the last one is older than this. After a workout, always. */
const OPEN_BACKUP_AGE_MS = 6 * 3_600_000

export type DriveErrorKind = 'offline' | 'cancelled' | 'http' | 'no-backup'
export class DriveError extends Error {
  constructor(readonly kind: DriveErrorKind, readonly status = 0) { super(`drive ${kind} ${status}`); this.name = 'DriveError' }
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

async function findBackupId(token: string): Promise<string | null> {
  const q = encodeURIComponent(`name='${BACKUP_NAME}'`)
  const body = await (await call(token, `${API}/files?spaces=appDataFolder&q=${q}&fields=files(id)`)).json() as { files?: { id: string }[] }
  return body.files?.[0]?.id ?? null
}

export async function accountEmail(token: string): Promise<string> {
  const body = await (await call(token, `${API}/about?fields=user(emailAddress)`)).json() as { user?: { emailAddress?: string } }
  return body.user?.emailAddress ?? ''
}

const isEmpty = (f: BackupFile) => !f.sessions.length && !f.meals.length && !f.weights.length

/**
 * Uploads this phone's data over the Drive backup. Returns false, writing nothing, when this phone has no
 * workouts, meals or weights and Drive already holds a backup: a new phone must never wipe the old backup.
 */
export async function backupNow(token: string): Promise<boolean> {
  const file = await buildBackup()
  const existing = await findBackupId(token)
  if (existing && isEmpty(file)) return false
  const boundary = `fq${Date.now().toString(36)}`
  const meta = existing ? { name: BACKUP_NAME } : { name: BACKUP_NAME, parents: ['appDataFolder'] }
  const body = [
    `--${boundary}`, 'Content-Type: application/json; charset=UTF-8', '', JSON.stringify(meta),
    `--${boundary}`, 'Content-Type: application/json', '', encodeBackup(file),
    `--${boundary}--`, '',
  ].join('\r\n')
  await call(token, existing ? `${UPLOAD}/files/${existing}?uploadType=multipart` : `${UPLOAD}/files?uploadType=multipart`, {
    method: existing ? 'PATCH' : 'POST',
    headers: { 'Content-Type': `multipart/related; boundary=${boundary}` },
    body,
  })
  await updateSettings((s) => ({ ...s, lastBackupAt: Date.now() }))
  return true
}

/** Downloads the Drive backup without applying it, so the person can see what's in it first. */
export async function fetchDriveBackup(token: string): Promise<BackupFile> {
  const id = await findBackupId(token)
  if (!id) throw new DriveError('no-backup')
  return decodeBackup(await (await call(token, `${API}/files/${id}?alt=media`)).text())
}

// ---------------------------------------------------------------- Flows

export type ConnectResult = { kind: 'backed-up' } | { kind: 'offer-restore'; file: BackupFile }

/**
 * Connect from a tap: sign in, remember the account, then back up. On a phone with no data and a backup
 * already in Drive (a new phone), returns that backup so the person can restore it instead.
 */
export async function connectDrive(): Promise<ConnectResult> {
  const token = await requestToken()
  const email = await accountEmail(token)
  await updateSettings((s) => ({ ...s, driveEmail: email }))
  if (isEmpty(await buildBackup())) {
    const id = await findBackupId(token)
    if (id) return { kind: 'offer-restore', file: await fetchDriveBackup(token) }
  }
  await backupNow(token)
  return { kind: 'backed-up' }
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
 * Background backup (app open, finished workout): only when connected and a token is still valid; never
 * opens a popup. On open it skips when the last backup is recent. Failures stay quiet: the "Tap to back
 * up" pill shows while there is newer data than the last backup.
 */
export async function autoBackup(reason: 'open' | 'workout'): Promise<void> {
  const s = await loadSettings()
  const token = validToken()
  if (!s.driveEmail || !token) return
  if (reason === 'open' && Date.now() - s.lastBackupAt < OPEN_BACKUP_AGE_MS) return
  try { await backupNow(token) } catch (e) { console.warn('auto backup failed', e) }
}

/** Newest change time among workouts, meals and weights: newer than lastBackupAt means not yet backed up. */
export async function lastActivityAt(): Promise<number> {
  const [session, meal, weight] = await Promise.all([
    db.sessions.orderBy('finishedAt').last(),
    db.meals.orderBy('loggedAt').last(),
    db.weights.toArray().then((w) => w.reduce((m, x) => Math.max(m, x.loggedAt), 0)),
  ])
  return Math.max(session?.finishedAt ?? 0, meal?.loggedAt ?? 0, weight)
}
