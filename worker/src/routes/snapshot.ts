import { AppContext } from "../context";
import { MAX_FCM_SENDS, sendPush } from "../fcm";
import { enforceWriteRateLimit } from "../rate";
import { isQuietHours, riyadhEpochDay } from "../time";
import { HttpError, UserRow } from "../types";
import { readJsonObject, validateSnapshot } from "../validate";

export async function handlePutSnapshot(
  ctx: AppContext,
  user: UserRow,
): Promise<{ ok: true }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  if (user.joined !== 1) {
    throw new HttpError(409, "not_joined");
  }
  const body = await readJsonObject(ctx.request);
  const today = riyadhEpochDay(ctx.nowMs);
  const snapshot = validateSnapshot(body, today);
  if (user.share_prs !== 1) {
    snapshot.prs = null;
  }
  const newWorkout = await ctx.store.upsertSnapshot(user.id, snapshot, ctx.nowSec, {
    day: today,
    shareWorkouts: user.share_workouts === 1,
  });
  if (newWorkout) {
    ctx.waitUntil(fanOutActivity(ctx, user, today));
  }
  return { ok: true };
}

/**
 * "<username> just finished a workout" to each eligible friend, at most once per sender -> friend per Riyadh day.
 * Quiet hours (and a missing FCM key) skip BEFORE the daily slot is claimed, so they never burn it.
 */
export async function fanOutActivity(ctx: AppContext, user: UserRow, day: number): Promise<void> {
  if (!ctx.env.FCM_SA || isQuietHours(ctx.nowMs)) return;
  const targets = await ctx.store.getActivityTargets(user.id, day);
  let budget = MAX_FCM_SENDS;
  for (const t of targets) {
    if (t.tokens.length > budget) break;
    if (!(await ctx.store.claimActivityPush(user.id, t.userId, day))) continue;
    budget -= t.tokens.length;
    await sendPush({
      fcmSaRaw: ctx.env.FCM_SA,
      tokens: t.tokens,
      data: { type: "activity", fromId: user.id, name: user.username ?? "ForgeQuest" },
      store: ctx.store,
      nowSec: ctx.nowSec,
      fetchFn: ctx.fetchFn,
    });
  }
}
