import type { SessionRow } from '../../data/db'

export type Debrief = { score: number; summary: string; nextTime: string[] }

/** Android WorkoutDebrief.decode: null for blank or broken text or an empty summary. */
export function decodeDebrief(text: string): Debrief | null {
  if (!text.trim()) return null
  try {
    const raw = JSON.parse(text) as Record<string, unknown>
    const summary = typeof raw.summary === 'string' ? raw.summary.trim() : ''
    if (!summary) return null
    const score = Math.min(10, Math.max(4, Math.round(Number(raw.score) || 0)))
    const nextTime = Array.isArray(raw.nextTime) ? raw.nextTime.filter((x): x is string => typeof x === 'string' && x.trim() !== '').map((x) => x.trim()) : []
    return { score, summary, nextTime }
  } catch { return null }
}

/** Epoch day of the first day of the week containing `day`. firstDow: 0 = Monday ... 6 = Sunday. 1970-01-01 was a Thursday. */
export function weekStart(day: number, firstDow: number): number {
  const dow = (((day + 3) % 7) + 7) % 7
  return day - ((dow - firstDow + 7) % 7)
}

export type Week = { start: number; sessions: SessionRow[] }

/** Newest sessions first, grouped by week, weeks newest first. */
export function groupWeeks(sessions: SessionRow[], firstDow: number): Week[] {
  const sorted = [...sessions].sort((a, b) => b.startedAt - a.startedAt)
  const map = new Map<number, SessionRow[]>()
  for (const s of sorted) {
    const k = weekStart(s.epochDay, firstDow)
    map.set(k, [...(map.get(k) ?? []), s])
  }
  return [...map.entries()].sort((a, b) => b[0] - a[0]).map(([start, list]) => ({ start, sessions: list }))
}

export const minutesOf = (s: Pick<SessionRow, 'startedAt' | 'finishedAt'>): number | null =>
  s.finishedAt == null ? null : Math.max(1, Math.floor((s.finishedAt - s.startedAt) / 60_000))

export const isoOfDay = (day: number): string => new Date(day * 86_400_000).toISOString().slice(0, 10)
export function dayOfIso(iso: string): number | null {
  const ms = Date.parse(`${iso}T00:00:00Z`)
  return Number.isNaN(ms) ? null : Math.round(ms / 86_400_000)
}
