import { readScore, snapshotScore } from "./score";

export interface Env {
  DB: D1Database;
  GOOGLE_AUD: string;
  GOOGLE_JWKS_URL: string;
  GOOGLE_ISSUERS: string;
  ALLOWED_ORIGINS: string;
  FCM_SA?: string;
}

export type FetchFn = (
  url: string,
  init?: { method?: string; headers?: Record<string, string>; body?: string },
) => Promise<Response>;

export class HttpError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
  ) {
    super(code);
  }
}

export type Badge = "trained" | "on_track" | "slacking" | "rest";
export type PushPlatform = "android" | "web";

export interface PrItem {
  exercise: string;
  kg: number;
  reps: number;
}

export interface SnapshotInput {
  /** v5 raw scoring inputs. All three must be supplied together; time is Unix seconds. */
  target?: number;
  prCount?: number;
  reachedAt?: number;
  weekStart: number;
  done: number;
  due: number;
  score: number;
  badge: Badge;
  streakWeeks: number;
  proteinDays: number;
  prs: PrItem[] | null;
  /** This week's result (shared streak); omitted by older builds. */
  weekState?: "hit" | "free" | "open";
  /** 4.2: planned training weekdays, ISO 1 (Mon) .. 7 (Sun). Drives the 4-hour "Go train" cooldown. */
  trainDays?: number[];
  /** Last week's final result, so a late sync still records it. */
  lastWeekState?: "hit" | "free" | "miss";
}

export interface Snapshot extends Omit<SnapshotInput, "weekState" | "lastWeekState" | "trainDays"> {
  scoreVersion: 1 | 2;
  breakdown: ReturnType<typeof snapshotScore>;
  updatedAt: number;
  /** The friend's state for the server's current week; null when unknown or their snapshot is from an older week. */
  weekState: "hit" | "free" | "open" | null;
}

export interface Me {
  id: string;
  username: string | null;
  name: string;
  photoUrl: string | null;
  sharePhoto: boolean;
  sharePrs: boolean;
  joined: boolean;
  /** v4: push me when a friend finishes a workout. */
  alertsOn: boolean;
  /** v4: friends may see (and be pushed) when I finish a workout. */
  shareWorkouts: boolean;
  /** v4: push me "Do your workout!" messages. */
  pingsOn: boolean;
}

export interface Person {
  id: string;
  username: string | null;
  name: string;
  photoUrl: string | null;
}

export interface Friend extends Person {
  snapshot: Snapshot | null;
  /** Consecutive weeks both of you hit your target since you became friends (see src/streak.ts). */
  sharedStreak: number;
  /** v4: I muted this friend's workout alerts. */
  muted: boolean;
  /** 4.2: Unix seconds when I may send this friend "Go train" again; null = now. */
  nudgeNextAt: number | null;
}

export interface NudgeView {
  fromId: string;
  name: string;
  username: string | null;
  day: number;
}

export interface InboxPing {
  fromId: string;
  name: string;
  username: string | null;
  day: number;
  createdAt: number;
  seen: boolean;
}

export interface InboxActivity {
  userId: string;
  name: string;
  username: string | null;
  at: number;
}

export interface Inbox {
  pings: InboxPing[];
  activity: InboxActivity[];
}

export interface UserRow {
  id: string;
  sub: string;
  code: string;
  username: string | null;
  google_photo: string | null;
  share_photo: number;
  share_prs: number;
  joined: number;
  created_at: number;
  alerts_on: number;
  share_workouts: number;
  pings_on: number;
}

export interface SessionRow {
  token_hash: string;
  user_id: string;
  expires_at: number;
}

export interface SnapshotRow {
  user_id: string;
  week_start: number;
  done: number;
  due: number;
  score: number;
  badge: string;
  streak_weeks: number;
  protein_days: number;
  prs: string | null;
  updated_at: number;
}

export function toMe(u: UserRow): Me {
  const sharePhoto = u.share_photo === 1;
  return {
    id: u.id,
    username: u.username,
    // One name only (4.1): `name` stays in the payload for 4.0 clients and always equals the username.
    name: u.username ?? "",
    photoUrl: sharePhoto ? u.google_photo : null,
    sharePhoto,
    sharePrs: u.share_prs === 1,
    joined: u.joined === 1,
    alertsOn: u.alerts_on === 1,
    shareWorkouts: u.share_workouts === 1,
    pingsOn: u.pings_on === 1,
  };
}

export function toPerson(u: UserRow): Person {
  return {
    id: u.id,
    username: u.username,
    name: u.username ?? "",
    photoUrl: u.share_photo === 1 ? u.google_photo : null,
  };
}

export function toSnapshot(
  row: SnapshotRow,
  sharePrs: boolean,
  weekState: Snapshot["weekState"] = null,
): Snapshot {
  const stored = readScore(row);
  const breakdown = snapshotScore({ done: row.done, due: row.due, proteinDays: row.protein_days,
    ...(stored.scoreVersion === 2 ? { target: stored.target, prCount: stored.prCount } : {}) });
  return {
    weekStart: row.week_start,
    done: row.done,
    due: row.due,
    score: breakdown.score,
    scoreVersion: stored.scoreVersion,
    target: stored.target,
    prCount: stored.prCount,
    reachedAt: stored.reachedAt,
    breakdown,
    badge: row.badge as Badge,
    streakWeeks: row.streak_weeks,
    proteinDays: row.protein_days,
    prs: sharePrs ? stored.prs : null,
    updatedAt: row.updated_at,
    weekState,
  };
}
