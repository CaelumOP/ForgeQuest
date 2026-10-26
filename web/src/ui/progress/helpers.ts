import type { ExerciseKind } from '../../domain/muscle'
import { weekStart } from '../../domain/dates'
import { formatWeight, weightNumber, type WeightUnit } from '../../domain/units'

/** Fill for a muscle by fatigue (Android recoveryColor): resting grey, teal, gold, coral. CSS variables only. */
export function recoveryFill(fatigue: number): string {
  if (fatigue <= 0) return 'var(--muscle-rest)'
  if (fatigue < 0.33) {
    const teal = Math.round((0.55 + 0.45 * (fatigue / 0.33)) * 100)
    return `color-mix(in srgb, var(--teal) ${teal}%, var(--muscle-rest))`
  }
  return fatigue < 0.66 ? 'var(--gold)' : 'var(--coral)'
}

/** A best-set value as text: "12", "30 s", "20 kg". */
export function formatValue(v: number, kind: ExerciseKind, unit: WeightUnit, sUnit: string, arabic: boolean): string {
  if (kind === 'HOLD') return `${Math.round(v)} ${sUnit}`
  if (kind === 'WEIGHTED') return formatWeight(unit, v, arabic, 0)
  return `${Math.round(v)}`
}

/** A change in a best set, or null when it is too small to show. */
export function formatDelta(diff: number, kind: ExerciseKind, unit: WeightUnit, sUnit: string, unitText: string): string | null {
  if (Math.abs(diff) < 0.01) return null
  const sign = diff > 0 ? '+' : '−'
  const a = Math.abs(diff)
  if (kind === 'HOLD') return `${sign}${Math.round(a)} ${sUnit}`
  if (kind === 'WEIGHTED') return `${sign}${weightNumber(unit, a, 0)} ${unitText}`
  return `${sign}${Math.round(a)}`
}

type Day = { epochDay: number; carbs: number }

/** Average carbs per logged day for each of the last 12 weeks, oldest first (0 when nothing was logged). Rounded, like Trends.carbWeeks. */
export function carbWeeks(byDay: readonly Day[], today: number): number[] {
  return Array.from({ length: 12 }, (_, i) => {
    const ws = weekStart(today) - 7 * (11 - i)
    const days = byDay.filter((d) => d.epochDay >= ws && d.epochDay <= ws + 6 && d.carbs > 0)
    return days.length ? Math.round(days.reduce((a, d) => a + d.carbs, 0) / days.length) : 0
  })
}

/** Number with thousands separators, Latin digits like the rest of the app text. */
export const grouped = (n: number): string => Math.round(n).toLocaleString('en-US')
