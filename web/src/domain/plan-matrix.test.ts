import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { expect, it } from 'vitest'
import { EXERCISE_CATALOG } from '../data/seed/exercise-catalog'
import { EQUIPMENT_PRESETS, presetEquipment } from './exercise-swap-engine'
import type { Muscle } from './muscle'
import { EXPERIENCES, PlanBuilder, spreadDays, TRAIN_GOALS } from './plan-builder'
import { RoutineAuditor } from './routine-auditor'

// Keep iteration order and canonical UTF-8/LF records identical to PlanMatrixTest.kt.
const FOCUS: (Muscle | null)[] = [null, 'CHEST', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'ABS', 'UPPER_BACK', 'GLUTES', 'QUADS', 'HAMSTRINGS', 'CALVES']

it('audits a deterministic matrix sample and matches the shared standard-plan golden', () => {
  const byId = new Map(EXERCISE_CATALOG.map((e) => [e.id, e]))
  const hash = createHash('sha256')
  let index = 0; let total = 0; let standard = 0; let clean = 0; let shortNotes = 0
  for (const goal of TRAIN_GOALS) for (let n = 1; n <= 7; n++) for (const preset of EQUIPMENT_PRESETS)
    for (const exp of EXPERIENCES) for (const focus of FOCUS) for (const minutes of [20, 30, 45, 60]) {
      // All standard plans plus a rotating 1-in-11 sample of nonstandard plans (44 = 11 focuses x 4 durations).
      const standardPlan = n >= 3 && n <= 6 && minutes >= 45
      const sampled = index % 11 === Math.floor(index / 44) % 11
      index++
      if (!sampled && !standardPlan) continue
      const equipment = presetEquipment(preset); equipment.add('BODYWEIGHT')
      const plan = PlanBuilder.build({ goal, days: spreadDays(n), minutes, equipment, experience: exp, focus: focus ? [focus] : [] }, EXERCISE_CATALOG)
      const key = `${goal}|${n}|${preset}|${exp}|${focus ?? '-'}|${minutes}`
      const kinds = RoutineAuditor.audit(RoutineAuditor.fromDraft(plan.draft), byId).map((f) => f.kind)
      expect(kinds.filter((k) => ['ABOVE_MRV', 'JUNK_VOLUME', 'RECOVERY_OVERLAP'].includes(k)), key).toEqual([])
      if (n <= 2) {
        expect(kinds, key).not.toContain('BELOW_MEV')
        if (kinds.includes('SHORT_PLAN')) shortNotes++
      }
      total++
      if (n < 3 || n > 6 || minutes < 45) continue
      standard++
      if (kinds.length === 0) clean++
      expect(kinds.filter((k) => k !== 'BELOW_MEV'), key).toEqual([])
      // Day and item order are significant; null is literal "null"; no localized titles.
      hash.update(`${key}\t${plan.draft.days.map((d) => `${d.dayOfWeek}:` + d.items.map((i) =>
        `${i.exerciseId}/${i.sets}/${i.repsMin ?? 'null'}-${i.repsMax ?? 'null'}/${i.seconds ?? 'null'}/${i.restSeconds}`).join(';')).join(' || ')}\n`)
    }
  expect(total).toBe(11340)
  expect(standard).toBe(9240)
  expect(shortNotes).toBeGreaterThan(0)
  expect(clean / standard).toBeGreaterThanOrEqual(0.92)
  const digest = hash.digest('hex')
  console.info(`matrix: ${clean}/${standard} clean; SHA-256 ${digest}`)
  expect(digest).toBe(readFileSync('../tools/plan-matrix.golden', 'utf8').trim())
}, 90_000)
