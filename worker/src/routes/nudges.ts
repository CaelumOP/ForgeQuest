import { AppContext } from "../context";
import { sendPush } from "../fcm";
import { enforceWriteRateLimit } from "../rate";
import { isQuietHours, riyadhEpochDay } from "../time";
import { HttpError, UserRow } from "../types";

export async function handleNudge(
  ctx: AppContext,
  user: UserRow,
  targetId: string,
): Promise<{ pushed: boolean }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  if (targetId === user.id) {
    throw new HttpError(409, "self");
  }
  if (!(await ctx.store.areFriends(user.id, targetId))) {
    throw new HttpError(403, "not_friends");
  }

  const today = riyadhEpochDay(ctx.nowMs);
  const created = await ctx.store.createNudgeIfAbsent(
    user.id,
    targetId,
    today,
    ctx.nowSec,
  );
  if (!created) {
    throw new HttpError(409, "already_nudged");
  }

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

  return { pushed };
}

export async function handleInboxSeen(ctx: AppContext, user: UserRow): Promise<{ ok: true }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  await ctx.store.markInboxSeen(user.id);
  return { ok: true };
}
