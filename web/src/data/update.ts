// Port of Android UpdateNotice.kt / UpdateChecker.kt for post-update "What's new" notes.
export const VERSION_URL = 'https://raw.githubusercontent.com/CaelumOP/ForgeQuest/main/apk/version.json';
const SEEN_VERSION_KEY = 'fq.whatsNew.seenVersion';

export interface LatestVersion {
  versionName: string;
  notes: string;
}

/** Compares dotted version strings (e.g. "4.2.0" vs "4.1.0"). Returns >0 if a > b. */
export function compareVersions(a: string, b: string): number {
  const pa = a.trim().replace(/^v/i, '').split('.').map((n) => Number.parseInt(n, 10) || 0);
  const pb = b.trim().replace(/^v/i, '').split('.').map((n) => Number.parseInt(n, 10) || 0);
  const len = Math.max(pa.length, pb.length);
  for (let i = 0; i < len; i++) {
    const diff = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (diff !== 0) return diff;
  }
  return 0;
}

export function parseLatestVersion(raw: string): LatestVersion | null {
  let root: unknown;
  try { root = JSON.parse(raw); } catch { return null; }
  if (typeof root !== 'object' || root === null || Array.isArray(root)) return null;
  const o = root as Record<string, unknown>;
  if (typeof o.versionName !== 'string' || typeof o.notes !== 'string') return null;
  return { versionName: o.versionName.trim(), notes: o.notes };
}

export async function fetchLatestVersion(): Promise<LatestVersion | null> {
  try {
    const r = await fetch(VERSION_URL, { cache: 'no-store' });
    if (!r.ok) return null;
    return parseLatestVersion(await r.text());
  } catch {
    return null;
  }
}

/**
 * Pure What's New decision matching Android UpdateNotice.kt:
 * - First install (`seenVersion == null`): record `currentVersion`, show nothing.
 * - Same or older version: show nothing.
 * - Updated (`currentVersion > seenVersion`): record `currentVersion`, fetch version.json, and show
 *   only when `latest.versionName === currentVersion` and `notes` is non-blank.
 */
export async function resolveWhatsNew(
  seenVersion: string | null,
  currentVersion: string,
  fetcher: () => Promise<LatestVersion | null> = fetchLatestVersion,
): Promise<{ nextSeen: string; whatsNew: LatestVersion | null }> {
  if (seenVersion == null || !seenVersion.trim()) {
    return { nextSeen: currentVersion, whatsNew: null };
  }
  if (compareVersions(currentVersion, seenVersion) <= 0) {
    return { nextSeen: seenVersion === currentVersion ? seenVersion : currentVersion, whatsNew: null };
  }
  const latest = await fetcher().catch(() => null);
  const whatsNew = latest && latest.versionName === currentVersion && latest.notes.trim() !== '' ? latest : null;
  return { nextSeen: currentVersion, whatsNew };
}

let sessionCheck: Promise<LatestVersion | null> | null = null;

/** Checks once per app load whether the web app updated to a new version with release notes. */
export function checkWhatsNew(currentVersion = __APP_VERSION__): Promise<LatestVersion | null> {
  sessionCheck ??= (async () => {
    let seen: string | null = null;
    try { seen = localStorage.getItem(SEEN_VERSION_KEY); } catch { return null; }
    const { nextSeen, whatsNew } = await resolveWhatsNew(seen, currentVersion);
    try { localStorage.setItem(SEEN_VERSION_KEY, nextSeen); } catch { /* ignore */ }
    return whatsNew;
  })();
  return sessionCheck;
}
