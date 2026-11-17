import { dayOfWeek, weekStart } from './dates'

export type ForgeBadge = 'trained' | 'on_track' | 'slacking' | 'rest'
export type FriendBadge = ForgeBadge | 'not_synced'

export interface ForgeWeek {
  weekStart: number
  done: number
  due: number
  score: number
  badge: ForgeBadge
  proteinDays: number
  target: number
  prCount: number
}

/** Public profile of a friend or invite counterpart (contract v2 `Person`). */
export interface Person {
  id: string
  username: string | null
  name: string
  photoUrl: string | null
}

export interface Pr {
  exercise: string
  kg: number
  reps: number
}

/** Weekly snapshot exactly as PUT /v1/snapshot takes it (plus updatedAt on reads). */
export interface Snapshot {
  target?: number
  prCount?: number
  /** Unix seconds of the last event that changed the total. */
  reachedAt?: number
  scoreVersion?: 1 | 2
  breakdown?: ForgeScoreBreakdown
  weekStart: number
  done: number
  due: number
  score: number
  badge: ForgeBadge
  streakWeeks: number
  proteinDays: number
  prs: Pr[] | null
  /** v3: this week's state ("open" while in progress). On reads: the friend's current-week state or null. */
  weekState?: 'hit' | 'free' | 'open' | null
  /** v3, upload only: last week's final state; omitted for a brand-new user. */
  lastWeekState?: 'hit' | 'free' | 'miss' | null
  updatedAt?: number
}

/** sharedStreak (v3): consecutive weeks you and this friend both hit, computed by the server. */
export type Friend = Person & { snapshot: Snapshot | null; sharedStreak?: number; muted?: boolean }

export interface LeaderboardEntry {
  person: Person
  isMe: boolean
  score: number
  badge: FriendBadge
  streakWeeks: number
  proteinDays: number
  prs: Pr[]
  syncedThisWeek: boolean
  /** Workout days this week (0 when not synced). */
  done: number
  /** This week's state for the shared-streak line; null when unknown. */
  weekState: 'hit' | 'free' | 'open' | null
  /** Weeks you and this friend both hit (0 for me). */
  sharedStreak: number
  target: number
  prCount: number
  reachedAt: number
  breakdown: ForgeScoreBreakdown | null
}

const RESERVED_USERNAMES = new Set([
  'admin', 'administrator', 'root', 'system', 'support', 'help', 'forgequest', 'forge', 'official',
  'staff', 'moderator', 'mod', 'team', 'null', 'undefined', 'me', 'you', 'friends', 'api', 'www',
])

/**
 * Client copy of the Worker's normalizeUsername (worker/src/validate.ts): trim, drop one leading "@", ASCII check
 * BEFORE lowercasing, no edge/double dots, not reserved. Returns the lowercase name or null.
 */
export function normalizeUsername(raw: string): string | null {
  let v = raw.trim()
  if (v.startsWith('@')) v = v.slice(1)
  if (!/^[A-Za-z0-9_.]{3,20}$/.test(v)) return null
  v = v.toLowerCase()
  if (v.startsWith('.') || v.endsWith('.') || v.includes('..')) return null
  if (RESERVED_USERNAMES.has(v) || v.startsWith('forgequest')) return null
  return v
}

/**
 * Computes the user's weekly Forge Score from a Sunday-first week boundary.
 * Mirrors ForgeScore.kt and worker/test/forge-score-v2.vectors.json.
 */
/** Event timestamps are Unix seconds; the caller filters to this Sunday week. */
export interface ForgeScoreEvent { at: number; workoutDay?: number; proteinDay?: number; pr?: boolean }

export function scoreReachedAt(target: number, events: ForgeScoreEvent[], weekStartedAt: number): number {
  const workouts = new Set<number>(), protein = new Set<number>()
  let prs = 0, score = 0, reached = weekStartedAt
  const groups = new Map<number, ForgeScoreEvent[]>()
  for (const event of events) groups.set(event.at, [...(groups.get(event.at) ?? []), event])
  for (const [at, group] of [...groups].sort(([a], [b]) => a - b)) {
    for (const event of group) {
      if (event.workoutDay !== undefined) workouts.add(event.workoutDay)
      if (event.proteinDay !== undefined) protein.add(event.proteinDay)
      if (event.pr) prs++
    }
    const next = scoreBreakdown(workouts.size, target, prs, protein.size).score
    if (next !== score) reached = at
    score = next
  }
  return reached
}

export interface ForgeScoreBreakdown {
  base: number
  prBonus: number
  proteinBonus: number
  extraBonus: number
  bonus: number
  score: number
}

export function scoreBreakdown(done: number, target: number, prCount = 0, proteinDays = 0): ForgeScoreBreakdown {
  if (![done, target, prCount, proteinDays].every(n => Number.isSafeInteger(n) && n >= 0)) throw new RangeError('Invalid score inputs')
  const base = target === 0 ? 0 : Math.round(100 * Math.min(done, target) / target)
  const prBonus = Math.min(prCount, 2) * 5
  const proteinBonus = Math.min(proteinDays, 3) * 2
  const extraBonus = done > target ? 5 : 0
  const bonus = Math.min(20, prBonus + proteinBonus + extraBonus)
  return { base, prBonus, proteinBonus, extraBonus, bonus, score: base + bonus }
}

export function computeForgeWeek(
  today: number,
  scheduledDays: Set<number>,
  trainedDays: Set<number>,
  proteinDays: Set<number> = new Set(),
  weeklyTargetOverride = 0,
  prCount = 0,
): ForgeWeek {
  const ws = weekStart(today)
  const trainedToday = trainedDays.has(today)

  let done = 0
  let protein = 0

  for (let d = ws; d <= today; d++) {
    if (trainedDays.has(d)) done++
    if (proteinDays.has(d)) protein++
  }

  const target = weeklyTargetOverride > 0 ? Math.min(7, weeklyTargetOverride) : [...scheduledDays].filter(d => d >= 1 && d <= 7).length
  const score = scoreBreakdown(done, target, prCount, protein).score

  let badge: ForgeBadge
  if (trainedToday) {
    badge = 'trained'
  } else if (!scheduledDays.has(dayOfWeek(today))) {
    badge = 'rest'
  } else if (score >= 60) {
    badge = 'on_track'
  } else {
    badge = 'slacking'
  }

  return {
    weekStart: ws,
    done,
    due: target,
    target,
    prCount,
    score,
    badge,
    proteinDays: protein,
  }
}

function toLeaderboardEntry(
  person: Person,
  isMe: boolean,
  snapshot: Snapshot | null,
  serverWeekStart: number,
  sharedStreak = 0,
): LeaderboardEntry {
  // Contract: a snapshot from an older week shows as neutral "not synced this week", score 0.
  if (snapshot === null || snapshot.weekStart < serverWeekStart) {
    return { person, isMe, score: 0, badge: 'not_synced', streakWeeks: 0, proteinDays: 0, prs: [], syncedThisWeek: false, done: 0, weekState: null, sharedStreak, target: 0, prCount: 0, reachedAt: Number.MAX_SAFE_INTEGER, breakdown: null }
  }
  return {
    person,
    isMe,
    score: snapshot.score,
    badge: snapshot.badge,
    streakWeeks: snapshot.streakWeeks,
    proteinDays: snapshot.proteinDays,
    prs: snapshot.prs ?? [],
    syncedThisWeek: true,
    done: snapshot.done,
    target: snapshot.target ?? snapshot.due,
    prCount: snapshot.prCount ?? 0,
    reachedAt: snapshot.reachedAt ?? snapshot.updatedAt ?? Number.MAX_SAFE_INTEGER,
    breakdown: snapshot.breakdown ?? null,
    weekState: snapshot.weekState ?? null,
    sharedStreak,
  }
}

/**
 * Builds the weekly leaderboard from me + friends (server weekStart from GET /friends). Sorted by score desc,
 * then reachedAt asc, then username asc; the user is included and flagged isMe.
 */
export function buildLeaderboard(
  serverWeekStart: number,
  me: Person | null,
  mySnapshot: Snapshot | null,
  friends: Friend[],
): LeaderboardEntry[] {
  const entries: LeaderboardEntry[] = []
  if (me) entries.push(toLeaderboardEntry(me, true, mySnapshot, serverWeekStart))
  for (const f of friends) {
    const person: Person = { id: f.id, username: f.username, name: f.name, photoUrl: f.photoUrl }
    entries.push(toLeaderboardEntry(person, false, f.snapshot, serverWeekStart, f.sharedStreak ?? 0))
  }
  entries.sort((a, b) => {
    if (b.score !== a.score) return b.score - a.score
    if (a.reachedAt !== b.reachedAt) return a.reachedAt - b.reachedAt
    const an = (a.person.username ?? '').toLowerCase()
    const bn = (b.person.username ?? '').toLowerCase()
    return an < bn ? -1 : an > bn ? 1 : 0
  })
  return entries
}
