export const SET_TYPES = ['WORKING', 'WARMUP', 'DROPSET', 'FAILURE', 'RESTPAUSE'] as const;
export type SetType = (typeof SET_TYPES)[number];
type TypedSet = { setType?: string; block?: string; autoTicked?: boolean };

/** Default WORKING also represents legacy rows, whose block exclusions must survive restore. */
export function countsForProgression(set: TypedSet): boolean {
  const type = set.setType ?? 'WORKING';
  if (type === 'FAILURE') return true;
  return type === 'WORKING' && !['warm-up', 'cool-down'].includes((set.block ?? '').toLowerCase());
}
export const countsForScore = countsForProgression;
/** A set the user actually trained, for "N sets" labels: drop/rest-pause/failure count, warm-ups and cool-downs don't. */
export function countsAsWorkingSet(set: TypedSet): boolean {
  return (set.setType ?? 'WORKING') !== 'WARMUP' && !['warm-up', 'cool-down'].includes((set.block ?? '').toLowerCase());
}
export function countsForPR(set: TypedSet): boolean {
  return !set.autoTicked && countsForProgression(set);
}
