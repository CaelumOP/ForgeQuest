import { generateInviteCode } from "../auth";
import { AppContext } from "../context";
import { enforceWriteRateLimit } from "../rate";
import { HttpError, Me, toMe, UserRow } from "../types";
import { readJsonObject, validateProfile } from "../validate";

export function handleGetMe(user: UserRow): Me {
  return toMe(user);
}

export async function handlePutMe(ctx: AppContext, user: UserRow): Promise<Me> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  const body = await readJsonObject(ctx.request);
  const profile = validateProfile(body);
  const updated = await ctx.store.updateProfile(user.id, profile);
  if (!updated) {
    throw new HttpError(401, "unauthorized");
  }
  return toMe(updated);
}

export async function handleRotateCode(ctx: AppContext, user: UserRow): Promise<Me> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  const updated = await ctx.store.rotateInviteCode(user.id, () => {
    let code = generateInviteCode();
    while (code === user.code) {
      code = generateInviteCode();
    }
    return code;
  });
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
