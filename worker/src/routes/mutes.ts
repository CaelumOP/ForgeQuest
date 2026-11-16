import { AppContext } from "../context";
import { enforceWriteRateLimit } from "../rate";
import { HttpError, UserRow } from "../types";

/** PUT/DELETE /v1/mutes/{id}: silence (or restore) a friend's workout alerts. Must be friends. */
export async function handleSetMute(
  ctx: AppContext,
  user: UserRow,
  friendId: string,
  muted: boolean,
): Promise<{ ok: true }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  if (!(await ctx.store.areFriends(user.id, friendId))) {
    throw new HttpError(404, "not_found");
  }
  await ctx.store.setMute(user.id, friendId, muted);
  return { ok: true };
}
