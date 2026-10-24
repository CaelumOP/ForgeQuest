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

const NEGATIONS = new Set(['no', 'not', 'without', 'never', 'zero', 'لا', 'ما', 'بدون', 'بلا', 'مافي', 'ليس', 'مو', 'مش'])
const CLAUSE_BREAK = /[.,;:!?،؛\n]| but | لكن | بس /

/**
 * True when some occurrence of a word isn't negated in its own clause, so "no pain", "pain-free", "didn't fail"
 * and «بدون ألم» don't count. Matched as substrings, so Arabic prefixes («بألم») still count.
 */
function foundIn(words: string[], n: string): boolean {
  return words.some((word) => {
    for (let i = n.indexOf(word); i >= 0; i = n.indexOf(word, i + 1)) {
      const before = n.slice(0, i).split(CLAUSE_BREAK).at(-1)!.split(/\s+/)
      const after = n.slice(i + word.length)
      const negated = before.some((w) => NEGATIONS.has(w) || w.endsWith("n't")) ||
        after.startsWith('-free') || after.startsWith(' free') || after.startsWith('less')
      if (!negated) return true
    }
    return false
  })
}

/**
 * The effort of a set, from its chip or, for sets logged before the chips existed, from words in its note.
 * A tapped chip always wins over the note.
 */
export function effortOf(effort: string, note: string): SetEffort {
  if (effort.trim() !== '') return parseEffort(effort)
  const n = note.toLowerCase()
  if (foundIn(PAIN_WORDS, n)) return 'PAIN'
  if (foundIn(STRAIN_WORDS, n)) return 'HARD'
  if (foundIn(EASY_WORDS, n)) return 'EASY'
  return 'OK'
}
