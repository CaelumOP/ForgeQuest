import { AppContext } from "../context";
import { assertNotInviteLocked, enforceWriteRateLimit, recordInviteFailure } from "../rate";
import { HttpError, Me, toMe, UserRow } from "../types";
import { readJsonObject, validateProfile } from "../validate";

export function handleGetMe(user: UserRow): Me {
  return toMe(user);
}

export async function handlePutMe(ctx: AppContext, user: UserRow): Promise<Me> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  const body = await readJsonObject(ctx.request);
  const profile = validateProfile(body);
  const username = profile.username ?? user.username;
  if (!username) {
    // First join must pick a username.
    throw new HttpError(400, "bad_username");
  }
  const changingUsername = username !== user.username;
  if (changingUsername) {
    // A 409 username_taken reveals that a name exists, so it shares the invite-failure budget.
    await assertNotInviteLocked(ctx.store, user.id, ctx.nowSec);
  }
  const updated = await ctx.store.updateProfile(user.id, {
    name: profile.name,
    username,
    sharePhoto: profile.sharePhoto,
    sharePrs: profile.sharePrs,
  });
  if (updated === "taken") {
    await recordInviteFailure(ctx.store, user.id, ctx.nowSec);
    throw new HttpError(409, "username_taken");
  }
  if (!updated) {
    throw new HttpError(401, "unauthorized");
  }
  return toMe(updated);
}

export async function handleDeleteMe(
  ctx: AppContext,
  user: UserRow,
): Promise<{ ok: true }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  await ctx.store.deleteUserCascade(user.id);
  return { ok: true };
}
