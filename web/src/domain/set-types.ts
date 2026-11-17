export const SET_TYPES = ['WORKING', 'WARMUP', 'DROPSET', 'FAILURE', 'RESTPAUSE'] as const;
export type SetType = (typeof SET_TYPES)[number];
type TypedSet = { setType?: string; block?: string };

/** Default WORKING also represents legacy rows, whose block exclusions must survive restore. */
export function countsForProgression(set: TypedSet): boolean {
  const type = set.setType ?? 'WORKING';
  if (type === 'FAILURE') return true;
  return type === 'WORKING' && !['warm-up', 'cool-down'].includes((set.block ?? '').toLowerCase());
}
export const countsForScore = countsForProgression;
export const countsForPR = countsForProgression;
