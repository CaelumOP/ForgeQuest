import { isSunday } from "./time";
import { Badge, HttpError, PrItem, PushPlatform, SnapshotInput } from "./types";

const BADGES: ReadonlySet<string> = new Set<Badge>(["trained", "on_track", "slacking", "rest"]);
const URL_PATTERN = /https?:\/\/|www\./i;
const CONTROL_CHARS = /[\x00-\x1F\x7F]/;

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

export function validateProfile(body: Record<string, unknown>): {
  name: string;
  sharePhoto: boolean;
  sharePrs: boolean;
} {
  if (
    typeof body.name !== "string" ||
    typeof body.sharePhoto !== "boolean" ||
    typeof body.sharePrs !== "boolean"
  ) {
    throw new HttpError(400, "bad_profile");
  }
  const name = body.name.trim();
  if (
    name.length < 1 ||
    name.length > 40 ||
    URL_PATTERN.test(name) ||
    CONTROL_CHARS.test(name)
  ) {
    throw new HttpError(400, "bad_profile");
  }
  return {
    name,
    sharePhoto: body.sharePhoto,
    sharePrs: body.sharePrs,
  };
}

export function validateSnapshot(
  body: Record<string, unknown>,
  todayEpochDay: number,
): SnapshotInput {
  const { weekStart, done, due, score, badge, streakWeeks, proteinDays, prs } = body;
  if (
    !isIntInRange(weekStart, -100_000, 1_000_000) ||
    !isSunday(weekStart) ||
    Math.abs(weekStart - todayEpochDay) > 8 ||
    !isIntInRange(done, 0, 7) ||
    !isIntInRange(due, 0, 7) ||
    !isIntInRange(score, 0, 100) ||
    typeof badge !== "string" ||
    !BADGES.has(badge) ||
    !isIntInRange(streakWeeks, 0, 520) ||
    !isIntInRange(proteinDays, 0, 7)
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
    score,
    badge: badge as Badge,
    streakWeeks,
    proteinDays,
    prs: validatedPrs,
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
