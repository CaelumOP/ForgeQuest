import { Store } from "./store";
import { HttpError } from "./types";

const HOUR_SEC = 3600;
const FIFTEEN_MIN_SEC = 900;

export function windowStartFor(nowSec: number, windowSec: number): number {
  return Math.floor(nowSec / windowSec) * windowSec;
}

export async function enforceRateLimit(
  store: Store,
  key: string,
  windowSec: number,
  limit: number,
  nowSec: number,
  errorCode: string = "rate_limited",
): Promise<void> {
  const win = windowStartFor(nowSec, windowSec);
  const count = await store.incrementRate(key, win);
  if (count > limit) {
    throw new HttpError(429, errorCode);
  }
}

export async function enforceAuthRateLimit(
  store: Store,
  ip: string,
  nowSec: number,
): Promise<void> {
  await enforceRateLimit(store, `auth:${ip}`, HOUR_SEC, 20, nowSec, "rate_limited");
}

export async function enforceWriteRateLimit(
  store: Store,
  userId: string,
  nowSec: number,
): Promise<void> {
  await enforceRateLimit(store, `write:${userId}`, HOUR_SEC, 120, nowSec, "rate_limited");
}

export async function assertNotInviteLocked(
  store: Store,
  userId: string,
  nowSec: number,
): Promise<void> {
  const win = windowStartFor(nowSec, FIFTEEN_MIN_SEC);
  const count = await store.getRateCount(`invite_fail:${userId}`, win);
  if (count >= 5) {
    throw new HttpError(429, "too_many_tries");
  }
}

export async function recordInviteFailure(
  store: Store,
  userId: string,
  nowSec: number,
): Promise<void> {
  const win = windowStartFor(nowSec, FIFTEEN_MIN_SEC);
  await store.incrementRate(`invite_fail:${userId}`, win);
}
