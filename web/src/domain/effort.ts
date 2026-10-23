/** How a set felt, from the one-tap chips under a finished set. Not tapping counts as OK. */
export const SET_EFFORTS = ['EASY', 'OK', 'HARD', 'PAIN'] as const
export type SetEffort = (typeof SET_EFFORTS)[number]

/** Reads the stored name; empty or unknown is OK. */
export function parseEffort(raw: string | null | undefined): SetEffort {
  return SET_EFFORTS.find((e) => e === raw) ?? 'OK'
}

// One word list for every reader of old notes (progression and recovery).
// web: audit #8 (Android has a second list in Recovery; "فشل" and the easy words came from it)
const PAIN_WORDS = ['pain', 'twinge', 'hurt', 'ألم']
const STRAIN_WORDS = ['rir 0', 'rpe 10', 'fail', 'صعب', 'فشل']
const EASY_WORDS = ['easy', 'rir 3', 'سهل']

/**
 * The effort of a set, from its chip or, for sets logged before the chips existed, from words in its note.
 * A tapped chip always wins over the note.
 */
export function effortOf(effort: string, note: string): SetEffort {
  if (effort.trim() !== '') return parseEffort(effort)
  const n = note.toLowerCase()
  if (PAIN_WORDS.some((w) => n.includes(w))) return 'PAIN'
  if (STRAIN_WORDS.some((w) => n.includes(w))) return 'HARD'
  if (EASY_WORDS.some((w) => n.includes(w))) return 'EASY'
  return 'OK'
}
