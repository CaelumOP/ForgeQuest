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
  weekStart: number;
  done: number;
  due: number;
  score: number;
  badge: Badge;
  streakWeeks: number;
  proteinDays: number;
  prs: PrItem[] | null;
}

export interface Snapshot extends SnapshotInput {
  updatedAt: number;
}

export interface Me {
  id: string;
  code: string;
  name: string;
  photoUrl: string | null;
  sharePhoto: boolean;
  sharePrs: boolean;
  joined: boolean;
}

export interface Person {
  id: string;
  name: string;
  photoUrl: string | null;
}

export interface Friend extends Person {
  snapshot: Snapshot | null;
}

export interface NudgeView {
  fromId: string;
  name: string;
  day: number;
}

export interface UserRow {
  id: string;
  sub: string;
  code: string;
  name: string;
  google_photo: string | null;
  share_photo: number;
  share_prs: number;
  joined: number;
  created_at: number;
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
    code: u.code,
    name: u.name,
    photoUrl: sharePhoto ? u.google_photo : null,
    sharePhoto,
    sharePrs: u.share_prs === 1,
    joined: u.joined === 1,
  };
}

export function toPerson(u: UserRow): Person {
  return {
    id: u.id,
    name: u.name,
    photoUrl: u.share_photo === 1 ? u.google_photo : null,
  };
}

export function toSnapshot(row: SnapshotRow, sharePrs: boolean): Snapshot {
  let prs: PrItem[] | null = null;
  if (sharePrs && row.prs !== null) {
    try {
      const parsed: unknown = JSON.parse(row.prs);
      if (Array.isArray(parsed)) {
        prs = parsed as PrItem[];
      }
    } catch {
      prs = null;
    }
  }
  return {
    weekStart: row.week_start,
    done: row.done,
    due: row.due,
    score: row.score,
    badge: row.badge as Badge,
    streakWeeks: row.streak_weeks,
    proteinDays: row.protein_days,
    prs,
    updatedAt: row.updated_at,
  };
}
