import type { ExerciseEntity } from '../../domain/entities'
import { EXERCISE_ALIASES } from './exercise-aliases'
import { EXERCISE_CATALOG } from './exercise-catalog'
import { EXERCISE_NAMES_AR } from './exercise-names-ar'

const EN_NAME_BY_ID = new Map<string, string>(EXERCISE_CATALOG.map((e) => [e.id, e.name]))

/** Lowercase, strip tashkeel/accents, unify أ/إ/آ->ا, ة->ه, ى->ي, and normalize separators to spaces. */
export function normalizeExerciseSearch(s: string): string {
  let out = ''
  for (const ch of s.toLowerCase().normalize('NFD').replace(/\p{Mn}+/gu, '')) {
    const code = ch.charCodeAt(0)
    if (ch === 'ٱ') out += 'ا'
    else if (ch === 'ة') out += 'ه'
    else if (ch === 'ى' || ch === 'ی' || ch === 'ې') out += 'ي'
    else if (ch === 'ک') out += 'ك'
    else if ("ءـ'’`".includes(ch)) continue
    else if (code >= 0x660 && code <= 0x669) out += String(code - 0x660)
    else if (code >= 0x6f0 && code <= 0x6f9) out += String(code - 0x6f0)
    else out += ch
  }
  return out.replace(/[^\p{L}\p{N}]+/gu, ' ').trim()
}

function scoreNormalized(exercise: Pick<ExerciseEntity, 'id' | 'name' | 'aliases'>, normQuery: string): number {
  const tokens = normQuery.split(' ').filter(Boolean)
  if (tokens.length === 0) return 1

  const currentName = normalizeExerciseSearch(exercise.name)
  const enRaw = EN_NAME_BY_ID.get(exercise.id)
  const enName = enRaw ? normalizeExerciseSearch(enRaw) : ''
  const arRaw = EXERCISE_NAMES_AR[exercise.id]
  const arName = arRaw ? normalizeExerciseSearch(arRaw) : ''
  const idNorm = normalizeExerciseSearch(exercise.id)
  const aliasParts = [
    exercise.aliases ? normalizeExerciseSearch(exercise.aliases) : '',
    EXERCISE_ALIASES[exercise.id] ? normalizeExerciseSearch(EXERCISE_ALIASES[exercise.id]) : '',
  ].filter(Boolean)
  const aliasesNorm = aliasParts.join(' ')

  const combined = [currentName, enName, arName, aliasesNorm, idNorm].filter(Boolean).join(' ')
  if (!tokens.every((t) => combined.includes(t))) return 0

  const hasWordStart = currentName.includes(` ${normQuery}`) || enName.includes(` ${normQuery}`) || arName.includes(` ${normQuery}`)
  const exactAlias = aliasesNorm.split(' ').some((w) => w === normQuery)

  if (currentName === normQuery || enName === normQuery || arName === normQuery || idNorm === normQuery) return 100
  if (currentName.startsWith(normQuery) || enName.startsWith(normQuery) || arName.startsWith(normQuery)) return exactAlias ? 85 : 80
  if (hasWordStart) return 70
  if (currentName.includes(normQuery) || enName.includes(normQuery) || arName.includes(normQuery)) return 60
  if (exactAlias) return 50
  return 30
}

export function scoreExerciseMatch(exercise: Pick<ExerciseEntity, 'id' | 'name' | 'aliases'>, query: string): number {
  const normQuery = normalizeExerciseSearch(query)
  if (!normQuery) return 1
  return scoreNormalized(exercise, normQuery)
}

export function matchesExerciseSearch(exercise: Pick<ExerciseEntity, 'id' | 'name' | 'aliases'>, query: string): boolean {
  return scoreExerciseMatch(exercise, query) > 0
}

export function filterExercisesBySearch<T extends Pick<ExerciseEntity, 'id' | 'name' | 'aliases'>>(
  exercises: readonly T[],
  query: string,
): T[] {
  const normQuery = normalizeExerciseSearch(query)
  if (!normQuery) return [...exercises]
  return exercises
    .map((ex) => ({ ex, s: scoreNormalized(ex, normQuery) }))
    .filter((x) => x.s > 0)
    .sort((a, b) =>
      b.s - a.s ||
      a.ex.name.length - b.ex.name.length ||
      a.ex.name.toLowerCase().localeCompare(b.ex.name.toLowerCase()),
    )
    .map((x) => x.ex)
}
