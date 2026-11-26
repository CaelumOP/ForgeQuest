// Exhaustive invariants for the starting-point progression graph. Same assertions as Android ProgressionGraphTest.kt.
import { describe, expect, it } from 'vitest'
import { EXERCISE_CATALOG } from '../data/seed/exercise-catalog'
import type { ExerciseRow } from '../data/db'
import {
  baselineNudge, baselineNudgeResult, baselineNudgeRow, computeTargetDisplay, type BaselineNudge, type BaselineTargetDisplay,
} from '../ui/baseline/helpers'
import { easierVariation, harderVariation } from './baseline-targets'
import { EQUIPMENT_PRESETS, PRESET_INFO, expandLegacyEquipment, profileFits, profileFor, type Equipment } from './exercise-swap-engine'
import { TIERS, isProgressionMove, patternFamily, tierOf } from './exercise-tiers'

const catalog: ExerciseRow[] = EXERCISE_CATALOG.map((e) => ({ ...e, videoUrl: e.videoUrl ?? null, custom: false }))
const ex = (id: string) => catalog.find((e) => e.id === id)!
const moves = catalog.filter(isProgressionMove)

const gears: [string, Set<Equipment>][] = [
  ...EQUIPMENT_PRESETS.map((p) => [p, new Set<Equipment>(PRESET_INFO[p].equipment)] as [string, Set<Equipment>]),
  ['BODYWEIGHT', new Set<Equipment>(['BODYWEIGHT'])],
  ['DUMBBELLS_ONLY', new Set<Equipment>(['BODYWEIGHT', 'DUMBBELLS'])],
  ['DUMBBELLS_BENCH', new Set<Equipment>(['BODYWEIGHT', 'DUMBBELLS', 'FLAT_BENCH'])],
]
const allowedFor = (gear: Set<Equipment>) => {
  const a = expandLegacyEquipment(gear)
  a.add('BODYWEIGHT')
  return a
}
const tierIdx = (e: ExerciseRow) => TIERS.indexOf(tierOf(e)!)
const family = (e: ExerciseRow) => patternFamily(profileFor(e).pattern)

type Cell = { move: ExerciseRow; gear: Set<Equipment>; gearName: string; allowed: Set<Equipment> }
const cells: Cell[] = []
for (const [gearName, gear] of gears) {
  const allowed = allowedFor(gear)
  for (const move of moves) if (profileFits(profileFor(move), allowed)) cells.push({ move, gear, gearName, allowed })
}

function walk(c: Cell, step: (e: ExerciseRow) => ExerciseRow | null): ExerciseRow[] {
  const path = [c.move]
  for (let i = 0; i < 10; i++) {
    const next = step(path[path.length - 1])
    if (!next) return path
    if (path.some((p) => p.id === next.id)) throw new Error(`cycle at ${next.id} from ${c.move.id} on ${c.gearName}`)
    path.push(next)
  }
  throw new Error(`no end within 10 steps from ${c.move.id} on ${c.gearName}`)
}
const harder = (c: Cell) => (e: ExerciseRow) => harderVariation(e, catalog, c.gear) as ExerciseRow | null
const easier = (c: Cell) => (e: ExerciseRow) => easierVariation(e, catalog, c.gear) as ExerciseRow | null

describe('progression graph', () => {
  it('walking harder terminates, stays in scope and gear, never goes down, +2 only when no +1 fits', () => {
    for (const c of cells) {
      const path = walk(c, harder(c))
      for (let i = 1; i < path.length; i++) {
        const from = path[i - 1]
        const to = path[i]
        expect(isProgressionMove(to), `${to.id} in scope`).toBe(true)
        expect(profileFits(profileFor(to), c.allowed), `${to.id} fits ${c.gearName}`).toBe(true)
        expect(tierIdx(to), `${from.id} -> ${to.id} tier`).toBeGreaterThanOrEqual(tierIdx(from))
        if (tierIdx(to) - tierIdx(from) === 2) {
          const plusOne = moves.some((m) => m.id !== from.id && profileFits(profileFor(m), c.allowed) && family(m) === family(from) && tierIdx(m) === tierIdx(from) + 1)
          expect(plusOne, `${from.id} -> ${to.id} skipped a tier on ${c.gearName}`).toBe(false)
        }
      }
    }
  }, 60000)

  it('walking easier terminates, stays in scope and gear, never goes up', () => {
    for (const c of cells) {
      const path = walk(c, easier(c))
      for (let i = 1; i < path.length; i++) {
        const from = path[i - 1]
        const to = path[i]
        expect(isProgressionMove(to), `${to.id} in scope`).toBe(true)
        expect(profileFits(profileFor(to), c.allowed), `${to.id} fits ${c.gearName}`).toBe(true)
        expect(tierIdx(to), `${from.id} -> ${to.id} tier`).toBeLessThanOrEqual(tierIdx(from))
      }
    }
  }, 60000)

  it('no premature dead end in either direction', () => {
    for (const c of cells) {
      const fitting = moves.filter((m) => m.id !== c.move.id && profileFits(profileFor(m), c.allowed) && family(m) === family(c.move))
      if (!harderVariation(c.move, catalog, c.gear)) {
        expect(fitting.some((m) => tierIdx(m) > tierIdx(c.move)), `${c.move.id} has no harder on ${c.gearName} but one fits`).toBe(false)
      }
      if (!easierVariation(c.move, catalog, c.gear)) {
        expect(
          tierIdx(c.move) === 0 || !fitting.some((m) => tierIdx(m) < tierIdx(c.move)),
          `${c.move.id} has no easier on ${c.gearName} but one fits`,
        ).toBe(true)
      }
    }
  }, 60000)

  const values: [string, number][] = [['REPS', 30], ['REPS', 2], ['REPS', 0], ['HOLD', 120], ['HOLD', 5], ['HOLD', 0]]
  it('the nudge never names a missing move and top / bottom copy follows', () => {
    let checked = 0
    for (const c of cells) {
      for (const [kind, value] of values) {
        if (c.move.kind !== kind) continue
        checked++
        const target: BaselineTargetDisplay = computeTargetDisplay(
          c.move,
          { reps: kind === 'REPS' ? value : null, seconds: kind === 'HOLD' ? value : null, weightKg: null },
          catalog, c.gear, 'KG',
        )
        const nudge: BaselineNudge = baselineNudge(target)
        const row = baselineNudgeRow(nudge)
        const result = baselineNudgeResult(nudge)
        const high = kind === 'REPS' ? value >= 25 : value >= 90
        const low = value < (kind === 'REPS' ? 4 : 15)
        const h = harderVariation(c.move, catalog, c.gear)
        const e = easierVariation(c.move, catalog, c.gear)
        const label = `${c.move.id}/${kind}${value}/${c.gearName}`
        if (nudge.kind === 'TRY_HARDER' || nudge.kind === 'TRY_EASIER') expect(nudge.exercise, label).toBeTruthy()
        expect(nudge.kind === 'TOP_LEVEL', label).toBe(high && h == null)
        expect(nudge.kind === 'BOTTOM_LEVEL', label).toBe(low && e == null)
        expect(nudge.kind === 'TRY_HARDER', label).toBe(high && h != null)
        expect(nudge.kind === 'TRY_EASIER', label).toBe(low && e != null)
        if (nudge.kind === 'TOP_LEVEL') {
          expect(row?.key).toBe('baseline_row_top_level')
          expect(result?.key).toBe('baseline_maxed_for_gear')
        }
        if (nudge.kind === 'BOTTOM_LEVEL') {
          expect(row?.key).toBe('baseline_row_bottom_level')
          expect(result?.key).toBe('baseline_easiest_for_gear')
        }
        if (nudge.kind === 'TRY_HARDER') {
          expect(row?.key).toBe('baseline_row_too_easy')
          expect(result?.key).toBe('baseline_outgrown_try_harder')
        }
        if (nudge.kind === 'TRY_EASIER') {
          expect(row?.key).toBe('baseline_row_too_hard')
          expect(result?.key).toBe('baseline_try_easier')
        }
        if (nudge.kind === 'NONE') {
          expect(row).toBeNull()
          expect(result).toBeNull()
        }
      }
    }
    expect(checked).toBeGreaterThan(1000)
  }, 120000)

  it('owner cases', () => {
    const noGear = new Set<Equipment>(['BODYWEIGHT'])
    expect(harderVariation(ex('plank'), catalog, noGear)?.id).toBe('hollow_hold')
    expect(harderVariation(ex('hollow_hold'), catalog, noGear)).toBeNull()
    const logged = computeTargetDisplay(ex('hollow_hold'), { reps: null, seconds: 205, weightKg: null }, catalog, noGear, 'KG')
    expect(logged).toMatchObject({ kind: 'HOLD', seconds: 60, level: 'OUTGROWN', harderExercise: null })
    expect(baselineNudge(logged).kind).toBe('TOP_LEVEL')
    for (const preset of ['CALISTHENICS_PARK', 'FULL_GYM'] as const) {
      expect(harderVariation(ex('hollow_hold'), catalog, new Set(PRESET_INFO[preset].equipment))?.id).toBe('l_sit')
    }
    const path = walk({ move: ex('push_up'), gear: noGear, gearName: 'BODYWEIGHT', allowed: allowedFor(noGear) }, (e) => harderVariation(e, catalog, noGear) as ExerciseRow | null)
    expect(path.length).toBeGreaterThan(1)
    expect(tierOf(path[path.length - 1])).toBe('ADVANCED')
  })

  it('summary', () => {
    const tops = cells.filter((c) => !harderVariation(c.move, catalog, c.gear)).length
    // eslint-disable-next-line no-console
    console.log(`progression graph: ${moves.length} moves, ${cells.length} move x gear cells checked, premature dead ends 0, cycles 0 (true tops: ${tops})`)
    expect(cells.length).toBeGreaterThan(0)
  })
})
