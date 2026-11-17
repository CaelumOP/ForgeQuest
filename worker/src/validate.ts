import { snapshotScore } from "./score";
import { isSunday } from "./time";
import { Badge, HttpError, PrItem, PushPlatform, SnapshotInput } from "./types";

const BADGES: ReadonlySet<string> = new Set<Badge>(["trained", "on_track", "slacking", "rest"]);

function isPlainObject(v: unknown): v is Record<string, unknown> {
  return v !== null && typeof v === "object" && !Array.isArray(v);
}

function isIntInRange(v: unknown, min: number, max: number): v is number {
  return typeof v === "number" && Number.isInteger(v) && v >= min && v <= max;
}

export async function readJsonObject(request: Request): Promise<Record<string, unknown>> {
  let parsed: unknown;
  try {
    parsed = await request.json();
  } catch {
    throw new HttpError(400, "bad_request");
  }
  if (!isPlainObject(parsed)) {
    throw new HttpError(400, "bad_request");
  }
  return parsed;
}

const RESERVED_USERNAMES: ReadonlySet<string> = new Set([
  "admin", "administrator", "root", "system", "support", "help", "forgequest", "forge", "official",
  "staff", "moderator", "mod", "team", "null", "undefined", "me", "you", "friends", "api", "www",
]);

/**
 * Normalises a username: trim, drop one leading "@", check ASCII `[A-Za-z0-9_.]{3,20}` BEFORE lowercasing
 * (toLowerCase maps some non-ASCII letters, e.g. the Kelvin sign, into a-z), then lowercase. Rejects leading/trailing
 * dots, "..", reserved names and anything starting with "forgequest". Returns null when invalid.
 */
export function normalizeUsername(raw: string): string | null {
  let v = raw.trim();
  if (v.startsWith("@")) v = v.slice(1);
  if (!/^[A-Za-z0-9_.]{3,20}$/.test(v)) return null;
  v = v.toLowerCase();
  if (v.startsWith(".") || v.endsWith(".") || v.includes("..")) return null;
  if (RESERVED_USERNAMES.has(v) || v.startsWith("forgequest")) return null;
  return v;
}

export function validateProfile(body: Record<string, unknown>): {
  sharePhoto: boolean;
  sharePrs: boolean;
  /** Normalised username when the body carries one; undefined when omitted. */
  username: string | undefined;
  /** v4 switches: undefined when omitted (keep the stored value). */
  alertsOn: boolean | undefined;
  shareWorkouts: boolean | undefined;
  pingsOn: boolean | undefined;
} {
  // 4.1 keeps one name only (the username): `name` from 4.0 clients is accepted and ignored, never a 400.
  if (typeof body.sharePhoto !== "boolean" || typeof body.sharePrs !== "boolean") {
    throw new HttpError(400, "bad_profile");
  }
  const optionalFlag = (v: unknown): boolean | undefined => {
    if (v === undefined) return undefined;
    if (typeof v !== "boolean") throw new HttpError(400, "bad_profile");
    return v;
  };
  const alertsOn = optionalFlag(body.alertsOn);
  const shareWorkouts = optionalFlag(body.shareWorkouts);
  const pingsOn = optionalFlag(body.pingsOn);
  let username: string | undefined;
  if (body.username !== undefined) {
    const normalized = typeof body.username === "string" ? normalizeUsername(body.username) : null;
    if (normalized === null) {
      throw new HttpError(400, "bad_username");
    }
    username = normalized;
  }
  return {
    sharePhoto: body.sharePhoto,
    sharePrs: body.sharePrs,
    username,
    alertsOn,
    shareWorkouts,
    pingsOn,
  };
}

export function validateSnapshot(
  body: Record<string, unknown>,
  todayEpochDay: number,
  nowSec = (todayEpochDay + 1) * 86400,
): SnapshotInput {
  const { weekStart, done, due, score, badge, streakWeeks, proteinDays, prs, weekState, lastWeekState } = body;
  const rawInputs = body.target !== undefined || body.prCount !== undefined || body.reachedAt !== undefined;
  if (rawInputs && (!isIntInRange(body.target, 0, 7) ||
      !isIntInRange(body.prCount, 0, 1000000) ||
      !isIntInRange(body.reachedAt, Math.max(0, Number(weekStart) * 86400 - 14 * 3600), nowSec))) {
    throw new HttpError(400, "bad_snapshot");
  }
  if (
    !isIntInRange(weekStart, -100_000, 1_000_000) ||
    !isSunday(weekStart) ||
    Math.abs(weekStart - todayEpochDay) > 8 ||
    !isIntInRange(done, 0, 7) ||
    !isIntInRange(due, 0, 7) ||
    (!(rawInputs && score === undefined) && !isIntInRange(score, 0, rawInputs ? 120 : 100)) ||
    typeof badge !== "string" ||
    !BADGES.has(badge) ||
    !isIntInRange(streakWeeks, 0, 520) ||
    !isIntInRange(proteinDays, 0, 7) ||
    // Optional; Android sends explicit nulls.
    !(weekState === undefined || weekState === null || weekState === "hit" || weekState === "free" || weekState === "open") ||
    !(lastWeekState === undefined || lastWeekState === null || lastWeekState === "hit" || lastWeekState === "free" || lastWeekState === "miss")
  ) {
    throw new HttpError(400, "bad_snapshot");
  }

  let validatedPrs: PrItem[] | null = null;
  if (prs !== null && prs !== undefined) {
    if (!Array.isArray(prs) || prs.length > 10) {
      throw new HttpError(400, "bad_snapshot");
    }
    validatedPrs = prs.map((item) => {
      if (!isPlainObject(item)) {
        throw new HttpError(400, "bad_snapshot");
      }
      if (
        typeof item.exercise !== "string" ||
        typeof item.kg !== "number" ||
        !Number.isFinite(item.kg) ||
        item.kg < 0 ||
        item.kg > 1000 ||
        !isIntInRange(item.reps, 1, 100)
      ) {
        throw new HttpError(400, "bad_snapshot");
      }
      const exercise = item.exercise.trim();
      if (exercise.length < 1 || exercise.length > 60) {
        throw new HttpError(400, "bad_snapshot");
      }
      return {
        exercise,
        kg: item.kg,
        reps: item.reps,
      };
    });
  }

  return {
    weekStart,
    done,
    due,
    score: snapshotScore({ done, due, proteinDays, ...(rawInputs ? { target: body.target as number, prCount: body.prCount as number } : {}) }).score,
    ...(rawInputs ? { target: body.target as number, prCount: body.prCount as number, reachedAt: body.reachedAt as number } : {}),
    badge: badge as Badge,
    streakWeeks,
    proteinDays,
    prs: validatedPrs,
    ...(typeof weekState === "string" ? { weekState: weekState as SnapshotInput["weekState"] } : {}),
    ...(typeof lastWeekState === "string" ? { lastWeekState: lastWeekState as SnapshotInput["lastWeekState"] } : {}),
  };
}

export function validatePushPut(body: Record<string, unknown>): {
  token: string;
  platform: PushPlatform;
} {
  if (typeof body.token !== "string" || (body.platform !== "android" && body.platform !== "web")) {
    throw new HttpError(400, "bad_request");
  }
  const token = body.token.trim();
  if (token.length < 1 || token.length > 512 || /\s/.test(token)) {
    throw new HttpError(400, "bad_request");
  }
  return { token, platform: body.platform };
}

export function validatePushDelete(body: Record<string, unknown>): { token: string } {
  if (typeof body.token !== "string") {
    throw new HttpError(400, "bad_request");
  }
  const token = body.token.trim();
  if (token.length < 1 || token.length > 512 || /\s/.test(token)) {
    throw new HttpError(400, "bad_request");
  }
  return { token };
}
