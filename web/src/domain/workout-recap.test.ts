import { describe, expect, it } from 'vitest'
import { formatWorkoutRecap, type RecapStrings } from './workout-recap'
import { toKg } from './units'

const enStrings: RecapStrings = {
  setsLabel: (c) => `${c} sets`,
  minutesLabel: (m) => `${m} min`,
  totalLiftedLabel: (t) => `${t} lifted`,
  bestLabel: (b) => `best ${b}`,
  prTag: 'PR',
  prsHeaderLabel: (l) => `PRs: ${l}`,
  repsLabel: (r) => `${r} reps`,
  secondsLabel: (s) => `${s}s`,
  listSeparator: ', ',
}

const arStrings: RecapStrings = {
  setsLabel: (c) => `${c} جلسات`,
  minutesLabel: (m) => `${m} د`,
  totalLiftedLabel: (t) => `الإجمالي ${t}`,
  bestLabel: (b) => `الأفضل ${b}`,
  prTag: 'رقم قياسي',
  prsHeaderLabel: (l) => `أرقام قياسية: ${l}`,
  repsLabel: (r) => `${r} عدّات`,
  secondsLabel: (s) => `${s} ث`,
  listSeparator: '، ',
}

describe('workout-recap', () => {
  it('formats EN recap with exercise unitOverride, PR flags, and warm-up exclusion', () => {
    const out = formatWorkoutRecap(
      {
        title: 'Push A',
        epochDay: 20500,
        minutes: 42,
        doneSetsCount: 6,
        defaultUnit: 'KG',
        exercises: [
          {
            exerciseId: 'barbell_bench_press',
            name: 'Bench Press',
            kind: 'WEIGHTED',
            unit: 'LB',
            sets: [
              { reps: 5, seconds: null, weightKg: toKg('LB', 250), isPr: false, countsForPr: false },
              { reps: 8, seconds: null, weightKg: toKg('LB', 185), isPr: true, countsForPr: true },
            ],
          },
          {
            exerciseId: 'plank',
            name: 'Plank',
            kind: 'HOLD',
            unit: 'KG',
            sets: [{ reps: null, seconds: 75, weightKg: null, isPr: false, countsForPr: true }],
          },
        ],
        prs: ['Bench Press', 'Bench Press'],
        arabic: false,
      },
      enStrings,
    )

    const plain = out.replace(/[\u2066\u2067\u2069]/g, '')
    expect(plain).toContain('Push A · ')
    expect(plain).toContain('6 sets · 42 min')
    expect(plain).toContain('Bench Press — best 185.0 lb × 8 (PR)')
    expect(plain).not.toContain('250')
    expect(plain).toContain('Plank — best 75s')
    expect(plain).toContain('PRs: Bench Press')
  })

  it('formats AR recap cleanly with reps exercises and no PR header when empty', () => {
    const out = formatWorkoutRecap(
      {
        title: 'علوي أ',
        epochDay: 20500,
        minutes: 35,
        doneSetsCount: 4,
        defaultUnit: 'KG',
        exercises: [
          {
            exerciseId: 'pull_up',
            name: 'سحب عالي',
            kind: 'REPS',
            unit: 'KG',
            sets: [{ reps: 12, seconds: null, weightKg: null, isPr: false, countsForPr: true }],
          },
        ],
        prs: [],
        arabic: true,
      },
      arStrings,
    )

    expect(out).toContain('4 جلسات · 35 د')
    expect(out).toContain('سحب عالي — الأفضل 12 عدّات')
    expect(out).not.toContain('أرقام قياسية')
  })
})
