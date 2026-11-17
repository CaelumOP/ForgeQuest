import type { ExerciseKind } from './muscle'
import { recordScore } from './progress'
import { formatWeight, fromKg, unitLabel, type WeightUnit } from './units'

export type RecapSet = {
  reps: number | null
  seconds: number | null
  weightKg: number | null
  isPr: boolean
  countsForPr: boolean
}

export type RecapExercise = {
  exerciseId: string
  name: string
  kind: ExerciseKind
  unit: WeightUnit
  sets: RecapSet[]
}

export type RecapStrings = {
  setsLabel: (count: number) => string
  minutesLabel: (minutes: number) => string
  totalLiftedLabel: (formattedTotal: string) => string
  bestLabel: (bestValue: string) => string
  prTag: string
  prsHeaderLabel: (list: string) => string
  repsLabel: (reps: number) => string
  secondsLabel: (seconds: number) => string
  listSeparator: string
}

export type RecapInput = {
  title: string
  epochDay: number
  minutes: number
  doneSetsCount: number
  defaultUnit: WeightUnit
  exercises: RecapExercise[]
  prs: string[]
  arabic: boolean
}

export function formatRecapDate(epochDay: number, arabic: boolean): string {
  const locale = arabic ? 'ar-u-nu-latn' : 'en-US'
  return new Date(epochDay * 86_400_000).toLocaleDateString(locale, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    timeZone: 'UTC',
  })
}

function formatWholeWeight(unit: WeightUnit, kg: number, arabic: boolean): string {
  const converted = Math.round(fromKg(unit, kg))
  const formatted = converted.toLocaleString('en-US')
  return `${formatted} ${unitLabel(unit, arabic)}`
}

function formatSetValue(
  kind: ExerciseKind,
  unit: WeightUnit,
  set: RecapSet,
  arabic: boolean,
  strings: RecapStrings,
): string | null {
  if (kind === 'HOLD') {
    return set.seconds != null && set.seconds > 0 ? strings.secondsLabel(set.seconds) : null
  }
  const r = set.reps
  if (r == null || r <= 0) return null
  const w = set.weightKg ?? 0
  if (kind === 'WEIGHTED') {
    return w > 0 ? `${formatWeight(unit, w, arabic)} × ${r}` : strings.repsLabel(r)
  }
  const base = strings.repsLabel(r)
  return w > 0 ? `${base} · +${formatWeight(unit, w, arabic)}` : base
}

export function formatWorkoutRecap(input: RecapInput, strings: RecapStrings): string {
  const dateText = formatRecapDate(input.epochDay, input.arabic)
  const header = [input.title.trim(), dateText].filter(Boolean).join(' · ')

  let totalKg = 0
  for (const ex of input.exercises) {
    const hasEligible = ex.sets.some((s) => s.countsForPr)
    for (const s of ex.sets) {
      if (hasEligible && !s.countsForPr) continue
      totalKg += Math.max(0, s.weightKg ?? 0) * Math.max(0, s.reps ?? 0)
    }
  }

  const metaParts = [
    strings.setsLabel(input.doneSetsCount),
    strings.minutesLabel(input.minutes),
  ]
  if (totalKg > 0.5) {
    metaParts.push(strings.totalLiftedLabel(formatWholeWeight(input.defaultUnit, totalKg, input.arabic)))
  }

  const lines: string[] = [header, metaParts.join(' · ')]

  const exLines: string[] = []
  for (const ex of input.exercises) {
    const eligible = ex.sets.filter((s) => s.countsForPr)
    const pool = eligible.length > 0 ? eligible : ex.sets
    if (pool.length === 0) continue
    let best = pool[0]
    let bestScore = -Infinity
    for (const s of pool) {
      const sc = recordScore(ex.kind, s.reps, s.seconds, s.weightKg, ex.exerciseId)
      if (sc > bestScore) {
        bestScore = sc
        best = s
      }
    }
    const bestValue = formatSetValue(ex.kind, ex.unit, best, input.arabic, strings)
    if (!bestValue) continue
    const hasPr = ex.sets.some((s) => s.isPr)
    const prSuffix = hasPr ? ` (${strings.prTag})` : ''
    exLines.push(`• ${ex.name} — ${strings.bestLabel(bestValue)}${prSuffix}`)
  }

  if (exLines.length > 0) {
    lines.push('', ...exLines)
  }

  const distinctPrs = [...new Set(input.prs)]
  if (distinctPrs.length > 0) {
    lines.push('', strings.prsHeaderLabel(distinctPrs.join(strings.listSeparator)))
  }
  return lines.join('\n')
}
