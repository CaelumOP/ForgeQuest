/**
 * How a set felt, as a reps-in-reserve scale: EASY (3+ left), HARD (1-3 left), FAILURE (0 left). PAIN is not on the
 * scale: it is the separate "it hurt" button. Not tapping counts as OK (neutral). A stored "OK" from before the scale
 * counts as neutral in the maths and shows as no chip (chipOf).
 */
export const SET_EFFORTS = ['EASY', 'OK', 'HARD', 'FAILURE', 'PAIN'] as const
export type SetEffort = (typeof SET_EFFORTS)[number]

/** The three chips of the scale, in display order; PAIN has its own button. */
export const EFFORT_CHIPS: readonly SetEffort[] = ['EASY', 'HARD', 'FAILURE']

/** Reps in reserve represented by `e`, used by baseline / e1RM math. */
export function effortRir(e: SetEffort): number {
  switch (e) {
    case 'EASY': return 3
    case 'OK': return 2
    case 'HARD': return 1
    case 'FAILURE': return 0
    case 'PAIN': return 2
  }
}

/** Reached the limit; progression checks FAILURE explicitly rather than using this flag. */
export const isStrain = (e: SetEffort): boolean => e === 'HARD' || e === 'FAILURE'

/** Reads the stored name; empty or unknown is OK (not tapped). */
export function parseEffort(raw: string | null | undefined): SetEffort {
  return SET_EFFORTS.find((e) => e === raw) ?? 'OK'
}

/**
 * What tapping [picked] does to a set's effort and set type. Tapping the chosen one again clears it. Failure also makes
 * the set a FAILURE set; leaving Failure puts it back to WORKING (only when Failure had set it, never from another type).
 */
/** The chip a stored value lights up: a stored "OK" shows as no chip, matching blank. */
export const chipOf = (raw: string): string => (raw === 'OK' ? '' : raw)

export function pickEffort(current: { effort: string; setType?: string }, picked: SetEffort): { effort: string; setType: string } {
  const type = current.setType || 'WORKING'
  const was = chipOf(current.effort)
  const effort = was === picked ? '' : picked
  if (effort === 'FAILURE') return { effort, setType: type === 'WORKING' ? 'FAILURE' : type }
  if (was === 'FAILURE' && type === 'FAILURE') return { effort, setType: 'WORKING' }
  return { effort, setType: type }
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
