import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { expect, it } from 'vitest'
import { EXERCISE_CATALOG } from '../data/seed/exercise-catalog'
import { EQUIPMENT_PRESETS, presetEquipment } from './exercise-swap-engine'
import type { Muscle } from './muscle'
import { EXPERIENCES, PlanBuilder, spreadDays, TRAIN_GOALS } from './plan-builder'
import { gradePlan } from './plan-grader'

/**
 * Locks plan quality: one deterministic plan per goal x days x preset x minutes group (700 plans). Floors are the
 * means measured after the master upgrade (3A) minus 2 points; every single plan must score at least 60.
 * The digest is shared with Android (PlanGraderFloorsTest.kt) through tools/plan-grader.golden: same scores on both.
 */
const FOCUSES: (Muscle | null)[] = [null, 'CHEST', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'ABS', 'UPPER_BACK', 'GLUTES', 'QUADS', 'HAMSTRINGS', 'CALVES']
export const GRADER_FLOORS: Record<string, number> = {
  '3x20:coverage': 21.29,
  'days:2': 87.97,
  'days:3': 88.35,
  'days:4': 86.42,
  'days:5': 84.76,
  'days:6': 81.79,
  'fatloss6:balance': 14.21,
  'goal:ENDURANCE': 84.77,
  'goal:FAT_LOSS': 85.12,
  'goal:GENERAL': 86.51,
  'goal:MUSCLE': 86.36,
  'goal:STRENGTH': 86.53,
  'minutes:20': 88.07,
  'minutes:30': 87.60,
  'minutes:45': 84.40,
  'minutes:60': 83.36,
}
const MIN_PLAN = 60

it('every sampled plan scores >= 60, group means stay above their floors, and match the shared golden', () => {
  const catalog = new Map(EXERCISE_CATALOG.map((e) => [e.id, e]))
  const hash = createHash('sha256')
  const sums = new Map<string, [number, number]>()
  const add = (k: string, v: number) => { const s = sums.get(k) ?? [0, 0]; s[0] += v; s[1]++; sums.set(k, s) }
  let g = 0
  for (const goal of TRAIN_GOALS) for (let days = 2; days <= 6; days++) for (const preset of EQUIPMENT_PRESETS) for (const minutes of [20, 30, 45, 60]) {
    const experience = EXPERIENCES[g % EXPERIENCES.length]
    const focus = FOCUSES[g % FOCUSES.length]
    g++
    const equipment = presetEquipment(preset); equipment.add('BODYWEIGHT')
    const plan = PlanBuilder.build({ goal, days: spreadDays(days), minutes, equipment, experience, focus: focus ? [focus] : [] }, EXERCISE_CATALOG)
    const grade = gradePlan(plan.draft, catalog, goal, minutes)
    const key = `${goal}|${days}|${preset}|${experience}|${focus ?? '-'}|${minutes}`
    expect(grade.total, key).toBeGreaterThanOrEqual(MIN_PLAN)
    const s = grade.scores
    hash.update(`${key}\t${grade.total.toFixed(2)}/${s.efficiency.toFixed(2)}/${s.coverage.toFixed(2)}/${s.balance.toFixed(2)}/${s.quality.toFixed(2)}/${s.pairing.toFixed(2)}\n`)
    add(`goal:${goal}`, grade.total); add(`days:${days}`, grade.total); add(`minutes:${minutes}`, grade.total)
    if (days === 3 && minutes === 20) add('3x20:coverage', s.coverage)
    if (goal === 'FAT_LOSS' && days === 6) add('fatloss6:balance', s.balance)
  }
  expect(g).toBe(700)
  const means = [...sums.entries()].map(([k, [sum, n]]) => [k, Math.round((sum / n) * 100) / 100] as const)
  console.info(means.map(([k, m]) => `${k}=${m.toFixed(2)}`).join(' '))
  for (const [k, m] of means) {
    const floor = GRADER_FLOORS[k]
    expect(floor, `missing floor ${k}`).toBeDefined()
    expect(m, k).toBeGreaterThanOrEqual(floor)
  }
  const digest = hash.digest('hex')
  console.info(`grader SHA-256 ${digest}`)
  expect(digest).toBe(readFileSync('../tools/plan-grader.golden', 'utf8').trim())
}, 180_000)
