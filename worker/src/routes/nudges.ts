import { AppContext } from "../context";
import { sendNudgePush } from "../fcm";
import { enforceWriteRateLimit } from "../rate";
import { riyadhEpochDay } from "../time";
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

  let pushed = false;
  if (ctx.env.FCM_SA) {
    const tokens = await ctx.store.getPushTokens(targetId);
    if (tokens.length > 0) {
      pushed = await sendNudgePush({
        fcmSaRaw: ctx.env.FCM_SA,
        tokens,
        fromName: user.name || "ForgeQuest",
        store: ctx.store,
        nowSec: ctx.nowSec,
        fetchFn: ctx.fetchFn,
      });
    }
  }

  return { pushed };
}
