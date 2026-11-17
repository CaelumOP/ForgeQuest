import { AppContext } from "../context";
import { sendPush } from "../fcm";
import { enforceWriteRateLimit } from "../rate";
import { isQuietHours, riyadhEpochDay } from "../time";
import { HttpError, UserRow } from "../types";
import { isTrainDay, nudgeNextAt, TRAIN_DAY_NUDGE_COOLDOWN_SEC } from "../nudge-rule";
import { readScore } from "../score";

export async function handleNudge(
  ctx: AppContext,
  user: UserRow,
  targetId: string,
): Promise<{ pushed: boolean; nextAt: number | null }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  if (targetId === user.id) {
    throw new HttpError(409, "self");
  }
  if (!(await ctx.store.areFriends(user.id, targetId))) {
    throw new HttpError(403, "not_friends");
  }

  const today = riyadhEpochDay(ctx.nowMs);
  // One "Go train" per sender -> friend per Riyadh day; on the friend's planned training day, again every 4 hours.
  // The same row is re-armed (new time, unseen again), so no schema change and old clients still read one row a day.
  const theirs = await ctx.store.getSnapshot(targetId);
  const trainDay = theirs ? isTrainDay(readScore(theirs).trainDays, today) : false;
  const created =
    (await ctx.store.createNudgeIfAbsent(user.id, targetId, today, ctx.nowSec)) ||
    (trainDay &&
      (await ctx.store.rearmNudge(user.id, targetId, today, ctx.nowSec, ctx.nowSec - TRAIN_DAY_NUDGE_COOLDOWN_SEC)));
  if (!created) {
    throw new HttpError(409, "already_nudged");
  }
  const nextAt = nudgeNextAt(ctx.nowSec, trainDay, today, ctx.nowSec);

  // Always stored (it shows in the inbox). The push is skipped when the target turned "Do your workout" messages
  // off or during quiet hours; the sender is never told which (privacy), they just see "Sent".
  let pushed = false;
  if (ctx.env.FCM_SA && !isQuietHours(ctx.nowMs)) {
    const target = await ctx.store.getUserById(targetId);
    const tokens = target && target.pings_on === 1 ? await ctx.store.getPushTokens(targetId) : [];
    if (tokens.length > 0) {
      pushed = await sendPush({
        fcmSaRaw: ctx.env.FCM_SA,
        tokens,
        data: { type: "ping", fromId: user.id, name: user.username ?? "ForgeQuest" },
        store: ctx.store,
        nowSec: ctx.nowSec,
        fetchFn: ctx.fetchFn,
      });
    }
  }

  return { pushed, nextAt };
}

export async function handleInboxSeen(ctx: AppContext, user: UserRow): Promise<{ ok: true }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  await ctx.store.markInboxSeen(user.id);
  return { ok: true };
}
