import { describe, expect, it } from 'vitest'
// Raw Android sources, read at test time so the web seed cannot drift from the Kotlin reference.
import exerciseCatalogKt from '../../../../app/src/main/java/com/forgequest/app/data/seed/ExerciseCatalog.kt?raw'
import starterPlansKt from '../../../../app/src/main/java/com/forgequest/app/data/seed/StarterPlans.kt?raw'
import { EXERCISE_CATALOG } from './exercise-catalog'
import { EXERCISE_NAMES_AR } from './exercise-names-ar'
import { localizePlan, STARTER_PLANS, STARTER_PLANS_AR } from './starter-plans'

describe('seed data mirrors the Android catalog', () => {
  it('catalog ids are unique and match the number of ex(...) calls in ExerciseCatalog.kt', () => {
    const kotlinCount = (exerciseCatalogKt.match(/^\s+ex\("/gm) ?? []).length
    const ids = EXERCISE_CATALOG.map((e) => e.id)
    expect(new Set(ids).size).toBe(ids.length)
    expect(ids.length).toBe(kotlinCount)
  })

  it('every catalog id has an Arabic name and every Arabic name is a catalog id', () => {
    const ids = new Set(EXERCISE_CATALOG.map((e) => e.id))
    const arIds = Object.keys(EXERCISE_NAMES_AR)
    for (const id of ids) expect(EXERCISE_NAMES_AR[id], id).toBeTruthy()
    for (const id of arIds) expect(ids.has(id), id).toBe(true)
  })

  it('every exerciseId used in the starter plans exists in the catalog', () => {
    const ids = new Set(EXERCISE_CATALOG.map((e) => e.id))
    for (const plan of STARTER_PLANS) {
      for (const day of plan.days) {
        for (const item of day.items) expect(ids.has(item.exerciseId), `${plan.name}: ${item.exerciseId}`).toBe(true)
      }
    }
  })

  it('the number of starter plans equals the number of PlanDraft(...) in StarterPlans.kt', () => {
    const kotlinCount = (starterPlansKt.match(/val \w+ = PlanDraft\(/g) ?? []).length
    expect(STARTER_PLANS.length).toBe(kotlinCount)
  })

  it('the Arabic starter map has the same entry count as the Kotlin arabic map', () => {
    const block = starterPlansKt.slice(starterPlansKt.indexOf('val arabic = mapOf('), starterPlansKt.indexOf('private fun localize'))
    const kotlinCount = (block.match(/"\s*to\s*"/g) ?? []).length
    expect(kotlinCount).toBe(18)
    expect(Object.keys(STARTER_PLANS_AR).length).toBe(kotlinCount)
  })

  it('localizePlan translates the Hemo plan name to Arabic in ar and leaves it in en', () => {
    expect(localizePlan(STARTER_PLANS[0], 'ar').name).toBe('هيمو · المستوى التأسيسي')
    expect(localizePlan(STARTER_PLANS[0], 'en')).toBe(STARTER_PLANS[0])
  })
})
