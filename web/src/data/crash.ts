// The last crash, kept on this phone only (like the Android app): time, app version, browser, message and stack.
// No user data and no network. On the next launch the app offers to share it once (CrashPrompt.tsx).

export type CrashReport = { time: number; version: string; userAgent: string; message: string; stack: string }
type Store = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

export const CRASH_KEY = 'fq.lastCrash'
const MAX_STACK = 4000

/** Which build crashed: the hashed bundle name (index-AbC123.js) names the exact deploy. */
function buildId(): string {
  try { return new URL(import.meta.url).pathname.split('/').pop() || '?' } catch { return '?' }
}

function defaultStore(): Store | null {
  try { return globalThis.localStorage ?? null } catch { return null }
}

/** Saves `err` as the last crash, replacing any older one. Never throws. */
export function saveCrash(err: unknown, store: Store | null = defaultStore(), now = Date.now()): void {
  try {
    if (!store) return
    const e = err instanceof Error ? err : null
    const report: CrashReport = {
      time: now,
      version: buildId(),
      userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : '',
      message: e ? `${e.name}: ${e.message}` : String(err),
      stack: (e?.stack ?? '').slice(0, MAX_STACK),
    }
    store.setItem(CRASH_KEY, JSON.stringify(report))
  } catch { /* storage full or blocked: nothing to do */ }
}

/** The saved crash, or null when there is none (or it can't be read). */
export function loadCrash(store: Store | null = defaultStore()): CrashReport | null {
  try {
    const raw = store?.getItem(CRASH_KEY)
    if (!raw) return null
    const r = JSON.parse(raw) as Partial<CrashReport>
    return typeof r.time === 'number' && typeof r.message === 'string' ? (r as CrashReport) : null
  } catch { return null }
}

export function clearCrash(store: Store | null = defaultStore()): void {
  try { store?.removeItem(CRASH_KEY) } catch { /* blocked */ }
}

/** Plain text for the share sheet or clipboard. */
export function crashText(r: CrashReport): string {
  return [
    'ForgeQuest Web crash report',
    `Time: ${new Date(r.time).toISOString()}`,
    `Version: ${r.version}`,
    `Browser: ${r.userAgent}`,
    `Error: ${r.message}`,
    r.stack,
  ].filter(Boolean).join('\n')
}
