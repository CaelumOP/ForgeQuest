import type { SessionRow } from '../../data/db'
import { plural, t } from '../../i18n'
import type { WorkoutScoreResult } from '../../domain/workout-score'

export type Debrief = { summary: string; nextTime: string[] }

/** Android WorkoutDebrief.decode: null for blank or broken text or an empty summary. */
export function decodeDebrief(text: string): Debrief | null {
  if (!text.trim()) return null
  try {
    const raw = JSON.parse(text) as Record<string, unknown>
    const summary = typeof raw.summary === 'string' ? raw.summary.trim() : ''
    if (!summary) return null
    const nextTime = Array.isArray(raw.nextTime) ? raw.nextTime.filter((x): x is string => typeof x === 'string' && x.trim() !== '').map((x) => x.trim()) : []
    return { summary, nextTime }
  } catch { return null }
}

/** "All 12 sets · Up on 3 of 4 · 1 PR": why the workout got its score. */
export function scoreReasons(r: WorkoutScoreResult): string {
  const sets = r.plannedSets == null ? plural('hist_sets', r.doneSets)
    : r.doneSets >= r.plannedSets ? plural('score_sets_all', r.doneSets)
    : t('score_sets_of', r.doneSets, r.plannedSets)
  return [sets, r.compared > 0 ? t('score_up', r.up, r.compared) : null, r.prs > 0 ? plural('score_prs', r.prs) : null].filter(Boolean).join(' · ')
}

/** "8/10 · All 12 sets · Up on 3 of 4 · 1 PR". */
export const scoreLine = (r: WorkoutScoreResult): string => `${t('debrief_score', r.score)} · ${scoreReasons(r)}`

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
