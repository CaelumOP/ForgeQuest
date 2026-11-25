import { expect, it } from 'vitest'
import { readyForHarder } from './exercise-tiers'
import { computeAdjustment, type IntraSessionSet } from './intra-session'
import type { ExerciseKind } from './muscle'
import { nextPrefill, type PastSet, type Prefill } from './progression'
import { fromKg, toKg, type WeightUnit } from './units'

// Mirror ProgressionSim24Test.kt: 24 weeks, three scheduled sessions per week.
const scenarios = ['consistent', 'layoff', 'inconsistent', 'light-dumbbells', 'push-up', 'plank', 'consistent-lb'] as const
type Scenario = typeof scenarios[number]
const expected: Record<Scenario, string> = {
  consistent: '72|0|false|SAME|false|82.50/11/-;82.50/11/-|82.50/11/-;82.50/11/-|OK,OK',
  layoff: '60|12|false|SAME|false|72.50/9/-;72.50/9/-|72.50/9/-;72.50/9/-|OK,OK',
  inconsistent: '53|19|false|SAME|false|77.50/10/-;77.50/10/-|77.50/10/-;77.50/10/-|OK,OK',
  'light-dumbbells': '72|0|false|SAME|false|7.50/18/-;7.50/18/-|7.50/18/-;7.50/18/-|OK,OK',
  'push-up': '72|0|true|SAME|false|-/14/-;-/14/-|-/14/-;-/14/-|OK,OK',
  plank: '72|0|false|SAME|false|-/-/45;-/-/45|-/-/45;-/-/45|OK,OK',
  'consistent-lb': '72|0|false|SAME|false|180.00/8/-;180.00/8/-|180.00/8/-;180.00/8/-|OK,OK',
}

it.each(scenarios)('%s progresses safely for 24 weeks', (name) => {
  const kind: ExerciseKind = name === 'push-up' ? 'REPS' : name === 'plank' ? 'HOLD' : 'WEIGHTED'
  const unit: WeightUnit = name === 'consistent-lb' ? 'LB' : 'KG'
  const start = kind !== 'WEIGHTED' ? null : name === 'light-dumbbells' ? 5 : unit === 'LB' ? toKg(unit, 135) : 60
  const repsMin = kind === 'HOLD' ? null : name === 'light-dumbbells' ? 10 : 8
  const repsMax = kind === 'HOLD' ? null : name === 'light-dumbbells' ? 15 : 12
  const target = { sets: 3, repsMin, repsMax, seconds: kind === 'HOLD' ? 30 : null, weightKg: start }
  const history: { deload: boolean; sets: PastSet[] }[] = []
  let seed = 12345; let attended = 0; let missed = 0; let unchangedMisses = 0
  let previousPrescription = ''; let ready = false; let lastState = ''; let firstTop = 0; let finalTop = 0
  const stalls: string[] = []
  const signature = (sets: readonly Prefill[]) => sets.map((p) =>
    `${p.weightKg == null ? '-' : fromKg(unit, p.weightKg).toFixed(2)}/${p.reps ?? '-'}/${p.seconds ?? '-'}`).join(';')
  const check = (p: Prefill, context: string) => {
    if (p.weightKg != null) {
      expect(p.weightKg, context).toBeGreaterThanOrEqual(0)
      const grid = unit === 'KG' ? 0.25 : 2.5
      const steps = fromKg(unit, p.weightKg) / grid
      expect(Math.abs(steps - Math.round(steps)), context).toBeLessThan(0.0001)
    }
    if (p.reps != null) expect(p.reps, context).toBeGreaterThanOrEqual(1)
    if (p.seconds != null) {
      expect(p.seconds, context).toBeGreaterThanOrEqual(5)
      expect(p.seconds % 5, context).toBe(0)
    }
  }
  for (let slot = 0; slot < 72; slot++) {
    const week = Math.floor(slot / 3) + 1
    // Unsigned 32-bit LCG, including skipped slots; identical arithmetic in Kotlin Long.
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0
    if ((name === 'layoff' && week >= 9 && week <= 12) || (name === 'inconsistent' && (seed >>> 16) % 4 === 0)) { missed++; continue }
    attended++
    const deload = week % 4 === 0
    const usable = history.filter((h) => !h.deload)
    const last = usable[0]?.sets ?? []; const previous = usable[1]?.sets ?? []
    const result = nextPrefill(kind, target, last, previous, unit, false, deload)
    const context = `${name} week ${week} session ${slot % 3 + 1}: ${result.reason}`
    const prescription = signature(result.sets)
    unchangedMisses = result.reason === 'MISSED' ? (prescription === previousPrescription ? unchangedMisses + 1 : 1) : 0
    if (unchangedMisses === 6) stalls.push(`${context}: ${prescription}`)
    previousPrescription = prescription
    result.sets.forEach((p) => check(p, context))
    finalTop = Math.max(...result.sets.map((p) => p.weightKg ?? 0))
    if (attended === 1) firstTop = finalTop
    const presc = [...result.sets]; const done: PastSet[] = []
    for (let i = 0; i < presc.length; i++) {
      const p = presc[i]
      const cap = name === 'light-dumbbells' ? 8 + 0.2 * (week - 1)
        : kind === 'REPS' ? 13 + 0.5 * (week - 1)
        : kind === 'HOLD' ? 40 + 2 * (week - 1)
        : name === 'layoff' && week >= 13 ? 82 + 2 * (week - 1) - 20 : 82 + 2 * (week - 1)
      const max = kind === 'WEIGHTED' ? Math.floor(30 * (cap / (p.weightKg ?? 1) - 1)) - i
        : kind === 'REPS' ? Math.floor(cap) - i : Math.floor(cap / 5) * 5 - 5 * i
      // Two untagged misses exercise light-load back-off without dropping a 5 kg dumbbell.
      const forced = name === 'light-dumbbells' && (slot === 3 || slot === 4)
      const actual = forced ? 7 : Math.min(p.reps ?? p.seconds ?? 0, Math.max(kind === 'HOLD' ? 5 : 1, max))
      const effort = forced ? 'OK' : actual < (p.reps ?? p.seconds ?? 0) ? 'FAILURE' : 'OK'
      const completed: PastSet = { reps: kind === 'HOLD' ? null : actual, seconds: kind === 'HOLD' ? actual : null, weightKg: p.weightKg, effort }
      done.push(completed)
      const entity = (index: number, pre: Prefill, finished?: PastSet): IntraSessionSet => ({
        id: index + 1, exerciseId: 'x', itemPosition: 0, setIndex: index,
        ...pre, ...(finished ?? {}), done: finished != null, effort: finished?.effort ?? '',
      })
      const remaining = presc.slice(i + 1).map((pre, j) => entity(i + j + 1, pre))
      const adjustment = computeAdjustment(entity(i, p, completed), remaining, kind, repsMin, unit)
      for (const change of adjustment?.changed ?? []) {
        const next = { reps: change.reps ?? null, seconds: change.seconds ?? null, weightKg: change.weightKg ?? null }
        check(next, `${context} adjusted set ${change.setIndex}`)
        presc[change.setIndex] = next
      }
    }
    if (kind === 'REPS') ready ||= readyForHarder(done, last, repsMax)
    history.unshift({ deload, sets: done })
    lastState = `${result.reason ?? '-'}|${result.holdCeilingReached}|${signature(result.sets)}|${signature(done)}|${done.map((p) => p.effort).join(',')}`
  }
  expect(attended + missed).toBe(72)
  if (name !== 'inconsistent') expect(missed).toBe(name === 'layoff' ? 12 : 0)
  else { expect(missed).toBeGreaterThan(0); expect(attended).toBeGreaterThan(36) }
  if (name.startsWith('consistent')) expect(finalTop).toBeGreaterThan(firstTop)
  if (name === 'push-up') expect(ready).toBe(true)
  const state = `${attended}|${missed}|${ready}|${lastState}`
  console.info(`${name}: ${state}`)
  // Light dumbbells: even with the high-rep bridge, stepping 5 -> 7.5 kg (+50 %) temporarily stalls a slow lifter
  // around week 9-10 at 7.50/10 (a step down is >10 %, audit S5) before recovering and finishing at 7.50/18.
  if (name === 'light-dumbbells') expect(finalTop).toBeGreaterThan(firstTop)
  else expect.soft(stalls, 'six unchanged MISSED prescriptions without recovery').toEqual([])
  expect(state).toBe(expected[name])
})
