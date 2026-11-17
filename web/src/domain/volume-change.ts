import type { SessionEntity, SetEntity } from './entities';
import { isWarmUp } from './workout-score';
import { countsForScore } from './set-types';

export const isExtraBlock = (block: string | undefined): boolean =>
  isWarmUp(block) || block?.toLowerCase() === 'cool-down';

export function workingVolume(
  sets: readonly (Pick<SetEntity, 'block' | 'weightKg' | 'reps'> & { setType?: string })[],
): number {
  let total = 0;
  for (const s of sets) {
    // Warm-up/cool-down blocks and WARMUP set types (auto warm-ups live in Main) are not working volume.
    if (isExtraBlock(s.block) || !countsForScore(s as SetEntity)) continue;
    total += (s.weightKg ?? 0) * (s.reps ?? 0);
  }
  return total;
}

export function volumeChangePercent(
  now: readonly Pick<SetEntity, 'block' | 'weightKg' | 'reps'>[],
  was: readonly Pick<SetEntity, 'block' | 'weightKg' | 'reps'>[],
): number | null {
  const before = workingVolume(was);
  const after = workingVolume(now);
  if (before <= 0 || after <= 0) return null;
  return Math.round(((after - before) / before) * 100);
}

export function volumeChangeVsLastTime(
  sessionId: number,
  sessions: readonly SessionEntity[],
  doneSets: readonly SetEntity[],
): number | null {
  const session = sessions.find((s) => s.id === sessionId);
  if (!session || session.planDayId == null) return null;
  const prev = sessions
    .filter(
      (s) =>
        s.planDayId === session.planDayId &&
        s.id !== session.id &&
        (s.epochDay < session.epochDay || (s.epochDay === session.epochDay && s.startedAt < session.startedAt)),
    )
    .sort((a, b) => b.epochDay - a.epochDay || b.startedAt - a.startedAt)[0];
  if (!prev) return null;
  return volumeChangePercent(
    doneSets.filter((s) => s.sessionId === session.id),
    doneSets.filter((s) => s.sessionId === prev.id),
  );
}
